package com.leadrush.landingpage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LandingPageResponse {
    private UUID id;
    private String name;
    private String slug;
    private String metaTitle;
    private String metaDescription;

    @JsonRawValue
    private String blocks;

    private String status;
    private LocalDateTime publishedAt;
    private int viewCount;
    private int conversionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
