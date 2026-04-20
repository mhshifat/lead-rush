/**
 * Typed wrapper for chrome.runtime.sendMessage. Used by the popup AND the
 * content script to talk to the background service worker.
 *
 * The background always responds with { ok: true, data } | { ok: false, error, code };
 * this helper unwraps the envelope into either a resolved value or a thrown
 * MessagingError that carries the structured error code so UI can branch on it.
 */
import type { Message, MessageErrorCode, MessageResponse } from './types'

export class MessagingError extends Error {
  constructor(
    message: string,
    public readonly code: MessageErrorCode,
    public readonly status?: number,
  ) {
    super(message)
    this.name = 'MessagingError'
  }
}

export async function send<T>(message: Message): Promise<T> {
  const response = await chrome.runtime.sendMessage<Message, MessageResponse<T>>(message)
  if (!response) throw new MessagingError('No response from background', 'UNKNOWN')
  if (!response.ok) throw new MessagingError(response.error, response.code, response.status)
  return response.data
}

/** True if the error was caused by a missing / revoked API key. */
export function isAuthError(err: unknown): boolean {
  return err instanceof MessagingError
    && (err.code === 'AUTH_REQUIRED' || err.code === 'NOT_CONFIGURED')
}
