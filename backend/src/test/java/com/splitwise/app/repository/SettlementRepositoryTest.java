package com.splitwise.app.repository;

import com.splitwise.app.entity.Group;
import com.splitwise.app.entity.Settlement;
import com.splitwise.app.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import com.splitwise.app.enums.AuthProvider;

class SettlementRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user1;
    private User user2;
    private User user3;
    private Group group;

    @BeforeEach
    void setUp() {
        user1 = createUser("User1", "u1@test.com");
        user2 = createUser("User2", "u2@test.com");
        user3 = createUser("User3", "u3@test.com");

        group = Group.builder()
                .name("Trip")
                .createdBy(user1)
                .build();

        entityManager.persist(group);
        entityManager.flush();
    }

    private User createUser(String name, String email) {
        User user = User.builder()
                .name(name)
                .email(email)
                .passwordHash("pwd")
                .provider(AuthProvider.LOCAL)
                .build();

        entityManager.persist(user);
        return user;
    }

    private Settlement createSettlement(
            Group group,
            User paidBy,
            User paidTo,
            Instant settledAt) {

        Settlement settlement = Settlement.builder()
                .group(group)
                .paidBy(paidBy)
                .paidTo(paidTo)
                .createdBy(paidBy)
                .amount(BigDecimal.valueOf(500))
                .currency("INR")
                .settledAt(settledAt)
                .build();

        entityManager.persist(settlement);
        return settlement;
    }

    @Test
    void findByGroupIdOrderBySettledAtDesc_shouldReturnOrderedSettlements() {

        Settlement older = createSettlement(
                group,
                user1,
                user2,
                Instant.parse("2025-01-01T00:00:00Z"));

        Settlement newer = createSettlement(
                group,
                user2,
                user1,
                Instant.parse("2025-02-01T00:00:00Z"));

        entityManager.flush();

        List<Settlement> result
                = settlementRepository.findByGroupIdOrderBySettledAtDesc(group.getId());

        assertThat(result)
                .containsExactly(newer, older);
    }

    @Test
    void findByGroupId_shouldIgnoreOtherGroups() {

        Group other = Group.builder()
                .name("Office")
                .createdBy(user1)
                .build();

        entityManager.persist(other);

        createSettlement(group, user1, user2, Instant.now());
        createSettlement(other, user1, user2, Instant.now());

        entityManager.flush();

        List<Settlement> result
                = settlementRepository.findByGroupIdOrderBySettledAtDesc(group.getId());

        assertThat(result)
                .hasSize(1);

        assertThat(result.getFirst().getGroup().getId())
                .isEqualTo(group.getId());
    }

    @Test
    void findByGroupId_shouldReturnEmptyWhenNoSettlements() {

        List<Settlement> result
                = settlementRepository.findByGroupIdOrderBySettledAtDesc(group.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByGroupId_pageable_shouldReturnFirstPage() {

        createSettlement(group, user1, user2,
                Instant.parse("2025-01-01T00:00:00Z"));

        createSettlement(group, user1, user2,
                Instant.parse("2025-02-01T00:00:00Z"));

        createSettlement(group, user1, user2,
                Instant.parse("2025-03-01T00:00:00Z"));

        entityManager.flush();

        Page<Settlement> page
                = settlementRepository.findByGroupIdOrderBySettledAtDesc(
                        group.getId(),
                        PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findByGroupId_pageable_shouldReturnSecondPage() {

        createSettlement(group, user1, user2,
                Instant.parse("2025-01-01T00:00:00Z"));

        createSettlement(group, user1, user2,
                Instant.parse("2025-02-01T00:00:00Z"));

        createSettlement(group, user1, user2,
                Instant.parse("2025-03-01T00:00:00Z"));

        entityManager.flush();

        Page<Settlement> page
                = settlementRepository.findByGroupIdOrderBySettledAtDesc(
                        group.getId(),
                        PageRequest.of(1, 2));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void findDirectSettlementsBetween_shouldReturnOnlyDirectSettlements() {

        Settlement expected
                = createSettlement(null, user1, user2, Instant.now());

        createSettlement(group, user1, user2, Instant.now());

        entityManager.flush();

        List<Settlement> result
                = settlementRepository.findDirectSettlementsBetween(
                        user1.getId(),
                        user2.getId());

        assertThat(result)
                .containsExactly(expected);
    }

    @Test
    void findDirectSettlementsBetween_shouldBeBidirectional() {

        Settlement settlement
                = createSettlement(null, user2, user1, Instant.now());

        entityManager.flush();

        List<Settlement> result
                = settlementRepository.findDirectSettlementsBetween(
                        user1.getId(),
                        user2.getId());

        assertThat(result)
                .containsExactly(settlement);
    }

    @Test
    void findDirectSettlementsBetween_shouldBeOrderedDescending() {

        Settlement oldSettlement
                = createSettlement(
                        null,
                        user1,
                        user2,
                        Instant.parse("2025-01-01T00:00:00Z"));

        Settlement newSettlement
                = createSettlement(
                        null,
                        user2,
                        user1,
                        Instant.parse("2025-03-01T00:00:00Z"));

        entityManager.flush();

        List<Settlement> result
                = settlementRepository.findDirectSettlementsBetween(
                        user1.getId(),
                        user2.getId());

        assertThat(result)
                .containsExactly(newSettlement, oldSettlement);
    }

    @Test
    void findAllSettlementsBetween_shouldIncludeGroupAndDirect() {

        Settlement direct
                = createSettlement(null, user1, user2, Instant.now());

        Settlement groupSettlement
                = createSettlement(group, user2, user1, Instant.now());

        entityManager.flush();

        List<Settlement> result
                = settlementRepository.findAllSettlementsBetween(
                        user1.getId(),
                        user2.getId());

        assertThat(result)
                .containsExactlyInAnyOrder(direct, groupSettlement);
    }

    @Test
    void findAllSettlementsBetween_shouldExcludeOtherUsers() {

        createSettlement(null, user1, user2, Instant.now());

        createSettlement(null, user1, user3, Instant.now());

        entityManager.flush();

        List<Settlement> result
                = settlementRepository.findAllSettlementsBetween(
                        user1.getId(),
                        user2.getId());

        assertThat(result).hasSize(1);
    }

    @Test
    void findAllSettlementsBetween_pageable_shouldReturnFirstPage() {

        createSettlement(null, user1, user2,
                Instant.parse("2025-01-01T00:00:00Z"));

        createSettlement(group, user1, user2,
                Instant.parse("2025-02-01T00:00:00Z"));

        createSettlement(null, user2, user1,
                Instant.parse("2025-03-01T00:00:00Z"));

        entityManager.flush();

        Page<Settlement> page
                = settlementRepository.findAllSettlementsBetween(
                        user1.getId(),
                        user2.getId(),
                        PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findAllSettlementsBetween_pageable_shouldReturnSecondPage() {

        createSettlement(null, user1, user2,
                Instant.parse("2025-01-01T00:00:00Z"));

        createSettlement(group, user1, user2,
                Instant.parse("2025-02-01T00:00:00Z"));

        createSettlement(null, user2, user1,
                Instant.parse("2025-03-01T00:00:00Z"));

        entityManager.flush();

        Page<Settlement> page
                = settlementRepository.findAllSettlementsBetween(
                        user1.getId(),
                        user2.getId(),
                        PageRequest.of(1, 2));

        assertThat(page.getContent()).hasSize(1);
    }
}
