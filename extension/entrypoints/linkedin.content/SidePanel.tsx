/**
 * Floating side panel that sits on LinkedIn profile pages.
 * States:
 *   - Not connected: prompt to open the popup
 *   - Connected + no tasks: show profile summary + Import button
 *   - Connected + tasks: show tasks with Complete button + Import button
 *   - Collapsed: just a Lead Rush pill that expands on click
 */
import { useEffect, useRef, useState } from 'react'
import { BrandMark } from '@/components/BrandMark'
import { isAuthError, send } from '@/lib/messaging'
import {
  isPostPage,
  isSearchResultsUrl,
  normalizeProfileUrl,
  SCRAPER_VERSION,
  scrapePostReactorsAndCommenters,
  scrapeProfile,
  scrapeProfileWithTelemetry,
  scrapeSearchResults,
} from '@/lib/linkedin-scraper'
import type {
  CheckSearchResult,
  ContactLookup,
  EnrollResult,
  ExtensionConfig,
  ExtensionSequence,
  ExtensionTaskDto,
  LinkedInImportPayload,
  LinkedInImportResult,
  MeDto,
  OpenerResult,
  PossibleMatch,
  SavedSearchDto,
} from '@/lib/types'

type Status = 'loading' | 'unauthenticated' | 'auth-expired' | 'ready' | 'error'

