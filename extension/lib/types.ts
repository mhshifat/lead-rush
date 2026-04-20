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

  // ── Deep-scrape fields (all optional, best-effort) ──

  /** Free-text "About" section. */
  about?: string
  /** Most recent experiences in display order. */
  experiences?: LinkedInExperience[]
  /** Education entries. */
  education?: LinkedInEducation[]
  /** Top skills, surfaced as badges in the admin app. */
  skills?: string[]

  /**
   * When set, the backend merges the scraped data into this specific contact
   * instead of creating a new one — used by the duplicate-detector flow.
   */
  mergeIntoContactId?: string

  /**
   * When set, the backend enrols the imported contact in this sequence right
   * after import. Powers the panel's "Import & enrol" one-click flow.
   */
  autoEnrollSequenceId?: string
}

/**
 * Candidate surfaced by the duplicate detector. Rendered as a "Could this be
 * the same person?" card in the panel before the import commits.
 */
export interface PossibleMatch {
  contactId: string
  fullName: string
  title: string | null
  companyName: string | null
  avatarUrl: string | null
  linkedinUrl: string | null
  reason: string  // 'NAME' | 'NAME_COMPANY'
}

export interface LinkedInExperience {
  title?: string
  companyName?: string
  /** Raw date range string, e.g. "2021 · Present". */
  dateRange?: string
}

export interface LinkedInEducation {
  school?: string
  degree?: string
  fieldOfStudy?: string
}

export interface LinkedInImportResult {
  contactId: string
  fullName: string
  created: boolean
  /** Populated when the import carried an autoEnrollSequenceId. */
  enrollmentId?: string
  enrolledSequenceName?: string
  /** Non-null on partial success — contact imported but enroll failed. */
  enrollError?: string
  /** Job/company changes detected by this import (empty on first-time import). */
  jobChanges?: JobChangeEventDto[]
}

/**
 * Summary returned by /ext/contacts/by-linkedin-url — lets the side panel
 * render an "Already in Lead Rush" card before the user clicks Import.
 * Null when the URL doesn't match any contact.
 */
export interface ContactLookup {
  contactId: string
  fullName: string
  title: string | null
  companyName: string | null
  leadScore: number
  lifecycleStage: string | null
  avatarUrl: string | null
  activeSequenceNames: string[]
  lastActivityAt: string | null
  createdAt: string
  contactUrl: string
  /** Recent teammate activity (last 14 days, max 3). Empty if nobody else touched. */
  collisions?: CollisionWarningDto[]
  /** Open deals this contact participates in, sorted by expected close date. */
  deals?: DealSummaryDto[]
  /** Job/company changes detected in the last 90 days, newest first. */
  recentJobChanges?: JobChangeEventDto[]
}

export interface DealSummaryDto {
  dealId: string
  name: string
  stageName: string
  stageColor: string | null
  stageType: string  // 'OPEN' | 'WON' | 'LOST'
  winProbability: number
  valueAmount: number | null
  valueCurrency: string | null
  expectedCloseAt: string | null   // ISO date
  dealUrl: string
}

export interface JobChangeEventDto {
  type: string  // 'TITLE' | 'COMPANY'
  from: string | null
  to: string | null
  at: string    // ISO timestamp
}

export interface CollisionWarningDto {
  userName: string
  action: string   // 'NOTE' | 'CALL' | 'LINKEDIN_MESSAGE' | 'LINKEDIN_CONNECT' | 'MANUAL'
  status: string   // 'PENDING' | 'COMPLETED' | 'CANCELLED'
  at: string       // ISO timestamp
}

/** Minimal sequence shape for the enroll picker in the side panel. */
export interface ExtensionSequence {
  id: string
  name: string
  stepCount: number
  hasDefaultMailbox: boolean
}

export interface EnrollResult {
  enrollmentId: string
  sequenceId: string
  sequenceName: string
}

// ── Saved searches ──

export interface SavedSearchDto {
  id: string
  name: string
  url: string
  knownProfileCount: number
  lastCheckedAt: string | null
  createdAt: string
}

export interface CheckSearchResult {
  /** Null when the URL isn't saved yet — panel shows "Save this search" CTA. */
  savedSearchId: string | null
  name?: string
  /** Profile URLs that weren't in the known list on the previous check. */
  newProfileUrls: string[]
  knownProfileCount: number
}

/** Input for the AI opener generator — stateless, scraped-in-the-moment data. */
export interface OpenerPayload {
  firstName: string
  lastName?: string
  title?: string
  companyName?: string
  location?: string
  about?: string
  /** "LINKEDIN_NOTE" (≤300 chars) or "EMAIL" (3-4 lines). */
  channel: 'LINKEDIN_NOTE' | 'EMAIL'
  /** Optional user value-prop one-liner. */
  valueProp?: string
}

export interface OpenerResult {
  text: string
  length: number
  channel: string
}

// ── Message protocol ──

export type Message =
  | { type: 'me' }
  | { type: 'listTasks' }
  | { type: 'tasksForUrl'; url: string }
  | { type: 'completeTask'; id: string }
  | { type: 'importContact'; payload: LinkedInImportPayload }
  | { type: 'lookupContact'; url: string }
  | { type: 'lookupByEmail'; email: string }
  | { type: 'listSequences' }
  | { type: 'enroll'; sequenceId: string; contactId: string }
  | { type: 'addNote'; contactId: string; body: string }
  | { type: 'reportScraperMiss'; layout: string; url: string; missedFields: string[]; scraperVersion: string }
  | { type: 'generateOpener'; payload: OpenerPayload }
  | { type: 'possibleMatches'; payload: LinkedInImportPayload }
  | { type: 'getAutoEnrollSequenceId' }
  | { type: 'setAutoEnrollSequenceId'; id: string | null }
  | { type: 'listSavedSearches' }
  | { type: 'saveSearch'; name: string; url: string; seenProfileUrls: string[] }
  | { type: 'checkSavedSearch'; url: string; currentProfileUrls: string[] }
  | { type: 'deleteSavedSearch'; id: string }
  | { type: 'setConfig'; apiKey: string; apiBaseUrl: string }
  | { type: 'getConfig' }
  | { type: 'clearConfig' }

/**
 * Error codes the background surfaces to the UI. `AUTH_REQUIRED` lets the
 * popup/panel show a Reconnect button instead of a raw stack trace.
 */
export type MessageErrorCode = 'AUTH_REQUIRED' | 'NOT_CONFIGURED' | 'UNKNOWN'

export type MessageResponse<T = unknown> =
  | { ok: true; data: T }
  | { ok: false; error: string; code: MessageErrorCode; status?: number }

// ── Config stored in chrome.storage.local ──

export interface ExtensionConfig {
  apiKey: string
  apiBaseUrl: string
}
