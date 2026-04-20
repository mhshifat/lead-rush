package com.leadrush.extension.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OpenerResponse {
    String text;
    /** Character count — useful for the 300-char LinkedIn note limit indicator. */
    int length;
    String channel;
}
