package com.leadrush.email.entity;

/**
 * Email provider type — determines which SMTP settings to use.
 */
public enum MailboxProvider {
    SMTP,       // Generic SMTP (user provides host/port/credentials)
    GMAIL,      // smtp.gmail.com:587 (uses App Password or OAuth — SMTP for now)
    OUTLOOK     // smtp.office365.com:587
}
