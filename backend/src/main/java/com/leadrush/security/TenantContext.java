package com.leadrush.security;

import java.util.UUID;

/** Per-request tenant context (workspace + user) held in ThreadLocal. */
public final class TenantContext {

    private static final ThreadLocal<UUID> currentWorkspaceId = new ThreadLocal<>();
    private static final ThreadLocal<UUID> currentUserId = new ThreadLocal<>();

    private TenantContext() {}

    public static UUID getWorkspaceId() {
        return currentWorkspaceId.get();
    }

    public static UUID getUserId() {
        return currentUserId.get();
    }

    public static void set(UUID workspaceId, UUID userId) {
        currentWorkspaceId.set(workspaceId);
        currentUserId.set(userId);
    }

    // MUST be called after each request to avoid leaking across pooled threads.
    public static void clear() {
        currentWorkspaceId.remove();
        currentUserId.remove();
    }
}
