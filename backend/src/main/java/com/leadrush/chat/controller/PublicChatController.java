package com.leadrush.chat.controller;

import com.leadrush.chat.dto.*;
import com.leadrush.chat.service.ChatService;
import com.leadrush.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Public chat endpoints — mounted under /api/v1/public so SecurityConfig lets them through
 * without a JWT. The embedded widget on a customer's site hits these from visitor browsers.
 */
@RestController
@RequestMapping("/api/v1/public/chat")
@RequiredArgsConstructor
public class PublicChatController {

    private final ChatService chatService;

    @GetMapping("/widget/{workspaceSlug}")
    public ApiResponse<ChatWidgetConfig> widget(@PathVariable String workspaceSlug) {
        return ApiResponse.success(chatService.getPublicConfig(workspaceSlug));
    }

    @PostMapping("/conversations")
    public ApiResponse<PublicConversationResponse> start(
            @Valid @RequestBody PublicStartConversationRequest request,
            HttpServletRequest httpRequest
    ) {
        // Backfill user-agent if the caller didn't send it
        if (request.getUserAgent() == null || request.getUserAgent().isBlank()) {
            request.setUserAgent(httpRequest.getHeader("User-Agent"));
        }
        return ApiResponse.success(chatService.start(request));
    }

    @PostMapping("/conversations/messages")
    public ApiResponse<PublicConversationResponse> send(@Valid @RequestBody PublicSendMessageRequest request) {
        return ApiResponse.success(chatService.visitorSend(request));
    }

    @GetMapping("/conversations/{visitorToken}")
    public ApiResponse<PublicConversationResponse> fetch(@PathVariable String visitorToken) {
        return ApiResponse.success(chatService.visitorFetch(visitorToken));
    }
}
