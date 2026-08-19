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
 * <currency><amount>, rest equally" -> mixed explicit + equal-remainder split -
 * Relative/absolute date phrases anywhere in the transcript (see
 * SpokenDateParser)
 *
 * This intentionally only handles amounts written as digits with a currency
 * marker (e.g. "₹1200", "1200 rupees") - transcripts with spoken- out numbers
 * ("twelve hundred rupees") fall through to AI, since reliably parsing number
 * words is its own can of worms not worth solving here when the AI fallback
 * already handles it.
 */
final class DeterministicExpenseParser {

    private DeterministicExpenseParser() {
    }

    private static final String CURRENCY_MARKER = "(?:₹|rs\\.?|inr|\\$|usd)";
    private static final String AMOUNT = "([0-9]+(?:\\.[0-9]{1,2})?)";

    // "<description> ₹1200 split between Paul, Emma, and Marco"
    private static final Pattern EQUAL_SPLIT = Pattern.compile(
            "^(.*?)\\s*" + CURRENCY_MARKER + "\\s*" + AMOUNT + "\\s*,?\\s*"
            + "split(?:ting)?\\s+(?:between|among)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);

    // "<description> ₹1200, Marco ₹200, rest equally" - optionally preceded
    // by an explicit "split between <names>" clause naming everyone else.
    private static final Pattern MIXED_SPLIT = Pattern.compile(
            "^(.*?)\\s*" + CURRENCY_MARKER + "\\s*" + AMOUNT + "\\s*,?\\s*"
            + "(?:split(?:ting)?\\s+(?:between|among)\\s+(.+?)\\s*,\\s*)?"
            + "([A-Za-z][A-Za-z'\\s]*?)\\s*" + CURRENCY_MARKER + "\\s*" + AMOUNT + "\\s*,\\s*"
            + "rest\\s+equally$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PAYER_SELF = Pattern.compile("\\bi\\s+paid\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYER_NAMED = Pattern.compile(
            "\\b([A-Za-z][A-Za-z']*)\\s+paid\\b", Pattern.CASE_INSENSITIVE);

    static Optional<ExpenseParseResult> tryParse(String transcript, GroupContext context) {

        if (transcript == null || transcript.isBlank()) {
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

        ExpenseDraft draft = ExpenseDraft.builder()
                .title(description)
                .amount(amount)
                .currency(context.expectedCurrency())
                .categoryId(resolveCategoryId(description, context).orElse(null))
                .categoryName(resolveCategoryId(description, context)
                        .flatMap(id -> context.categories().stream()
                        .filter(c -> c.id().equals(id)).findFirst())
                        .map(GroupContext.CategoryOption::name)
                        .orElse(null))
                .expenseDate(resolveDate(text))
                .payerUserId(resolvePayer(text, context).map(GroupContext.Member::userId).orElse(null))
                .splitType(Expense.SplitType.EQUAL)
                .participants(participants)
                .build();

        return Optional.of(ExpenseParseResult.builder()
                .draft(draft)
                .confident(true)
                .clarificationQuestion(null)
                .build());
    }

    private static Optional<ExpenseParseResult> tryMixedSplit(String text, GroupContext context) {

        Matcher matcher = MIXED_SPLIT.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String description = cleanDescription(matcher.group(1));
        BigDecimal totalAmount = new BigDecimal(matcher.group(2));
        String namesBlob = matcher.group(3); // may be null - the explicit "split between" clause is optional
        String exceptionName = matcher.group(4).trim();
        BigDecimal exceptionAmount = new BigDecimal(matcher.group(5));

        Optional<GroupContext.Member> exceptionMember = ParticipantNameResolver.resolve(exceptionName, context);
        if (exceptionMember.isEmpty()) {
            return Optional.empty();
        }

        List<GroupContext.Member> everyone;
        if (namesBlob != null) {
            everyone = resolveAllOrNothing(namesBlob, context);
            if (everyone.isEmpty() || everyone.stream().noneMatch(m -> m.equals(exceptionMember.get()))) {
                return Optional.empty();
            }
        } else {
            // No explicit participant list given other than the one
            // exception - too ambiguous who "the rest" refers to for the
            // deterministic parser to guess; let AI handle it with full
            // sentence context instead.
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

        ExpenseDraft draft = ExpenseDraft.builder()
                .title(description)
                .amount(totalAmount)
                .currency(context.expectedCurrency())
                .categoryId(resolveCategoryId(description, context).orElse(null))
                .categoryName(resolveCategoryId(description, context)
                        .flatMap(id -> context.categories().stream()
                        .filter(c -> c.id().equals(id)).findFirst())
                        .map(GroupContext.CategoryOption::name)
                        .orElse(null))
                .expenseDate(resolveDate(text))
                .payerUserId(resolvePayer(text, context).map(GroupContext.Member::userId).orElse(null))
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
