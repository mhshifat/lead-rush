package com.leadrush.auth.service;

import com.leadrush.auth.dto.*;
import com.leadrush.auth.entity.*;
import com.leadrush.auth.repository.*;
import com.leadrush.common.exception.*;
import com.leadrush.config.LeadRushProperties;
import com.leadrush.email.service.TransactionalEmailService;
import com.leadrush.security.JwtTokenProvider;
import com.leadrush.workspace.entity.Workspace;
import com.leadrush.workspace.entity.WorkspaceMembership;
import com.leadrush.workspace.entity.WorkspaceRole;
import com.leadrush.workspace.repository.WorkspaceMembershipRepository;
import com.leadrush.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LeadRushProperties properties;
    private final TransactionalEmailService transactionalEmailService;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .primaryProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .lastActivationEmailSentAt(LocalDateTime.now())
                .systemRole(SystemRole.USER)
                .build();
        user = userRepository.save(user);

        createDefaultWorkspace(user);

        String token = generateActivationToken(user.getId());
        transactionalEmailService.sendActivationEmail(user.getEmail(), user.getName(), token);

        log.info("User registered: {} (id: {})", user.getEmail(), user.getId());
    }

    /** Shared between email/password register + OAuth first-login flows. */
    private void createDefaultWorkspace(User user) {
        String slug = generateSlug(user.getName());
        Workspace workspace = Workspace.builder()
                .name(user.getName() + "'s Workspace")
                .slug(slug)
                .build();
        workspace = workspaceRepository.save(workspace);

        WorkspaceMembership membership = WorkspaceMembership.builder()
                .userId(user.getId())
                .workspaceId(workspace.getId())
                .role(WorkspaceRole.OWNER)
                .build();
        membershipRepository.save(membership);
    }

    // ─────────────────────────────────────────────────────────────────────
    // OAuth login — called by OAuth2LoginSuccessHandler once Spring Security
    // has finished the provider handshake. Does the create-or-link dance and
    // returns a fully-signed AuthResponse the handler can hand to the client.
    // ─────────────────────────────────────────────────────────────────────
    @Transactional
    public AuthResponse loginViaOAuth(AuthProvider provider, OAuthProfile profile) {
        if (profile.email() == null || profile.email().isBlank()) {
            throw new BusinessException(
                "Your " + provider.name().toLowerCase() + " account doesn't have a public email. "
                + "Grant email access on the consent screen and try again."
            );
        }

        User user = switch (provider) {
            case GOOGLE -> findOrCreateViaOAuth(provider, profile,
                    userRepository.findByGoogleId(profile.providerId()));
            case GITHUB -> findOrCreateViaOAuth(provider, profile,
                    userRepository.findByGithubId(profile.providerId()));
            default -> throw new BusinessException("Unsupported OAuth provider: " + provider);
        };

        user.setLastUsedProvider(provider);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    private User findOrCreateViaOAuth(AuthProvider provider, OAuthProfile profile,
                                      java.util.Optional<User> byProviderId) {
        // 1. Already linked? just return — happy path for returning users.
        if (byProviderId.isPresent()) return byProviderId.get();

        // 2. Account exists at this email? Link the provider to it. Google/GitHub
        //    have already verified the email, so we mark it verified too (useful
        //    when a LOCAL user signs in via Google before ever clicking their
        //    activation link — they get in immediately).
        java.util.Optional<User> byEmail = userRepository.findByEmail(profile.email());
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            linkProvider(existing, provider, profile.providerId());
            existing.setEmailVerified(true);
            if (existing.getAvatarUrl() == null && profile.avatarUrl() != null) {
                existing.setAvatarUrl(profile.avatarUrl());
            }
            return userRepository.save(existing);
        }

        // 3. Brand new user — create the account + their default workspace.
        User fresh = User.builder()
                .email(profile.email())
                .name(profile.name() != null ? profile.name() : profile.email())
                .avatarUrl(profile.avatarUrl())
                .primaryProvider(provider)
                .emailVerified(true)            // trusted — the IdP vouched for it
                .systemRole(SystemRole.USER)
                .build();
        linkProvider(fresh, provider, profile.providerId());
        fresh = userRepository.save(fresh);
        createDefaultWorkspace(fresh);
        return fresh;
    }

    private void linkProvider(User user, AuthProvider provider, String providerId) {
        switch (provider) {
            case GOOGLE -> user.setGoogleId(providerId);
            case GITHUB -> user.setGithubId(providerId);
            default -> { /* unreachable — validated upstream */ }
        }
    }

    /**
     * Neutral shape decoupled from Spring's OAuth2User — lets the handler feed
     * Google and GitHub attributes into one method without the service having
     * to know about Spring Security types.
     */
    public record OAuthProfile(
            String providerId,
            String email,
            String name,
            String avatarUrl
    ) {}

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("Invalid email or password"));

        if (!user.hasPassword()) {
            throw new BusinessException("This account uses " + user.getPrimaryProvider() + " sign-in. Please use that method.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid email or password");
        }

        if (!user.isEmailVerified()) {
            String sentAt = user.getLastActivationEmailSentAt() != null
                    ? user.getLastActivationEmailSentAt().toString()
                    : LocalDateTime.now().toString();
            throw new ActivationRequiredException(
                "Account not activated. Check your email.",
                sentAt
            );
        }

        user.setLastUsedProvider(AuthProvider.LOCAL);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        ActivationToken activationToken = activationTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("Invalid activation link"));

        if (activationToken.isExpired()) {
            throw new BusinessException("Activation link has expired. Please request a new one.");
        }

        if (activationToken.isUsed()) {
            throw new BusinessException("This activation link has already been used");
        }

        User user = userRepository.findById(activationToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", activationToken.getUserId()));

        user.setEmailVerified(true);
        userRepository.save(user);

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);

        log.info("User activated: {} (id: {})", user.getEmail(), user.getId());
    }

    @Transactional
    public void resendActivation(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("No account found with this email"));

        if (user.isEmailVerified()) {
            throw new BusinessException("Account is already activated");
        }

        if (user.getLastActivationEmailSentAt() != null) {
            LocalDateTime canResendAfter = user.getLastActivationEmailSentAt().plusMinutes(5);
            if (LocalDateTime.now().isBefore(canResendAfter)) {
                throw new BusinessException("Please wait before requesting another activation email");
            }
        }

        activationTokenRepository.deleteByUserIdAndUsedFalse(user.getId());
        String token = generateActivationToken(user.getId());

        user.setLastActivationEmailSentAt(LocalDateTime.now());
        userRepository.save(user);

        transactionalEmailService.sendActivationEmail(user.getEmail(), user.getName(), token);

        log.info("Activation email resent for: {}", email);
    }

    // Returns the signed-in user's profile + workspace memberships without issuing new tokens.
    // Used by the frontend to rehydrate state after a page refresh.
    @Transactional(readOnly = true)
    public AuthResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<WorkspaceMembership> memberships = membershipRepository.findByUserId(userId);
        List<AuthResponse.WorkspaceDto> workspaceDtos = memberships.stream()
                .map(m -> {
                    Workspace ws = workspaceRepository.findById(m.getWorkspaceId()).orElse(null);
                    if (ws == null) return null;
                    return AuthResponse.WorkspaceDto.builder()
                            .id(ws.getId()).name(ws.getName()).slug(ws.getSlug())
                            .logoUrl(ws.getLogoUrl()).role(m.getRole().name())
                            .build();
                })
                .filter(dto -> dto != null)
                .toList();

        return AuthResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId()).email(user.getEmail()).name(user.getName())
                        .avatarUrl(user.getAvatarUrl()).hasPassword(user.hasPassword())
                        .primaryProvider(user.getPrimaryProvider().name())
                        .lastUsedProvider(user.getLastUsedProvider() != null
                                ? user.getLastUsedProvider().name() : null)
                        .build())
                .workspaces(workspaceDtos)
                .build();
    }

    // Re-issues access token for a different workspace; refresh token is not rotated.
    @Transactional
    public AuthResponse switchWorkspace(UUID userId, UUID targetWorkspaceId) {
        WorkspaceMembership membership = membershipRepository
                .findByUserIdAndWorkspaceId(userId, targetWorkspaceId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of that workspace"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Workspace workspace = workspaceRepository.findById(targetWorkspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", targetWorkspaceId));

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), workspace.getId(), membership.getRole().name());

        List<WorkspaceMembership> memberships = membershipRepository.findByUserId(user.getId());
        List<AuthResponse.WorkspaceDto> workspaceDtos = memberships.stream()
                .map(m -> {
                    Workspace ws = workspaceRepository.findById(m.getWorkspaceId()).orElse(null);
                    if (ws == null) return null;
                    return AuthResponse.WorkspaceDto.builder()
                            .id(ws.getId()).name(ws.getName()).slug(ws.getSlug())
                            .logoUrl(ws.getLogoUrl()).role(m.getRole().name())
                            .build();
                })
                .filter(dto -> dto != null)
                .toList();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(null)
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId()).email(user.getEmail()).name(user.getName())
                        .avatarUrl(user.getAvatarUrl()).hasPassword(user.hasPassword())
                        .primaryProvider(user.getPrimaryProvider().name())
                        .lastUsedProvider(user.getLastUsedProvider() != null
                                ? user.getLastUsedProvider().name() : null)
                        .build())
                .workspaces(workspaceDtos)
                .build();
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenString) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!refreshToken.isValid()) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        // Token rotation: revoke the presented refresh token to prevent replay.
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", refreshToken.getUserId()));

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        List<WorkspaceMembership> memberships = membershipRepository.findByUserId(user.getId());

        if (memberships.isEmpty()) {
            throw new BusinessException("User has no workspaces");
        }

        WorkspaceMembership defaultMembership = memberships.getFirst();
        Workspace defaultWorkspace = workspaceRepository.findById(defaultMembership.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", defaultMembership.getWorkspaceId()));

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                defaultWorkspace.getId(),
                defaultMembership.getRole().name()
        );

        String refreshTokenString = jwtTokenProvider.generateRefreshToken(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .token(refreshTokenString)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(refreshToken);

        List<AuthResponse.WorkspaceDto> workspaceDtos = memberships.stream()
                .map(m -> {
                    Workspace ws = workspaceRepository.findById(m.getWorkspaceId()).orElse(null);
                    if (ws == null) return null;
                    return AuthResponse.WorkspaceDto.builder()
                            .id(ws.getId())
                            .name(ws.getName())
                            .slug(ws.getSlug())
                            .logoUrl(ws.getLogoUrl())
                            .role(m.getRole().name())
                            .build();
                })
                .filter(dto -> dto != null)
                .toList();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenString)
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .avatarUrl(user.getAvatarUrl())
                        .hasPassword(user.hasPassword())
                        .primaryProvider(user.getPrimaryProvider().name())
                        .lastUsedProvider(user.getLastUsedProvider() != null ? user.getLastUsedProvider().name() : null)
                        .build())
                .workspaces(workspaceDtos)
                .build();
    }

    private String generateActivationToken(UUID userId) {
        String token = UUID.randomUUID().toString();
        ActivationToken activationToken = ActivationToken.builder()
                .userId(userId)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        activationTokenRepository.save(activationToken);
        return token;
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        if (base.isEmpty()) base = "workspace";

        String slug = base;
        if (workspaceRepository.existsBySlug(slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 4);
        }
        return slug;
    }
}
