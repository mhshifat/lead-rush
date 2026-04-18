/**
 * Shared types + message protocol between the content script, popup, and background.
 *
 * All API work happens in the background (to avoid CORS from content scripts).
 * Content scripts and the popup send typed messages; the background returns
 * a `{ ok: true, data } | { ok: false, error }` envelope.
 */

// ── Server response shapes (mirror backend DTOs) ──

export interface MeDto {
  userId: string
  userName: string
  userEmail: string
  workspaceId: string
  workspaceName: string
}

export interface ExtensionTaskDto {
  id: string
  type: 'LINKEDIN_CONNECT' | 'LINKEDIN_MESSAGE'
  title: string
  description: string | null
  contactId: string | null
  contactName: string | null
  contactTitle: string | null
  contactCompany: string | null
  contactLinkedinUrl: string | null
  dueAt: string | null
}

export interface LinkedInImportPayload {
  linkedinUrl: string
  firstName?: string
  lastName?: string
  title?: string
  companyName?: string
  avatarUrl?: string
  location?: string
}

export interface LinkedInImportResult {
  contactId: string
  fullName: string
  created: boolean
}

// ── Message protocol ──

export type Message =
  | { type: 'me' }
  | { type: 'listTasks' }
  | { type: 'tasksForUrl'; url: string }
  | { type: 'completeTask'; id: string }
  | { type: 'importContact'; payload: LinkedInImportPayload }
  | { type: 'setConfig'; apiKey: string; apiBaseUrl: string }
  | { type: 'getConfig' }
  | { type: 'clearConfig' }

export type MessageResponse<T = unknown> =
  | { ok: true; data: T }
  | { ok: false; error: string }

// ── Config stored in chrome.storage.local ──

export interface ExtensionConfig {
  apiKey: string
  apiBaseUrl: string
}
