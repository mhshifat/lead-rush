package com.leadrush.notification.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.notification.dto.NotificationResponse;
import com.leadrush.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<Page<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(notificationService.list(page, size));
    }

    @GetMapping("/unread")
    public ApiResponse<List<NotificationResponse>> listUnread() {
        return ApiResponse.success(notificationService.listUnread());
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success(Map.of("count", notificationService.unreadCount()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID id) {
        return ApiResponse.success(notificationService.markRead(id));
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead();
        return ApiResponse.success(null);
    }
}
