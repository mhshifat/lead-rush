package com.leadrush.workspace.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateWorkspaceRequest {

    @Size(min = 1, max = 100, message = "Name must be 1-100 characters")
    private String name;

    private String logoUrl;
}
