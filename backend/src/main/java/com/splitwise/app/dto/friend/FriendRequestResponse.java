package com.splitwise.app.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class FriendRequestResponse {
    private UUID id;
    private UUID senderId;
    private String senderName;
    private String senderEmail;
    private String status;
    private Instant createdAt;
}
