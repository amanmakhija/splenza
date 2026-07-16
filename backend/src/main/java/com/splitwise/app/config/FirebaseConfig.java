package com.splitwise.app.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void init() throws Exception {

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        InputStream stream = getClass()
                .getClassLoader()
                .getResourceAsStream("firebase/firebase-admin.json");

        if (stream == null) {
            throw new IllegalStateException(
                    "firebase-admin.json not found"
            );
        }

        FirebaseOptions options
                = FirebaseOptions.builder()
                        .setCredentials(
                                GoogleCredentials.fromStream(stream)
                        )
                        .build();

        FirebaseApp.initializeApp(options);

    }

}
