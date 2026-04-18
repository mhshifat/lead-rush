package com.leadrush.landingpage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.*;

import java.util.List;

/**
 * Public response for a landing page — used by the public /p/{slug} renderer.
 * Includes block data AND any forms referenced by form blocks (so the renderer
 * can draw the form fields without extra round-trips).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicPageResponse {
    private String name;
    private String slug;
    private String metaTitle;
    private String metaDescription;

    @JsonRawValue
    private String blocks;

    /** Forms referenced by form-type blocks on this page. */
    private List<FormResponse> forms;
}
