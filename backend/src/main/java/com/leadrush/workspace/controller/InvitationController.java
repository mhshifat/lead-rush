package com.leadrush.workspace.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.workspace.dto.InvitationResponse;
import com.leadrush.workspace.dto.WorkspaceResponse;
import com.leadrush.workspace.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Invitation accept + preview endpoints.
 *
 * Preview is PUBLIC so an unauthenticated recipient can see who invited them
 * to which workspace before they sign in or register.
 *
 * Accept is AUTHENTICATED — the caller must be logged in with the invited email.
 */
@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final WorkspaceService workspaceService;

    /** Public preview — mounted under /api/v1/public so SecurityConfig lets it through. */
    @GetMapping("/api/v1/public/invitations/{token}")
    public ApiResponse<InvitationResponse> preview(@PathVariable String token) {
        return ApiResponse.success(workspaceService.previewInvitation(token));
    }

    @PostMapping("/api/v1/invitations/{token}/accept")
    public ApiResponse<WorkspaceResponse> accept(@PathVariable String token) {
        return ApiResponse.success(workspaceService.acceptInvitation(token));
    }
}
