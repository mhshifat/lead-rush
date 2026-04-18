export interface WebhookEndpointApiDto {
  id: string
  url: string
  description: string | null
  events: string[]
  enabled: boolean
  consecutiveFailures: number
  disabledReason: string | null
  lastSuccessAt: string | null
  lastFailureAt: string | null
  /** Plaintext — only present in create / rotate-secret responses. */
  secret?: string
  createdAt: string
  updatedAt: string
}

export interface WebhookDeliveryApiDto {
  id: string
  endpointId: string
  eventType: string
  eventId: string
  payload: unknown
  status: string
  attemptCount: number
  lastStatusCode: number | null
  lastError: string | null
  nextAttemptAt: string | null
  lastAttemptAt: string | null
  deliveredAt: string | null
  createdAt: string
}

export interface WebhookEndpointDto {
  url: string
  description?: string
  events?: string[]
  enabled?: boolean
}
