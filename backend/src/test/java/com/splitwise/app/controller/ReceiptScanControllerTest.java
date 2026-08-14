package com.splitwise.app.controller;

import com.splitwise.app.config.SecurityConfig;
import com.splitwise.app.dto.receipt.ReceiptScanResult;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.security.AdminBroadcastFilter;
import com.splitwise.app.security.AppUserDetailsService;
import com.splitwise.app.security.JwtAuthenticationEntryPoint;
import com.splitwise.app.security.JwtAuthenticationFilter;
import com.splitwise.app.security.JwtService;
import com.splitwise.app.security.RtdnWebhookFilter;
import com.splitwise.app.service.ReceiptScanService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReceiptScanController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AdminBroadcastFilter.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RtdnWebhookFilter.class)
})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReceiptScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReceiptScanService receiptScanService;

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
    @DisplayName("Scanning a receipt returns the structured result")
    @WithMockUser(username = USER_ID)
    void scan_returnsResult() throws Exception {

        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

        ReceiptScanResult result = ReceiptScanResult.builder()
                .merchantName("Cafe Coffee Day")
                .totalAmount(new BigDecimal("250.00"))
                .currency("INR")
                .creditsRemaining(1)
                .build();

        when(receiptScanService.scan(any(), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/v1/receipt-scans").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantName").value("Cafe Coffee Day"))
                .andExpect(jsonPath("$.creditsRemaining").value(1));
    }

    @Test
    @DisplayName("Returns 402 when the user is out of AI credits")
    @WithMockUser(username = USER_ID)
    void scan_returns402_whenOutOfCredits() throws Exception {

        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(receiptScanService.scan(any(), any()))
                .thenThrow(ApiException.paymentRequired("You're out of AI credits."));

        mockMvc.perform(multipart("/api/v1/receipt-scans").file(file))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.message").value("You're out of AI credits."));
    }

    @Test
    @DisplayName("Returns 400 for an invalid/corrupt image")
    @WithMockUser(username = USER_ID)
    void scan_returns400_forInvalidImage() throws Exception {

        MockMultipartFile file
                = new MockMultipartFile("file", "receipt.txt", "text/plain", "not an image".getBytes());

        when(receiptScanService.scan(any(), any()))
                .thenThrow(ApiException.badRequest("File must be a valid JPEG, PNG, WEBP, or HEIC image"));

        mockMvc.perform(multipart("/api/v1/receipt-scans").file(file))
                .andExpect(status().isBadRequest());
    }
}
