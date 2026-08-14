package com.splitwise.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.billing.GooglePlayBillingService;
import com.splitwise.app.config.AiCreditProperties;
import com.splitwise.app.config.SecurityConfig;
import com.splitwise.app.dto.aicredit.AiFeatureCreditsResponse;
import com.splitwise.app.dto.aicredit.VerifyPurchaseRequest;
import com.splitwise.app.dto.aicredit.WalletBalanceResponse;
import com.splitwise.app.entity.AiCreditPurchase;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AiFeature;
import com.splitwise.app.enums.PurchaseStatus;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.repository.AiCreditPurchaseRepository;
import com.splitwise.app.repository.UserRepository;
import com.splitwise.app.security.AdminBroadcastFilter;
import com.splitwise.app.security.AppUserDetailsService;
import com.splitwise.app.security.JwtAuthenticationEntryPoint;
import com.splitwise.app.security.JwtAuthenticationFilter;
import com.splitwise.app.security.JwtService;
import com.splitwise.app.security.RtdnWebhookFilter;
import com.splitwise.app.service.AiCreditService;
import com.splitwise.app.service.RtdnNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AiCreditController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AdminBroadcastFilter.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RtdnWebhookFilter.class)
})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AiCreditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiCreditService aiCreditService;
    @MockBean
    private AiCreditProperties aiCreditProperties;
    @MockBean
    private AiCreditPurchaseRepository purchaseRepository;
    @MockBean
    private GooglePlayBillingService googlePlayBillingService;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private RtdnNotificationService rtdnNotificationService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private AppUserDetailsService appUserDetailsService;
    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    private AiCreditProperties.Package pack;

    @BeforeEach
    void setUp() {
        pack = new AiCreditProperties.Package();
        pack.setId("pack_30");
        pack.setCredits(30);
        pack.setPriceInPaise(9900);
        pack.setCurrency("INR");
        pack.setGooglePlayProductId("ai_credits_30");
    }

    @Test
    @DisplayName("GET balance returns the current user's credit balance for a feature")
    @WithMockUser(username = USER_ID)
    void getBalance_returnsBalance() throws Exception {

        AiFeatureCreditsResponse response = AiFeatureCreditsResponse.builder()
                .featureKey("RECEIPT_SCAN")
                .freeRemaining(2)
                .freeLimitPerDay(2)
                .freeResetAt(Instant.now())
                .purchasedBalance(5)
                .totalAvailable(7)
                .build();

        when(aiCreditService.getBalance(UUID.fromString(USER_ID), AiFeature.RECEIPT_SCAN)).thenReturn(response);

        mockMvc.perform(get("/api/v1/ai-credits/RECEIPT_SCAN/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeRemaining").value(2))
                .andExpect(jsonPath("$.purchasedBalance").value(5))
                .andExpect(jsonPath("$.totalAvailable").value(7));
    }

    @Test
    @DisplayName("GET balance with an unknown feature key returns 400")
    @WithMockUser(username = USER_ID)
    void getBalance_unknownFeature_returns400() throws Exception {

        mockMvc.perform(get("/api/v1/ai-credits/NOT_A_FEATURE/balance"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET packages returns the configured credit packages")
    @WithMockUser(username = USER_ID)
    void getPackages_returnsPackages() throws Exception {

        when(aiCreditService.getPackages()).thenReturn(List.of(
                com.splitwise.app.dto.aicredit.CreditPackageResponse.builder()
                        .id("pack_30").credits(30).priceInPaise(9900).currency("INR")
                        .googlePlayProductId("ai_credits_30").build()
        ));

        mockMvc.perform(get("/api/v1/ai-credits/packages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pack_30"))
                .andExpect(jsonPath("$[0].googlePlayProductId").value("ai_credits_30"));
    }

    @Test
    @DisplayName("Verify purchase: new purchase token is verified against Play and credits the wallet")
    @WithMockUser(username = USER_ID)
    void verifyPurchase_newToken_verifiesAndCredits() throws Exception {

        UUID userId = UUID.fromString(USER_ID);

        VerifyPurchaseRequest request = new VerifyPurchaseRequest();
        request.setProductId("ai_credits_30");
        request.setPurchaseToken("token-xyz");

        when(purchaseRepository.findByGooglePlayPurchaseToken("token-xyz")).thenReturn(Optional.empty());
        when(aiCreditProperties.getPackages()).thenReturn(List.of(pack));

        User user = new User();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(aiCreditService.creditPurchase(any(), eq(pack), eq("ai_credits_30"), eq("token-xyz")))
                .thenReturn(WalletBalanceResponse.builder().purchasedBalance(30).build());

        mockMvc.perform(post("/api/v1/ai-credits/purchases/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasedBalance").value(30));

        verify(googlePlayBillingService).verifyAndAcknowledge("ai_credits_30", "token-xyz");
        verify(aiCreditService).creditPurchase(any(), eq(pack), eq("ai_credits_30"), eq("token-xyz"));
    }

    @Test
    @DisplayName("Verify purchase: an already-VERIFIED token is idempotent - no re-verification, no re-credit")
    @WithMockUser(username = USER_ID)
    void verifyPurchase_alreadyVerifiedToken_isIdempotent() throws Exception {

        UUID userId = UUID.fromString(USER_ID);

        VerifyPurchaseRequest request = new VerifyPurchaseRequest();
        request.setProductId("ai_credits_30");
        request.setPurchaseToken("token-already-verified");

        AiCreditPurchase existing = AiCreditPurchase.builder()
                .status(PurchaseStatus.VERIFIED)
                .googlePlayPurchaseToken("token-already-verified")
                .build();

        when(purchaseRepository.findByGooglePlayPurchaseToken("token-already-verified"))
                .thenReturn(Optional.of(existing));

        when(aiCreditService.getBalance(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(AiFeatureCreditsResponse.builder().purchasedBalance(30).build());

        mockMvc.perform(post("/api/v1/ai-credits/purchases/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasedBalance").value(30));

        verify(googlePlayBillingService, never()).verifyAndAcknowledge(any(), any());
        verify(aiCreditService, never()).creditPurchase(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Verify purchase: unknown product ID is rejected before ever calling Google Play")
    @WithMockUser(username = USER_ID)
    void verifyPurchase_unknownProductId_isRejected() throws Exception {

        VerifyPurchaseRequest request = new VerifyPurchaseRequest();
        request.setProductId("not_a_real_product");
        request.setPurchaseToken("token-xyz");

        when(purchaseRepository.findByGooglePlayPurchaseToken("token-xyz")).thenReturn(Optional.empty());
        when(aiCreditProperties.getPackages()).thenReturn(List.of(pack));

        mockMvc.perform(post("/api/v1/ai-credits/purchases/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(googlePlayBillingService, never()).verifyAndAcknowledge(any(), any());
    }

    @Test
    @DisplayName("Verify purchase: missing required fields fails validation with 400")
    @WithMockUser(username = USER_ID)
    void verifyPurchase_missingFields_returns400() throws Exception {

        String invalidJson = "{}";

        mockMvc.perform(post("/api/v1/ai-credits/purchases/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
