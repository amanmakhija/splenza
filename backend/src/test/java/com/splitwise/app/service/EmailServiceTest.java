package com.splitwise.app.service;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {

        mimeMessage = new MimeMessage((jakarta.mail.Session) null);

        ReflectionTestUtils.setField(
                emailService,
                "from",
                "noreply@splenza.in"
        );

        when(mailSender.createMimeMessage())
                .thenReturn(mimeMessage);
    }

    @Test
    void sendVerificationEmail_shouldSendHtmlEmail() throws Exception {

        emailService.sendVerificationEmail(
                "aman@test.com",
                "Aman",
                "123456"
        );

        verify(mailSender).send(mimeMessage);

        Address[] to = mimeMessage.getRecipients(Message.RecipientType.TO);

        assertEquals(
                "aman@test.com",
                ((InternetAddress) to[0]).getAddress()
        );

        assertEquals(
                "Verify your Splenza account",
                mimeMessage.getSubject()
        );
    }

    @Test
    void sendPasswordResetEmail_shouldSendHtmlEmail() throws Exception {

        String link = "https://splenza.in/reset?token=abc";

        emailService.sendPasswordResetEmail(
                "aman@test.com",
                "Aman",
                link
        );

        verify(mailSender).send(mimeMessage);

        assertEquals(
                "Reset your Splenza password",
                mimeMessage.getSubject()
        );
    }

    @Test
    void sendVerificationEmail_shouldContainOtp() throws Exception {

        emailService.sendVerificationEmail(
                "aman@test.com",
                "Aman",
                "654321"
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        mimeMessage.writeTo(out);

        String content = out.toString();

        assertTrue(content.contains("654321"));
        assertTrue(content.contains("Verify your email"));
        assertTrue(content.contains("Aman"));
    }

    @Test
    void sendVerificationEmail_shouldWrapMessagingException() {

        when(mailSender.createMimeMessage())
                .thenThrow(new RuntimeException("SMTP"));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> emailService.sendVerificationEmail(
                        "aman@test.com",
                        "Aman",
                        "123456"
                )
        );

        assertEquals("SMTP", ex.getMessage());
    }
}
