package com.leadrush.sequence.entity;

public enum SequenceStatus {
    DRAFT,      // Being edited — can add/remove steps freely
    ACTIVE,     // Running — new enrollments accepted
    PAUSED      // Stopped — no new enrollments, existing ones paused
}
