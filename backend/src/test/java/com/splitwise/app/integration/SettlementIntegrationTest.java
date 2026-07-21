package com.splitwise.app.integration;

import com.splitwise.app.dto.expense.CreateExpenseRequest;
import com.splitwise.app.dto.settlement.CreateSettlementRequest;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.User;
import com.splitwise.app.integration.util.TestDataFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SettlementIntegrationTest extends BaseIntegrationTest {

    // --------------------------------------------------------
    // Create settlement
    // --------------------------------------------------------
    @Nested
    @DisplayName("Create settlement")
    class CreateSettlementTests {

        @Test
        @DisplayName("should settle up within a group")
        void shouldSettleWithinGroup() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            CreateSettlementRequest request =
                    TestDataFactory.settlementRequest(groupId, creator.getId(), new BigDecimal("50.00"));

            mockMvc.perform(
                            post("/api/v1/settlements")
                                    .header("Authorization", bearerTokenFor(friend))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.groupId").value(groupId.toString()))
                    .andExpect(jsonPath("$.paidBy").value(friend.getId().toString()))
                    .andExpect(jsonPath("$.paidTo").value(creator.getId().toString()))
                    .andExpect(jsonPath("$.amount").value(50.00));
        }

        @Test
        @DisplayName("should settle up directly between friends (no group)")
        void shouldSettleDirectBetweenFriends() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            CreateSettlementRequest request =
                    TestDataFactory.settlementRequest(null, creator.getId(), new BigDecimal("20.00"));

            mockMvc.perform(
                            post("/api/v1/settlements")
                                    .header("Authorization", bearerTokenFor(friend))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.groupId").doesNotExist())
                    .andExpect(jsonPath("$.amount").value(20.00));
        }

        @Test
        @DisplayName("should reject settling with yourself")
        void shouldRejectSelfSettlement() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            CreateSettlementRequest request =
                    TestDataFactory.settlementRequest(null, creator.getId(), new BigDecimal("10.00"));

            mockMvc.perform(
                            post("/api/v1/settlements")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject settling in a group where the recipient isn't a member")
        void shouldRejectWhenRecipientNotGroupMember() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");

            UUID groupId = createGroup(creator, "Goa Trip");

            CreateSettlementRequest request =
                    TestDataFactory.settlementRequest(groupId, outsider.getId(), new BigDecimal("10.00"));

            mockMvc.perform(
                            post("/api/v1/settlements")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject settling in a group the acting user isn't a member of")
        void shouldRejectWhenActingUserNotGroupMember() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");
            makeFriends(creator, friend);
            makeFriends(outsider, creator);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            CreateSettlementRequest request =
                    TestDataFactory.settlementRequest(groupId, creator.getId(), new BigDecimal("10.00"));

            mockMvc.perform(
                            post("/api/v1/settlements")
                                    .header("Authorization", bearerTokenFor(outsider))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject an invalid recipient id")
        void shouldRejectInvalidRecipient() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            CreateSettlementRequest request =
                    TestDataFactory.settlementRequest(null, UUID.randomUUID(), new BigDecimal("10.00"));

            mockMvc.perform(
                            post("/api/v1/settlements")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject a zero or negative amount")
        void shouldRejectNonPositiveAmount() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            CreateSettlementRequest request =
                    TestDataFactory.settlementRequest(null, creator.getId(), BigDecimal.ZERO);

            mockMvc.perform(
                            post("/api/v1/settlements")
                                    .header("Authorization", bearerTokenFor(friend))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject settlement creation without authentication")
        void shouldRejectCreateWithoutAuth() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            CreateSettlementRequest request =
                    TestDataFactory.settlementRequest(null, creator.getId(), new BigDecimal("10.00"));

            mockMvc.perform(
                            post("/api/v1/settlements")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // History
    // --------------------------------------------------------
    @Nested
    @DisplayName("Settlement history")
    class SettlementHistoryTests {

        @Test
        @DisplayName("should let a group member list settlement history")
        void shouldListGroupHistory() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            settle(friend, groupId, creator.getId(), new BigDecimal("15.00"));

            mockMvc.perform(
                            get("/api/v1/settlements/group/" + groupId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].amount").value(15.00));
        }

        @Test
        @DisplayName("should reject listing settlement history for a group you're not a member of")
        void shouldRejectGroupHistoryForNonMember() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");

            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(
                            get("/api/v1/settlements/group/" + groupId)
                                    .header("Authorization", bearerTokenFor(outsider))
                    )
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should list settlement history with a friend")
        void shouldListFriendHistory() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            settle(friend, null, creator.getId(), new BigDecimal("12.00"));

            mockMvc.perform(
                            get("/api/v1/settlements/friend/" + creator.getId())
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].amount").value(12.00));
        }

        @Test
        @DisplayName("should reject friend history without authentication")
        void shouldRejectFriendHistoryWithoutAuth() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            mockMvc.perform(get("/api/v1/settlements/friend/" + creator.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject group settlement history without authentication")
        void shouldRejectGroupHistoryWithoutAuth() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(get("/api/v1/settlements/group/" + groupId))
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Balance effect: settle → balance becomes zero
    // --------------------------------------------------------
    @Nested
    @DisplayName("Settlement zeroes out balances")
    class SettlementBalanceEffectTests {

        @Test
        @DisplayName("should bring group balances to zero after a full settlement")
        void shouldZeroBalancesAfterFullSettlement() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            // creator pays 100, split equally -> friend owes creator 50
            CreateExpenseRequest expenseRequest = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("100.00"), Expense.SplitType.EQUAL,
                    List.of(
                            TestDataFactory.participant(creator.getId()),
                            TestDataFactory.participant(friend.getId())));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(expenseRequest))
                    )
                    .andExpect(status().isCreated());

            // friend settles the full 50 owed to creator
            CreateSettlementRequest settleRequest =
                    TestDataFactory.settlementRequest(groupId, creator.getId(), new BigDecimal("50.00"));

            mockMvc.perform(
                            post("/api/v1/settlements")
                                    .header("Authorization", bearerTokenFor(friend))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(settleRequest))
                    )
                    .andExpect(status().isCreated());

            mockMvc.perform(
                            get("/api/v1/balances/group/" + groupId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawBalances[0].userId").value(creator.getId().toString()))
                    .andExpect(jsonPath("$.rawBalances[0].netAmount").value(0.00))
                    .andExpect(jsonPath("$.rawBalances[1].userId").value(friend.getId().toString()))
                    .andExpect(jsonPath("$.rawBalances[1].netAmount").value(0.00))
                    .andExpect(jsonPath("$.simplifiedDebts.length()").value(0));
        }

        @Test
        @DisplayName("should leave a partial remaining balance after a partial settlement")
        void shouldLeavePartialBalanceAfterPartialSettlement() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            CreateExpenseRequest expenseRequest = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("100.00"), Expense.SplitType.EQUAL,
                    List.of(
                            TestDataFactory.participant(creator.getId()),
                            TestDataFactory.participant(friend.getId())));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(expenseRequest))
                    )
                    .andExpect(status().isCreated());

            // friend only pays back 20 of the 50 owed
            CreateSettlementRequest settleRequest =
                    TestDataFactory.settlementRequest(groupId, creator.getId(), new BigDecimal("20.00"));

            mockMvc.perform(
                            post("/api/v1/settlements")
                                    .header("Authorization", bearerTokenFor(friend))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(settleRequest))
                    )
                    .andExpect(status().isCreated());

            mockMvc.perform(
                            get("/api/v1/balances/group/" + groupId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawBalances[0].userId").value(creator.getId().toString()))
                    .andExpect(jsonPath("$.rawBalances[0].netAmount").value(30.00))
                    .andExpect(jsonPath("$.rawBalances[1].userId").value(friend.getId().toString()))
                    .andExpect(jsonPath("$.rawBalances[1].netAmount").value(-30.00));
        }
    }

    // --------------------------------------------------------
    // Helpers
    // --------------------------------------------------------
    private UUID createGroup(User creator, String name, User... friendsToAdd) throws Exception {

        List<UUID> memberIds = friendsToAdd.length == 0
                ? null
                : List.of(java.util.Arrays.stream(friendsToAdd).map(User::getId).toArray(UUID[]::new));

        var request = TestDataFactory.createGroupRequest(name, memberIds);

        String response = mockMvc.perform(
                        post("/api/v1/groups")
                                .header("Authorization", bearerTokenFor(creator))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void settle(User actor, UUID groupId, UUID paidTo, BigDecimal amount) throws Exception {

        CreateSettlementRequest request = TestDataFactory.settlementRequest(groupId, paidTo, amount);

        mockMvc.perform(
                        post("/api/v1/settlements")
                                .header("Authorization", bearerTokenFor(actor))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated());
    }
}