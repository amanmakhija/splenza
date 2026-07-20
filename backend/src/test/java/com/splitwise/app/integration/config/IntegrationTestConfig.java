package com.splitwise.app.integration.config;

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

    @Bean
    @Primary
    JavaMailSender javaMailSender() {

        JavaMailSender sender = Mockito.mock(JavaMailSender.class);

        Mockito.when(sender.createMimeMessage())
                .thenAnswer(invocation ->
                        new MimeMessage(
                                Session.getDefaultInstance(new Properties())
                        ));

        Mockito.doAnswer(invocation -> {

            lastMessage = invocation.getArgument(0);

            return null;

        }).when(sender).send(Mockito.any(MimeMessage.class));

        return sender;
    }

    public MimeMessage getLastMessage() {
        return lastMessage;
    }
}