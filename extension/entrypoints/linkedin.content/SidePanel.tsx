/**
 * Floating side panel that sits on LinkedIn profile pages.
 * States:
 *   - Not connected: prompt to open the popup
 *   - Connected + no tasks: show profile summary + Import button
 *   - Connected + tasks: show tasks with Complete button + Import button
 *   - Collapsed: just a Lead Rush pill that expands on click
 */
import { useEffect, useRef, useState } from 'react'
import { send } from '@/lib/messaging'
import { normalizeProfileUrl, scrapeProfile } from '@/lib/linkedin-scraper'
import type {
  ExtensionConfig,
  ExtensionTaskDto,
  LinkedInImportPayload,
  LinkedInImportResult,
  MeDto,
} from '@/lib/types'

type Status = 'loading' | 'unauthenticated' | 'ready' | 'error'

export function SidePanel() {
  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)
  const [expanded, setExpanded] = useState(true)

  const [me, setMe] = useState<MeDto | null>(null)
  const [tasks, setTasks] = useState<ExtensionTaskDto[]>([])
  const [profile, setProfile] = useState<LinkedInImportPayload | null>(null)
  const [lastImport, setLastImport] = useState<LinkedInImportResult | null>(null)
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
      const url = normalizeProfileUrl(window.location.href)
      profileUrlRef.current = url
      // Re-scrape whenever we load (profile DOM may have rehydrated)
      setProfile(scrapeProfile())

      const [meResult, tasksResult] = await Promise.all([
        send<MeDto>({ type: 'me' }),
        send<ExtensionTaskDto[]>({ type: 'tasksForUrl', url }),
      ])
      setMe(meResult)
      setTasks(tasksResult)
      setStatus('ready')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error')
      setStatus('error')
    }
  }

  useEffect(() => { void load() }, [])

  function showToast(msg: string) {
    setToast(msg)
    setTimeout(() => setToast(null), 2500)
  }

  async function handleImport() {
    const payload = profile ?? scrapeProfile()
    if (!payload) { showToast('Could not read this profile'); return }
    setBusy(true)
    try {
      const result = await send<LinkedInImportResult>({ type: 'importContact', payload })
      setLastImport(result)
      showToast(result.created ? 'Contact created' : 'Contact updated')
      // Refresh tasks — new contact may have matched a pending task
      const refreshed = await send<ExtensionTaskDto[]>({
        type: 'tasksForUrl',
        url: profileUrlRef.current ?? payload.linkedinUrl,
      })
      setTasks(refreshed)
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Import failed')
    } finally {
      setBusy(false)
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

  // Collapsed state — thin pill in the corner
  if (!expanded) {
    return (
      <div
        className="lr-reset lr-root"
        style={{
          position: 'fixed', right: 20, bottom: 20, zIndex: 9999,
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
        position: 'fixed', right: 20, bottom: 20, zIndex: 9999,
        width: 340, maxHeight: 540,
        borderRadius: 14, overflow: 'hidden',
        boxShadow: '0 20px 60px -20px rgba(0,0,0,0.7), 0 0 0 1px hsl(240 5% 100% / 0.06)',
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
          <div style={{
            width: 20, height: 20, borderRadius: 6,
            background: 'linear-gradient(135deg, hsl(243 80% 65%), hsl(280 75% 60%))',
          }} />
          <div style={{ fontWeight: 600, fontSize: 13 }}>Lead Rush</div>
        </div>
        <button className="lr-button lr-button--ghost" style={{ padding: '4px 6px' }} onClick={() => setExpanded(false)}>
          ×
        </button>
      </div>

      <div className="lr-glass" style={{
        padding: 14,
        maxHeight: 460, overflowY: 'auto',
        background: 'hsl(240 6% 7% / 0.92)',
        borderTop: 'none', borderRadius: 0,
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

            {/* Profile scraped summary */}
            {profile && (
              <div className="lr-hairline" style={{
                padding: 10, borderRadius: 'var(--lr-radius)', marginBottom: 10,
              }}>
                <div style={{ fontWeight: 600, fontSize: 13 }}>
                  {[profile.firstName, profile.lastName].filter(Boolean).join(' ') || 'Unknown name'}
                </div>
                {profile.title && (
                  <div style={{ fontSize: 12, color: 'var(--lr-fg-muted)' }}>
                    {profile.title}{profile.companyName ? ` · ${profile.companyName}` : ''}
                  </div>
                )}
                <div style={{ display: 'flex', gap: 6, marginTop: 10 }}>
                  <button
                    className="lr-button lr-button--primary"
                    style={{ flex: 1 }}
                    disabled={busy}
                    onClick={handleImport}
                  >
                    {lastImport
                      ? (lastImport.created ? '✓ Imported' : '✓ Updated')
                      : 'Import to Lead Rush'}
                  </button>
                </div>
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
