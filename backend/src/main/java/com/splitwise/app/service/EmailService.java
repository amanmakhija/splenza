package com.splitwise.app.service;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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
}
