package com.splitwise.app.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "The current authenticated user's full profile")
public class UserProfileResponse {

    @Schema(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Display name", example = "Priya Sharma")
    private String name;

    @Schema(description = "Email address", example = "priya@example.com")
    private String email;

    @Schema(description = "Phone number, or null if not set", example = "+919876543210")
    private String phoneNumber;

    @Schema(description = "URL of the profile picture, or null if not set")
    private String profilePictureUrl;

    @Schema(description = "UPI VPA for receiving settlements, or null if not set", example = "priya@okhdfcbank")
    private String upiId;
}
