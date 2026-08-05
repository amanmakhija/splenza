package com.splitwise.app.exception;

import java.util.Map;

/**
 * Thrown when manual (non-Bean-Validation) field-level validation fails, e.g.
 * in service-layer code that can't use @Valid/@Pattern directly. Handled by
 * GlobalExceptionHandler to produce the same field-errors ErrorResponse shape
 * as MethodArgumentNotValidException, so API consumers see a consistent error
 * format regardless of which validation path caught the problem.
 */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(Map<String, String> fieldErrors) {
        super("One or more fields are invalid");
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
