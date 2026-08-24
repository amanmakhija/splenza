package com.splitwise.app.integration;

import com.splitwise.app.dto.recurring.CreateRecurringPaymentRequest;
import com.splitwise.app.dto.recurring.RecurringParticipantInput;
import com.splitwise.app.dto.recurring.UpdateRecurringPaymentRequest;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.RecurringPayment;
import com.splitwise.app.entity.RecurringSuggestion;
import com.splitwise.app.entity.User;
import com.splitwise.app.repository.RecurringPaymentRepository;
import com.splitwise.app.repository.RecurringSuggestionRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the recurring-payments API (spec §2, §8) via MockMvc
 * against a real Testcontainers Postgres. Verifies the frozen JSON contract
 * (camelCase field names in {@code api.ts}), ownership scoping, split validation
 * reuse, soft-delete semantics, and the suggestion accept/dismiss lifecycle.
 */
class RecurringPaymentIntegrationTest extends BaseIntegrationTest {

    private static final String BASE = "/api/v1/recurring-payments";

    @Autowired
    private RecurringPaymentRepository recurringPaymentRepository;

    @Autowired
    private RecurringSuggestionRepository recurringSuggestionRepository;

    // ---------------------------------------------------------------------
    // Create
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("POST creates a MONTHLY rule, anchors the next occurrence, and returns the frozen contract")
    void createReturnsAnchoredRule() throws Exception {
        User owner = createVerifiedUser("owner@test.com", "Password1!");
        User friend = createVerifiedUser("friend@test.com", "Password1!", "Ravi");
        makeFriends(owner, friend);

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearerTokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(monthlyEqualRequest(owner, friend))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Netflix"))
                .andExpect(jsonPath("$.amount").value(600.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.intervalAnchor").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.autoCreate").value(true))
                .andExpect(jsonPath("$.paidBy").value(owner.getId().toString()))
                .andExpect(jsonPath("$.splitType").value("EQUAL"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                // anchor = day-of-month 1 => next occurrence always lands on the 1st.
                .andExpect(jsonPath("$.nextOccurrenceDate", Matchers.endsWith("-01")));
    }

    @Test
    @DisplayName("POST with EXACT shares that don't sum to the total is rejected (shared split rules)")
    void createWithExactMismatchIsBadRequest() throws Exception {
        User owner = createVerifiedUser("owner@test.com", "Password1!");
        User friend = createVerifiedUser("friend@test.com", "Password1!", "Ravi");
        makeFriends(owner, friend);

        CreateRecurringPaymentRequest request = monthlyEqualRequest(owner, friend);
        request.setSplitType(Expense.SplitType.EXACT);
        // Total is 600.00 but the exact shares sum to 200.00.
        request.setParticipants(List.of(
                exactParticipant(owner.getId(), "100.00"),
                exactParticipant(friend.getId(), "100.00")));

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearerTokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------
    // Read + ownership
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("GET list returns the caller's rules; a non-owner cannot fetch a rule by id (404)")
    void listAndOwnershipScoping() throws Exception {
        User owner = createVerifiedUser("owner@test.com", "Password1!");
        User friend = createVerifiedUser("friend@test.com", "Password1!", "Ravi");
        User stranger = createVerifiedUser("stranger@test.com", "Password1!", "Nobody");
        makeFriends(owner, friend);

        UUID ruleId = createRuleReturningId(owner, friend);

        mockMvc.perform(get(BASE).header("Authorization", bearerTokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ruleId.toString()));

        mockMvc.perform(get(BASE + "/" + ruleId).header("Authorization", bearerTokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId.toString()));

        // Someone else's rule is invisible: surfaces as 404, not 403.
        mockMvc.perform(get(BASE + "/" + ruleId).header("Authorization", bearerTokenFor(stranger)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------
    // Update
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("PATCH status pauses the rule")
    void patchPausesRule() throws Exception {
        User owner = createVerifiedUser("owner@test.com", "Password1!");
        User friend = createVerifiedUser("friend@test.com", "Password1!", "Ravi");
        makeFriends(owner, friend);
        UUID ruleId = createRuleReturningId(owner, friend);

        UpdateRecurringPaymentRequest patch = new UpdateRecurringPaymentRequest();
        patch.setStatus(RecurringPayment.Status.PAUSED);

        mockMvc.perform(patch(BASE + "/" + ruleId)
                        .header("Authorization", bearerTokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    // ---------------------------------------------------------------------
    // Delete (soft-end)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("DELETE soft-ends the rule (status=ENDED) and keeps it fetchable")
    void deleteSoftEndsRule() throws Exception {
        User owner = createVerifiedUser("owner@test.com", "Password1!");
        User friend = createVerifiedUser("friend@test.com", "Password1!", "Ravi");
        makeFriends(owner, friend);
        UUID ruleId = createRuleReturningId(owner, friend);

        mockMvc.perform(delete(BASE + "/" + ruleId).header("Authorization", bearerTokenFor(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + ruleId).header("Authorization", bearerTokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));
    }

    // ---------------------------------------------------------------------
    // Suggestions: accept + dismiss
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Accepting a PENDING suggestion creates a rule and marks the suggestion ACCEPTED")
    void acceptSuggestionCreatesRule() throws Exception {
        User owner = createVerifiedUser("owner@test.com", "Password1!");
        User friend = createVerifiedUser("friend@test.com", "Password1!", "Ravi");
        makeFriends(owner, friend);
        RecurringSuggestion suggestion = seedPendingSuggestion(owner, "cluster-netflix");

        mockMvc.perform(post(BASE + "/suggestions/" + suggestion.getId() + "/accept")
                        .header("Authorization", bearerTokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(monthlyEqualRequest(owner, friend))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.source").value("SUGGESTION_ACCEPTED"));

        assertThat(recurringSuggestionRepository.findById(suggestion.getId()))
                .get()
                .extracting(RecurringSuggestion::getStatus)
                .isEqualTo(RecurringSuggestion.Status.ACCEPTED);

        assertThat(recurringPaymentRepository.findByUserIdOrderByCreatedAtDesc(owner.getId()))
                .singleElement()
                .extracting(RecurringPayment::getSource)
                .isEqualTo(RecurringPayment.Source.SUGGESTION_ACCEPTED);
    }

    @Test
    @DisplayName("Dismissing a suggestion twice returns 409 on the second attempt")
    void dismissThenReDismissConflicts() throws Exception {
        User owner = createVerifiedUser("owner@test.com", "Password1!");
        RecurringSuggestion suggestion = seedPendingSuggestion(owner, "cluster-gym");

        mockMvc.perform(post(BASE + "/suggestions/" + suggestion.getId() + "/dismiss")
                        .header("Authorization", bearerTokenFor(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(BASE + "/suggestions/" + suggestion.getId() + "/dismiss")
                        .header("Authorization", bearerTokenFor(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Accepting another user's suggestion is not found (ownership scoping)")
    void acceptForeignSuggestionIsNotFound() throws Exception {
        User owner = createVerifiedUser("owner@test.com", "Password1!");
        User friend = createVerifiedUser("friend@test.com", "Password1!", "Ravi");
        User stranger = createVerifiedUser("stranger@test.com", "Password1!", "Nobody");
        makeFriends(stranger, friend);
        RecurringSuggestion suggestion = seedPendingSuggestion(owner, "cluster-netflix");

        mockMvc.perform(post(BASE + "/suggestions/" + suggestion.getId() + "/accept")
                        .header("Authorization", bearerTokenFor(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(monthlyEqualRequest(stranger, friend))))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private UUID createRuleReturningId(User owner, User friend) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE)
                        .header("Authorization", bearerTokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(monthlyEqualRequest(owner, friend))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    /** A valid direct-friend MONTHLY EQUAL rule, anchored to the 1st, starting in the near future. */
    private CreateRecurringPaymentRequest monthlyEqualRequest(User owner, User friend) {
        CreateRecurringPaymentRequest request = new CreateRecurringPaymentRequest();
        request.setTitle("Netflix");
        request.setAmount(new BigDecimal("600.00"));
        request.setCurrency("INR");
        request.setPaidBy(owner.getId());
        request.setSplitType(Expense.SplitType.EQUAL);
        request.setParticipants(List.of(participant(owner.getId()), participant(friend.getId())));
        request.setFrequency(RecurringPayment.Frequency.MONTHLY);
        request.setIntervalAnchor(1);
        request.setStartDate(LocalDate.now(ZoneOffset.UTC).plusDays(3));
        request.setAutoCreate(true);
        request.setReminderEnabled(false);
        return request;
    }

    private RecurringParticipantInput participant(UUID userId) {
        RecurringParticipantInput input = new RecurringParticipantInput();
        input.setUserId(userId);
        return input;
    }

    private RecurringParticipantInput exactParticipant(UUID userId, String shareAmount) {
        RecurringParticipantInput input = new RecurringParticipantInput();
        input.setUserId(userId);
        input.setShareAmount(new BigDecimal(shareAmount));
        return input;
    }

    private RecurringSuggestion seedPendingSuggestion(User user, String clusterKey) {
        return recurringSuggestionRepository.save(RecurringSuggestion.builder()
                .user(user)
                .clusterKey(clusterKey)
                .suggestedTitle("Netflix")
                .suggestedAmount(new BigDecimal("600.00"))
                .currency("INR")
                .suggestedFrequency(RecurringPayment.Frequency.MONTHLY)
                .confidenceScore(new BigDecimal("0.950"))
                .matchedExpenseIds(List.of())
                .firstSeenDate(LocalDate.of(2026, 1, 1))
                .lastSeenDate(LocalDate.of(2026, 6, 1))
                .predictedNextDate(LocalDate.of(2026, 7, 1))
                .status(RecurringSuggestion.Status.PENDING)
                .build());
    }
}
