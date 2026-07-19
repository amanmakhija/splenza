package com.splitwise.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.dto.expense.CreateExpenseRequest;
import com.splitwise.app.dto.expense.ExpenseParticipantInput;
import com.splitwise.app.dto.expense.ExpenseResponse;
import com.splitwise.app.dto.expense.UpdateExpenseRequest;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.security.AppUserDetailsService;
import com.splitwise.app.security.JwtAuthenticationEntryPoint;
import com.splitwise.app.security.JwtAuthenticationFilter;
import com.splitwise.app.security.JwtService;
import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.config.SecurityConfig;
import com.splitwise.app.service.ExpenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(
        controllers = ExpenseController.class,
        excludeFilters = {
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = SecurityConfig.class),
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = RateLimitFilter.class)
        }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private String asJson(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    private CreateExpenseRequest createRequest() {

        UUID paidBy = UUID.randomUUID();

        ExpenseParticipantInput participant = new ExpenseParticipantInput();
        participant.setUserId(paidBy);
        participant.setAmount(BigDecimal.valueOf(500));

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setTitle("Dinner");
        request.setAmount(BigDecimal.valueOf(500));
        request.setCurrency("INR");
        request.setExpenseDate(LocalDate.now());
        request.setPaidBy(paidBy);
        request.setSplitType(Expense.SplitType.EQUAL);
        request.setParticipants(List.of(participant));

        return request;
    }

    private ExpenseResponse expenseResponse() {

        return ExpenseResponse.builder()
                .id(UUID.randomUUID())
                .title("Dinner")
                .amount(BigDecimal.valueOf(500))
                .currency("INR")
                .splitType("EQUAL")
                .build();
    }

    @Nested
    @DisplayName("Create Expense")
    class CreateExpenseTests {

        @Test
        @DisplayName("Should create expense successfully")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldCreateExpenseSuccessfully() throws Exception {

            CreateExpenseRequest request = createRequest();
            ExpenseResponse response = expenseResponse();

            when(expenseService.create(any(), any(CreateExpenseRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(response.getId().toString()))
                    .andExpect(jsonPath("$.title").value("Dinner"))
                    .andExpect(jsonPath("$.amount").value(500))
                    .andExpect(jsonPath("$.currency").value("INR"))
                    .andExpect(jsonPath("$.splitType").value("EQUAL"));

            verify(expenseService)
                    .create(any(), eq(request));
        }

        @Test
        @DisplayName("Should return bad request when request is invalid")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnBadRequestWhenRequestIsInvalid() throws Exception {

            CreateExpenseRequest request = new CreateExpenseRequest();

            mockMvc.perform(post("/api/v1/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(expenseService);
        }

        @Test
        @DisplayName("Should return bad request when title is blank")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnBadRequestWhenTitleIsBlank() throws Exception {

            CreateExpenseRequest request = createRequest();
            request.setTitle("");

            mockMvc.perform(post("/api/v1/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(expenseService);
        }

        @Test
        @DisplayName("Should return bad request when amount is missing")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnBadRequestWhenAmountIsMissing() throws Exception {

            CreateExpenseRequest request = createRequest();
            request.setAmount(null);

            mockMvc.perform(post("/api/v1/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(expenseService);
        }

        @Test
        @DisplayName("Should return bad request when participant list is empty")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnBadRequestWhenParticipantsAreEmpty() throws Exception {

            CreateExpenseRequest request = createRequest();
            request.setParticipants(List.of());

            mockMvc.perform(post("/api/v1/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(expenseService);
        }

        @Test
        @DisplayName("Should return not found when group does not exist")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnNotFoundWhenGroupDoesNotExist() throws Exception {

            CreateExpenseRequest request = createRequest();

            when(expenseService.create(any(), any(CreateExpenseRequest.class)))
                    .thenThrow(ApiException.notFound("Group not found"));

            mockMvc.perform(post("/api/v1/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isNotFound());

            verify(expenseService)
                    .create(any(), eq(request));
        }

        @Test
        @DisplayName("Should return forbidden when user is not allowed")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnForbiddenWhenUserHasNoAccess() throws Exception {

            CreateExpenseRequest request = createRequest();

            when(expenseService.create(any(), any(CreateExpenseRequest.class)))
                    .thenThrow(ApiException.forbidden("Not allowed"));

            mockMvc.perform(post("/api/v1/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isForbidden());

            verify(expenseService)
                    .create(any(), eq(request));
        }
    }

    @Nested
    @DisplayName("Update Expense")
    class UpdateExpenseTests {

        @Test
        @DisplayName("Should update expense successfully")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldUpdateExpenseSuccessfully() throws Exception {

            UUID expenseId = UUID.randomUUID();

            UpdateExpenseRequest request = updateRequest();
            ExpenseResponse response = expenseResponse();

            when(expenseService.update(any(), eq(expenseId), any()))
                    .thenReturn(response);

            mockMvc.perform(put("/api/v1/expenses/{expenseId}", expenseId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(response.getId().toString()))
                    .andExpect(jsonPath("$.title").value("Dinner"))
                    .andExpect(jsonPath("$.amount").value(500));

            verify(expenseService)
                    .update(any(), eq(expenseId), eq(request));
        }

        @Test
        @DisplayName("Should return bad request when update request is invalid")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnBadRequestWhenUpdateRequestIsInvalid() throws Exception {

            UUID expenseId = UUID.randomUUID();

            UpdateExpenseRequest request = new UpdateExpenseRequest();

            mockMvc.perform(put("/api/v1/expenses/{expenseId}", expenseId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(expenseService);
        }

        @Test
        @DisplayName("Should return not found when expense does not exist")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnNotFoundWhenExpenseDoesNotExist() throws Exception {

            UUID expenseId = UUID.randomUUID();

            UpdateExpenseRequest request = updateRequest();

            when(expenseService.update(any(), eq(expenseId), any()))
                    .thenThrow(ApiException.notFound("Expense not found"));

            mockMvc.perform(put("/api/v1/expenses/{expenseId}", expenseId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isNotFound());

            verify(expenseService)
                    .update(any(), eq(expenseId), eq(request));
        }

        @Test
        @DisplayName("Should return forbidden when user cannot update expense")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnForbiddenWhenUserCannotUpdateExpense() throws Exception {

            UUID expenseId = UUID.randomUUID();

            UpdateExpenseRequest request = updateRequest();

            when(expenseService.update(any(), eq(expenseId), any()))
                    .thenThrow(ApiException.forbidden("Not allowed"));

            mockMvc.perform(put("/api/v1/expenses/{expenseId}", expenseId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isForbidden());

            verify(expenseService)
                    .update(any(), eq(expenseId), eq(request));
        }
    }

    @Nested
    @DisplayName("Delete Expense")
    class DeleteExpenseTests {

        @Test
        @DisplayName("Should delete expense successfully")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldDeleteExpenseSuccessfully() throws Exception {

            UUID expenseId = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/expenses/{expenseId}", expenseId))
                    .andExpect(status().isNoContent());

            verify(expenseService)
                    .delete(any(), eq(expenseId));
        }

        @Test
        @DisplayName("Should return not found when deleting unknown expense")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnNotFoundWhenDeletingUnknownExpense() throws Exception {

            UUID expenseId = UUID.randomUUID();

            doThrow(ApiException.notFound("Expense not found"))
                    .when(expenseService)
                    .delete(any(), eq(expenseId));

            mockMvc.perform(delete("/api/v1/expenses/{expenseId}", expenseId))
                    .andExpect(status().isNotFound());

            verify(expenseService)
                    .delete(any(), eq(expenseId));
        }

        @Test
        @DisplayName("Should return forbidden when user cannot delete expense")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnForbiddenWhenUserCannotDeleteExpense() throws Exception {

            UUID expenseId = UUID.randomUUID();

            doThrow(ApiException.forbidden("Not allowed"))
                    .when(expenseService)
                    .delete(any(), eq(expenseId));

            mockMvc.perform(delete("/api/v1/expenses/{expenseId}", expenseId))
                    .andExpect(status().isForbidden());

            verify(expenseService)
                    .delete(any(), eq(expenseId));
        }

    }

    @Nested
    @DisplayName("Duplicate Expense")
    class DuplicateExpenseTests {

        @Test
        @DisplayName("Should duplicate expense successfully")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldDuplicateExpenseSuccessfully() throws Exception {

            UUID expenseId = UUID.randomUUID();
            ExpenseResponse response = expenseResponse();

            when(expenseService.duplicate(any(), eq(expenseId)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/expenses/{expenseId}/duplicate", expenseId))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(response.getId().toString()))
                    .andExpect(jsonPath("$.title").value("Dinner"))
                    .andExpect(jsonPath("$.amount").value(500));

            verify(expenseService)
                    .duplicate(any(), eq(expenseId));
        }

        @Test
        @DisplayName("Should return not found when expense does not exist")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnNotFoundWhenExpenseDoesNotExist() throws Exception {

            UUID expenseId = UUID.randomUUID();

            when(expenseService.duplicate(any(), eq(expenseId)))
                    .thenThrow(ApiException.notFound("Expense not found"));

            mockMvc.perform(post("/api/v1/expenses/{expenseId}/duplicate", expenseId))
                    .andExpect(status().isNotFound());

            verify(expenseService)
                    .duplicate(any(), eq(expenseId));
        }

        @Test
        @DisplayName("Should return forbidden when user cannot duplicate expense")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnForbiddenWhenUserCannotDuplicateExpense() throws Exception {

            UUID expenseId = UUID.randomUUID();

            when(expenseService.duplicate(any(), eq(expenseId)))
                    .thenThrow(ApiException.forbidden("Not allowed"));

            mockMvc.perform(post("/api/v1/expenses/{expenseId}/duplicate", expenseId))
                    .andExpect(status().isForbidden());

            verify(expenseService)
                    .duplicate(any(), eq(expenseId));
        }
    }

    @Nested
    @DisplayName("Get Expense")
    class GetExpenseTests {

        @Test
        @DisplayName("Should return expense by id")
        void shouldReturnExpenseById() throws Exception {

            UUID expenseId = UUID.randomUUID();
            ExpenseResponse response = expenseResponse();

            when(expenseService.getById(expenseId))
                    .thenReturn(response);

            mockMvc.perform(get("/api/v1/expenses/{expenseId}", expenseId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(response.getId().toString()))
                    .andExpect(jsonPath("$.title").value("Dinner"))
                    .andExpect(jsonPath("$.currency").value("INR"));

            verify(expenseService)
                    .getById(expenseId);
        }

        @Test
        @DisplayName("Should return not found when expense does not exist")
        void shouldReturnNotFoundWhenExpenseDoesNotExist() throws Exception {

            UUID expenseId = UUID.randomUUID();

            when(expenseService.getById(expenseId))
                    .thenThrow(ApiException.notFound("Expense not found"));

            mockMvc.perform(get("/api/v1/expenses/{expenseId}", expenseId))
                    .andExpect(status().isNotFound());

            verify(expenseService)
                    .getById(expenseId);
        }
    }

    @Nested
    @DisplayName("Group Expenses")
    class GroupExpenseTests {

        @Test
        @DisplayName("Should return group expenses")
        void shouldReturnGroupExpenses() throws Exception {

            UUID groupId = UUID.randomUUID();

            PageResponse<ExpenseResponse> page = PageResponse.<ExpenseResponse>builder()
                    .content(List.of(expenseResponse()))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .last(true)
                    .build();

            when(expenseService.listForGroupPaged(eq(groupId), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/expenses/group/{groupId}", groupId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20));

            verify(expenseService)
                    .listForGroupPaged(eq(groupId), any());
        }

        @Test
        @DisplayName("Should return empty page when group has no expenses")
        void shouldReturnEmptyGroupExpenses() throws Exception {

            UUID groupId = UUID.randomUUID();

            PageResponse<ExpenseResponse> page = PageResponse.<ExpenseResponse>builder()
                    .content(List.of())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();

            when(expenseService.listForGroupPaged(eq(groupId), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/expenses/group/{groupId}", groupId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));

            verify(expenseService)
                    .listForGroupPaged(eq(groupId), any());
        }
    }

    @Nested
    @DisplayName("My Expenses")
    class MyExpenseTests {

        @Test
        @DisplayName("Should return current user's expenses")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnMyExpenses() throws Exception {

            PageResponse<ExpenseResponse> page = PageResponse.<ExpenseResponse>builder()
                    .content(List.of(expenseResponse()))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .last(true)
                    .build();

            when(expenseService.listForUserPaged(any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/expenses/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page").value(0));

            verify(expenseService)
                    .listForUserPaged(any(), any());
        }

        @Test
        @DisplayName("Should return empty page when user has no expenses")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnEmptyMyExpenses() throws Exception {

            PageResponse<ExpenseResponse> page = PageResponse.<ExpenseResponse>builder()
                    .content(List.of())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();

            when(expenseService.listForUserPaged(any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/expenses/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));

            verify(expenseService)
                    .listForUserPaged(any(), any());
        }
    }

    @Nested
    @DisplayName("Friend Expenses")
    class FriendExpenseTests {

        @Test
        @DisplayName("Should return expenses with friend")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnFriendExpenses() throws Exception {

            UUID friendId = UUID.randomUUID();

            PageResponse<ExpenseResponse> page = PageResponse.<ExpenseResponse>builder()
                    .content(List.of(expenseResponse()))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .last(true)
                    .build();

            when(expenseService.listDirectWithFriendPaged(any(), eq(friendId), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/expenses/friend/{friendId}", friendId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));

            verify(expenseService)
                    .listDirectWithFriendPaged(any(), eq(friendId), any());
        }

        @Test
        @DisplayName("Should return empty friend expenses")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnEmptyFriendExpenses() throws Exception {

            UUID friendId = UUID.randomUUID();

            PageResponse<ExpenseResponse> page = PageResponse.<ExpenseResponse>builder()
                    .content(List.of())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();

            when(expenseService.listDirectWithFriendPaged(any(), eq(friendId), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/expenses/friend/{friendId}", friendId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));

            verify(expenseService)
                    .listDirectWithFriendPaged(any(), eq(friendId), any());
        }
    }

    @Nested
    @DisplayName("Search Expenses")
    class SearchExpenseTests {

        @Test
        @DisplayName("Should search expenses")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldSearchExpenses() throws Exception {

            PageResponse<ExpenseResponse> page = PageResponse.<ExpenseResponse>builder()
                    .content(List.of(expenseResponse()))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .last(true)
                    .build();

            when(expenseService.searchPaged(any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/expenses/search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));

            verify(expenseService)
                    .searchPaged(any(), any(), any());
        }

        @Test
        @DisplayName("Should search expenses with filters")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldSearchExpensesWithFilters() throws Exception {

            PageResponse<ExpenseResponse> page = PageResponse.<ExpenseResponse>builder()
                    .content(List.of(expenseResponse()))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .last(true)
                    .build();

            when(expenseService.searchPaged(any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/expenses/search")
                    .param("query", "Dinner")
                    .param("sort", "LATEST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));

            verify(expenseService)
                    .searchPaged(any(), any(), any());
        }

        @Test
        @DisplayName("Should return empty search result")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnEmptySearchResult() throws Exception {

            PageResponse<ExpenseResponse> page = PageResponse.<ExpenseResponse>builder()
                    .content(List.of())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();

            when(expenseService.searchPaged(any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/expenses/search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));

            verify(expenseService)
                    .searchPaged(any(), any(), any());
        }
    }

    private UpdateExpenseRequest updateRequest() {
        UpdateExpenseRequest request = new UpdateExpenseRequest();

        UUID paidBy = UUID.randomUUID();

        ExpenseParticipantInput participant = new ExpenseParticipantInput();
        participant.setUserId(paidBy);
        participant.setAmount(BigDecimal.valueOf(500));

        request.setTitle("Dinner");
        request.setAmount(BigDecimal.valueOf(500));
        request.setCurrency("INR");
        request.setExpenseDate(LocalDate.now());
        request.setPaidBy(paidBy);
        request.setSplitType(Expense.SplitType.EQUAL);
        request.setParticipants(List.of(participant));

        return request;
    }
}
