package com.leadrush.extension.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * Result of a saved-search check. {@code savedSearchId} is null when the URL
 * isn't saved — the panel uses that to show a "Save this search" button
 * instead of the alerts UI.
 */
@Value
@Builder
public class CheckSearchResponse {
    /** Null if no saved search matches this URL in the current workspace. */
    UUID savedSearchId;
    String name;
    /** Only populated when savedSearchId is non-null. */
    List<String> newProfileUrls;
    /** Total profiles on the saved search's known list AFTER this check. */
    int knownProfileCount;
}
