package com.splitwise.app.integration.config;

import com.splitwise.app.sms.SmsSender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

@TestConfiguration
public class IntegrationTestConfig {

    private MimeMessage lastMessage;
    private String lastSmsPhone;
    private String lastSmsMessage;

    @Bean
    @Primary
    JavaMailSender javaMailSender() {

        JavaMailSender sender = Mockito.mock(JavaMailSender.class);

        Mockito.when(sender.createMimeMessage())
                .thenAnswer(invocation
                        -> new MimeMessage(
                        Session.getDefaultInstance(new Properties())
                ));

        Mockito.doAnswer(invocation -> {

            lastMessage = invocation.getArgument(0);

            return null;

        }).when(sender).send(Mockito.any(MimeMessage.class));

        return sender;
    }

    /**
     * Test double for SmsSender, overriding NoOpSmsSender in the test context -
     * captures the last "sent" SMS so tests can pull the OTP out of it the same
     * way getLastOtp() does for email, rather than needing to reverse-engineer
     * the hashed code from otp_challenges.
     */
    @Bean
    @Primary
    SmsSender smsSender() {
        return (phone, message) -> {
            lastSmsPhone = phone;
            lastSmsMessage = message;
        };
    }

    public MimeMessage getLastMessage() {
        return lastMessage;
    }

    public String getLastSmsPhone() {
        return lastSmsPhone;
    }

    public String getLastSmsMessage() {
        return lastSmsMessage;
    }
}
