package com.leadrush.extension.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class SavedSearchResponse {
    UUID id;
    String name;
    String url;
    int knownProfileCount;
    LocalDateTime lastCheckedAt;
    LocalDateTime createdAt;
}
