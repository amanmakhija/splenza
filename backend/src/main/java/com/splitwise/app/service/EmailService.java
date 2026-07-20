package com.splitwise.app.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
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

        String subject = "Reset your Splenza password";

        String html = """
        <!DOCTYPE html>
        <html>
        <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,sans-serif;color:#333;">

        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:40px 20px;">
        <tr>
        <td align="center">

        <table role="presentation" width="600" cellspacing="0" cellpadding="0"
        style="background:#ffffff;border-radius:12px;padding:40px;">

        <tr>
        <td>

        <h2 style="margin:0 0 24px;color:#222;">
        Reset your password
        </h2>

        <p style="margin:0 0 16px;">
        Hi %s,
        </p>

        <p style="margin:0 0 24px;">
        We received a request to reset your password.
        </p>

        <div style="text-align:center;margin:32px 0;">

        <a href="%s"
        style="
        display:inline-block;
        background:#4B4FE0;
        color:#ffffff;
        text-decoration:none;
        padding:14px 32px;
        border-radius:8px;
        font-size:16px;
        font-weight:bold;
        ">
        Reset Password
        </a>

        </div>

        <p style="margin:24px 0 8px;">
        Or copy and paste this link into your browser:
        </p>

        <p style="word-break:break-all;">
        <a href="%s" style="color:#4B4FE0;">
        %s
        </a>
        </p>

        <p style="margin:24px 0;">
        This link expires in <strong>1 hour</strong>.
        </p>

        <p style="margin:24px 0;">
        If you didn't request this, you can safely ignore this email.
        </p>

        <hr style="border:none;border-top:1px solid #e5e5e5;margin:32px 0;">

        <p style="color:#666;font-size:14px;margin:0;">
        — Splenza Team
        </p>

        </td>
        </tr>

        </table>

        </td>
        </tr>
        </table>

        </body>
        </html>
        """
                .formatted(name, resetLink, resetLink, resetLink);

        sendHtmlEmail(
                to,
                subject,
                html
        );

        log.info(
                "Password reset email sent to {}.",
                maskEmail(to)
        );
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

        log.info(
                "Verification email sent to {}.",
                maskEmail(email)
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

            log.error(
                    "Failed to send email to {}. Subject={}",
                    maskEmail(to),
                    subject,
                    e
            );

            throw new RuntimeException(
                    "Failed to send email",
                    e
            );

        }

    }

    private String maskEmail(String email) {

        int at = email.indexOf('@');

        if (at <= 1) {
            return "***";
        }

        return email.charAt(0)
                + "***"
                + email.substring(at);
    }
}
