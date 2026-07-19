package com.splitwise.app.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();

        request = mock(HttpServletRequest.class);

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    @DisplayName("Handle ApiException")
    void handleApiException_shouldReturnExpectedResponse() {

        ApiException exception = ApiException.notFound("Group not found");

        ResponseEntity<ErrorResponse> response
                = handler.handleApiException(exception, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        ErrorResponse body = response.getBody();

        assertNotNull(body);
        assertEquals(404, body.getStatus());
        assertEquals("Not Found", body.getError());
        assertEquals("Group not found", body.getMessage());
        assertEquals("/api/test", body.getPath());
        assertNotNull(body.getTimestamp());
    }

    @Test
    @DisplayName("Handle MethodArgumentNotValidException")
    void handleValidation_shouldReturnFieldErrors() {

        BeanPropertyBindingResult bindingResult
                = new BeanPropertyBindingResult(new Object(), "request");

        bindingResult.addError(
                new FieldError(
                        "request",
                        "email",
                        "Email is required"
                ));

        bindingResult.addError(
                new FieldError(
                        "request",
                        "password",
                        "Password is required"
                ));

        MethodArgumentNotValidException exception
                = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response
                = handler.handleValidation(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        ErrorResponse body = response.getBody();

        assertNotNull(body);
        assertEquals(400, body.getStatus());
        assertEquals("Validation Failed", body.getError());
        assertEquals("One or more fields are invalid", body.getMessage());
        assertEquals("/api/test", body.getPath());

        assertEquals(
                "Email is required",
                body.getFieldErrors().get("email")
        );

        assertEquals(
                "Password is required",
                body.getFieldErrors().get("password")
        );
    }

    @Test
    @DisplayName("Handle ConstraintViolationException")
    void handleConstraintViolation_shouldReturnFieldErrors() {

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);

        Path path = mock(Path.class);

        when(path.toString()).thenReturn("createGroup.name");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");

        ConstraintViolationException exception
                = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response
                = handler.handleConstraintViolation(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        ErrorResponse body = response.getBody();

        assertNotNull(body);
        assertEquals(400, body.getStatus());
        assertEquals("Validation Failed", body.getError());
        assertEquals("One or more parameters are invalid", body.getMessage());
        assertEquals("/api/test", body.getPath());

        assertEquals(
                "must not be blank",
                body.getFieldErrors().get("name")
        );
    }

    @Test
    @DisplayName("Handle BadCredentialsException")
    void handleBadCredentials_shouldReturnUnauthorized() {

        BadCredentialsException exception
                = new BadCredentialsException("Bad credentials");

        ResponseEntity<ErrorResponse> response
                = handler.handleBadCredentials(exception, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        ErrorResponse body = response.getBody();

        assertNotNull(body);
        assertEquals(401, body.getStatus());
        assertEquals("Unauthorized", body.getError());
        assertEquals("Invalid email or password", body.getMessage());
        assertEquals("/api/test", body.getPath());
        assertNull(body.getCode());
        assertNull(body.getFieldErrors());
    }

    @Test
    @DisplayName("Handle generic exception")
    void handleGeneric_shouldReturnInternalServerError() {

        Exception exception = new RuntimeException("Unexpected error");

        ResponseEntity<ErrorResponse> response
                = handler.handleGeneric(exception, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        ErrorResponse body = response.getBody();

        assertNotNull(body);
        assertEquals(500, body.getStatus());
        assertEquals("Internal Server Error", body.getError());
        assertEquals(
                "Something went wrong. Please try again later.",
                body.getMessage()
        );
        assertEquals("/api/test", body.getPath());
        assertNull(body.getCode());
        assertNull(body.getFieldErrors());
    }
}
