package com.splitwise.app.service;

import com.splitwise.app.repository.PendingSignupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PendingSignupCleanupService {

    private final PendingSignupRepository repository;

    @Scheduled(cron = "0 */30 * * * *")
    public void cleanup() {

        repository.deleteAllByExpiresAtBefore(
                Instant.now()
        );

    }

}
