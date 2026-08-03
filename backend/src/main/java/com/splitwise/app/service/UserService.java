package com.splitwise.app.service;

import com.splitwise.app.dto.user.UserLookupRequest;
import com.splitwise.app.dto.user.UserLookupResponse;
import com.splitwise.app.entity.User;
import com.splitwise.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserLookupResponse> lookupUsers(UUID requestingUserId, UserLookupRequest request) {
        List<UserLookupResponse> results = new ArrayList<>();

        if (request.getPhoneNumbers() != null) {
            Set<String> seenPhones = new HashSet<>();
            for (String phoneNumber : request.getPhoneNumbers()) {
                if (phoneNumber == null || phoneNumber.isBlank()) continue;
                String trimmedPhone = phoneNumber.trim();
                if (!seenPhones.add(trimmedPhone)) continue;
                Optional<User> userOpt = userRepository.findByPhoneNumberAndDeletedFalse(trimmedPhone);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (!user.getId().equals(requestingUserId)) {
                        results.add(UserLookupResponse.builder()
                                .matchedValue(phoneNumber)
                                .userId(user.getId())
                                .name(user.getName())
                                .build());
                    }
                }
            }
        }

        if (request.getEmails() != null) {
            Set<String> seenEmails = new HashSet<>();
            for (String email : request.getEmails()) {
                if (email == null || email.isBlank()) continue;
                String trimmedEmail = email.trim().toLowerCase();
                if (!seenEmails.add(trimmedEmail)) continue;
                Optional<User> userOpt = userRepository.findByEmailIgnoreCaseAndDeletedFalse(trimmedEmail);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (!user.getId().equals(requestingUserId)) {
                        results.add(UserLookupResponse.builder()
                                .matchedValue(email)
                                .userId(user.getId())
                                .name(user.getName())
                                .build());
                    }
                }
            }
        }

        return results;
    }
}