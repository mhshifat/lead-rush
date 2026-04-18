package com.leadrush.common;

import com.leadrush.security.TenantContext;
import jakarta.persistence.PrePersist;

/** Stamps workspaceId from TenantContext onto new entities before insert. */
public class TenantEntityListener {

    @PrePersist
    public void setWorkspaceId(TenantEntity entity) {
        if (entity.getWorkspaceId() == null) {
            entity.setWorkspaceId(TenantContext.getWorkspaceId());
        }
    }
}
