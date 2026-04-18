package com.leadrush.ai.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateEmailResponse {
    private String subject;
    private String bodyHtml;
    private String bodyText;
}
