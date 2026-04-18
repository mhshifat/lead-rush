/**
 * Typed wrapper around chrome.storage.local for the extension's config.
 * Used by the background service worker — the popup + content scripts
 * should never touch storage directly; they go through background messages.
 */
import type { ExtensionConfig } from './types'

const KEY = 'leadrush.config'

export async function getConfig(): Promise<ExtensionConfig | null> {
  const result = await chrome.storage.local.get(KEY)
  return (result[KEY] as ExtensionConfig | undefined) ?? null
}

export async function setConfig(config: ExtensionConfig): Promise<void> {
  await chrome.storage.local.set({ [KEY]: config })
}

export async function clearConfig(): Promise<void> {
  await chrome.storage.local.remove(KEY)
}
