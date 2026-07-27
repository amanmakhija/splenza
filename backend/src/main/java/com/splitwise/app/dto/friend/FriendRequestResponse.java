package com.splitwise.app.dto.friend;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A friend request, either pending, accepted, or rejected")
@Data
@Builder
@AllArgsConstructor
public class FriendRequestResponse {

    @Schema(description = "Unique ID of the friend request")
    private UUID id;

    @Schema(description = "ID of the user who sent the request")
    private UUID senderId;

    @Schema(description = "Display name of the sender", example = "Aman")
    private String senderName;

    @Schema(description = "Email address of the sender", example = "aman@example.com")
    private String senderEmail;

    @Schema(description = "Current status of the request", example = "PENDING",
            allowableValues = {"PENDING", "ACCEPTED", "REJECTED"})
    private String status;

    @Schema(description = "Timestamp the request was sent")
    private Instant createdAt;
}