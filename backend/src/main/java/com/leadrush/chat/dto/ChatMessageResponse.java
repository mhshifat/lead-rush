package com.leadrush.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageResponse {
    private UUID id;
    private UUID conversationId;
    private String sender;
    private UUID agentUserId;
    private String agentName;
    private String body;
    private LocalDateTime createdAt;
}
