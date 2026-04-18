/**
 * Typed wrapper for chrome.runtime.sendMessage. Used by the popup AND the
 * content script to talk to the background service worker.
 *
 * The background always responds with { ok: true, data } | { ok: false, error };
 * this helper unwraps the envelope into either a resolved value or a thrown error.
 */
import type { Message, MessageResponse } from './types'

export async function send<T>(message: Message): Promise<T> {
  const response = await chrome.runtime.sendMessage<Message, MessageResponse<T>>(message)
  if (!response) throw new Error('No response from background')
  if (!response.ok) throw new Error(response.error)
  return response.data
}
