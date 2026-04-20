package com.leadrush.extension.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Single candidate in the duplicate-detector flow. The panel renders this
 * as a "Could this be the same person?" card so the user can merge instead
 * of creating a sibling contact.
 */
@Value
@Builder
public class PossibleMatchResponse {
    UUID contactId;
    String fullName;
    String title;
    String companyName;
    String avatarUrl;
    /** Existing LinkedIn URL on this contact, if any — helps the user judge "is this really a different person?". */
    String linkedinUrl;
    /** How we matched: "NAME", "NAME_COMPANY", etc. — panel uses this to tweak copy. */
    String reason;
}
