package com.splitwise.app.security;

import com.splitwise.app.entity.User;
import com.splitwise.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserDetailsService {

    private final UserRepository userRepository;

    public UserDetails loadUserById(UUID userId) {

        log.debug("Loading user details by id {}.", userId);

        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> {
                    log.warn("User {} not found or has been deleted.", userId);
                    return new UsernameNotFoundException("User not found");
                });

        log.debug("Successfully loaded user details for user {}.", userId);

        return buildUserDetails(user);
    }

    public UserDetails loadUserByEmail(String email) {

        log.debug("Loading user details for email {}.", maskEmail(email));

        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> {
                    log.warn("User with email {} not found.", email);
                    return new UsernameNotFoundException("User not found");
                });

        log.debug("Successfully loaded user details for user {}.", user.getId());

        return buildUserDetails(user);
    }

    private UserDetails buildUserDetails(User user) {

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getId().toString())
                .password(
                        java.util.Objects.requireNonNullElse(
                                user.getPasswordHash(),
                                ""
                        )
                )
                .authorities(Collections.singletonList(() -> "ROLE_USER"))
                .build();
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
