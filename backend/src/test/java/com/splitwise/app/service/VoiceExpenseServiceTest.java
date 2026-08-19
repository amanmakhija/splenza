package com.splitwise.app.service;

import com.splitwise.app.dto.voiceexpense.DraftParticipant;
import com.splitwise.app.dto.voiceexpense.ExpenseDraft;
import com.splitwise.app.dto.voiceexpense.VoiceExpenseResult;
import com.splitwise.app.entity.Category;
import com.splitwise.app.entity.Group;
import com.splitwise.app.entity.GroupMember;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AiFeature;
import com.splitwise.app.enums.CreditSource;
import com.splitwise.app.enums.VoiceExpenseStatus;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.nlp.ExpenseDraftValidator;
import com.splitwise.app.nlp.ExpenseNlpEngine;
import com.splitwise.app.nlp.ExpenseParseResult;
import com.splitwise.app.repository.CategoryRepository;
import com.splitwise.app.repository.GroupMemberRepository;
import com.splitwise.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceExpenseServiceTest {

    @Mock
    private AiCreditService aiCreditService;
    @Mock
    private SpeechToTextService speechToTextService;
    @Mock
    private ExpenseNlpEngine expenseNlpEngine;
    @Mock
    private ExpenseDraftValidator draftValidator;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private VoiceExpenseService service;

    private UUID userId;
    private UUID groupId;

    private static final byte[] AUDIO_BYTES = new byte[]{1, 2, 3, 4};

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        groupId = UUID.randomUUID();
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "recording.m4a", "audio/m4a", AUDIO_BYTES);
    }

    private void stubMembershipAndContext() {
        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNullAndDeletedFalse(groupId, userId))
                .thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndLeftAtIsNullAndDeletedFalse(groupId))
                .thenReturn(List.of());
        when(categoryRepository.findAll()).thenReturn(List.of());

        User user = new User();
        user.setId(userId);
        user.setPreferredCurrency("INR");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Rejects a request from a non-member without spending a credit")
    void process_rejectsNonMember() {

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNullAndDeletedFalse(groupId, userId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.process(userId, groupId, file()))
                .isInstanceOf(ApiException.class);

        verify(aiCreditService, never()).consume(any(), any());
    }

    @Test
    @DisplayName("Rejects an empty file without spending a credit")
    void process_rejectsEmptyFile() {

        MockMultipartFile empty = new MockMultipartFile("file", "recording.m4a", "audio/m4a", new byte[0]);

        assertThatThrownBy(() -> service.process(userId, groupId, empty))
                .isInstanceOf(ApiException.class);

        verify(aiCreditService, never()).consume(any(), any());
        verify(groupMemberRepository, never()).existsByGroupIdAndUserIdAndLeftAtIsNullAndDeletedFalse(any(), any());
    }

    @Test
    @DisplayName("Happy path: transcribes, parses, validates, and returns an OK result")
    void process_happyPath() {

        stubMembershipAndContext();

        when(aiCreditService.consume(userId, AiFeature.VOICE_EXPENSE))
                .thenReturn(new AiCreditService.ConsumptionResult(CreditSource.FREE));
        when(speechToTextService.transcribe(eq(AUDIO_BYTES), eq("audio/m4a")))
                .thenReturn("Dinner 1000 split between Paul and Emma");

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(new BigDecimal("1000"))
                .participants(List.of(DraftParticipant.builder().userId(UUID.randomUUID()).build()))
                .build();
        ExpenseParseResult parsed = ExpenseParseResult.builder().draft(draft).confident(true).build();
        ExpenseParseResult validated = ExpenseParseResult.builder().draft(draft).confident(true).build();

        when(expenseNlpEngine.parse(eq("Dinner 1000 split between Paul and Emma"), any())).thenReturn(parsed);
        when(draftValidator.validate(eq(parsed), any())).thenReturn(validated);
        when(aiCreditService.getBalance(userId, AiFeature.VOICE_EXPENSE))
                .thenReturn(com.splitwise.app.dto.aicredit.AiFeatureCreditsResponse.builder()
                        .totalAvailable(1).build());

        VoiceExpenseResult result = service.process(userId, groupId, file());

        assertThat(result.getStatus()).isEqualTo(VoiceExpenseStatus.OK);
        assertThat(result.getTranscript()).isEqualTo("Dinner 1000 split between Paul and Emma");
        assertThat(result.getCreditsRemaining()).isEqualTo(1);
        verify(aiCreditService, never()).refund(any(), any(), any());
    }

    @Test
    @DisplayName("Returns NEEDS_CLARIFICATION status when validation downgrades confidence")
    void process_returnsNeedsClarification() {

        stubMembershipAndContext();

        when(aiCreditService.consume(userId, AiFeature.VOICE_EXPENSE))
                .thenReturn(new AiCreditService.ConsumptionResult(CreditSource.FREE));
        when(speechToTextService.transcribe(any(), any())).thenReturn("some transcript");

        ExpenseDraft draft = ExpenseDraft.builder().amount(null).participants(List.of()).build();
        ExpenseParseResult parsed = ExpenseParseResult.builder().draft(draft).confident(false).build();
        ExpenseParseResult validated = ExpenseParseResult.builder()
                .draft(draft).confident(false).clarificationQuestion("How much was this expense?").build();

        when(expenseNlpEngine.parse(any(), any())).thenReturn(parsed);
        when(draftValidator.validate(any(), any())).thenReturn(validated);
        when(aiCreditService.getBalance(userId, AiFeature.VOICE_EXPENSE))
                .thenReturn(com.splitwise.app.dto.aicredit.AiFeatureCreditsResponse.builder()
                        .totalAvailable(1).build());

        VoiceExpenseResult result = service.process(userId, groupId, file());

        assertThat(result.getStatus()).isEqualTo(VoiceExpenseStatus.NEEDS_CLARIFICATION);
        assertThat(result.getClarificationQuestion()).isEqualTo("How much was this expense?");
    }

    @Test
    @DisplayName("Refunds the credit if the STT call fails, and rethrows")
    void process_refundsCredit_onSttFailure() {

        stubMembershipAndContext();

        when(aiCreditService.consume(userId, AiFeature.VOICE_EXPENSE))
                .thenReturn(new AiCreditService.ConsumptionResult(CreditSource.PURCHASED));
        when(speechToTextService.transcribe(any(), any()))
                .thenThrow(ApiException.badRequest("STT failed"));

        assertThatThrownBy(() -> service.process(userId, groupId, file()))
                .isInstanceOf(ApiException.class);

        verify(aiCreditService).refund(userId, AiFeature.VOICE_EXPENSE, CreditSource.PURCHASED);
    }

    @Test
    @DisplayName("Refunds the credit if the parsing pipeline fails, and rethrows")
    void process_refundsCredit_onParseFailure() {

        stubMembershipAndContext();

        when(aiCreditService.consume(userId, AiFeature.VOICE_EXPENSE))
                .thenReturn(new AiCreditService.ConsumptionResult(CreditSource.FREE));
        when(speechToTextService.transcribe(any(), any())).thenReturn("some transcript");
        when(expenseNlpEngine.parse(any(), any())).thenThrow(ApiException.badRequest("AI failed"));

        assertThatThrownBy(() -> service.process(userId, groupId, file()))
                .isInstanceOf(ApiException.class);

        verify(aiCreditService).refund(userId, AiFeature.VOICE_EXPENSE, CreditSource.FREE);
    }

    @Test
    @DisplayName("Rejects a file over the size limit without spending a credit")
    void process_rejectsOversizedFile() {

        byte[] oversized = new byte[16 * 1024 * 1024];
        MockMultipartFile big = new MockMultipartFile("file", "recording.m4a", "audio/m4a", oversized);

        assertThatThrownBy(() -> service.process(userId, groupId, big))
                .isInstanceOf(ApiException.class);

        verify(aiCreditService, never()).consume(any(), any());
    }

    @Test
    @DisplayName("Builds the group context from active members only, using the requesting user's "
            + "preferred currency")
    void process_buildsContextFromActiveMembersOnly() {

        stubMembershipAndContext();

        UUID otherUserId = UUID.randomUUID();
        User otherUser = new User();
        otherUser.setId(otherUserId);
        otherUser.setName("Other User");

        GroupMember member = GroupMember.builder().user(otherUser).group(new Group()).build();
        when(groupMemberRepository.findByGroupIdAndLeftAtIsNullAndDeletedFalse(groupId))
                .thenReturn(List.of(member));

        when(aiCreditService.consume(userId, AiFeature.VOICE_EXPENSE))
                .thenReturn(new AiCreditService.ConsumptionResult(CreditSource.FREE));
        when(speechToTextService.transcribe(any(), any())).thenReturn("transcript");

        ExpenseDraft draft = ExpenseDraft.builder().amount(null).participants(List.of()).build();
        ExpenseParseResult parsed = ExpenseParseResult.builder().draft(draft).confident(false).build();
        when(expenseNlpEngine.parse(any(), any())).thenReturn(parsed);
        when(draftValidator.validate(any(), any())).thenReturn(parsed);
        when(aiCreditService.getBalance(any(), any()))
                .thenReturn(com.splitwise.app.dto.aicredit.AiFeatureCreditsResponse.builder()
                        .totalAvailable(0).build());

        service.process(userId, groupId, file());

        var contextCaptor = org.mockito.ArgumentCaptor.forClass(com.splitwise.app.nlp.GroupContext.class);
        verify(expenseNlpEngine).parse(eq("transcript"), contextCaptor.capture());

        var context = contextCaptor.getValue();
        assertThat(context.expectedCurrency()).isEqualTo("INR");
        assertThat(context.requestingUserId()).isEqualTo(userId);
        assertThat(context.members()).extracting("userId").containsExactly(otherUserId);
    }
}
