package com.splitwise.app.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Standard API error response structure")
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Schema(description = "Timestamp when the error occurred")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "HTTP status reason phrase", example = "Bad Request")
    private String error;

    @Schema(description = "Human-readable error message", example = "Invalid request parameters")
    private String message;

    @Schema(description = "API endpoint path where the error occurred", example = "/api/v1/groups")
    private String path;

    @Schema(description = "Map of field names to validation error messages", example = "{\"email\": \"Email must be valid\"}")
    private Map<String, String> fieldErrors;

    @Schema(description = "Specific application business error code, if applicable", example = "EMAIL_NOT_VERIFIED")
    private ErrorCode code;
}
