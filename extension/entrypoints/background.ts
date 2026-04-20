/**
 * Background service worker — the ONLY place that calls the Lead Rush API.
 *
 * Why centralize here?
 *   - Chrome MV3 gives service workers host_permissions, bypassing CORS
 *   - Content scripts injected into linkedin.com can't fetch leadrush.com without CORS
 *   - Popup + content scripts just send typed messages; we handle the HTTP
 */
import { defineBackground } from 'wxt/utils/define-background'
import { apiFetch, ApiError } from '@/lib/api'
import {
  deriveNameFromProfileUrl,
  isProfileUrl,
  isSalesNavProfileUrl,
  normalizeProfileUrl,
} from '@/lib/linkedin-scraper'
import {
  clearConfig,
  getAutoEnrollSequenceId,
  getConfig,
  setAutoEnrollSequenceId,
  setConfig,
} from '@/lib/storage'
import type {
  CheckSearchResult,
  ContactLookup,
  EnrollResult,
  ExtensionSequence,
  ExtensionTaskDto,
  LinkedInImportPayload,
  LinkedInImportResult,
  MeDto,
  Message,
  MessageResponse,
  OpenerPayload,
  OpenerResult,
  PossibleMatch,
  SavedSearchDto,
} from '@/lib/types'

/** Chrome alarm name used for periodic badge refresh. */
const BADGE_ALARM = 'lr-badge-refresh'
/** Context-menu item id for the right-click "Capture to Lead Rush" flow. */
const CAPTURE_MENU_ID = 'lr-capture-linkedin'
/** Maps notification id → LinkedIn URL we just captured, so we can open it on click. */
const notificationTargets = new Map<string, string>()

export default defineBackground(() => {
  chrome.runtime.onMessage.addListener((raw, _sender, sendResponse) => {
    const message = raw as Message

    // Promise must be resolved via sendResponse — return true to keep the channel open.
    handle(message)
      .then(data => {
        sendResponse({ ok: true, data } satisfies MessageResponse)
        // Any mutation that changes the task queue deserves a badge refresh.
        if (message.type === 'completeTask' || message.type === 'importContact' || message.type === 'setConfig') {
          void refreshBadge()
        }
      })
      .catch((err: unknown) => {
        sendResponse(toErrorResponse(err))
      })

    return true // async response
  })

  // ── Badge count ──
  // Refresh on install/update + every 5 minutes via chrome.alarms. 5m is a
  // reasonable trade-off: low enough that a new assignee sees it within a
  // coffee break, high enough to avoid hammering the API.
  chrome.runtime.onInstalled.addListener(() => {
    void refreshBadge()
    chrome.alarms.create(BADGE_ALARM, { periodInMinutes: 5 })
  })
  chrome.runtime.onStartup.addListener(() => {
    void refreshBadge()
    chrome.alarms.create(BADGE_ALARM, { periodInMinutes: 5 })
  })
  chrome.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name === BADGE_ALARM) void refreshBadge()
  })

  // ── Right-click "Capture to Lead Rush" ──
  // Registered here (not via WXT manifest) because the targetUrlPatterns list is
  // easier to maintain alongside the URL classifiers than in JSON.
  chrome.runtime.onInstalled.addListener(() => { void registerContextMenus() })
  chrome.runtime.onStartup.addListener(() => { void registerContextMenus() })
  chrome.contextMenus.onClicked.addListener((info, tab) => {
    if (info.menuItemId !== CAPTURE_MENU_ID) return
    const url = info.linkUrl ?? (info.selectionText && pickFirstLinkedInUrl(info.selectionText))
                ?? tab?.url ?? null
    if (!url) { void notifyFailure('No LinkedIn URL found in this link.'); return }
    void captureFromUrl(url)
  })
  // Click the toast → open the contact in the web app.
  chrome.notifications.onClicked.addListener((notificationId) => {
    const url = notificationTargets.get(notificationId)
    if (url) {
      void chrome.tabs.create({ url })
      notificationTargets.delete(notificationId)
    }
    chrome.notifications.clear(notificationId)
  })

  // ── Keyboard shortcut → forward to content script ──
  // Hotkey wakes the service worker; we ping the active LinkedIn tab so its
  // content script can scrape + import without the user opening the popup.
  chrome.commands.onCommand.addListener(async (command) => {
    if (command !== 'import-current-profile') return
    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
      if (!tab?.id || !tab.url) return
      if (!/linkedin\.com/.test(tab.url)) return
      await chrome.tabs.sendMessage(tab.id, { type: 'hotkey-import' })
    } catch {
      // If the tab doesn't have the content script (not on LinkedIn), ignore.
    }
  })
})

