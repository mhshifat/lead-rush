/**
 * Popup — three states:
 *   1. Not connected → paste API key + choose API base URL
 *   2. Connected → show "me" header + pending LinkedIn tasks
 *   3. Error connecting → show error + let user re-paste
 */
import { useEffect, useState } from 'react'
import { send } from '@/lib/messaging'
import type { ExtensionConfig, ExtensionTaskDto, MeDto } from '@/lib/types'
import { AnimatedOrbs } from '@/components/AnimatedOrbs'

const DEFAULT_API_BASE = 'http://localhost:8080'

export function App() {
  const [loading, setLoading] = useState(true)
  const [me, setMe] = useState<MeDto | null>(null)
  const [tasks, setTasks] = useState<ExtensionTaskDto[]>([])
  const [error, setError] = useState<string | null>(null)

  // Connect form state
  const [apiKey, setApiKey] = useState('')
  const [apiBaseUrl, setApiBaseUrl] = useState(DEFAULT_API_BASE)
  const [connecting, setConnecting] = useState(false)

  async function refresh() {
    setLoading(true)
    setError(null)
    try {
      const config = await send<ExtensionConfig | null>({ type: 'getConfig' })
      if (!config) {
        setMe(null)
        setTasks([])
        return
      }
      setApiBaseUrl(config.apiBaseUrl)
      const [meResult, tasksResult] = await Promise.all([
        send<MeDto>({ type: 'me' }),
        send<ExtensionTaskDto[]>({ type: 'listTasks' }),
      ])
      setMe(meResult)
      setTasks(tasksResult)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error')
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
        apiBaseUrl: apiBaseUrl.trim().replace(/\/$/, ''),
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
            <div
              style={{
                width: 22, height: 22, borderRadius: 6,
                background: 'linear-gradient(135deg, hsl(243 80% 65%), hsl(280 75% 60%))',
              }}
            />
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
              Connect to Lead Rush
            </h1>
            <p className="lr-muted" style={{ fontSize: 12, marginBottom: 16 }}>
              Generate an API key in Settings → API keys, then paste it here.
            </p>

            {error && (
              <div style={{
                padding: 10, marginBottom: 12, borderRadius: 'var(--lr-radius)',
                background: 'hsl(0 72% 58% / 0.1)',
                border: '1px solid hsl(0 72% 58% / 0.3)',
                color: 'hsl(0 90% 85%)', fontSize: 12,
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
            <label className="lr-label">API URL</label>
            <input
              className="lr-input"
              placeholder="http://localhost:8080"
              value={apiBaseUrl}
              onChange={e => setApiBaseUrl(e.target.value)}
              style={{ marginBottom: 16 }}
            />
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
                padding: 20, fontSize: 12, textAlign: 'center',
              }}>
                Nothing pending right now. Visit a LinkedIn profile with an active LinkedIn step to surface it here.
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
