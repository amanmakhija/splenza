package com.splitwise.app.controller;

import com.splitwise.app.config.SecurityConfig;
import com.splitwise.app.dto.voiceexpense.ExpenseDraft;
import com.splitwise.app.dto.voiceexpense.VoiceExpenseResult;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.enums.VoiceExpenseStatus;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.security.AdminBroadcastFilter;
import com.splitwise.app.security.AppUserDetailsService;
import com.splitwise.app.security.JwtAuthenticationEntryPoint;
import com.splitwise.app.security.JwtAuthenticationFilter;
import com.splitwise.app.security.JwtService;
import com.splitwise.app.security.RtdnWebhookFilter;
import com.splitwise.app.service.VoiceExpenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VoiceExpenseController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AdminBroadcastFilter.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RtdnWebhookFilter.class)
})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class VoiceExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VoiceExpenseService voiceExpenseService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private AppUserDetailsService appUserDetailsService;
    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    @DisplayName("Returns an OK draft on a successful parse")
    @WithMockUser(username = USER_ID)
    void transcribeAndParse_returnsOkDraft() throws Exception {

        MockMultipartFile file = new MockMultipartFile("file", "recording.m4a", "audio/m4a", new byte[]{1, 2, 3});

        VoiceExpenseResult result = VoiceExpenseResult.builder()
                .status(VoiceExpenseStatus.OK)
                .draft(ExpenseDraft.builder()
                        .title("Dinner")
                        .amount(new BigDecimal("1200.00"))
                        .currency("INR")
                        .splitType(Expense.SplitType.EQUAL)
                        .participants(List.of())
                        .build())
                .transcript("Dinner 1200 split between Paul and Emma")
                .creditsRemaining(1)
                .build();

        when(voiceExpenseService.process(any(), any(), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/v1/voice-expenses")
                .file(file)
                .param("groupId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.draft.title").value("Dinner"))
                .andExpect(jsonPath("$.creditsRemaining").value(1));
    }

    @Test
    @DisplayName("Returns NEEDS_CLARIFICATION with a question when confidence is low")
    @WithMockUser(username = USER_ID)
    void transcribeAndParse_returnsNeedsClarification() throws Exception {

        MockMultipartFile file = new MockMultipartFile("file", "recording.m4a", "audio/m4a", new byte[]{1, 2, 3});

        VoiceExpenseResult result = VoiceExpenseResult.builder()
                .status(VoiceExpenseStatus.NEEDS_CLARIFICATION)
                .draft(ExpenseDraft.builder().title("Dinner").participants(List.of()).build())
                .clarificationQuestion("How much did Marco's drink cost?")
                .transcript("some transcript")
                .creditsRemaining(1)
                .build();

        when(voiceExpenseService.process(any(), any(), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/v1/voice-expenses")
                .file(file)
                .param("groupId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_CLARIFICATION"))
                .andExpect(jsonPath("$.clarificationQuestion").value("How much did Marco's drink cost?"));
    }

    @Test
    @DisplayName("Returns 402 when the user is out of AI credits")
    @WithMockUser(username = USER_ID)
    void transcribeAndParse_returns402_whenOutOfCredits() throws Exception {

        MockMultipartFile file = new MockMultipartFile("file", "recording.m4a", "audio/m4a", new byte[]{1, 2, 3});

        when(voiceExpenseService.process(any(), any(), any()))
                .thenThrow(ApiException.paymentRequired("You're out of AI credits."));

        mockMvc.perform(multipart("/api/v1/voice-expenses")
                .file(file)
                .param("groupId", UUID.randomUUID().toString()))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.message").value("You're out of AI credits."));
    }

    @Test
    @DisplayName("Returns 403 when the user isn't a member of the group")
    @WithMockUser(username = USER_ID)
    void transcribeAndParse_returns403_whenNotAGroupMember() throws Exception {

        MockMultipartFile file = new MockMultipartFile("file", "recording.m4a", "audio/m4a", new byte[]{1, 2, 3});

        when(voiceExpenseService.process(any(), any(), any()))
                .thenThrow(ApiException.forbidden("You are not a member of this group"));

        mockMvc.perform(multipart("/api/v1/voice-expenses")
                .file(file)
                .param("groupId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }
}
