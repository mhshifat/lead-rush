package com.leadrush.extension.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Minimal sequence summary for the extension's enroll picker.
 * Only ACTIVE sequences are ever returned — you can't enroll into a DRAFT/PAUSED one.
 */
@Value
@Builder
public class ExtensionSequenceResponse {
    UUID id;
    String name;
    int stepCount;
    /** True if the sequence has a default mailbox — extension-enrolls require one. */
    boolean hasDefaultMailbox;
}
