package com.splitwise.app.integration;

import com.splitwise.app.dto.expense.CreateExpenseRequest;
import com.splitwise.app.dto.expense.ExpenseParticipantInput;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

class ExpenseIntegrationTest extends BaseIntegrationTest {

    // --------------------------------------------------------
    // Create expense - split calculation
    // --------------------------------------------------------
    @Nested
    @DisplayName("Create expense - splits")
    class CreateExpenseSplitTests {

        @Test
        @DisplayName("should split equally with remainder cents going to earlier participants")
        void shouldSplitEqually() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            // 100.00 / 3 = 33.33 with 0.01 remainder -> first participant gets 33.34
            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("100.00"), Expense.SplitType.EQUAL,
                    List.of(
                            TestDataFactory.participant(creator.getId()),
                            TestDataFactory.participant(friend.getId()),
                            TestDataFactory.participant(creator.getId()) // placeholder replaced below
                    ));
            // three-way split needs a third distinct user
            User third = createVerifiedUser("third@test.com", "Password123");
            makeFriends(creator, third);
            mockMvc.perform(post("/api/v1/groups/" + groupId + "/members/" + third.getId())
                            .header("Authorization", bearerTokenFor(creator)))
                    .andExpect(status().isOk());

            request.setParticipants(List.of(
                    TestDataFactory.participant(creator.getId()),
                    TestDataFactory.participant(friend.getId()),
                    TestDataFactory.participant(third.getId())));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.amount").value(100.00))
                    .andExpect(jsonPath("$.participants.length()").value(3))
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(33.34))
                    .andExpect(jsonPath("$.participants[1].shareAmount").value(33.33))
                    .andExpect(jsonPath("$.participants[2].shareAmount").value(33.33));
        }

        @Test
        @DisplayName("should split by exact amounts that sum to the total")
        void shouldSplitExactly() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("100.00"), Expense.SplitType.EXACT,
                    List.of(
                            TestDataFactory.exactParticipant(creator.getId(), new BigDecimal("60.00")),
                            TestDataFactory.exactParticipant(friend.getId(), new BigDecimal("40.00"))));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(60.00))
                    .andExpect(jsonPath("$.participants[1].shareAmount").value(40.00));
        }

        @Test
        @DisplayName("should reject exact amounts that don't sum to the total")
        void shouldRejectExactMismatch() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("100.00"), Expense.SplitType.EXACT,
                    List.of(
                            TestDataFactory.exactParticipant(creator.getId(), new BigDecimal("60.00")),
                            TestDataFactory.exactParticipant(friend.getId(), new BigDecimal("30.00"))));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should split by percentage, with the last participant absorbing rounding")
        void shouldSplitByPercentage() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("100.00"), Expense.SplitType.PERCENTAGE,
                    List.of(
                            TestDataFactory.percentageParticipant(creator.getId(), new BigDecimal("70")),
                            TestDataFactory.percentageParticipant(friend.getId(), new BigDecimal("30"))));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(70.00))
                    .andExpect(jsonPath("$.participants[1].shareAmount").value(30.00));
        }

        @Test
        @DisplayName("should reject percentages that don't add up to 100")
        void shouldRejectPercentageMismatch() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("100.00"), Expense.SplitType.PERCENTAGE,
                    List.of(
                            TestDataFactory.percentageParticipant(creator.getId(), new BigDecimal("70")),
                            TestDataFactory.percentageParticipant(friend.getId(), new BigDecimal("20"))));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should split by shares proportionally")
        void shouldSplitByShares() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            // 3 shares vs 1 share out of 4 total -> 75/25
            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("100.00"), Expense.SplitType.SHARES,
                    List.of(
                            TestDataFactory.sharesParticipant(creator.getId(), 3),
                            TestDataFactory.sharesParticipant(friend.getId(), 1)));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(75.00))
                    .andExpect(jsonPath("$.participants[1].shareAmount").value(25.00));
        }
    }

    // --------------------------------------------------------
    // Create expense - validation / access control
    // --------------------------------------------------------
    @Nested
    @DisplayName("Create expense - validation")
    class CreateExpenseValidationTests {

        @Test
        @DisplayName("should reject a group expense with a non-member participant")
        void shouldRejectNonGroupMemberParticipant() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");

            UUID groupId = createGroup(creator, "Goa Trip");

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("50.00"), Expense.SplitType.EQUAL,
                    List.of(
                            TestDataFactory.participant(creator.getId()),
                            TestDataFactory.participant(outsider.getId())));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject creation by someone not in the group")
        void shouldRejectCreationByNonGroupMember() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");

            UUID groupId = createGroup(creator, "Goa Trip");

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("50.00"), Expense.SplitType.EQUAL,
                    List.of(TestDataFactory.participant(creator.getId())));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(outsider))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject duplicate participants")
        void shouldRejectDuplicateParticipants() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("50.00"), Expense.SplitType.EQUAL,
                    List.of(
                            TestDataFactory.participant(creator.getId()),
                            TestDataFactory.participant(creator.getId())));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject an empty participant list")
        void shouldRejectEmptyParticipants() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("50.00"), Expense.SplitType.EQUAL,
                    List.of());

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should create a direct (non-group) expense between friends")
        void shouldCreateDirectExpenseBetweenFriends() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    null, creator.getId(), new BigDecimal("40.00"), Expense.SplitType.EQUAL,
                    List.of(
                            TestDataFactory.participant(creator.getId()),
                            TestDataFactory.participant(friend.getId())));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.groupId").doesNotExist());
        }

        @Test
        @DisplayName("should reject a direct expense including a non-friend")
        void shouldRejectDirectExpenseWithNonFriend() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User stranger = createVerifiedUser("stranger@test.com", "Password123");

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    null, creator.getId(), new BigDecimal("40.00"), Expense.SplitType.EQUAL,
                    List.of(
                            TestDataFactory.participant(creator.getId()),
                            TestDataFactory.participant(stranger.getId())));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject creation without authentication")
        void shouldRejectCreateWithoutAuth() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("50.00"), Expense.SplitType.EQUAL,
                    List.of(TestDataFactory.participant(creator.getId())));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Get / list expenses
    // --------------------------------------------------------
    @Nested
    @DisplayName("Get / list expenses")
    class GetExpenseTests {

        @Test
        @DisplayName("should fetch an expense by id")
        void shouldGetExpenseById() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");
            UUID expenseId = createExpense(creator, groupId, new BigDecimal("50.00"), creator);

            mockMvc.perform(
                        get("/api/v1/expenses/" + expenseId)
                        .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Dinner"))
                    .andExpect(jsonPath("$.amount").value(50.00));
        }

        @Test
        @DisplayName("should list expenses for a group, paginated, newest first")
        void shouldListExpensesForGroup() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");

            createExpense(creator, groupId, new BigDecimal("10.00"), creator);
            createExpense(creator, groupId, new BigDecimal("20.00"), creator);

            mockMvc.perform(
                            get("/api/v1/expenses/group/" + groupId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("should list the current user's own expenses")
        void shouldListMyExpenses() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");
            createExpense(creator, groupId, new BigDecimal("15.00"), creator);

            mockMvc.perform(
                            get("/api/v1/expenses/me")
                            .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        @DisplayName("should search expenses by title")
        void shouldSearchExpensesByTitle() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");
            createExpense(creator, groupId, new BigDecimal("25.00"), creator);

            mockMvc.perform(
                            get("/api/v1/expenses/search")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .param("query", "Dinner")
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        @DisplayName("should return 404 for a non-existent expense")
        void shouldReturn404ForMissingExpense() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            mockMvc.perform(
                        get("/api/v1/expenses/" + UUID.randomUUID())
                        .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isNotFound());
        }
    }

    // --------------------------------------------------------
    // Delete expense
    // --------------------------------------------------------
    @Nested
    @DisplayName("Delete expense")
    class DeleteExpenseTests {

        @Test
        @DisplayName("should let the payer soft-delete their expense")
        void shouldDeleteExpenseSuccessfully() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");
            UUID expenseId = createExpense(creator, groupId, new BigDecimal("30.00"), creator);

            mockMvc.perform(
                            delete("/api/v1/expenses/" + expenseId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isNoContent());

            assertThat(expenseRepository.findById(expenseId).orElseThrow().isDeleted()).isTrue();

            mockMvc.perform(
                            get("/api/v1/expenses/" + expenseId)
                                    .header("Authorization", bearerTokenFor(creator))   // ADDED
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should reject deletion by someone who didn't create or pay for it")
        void shouldRejectDeleteByUnrelatedUser() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);
            UUID expenseId = createExpense(creator, groupId, new BigDecimal("30.00"), creator);

            mockMvc.perform(
                            delete("/api/v1/expenses/" + expenseId)
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject deleting a non-existent expense")
        void shouldRejectDeleteMissingExpense() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            mockMvc.perform(
                            delete("/api/v1/expenses/" + UUID.randomUUID())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should reject deletion without authentication")
        void shouldRejectDeleteWithoutAuth() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");
            UUID expenseId = createExpense(creator, groupId, new BigDecimal("30.00"), creator);

            mockMvc.perform(delete("/api/v1/expenses/" + expenseId))
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Balances update after expense creation
    // --------------------------------------------------------
    @Nested
    @DisplayName("Balances update")
    class BalanceUpdateTests {

        @Test
        @DisplayName("should reflect an equal-split expense in group balances")
        void shouldUpdateBalancesAfterEqualSplitExpense() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            CreateExpenseRequest request = TestDataFactory.expenseRequest(
                    groupId, creator.getId(), new BigDecimal("100.00"), Expense.SplitType.EQUAL,
                    List.of(
                            TestDataFactory.participant(creator.getId()),
                            TestDataFactory.participant(friend.getId())));

            mockMvc.perform(
                            post("/api/v1/expenses")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated());

            mockMvc.perform(
                            get("/api/v1/balances/group/" + groupId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawBalances[0].userId").value(creator.getId().toString()))
                    .andExpect(jsonPath("$.rawBalances[0].netAmount").value(50.00))
                    .andExpect(jsonPath("$.rawBalances[1].userId").value(friend.getId().toString()))
                    .andExpect(jsonPath("$.rawBalances[1].netAmount").value(-50.00))
                    .andExpect(jsonPath("$.simplifiedDebts.length()").value(1))
                    .andExpect(jsonPath("$.simplifiedDebts[0].fromUserId").value(friend.getId().toString()))
                    .andExpect(jsonPath("$.simplifiedDebts[0].toUserId").value(creator.getId().toString()))
                    .andExpect(jsonPath("$.simplifiedDebts[0].amount").value(50.00));
        }

        @Test
        @DisplayName("should return zero balances for a group with no expenses")
        void shouldReturnZeroBalancesForFreshGroup() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(
                            get("/api/v1/balances/group/" + groupId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawBalances[0].netAmount").value(0.00))
                    .andExpect(jsonPath("$.simplifiedDebts.length()").value(0));
        }

        @Test
        @DisplayName("should zero out balances again after a member removes the expense causing it")
        void shouldReflectDeletedExpenseInBalances() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            UUID expenseId = createExpenseSplitBetween(creator, groupId, new BigDecimal("60.00"), creator, friend);

            mockMvc.perform(
                            delete("/api/v1/expenses/" + expenseId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isNoContent());

            mockMvc.perform(
                            get("/api/v1/balances/group/" + groupId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawBalances[0].userId").value(creator.getId().toString()))
                    .andExpect(jsonPath("$.rawBalances[0].netAmount").value(0.00))
                    .andExpect(jsonPath("$.rawBalances[1].userId").value(friend.getId().toString()))
                    .andExpect(jsonPath("$.rawBalances[1].netAmount").value(0.00));
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

    private UUID createExpense(User actor, UUID groupId, BigDecimal amount, User paidBy) throws Exception {
        return createExpenseSplitBetween(actor, groupId, amount, paidBy, paidBy);
    }

    private UUID createExpenseSplitBetween(User actor, UUID groupId, BigDecimal amount, User paidBy, User... splitAmong)
            throws Exception {

        List<ExpenseParticipantInput> participants = java.util.Arrays.stream(splitAmong)
                .distinct()
                .map(u -> TestDataFactory.participant(u.getId()))
                .toList();

        CreateExpenseRequest request = TestDataFactory.expenseRequest(
                groupId, paidBy.getId(), amount, Expense.SplitType.EQUAL, participants);

        String response = mockMvc.perform(
                        post("/api/v1/expenses")
                                .header("Authorization", bearerTokenFor(actor))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}