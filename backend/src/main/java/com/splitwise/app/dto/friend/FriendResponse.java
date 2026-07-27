package com.splitwise.app.dto.friend;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Schema(description = "A confirmed friend - an existing accepted connection between two users")
@Data
@Builder
@AllArgsConstructor
public class FriendResponse {

    @Schema(description = "ID of the friend")
    private UUID userId;

    @Schema(description = "Display name of the friend", example = "Priya")
    private String name;

    @Schema(description = "Email address of the friend", example = "priya@example.com")
    private String email;

    @Schema(description = "Phone number of the friend, if they have one on file", example = "+919876543210")
    private String phoneNumber;

    @Schema(description = "URL of the friend's profile picture, or null if not set")
    private String profilePictureUrl;
}