package com.splitwise.app.config;

import java.io.FileInputStream;
import java.io.InputStream;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.enabled:false}")
    private boolean enabled;

    @Value("${firebase.credentials:}")
    private String credentialsPath;

    @PostConstruct
    public void init() throws Exception {

        if (!enabled) {
            log.info("Firebase Cloud Messaging is disabled.");
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase already initialized. Skipping initialization.");
            return;
        }

        log.info("Initializing Firebase using credentials at '{}'.", credentialsPath);

        try (InputStream stream = new FileInputStream(credentialsPath)) {

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();

            FirebaseApp.initializeApp(options);

            log.info("Firebase initialized successfully.");

        } catch (Exception ex) {

            log.error("Failed to initialize Firebase.", ex);

            throw ex;
        }
    }
}
