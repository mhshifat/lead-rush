/**
 * Gmail sidebar floating panel.
 *
 * Flow:
 *   1. Poll `extractGmailThreadContext()` on hash changes + DOM mutations
 *   2. When the sender email changes, call the background `lookupByEmail`
 *   3. Render one of three states: loading, matched (contact card), or
 *      "Not in Lead Rush yet" with a deep-link to the web app.
 *
 * Deliberately narrow surface: no import, no enroll, no AI opener (yet).
 * The goal is to orient the user — "who is this?" — not to duplicate the
 * LinkedIn panel's affordances inside Gmail.
 */
import { useEffect, useRef, useState } from 'react'
import { BrandMark } from '@/components/BrandMark'
import { isAuthError, send } from '@/lib/messaging'
import { extractGmailThreadContext, type GmailThreadContext } from '@/lib/gmail-scraper'
import type { ContactLookup, ExtensionConfig, MeDto } from '@/lib/types'

type Status = 'loading' | 'unauthenticated' | 'auth-expired' | 'ready' | 'error'

/** How often we re-check the open thread — Gmail mutates the DOM often enough that
 * a MutationObserver bundle gets noisy; a cheap 1.5s poll is simpler. */
const POLL_INTERVAL_MS = 1500

export function GmailPanel() {
  const [status, setStatus] = useState<Status>('loading')
  const [expanded, setExpanded] = useState(true)
  const [me, setMe] = useState<MeDto | null>(null)

  const [thread, setThread] = useState<GmailThreadContext | null>(null)
  const [lookup, setLookup] = useState<ContactLookup | null>(null)
  const [lookupLoading, setLookupLoading] = useState(false)

  // Avoid hammering the API on every poll — only re-fetch when the primary
  // participant actually changes.
  const lastQueriedEmailRef = useRef<string | null>(null)

  async function loadMe() {
    try {
      const config = await send<ExtensionConfig | null>({ type: 'getConfig' })
      if (!config) { setStatus('unauthenticated'); return }
      const dto = await send<MeDto>({ type: 'me' })
      setMe(dto)
      setStatus('ready')
    } catch (err) {
      if (isAuthError(err)) {
        setStatus('auth-expired')
      } else {
        console.warn('[lead-rush][gmail] me failed', err)
        setStatus('error')
      }
    }
  }

  useEffect(() => { void loadMe() }, [])

  // Poll the DOM for thread-context changes.
  useEffect(() => {
    if (status !== 'ready') return
    let cancelled = false
    const tick = () => {
      if (cancelled) return
      const next = extractGmailThreadContext()
      setThread(prev => {
        if (prev?.primaryEmail === next?.primaryEmail
            && prev?.allParticipants.join(',') === next?.allParticipants.join(',')) {
          return prev
        }
        return next
      })
    }
    tick()
    const id = setInterval(tick, POLL_INTERVAL_MS)
    return () => { cancelled = true; clearInterval(id) }
  }, [status])

  // Fetch the contact lookup whenever the primary email changes.
  useEffect(() => {
    if (status !== 'ready') return
    if (!thread) { setLookup(null); lastQueriedEmailRef.current = null; return }
    if (lastQueriedEmailRef.current === thread.primaryEmail) return
    lastQueriedEmailRef.current = thread.primaryEmail

    setLookupLoading(true)
    void send<ContactLookup | null>({ type: 'lookupByEmail', email: thread.primaryEmail })
      .then(result => { setLookup(result ?? null) })
      .catch(err => {
        console.warn('[lead-rush][gmail] lookupByEmail failed', err)
        if (isAuthError(err)) setStatus('auth-expired')
      })
      .finally(() => setLookupLoading(false))
  }, [status, thread])

  // ── Collapsed pill ──
  if (!expanded) {
    return (
      <button
        className="lr-reset lr-fab"
        aria-label="Expand Lead Rush"
        onClick={() => setExpanded(true)}
        style={{
          position: 'fixed', bottom: 16, right: 16, zIndex: 2147483000,
          padding: '8px 12px', borderRadius: 999,
          background: 'hsl(240 6% 10%)', color: 'var(--lr-fg)',
          border: '1px solid var(--lr-border)',
          display: 'inline-flex', alignItems: 'center', gap: 6,
          fontSize: 12, cursor: 'pointer',
        }}
      >
        <BrandMark size={14} /> Lead Rush
      </button>
    )
  }

  // Not on an open thread — collapse to the FAB instead of hiding entirely.
  // Hiding made the extension invisible on the inbox list, so users couldn't
  // tell it was even loaded. Showing the pill orients them + gives a hint.
  if (status === 'ready' && !thread) {
    return (
      <button
        className="lr-reset lr-fab"
        aria-label="Lead Rush — open a thread to see contact info"
        title="Open a Gmail thread to see its Lead Rush contact"
        onClick={() => { /* no-op — nothing to expand without a thread */ }}
        style={{
          position: 'fixed', bottom: 16, right: 16, zIndex: 2147483000,
          padding: '8px 12px', borderRadius: 999,
          background: 'hsl(240 6% 10%)', color: 'var(--lr-fg)',
          border: '1px solid var(--lr-border)',
          display: 'inline-flex', alignItems: 'center', gap: 6,
          fontSize: 12, cursor: 'default', opacity: 0.75,
        }}
      >
        <BrandMark size={14} /> Lead Rush · open a thread
      </button>
    )
  }

  return (
    <div
      className="lr-reset"
      style={{
        position: 'fixed', bottom: 16, right: 16, zIndex: 2147483000,
        width: 320, maxHeight: '70vh', overflow: 'auto',
        background: 'hsl(240 6% 10%)',
        border: '1px solid var(--lr-border)',
        borderRadius: 'var(--lr-radius-lg)',
        boxShadow: '0 8px 32px hsl(0 0% 0% / 0.5)',
        color: 'var(--lr-fg)',
        fontFamily: 'var(--lr-font, system-ui)',
        fontSize: 12,
      }}
    >
      {/* Header */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '10px 12px',
        borderBottom: '1px solid var(--lr-border)',
      }}>
        <BrandMark size={16} />
        <div style={{ flex: 1, fontWeight: 600 }}>Lead Rush</div>
        <button
          className="lr-button lr-button--ghost"
          style={{ padding: '2px 6px', fontSize: 11 }}
          onClick={() => setExpanded(false)}
          aria-label="Collapse"
        >
          —
        </button>
      </div>

      <div style={{ padding: 12 }}>
        {status === 'loading' && (
          <div style={{ color: 'var(--lr-fg-muted)' }}>Loading…</div>
        )}

        {status === 'unauthenticated' && (
          <div style={{ color: 'var(--lr-fg-muted)' }}>
            Not connected. Click the Lead Rush icon in your toolbar to sign in.
          </div>
        )}

        {status === 'auth-expired' && (
          <div style={{ color: 'hsl(0 72% 70%)' }}>
            Your API key was revoked or expired. Reconnect from the extension popup.
          </div>
        )}

        {status === 'error' && (
          <div style={{ color: 'hsl(0 72% 70%)' }}>Couldn&apos;t reach Lead Rush. Try again.</div>
        )}

        {status === 'ready' && thread && (
          <>
            {/* Sender identity — visible regardless of match state */}
            <div style={{
              fontSize: 10, color: 'var(--lr-fg-muted)',
              textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
            }}>
              Thread sender
            </div>
            <div style={{ fontWeight: 600 }}>
              {thread.primaryName ?? thread.primaryEmail}
            </div>
            {thread.primaryName && (
              <div style={{ color: 'var(--lr-fg-muted)', fontSize: 11 }}>
                {thread.primaryEmail}
              </div>
            )}

            <div style={{
              marginTop: 10, paddingTop: 10,
              borderTop: '1px solid hsl(240 5% 100% / 0.06)',
            }}>
              {lookupLoading && (
                <div style={{ color: 'var(--lr-fg-muted)' }}>Looking up…</div>
              )}

              {!lookupLoading && lookup && (
                <>
                  <div style={{
                    fontSize: 10, color: 'hsl(150 70% 60%)',
                    textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
                  }}>
                    ✓ In Lead Rush
                  </div>
                  <div style={{ fontWeight: 600, fontSize: 13 }}>{lookup.fullName}</div>
                  {(lookup.title || lookup.companyName) && (
                    <div style={{ color: 'var(--lr-fg-muted)', fontSize: 11 }}>
                      {lookup.title ?? ''}{lookup.title && lookup.companyName ? ' · ' : ''}{lookup.companyName ?? ''}
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
                      <div style={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                        {lookup.leadScore}
                      </div>
                    </div>
                    <div>
                      <div style={{ fontSize: 10, color: 'var(--lr-fg-muted)' }}>Stage</div>
                      <div style={{ fontWeight: 600, textTransform: 'capitalize' }}>
                        {(lookup.lifecycleStage ?? '—').toLowerCase()}
                      </div>
                    </div>
                    <div>
                      <div style={{ fontSize: 10, color: 'var(--lr-fg-muted)' }}>Last touch</div>
                      <div style={{ fontWeight: 600 }}>
                        {relative(lookup.lastActivityAt ?? lookup.createdAt)}
                      </div>
                    </div>
                  </div>

                  {/* Job-change banner — a timely "they just moved jobs" signal */}
                  {lookup.recentJobChanges && lookup.recentJobChanges.length > 0 && (
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
                      {lookup.recentJobChanges.slice(0, 2).map((c, i) => (
                        <div key={i} style={{ lineHeight: 1.5 }}>
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

                  {lookup.activeSequenceNames.length > 0 && (
                    <div style={{ marginTop: 8 }}>
                      <div style={{
                        fontSize: 10, color: 'var(--lr-fg-muted)',
                        textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
                      }}>
                        Active sequences
                      </div>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                        {lookup.activeSequenceNames.map(name => (
                          <span key={name} className="lr-badge lr-badge--primary">{name}</span>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Open deals */}
                  {lookup.deals && lookup.deals.length > 0 && (
                    <div style={{ marginTop: 8 }}>
                      <div style={{
                        fontSize: 10, color: 'var(--lr-fg-muted)',
                        textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
                      }}>
                        Open deals
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                        {lookup.deals.map(d => (
                          <a key={d.dealId} href={d.dealUrl} target="_blank" rel="noopener noreferrer" style={{
                            display: 'block', padding: 6,
                            background: 'hsl(240 6% 9%)',
                            border: '1px solid var(--lr-border)',
                            borderRadius: 'var(--lr-radius)',
                            textDecoration: 'none', color: 'var(--lr-fg)',
                          }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 6 }}>
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

                  {lookup.collisions && lookup.collisions.length > 0 && (
                    <div style={{
                      marginTop: 8, padding: 8,
                      background: 'hsl(38 92% 50% / 0.08)',
                      border: '1px solid hsl(38 92% 50% / 0.25)',
                      borderRadius: 'var(--lr-radius)',
                    }}>
                      <div style={{
                        fontSize: 10, color: 'hsl(38 92% 65%)',
                        textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
                      }}>
                        ⚠ Teammate activity
                      </div>
                      {lookup.collisions.slice(0, 2).map((c, i) => (
                        <div key={i} style={{ lineHeight: 1.5 }}>
                          <strong>{c.userName}</strong>
                          {' '}recently touched this contact
                          {' '}<span style={{ color: 'var(--lr-fg-muted)' }}>· {relative(c.at)}</span>
                        </div>
                      ))}
                    </div>
                  )}

                  <div style={{ marginTop: 10 }}>
                    <a
                      href={lookup.contactUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="lr-button lr-button--primary"
                      style={{ display: 'block', textAlign: 'center', textDecoration: 'none' }}
                    >
                      Open in Lead Rush ↗
                    </a>
                  </div>
                </>
              )}

              {!lookupLoading && !lookup && (
                <>
                  <div style={{
                    fontSize: 10, color: 'var(--lr-fg-muted)',
                    textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4,
                  }}>
                    Not in Lead Rush yet
                  </div>
                  <div style={{ color: 'var(--lr-fg-muted)' }}>
                    We don&apos;t have a contact with this email. Add them from the web app.
                  </div>
                  <div style={{ marginTop: 10 }}>
                    <a
                      href={buildAddContactUrl(me, thread)}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="lr-button lr-button--outline"
                      style={{ display: 'block', textAlign: 'center', textDecoration: 'none' }}
                    >
                      Add to Lead Rush ↗
                    </a>
                  </div>
                </>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}

/**
 * Deep-link into the web app's /contacts page with `?new=1&email=...&firstName=...`
 * query params. The contacts page reads these on mount and opens the
 * Create-contact dialog with the sender's details already filled in.
 *
 * We don't carry the workspace in the URL — the web app routes are flat and
 * scope by JWT. The MeDto param is kept so future auth-per-workspace link
 * variants can be wired without changing the panel.
 */
function buildAddContactUrl(_me: MeDto | null, thread: GmailThreadContext): string {
  // Nuxt dev server is pinned to :4000 in nuxt.config.ts to avoid colliding
  // with the user's local Next.js projects on :3000. Match that default here.
  const base = (import.meta.env.WXT_FRONTEND_URL as string | undefined)
    ?? 'http://localhost:4000'
  const params = new URLSearchParams()
  params.set('new', '1')
  params.set('email', thread.primaryEmail)
  if (thread.primaryName) {
    const [first, ...rest] = thread.primaryName.split(' ')
    if (first) params.set('firstName', first)
    if (rest.length > 0) params.set('lastName', rest.join(' '))
  }
  return `${base.replace(/\/$/, '')}/contacts?${params.toString()}`
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
