// Mirrors the backend error envelope shape.
export interface ErrorApiDto {
  success: false
  error: {
    category: 'VALIDATION' | 'BUSINESS' | 'AUTH' | 'SYSTEM' | 'ACTIVATION_REQUIRED'
    message: string
    correlationId?: string
    details?: Array<{
      field: string
      message: string
    }>
    lastActivationEmailSentAt?: string
  }
}
