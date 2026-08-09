package com.splitwise.app.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwoFactorSmsSenderTest {

    private MockRestServiceServer mockServer;
    private TwoFactorSmsSender sender;
    private TwoFactorProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TwoFactorProperties();
        properties.setApiKey("test-api-key");
        properties.setBaseUrl("https://2factor.in");

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        mockServer = MockRestServiceServer.bindTo(builder).build();

        sender = new TwoFactorSmsSender(builder.build(), properties);
    }

    @Test
    void send_success_extractsCodeAndStripsCountryCode() {
        mockServer.expect(requestTo(
                "https://2factor.in/API/V1/test-api-key/SMS/9876543210/482913"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"Status\":\"Success\",\"Details\":\"abc-session-id\"}",
                        MediaType.APPLICATION_JSON));

        sender.send("+919876543210", "Your Splenza verification code is 482913. It expires in 10 minutes.");

        mockServer.verify();
    }

    @Test
    void send_includesTemplateNameWhenConfigured() {
        properties.setTemplateName("splenza_otp");

        mockServer.expect(requestTo(
                "https://2factor.in/API/V1/test-api-key/SMS/9876543210/482913/splenza_otp"))
                .andRespond(withSuccess(
                        "{\"Status\":\"Success\",\"Details\":\"abc-session-id\"}",
                        MediaType.APPLICATION_JSON));

        sender.send("+919876543210", "Your Splenza verification code is 482913. It expires in 10 minutes.");

        mockServer.verify();
    }

    @Test
    void send_providerReportsError_throws() {
        mockServer.expect(method(GET))
                .andRespond(withSuccess(
                        "{\"Status\":\"Error\",\"Details\":\"Invalid API Key\"}",
                        MediaType.APPLICATION_JSON));

        assertThrows(RuntimeException.class,
                () -> sender.send("+919876543210", "Your Splenza verification code is 482913."));
    }

    @Test
    void send_serverError_throwsRatherThanSwallowing() {
        mockServer.expect(method(GET))
                .andRespond(withServerError());

        assertThrows(RuntimeException.class,
                () -> sender.send("+919876543210", "Your Splenza verification code is 482913."));
    }

    @Test
    void send_messageWithoutExtractableCode_throwsBeforeCallingProvider() {
        assertThrows(IllegalStateException.class,
                () -> sender.send("+919876543210", "No code in here at all"));

        mockServer.verify(); // zero expectations set, zero requests should have been made
    }
}
