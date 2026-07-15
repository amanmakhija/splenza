package com.splitwise.app.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public ApiException(
            String message,
            HttpStatus status,
            ErrorCode code
    ) {
        super(message);
        this.status = status;
        this.code = code;
    }

    // Default constructor for exceptions without an explicit error code
    public ApiException(
            String message,
            HttpStatus status
    ) {
        this(message, status, null);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(
                message,
                HttpStatus.NOT_FOUND
        );
    }

    public static ApiException badRequest(String message) {
        return new ApiException(
                message,
                HttpStatus.BAD_REQUEST
        );
    }

    public static ApiException conflict(String message) {
        return new ApiException(
                message,
                HttpStatus.CONFLICT
        );
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(
                message,
                HttpStatus.UNAUTHORIZED
        );
    }

    public static ApiException forbidden(String message) {
        return new ApiException(
                message,
                HttpStatus.FORBIDDEN
        );
    }

    public static ApiException emailNotVerified() {
        return new ApiException(
                "Please verify your email.",
                HttpStatus.FORBIDDEN,
                ErrorCode.EMAIL_NOT_VERIFIED
        );
    }

    public static ApiException verificationExpired() {
        return new ApiException(
                "Your verification request has expired. Please sign up again.",
                HttpStatus.BAD_REQUEST,
                ErrorCode.VERIFICATION_EXPIRED
        );
    }
}
