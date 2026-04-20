/**
 * Typed wrapper around chrome.storage.local for the extension's config.
 * Used by the background service worker — the popup + content scripts
 * should never touch storage directly; they go through background messages.
 */
import type { ExtensionConfig } from './types'

const KEY = 'leadrush.config'
const AUTO_ENROLL_KEY = 'leadrush.autoEnrollSequenceId'

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

/**
 * Last-used sequence for the one-click "Import & enrol" flow. Stored per-browser
 * rather than per-workspace in the backend — the extension is a personal tool,
 * muscle memory beats per-user server state here.
 */
export async function getAutoEnrollSequenceId(): Promise<string | null> {
  const result = await chrome.storage.local.get(AUTO_ENROLL_KEY)
  const value = result[AUTO_ENROLL_KEY]
  return typeof value === 'string' ? value : null
}

export async function setAutoEnrollSequenceId(id: string | null): Promise<void> {
  if (id == null || id === '') {
    await chrome.storage.local.remove(AUTO_ENROLL_KEY)
  } else {
    await chrome.storage.local.set({ [AUTO_ENROLL_KEY]: id })
  }
}
