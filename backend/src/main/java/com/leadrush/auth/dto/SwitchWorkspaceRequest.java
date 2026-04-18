package com.leadrush.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SwitchWorkspaceRequest {

    @NotNull(message = "workspaceId is required")
    private UUID workspaceId;
}
