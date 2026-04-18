package com.leadrush.auth.entity;

/**
 * How a user signed up or last logged in.
 *
 * Used for:
 *   - primaryProvider: how they first created their account
 *   - lastUsedProvider: what they used most recently (for "Last used" badge)
 */
public enum AuthProvider {
    LOCAL,      // Email + password
    GOOGLE,     // Google OAuth
    GITHUB      // GitHub OAuth
}
