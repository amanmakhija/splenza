package com.splitwise.app.service;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public void sendPasswordResetEmail(
            String to,
            String name,
            String resetLink
    ) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Reset your Splenza password");

        message.setText("""
                Hi %s,

                We received a request to reset your password.

                Reset your password here:

                %s

                This link expires in 1 hour.

                If you didn't request this, you can safely ignore this email.

                — Splenza Team
                """.formatted(name, resetLink));

        mailSender.send(message);
    }

    public void sendVerificationEmail(
            String email,
            String name,
            String otp
    ) {

        String subject = "Verify your Splenza account";

        String html = """
        <!DOCTYPE html>
        <html>
        <body style="font-family:Arial,sans-serif">

        <h2>Verify your email</h2>

        <p>Hello %s,</p>

        <p>Your verification code is:</p>

        <div
        style="
        font-size:34px;
        font-weight:bold;
        letter-spacing:8px;
        color:#4B4FE0;
        margin:24px 0;
        ">

        %s

        </div>

        <p>This code expires in 10 minutes.</p>

        <p>If you didn't create this account,
        simply ignore this email.</p>

        </body>
        </html>
        """
                .formatted(name, otp);

        sendHtmlEmail(
                email,
                subject,
                html
        );
    }

    private void sendHtmlEmail(
            String to,
            String subject,
            String html
    ) {

        try {

            MimeMessage message
                    = mailSender.createMimeMessage();

            MimeMessageHelper helper
                    = new MimeMessageHelper(
                            message,
                            true,
                            "UTF-8"
                    );

            helper.setTo(to);

            helper.setFrom(from);

            helper.setSubject(subject);

            helper.setText(html, true);

            mailSender.send(message);

        } catch (MessagingException e) {

            throw new RuntimeException(
                    "Failed to send email",
                    e
            );

        }

    }
}
