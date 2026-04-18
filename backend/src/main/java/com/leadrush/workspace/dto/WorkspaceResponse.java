package com.leadrush.workspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkspaceResponse {

    private UUID id;
    private String name;
    private String slug;
    private String logoUrl;
    private String role;        // caller's role in this workspace
    private int memberCount;
    private LocalDateTime createdAt;
}
