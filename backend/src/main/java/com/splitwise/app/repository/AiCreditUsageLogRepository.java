package com.splitwise.app.repository;

import com.splitwise.app.entity.AiCreditUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiCreditUsageLogRepository extends JpaRepository<AiCreditUsageLog, UUID> {
}
