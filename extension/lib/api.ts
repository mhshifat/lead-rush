/**
 * Lightweight fetch wrapper for calling the Lead Rush API with an X-API-Key header.
 * Used ONLY from the background service worker — host_permissions in the manifest
 * bypass CORS for these requests. Content scripts + the popup never call fetch().
 */
import type { ExtensionConfig } from './types'

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function apiFetch<T>(
  config: ExtensionConfig,
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const url = `${config.apiBaseUrl.replace(/\/$/, '')}${path}`
  const res = await fetch(url, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': config.apiKey,
      ...(init.headers ?? {}),
    },
  })

  if (!res.ok) {
    let message = `Request failed (${res.status})`
    let rawBody: unknown = null
    try {
      rawBody = await res.json()
      message = (rawBody as any)?.error?.message ?? message
    } catch { /* ignore parse errors */ }
    // Surface the raw response so the user can see WHY the backend rejected —
    // helpful when a 401 is really a missing header, wrong URL, or the key
    // was revoked on the server.
    console.warn('[lead-rush] api error', { url, status: res.status, body: rawBody })
    throw new ApiError(res.status, message)
  }

  // Backend wraps every success response as { success: true, data: ... }
  const body = await res.json()
  return body?.data as T
}
