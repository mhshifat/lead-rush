package com.leadrush.leadscoring.entity;

/**
 * Comparison operators for rule conditions.
 *
 * Example: "Only fire this rule when contact.title CONTAINS 'CEO'".
 */
public enum ConditionOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    GREATER_THAN,
    LESS_THAN
}
