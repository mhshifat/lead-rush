package com.leadrush.extension.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Used for both create + update. If a search already exists at {@link #url} in
 * this workspace, the service merges rather than throwing — the user's intent
 * is "remember this search", not "fail on duplicate".
 */
@Data
public class SaveSearchRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Size(max = 2000)
    private String url;

    /**
     * Profile URLs currently visible on the page at save time. Used as the
     * initial "known" set so the first revisit doesn't light up every row as
     * new. Normalised via the extension's url helper.
     */
    private List<String> seenProfileUrls;
}
