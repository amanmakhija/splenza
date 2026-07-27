package com.splitwise.app.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Schema(description = "Details of a group member")
@Data
@Builder
@AllArgsConstructor
public class GroupMemberResponse {

    @Schema(description = "Unique ID of the user", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID userId;

    @Schema(description = "Full name of the member", example = "Priya Sharma")
    private String name;

    @Schema(description = "Email address of the member", example = "priya@example.com")
    private String email;

    @Schema(description = "Profile picture URL of the member", example = "https://example.com/images/profile.jpg")
    private String profilePictureUrl;

    @Schema(description = "Role of the user in the group", example = "MEMBER")
    private String role;
}
