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
public class ChatConversationSummary {
    private UUID id;
    private String visitorName;
    private String visitorEmail;
    private UUID contactId;
    private String status;
    private UUID assignedUserId;
    private int unreadByTeam;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
