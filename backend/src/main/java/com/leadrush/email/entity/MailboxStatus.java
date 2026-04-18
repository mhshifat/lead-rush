package com.leadrush.email.entity;

public enum MailboxStatus {
    ACTIVE,     // Ready to send
    PAUSED,     // Temporarily disabled (by user)
    ERROR       // Connection failed — needs attention
}
