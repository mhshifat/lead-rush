package com.leadrush.contact.entity;

/** How this contact was acquired. */
public enum ContactSource {
    MANUAL,         // Created manually in the UI
    CSV_IMPORT,     // Imported from CSV file
    FORM,           // Submitted a landing page form
    LINKEDIN,       // Scraped/imported from LinkedIn
    API,            // Created via public API
    ENRICHMENT,     // Discovered during enrichment
    CHAT            // Started a chat conversation from the widget
}
