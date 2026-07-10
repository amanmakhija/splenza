package com.splitwise.app.repository;

import com.splitwise.app.entity.ImportHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportHistoryRepository extends JpaRepository<ImportHistory, UUID> {

    List<ImportHistory> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
