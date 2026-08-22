package com.splitwise.app.nlp;

import com.splitwise.app.dto.voiceexpense.DraftParticipant;
import com.splitwise.app.dto.voiceexpense.ExpenseDraft;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.service.CategoryKeywords;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based parsing for the well-structured patterns explicitly worth handling
 * without an AI call - faster, cheaper, and more predictable than the AI
 * fallback for the phrasings it covers. Deliberately narrow: if the transcript
 * doesn't cleanly match one of these patterns with every name resolvable, this
 * returns empty rather than a low-confidence partial match -
 * CompositeExpenseNlpEngine falls through to AiExpenseParser in that case,
 * which has broader (but AI-cost-bearing) understanding.
 *
 * Patterns covered (see class javadoc on CompositeExpenseNlpEngine for how this
 * fits into the overall pipeline): - "<description> <currency><amount> split
 * between <names>" -> equal split - "<description> <currency><amount>, <name>
 * <currency><amount>, rest equally" -> mixed explicit + equal-remainder split,
 * optionally preceded by its own "split between <names>" clause; when that
 * clause is absent, "the rest" defaults to every other active group member (see
 * tryMixedSplit) - Relative/absolute date phrases anywhere in the transcript
 * (see SpokenDateParser)
 *
 * This intentionally only handles amounts written as digits with a currency
 * marker (e.g. "₹1200", "1200 rupees") - transcripts with spoken- out numbers
 * ("twelve hundred rupees") fall through to AI, since reliably parsing number
 * words is its own can of worms not worth solving here when the AI fallback
 * already handles it.
 *
 * The mixed-split pattern is deliberately implemented as TWO simple,
 * separately-testable regexes (MIXED_TAIL, MIXED_HEAD) rather than one pattern
 * with a nested optional clause + adjacent lazy quantifiers - that combination
 * is a classic source of silent mismatches and catastrophic backtracking on
 * adversarial input, and this runs on real user transcripts whose exact
 * phrasing isn't controlled. See DeterministicExpenseParserTest for correctness
 * coverage (including adversarial/pathological-input timing) and
 * MAX_TRANSCRIPT_LENGTH below for a cheap, unconditional backstop regardless of
 * what those tests show.
 */
final class DeterministicExpenseParser {

    private DeterministicExpenseParser() {
    }

    /**
     * Voice-expense transcripts are realistically a sentence or two - a
     * transcript far longer than that is either a mis-transcription or
     * pathological input, and isn't worth attempting deterministic parsing on
     * regardless of how well-behaved the regexes measure as being. This is
     * unconditional defense-in-depth: it bounds worst-case regex work to a
     * small, fixed input size no matter what future transcript phrasing turns
     * up, rather than relying solely on "the current patterns tested fine."
     */
    private static final int MAX_TRANSCRIPT_LENGTH = 500;

    private static final String CURRENCY_MARKER = "(?:₹|rs\\.?|inr|\\$|usd)";
    private static final String AMOUNT = "([0-9]+(?:\\.[0-9]{1,2})?)";

