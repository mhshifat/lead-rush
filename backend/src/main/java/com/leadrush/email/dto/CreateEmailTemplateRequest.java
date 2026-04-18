package com.leadrush.email.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateEmailTemplateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Subject is required")
    private String subject;

    private String bodyHtml;
    private String bodyText;
}
