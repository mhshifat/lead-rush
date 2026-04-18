package com.leadrush.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatConversationDetail {
    private UUID id;
    private String visitorName;
    private String visitorEmail;
    private UUID contactId;
    private String contactName;
    private String status;
    private UUID assignedUserId;
    private String sourceUrl;
    private List<ChatMessageResponse> messages;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
}
