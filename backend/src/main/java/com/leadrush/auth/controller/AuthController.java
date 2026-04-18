package com.leadrush.auth.controller;

import com.leadrush.auth.dto.*;
import com.leadrush.auth.service.AuthService;
import com.leadrush.common.ApiResponse;
import com.leadrush.config.LeadRushProperties;
import com.leadrush.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Auth REST controller — registration, login, activation, token refresh.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LeadRushProperties properties;

    /**
     * POST /api/v1/auth/register
     *
     * Creates a new user account, then sends an activation email via SMTP.
     * Does NOT log the user in.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created. Check your email to activate."));
    }

    /**
     * POST /api/v1/auth/login
     *
     * Authenticates with email/password.
     * Returns JWT tokens + user info + workspaces.
     * Throws ACTIVATION_REQUIRED if the account hasn't been activated.
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    /**
     * GET /api/v1/auth/verify-email?token=xxx
     *
     * Activates the user's account. Called when they click the email link.
     * Redirects to the frontend login page with ?activated=true.
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header("Location", properties.getFrontendUrl() + "/auth/login?activated=true")
                .build();
    }

    /**
     * POST /api/v1/auth/resend-activation
     *
     * Resends the activation email. Has a 5-minute cooldown.
     */
    @PostMapping("/resend-activation")
    public ApiResponse<String> resendActivation(@Valid @RequestBody ResendActivationRequest request) {
        authService.resendActivation(request.getEmail());
        return ApiResponse.success("Activation email sent");
    }

    /**
     * POST /api/v1/auth/refresh
     *
     * Exchange a refresh token for a new access token + new refresh token.
     * The old refresh token is revoked (token rotation).
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request.getRefreshToken());
        return ApiResponse.success(response);
    }

    /**
     * POST /api/v1/auth/switch-workspace
     *
     * Re-issue the access token scoped to a different workspace the user belongs to.
     * Response contains a NEW accessToken (with the new workspaceId + role).
     * The refreshToken is NOT rotated — the client keeps using the existing one.
     */
    @PostMapping("/switch-workspace")
    public ApiResponse<AuthResponse> switchWorkspace(@Valid @RequestBody SwitchWorkspaceRequest request) {
        AuthResponse response = authService.switchWorkspace(
                TenantContext.getUserId(), request.getWorkspaceId());
        return ApiResponse.success(response);
    }
}
