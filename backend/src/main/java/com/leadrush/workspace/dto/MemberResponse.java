package com.leadrush.workspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemberResponse {

    private UUID membershipId;
    private UUID userId;
    private String name;
    private String email;
    private String avatarUrl;
    private String role;
    private LocalDateTime joinedAt;
}
