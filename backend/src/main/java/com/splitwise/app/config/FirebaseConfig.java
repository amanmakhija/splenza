package com.splitwise.app.config;

import java.io.FileInputStream;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;

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
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try (InputStream stream
                = new FileInputStream(credentialsPath)) {
            FirebaseOptions options
                    = FirebaseOptions.builder()
                            .setCredentials(
                                    GoogleCredentials.fromStream(stream)
                            )
                            .build();
            FirebaseApp.initializeApp(options);
        }
    }

}
