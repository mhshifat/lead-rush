package com.leadrush.chat.dto;

import lombok.Data;

@Data
public class UpdateWidgetRequest {
    private Boolean enabled;
    private String displayName;
    private String greeting;
    private String offlineMessage;
    private String primaryColor;
    private String position;       // BOTTOM_RIGHT | BOTTOM_LEFT
    private Boolean requireEmail;
}
