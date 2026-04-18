/**
 * AppError entity — what the frontend uses everywhere to represent errors.
 *
 * This is the ENTITY layer of our Entity Pattern.
 * Components, composables, and stores use THIS — never the raw ErrorApiDto.
 *
 * The key field is `isSystemError`:
 *   true → show toast with "Copy Reference ID" button, never auto-dismiss
 *   false → show simple toast, auto-dismiss after 5 seconds
 */
export interface AppError {
  category: 'VALIDATION' | 'BUSINESS' | 'AUTH' | 'SYSTEM' | 'ACTIVATION_REQUIRED'
  message: string
  correlationId?: string                    // only for SYSTEM errors
  fieldErrors?: Record<string, string>      // only for VALIDATION: { email: "Already taken", password: "Too short" }
  lastActivationEmailSentAt?: string        // only for ACTIVATION_REQUIRED
  isSystemError: boolean                    // computed: true if correlationId exists
}
