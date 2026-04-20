/**
 * Popup — three states:
 *   1. Not connected → paste API key
 *   2. Connected → show "me" header + pending LinkedIn tasks
 *   3. Error connecting → show error + let user re-paste
 *
 * The backend URL is baked in at build time (WXT env var). We don't ask the
 * user because in practice it only has two values: localhost for dev builds,
 * one production URL for the shipped extension.
 */
import { useEffect, useState } from 'react'
import { isAuthError, send } from '@/lib/messaging'
import type { ExtensionConfig, ExtensionTaskDto, MeDto } from '@/lib/types'
import { AnimatedOrbs } from '@/components/AnimatedOrbs'
import { BrandMark } from '@/components/BrandMark'

// Build-time-baked backend URL. Override for prod with:
//   WXT_BACKEND_URL=https://api.leadrush.com wxt build
// Vite substitutes the literal at build time — no runtime fetch to configure it.
const BACKEND_URL = (import.meta.env.WXT_BACKEND_URL as string | undefined) ?? 'http://localhost:8080'

export function App() {
  const [loading, setLoading] = useState(true)
  const [me, setMe] = useState<MeDto | null>(null)
  const [tasks, setTasks] = useState<ExtensionTaskDto[]>([])
  const [error, setError] = useState<string | null>(null)
  const [authExpired, setAuthExpired] = useState(false)

  // Connect form state
  const [apiKey, setApiKey] = useState('')
  const [connecting, setConnecting] = useState(false)

  async function refresh() {
    setLoading(true)
    setError(null)
    setAuthExpired(false)
    try {
      const config = await send<ExtensionConfig | null>({ type: 'getConfig' })
      if (!config) {
        setMe(null)
        setTasks([])
        return
      }
      const [meResult, tasksResult] = await Promise.all([
        send<MeDto>({ type: 'me' }),
        send<ExtensionTaskDto[]>({ type: 'listTasks' }),
      ])
      setMe(meResult)
      setTasks(tasksResult)
    } catch (err) {
      if (isAuthError(err)) {
        // Key was revoked on the server — show the paste-a-new-one UI with a warning,
        // but keep the old base URL for convenience.
        setAuthExpired(true)
        setError('Your API key was revoked or expired. Paste a new one below.')
      } else {
        setError(err instanceof Error ? err.message : 'Unknown error')
      }
      setMe(null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void refresh() }, [])

  async function handleConnect() {
    if (!apiKey.trim()) { setError('Paste your API key'); return }
    setConnecting(true)
    setError(null)
    try {
      await send({
        type: 'setConfig',
        apiKey: apiKey.trim(),
        apiBaseUrl: BACKEND_URL,
      })
      setApiKey('')
      await refresh()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not connect')
    } finally {
      setConnecting(false)
    }
  }

  async function handleDisconnect() {
    await send({ type: 'clearConfig' })
    setMe(null)
    setTasks([])
  }

  async function openProfile(url: string | null) {
    if (!url) return
    await chrome.tabs.create({ url })
  }

  return (
    <div
      className="lr-reset lr-root"
      style={{ position: 'relative', width: 360, minHeight: 420, overflow: 'hidden' }}
    >
      <AnimatedOrbs />

      <div style={{ position: 'relative', zIndex: 1, padding: 16 }}>
        {/* Brand row */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <BrandMark size={22} />
            <div style={{ fontWeight: 600, letterSpacing: '-0.01em' }}>Lead Rush</div>
          </div>
          {me && (
            <button className="lr-button lr-button--ghost" style={{ padding: '4px 8px' }} onClick={handleDisconnect}>
              Sign out
            </button>
          )}
        </div>

        {loading && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--lr-fg-muted)' }}>
            <span className="lr-spinner" /> Loading…
          </div>
        )}

        {/* Unauthenticated */}
        {!loading && !me && (
          <div className="lr-fade-up">
            <h1 className="lr-gradient-text" style={{ fontSize: 20, fontWeight: 600, letterSpacing: '-0.01em', marginBottom: 4 }}>
              {authExpired ? 'Reconnect to Lead Rush' : 'Connect to Lead Rush'}
            </h1>
            <p className="lr-muted" style={{ fontSize: 12, marginBottom: 16 }}>
              {authExpired
                ? 'Generate a fresh key in Settings → API keys and paste it below.'
                : 'Generate an API key in Settings → API keys, then paste it here.'}
            </p>

            {error && (
              <div style={{
                padding: 10, marginBottom: 12, borderRadius: 'var(--lr-radius)',
                background: authExpired ? 'hsl(30 90% 58% / 0.12)' : 'hsl(0 72% 58% / 0.1)',
                border: `1px solid ${authExpired ? 'hsl(30 90% 58% / 0.35)' : 'hsl(0 72% 58% / 0.3)'}`,
                color: authExpired ? 'hsl(30 100% 82%)' : 'hsl(0 90% 85%)', fontSize: 12,
              }}>{error}</div>
            )}

            <label className="lr-label">API key</label>
            <input
              className="lr-input"
              type="password"
              placeholder="lr_..."
              value={apiKey}
              onChange={e => setApiKey(e.target.value)}
              style={{ marginBottom: 12 }}
            />
            <p
              className="lr-muted"
              style={{ fontSize: 11, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 6 }}
              title={BACKEND_URL /* URL still visible in the tooltip for support debugging */}
            >
              <span style={{
                display: 'inline-block', width: 6, height: 6, borderRadius: 999,
                background: 'hsl(150 60% 50%)',
              }} />
              Ready to connect
            </p>
            <button
              className="lr-button lr-button--primary"
              style={{ width: '100%' }}
              disabled={connecting}
              onClick={handleConnect}
            >
              {connecting ? 'Connecting…' : 'Connect'}
            </button>
          </div>
        )}

        {/* Authenticated */}
        {!loading && me && (
          <div className="lr-fade-up">
            <div className="lr-hairline" style={{ borderRadius: 'var(--lr-radius)', padding: 12, marginBottom: 16 }}>
              <div style={{ fontSize: 11, color: 'var(--lr-fg-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Connected
              </div>
              <div style={{ fontWeight: 600, fontSize: 14 }}>{me.workspaceName}</div>
              <div style={{ fontSize: 12, color: 'var(--lr-fg-muted)' }}>{me.userEmail}</div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 8 }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>Pending LinkedIn tasks</div>
              <div style={{ fontSize: 11, color: 'var(--lr-fg-muted)' }}>
                {tasks.length} {tasks.length === 1 ? 'task' : 'tasks'}
              </div>
            </div>

            {error && (
              <div style={{
                padding: 10, marginBottom: 12, borderRadius: 'var(--lr-radius)',
                background: 'hsl(0 72% 58% / 0.1)',
                border: '1px solid hsl(0 72% 58% / 0.3)',
                color: 'hsl(0 90% 85%)', fontSize: 12,
              }}>{error}</div>
            )}

            {tasks.length === 0 ? (
              <div className="lr-hairline lr-muted" style={{
                borderRadius: 'var(--lr-radius)',
                padding: 16, fontSize: 12, textAlign: 'center',
                display: 'flex', flexDirection: 'column', gap: 6,
              }}>
                <div style={{ fontWeight: 600, color: 'var(--lr-fg)' }}>You're all caught up</div>
                <div>
                  Tasks here come from sequences with{' '}
                  <strong style={{ color: 'var(--lr-fg)' }}>LinkedIn Connect</strong>{' '}
                  or{' '}
                  <strong style={{ color: 'var(--lr-fg)' }}>LinkedIn Message</strong>{' '}
                  steps — assigned to you when an enrolled contact reaches that step.
                </div>
                <div style={{ marginTop: 4 }}>
                  To import the profile you're currently viewing, use the Lead Rush side panel on the LinkedIn page.
                </div>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 300, overflowY: 'auto' }}>
                {tasks.map(task => (
                  <button
                    key={task.id}
                    className="lr-hairline"
                    style={{
                      textAlign: 'left', padding: 10, borderRadius: 'var(--lr-radius)',
                      background: 'hsl(240 6% 9% / 0.5)', cursor: task.contactLinkedinUrl ? 'pointer' : 'default',
                    }}
                    onClick={() => openProfile(task.contactLinkedinUrl)}
                  >
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginBottom: 4 }}>
                      <span className="lr-badge lr-badge--primary">
                        {task.type === 'LINKEDIN_CONNECT' ? 'Connect' : 'Message'}
                      </span>
                      <span style={{ fontSize: 12, fontWeight: 500 }}>{task.contactName ?? 'Unknown contact'}</span>
                    </div>
                    {task.contactTitle && (
                      <div style={{ fontSize: 11, color: 'var(--lr-fg-muted)' }}>
                        {task.contactTitle}{task.contactCompany ? ` · ${task.contactCompany}` : ''}
                      </div>
                    )}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
