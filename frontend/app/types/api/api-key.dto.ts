export interface ApiKeyApiDto {
  id: string
  name: string
  keyPrefix: string
  /** Plaintext — only present in the POST response. */
  plaintext?: string
  lastUsedAt: string | null
  revokedAt: string | null
  createdAt: string
}

export interface CreateApiKeyDto {
  name: string
}
