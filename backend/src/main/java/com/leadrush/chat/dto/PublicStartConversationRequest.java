package com.leadrush.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Public payload from the embedded widget when a visitor sends their first message.
 * The visitorToken may be absent (first visit) or present (returning visitor).
 */
@Data
public class PublicStartConversationRequest {

    @NotBlank(message = "workspaceSlug is required")
    private String workspaceSlug;

    /** Existing visitor token from the browser, if any. */
    private String visitorToken;

    private String visitorName;
    private String visitorEmail;

    @NotBlank(message = "message is required")
    private String message;

    private String sourceUrl;
    private String userAgent;
}
