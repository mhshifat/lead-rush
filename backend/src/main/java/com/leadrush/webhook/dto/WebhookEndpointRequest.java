package com.leadrush.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class WebhookEndpointRequest {

    @NotBlank(message = "URL is required")
    @Pattern(regexp = "^https?://.+", message = "URL must start with http:// or https://")
    private String url;

    private String description;

    /**
     * List of event topics (e.g. "contact.created") or a single "*" for all.
     * Empty list is treated the same as no subscriptions (endpoint fires for nothing).
     */
    private List<String> events;

    private Boolean enabled;
}
