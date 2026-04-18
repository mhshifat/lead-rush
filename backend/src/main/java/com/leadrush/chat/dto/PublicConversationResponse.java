package com.leadrush.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Public response returned to the embedded widget — contains the visitor token
 * so returning visits thread into the same conversation, plus the message log.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicConversationResponse {
    private UUID conversationId;
    private String visitorToken;
    private List<ChatMessageResponse> messages;
}
