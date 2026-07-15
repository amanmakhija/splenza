package com.splitwise.app.repository;

import com.splitwise.app.entity.EmailVerificationToken;
import com.splitwise.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByOtpHashAndUsedFalse(String otpHash);

    void deleteByUser(User user);

    Optional<EmailVerificationToken> findTopByUserAndUsedFalseOrderByExpiresAtDesc(User user);
}