    // "<description> ₹1200 split between Paul, Emma, and Marco"
    private static final Pattern EQUAL_SPLIT = Pattern.compile(
            "^(.*?)\\s*" + CURRENCY_MARKER + "\\s*" + AMOUNT + "\\s*,?\\s*"
            + "split(?:ting)?\\s+(?:between|among)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);

    // Pass 1 of the mixed-split pattern: find the trailing "<name>
    // <currency><amount>, rest equally" clause, searching from the end.
    // Note the exception-name character class ([A-Za-z'\s]) can never
    // consume a digit or currency symbol, so this can't accidentally eat
    // into an unrelated earlier "<currency><amount>" elsewhere in the
    // transcript - that's what keeps this pass unambiguous and linear.
    private static final Pattern MIXED_TAIL = Pattern.compile(
            ",\\s*([A-Za-z][A-Za-z'\\s]*?)\\s*" + CURRENCY_MARKER + "\\s*" + AMOUNT + "\\s*,\\s*"
            + "rest\\s+equally\\s*$",
            Pattern.CASE_INSENSITIVE);

    // Pass 2, applied only to whatever text preceded the MIXED_TAIL match:
    // "<description> <currency><amount>[, split between <names>]" - a
    // single optional trailing group with nothing after it, so there's no
    // ambiguity about where it starts or ends.
    private static final Pattern MIXED_HEAD = Pattern.compile(
            "^(.*?)\\s*" + CURRENCY_MARKER + "\\s*" + AMOUNT
            + "(?:\\s*,?\\s*split(?:ting)?\\s+(?:between|among)\\s+(.+))?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PAYER_SELF = Pattern.compile("\\bi\\s+paid\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYER_NAMED = Pattern.compile(
            "\\b([A-Za-z][A-Za-z']*)\\s+paid\\b", Pattern.CASE_INSENSITIVE);

    static Optional<ExpenseParseResult> tryParse(String transcript, GroupContext context) {

        if (transcript == null || transcript.isBlank() || transcript.length() > MAX_TRANSCRIPT_LENGTH) {
            return Optional.empty();
        }

        String text = transcript.trim();

        Optional<ExpenseParseResult> mixed = tryMixedSplit(text, context);
        if (mixed.isPresent()) {
            return mixed;
        }

        return tryEqualSplit(text, context);
    }

    private static Optional<ExpenseParseResult> tryEqualSplit(String text, GroupContext context) {

        Matcher matcher = EQUAL_SPLIT.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String description = cleanDescription(matcher.group(1));
        BigDecimal amount = new BigDecimal(matcher.group(2));
        String namesBlob = matcher.group(3);

        List<GroupContext.Member> resolved = resolveAllOrNothing(namesBlob, context);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }

        List<DraftParticipant> participants = resolved.stream()
                .map(m -> DraftParticipant.builder().userId(m.userId()).amount(null).build())
                .toList();

        ExpenseDraft draft = buildDraftShell(description, amount, text, context)
                .splitType(Expense.SplitType.EQUAL)
                .participants(participants)
                .build();

        return Optional.of(ExpenseParseResult.builder()
                .draft(draft)
                .confident(true)
                .clarificationQuestion(null)
                .build());
    }

    /**
     * Two-pass: first find the trailing "<name> <currency><amount>, rest
     * equally" clause (MIXED_TAIL), then parse whatever text preceded it as
     * "<description> <currency><total>[, split between <names>]" (MIXED_HEAD).
     * See class javadoc for why this is deliberately two simple patterns
     * instead of one with a nested optional clause.
     */
    private static Optional<ExpenseParseResult> tryMixedSplit(String text, GroupContext context) {

        Matcher tailMatcher = MIXED_TAIL.matcher(text);
        if (!tailMatcher.find()) {
            return Optional.empty();
        }

        String exceptionName = tailMatcher.group(1).trim();
        BigDecimal exceptionAmount = new BigDecimal(tailMatcher.group(2));
        String headText = text.substring(0, tailMatcher.start()).trim();

        Matcher headMatcher = MIXED_HEAD.matcher(headText);
        if (!headMatcher.matches()) {
            return Optional.empty();
        }

        String description = cleanDescription(headMatcher.group(1));
        BigDecimal totalAmount = new BigDecimal(headMatcher.group(2));
        String namesBlob = headMatcher.group(3); // may be null - see resolveEveryoneForMixedSplit

        Optional<GroupContext.Member> exceptionMember = ParticipantNameResolver.resolve(exceptionName, context);
        if (exceptionMember.isEmpty()) {
            return Optional.empty();
        }

        List<GroupContext.Member> everyone = resolveEveryoneForMixedSplit(namesBlob, exceptionMember.get(), context);
        if (everyone.isEmpty()) {
            return Optional.empty();
        }

        int remainingCount = everyone.size() - 1;
        if (remainingCount <= 0 || exceptionAmount.compareTo(totalAmount) > 0) {
            return Optional.empty();
        }

        BigDecimal remainder = totalAmount.subtract(exceptionAmount);
        BigDecimal perPerson = remainder.divide(BigDecimal.valueOf(remainingCount), 2, RoundingMode.DOWN);
        BigDecimal distributed = perPerson.multiply(BigDecimal.valueOf(remainingCount));
        BigDecimal leftoverPaisa = remainder.subtract(distributed);

        List<DraftParticipant> participants = new ArrayList<>();
        boolean leftoverApplied = false;
        for (GroupContext.Member member : everyone) {
            if (member.equals(exceptionMember.get())) {
                participants.add(DraftParticipant.builder().userId(member.userId()).amount(exceptionAmount).build());
                continue;
            }
            BigDecimal share = perPerson;
            // Absorb the rounding remainder (a few cents/paise at most) into
            // the first non-exception participant, rather than losing it or
            // leaving the split unreconciled.
            if (!leftoverApplied && leftoverPaisa.compareTo(BigDecimal.ZERO) != 0) {
                share = share.add(leftoverPaisa);
                leftoverApplied = true;
            }
            participants.add(DraftParticipant.builder().userId(member.userId()).amount(share).build());
        }

        ExpenseDraft draft = buildDraftShell(description, totalAmount, text, context)
                .splitType(Expense.SplitType.EXACT)
                .participants(participants)
                .build();

        return Optional.of(ExpenseParseResult.builder()
                .draft(draft)
                .confident(true)
                .clarificationQuestion(null)
                .build());
    }

    /**
     * @return everyone splitting this expense - the explicit "split between
     * <names>" list if one was given (must include the exception member, and
     * every name must resolve or this bails entirely), or every active group
     * member if no explicit list was given at all (the canonical form of this
     * pattern - "<description>
     * <amount>, <name> <amount>, rest equally" - has no names list, so "the
     * rest" means everyone else in the group)
     */
    private static List<GroupContext.Member> resolveEveryoneForMixedSplit(
            String namesBlob, GroupContext.Member exceptionMember, GroupContext context) {

        if (namesBlob == null) {
            return context.members();
        }

        List<GroupContext.Member> resolved = resolveAllOrNothing(namesBlob, context);
        if (resolved.isEmpty() || resolved.stream().noneMatch(m -> m.equals(exceptionMember))) {
            return List.of();
        }
        return resolved;
    }

    /**
     * Splits a comma/"and"/"&"-separated blob of names and resolves every one
     * of them - all-or-nothing, since a partially-resolved participant list
     * from a "confident" pattern match would silently drop someone the user
     * explicitly named. The names blob is greedily captured to the end of the
     * transcript (since names can be followed by an arbitrary date/ payer
     * clause, e.g. "...Paul, Emma, and Marco, yesterday" or "..., Paul paid"),
     * so trailing non-name tokens - dates, "I paid"/"<name>
     * paid" - are filtered out here rather than treated as unresolvable names
     * that would fail the whole match.
     */
    private static List<GroupContext.Member> resolveAllOrNothing(String namesBlob, GroupContext context) {

        String[] tokens = namesBlob.split(",|\\band\\b|&");
        List<GroupContext.Member> resolved = new ArrayList<>();

        for (String token : tokens) {
            String name = token.trim();
            if (name.isEmpty() || isTrailingNoise(name)) {
                continue;
            }
            Optional<GroupContext.Member> member = ParticipantNameResolver.resolve(name, context);
            if (member.isEmpty()) {
                return List.of();
            }
            if (!resolved.contains(member.get())) {
                resolved.add(member.get());
            }
        }

        return resolved;
    }

    private static final Pattern TRAILING_NOISE = Pattern.compile(
            "(?i)^(yesterday|today|tonight|this morning|on|last\\s+\\w+|.*\\bpaid\\b.*|.*\\d.*|"
            + "january|february|march|april|may|june|july|august|september|october|november"
            + "|december)$");

    private static boolean isTrailingNoise(String token) {
        return TRAILING_NOISE.matcher(token).matches();
    }

    private static ExpenseDraft.ExpenseDraftBuilder buildDraftShell(
            String description, BigDecimal amount, String fullText, GroupContext context) {

        Optional<UUID> categoryId = resolveCategoryId(description, context);

        return ExpenseDraft.builder()
                .title(description)
                .amount(amount)
                .currency(context.expectedCurrency())
                .categoryId(categoryId.orElse(null))
                .categoryName(categoryId
                        .flatMap(id -> context.categories().stream()
                        .filter(c -> c.id().equals(id)).findFirst())
                        .map(GroupContext.CategoryOption::name)
                        .orElse(null))
                .expenseDate(resolveDate(fullText))
                .payerUserId(resolvePayer(fullText, context).map(GroupContext.Member::userId).orElse(null));
    }

    private static Optional<UUID> resolveCategoryId(String description, GroupContext context) {

        String haystack = " " + description.toLowerCase(Locale.ROOT) + " ";

        for (var entry : CategoryKeywords.KEYWORDS_BY_CATEGORY_NAME.entrySet()) {
            boolean hit = entry.getValue().stream().anyMatch(haystack::contains);
            if (!hit) {
                continue;
            }
            Optional<UUID> match = context.categories().stream()
                    .filter(c -> c.name().equalsIgnoreCase(entry.getKey()))
                    .map(GroupContext.CategoryOption::id)
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
        }

        return context.categories().stream()
                .filter(c -> "Others".equalsIgnoreCase(c.name()))
                .map(GroupContext.CategoryOption::id)
                .findFirst();
    }

    private static LocalDate resolveDate(String text) {
        LocalDate parsed = SpokenDateParser.parse(text, LocalDate.now());
        return parsed != null ? parsed : LocalDate.now();
    }

    private static Optional<GroupContext.Member> resolvePayer(String text, GroupContext context) {

        if (PAYER_SELF.matcher(text).find()) {
            return context.members().stream()
                    .filter(m -> m.userId().equals(context.requestingUserId()))
                    .findFirst();
        }

        Matcher namedMatcher = PAYER_NAMED.matcher(text);
        if (namedMatcher.find()) {
            return ParticipantNameResolver.resolve(namedMatcher.group(1), context);
        }

        return Optional.empty();
    }

    private static String cleanDescription(String raw) {
        String cleaned = raw.trim();
        // Strip a leading "split" / "splitting" verb some phrasings start
        // with before the description proper (e.g. "Split dinner ₹1200...").
        cleaned = cleaned.replaceFirst("(?i)^split(?:ting)?\\s+", "");
        cleaned = cleaned.replaceFirst("(?i)^for\\s+", "");
        if (cleaned.isEmpty()) {
            cleaned = "Expense";
        }
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }
}
