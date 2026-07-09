package com.splitwise.app.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class FriendResponse {

    private UUID userId;
    private String name;
    private String email;
    private String phoneNumber;
    private String profilePictureUrl;
}
