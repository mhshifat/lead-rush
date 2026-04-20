package com.leadrush.extension.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class ExtensionEnrollResponse {
    UUID enrollmentId;
    UUID sequenceId;
    String sequenceName;
}
