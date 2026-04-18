package com.leadrush.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {

    @NotBlank(message = "Role is required")
    private String role;
}
