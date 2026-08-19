package com.splitwise.app.service;

import com.splitwise.app.dto.voiceexpense.VoiceExpenseResult;
import com.splitwise.app.entity.GroupMember;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AiFeature;
import com.splitwise.app.enums.CreditSource;
import com.splitwise.app.enums.VoiceExpenseStatus;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.nlp.ExpenseDraftValidator;
import com.splitwise.app.nlp.ExpenseNlpEngine;
import com.splitwise.app.nlp.ExpenseParseResult;
import com.splitwise.app.nlp.GroupContext;
import com.splitwise.app.repository.CategoryRepository;
import com.splitwise.app.repository.GroupMemberRepository;
import com.splitwise.app.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the full voice-expense pipeline: validate the upload, spend/
 * refund a VOICE_EXPENSE AI credit via AiCreditService (exact same shared-
 * wallet pattern as ReceiptScanService - see AiCreditService's class javadoc),
 * transcribe, parse (deterministic-first-then-AI via ExpenseNlpEngine), and
 * validate the result (ExpenseDraftValidator) before ever returning it. Never
 * persists an expense itself - this only ever returns a draft; the existing
 * expense-creation endpoint is what actually saves anything, completely
 * unchanged by this feature.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceExpenseService {

    private static final long MAX_FILE_SIZE_BYTES = 15L * 1024 * 1024;

    private final AiCreditService aiCreditService;
    private final SpeechToTextService speechToTextService;
    private final ExpenseNlpEngine expenseNlpEngine;
    private final ExpenseDraftValidator draftValidator;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public VoiceExpenseResult process(UUID userId, UUID groupId, MultipartFile file) {

        byte[] audioBytes = validateAndRead(file);

        // Membership check happens before spending a credit - an
        // unauthorized request should never cost the user anything.
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNullAndDeletedFalse(groupId, userId)) {
            throw ApiException.forbidden("You are not a member of this group");
        }

        GroupContext context = buildGroupContext(userId, groupId);

        // Consume-before-work: reserve the credit first, refund it if the
        // pipeline fails entirely before producing any result - same
        // pattern as receipt scanning (see AiCreditService's class javadoc).
        var consumption = aiCreditService.consume(userId, AiFeature.VOICE_EXPENSE);
        CreditSource source = consumption.source();

        try {
            String transcript = speechToTextService.transcribe(audioBytes, file.getContentType());
            ExpenseParseResult parsed = expenseNlpEngine.parse(transcript, context);
            ExpenseParseResult validated = draftValidator.validate(parsed, context);

            int creditsRemaining = aiCreditService.getBalance(userId, AiFeature.VOICE_EXPENSE).getTotalAvailable();

            return VoiceExpenseResult.builder()
                    .status(validated.isConfident() ? VoiceExpenseStatus.OK : VoiceExpenseStatus.NEEDS_CLARIFICATION)
                    .draft(validated.getDraft())
                    .clarificationQuestion(validated.getClarificationQuestion())
                    .transcript(transcript)
                    .creditsRemaining(creditsRemaining)
                    .build();

        } catch (RuntimeException ex) {
            aiCreditService.refund(userId, AiFeature.VOICE_EXPENSE, source);
            throw ex;
        }
    }

    private byte[] validateAndRead(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No audio recording was provided");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw ApiException.badRequest("Recording exceeds the maximum allowed size of 15MB");
        }

        try {
            return file.getBytes();
        } catch (IOException ex) {
            log.warn("Failed to read uploaded voice recording bytes.", ex);
            throw ApiException.badRequest("Could not read the uploaded recording");
        }
    }

    private GroupContext buildGroupContext(UUID userId, UUID groupId) {

        List<GroupMember> activeMembers = groupMemberRepository
                .findByGroupIdAndLeftAtIsNullAndDeletedFalse(groupId);

        List<GroupContext.Member> members = activeMembers.stream()
                .map(gm -> new GroupContext.Member(gm.getUser().getId(), gm.getUser().getName()))
                .toList();

        List<GroupContext.CategoryOption> categories = categoryRepository.findAll().stream()
                .map(c -> new GroupContext.CategoryOption(c.getId(), c.getName()))
                .toList();

        User requestingUser = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        return new GroupContext(
                groupId,
                userId,
                requestingUser.getPreferredCurrency(),
                members,
                categories
        );
    }
}
