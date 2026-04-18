package com.leadrush.chat.controller;

import com.leadrush.chat.dto.*;
import com.leadrush.chat.service.ChatService;
import com.leadrush.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Authenticated chat endpoints — for workspace team members.
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // ── Widget config ──

    @GetMapping("/widget")
    public ApiResponse<ChatWidgetConfig> getWidget() {
        return ApiResponse.success(chatService.getWorkspaceWidget());
    }

    @PutMapping("/widget")
    public ApiResponse<ChatWidgetConfig> updateWidget(@RequestBody UpdateWidgetRequest request) {
        return ApiResponse.success(chatService.updateWorkspaceWidget(request));
    }

    // ── Inbox ──

    @GetMapping("/conversations")
    public ApiResponse<Page<ChatConversationSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return ApiResponse.success(chatService.listConversations(page, size));
    }

    @GetMapping("/conversations/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success(Map.of("count", chatService.unreadCount()));
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<ChatConversationDetail> get(@PathVariable UUID id) {
        return ApiResponse.success(chatService.getConversation(id));
    }

    @PostMapping("/conversations/{id}/messages")
    public ApiResponse<ChatMessageResponse> send(
            @PathVariable UUID id,
            @Valid @RequestBody AgentSendMessageRequest request
    ) {
        return ApiResponse.success(chatService.agentSend(id, request));
    }

    @PostMapping("/conversations/{id}/close")
    public ApiResponse<Void> close(@PathVariable UUID id) {
        chatService.closeConversation(id);
        return ApiResponse.success(null);
    }
}
