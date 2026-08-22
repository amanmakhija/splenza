package com.splitwise.app.integration;

import com.splitwise.app.dto.expense.CreateExpenseRequest;
import com.splitwise.app.dto.settlement.CreateSettlementRequest;
import com.splitwise.app.dto.settlement.UpdateSettlementRequest;
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
    // Get a single settlement
    // --------------------------------------------------------
    @Nested
    @DisplayName("Get settlement by ID")
    class GetSettlementTests {

        @Test
        @DisplayName("should let the payer fetch the settlement")
        void shouldReturnForPayer() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID settlementId = settleReturningId(friend, null, creator.getId(), new BigDecimal("20.00"));

            // friend is the payer
            mockMvc.perform(
                            get("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(settlementId.toString()))
                    .andExpect(jsonPath("$.paidBy").value(friend.getId().toString()))
                    .andExpect(jsonPath("$.paidTo").value(creator.getId().toString()))
                    .andExpect(jsonPath("$.amount").value(20.00));
        }

        @Test
        @DisplayName("should let the payee fetch the settlement")
        void shouldReturnForPayee() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID settlementId = settleReturningId(friend, null, creator.getId(), new BigDecimal("20.00"));

            // creator is the payee
            mockMvc.perform(
                            get("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(settlementId.toString()));
        }

        @Test
        @DisplayName("should return 403 for a non-participant")
        void shouldReturnForbiddenForNonParticipant() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");
            makeFriends(creator, friend);

            UUID settlementId = settleReturningId(friend, null, creator.getId(), new BigDecimal("20.00"));

            mockMvc.perform(
                            get("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(outsider))
                    )
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 404 for an unknown id")
        void shouldReturnNotFoundForUnknownId() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            mockMvc.perform(
                            get("/api/v1/settlements/" + UUID.randomUUID())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should reject fetching without authentication")
        void shouldRejectWithoutAuth() throws Exception {

            mockMvc.perform(get("/api/v1/settlements/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Edit settlement amount (the ONLY editable field)
    // --------------------------------------------------------
    @Nested
    @DisplayName("Edit settlement amount")
    class EditSettlementTests {

        @Test
        @DisplayName("should update the amount and recompute group balances")
        void shouldUpdateAmountAndRecomputeBalances() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            // creator pays 100 split equally -> friend owes creator 50
            postEqualExpense(creator, groupId, new BigDecimal("100.00"), creator, friend);

            // friend settles the full 50 -> balances go to zero
            UUID settlementId = settleReturningId(friend, groupId, creator.getId(), new BigDecimal("50.00"));

            assertGroupNet(creator, groupId, creator, new BigDecimal("0.00"), friend, new BigDecimal("0.00"));

            // edit the settlement down to 20 -> friend now still owes 30
            mockMvc.perform(
                            patch("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(friend))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(updateAmount("20.00")))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(20.00));

            assertGroupNet(creator, groupId, creator, new BigDecimal("30.00"), friend, new BigDecimal("-30.00"));
        }

        @Test
        @DisplayName("should let the payee (not just the payer) edit")
        void shouldAllowPayeeToEdit() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            // friend is payer, creator is payee
            UUID settlementId = settleReturningId(friend, null, creator.getId(), new BigDecimal("40.00"));

            // creator (the payee) edits it
            mockMvc.perform(
                            patch("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(updateAmount("25.00")))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(25.00));
        }

        @Test
        @DisplayName("should ignore immutable fields (paidBy/paidTo/groupId/settledAt) in the body")
        void shouldIgnoreImmutableFields() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            // friend pays creator in the group
            UUID settlementId = settleReturningId(friend, groupId, creator.getId(), new BigDecimal("30.00"));

            // attempt to change everything - only amount must take effect
            String body = "{\"amount\":99.00"
                    + ",\"paidBy\":\"" + UUID.randomUUID() + "\""
                    + ",\"paidTo\":\"" + UUID.randomUUID() + "\""
                    + ",\"groupId\":\"" + UUID.randomUUID() + "\""
                    + ",\"settledAt\":\"2000-01-01T00:00:00Z\"}";

            mockMvc.perform(
                            patch("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(friend))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(99.00))
                    .andExpect(jsonPath("$.paidBy").value(friend.getId().toString()))
                    .andExpect(jsonPath("$.paidTo").value(creator.getId().toString()))
                    .andExpect(jsonPath("$.groupId").value(groupId.toString()));

            // and it is persisted that way
            mockMvc.perform(
                            get("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(99.00))
                    .andExpect(jsonPath("$.paidBy").value(friend.getId().toString()))
                    .andExpect(jsonPath("$.paidTo").value(creator.getId().toString()))
                    .andExpect(jsonPath("$.groupId").value(groupId.toString()));
        }

        @Test
        @DisplayName("should return 403 for a non-participant")
        void shouldReturnForbiddenForNonParticipant() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");
            makeFriends(creator, friend);

            UUID settlementId = settleReturningId(friend, null, creator.getId(), new BigDecimal("20.00"));

            mockMvc.perform(
                            patch("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(outsider))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(updateAmount("10.00")))
                    )
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject a non-positive amount")
        void shouldRejectNonPositiveAmount() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID settlementId = settleReturningId(friend, null, creator.getId(), new BigDecimal("20.00"));

            mockMvc.perform(
                            patch("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(friend))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(updateAmount("0.00")))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 for an unknown id")
        void shouldReturnNotFoundForUnknownId() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            mockMvc.perform(
                            patch("/api/v1/settlements/" + UUID.randomUUID())
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(updateAmount("10.00")))
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should reject editing without authentication")
        void shouldRejectWithoutAuth() throws Exception {

            mockMvc.perform(
                            patch("/api/v1/settlements/" + UUID.randomUUID())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(updateAmount("10.00")))
                    )
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Delete settlement (reverses its balance effect)
    // --------------------------------------------------------
    @Nested
    @DisplayName("Delete settlement")
    class DeleteSettlementTests {

        @Test
        @DisplayName("should reverse group balances on delete")
        void shouldReverseGroupBalancesOnDelete() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            // creator pays 100 split equally -> friend owes creator 50
            postEqualExpense(creator, groupId, new BigDecimal("100.00"), creator, friend);

            // friend settles the full 50 -> balances go to zero
            UUID settlementId = settleReturningId(friend, groupId, creator.getId(), new BigDecimal("50.00"));

            assertGroupNet(creator, groupId, creator, new BigDecimal("0.00"), friend, new BigDecimal("0.00"));

            mockMvc.perform(
                            delete("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isNoContent());

            // deleting the settlement puts the 50 debt back
            assertGroupNet(creator, groupId, creator, new BigDecimal("50.00"), friend, new BigDecimal("-50.00"));
        }

        @Test
        @DisplayName("should reverse a direct friend balance on delete (reverse-direction soft delete excluded)")
        void shouldReverseDirectFriendBalanceOnDelete() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            // friend pays creator 30 directly (paidBy = friend). From creator's
            // point of view this is the reverse direction of findAllSettlementsBetween.
            UUID settlementId = settleReturningId(friend, null, creator.getId(), new BigDecimal("30.00"));

            // creator now owes friend 30 (negative from creator's view)
            mockMvc.perform(
                            get("/api/v1/balances/friend/" + friend.getId())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.netAmount").value(-30.00));

            // creator (the payee) deletes it
            mockMvc.perform(
                            delete("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isNoContent());

            // if the JPQL precedence bug were present, the soft-deleted reverse-direction
            // settlement would still be counted and this would stay at -30
            mockMvc.perform(
                            get("/api/v1/balances/friend/" + friend.getId())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.netAmount").value(0.00));
        }

        @Test
        @DisplayName("should make the settlement disappear (404 on subsequent GET)")
        void shouldReturnNotFoundAfterDelete() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID settlementId = settleReturningId(friend, null, creator.getId(), new BigDecimal("20.00"));

            mockMvc.perform(
                            delete("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isNoContent());

            mockMvc.perform(
                            get("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should reject a second delete of the same settlement")
        void shouldRejectDoubleDelete() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID settlementId = settleReturningId(friend, null, creator.getId(), new BigDecimal("20.00"));

            mockMvc.perform(
                            delete("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isNoContent());

            mockMvc.perform(
                            delete("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 for a non-participant")
        void shouldReturnForbiddenForNonParticipant() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");
            makeFriends(creator, friend);

            UUID settlementId = settleReturningId(friend, null, creator.getId(), new BigDecimal("20.00"));

            mockMvc.perform(
                            delete("/api/v1/settlements/" + settlementId)
                                    .header("Authorization", bearerTokenFor(outsider))
                    )
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 404 for an unknown id")
        void shouldReturnNotFoundForUnknownId() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            mockMvc.perform(
                            delete("/api/v1/settlements/" + UUID.randomUUID())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should reject deleting without authentication")
        void shouldRejectWithoutAuth() throws Exception {

            mockMvc.perform(delete("/api/v1/settlements/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
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

    private UUID settleReturningId(User actor, UUID groupId, UUID paidTo, BigDecimal amount) throws Exception {

        CreateSettlementRequest request = TestDataFactory.settlementRequest(groupId, paidTo, amount);

        String response = mockMvc.perform(
                        post("/api/v1/settlements")
                                .header("Authorization", bearerTokenFor(actor))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void postEqualExpense(User payer, UUID groupId, BigDecimal amount, User... participants) throws Exception {

        CreateExpenseRequest request = TestDataFactory.expenseRequest(
                groupId, payer.getId(), amount, Expense.SplitType.EQUAL,
                java.util.Arrays.stream(participants)
                        .map(u -> TestDataFactory.participant(u.getId()))
                        .toList());

        mockMvc.perform(
                        post("/api/v1/expenses")
                                .header("Authorization", bearerTokenFor(payer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated());
    }

    private UpdateSettlementRequest updateAmount(String amount) {
        UpdateSettlementRequest request = new UpdateSettlementRequest();
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    /**
     * Asserts the two members' raw net balances in a group. The balance endpoint
     * returns members in insertion order, so index 0 is the group creator and
     * index 1 is the first added friend (matching how these tests build groups).
     */
    private void assertGroupNet(User viewer, UUID groupId,
                                User member0, BigDecimal net0,
                                User member1, BigDecimal net1) throws Exception {

        mockMvc.perform(
                        get("/api/v1/balances/group/" + groupId)
                                .header("Authorization", bearerTokenFor(viewer))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawBalances[0].userId").value(member0.getId().toString()))
                .andExpect(jsonPath("$.rawBalances[0].netAmount").value(net0.doubleValue()))
                .andExpect(jsonPath("$.rawBalances[1].userId").value(member1.getId().toString()))
                .andExpect(jsonPath("$.rawBalances[1].netAmount").value(net1.doubleValue()));
    }
}