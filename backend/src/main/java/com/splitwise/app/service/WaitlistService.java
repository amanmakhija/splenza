package com.splitwise.app.service;

import com.splitwise.app.dto.waitlist.WaitlistRequest;
import com.splitwise.app.dto.waitlist.WaitlistResponse;
import com.splitwise.app.entity.Waitlist;
import com.splitwise.app.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository repository;

    public WaitlistResponse joinWaitlist(WaitlistRequest request) {

        String email = request.email().trim().toLowerCase();

        if (repository.existsByEmail(email)) {
            return new WaitlistResponse("You're already on the waitlist!");
        }

        Waitlist waitlist = Waitlist.builder()
                .email(email)
                .build();

        repository.save(waitlist);

        log.info(
                "New waitlist signup received for email {}.",
                maskEmail(email)
        );

        return new WaitlistResponse(
                "You've successfully joined the Splenza waitlist!"
        );
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

}
