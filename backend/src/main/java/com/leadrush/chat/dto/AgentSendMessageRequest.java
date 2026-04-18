package com.leadrush.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentSendMessageRequest {
    @NotBlank
    private String message;
}
