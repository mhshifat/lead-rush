package com.leadrush.workspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InviteRequest {

    @Email(message = "Valid email required")
    @NotBlank(message = "Email is required")
    private String email;

    /** OWNER / ADMIN / MANAGER / MEMBER / VIEWER. Defaults to MEMBER if omitted. */
    private String role;
}
