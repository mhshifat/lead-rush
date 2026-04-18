package com.leadrush.auth.entity;

/**
 * Platform-level role (NOT workspace role).
 *
 * USER = normal user
 * SUPER_ADMIN = platform administrator (can manage all workspaces, impersonate users)
 */
public enum SystemRole {
    USER,
    SUPER_ADMIN
}
