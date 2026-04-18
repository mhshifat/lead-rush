package com.leadrush.sequence.entity;

public enum StepType {
    EMAIL,                  // Send an email from a template
    DELAY,                  // Wait N days before next step
    CALL,                   // Creates a CALL task for the user
    TASK,                   // Creates a MANUAL task with custom description
    LINKEDIN_MESSAGE,       // Creates a LINKEDIN_MESSAGE task (user performs action in LinkedIn)
    LINKEDIN_CONNECT        // Creates a LINKEDIN_CONNECT task (user sends connection request)
}
