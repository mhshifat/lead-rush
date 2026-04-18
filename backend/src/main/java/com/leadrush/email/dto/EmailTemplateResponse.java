package com.leadrush.email.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailTemplateResponse {

    private UUID id;
    private String name;
    private String subject;
    private String bodyHtml;
    private String bodyText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
