package com.leadrush.common;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

/** Base entity for tenant-scoped tables. Adds workspace_id + Hibernate filter. */
@MappedSuperclass
@Getter
@Setter
@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "workspaceId", type = UUID.class)
)
@Filter(
    name = "tenantFilter",
    condition = "workspace_id = :workspaceId"
)
@EntityListeners(TenantEntityListener.class)
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;
}
