package com.leadrush.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * Public config served to embedded widgets — never includes workspace internals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatWidgetConfig {
    private String workspaceSlug;
    private String workspaceName;
    private boolean enabled;
    private String displayName;
    private String greeting;
    private String offlineMessage;
    private String primaryColor;
    private String position;
    private boolean requireEmail;
}
