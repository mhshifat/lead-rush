package com.leadrush.extension.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.UUID;

/**
 * Called once on popup load so the extension can show "Connected to Workspace X as Name".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeResponse {
    private UUID userId;
    private String userName;
    private String userEmail;
    private UUID workspaceId;
    private String workspaceName;
}
