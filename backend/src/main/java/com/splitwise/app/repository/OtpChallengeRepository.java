package com.splitwise.app.repository;

import com.splitwise.app.entity.OtpChallenge;
import com.splitwise.app.enums.IdentifierType;
import com.splitwise.app.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    /**
     * Most recent challenge for this identifier+purpose, consumed or not - used
     * for the resend cooldown check.
     */
    Optional<OtpChallenge> findFirstByIdentifierTypeAndIdentifierValueAndPurposeOrderByCreatedAtDesc(
            IdentifierType identifierType, String identifierValue, OtpPurpose purpose);

    /**
     * The live (unconsumed) challenge to verify a submitted code against.
     */
    Optional<OtpChallenge> findFirstByIdentifierTypeAndIdentifierValueAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            IdentifierType identifierType, String identifierValue, OtpPurpose purpose);
}
