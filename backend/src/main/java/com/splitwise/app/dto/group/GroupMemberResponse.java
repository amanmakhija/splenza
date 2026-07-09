package com.splitwise.app.dto.group;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class GroupMemberResponse {
    private UUID userId;
    private String name;
    private String email;
    private String profilePictureUrl;
    private String role;
}
