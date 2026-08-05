package com.splitwise.app.repository;

import com.splitwise.app.entity.Settlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    // Unchanged existing queries — updated to exclude soft-deleted settlements
    List<Settlement> findByGroupIdAndDeletedFalseOrderBySettledAtDesc(UUID groupId);

    Page<Settlement> findByGroupIdAndDeletedFalseOrderBySettledAtDesc(UUID groupId, Pageable pageable);

    @Query("select s from Settlement s where s.group is null and s.deleted = false and "
            + "((s.paidBy.id = :u1 and s.paidTo.id = :u2) or (s.paidBy.id = :u2 and s.paidTo.id = :u1)) "
            + "order by s.settledAt desc")
    List<Settlement> findDirectSettlementsBetween(@Param("u1") UUID u1, @Param("u2") UUID u2);

    @Query("select s from Settlement s where s.deleted = false and "
            + "(s.paidBy.id = :u1 and s.paidTo.id = :u2) or (s.paidBy.id = :u2 and s.paidTo.id = :u1) "
            + "order by s.settledAt desc")
    List<Settlement> findAllSettlementsBetween(@Param("u1") UUID u1, @Param("u2") UUID u2);

    @Query("select s from Settlement s where s.deleted = false and "
            + "(s.paidBy.id = :u1 and s.paidTo.id = :u2) or (s.paidBy.id = :u2 and s.paidTo.id = :u1) "
            + "order by s.settledAt desc")
    Page<Settlement> findAllSettlementsBetween(@Param("u1") UUID u1, @Param("u2") UUID u2, Pageable pageable);

    // --- Cascade soft-delete / restore support ---
    @Modifying
    @Query("update Settlement s set s.deleted = true where s.group.id = :groupId and s.deleted = false")
    int softDeleteByGroupId(@Param("groupId") UUID groupId);

    @Modifying
    @Query("update Settlement s set s.deleted = false where s.group.id = :groupId and s.deleted = true")
    int restoreByGroupId(@Param("groupId") UUID groupId);

    @Query("select s from Settlement s where s.group.id = :groupId and s.deleted = false")
    List<Settlement> findActiveByGroupId(@Param("groupId") UUID groupId);

    // Backward-compat aliases so existing service callers (BalanceService,
    // SettlementService, ExportService) don't need to change.
    default List<Settlement> findByGroupIdOrderBySettledAtDesc(UUID groupId) {
        return findByGroupIdAndDeletedFalseOrderBySettledAtDesc(groupId);
    }

    default Page<Settlement> findByGroupIdOrderBySettledAtDesc(UUID groupId, Pageable pageable) {
        return findByGroupIdAndDeletedFalseOrderBySettledAtDesc(groupId, pageable);
    }
}
