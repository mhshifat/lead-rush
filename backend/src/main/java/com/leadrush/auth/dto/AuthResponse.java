package com.leadrush.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response for successful login / token refresh.
 *
 * Contains: tokens + user info + list of workspaces the user belongs to.
 * The frontend stores the tokens and uses the workspace list for the workspace switcher.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private UserDto user;
    private List<WorkspaceDto> workspaces;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
        private UUID id;
        private String email;
        private String name;
        private String avatarUrl;
        private boolean hasPassword;
        private String primaryProvider;
        private String lastUsedProvider;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceDto {
        private UUID id;
        private String name;
        private String slug;
        private String logoUrl;
        private String role;        // the user's role in this workspace
    }
}
