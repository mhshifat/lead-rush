package com.leadrush.auth.entity;

import com.leadrush.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Global entity (not tenant-scoped): one user can belong to multiple workspaces.
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {})
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    // Null for OAuth-only users; can be set later via POST /auth/set-password.
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    private String avatarUrl;

    @Column(unique = true)
    private String googleId;

    @Column(unique = true)
    private String githubId;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_provider", nullable = false)
    @Builder.Default
    private AuthProvider primaryProvider = AuthProvider.LOCAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_used_provider")
    private AuthProvider lastUsedProvider;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "last_activation_email_sent_at")
    private LocalDateTime lastActivationEmailSentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false)
    @Builder.Default
    private SystemRole systemRole = SystemRole.USER;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public boolean hasPassword() {
        return passwordHash != null;
    }
}
