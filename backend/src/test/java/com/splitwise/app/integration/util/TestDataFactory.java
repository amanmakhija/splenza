package com.splitwise.app.integration.util;

import com.splitwise.app.dto.auth.ChangePasswordRequest;
import com.splitwise.app.dto.auth.ChangePendingEmailRequest;
import com.splitwise.app.dto.auth.ForgotPasswordRequest;
import com.splitwise.app.dto.auth.GoogleLoginRequest;
import com.splitwise.app.dto.auth.LoginRequest;
import com.splitwise.app.dto.auth.RefreshTokenRequest;
import com.splitwise.app.dto.auth.ResendVerificationRequest;
import com.splitwise.app.dto.auth.ResetPasswordRequest;
import com.splitwise.app.dto.auth.SetPasswordRequest;
import com.splitwise.app.dto.auth.SignupRequest;
import com.splitwise.app.dto.expense.CreateExpenseRequest;
import com.splitwise.app.dto.expense.ExpenseParticipantInput;
import com.splitwise.app.dto.group.CreateGroupRequest;
import com.splitwise.app.dto.group.UpdateGroupRequest;
import com.splitwise.app.entity.Expense;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class TestDataFactory {

    private TestDataFactory() {
    }

    public static final String DEFAULT_EMAIL = "aman@test.com";
    public static final String DEFAULT_PASSWORD = "Password123";

    public static SignupRequest signupRequest() {

        SignupRequest request = new SignupRequest();

        request.setName("Aman");
        request.setEmail(DEFAULT_EMAIL);
        request.setPhoneNumber("9876543210");
        request.setPassword(DEFAULT_PASSWORD);

        return request;
    }

    public static LoginRequest loginRequest() {
        return loginRequest(DEFAULT_EMAIL, DEFAULT_PASSWORD);
    }

    public static LoginRequest loginRequest(String email, String password) {

        LoginRequest request = new LoginRequest();

        request.setEmail(email);
        request.setPassword(password);

        return request;
    }

    public static RefreshTokenRequest refreshTokenRequest(String refreshToken) {

        RefreshTokenRequest request = new RefreshTokenRequest();

        request.setRefreshToken(refreshToken);

        return request;
    }

    public static ForgotPasswordRequest forgotPasswordRequest(String email) {

        ForgotPasswordRequest request = new ForgotPasswordRequest();

        request.setEmail(email);

        return request;
    }

    public static ResetPasswordRequest resetPasswordRequest(String token, String newPassword) {

        ResetPasswordRequest request = new ResetPasswordRequest();

        request.setToken(token);
        request.setNewPassword(newPassword);

        return request;
    }

    public static ChangePasswordRequest changePasswordRequest(String currentPassword, String newPassword) {

        ChangePasswordRequest request = new ChangePasswordRequest();

        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);

        return request;
    }

    public static GoogleLoginRequest googleLoginRequest(String idToken) {

        GoogleLoginRequest request = new GoogleLoginRequest();

        request.setIdToken(idToken);

        return request;
    }

    public static ChangePendingEmailRequest changePendingEmailRequest(String oldEmail, String newEmail) {

        ChangePendingEmailRequest request = new ChangePendingEmailRequest();

        request.setOldEmail(oldEmail);
        request.setNewEmail(newEmail);

        return request;
    }

    public static ResendVerificationRequest resendVerificationRequest(String email) {

        ResendVerificationRequest request = new ResendVerificationRequest();

        request.setEmail(email);

        return request;
    }

    public static SetPasswordRequest setPasswordRequest(String password) {

        SetPasswordRequest request = new SetPasswordRequest();

        request.setPassword(password);

        return request;
    }

    public static CreateGroupRequest createGroupRequest(String name) {
        return createGroupRequest(name, null);
    }

    public static CreateGroupRequest createGroupRequest(String name, List<UUID> memberIds) {

        CreateGroupRequest request = new CreateGroupRequest();

        request.setName(name);
        request.setDescription("Test group description");
        request.setMemberIds(memberIds);

        return request;
    }

    public static UpdateGroupRequest updateGroupRequest(String name) {

        UpdateGroupRequest request = new UpdateGroupRequest();

        request.setName(name);
        request.setDescription("Updated description");

        return request;
    }

    public static ExpenseParticipantInput participant(UUID userId) {
        ExpenseParticipantInput input = new ExpenseParticipantInput();
        input.setUserId(userId);
        return input;
    }

    public static ExpenseParticipantInput exactParticipant(UUID userId, BigDecimal amount) {
        ExpenseParticipantInput input = new ExpenseParticipantInput();
        input.setUserId(userId);
        input.setAmount(amount);
        return input;
    }

    public static ExpenseParticipantInput percentageParticipant(UUID userId, BigDecimal percentage) {
        ExpenseParticipantInput input = new ExpenseParticipantInput();
        input.setUserId(userId);
        input.setPercentage(percentage);
        return input;
    }

    public static ExpenseParticipantInput sharesParticipant(UUID userId, int shares) {
        ExpenseParticipantInput input = new ExpenseParticipantInput();
        input.setUserId(userId);
        input.setShares(shares);
        return input;
    }

    public static CreateExpenseRequest expenseRequest(
            UUID groupId,
            UUID paidBy,
            BigDecimal amount,
            Expense.SplitType splitType,
            List<ExpenseParticipantInput> participants) {

        CreateExpenseRequest request = new CreateExpenseRequest();

        request.setGroupId(groupId);
        request.setTitle("Dinner");
        request.setAmount(amount);
        request.setCurrency("INR");
        request.setExpenseDate(LocalDate.now());
        request.setPaidBy(paidBy);
        request.setSplitType(splitType);
        request.setParticipants(participants);

        return request;
    }
}