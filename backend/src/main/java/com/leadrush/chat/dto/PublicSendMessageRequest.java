package com.leadrush.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublicSendMessageRequest {
    @NotBlank
    private String visitorToken;

    @NotBlank
    private String message;
}
