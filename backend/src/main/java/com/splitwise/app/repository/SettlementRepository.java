package com.splitwise.app.repository;

import com.splitwise.app.entity.Settlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    List<Settlement> findByGroupIdOrderBySettledAtDesc(UUID groupId);

    Page<Settlement> findByGroupIdOrderBySettledAtDesc(UUID groupId, Pageable pageable);

    @Query("select s from Settlement s where s.group is null and "
            + "((s.paidBy.id = :u1 and s.paidTo.id = :u2) or (s.paidBy.id = :u2 and s.paidTo.id = :u1)) "
            + "order by s.settledAt desc")
    List<Settlement> findDirectSettlementsBetween(@Param("u1") UUID u1, @Param("u2") UUID u2);

    /**
     * All settlements between exactly these two users, in ANY group or direct -
     * used for the collective per-friend balance, which must include
     * shared-group settlements too.
     */
    @Query("select s from Settlement s where "
            + "(s.paidBy.id = :u1 and s.paidTo.id = :u2) or (s.paidBy.id = :u2 and s.paidTo.id = :u1) "
            + "order by s.settledAt desc")
    List<Settlement> findAllSettlementsBetween(@Param("u1") UUID u1, @Param("u2") UUID u2);

    @Query("select s from Settlement s where "
            + "(s.paidBy.id = :u1 and s.paidTo.id = :u2) or (s.paidBy.id = :u2 and s.paidTo.id = :u1) "
            + "order by s.settledAt desc")
    Page<Settlement> findAllSettlementsBetween(@Param("u1") UUID u1, @Param("u2") UUID u2, Pageable pageable);
}
