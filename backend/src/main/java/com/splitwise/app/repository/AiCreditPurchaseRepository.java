package com.splitwise.app.repository;

import com.splitwise.app.entity.AiCreditPurchase;
import com.splitwise.app.enums.PurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiCreditPurchaseRepository extends JpaRepository<AiCreditPurchase, UUID> {

    Optional<AiCreditPurchase> findByGooglePlayPurchaseTokenAndStatus(String purchaseToken, PurchaseStatus status);

    Optional<AiCreditPurchase> findByGooglePlayPurchaseToken(String purchaseToken);
}
