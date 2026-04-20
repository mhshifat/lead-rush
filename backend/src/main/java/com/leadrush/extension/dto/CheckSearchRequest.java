package com.leadrush.extension.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Hit by the extension every time the side panel mounts on a LinkedIn search
 * URL. The service returns which profiles are new since the last check AND
 * updates the known-set atomically so a second mount seconds later doesn't
 * re-flag the same rows.
 */
@Data
public class CheckSearchRequest {

    @NotBlank
    private String url;

    /** Profile URLs currently on the page. Empty list is valid (e.g. filters hid everything). */
    private List<String> currentProfileUrls;
}