// ── Dispatcher ──

async function handle(message: Message): Promise<unknown> {
  switch (message.type) {
    case 'setConfig': {
      await setConfig({ apiKey: message.apiKey, apiBaseUrl: message.apiBaseUrl })
      return await fetchMe()
    }
    case 'getConfig':
      return await getConfig()
    case 'clearConfig':
      await clearConfig()
      return null
    case 'me':
      return await fetchMe()
    case 'listTasks':
      return await listTasks()
    case 'tasksForUrl':
      return await tasksForUrl(message.url)
    case 'completeTask':
      return await completeTask(message.id)
    case 'importContact':
      return await importContact(message.payload)
    case 'lookupContact':
      return await lookupContact(message.url)
    case 'lookupByEmail':
      return await lookupByEmail(message.email)
    case 'listSequences':
      return await listSequences()
    case 'enroll':
      return await enroll(message.sequenceId, message.contactId)
    case 'addNote':
      return await addNote(message.contactId, message.body)
    case 'reportScraperMiss':
      return await reportScraperMiss(message.layout, message.url, message.missedFields, message.scraperVersion)
    case 'generateOpener':
      return await generateOpener(message.payload)
    case 'possibleMatches':
      return await possibleMatches(message.payload)
    case 'getAutoEnrollSequenceId':
      return await getAutoEnrollSequenceId()
    case 'setAutoEnrollSequenceId':
      await setAutoEnrollSequenceId(message.id)
      return null
    case 'listSavedSearches':
      return await listSavedSearches()
    case 'saveSearch':
      return await saveSearch(message.name, message.url, message.seenProfileUrls)
    case 'checkSavedSearch':
      return await checkSavedSearch(message.url, message.currentProfileUrls)
    case 'deleteSavedSearch':
      return await deleteSavedSearch(message.id)
  }
}

// ── API calls ──

async function fetchMe(): Promise<MeDto> {
  const config = await requireConfig()
  return apiFetch<MeDto>(config, '/api/v1/ext/me')
}

async function listTasks(): Promise<ExtensionTaskDto[]> {
  const config = await requireConfig()
  return apiFetch<ExtensionTaskDto[]>(config, '/api/v1/ext/tasks')
}

async function tasksForUrl(url: string): Promise<ExtensionTaskDto[]> {
  const config = await requireConfig()
  const qs = new URLSearchParams({ url }).toString()
  return apiFetch<ExtensionTaskDto[]>(config, `/api/v1/ext/tasks/by-linkedin-url?${qs}`)
}

async function completeTask(id: string): Promise<void> {
  const config = await requireConfig()
  await apiFetch<void>(config, `/api/v1/ext/tasks/${id}/complete`, { method: 'POST' })
}

