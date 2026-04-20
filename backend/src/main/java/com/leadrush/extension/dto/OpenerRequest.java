package com.leadrush.extension.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Input for the AI opener generator, called from the LinkedIn side panel.
 * We intentionally DON'T use the contact entity — the scraper may have fresher
 * data than what's in the DB yet (profile changed, not re-imported).
 */
@Data
public class OpenerRequest {
    @NotBlank
    private String firstName;
    private String lastName;
    private String title;
    private String companyName;
    private String location;
    /** Free-text "About" blurb when scraped — helps the model personalise. */
    private String about;

    /** "LINKEDIN_NOTE" (300 chars max) or "EMAIL" (3-4 lines). */
    @NotBlank
    private String channel;

    /**
     * Optional value-prop one-liner the user maintains in workspace settings.
     * If blank, the model improvises something neutral.
     */
    @Size(max = 500)
    private String valueProp;
}