export function SidePanel() {
  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)
  const [expanded, setExpanded] = useState(true)

  const [me, setMe] = useState<MeDto | null>(null)
  const [tasks, setTasks] = useState<ExtensionTaskDto[]>([])
  const [profile, setProfile] = useState<LinkedInImportPayload | null>(null)
  const [lastImport, setLastImport] = useState<LinkedInImportResult | null>(null)
  const [existing, setExisting] = useState<ContactLookup | null>(null)
  const [sequences, setSequences] = useState<ExtensionSequence[] | null>(null)
  const [selectedSequenceId, setSelectedSequenceId] = useState<string>('')
  const [showSequencePicker, setShowSequencePicker] = useState(false)
  const [showNoteComposer, setShowNoteComposer] = useState(false)
  const [noteBody, setNoteBody] = useState('')

  // ── Opener composer ──
  const [showOpenerPanel, setShowOpenerPanel] = useState(false)
  const [openerChannel, setOpenerChannel] = useState<'LINKEDIN_NOTE' | 'EMAIL'>('LINKEDIN_NOTE')
  const [openerValueProp, setOpenerValueProp] = useState('')
  const [openerText, setOpenerText] = useState('')
  const [openerLoading, setOpenerLoading] = useState(false)
  const [openerCopied, setOpenerCopied] = useState(false)

  // ── Duplicate detector ──
  // Loaded lazily when profile is scraped AND no exact-URL match exists.
  const [possibleMatches, setPossibleMatches] = useState<PossibleMatch[] | null>(null)
  const [dismissedMatchIds, setDismissedMatchIds] = useState<Set<string>>(new Set())

  // ── Post-connect auto-enroll ──
  // Sticky selection: once the user picks "auto-enrol in X", the panel remembers
  // it for subsequent imports this browser until they choose "None".
  const [autoEnrollSequenceId, setAutoEnrollSequenceIdState] = useState<string | null>(null)

  // ── Saved-search alerts (only meaningful in search mode) ──
  const [savedSearchCheck, setSavedSearchCheck] = useState<CheckSearchResult | null>(null)
  const [saveSearchBusy, setSaveSearchBusy] = useState(false)

  // ── Bulk import (search-results mode OR post-detail mode) ──
  const [isSearchMode, setIsSearchMode] = useState(false)
  const [isPostMode, setIsPostMode] = useState(false)
  const [searchRows, setSearchRows] = useState<LinkedInImportPayload[]>([])
  const [bulkProgress, setBulkProgress] = useState<{
    total: number; done: number; created: number; updated: number; failed: number
  } | null>(null)
  const cancelBulkRef = useRef(false)
  const [busy, setBusy] = useState(false)
  const [toast, setToast] = useState<string | null>(null)

  const profileUrlRef = useRef<string | null>(null)

  async function load() {
    setStatus('loading')
    setError(null)
    try {
      const config = await send<ExtensionConfig | null>({ type: 'getConfig' })
      if (!config) {
        setStatus('unauthenticated')
        return
      }
      const href = window.location.href
      const url = normalizeProfileUrl(href)
      profileUrlRef.current = url

      // On search-results pages AND post-detail pages we skip the single-profile
      // scrape and switch the panel into bulk-import mode. The row set is scraped
      // differently per page type, but both flow through the same bulk UI.
      const searchMode = isSearchResultsUrl(href)
      const postMode = !searchMode && isPostPage(href)
      setIsSearchMode(searchMode || postMode)
      setIsPostMode(postMode)
      if (searchMode || postMode) {
        setSearchRows(searchMode ? scrapeSearchResults() : scrapePostReactorsAndCommenters())
        // We still want /me to confirm auth, but skip tasks/lookup since they
        // don't apply to the bulk page.
        const meSettled = await send<MeDto>({ type: 'me' }).then(
          v => ({ status: 'fulfilled' as const, value: v }),
          e => ({ status: 'rejected' as const, reason: e }),
        )
        if (meSettled.status === 'rejected') {
          if (isAuthError(meSettled.reason)) {
            setStatus('auth-expired')
            setError(meSettled.reason instanceof Error ? meSettled.reason.message : 'Session expired')
          } else {
            setError(meSettled.reason instanceof Error ? meSettled.reason.message : 'Unknown error')
            setStatus('error')
          }
          return
        }
        setMe(meSettled.value)
        setStatus('ready')
        return
      }

      // Re-scrape whenever we load (profile DOM may have rehydrated).
      const result = scrapeProfileWithTelemetry()
      setProfile(result.payload)
      // Fire-and-forget telemetry ping if the scraper missed fields we care
      // about — lets us detect LinkedIn DOM changes without waiting on users.
      if (result.missedFields.length > 0 && result.payload) {
        void send({
          type: 'reportScraperMiss',
          layout: result.layout,
          url,
          missedFields: result.missedFields,
          scraperVersion: SCRAPER_VERSION,
        }).catch(() => {})
      }

      // Call each endpoint independently so a single failing route (e.g. an
      // endpoint that doesn't exist on an older backend) doesn't tank the
      // whole panel. Only /me determines connectivity — the other two are
      // "nice to have" enrichments and degrade silently.
      const [meSettled, tasksSettled, lookupSettled] = await Promise.allSettled([
        send<MeDto>({ type: 'me' }),
        send<ExtensionTaskDto[]>({ type: 'tasksForUrl', url }),
        send<ContactLookup | null>({ type: 'lookupContact', url }),
      ])

      // /me is the truth source for auth. If it fails with an auth error,
      // the key is actually dead.
      if (meSettled.status === 'rejected') {
        if (isAuthError(meSettled.reason)) {
          setStatus('auth-expired')
          setError(meSettled.reason instanceof Error ? meSettled.reason.message : 'Session expired')
        } else {
          setError(meSettled.reason instanceof Error ? meSettled.reason.message : 'Unknown error')
          setStatus('error')
        }
        return
      }

      setMe(meSettled.value)
      // Secondary calls: log failures, don't block the UI.
      if (tasksSettled.status === 'fulfilled') setTasks(tasksSettled.value)
      else console.warn('[lead-rush] tasksForUrl failed', tasksSettled.reason)

      if (lookupSettled.status === 'fulfilled') setExisting(lookupSettled.value)
      else console.warn('[lead-rush] lookupContact failed', lookupSettled.reason)

      setStatus('ready')
    } catch (err) {
      // Anything that escapes the settled handlers above (shouldn't normally happen)
      setError(err instanceof Error ? err.message : 'Unknown error')
      setStatus('error')
    }
  }

  function openExtensionPopup() {
    // Best-effort — we can't programmatically open the popup, so prompt the user.
    showToast('Click the Lead Rush icon in your toolbar to reconnect')
  }

  useEffect(() => { void load() }, [])

  // Restore the last-used auto-enroll sequence id from extension storage once per mount.
  useEffect(() => {
    void send<string | null>({ type: 'getAutoEnrollSequenceId' })
      .then(id => setAutoEnrollSequenceIdState(id ?? null))
      .catch(() => {})
  }, [])

  // Lazy-load the sequence list whenever the panel is ready — the auto-enroll
  // dropdown needs it, not just the explicit enroll picker.
  useEffect(() => {
    if (status !== 'ready') return
    if (sequences !== null) return
    void send<ExtensionSequence[]>({ type: 'listSequences' })
      .then(setSequences)
      .catch(err => console.warn('[lead-rush] listSequences failed', err))
  }, [status, sequences])

  // Saved-search check on search/bulk mount. Returns "new since last visit"
  // URLs AND updates the known-set atomically, so opening the panel twice
  // in quick succession doesn't re-flag the same rows.
  useEffect(() => {
    if (status !== 'ready') return
    if (!isSearchMode || isPostMode) return            // post pages aren't saveable
    if (searchRows.length === 0) return                  // wait for the scrape
    if (savedSearchCheck !== null) return                // already checked this mount

    const url = window.location.href
    const currentProfileUrls = searchRows.map(r => r.linkedinUrl)
    void send<CheckSearchResult>({ type: 'checkSavedSearch', url, currentProfileUrls })
      .then(setSavedSearchCheck)
      .catch(err => console.warn('[lead-rush] checkSavedSearch failed', err))
  }, [status, isSearchMode, isPostMode, searchRows, savedSearchCheck])

  // Lazy-fetch possible duplicate matches once the first scrape + lookup settle.
  // Only runs when there's NO exact linkedinUrl match — if `existing` is set,
  // the panel already shows that contact, no fuzzy lookup needed.
  useEffect(() => {
    if (status !== 'ready') return
    if (!profile?.firstName) return
    if (existing) { setPossibleMatches(null); return }
    if (lastImport) { setPossibleMatches(null); return } // user just imported — don't nag
    if (possibleMatches !== null) return                   // already fetched this session

    let cancelled = false
    void send<PossibleMatch[]>({ type: 'possibleMatches', payload: profile })
      .then(list => { if (!cancelled) setPossibleMatches(list) })
      .catch(err => {
        console.warn('[lead-rush] possibleMatches failed', err)
        if (!cancelled) setPossibleMatches([])
      })
    return () => { cancelled = true }
  }, [status, profile, existing, lastImport])

  // Hotkey bridge — the background service worker forwards the Cmd/Ctrl+Shift+L
  // command here so users can import the visible profile without clicking.
  useEffect(() => {
    const listener = (msg: { type?: string }) => {
      if (msg?.type === 'hotkey-import') {
        setExpanded(true)
        void handleImport()
      }
    }
    chrome.runtime.onMessage.addListener(listener)
    return () => chrome.runtime.onMessage.removeListener(listener)
  }, [profile, existing, lastImport])

  function showToast(msg: string) {
    setToast(msg)
    setTimeout(() => setToast(null), 2500)
  }

  /**
   * Bulk-import every scraped search-result row. Sequential with a small pause
   * between rows so we don't hammer the backend AND we stay inside LinkedIn's
   * "reasonable activity" envelope (a burst of 25 POSTs in 500ms would read
   * as botty to anything watching network patterns).
   */
  async function handleBulkImport() {
    if (searchRows.length === 0) return
    cancelBulkRef.current = false
    setBusy(true)
    setBulkProgress({ total: searchRows.length, done: 0, created: 0, updated: 0, failed: 0 })
    try {
      for (let i = 0; i < searchRows.length; i++) {
        if (cancelBulkRef.current) break
        const row = searchRows[i]!
        try {
          const result = await send<LinkedInImportResult>({ type: 'importContact', payload: row })
          setBulkProgress(p => p ? {
            ...p,
            done: p.done + 1,
            created: p.created + (result.created ? 1 : 0),
            updated: p.updated + (result.created ? 0 : 1),
          } : p)
        } catch (err) {
          if (isAuthError(err)) {
            // Stop immediately — continuing would fail every remaining row.
            setStatus('auth-expired')
            setError(err instanceof Error ? err.message : 'Session expired')
            return
          }
          console.warn('[lead-rush] bulk import row failed', err)
          setBulkProgress(p => p ? { ...p, done: p.done + 1, failed: p.failed + 1 } : p)
        }
        // Small pause between rows. Tighter than nothing, looser than rapid fire.
        await new Promise(r => setTimeout(r, 250))
      }
    } finally {
      setBusy(false)
    }
  }

  function cancelBulkImport() {
    cancelBulkRef.current = true
  }

  function refreshSearchRows() {
    setSearchRows(isPostMode ? scrapePostReactorsAndCommenters() : scrapeSearchResults())
  }

  async function handleImport() {
    const payload = profile ?? scrapeProfile()
    if (!payload) { showToast('Could not read this profile'); return }
    // Merge in the sticky auto-enroll selection — the backend handles both
    // import + enroll in a single round-trip so the panel doesn't need a
    // second call on the happy path.
    const enrollId = autoEnrollSequenceId ?? undefined
    const withEnroll: LinkedInImportPayload = enrollId
      ? { ...payload, autoEnrollSequenceId: enrollId }
      : payload
    setBusy(true)
    try {
      const result = await send<LinkedInImportResult>({ type: 'importContact', payload: withEnroll })
      setLastImport(result)
      // Prioritise the job-change signal in the toast — it's the headline news
      // when a prospect's role or employer shifts under a rep's radar.
      const headline = result.jobChanges?.find(c => c.type === 'COMPANY')
                       ?? result.jobChanges?.find(c => c.type === 'TITLE')
      if (headline) {
        const verb = headline.type === 'COMPANY' ? 'moved to' : 'now'
        showToast(`✨ Job change: ${verb} ${headline.to}`)
      } else if (result.enrolledSequenceName) {
        showToast(`${result.created ? 'Imported' : 'Updated'} & enrolled in ${result.enrolledSequenceName}`)
      } else if (result.enrollError) {
        showToast(`${result.created ? 'Imported' : 'Updated'} — enroll failed: ${result.enrollError}`)
      } else {
        showToast(result.created ? 'Contact created' : 'Contact updated')
      }
      // Refresh tasks + existing-contact summary in parallel — new contact may
      // have matched a pending task, and the lookup card should now show stats.
      const refreshUrl = profileUrlRef.current ?? payload.linkedinUrl
      const [refreshedTasks, lookup] = await Promise.all([
        send<ExtensionTaskDto[]>({ type: 'tasksForUrl', url: refreshUrl }),
        send<ContactLookup | null>({ type: 'lookupContact', url: refreshUrl }),
      ])
      setTasks(refreshedTasks)
      setExisting(lookup)
    } catch (err) {
      // 401/403 means the key was revoked mid-session. Flip to the recovery UI
      // instead of just toasting — the user needs to reconnect, not retry.
      if (isAuthError(err)) {
        setStatus('auth-expired')
        setError(err instanceof Error ? err.message : 'Session expired')
      } else {
        showToast(err instanceof Error ? err.message : 'Import failed')
      }
    } finally {
      setBusy(false)
    }
  }

  // Lazy-load sequences when the user opens the picker — no point fetching
  // them for users who never touch enroll.
  async function openSequencePicker() {
    setShowSequencePicker(true)
    if (sequences !== null) return
    try {
      const list = await send<ExtensionSequence[]>({ type: 'listSequences' })
      setSequences(list)
      const firstEnrollable = list.find(s => s.hasDefaultMailbox)
      if (firstEnrollable) setSelectedSequenceId(firstEnrollable.id)
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Could not load sequences')
    }
  }

  async function handleEnroll() {
    const contactId = existing?.contactId ?? lastImport?.contactId
    if (!contactId) { showToast('Import first, then enroll'); return }
    if (!selectedSequenceId) { showToast('Pick a sequence'); return }
    setBusy(true)
    try {
      const result = await send<EnrollResult>({
        type: 'enroll',
        sequenceId: selectedSequenceId,
        contactId,
      })
      showToast(`Enrolled in ${result.sequenceName}`)
      setShowSequencePicker(false)
      // Refresh the lookup so the new enrollment appears in the "Active sequences" pill list
      const refreshUrl = profileUrlRef.current
      if (refreshUrl) {
        const lookup = await send<ContactLookup | null>({ type: 'lookupContact', url: refreshUrl })
        setExisting(lookup)
      }
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Enroll failed')
    } finally {
      setBusy(false)
    }
  }

  async function handleSaveNote() {
    const contactId = existing?.contactId ?? lastImport?.contactId
    if (!contactId) { showToast('Import first'); return }
    const body = noteBody.trim()
    if (!body) { showToast('Note is empty'); return }
    setBusy(true)
    try {
      await send({ type: 'addNote', contactId, body })
      setNoteBody('')
      setShowNoteComposer(false)
      showToast('Note saved')
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Note failed')
    } finally {
      setBusy(false)
    }
  }

  async function handleGenerateOpener() {
    if (!profile?.firstName) { showToast('Open a profile first'); return }
    setOpenerLoading(true)
    setOpenerCopied(false)
    try {
      const result = await send<OpenerResult>({
        type: 'generateOpener',
        payload: {
          firstName: profile.firstName,
          lastName: profile.lastName,
          title: profile.title,
          companyName: profile.companyName,
          location: profile.location,
          about: profile.about,
          channel: openerChannel,
          valueProp: openerValueProp.trim() || undefined,
        },
      })
      setOpenerText(result.text)
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Opener failed')
    } finally {
      setOpenerLoading(false)
    }
  }

  async function handleMergeInto(match: PossibleMatch) {
    const payload = profile ?? scrapeProfile()
    if (!payload) { showToast('Could not read this profile'); return }
    setBusy(true)
    try {
      const result = await send<LinkedInImportResult>({
        type: 'importContact',
        payload: { ...payload, mergeIntoContactId: match.contactId },
      })
      setLastImport(result)
      setPossibleMatches(null)
      showToast(`Merged into ${result.fullName}`)
      const refreshUrl = profileUrlRef.current ?? payload.linkedinUrl
      const lookup = await send<ContactLookup | null>({ type: 'lookupContact', url: refreshUrl })
      setExisting(lookup)
    } catch (err) {
      if (isAuthError(err)) {
        setStatus('auth-expired')
        setError(err instanceof Error ? err.message : 'Session expired')
      } else {
        showToast(err instanceof Error ? err.message : 'Merge failed')
      }
    } finally {
      setBusy(false)
    }
  }

  function handleDismissMatch(contactId: string) {
    setDismissedMatchIds(prev => new Set(prev).add(contactId))
  }

  async function handleAutoEnrollChange(id: string) {
    const next = id === '' ? null : id
    setAutoEnrollSequenceIdState(next)
    await send({ type: 'setAutoEnrollSequenceId', id: next }).catch(() => {})
  }

  async function handleSaveSearch() {
    // Default name = prompt with filters summary; prompts don't render inside
    // Shadow DOM cleanly on LinkedIn, so we fall back to a sensible auto-name.
    const defaultName = `Saved search · ${new Date().toLocaleDateString(undefined, {
      month: 'short', day: 'numeric',
    })}`
    setSaveSearchBusy(true)
    try {
      const url = window.location.href
      const seenProfileUrls = searchRows.map(r => r.linkedinUrl)
      const saved = await send<SavedSearchDto>({
        type: 'saveSearch',
        name: defaultName,
        url,
        seenProfileUrls,
      })
      // Reflect the saved state immediately — no new profiles since we just
      // seeded the known-set from the current list.
      setSavedSearchCheck({
        savedSearchId: saved.id,
        name: saved.name,
        newProfileUrls: [],
        knownProfileCount: saved.knownProfileCount,
      })
      showToast('Search saved — we\'ll flag new profiles next time')
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Save failed')
    } finally {
      setSaveSearchBusy(false)
    }
  }

  async function handleCopyOpener() {
    if (!openerText) return
    try {
      await navigator.clipboard.writeText(openerText)
      setOpenerCopied(true)
      setTimeout(() => setOpenerCopied(false), 1500)
    } catch {
      showToast('Copy failed — select & copy manually')
    }
  }

  async function handleCompleteTask(task: ExtensionTaskDto) {
    setBusy(true)
    try {
      await send({ type: 'completeTask', id: task.id })
      setTasks(prev => prev.filter(t => t.id !== task.id))
      showToast('Task completed')
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Could not complete task')
    } finally {
      setBusy(false)
    }
  }

  async function copyDescription(description: string | null) {
    if (!description) return
    await navigator.clipboard.writeText(description)
    showToast('Copied to clipboard')
  }

  // Compact relative timestamp — "just now", "3h", "2d", then fall back to short date.
  function relative(iso: string | null): string {
    if (!iso) return '—'
    const d = new Date(iso).getTime()
    const diffMs = Date.now() - d
    const mins = Math.floor(diffMs / 60_000)
    if (mins < 1) return 'just now'
    if (mins < 60) return `${mins}m ago`
    const hours = Math.floor(mins / 60)
    if (hours < 24) return `${hours}h ago`
    const days = Math.floor(hours / 24)
    if (days < 30) return `${days}d ago`
    return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
  }

  function formatCurrency(amount: number, currency: string | null): string {
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: currency ?? 'USD',
        maximumFractionDigits: 0,
      }).format(amount)
    } catch {
      return `${currency ?? ''} ${amount}`.trim()
    }
  }

  function formatDate(iso: string): string {
    try {
      return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
    } catch {
      return iso
    }
  }

  /**
   * Render a teammate task as a short sentence. Past tense when completed,
   * present tense when pending — keeps the warning scannable.
   */
  function collisionVerb(action: string, status: string): string {
    const completed = status === 'COMPLETED'
    switch (action) {
      case 'LINKEDIN_MESSAGE': return completed ? 'sent a LinkedIn message' : 'has a LinkedIn message queued'
      case 'LINKEDIN_CONNECT': return completed ? 'sent a connection request' : 'has a connection request queued'
      case 'CALL':             return completed ? 'logged a call' : 'has a call scheduled'
      case 'NOTE':             return 'left a note'
      case 'MANUAL':           return completed ? 'completed a task' : 'has a task open'
      default:                 return completed ? 'recently touched this contact' : 'is working on this contact'
    }
  }

  // Collapsed state — thin pill in the corner
  if (!expanded) {
    return (
      <div
        className="lr-reset lr-root"
        style={{
          position: 'fixed', right: 20, bottom: 20, zIndex: 2147483647,
        }}
      >
        <button
          className="lr-button lr-button--primary"
          onClick={() => setExpanded(true)}
          style={{ borderRadius: 999, padding: '8px 14px', boxShadow: '0 8px 30px hsl(243 70% 63% / 0.4)' }}
        >
          <span style={{
            width: 8, height: 8, borderRadius: 999,
            background: tasks.length > 0 ? 'hsl(150 70% 70%)' : 'rgba(255,255,255,0.6)',
          }} />
          Lead Rush{tasks.length > 0 ? ` · ${tasks.length}` : ''}
        </button>
      </div>
    )
  }

  return (
    <div
      className="lr-reset lr-root lr-fade-up"
      style={{
        position: 'fixed', right: 20, bottom: 20, zIndex: 2147483647,
        width: 340, maxHeight: 540,
        borderRadius: 14, overflow: 'hidden',
        // Fully opaque wrapper so LinkedIn's content never bleeds through,
        // no matter what class the children apply.
        background: 'hsl(240 6% 6%)',
        boxShadow: '0 20px 60px -20px rgba(0,0,0,0.7), 0 0 0 1px hsl(240 5% 100% / 0.1)',
      }}
    >
      {/* Header */}
      <div style={{
        padding: '12px 14px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        background: 'hsl(240 6% 8%)',
        borderBottom: '1px solid var(--lr-border)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <BrandMark size={20} />
          <div style={{ fontWeight: 600, fontSize: 13 }}>Lead Rush</div>
        </div>
        <button className="lr-button lr-button--ghost" style={{ padding: '4px 6px' }} onClick={() => setExpanded(false)}>
          ×
        </button>
      </div>

      {/*
        Solid body — no blur/translucency. Previously used .lr-glass which is
        72% opaque; over LinkedIn's white content the text washed out.
      */}
      <div style={{
        padding: 14,
        maxHeight: 460, overflowY: 'auto',
        background: 'hsl(240 6% 7%)',
        color: 'var(--lr-fg)',
      }}>
        {status === 'loading' && (
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', color: 'var(--lr-fg-muted)' }}>
            <span className="lr-spinner" /> Reading profile…
          </div>
        )}

        {status === 'unauthenticated' && (
          <div>
            <p className="lr-muted" style={{ fontSize: 12, marginBottom: 10 }}>
              Open the Lead Rush extension icon and paste your API key to connect.
            </p>
            <button className="lr-button lr-button--primary" style={{ width: '100%' }} onClick={load}>
              Check again
            </button>
          </div>
        )}

        {status === 'auth-expired' && (
          <div>
            <div style={{
              padding: 10, borderRadius: 'var(--lr-radius)',
              background: 'hsl(30 90% 58% / 0.12)',
              border: '1px solid hsl(30 90% 58% / 0.35)',
              color: 'hsl(30 100% 82%)', fontSize: 12, marginBottom: 10,
              display: 'flex', gap: 8, alignItems: 'start',
            }}>
              <span style={{ fontWeight: 700 }}>⚠</span>
              <span>
                Your API key was revoked or expired. Paste a fresh one from the popup to keep working.
              </span>
            </div>
            <div style={{ display: 'flex', gap: 6 }}>
              <button
                className="lr-button lr-button--outline"
                style={{ flex: 1 }}
                onClick={openExtensionPopup}
              >
                Reconnect
              </button>
              <button
                className="lr-button lr-button--primary"
                style={{ flex: 1 }}
                onClick={load}
              >
                Retry
              </button>
            </div>
          </div>
        )}

        {status === 'error' && (
          <div>
            <div style={{
              padding: 10, borderRadius: 'var(--lr-radius)',
              background: 'hsl(0 72% 58% / 0.1)',
              border: '1px solid hsl(0 72% 58% / 0.3)',
              color: 'hsl(0 90% 85%)', fontSize: 12, marginBottom: 10,
            }}>{error}</div>
            <button className="lr-button lr-button--outline" style={{ width: '100%' }} onClick={load}>
              Retry
            </button>
          </div>
        )}

        {status === 'ready' && (
          <div>
            {me && (
              <div style={{
                fontSize: 11, color: 'var(--lr-fg-muted)', marginBottom: 10,
                textTransform: 'uppercase', letterSpacing: '0.05em',
              }}>
                {me.workspaceName}
              </div>
            )}

            {/* ─── Bulk import mode (search results pages) ─── */}
            {isSearchMode && (
              <div>
                <div className="lr-hairline" style={{
                  padding: 12, borderRadius: 'var(--lr-radius)', marginBottom: 10,
                  background: 'hsl(243 70% 50% / 0.06)',
                  borderColor: 'hsl(243 70% 50% / 0.3)',
                }}>
                  <div style={{
                    display: 'inline-flex', alignItems: 'center', gap: 6,
                    color: 'hsl(243 70% 78%)', fontSize: 11, fontWeight: 600,
                    textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 6,
                  }}>
                    <span style={{
                      width: 6, height: 6, borderRadius: 999, background: 'hsl(243 70% 68%)',
                    }} />
                    {isPostMode ? 'Post reactors & commenters' : 'Bulk import'}
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>
                    {searchRows.length} {searchRows.length === 1 ? 'profile' : 'profiles'} on this page
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--lr-fg-muted)', marginTop: 2 }}>
                    {isPostMode
                      ? 'Open the reactor list or scroll comments to reveal more, then refresh.'
                      : 'Scroll the page to load more, then refresh to rescan.'}
                  </div>

                  {/* Saved-search alerts — only on search pages, not post pages */}
                  {!isPostMode && savedSearchCheck !== null && (
                    <div style={{
                      marginTop: 10, paddingTop: 10,
                      borderTop: '1px solid hsl(240 5% 100% / 0.06)',
                    }}>
                      {savedSearchCheck.savedSearchId === null ? (
                        <button
                          className="lr-button lr-button--outline"
                          style={{ width: '100%', fontSize: 11 }}
                          disabled={saveSearchBusy || searchRows.length === 0}
                          onClick={handleSaveSearch}
                        >
                          {saveSearchBusy ? 'Saving…' : '☆ Save this search'}
                        </button>
                      ) : (
                        <div style={{
                          padding: 8, borderRadius: 'var(--lr-radius)',
                          background: savedSearchCheck.newProfileUrls.length > 0
                            ? 'hsl(150 70% 40% / 0.1)'
                            : 'hsl(240 6% 9%)',
                          border: savedSearchCheck.newProfileUrls.length > 0
                            ? '1px solid hsl(150 70% 40% / 0.3)'
                            : '1px solid var(--lr-border)',
                        }}>
                          <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--lr-fg)' }}>
                            {savedSearchCheck.newProfileUrls.length > 0
                              ? `✨ ${savedSearchCheck.newProfileUrls.length} new since last visit`
                              : '★ Saved — no new profiles'}
                          </div>
                          <div style={{ fontSize: 10, color: 'var(--lr-fg-muted)', marginTop: 2 }}>
                            {savedSearchCheck.name} · {savedSearchCheck.knownProfileCount} known
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Preview list — up to 5 names, with "new since last visit" dots */}
                  {searchRows.length > 0 && (
                    <div style={{
                      marginTop: 10, paddingTop: 10,
                      borderTop: '1px solid hsl(240 5% 100% / 0.06)',
                      display: 'flex', flexDirection: 'column', gap: 4,
                    }}>
                      {searchRows.slice(0, 5).map(row => {
                        const isNew = savedSearchCheck?.newProfileUrls?.includes(row.linkedinUrl)
                        return (
                          <div key={row.linkedinUrl} style={{
                            fontSize: 12, color: 'var(--lr-fg)',
                            display: 'flex', alignItems: 'center', gap: 6,
                          }}>
                            {isNew && (
                              <span title="New since last visit" style={{
                                width: 6, height: 6, borderRadius: 999,
                                background: 'hsl(150 70% 55%)', flexShrink: 0,
                              }} />
                            )}
                            <span style={{ fontWeight: 500 }}>
                              {[row.firstName, row.lastName].filter(Boolean).join(' ') || 'Unknown'}
                            </span>
                            {row.title && (
                              <span style={{ color: 'var(--lr-fg-muted)' }}> — {row.title}</span>
                            )}
                          </div>
                        )
                      })}
                      {searchRows.length > 5 && (
                        <div style={{ fontSize: 11, color: 'var(--lr-fg-muted)' }}>
                          +{searchRows.length - 5} more…
                        </div>
                      )}
                    </div>
                  )}

                  {/* Progress bar */}
                  {bulkProgress && (
                    <div style={{ marginTop: 10 }}>
                      <div style={{
                        display: 'flex', justifyContent: 'space-between',
                        fontSize: 11, color: 'var(--lr-fg-muted)', marginBottom: 4,
                      }}>
                        <span>Progress</span>
                        <span>{bulkProgress.done} / {bulkProgress.total}</span>
                      </div>
                      <div style={{
                        height: 4, borderRadius: 999,
                        background: 'hsl(240 5% 100% / 0.08)', overflow: 'hidden',
                      }}>
                        <div style={{
                          height: '100%', width: `${(bulkProgress.done / bulkProgress.total) * 100}%`,
                          background: 'hsl(243 70% 63%)',
                          transition: 'width 0.2s ease-out',
                        }} />
                      </div>
                      <div style={{
                        display: 'flex', gap: 10,
                        fontSize: 11, marginTop: 6, color: 'var(--lr-fg-muted)',
                      }}>
                        <span>✓ <strong style={{ color: 'hsl(150 70% 60%)' }}>{bulkProgress.created}</strong> new</span>
                        <span>⟳ <strong>{bulkProgress.updated}</strong> updated</span>
                        {bulkProgress.failed > 0 && (
                          <span>✕ <strong style={{ color: 'hsl(0 72% 68%)' }}>{bulkProgress.failed}</strong> failed</span>
                        )}
                      </div>
                    </div>
                  )}

                  {/* Actions */}
                  <div style={{ display: 'flex', gap: 6, marginTop: 10 }}>
                    {!busy ? (
                      <>
                        <button
                          className="lr-button lr-button--outline"
                          style={{ flex: '0 0 auto' }}
                          onClick={refreshSearchRows}
                        >
                          ↻ Rescan
                        </button>
                        <button
                          className="lr-button lr-button--primary"
                          style={{ flex: 1 }}
                          disabled={searchRows.length === 0}
                          onClick={handleBulkImport}
                        >
                          {bulkProgress ? 'Run again' : `Import ${searchRows.length} profiles`}
                        </button>
                      </>
                    ) : (
                      <button
                        className="lr-button lr-button--outline"
                        style={{ width: '100%' }}
                        onClick={cancelBulkImport}
                      >
                        Cancel
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )}

            {/* ─── Single-profile mode (everything below is gated on NOT being in search mode) ─── */}
            {!isSearchMode && (<>

            {/* Already-in-Lead-Rush card — rendered when the URL matches a contact */}
            {existing && (
              <div
                className="lr-hairline"
                style={{
                  padding: 10, borderRadius: 'var(--lr-radius)', marginBottom: 10,
                  background: 'hsl(150 70% 50% / 0.06)',
                  borderColor: 'hsl(150 70% 50% / 0.3)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                  <div style={{
                    display: 'inline-flex', alignItems: 'center', gap: 6,
                    color: 'hsl(150 70% 70%)', fontSize: 11, fontWeight: 600,
                    textTransform: 'uppercase', letterSpacing: '0.05em',
                  }}>
                    <span style={{
                      width: 6, height: 6, borderRadius: 999, background: 'hsl(150 70% 60%)',
                    }} />
                    In Lead Rush
                  </div>
                  <a
                    href={existing.contactUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{ fontSize: 11, color: 'hsl(243 80% 78%)', textDecoration: 'none' }}
                  >
                    Open ↗
                  </a>
                </div>
                <div style={{ fontWeight: 600, fontSize: 13, marginTop: 6 }}>
                  {existing.fullName}
                </div>
                {(existing.title || existing.companyName) && (
                  <div style={{ fontSize: 12, color: 'var(--lr-fg-muted)' }}>
                    {existing.title ?? ''}{existing.title && existing.companyName ? ' · ' : ''}{existing.companyName ?? ''}
                  </div>
                )}

                {/* Stats strip */}
                <div style={{
                  display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: 6, marginTop: 8, paddingTop: 8,
                  borderTop: '1px solid hsl(240 5% 100% / 0.06)',
                }}>
                  <div>
                    <div style={{ fontSize: 10, color: 'var(--lr-fg-muted)' }}>Score</div>
                    <div style={{ fontSize: 13, fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                      {existing.leadScore}
                    </div>
                  </div>
                  <div>
                    <div style={{ fontSize: 10, color: 'var(--lr-fg-muted)' }}>Stage</div>
                    <div style={{ fontSize: 12, fontWeight: 600, textTransform: 'capitalize' }}>
                      {(existing.lifecycleStage ?? '—').toLowerCase()}
                    </div>
                  </div>
                  <div>
                    <div style={{ fontSize: 10, color: 'var(--lr-fg-muted)' }}>Last touch</div>
                    <div style={{ fontSize: 12, fontWeight: 600 }}>
                      {relative(existing.lastActivityAt ?? existing.createdAt)}
                    </div>
                  </div>
                </div>

                {/* Job-change banner — surfaced right under the stats strip because
                    a fresh role/employer change is the most action-worthy signal on a revisit. */}
                {existing.recentJobChanges && existing.recentJobChanges.length > 0 && (
                  <div style={{
                    marginTop: 8, padding: 8,
                    background: 'hsl(200 85% 45% / 0.1)',
                    border: '1px solid hsl(200 85% 45% / 0.3)',
                    borderRadius: 'var(--lr-radius)',
                  }}>
                    <div style={{
                      fontSize: 10, color: 'hsl(200 85% 70%)',
                      textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
                    }}>
                      ✨ Recent change
                    </div>
                    {existing.recentJobChanges.slice(0, 2).map((c, i) => (
                      <div key={i} style={{ fontSize: 12, lineHeight: 1.5 }}>
                        {c.type === 'COMPANY' ? 'Moved to' : 'Now'}{' '}
                        <strong>{c.to}</strong>
                        {c.from && (
                          <span style={{ color: 'var(--lr-fg-muted)' }}> (was {c.from})</span>
                        )}
                        <span style={{ color: 'var(--lr-fg-muted)' }}> · {relative(c.at)}</span>
                      </div>
                    ))}
                  </div>
                )}

                {/* Active sequences */}
                {existing.activeSequenceNames.length > 0 && (
                  <div style={{ marginTop: 8 }}>
                    <div style={{
                      fontSize: 10, color: 'var(--lr-fg-muted)',
                      textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
                    }}>
                      Active sequences
                    </div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                      {existing.activeSequenceNames.map(name => (
                        <span key={name} className="lr-badge lr-badge--primary">{name}</span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Open deals — surfaces value + stage + close date */}
                {existing.deals && existing.deals.length > 0 && (
                  <div style={{ marginTop: 8 }}>
                    <div style={{
                      fontSize: 10, color: 'var(--lr-fg-muted)',
                      textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
                    }}>
                      Open deals
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      {existing.deals.map(d => (
                        <a key={d.dealId} href={d.dealUrl} target="_blank" rel="noopener noreferrer" style={{
                          display: 'block', padding: 6,
                          background: 'hsl(240 6% 9%)',
                          border: '1px solid var(--lr-border)',
                          borderRadius: 'var(--lr-radius)',
                          textDecoration: 'none', color: 'var(--lr-fg)',
                        }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 6, fontSize: 12 }}>
                            <span style={{ fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                              {d.name}
                            </span>
                            {d.valueAmount != null && (
                              <span style={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums', flexShrink: 0 }}>
                                {formatCurrency(d.valueAmount, d.valueCurrency)}
                              </span>
                            )}
                          </div>
                          <div style={{ display: 'flex', gap: 6, marginTop: 2, fontSize: 10, color: 'var(--lr-fg-muted)' }}>
                            <span style={{
                              padding: '1px 6px', borderRadius: 999,
                              background: d.stageColor ? `${d.stageColor}22` : 'hsl(240 6% 14%)',
                              color: d.stageColor ?? 'var(--lr-fg-muted)',
                              border: `1px solid ${d.stageColor ?? 'var(--lr-border)'}`,
                            }}>
                              {d.stageName}
                            </span>
                            <span>{d.winProbability}%</span>
                            {d.expectedCloseAt && (
                              <span>· close {formatDate(d.expectedCloseAt)}</span>
                            )}
                          </div>
                        </a>
                      ))}
                    </div>
                  </div>
                )}

                {/* Collision warning — a teammate recently touched this contact */}
                {existing.collisions && existing.collisions.length > 0 && (
                  <div style={{
                    marginTop: 8, padding: 8,
                    background: 'hsl(38 92% 50% / 0.08)',
                    border: '1px solid hsl(38 92% 50% / 0.25)',
                    borderRadius: 'var(--lr-radius)',
                  }}>
                    <div style={{
                      fontSize: 10, color: 'hsl(38 92% 65%)',
                      textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
                      display: 'flex', alignItems: 'center', gap: 4,
                    }}>
                      ⚠ Teammate activity
                    </div>
                    {existing.collisions.map((c, i) => (
                      <div key={i} style={{ fontSize: 12, lineHeight: 1.5 }}>
                        <strong>{c.userName}</strong>
                        {' '}{collisionVerb(c.action, c.status)}
                        {' '}<span style={{ color: 'var(--lr-fg-muted)' }}>· {relative(c.at)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Duplicate detector — surfaced only when no exact linkedinUrl match exists */}
            {!existing && !lastImport && profile && possibleMatches && possibleMatches.length > 0 && (
              (() => {
                const visible = possibleMatches.filter(m => !dismissedMatchIds.has(m.contactId))
                if (visible.length === 0) return null
                return (
                  <div style={{
                    padding: 10, marginBottom: 10,
                    background: 'hsl(38 92% 50% / 0.08)',
                    border: '1px solid hsl(38 92% 50% / 0.25)',
                    borderRadius: 'var(--lr-radius)',
                  }}>
                    <div style={{
                      fontSize: 10, color: 'hsl(38 92% 65%)',
                      textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 6,
                    }}>
                      Could this be the same person?
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      {visible.map(m => (
                        <div key={m.contactId} style={{
                          padding: 8, borderRadius: 'var(--lr-radius)',
                          background: 'hsl(240 6% 9%)',
                          border: '1px solid var(--lr-border)',
                        }}>
                          <div style={{ fontWeight: 600, fontSize: 12 }}>{m.fullName}</div>
                          {(m.title || m.companyName) && (
                            <div style={{ fontSize: 11, color: 'var(--lr-fg-muted)' }}>
                              {m.title ?? ''}{m.title && m.companyName ? ' · ' : ''}{m.companyName ?? ''}
                            </div>
                          )}
                          <div style={{ fontSize: 10, color: 'var(--lr-fg-muted)', marginTop: 2 }}>
                            {m.linkedinUrl
                              ? 'Has a different LinkedIn URL on file'
                              : 'No LinkedIn URL on file — matched by '
                                + (m.reason === 'NAME_COMPANY' ? 'name + company' : 'name')}
                          </div>
                          <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                            <button
                              className="lr-button lr-button--ghost"
                              style={{ flex: 1, fontSize: 11, padding: '4px 8px' }}
                              disabled={busy}
                              onClick={() => handleDismissMatch(m.contactId)}
                            >
                              Different person
                            </button>
                            <button
                              className="lr-button lr-button--primary"
                              style={{ flex: 1, fontSize: 11, padding: '4px 8px' }}
                              disabled={busy || !!m.linkedinUrl}
                              title={m.linkedinUrl ? 'Already has a LinkedIn URL' : ''}
                              onClick={() => handleMergeInto(m)}
                            >
                              Merge into this
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )
              })()
            )}

            {/* Profile scraped summary — the Import/Refresh affordance */}
            {profile && (
              <div className="lr-hairline" style={{
                padding: 10, borderRadius: 'var(--lr-radius)', marginBottom: 10,
              }}>
                <div style={{
                  fontSize: 10, color: 'var(--lr-fg-muted)',
                  textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
                }}>
                  {existing ? 'Scraped from page' : 'New contact'}
                </div>
                <div style={{ fontWeight: 600, fontSize: 13 }}>
                  {[profile.firstName, profile.lastName].filter(Boolean).join(' ') || 'Unknown name'}
                </div>
                {profile.title && (
                  <div style={{ fontSize: 12, color: 'var(--lr-fg-muted)' }}>
                    {profile.title}{profile.companyName ? ` · ${profile.companyName}` : ''}
                  </div>
                )}

                {/* Auto-enroll: sticky, browser-local. Hidden until a sequence exists. */}
                {sequences && sequences.length > 0 && !existing && (
                  <div style={{ marginTop: 10 }}>
                    <label style={{
                      display: 'block', fontSize: 10, color: 'var(--lr-fg-muted)',
                      textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
                    }}>
                      Auto-enroll after import
                    </label>
                    <select
                      value={autoEnrollSequenceId ?? ''}
                      onChange={e => void handleAutoEnrollChange(e.target.value)}
                      disabled={busy}
                      style={{
                        width: '100%', padding: '6px 8px', fontSize: 12,
                        background: 'hsl(240 6% 9%)', color: 'var(--lr-fg)',
                        border: '1px solid var(--lr-border)',
                        borderRadius: 'var(--lr-radius)', fontFamily: 'inherit',
                      }}
                    >
                      <option value="">None — import only</option>
                      {sequences.map(s => (
                        <option
                          key={s.id}
                          value={s.id}
                          disabled={!s.hasDefaultMailbox}
                        >
                          {s.name}{s.hasDefaultMailbox ? '' : ' (no mailbox)'}
                        </option>
                      ))}
                    </select>
                  </div>
                )}

                <div style={{ display: 'flex', gap: 6, marginTop: 10 }}>
                  <button
                    className="lr-button lr-button--primary"
                    style={{ flex: 1, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                    disabled={busy}
                    onClick={handleImport}
                  >
                    {busy && <span className="lr-spinner" />}
                    {busy
                      ? (existing ? 'Refreshing…' : autoEnrollSequenceId ? 'Importing & enrolling…' : 'Importing…')
                      : lastImport
                        ? (lastImport.enrolledSequenceName
                            ? '✓ Imported & enrolled'
                            : lastImport.created ? '✓ Imported' : '✓ Updated')
                        : existing
                          ? 'Refresh from page'
                          : autoEnrollSequenceId ? 'Import & enroll' : 'Import to Lead Rush'}
                  </button>
                </div>
              </div>
            )}

            {/* Quick actions row — enroll + note (contact must exist) */}
            {(existing?.contactId || lastImport?.contactId) && !showSequencePicker && !showNoteComposer && !showOpenerPanel && (
              <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
                <button
                  className="lr-button lr-button--outline"
                  style={{ flex: 1 }}
                  disabled={busy}
                  onClick={openSequencePicker}
                >
                  + Enroll
                </button>
                <button
                  className="lr-button lr-button--outline"
                  style={{ flex: 1 }}
                  disabled={busy}
                  onClick={() => setShowNoteComposer(true)}
                >
                  + Note
                </button>
              </div>
            )}

            {/* AI opener — works before import (stateless) as long as we scraped a profile */}
            {profile?.firstName && !showSequencePicker && !showNoteComposer && !showOpenerPanel && (
              <button
                className="lr-button lr-button--outline"
                style={{ width: '100%', marginBottom: 10 }}
                disabled={busy}
                onClick={() => setShowOpenerPanel(true)}
              >
                ✨ Generate opener
              </button>
            )}

            {/* Opener composer */}
            {showOpenerPanel && (
              <div className="lr-hairline" style={{
                padding: 10, borderRadius: 'var(--lr-radius)', marginBottom: 10,
                display: 'flex', flexDirection: 'column', gap: 8,
              }}>
                <div style={{
                  fontSize: 10, color: 'var(--lr-fg-muted)',
                  textTransform: 'uppercase', letterSpacing: '0.05em',
                }}>
                  AI opener
                </div>

                {/* Channel toggle */}
                <div style={{ display: 'flex', gap: 4 }}>
                  {(['LINKEDIN_NOTE', 'EMAIL'] as const).map(ch => (
                    <button
                      key={ch}
                      className={`lr-button ${openerChannel === ch ? 'lr-button--primary' : 'lr-button--ghost'}`}
                      style={{ flex: 1, fontSize: 11, padding: '4px 8px' }}
                      disabled={openerLoading}
                      onClick={() => { setOpenerChannel(ch); setOpenerText('') }}
                    >
                      {ch === 'LINKEDIN_NOTE' ? 'LinkedIn note' : 'Email'}
                    </button>
                  ))}
                </div>

                <input
                  type="text"
                  value={openerValueProp}
                  onChange={e => setOpenerValueProp(e.target.value)}
                  placeholder="Your value prop (optional)"
                  maxLength={500}
                  style={{
                    width: '100%', padding: '6px 8px', fontSize: 12,
                    background: 'hsl(240 6% 9%)', color: 'var(--lr-fg)',
                    border: '1px solid var(--lr-border)', borderRadius: 'var(--lr-radius)',
                    fontFamily: 'inherit',
                  }}
                />

                {openerText && (
                  <div style={{
                    padding: 8, fontSize: 12, lineHeight: 1.5,
                    background: 'hsl(240 6% 9%)', color: 'var(--lr-fg)',
                    border: '1px solid var(--lr-border)', borderRadius: 'var(--lr-radius)',
                    whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                  }}>
                    {openerText}
                    <div style={{
                      marginTop: 6, fontSize: 10, color: 'var(--lr-fg-muted)',
                      display: 'flex', justifyContent: 'space-between',
                    }}>
                      <span>{openerText.length} chars</span>
                      {openerChannel === 'LINKEDIN_NOTE' && (
                        <span style={{ color: openerText.length > 280 ? 'var(--lr-danger, #ef4444)' : 'var(--lr-fg-muted)' }}>
                          {openerText.length > 280 ? 'Over LinkedIn limit' : `${300 - openerText.length} left`}
                        </span>
                      )}
                    </div>
                  </div>
                )}

                <div style={{ display: 'flex', gap: 6 }}>
                  <button
                    className="lr-button lr-button--ghost"
                    style={{ flex: 1 }}
                    disabled={openerLoading}
                    onClick={() => { setShowOpenerPanel(false); setOpenerText(''); setOpenerCopied(false) }}
                  >
                    Close
                  </button>
                  {openerText && (
                    <button
                      className="lr-button lr-button--outline"
                      style={{ flex: 1 }}
                      disabled={openerLoading}
                      onClick={handleCopyOpener}
                    >
                      {openerCopied ? '✓ Copied' : 'Copy'}
                    </button>
                  )}
                  <button
                    className="lr-button lr-button--primary"
                    style={{ flex: 1 }}
                    disabled={openerLoading}
                    onClick={handleGenerateOpener}
                  >
                    {openerLoading && <span className="lr-spinner" />}
                    {openerLoading ? 'Writing…' : openerText ? 'Regenerate' : 'Generate'}
                  </button>
                </div>
              </div>
            )}

            {/* Note composer */}
            {showNoteComposer && (existing?.contactId || lastImport?.contactId) && (
              <div className="lr-hairline" style={{
                padding: 10, borderRadius: 'var(--lr-radius)', marginBottom: 10,
                display: 'flex', flexDirection: 'column', gap: 8,
              }}>
                <div style={{
                  fontSize: 10, color: 'var(--lr-fg-muted)',
                  textTransform: 'uppercase', letterSpacing: '0.05em',
                }}>
                  Drop a note
                </div>
                <textarea
                  value={noteBody}
                  onChange={e => setNoteBody(e.target.value)}
                  placeholder="Said he'd have budget in Q3…"
                  rows={4}
                  maxLength={2000}
                  autoFocus
                  style={{
                    width: '100%', resize: 'vertical', padding: '6px 8px', fontSize: 12,
                    background: 'hsl(240 6% 9%)', color: 'var(--lr-fg)',
                    border: '1px solid var(--lr-border)', borderRadius: 'var(--lr-radius)',
                    fontFamily: 'inherit',
                  }}
                />
                <div style={{ display: 'flex', gap: 6 }}>
                  <button
                    className="lr-button lr-button--ghost"
                    style={{ flex: 1 }}
                    onClick={() => { setShowNoteComposer(false); setNoteBody('') }}
                  >
                    Cancel
                  </button>
                  <button
                    className="lr-button lr-button--primary"
                    style={{ flex: 1 }}
                    disabled={busy || !noteBody.trim()}
                    onClick={handleSaveNote}
                  >
                    Save note
                  </button>
                </div>
              </div>
            )}

            {/* Enroll in sequence — only meaningful once we have a contact id */}
            {(existing?.contactId || lastImport?.contactId) && showSequencePicker && (
              <div className="lr-hairline" style={{
                padding: 10, borderRadius: 'var(--lr-radius)', marginBottom: 10,
              }}>
                {sequences === null ? (
                  <div style={{ fontSize: 12, color: 'var(--lr-fg-muted)', textAlign: 'center', padding: 6 }}>
                    Loading sequences…
                  </div>
                ) : sequences.length === 0 ? (
                  <div style={{ fontSize: 12, color: 'var(--lr-fg-muted)' }}>
                    No active sequences. Activate one in Lead Rush first.
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <div style={{
                      fontSize: 10, color: 'var(--lr-fg-muted)',
                      textTransform: 'uppercase', letterSpacing: '0.05em',
                    }}>
                      Enroll in
                    </div>
                    <select
                      value={selectedSequenceId}
                      onChange={e => setSelectedSequenceId(e.target.value)}
                      style={{
                        width: '100%', padding: '6px 8px', fontSize: 12,
                        background: 'hsl(240 6% 9%)', color: 'var(--lr-fg)',
                        border: '1px solid var(--lr-border)', borderRadius: 'var(--lr-radius)',
                      }}
                    >
                      <option value="">— pick one —</option>
                      {sequences.map(s => (
                        <option key={s.id} value={s.id} disabled={!s.hasDefaultMailbox}>
                          {s.name} ({s.stepCount} steps){!s.hasDefaultMailbox ? ' · no mailbox available' : ''}
                        </option>
                      ))}
                    </select>
                    <div style={{ display: 'flex', gap: 6 }}>
                      <button
                        className="lr-button lr-button--ghost"
                        style={{ flex: 1 }}
                        onClick={() => setShowSequencePicker(false)}
                      >
                        Cancel
                      </button>
                      <button
                        className="lr-button lr-button--primary"
                        style={{ flex: 1 }}
                        disabled={busy || !selectedSequenceId}
                        onClick={handleEnroll}
                      >
                        Enroll
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Tasks */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
              <div style={{ fontSize: 12, fontWeight: 600 }}>Pending tasks</div>
              <div style={{ fontSize: 11, color: 'var(--lr-fg-muted)' }}>
                {tasks.length} for this contact
              </div>
            </div>

            {tasks.length === 0 ? (
              <div className="lr-muted" style={{
                fontSize: 11, padding: '10px 12px',
                textAlign: 'center',
                borderRadius: 'var(--lr-radius)',
                border: '1px dashed var(--lr-border-strong)',
              }}>
                No LinkedIn tasks for this contact yet.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {tasks.map(task => (
                  <div
                    key={task.id}
                    className="lr-hairline"
                    style={{ padding: 10, borderRadius: 'var(--lr-radius)' }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                      <span className="lr-badge lr-badge--primary">
                        {task.type === 'LINKEDIN_CONNECT' ? 'Connect' : 'Message'}
                      </span>
                    </div>
                    {task.description && (
                      <div style={{
                        fontSize: 12, color: 'var(--lr-fg)',
                        background: 'hsl(240 5% 100% / 0.03)',
                        padding: 8, borderRadius: 6, marginBottom: 8,
                        whiteSpace: 'pre-wrap',
                      }}>
                        {task.description}
                      </div>
                    )}
                    <div style={{ display: 'flex', gap: 6 }}>
                      {task.description && (
                        <button
                          className="lr-button lr-button--outline"
                          style={{ flex: 1 }}
                          disabled={busy}
                          onClick={() => copyDescription(task.description)}
                        >
                          Copy copy
                        </button>
                      )}
                      <button
                        className="lr-button lr-button--primary"
                        style={{ flex: 1 }}
                        disabled={busy}
                        onClick={() => handleCompleteTask(task)}
                      >
                        Mark done
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}

            </>)}{/* end !isSearchMode */}
          </div>
        )}
      </div>

      {/* Toast */}
      {toast && (
        <div style={{
          position: 'absolute', left: 12, right: 12, bottom: 12,
          padding: '8px 12px', borderRadius: 'var(--lr-radius)',
          background: 'hsl(243 70% 20% / 0.85)',
          color: 'var(--lr-fg)', fontSize: 12,
          border: '1px solid hsl(243 70% 63% / 0.3)',
          backdropFilter: 'blur(10px)',
        }}>
          {toast}
        </div>
      )}
    </div>
  )
}