async function importContact(payload: unknown): Promise<LinkedInImportResult> {
  const config = await requireConfig()
  return apiFetch<LinkedInImportResult>(config, '/api/v1/ext/contacts/from-linkedin', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

async function lookupContact(url: string): Promise<ContactLookup | null> {
  const config = await requireConfig()
  const qs = new URLSearchParams({ url }).toString()
  return apiFetch<ContactLookup | null>(config, `/api/v1/ext/contacts/by-linkedin-url?${qs}`)
}

async function lookupByEmail(email: string): Promise<ContactLookup | null> {
  const config = await requireConfig()
  const qs = new URLSearchParams({ email }).toString()
  return apiFetch<ContactLookup | null>(config, `/api/v1/ext/contacts/by-email?${qs}`)
}

async function listSequences(): Promise<ExtensionSequence[]> {
  const config = await requireConfig()
  return apiFetch<ExtensionSequence[]>(config, '/api/v1/ext/sequences')
}

async function enroll(sequenceId: string, contactId: string): Promise<EnrollResult> {
  const config = await requireConfig()
  return apiFetch<EnrollResult>(config, '/api/v1/ext/enrollments', {
    method: 'POST',
    body: JSON.stringify({ sequenceId, contactId }),
  })
}

async function generateOpener(payload: OpenerPayload): Promise<OpenerResult> {
  const config = await requireConfig()
  return apiFetch<OpenerResult>(config, '/api/v1/ext/ai/opener', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

async function possibleMatches(payload: LinkedInImportPayload): Promise<PossibleMatch[]> {
  const config = await requireConfig()
  return apiFetch<PossibleMatch[]>(config, '/api/v1/ext/contacts/possible-matches', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

async function listSavedSearches(): Promise<SavedSearchDto[]> {
  const config = await requireConfig()
  return apiFetch<SavedSearchDto[]>(config, '/api/v1/ext/saved-searches')
}

async function saveSearch(name: string, url: string, seenProfileUrls: string[]): Promise<SavedSearchDto> {
  const config = await requireConfig()
  return apiFetch<SavedSearchDto>(config, '/api/v1/ext/saved-searches', {
    method: 'POST',
    body: JSON.stringify({ name, url, seenProfileUrls }),
  })
}

async function checkSavedSearch(url: string, currentProfileUrls: string[]): Promise<CheckSearchResult> {
  const config = await requireConfig()
  return apiFetch<CheckSearchResult>(config, '/api/v1/ext/saved-searches/check', {
    method: 'POST',
    body: JSON.stringify({ url, currentProfileUrls }),
  })
}

async function deleteSavedSearch(id: string): Promise<void> {
  const config = await requireConfig()
  await apiFetch<void>(config, `/api/v1/ext/saved-searches/${id}`, { method: 'DELETE' })
}

async function addNote(contactId: string, body: string): Promise<void> {
  const config = await requireConfig()
  await apiFetch<void>(config, `/api/v1/ext/contacts/${contactId}/notes`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  })
}

/**
 * Best-effort telemetry ping — swallows every failure, including
 * "not connected". We don't want scraper misses to show up as errors in the UI.
 */
async function reportScraperMiss(
  layout: string,
  url: string,
  missedFields: string[],
  scraperVersion: string,
): Promise<void> {
  if (!missedFields || missedFields.length === 0) return
  try {
    const config = await getConfig()
    if (!config) return
    await apiFetch<void>(config, '/api/v1/ext/telemetry/scraper', {
      method: 'POST',
      body: JSON.stringify({ layout, url, missedFields, scraperVersion }),
    })
  } catch {
    // Intentional: telemetry is fire-and-forget.
  }
}

async function requireConfig() {
  const config = await getConfig()
  if (!config) throw new NotConfiguredError()
  return config
}

class NotConfiguredError extends Error {
  constructor() {
    super('Not connected — paste your API key in the extension popup')
    this.name = 'NotConfiguredError'
  }
}

/**
 * Maps a thrown value to the extension's error envelope. Tags authentication
 * failures specifically so the UI can offer a Reconnect button.
 */
function toErrorResponse(err: unknown): MessageResponse {
  if (err instanceof ApiError) {
    const authExpired = err.status === 401 || err.status === 403
    return {
      ok: false,
      error: authExpired
        ? 'Your API key was revoked or expired. Reconnect to continue.'
        : err.message,
      status: err.status,
      code: authExpired ? 'AUTH_REQUIRED' : 'UNKNOWN',
    }
  }
  if (err instanceof NotConfiguredError) {
    return { ok: false, error: err.message, code: 'NOT_CONFIGURED' }
  }
  return {
    ok: false,
    error: err instanceof Error ? err.message : 'Unknown error',
    code: 'UNKNOWN',
  }
}

// ── Context-menu capture ──

/**
 * Idempotent — we re-register on each install/startup in case Chrome GCed the
 * service worker and dropped the registration. `create` throws if the id
 * already exists, so we remove first.
 */
async function registerContextMenus(): Promise<void> {
  try {
    await new Promise<void>((resolve) => chrome.contextMenus.removeAll(() => resolve()))
    chrome.contextMenus.create({
      id: CAPTURE_MENU_ID,
      title: 'Capture to Lead Rush',
      contexts: ['link', 'selection'],
      // Narrow to LinkedIn — prevents the menu appearing on every link.
      targetUrlPatterns: [
        '*://*.linkedin.com/in/*',
        '*://*.linkedin.com/sales/lead/*',
        '*://*.linkedin.com/sales/people/*',
      ],
    })
  } catch (err) {
    console.warn('[lead-rush] failed to register context menu', err)
  }
}

/** Pull the first linkedin.com/in/... or /sales/... URL out of a text blob. */
function pickFirstLinkedInUrl(text: string): string | null {
  const match = text.match(/https?:\/\/(?:www\.|[a-z]{2}\.)?linkedin\.com\/(?:in|sales\/lead|sales\/people)\/[^\s"<>]+/i)
  return match ? match[0] : null
}

/**
 * Create (or update) a shell contact from a LinkedIn URL with no DOM access.
 * First name is derived from the URL slug — the user sees a toast confirming
 * the capture, and can click it to open the contact in Lead Rush.
 */
async function captureFromUrl(rawUrl: string): Promise<void> {
  if (!isProfileUrl(rawUrl)) {
    await notifyFailure('Link is not a LinkedIn profile.')
    return
  }
  const linkedinUrl = normalizeProfileUrl(rawUrl)
  const derived = deriveNameFromProfileUrl(linkedinUrl)

  // Sales Nav URLs are hashed — no name in slug. Still capture with a
  // placeholder firstName; the panel will enrich it on the next visit.
  const firstName = derived?.firstName
    ?? (isSalesNavProfileUrl(linkedinUrl) ? 'LinkedIn' : 'LinkedIn')
  const lastName = derived?.lastName

  try {
    const config = await getConfig()
    if (!config) {
      await notifyFailure('Not connected — paste your API key in the extension popup.')
      return
    }
    const result = await apiFetch<LinkedInImportResult>(
      config,
      '/api/v1/ext/contacts/from-linkedin',
      {
        method: 'POST',
        body: JSON.stringify({ linkedinUrl, firstName, lastName }),
      },
    )
    await notifySuccess(result, linkedinUrl)
  } catch (err) {
    if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
      await notifyFailure('Your API key was revoked or expired. Reconnect in the popup.')
    } else {
      await notifyFailure(err instanceof Error ? err.message : 'Capture failed')
    }
  }
}

async function notifySuccess(result: LinkedInImportResult, linkedinUrl: string): Promise<void> {
  const id = `lr-capture-${Date.now()}`
  const message = result.created
    ? `Added ${result.fullName}. Open in Lead Rush?`
    : `${result.fullName} was already captured.`
  notificationTargets.set(id, linkedinUrl)
  await chrome.notifications.create(id, {
    type: 'basic',
    iconUrl: chrome.runtime.getURL('icon/128.png'),
    title: 'Lead Rush',
    message,
    // Hides automatically, but the click handler above still fires if the user is fast.
  })
}

async function notifyFailure(message: string): Promise<void> {
  const id = `lr-capture-err-${Date.now()}`
  await chrome.notifications.create(id, {
    type: 'basic',
    iconUrl: chrome.runtime.getURL('icon/128.png'),
    title: 'Lead Rush',
    message,
  })
}

// ── Badge helpers ──

/**
 * Fetches the pending-task count and writes it to the action icon badge.
 * Silent on every failure path: if the user isn't connected, the key is bad,
 * or the network is out — just clear the badge, don't crash the worker.
 */
async function refreshBadge(): Promise<void> {
  try {
    const config = await getConfig()
    if (!config) { await setBadgeCount(0); return }
    const tasks = await apiFetch<ExtensionTaskDto[]>(config, '/api/v1/ext/tasks')
    await setBadgeCount(tasks.length)
  } catch {
    await setBadgeCount(0)
  }
}

async function setBadgeCount(n: number): Promise<void> {
  const text = n > 0 ? (n > 99 ? '99+' : String(n)) : ''
  await chrome.action.setBadgeText({ text })
  await chrome.action.setBadgeBackgroundColor({ color: '#5E6AD2' })
  // MV3 only — ignored on Firefox MV2. Keeps text white on the primary-brand background.
  if (chrome.action.setBadgeTextColor) {
    try { await chrome.action.setBadgeTextColor({ color: '#FFFFFF' }) } catch {}
  }
}
