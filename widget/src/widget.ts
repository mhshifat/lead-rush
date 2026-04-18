/**
 * Lead Rush embeddable chat widget.
 *
 * Drop-in install:
 *   <script async src="https://leadrush.com/widget.js"
 *           data-workspace="acme"
 *           data-api="https://api.leadrush.com"></script>
 *
 * All styles are inlined + scoped under #lr-widget so they can't collide with
 * the host page. Uses fetch + HTTP polling for visitor-side real-time — avoids
 * the STOMP auth complexity for the public widget. Agents see messages via
 * the authenticated WebSocket already.
 */

type WidgetConfig = {
  workspaceSlug: string
  workspaceName: string
  enabled: boolean
  displayName: string
  greeting: string
  offlineMessage: string
  primaryColor: string
  position: 'BOTTOM_RIGHT' | 'BOTTOM_LEFT'
  requireEmail: boolean
}

type ChatMessage = {
  id: string
  sender: 'VISITOR' | 'AGENT' | 'SYSTEM'
  agentName: string | null
  body: string
  createdAt: string
}

type ConversationResponse = {
  conversationId: string
  visitorToken: string
  messages: ChatMessage[]
}

type ApiEnvelope<T> = { success: true; data: T } | { success: false; error: { message: string } }

const STORAGE_TOKEN_KEY = 'leadrush:visitorToken'
const STORAGE_EMAIL_KEY = 'leadrush:visitorEmail'
const STORAGE_NAME_KEY = 'leadrush:visitorName'
const POLL_INTERVAL_MS = 4000

// Read params off the <script> tag we're running inside
const scriptTag = (document.currentScript as HTMLScriptElement | null)
    ?? Array.from(document.querySelectorAll<HTMLScriptElement>('script[data-workspace]')).pop()
    ?? null

if (scriptTag) initWidget(scriptTag)

function initWidget(script: HTMLScriptElement) {
  const workspaceSlug = script.getAttribute('data-workspace')
  const apiBase = (script.getAttribute('data-api') ?? '').replace(/\/$/, '')
  if (!workspaceSlug || !apiBase) {
    console.warn('[lead-rush] widget needs data-workspace and data-api attributes')
    return
  }

  bootstrap(workspaceSlug, apiBase).catch(err => console.warn('[lead-rush] init failed', err))
}

async function bootstrap(workspaceSlug: string, apiBase: string) {
  const config = await fetchConfig(apiBase, workspaceSlug)
  if (!config.enabled) return

  const ui = renderWidget(config)
  const state = {
    config,
    apiBase,
    workspaceSlug,
    conversationId: null as string | null,
    visitorToken: localStorage.getItem(STORAGE_TOKEN_KEY),
    messageIds: new Set<string>(),
    pollTimer: 0 as unknown as number,
    open: false,
    emailPromptVisible: false,
  }

  // Prefill identity if we know it
  const savedEmail = localStorage.getItem(STORAGE_EMAIL_KEY)
  const savedName = localStorage.getItem(STORAGE_NAME_KEY)
  if (savedEmail) ui.emailInput.value = savedEmail
  if (savedName) ui.nameInput.value = savedName

  // Resume conversation if token present
  if (state.visitorToken) {
    try {
      const existing = await apiGet<ConversationResponse>(
        apiBase, `/api/v1/public/chat/conversations/${encodeURIComponent(state.visitorToken)}`)
      state.conversationId = existing.conversationId
      existing.messages.forEach(msg => appendMessage(ui, state, msg))
    } catch {
      // Token no longer valid — wipe and treat as fresh visitor
      localStorage.removeItem(STORAGE_TOKEN_KEY)
      state.visitorToken = null
    }
  }

  setEmailPromptVisibility(ui, config.requireEmail && !savedEmail)

  ui.toggle.addEventListener('click', () => {
    state.open = !state.open
    ui.panel.style.display = state.open ? 'flex' : 'none'
    if (state.open && state.conversationId) void refreshMessages(state, ui)
  })
  ui.close.addEventListener('click', () => { state.open = false; ui.panel.style.display = 'none' })

  ui.form.addEventListener('submit', async (e) => {
    e.preventDefault()
    const message = ui.textarea.value.trim()
    if (!message) return
    ui.textarea.value = ''

    const name = ui.nameInput.value.trim()
    const email = ui.emailInput.value.trim()
    if (config.requireEmail && !email) {
      setEmailPromptVisibility(ui, true)
      ui.emailInput.focus()
      return
    }
    if (name) localStorage.setItem(STORAGE_NAME_KEY, name)
    if (email) localStorage.setItem(STORAGE_EMAIL_KEY, email)

    ui.sendButton.disabled = true
    try {
      if (!state.conversationId) {
        const started = await apiPost<ConversationResponse>(
          apiBase, '/api/v1/public/chat/conversations',
          {
            workspaceSlug,
            visitorToken: state.visitorToken,
            visitorName: name || undefined,
            visitorEmail: email || undefined,
            message,
            sourceUrl: window.location.href,
            userAgent: navigator.userAgent,
          },
        )
        state.conversationId = started.conversationId
        state.visitorToken = started.visitorToken
        localStorage.setItem(STORAGE_TOKEN_KEY, started.visitorToken)
        started.messages.forEach(msg => appendMessage(ui, state, msg))
        setEmailPromptVisibility(ui, false)
      } else {
        const updated = await apiPost<ConversationResponse>(
          apiBase, '/api/v1/public/chat/conversations/messages',
          { visitorToken: state.visitorToken, message },
        )
        updated.messages.forEach(msg => appendMessage(ui, state, msg))
      }
      startPolling(state, ui)
    } catch (err) {
      console.warn('[lead-rush] send failed', err)
      appendLocalSystemMessage(ui, "Couldn't send that — try again in a moment.")
    } finally {
      ui.sendButton.disabled = false
    }
  })

  // Begin polling if we have an active conversation (even when panel is closed, so
  // the unread dot lights up on agent reply)
  if (state.conversationId) startPolling(state, ui)
}

// ── API ──

async function fetchConfig(apiBase: string, slug: string): Promise<WidgetConfig> {
  return apiGet<WidgetConfig>(apiBase, `/api/v1/public/chat/widget/${encodeURIComponent(slug)}`)
}

async function apiGet<T>(apiBase: string, path: string): Promise<T> {
  const res = await fetch(apiBase + path, { headers: { 'Accept': 'application/json' } })
  const body = await res.json() as ApiEnvelope<T>
  if (!res.ok || 'error' in body) throw new Error(extractError(body) ?? `${res.status}`)
  return (body as { success: true; data: T }).data
}

async function apiPost<T>(apiBase: string, path: string, payload: unknown): Promise<T> {
  const res = await fetch(apiBase + path, {
    method: 'POST',
    headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  const body = await res.json() as ApiEnvelope<T>
  if (!res.ok || 'error' in body) throw new Error(extractError(body) ?? `${res.status}`)
  return (body as { success: true; data: T }).data
}

function extractError(body: unknown): string | null {
  if (body && typeof body === 'object' && 'error' in body) {
    const err = (body as { error?: { message?: string } }).error
    return err?.message ?? null
  }
  return null
}

// ── Polling (visitor sees agent replies) ──

function startPolling(state: ReturnType<typeof makeState>, ui: WidgetUI) {
  if (state.pollTimer) return
  state.pollTimer = window.setInterval(async () => {
    if (!state.visitorToken) return
    try { await refreshMessages(state, ui) } catch { /* swallow transient errors */ }
  }, POLL_INTERVAL_MS)
}

async function refreshMessages(state: ReturnType<typeof makeState>, ui: WidgetUI) {
  if (!state.visitorToken) return
  const res = await apiGet<ConversationResponse>(
    state.apiBase,
    `/api/v1/public/chat/conversations/${encodeURIComponent(state.visitorToken)}`)
  res.messages.forEach(msg => appendMessage(ui, state, msg))
}

// Dummy helper to sharpen the inferred state shape used above
function makeState() {
  return {
    config: {} as WidgetConfig,
    apiBase: '',
    workspaceSlug: '',
    conversationId: null as string | null,
    visitorToken: null as string | null,
    messageIds: new Set<string>(),
    pollTimer: 0,
    open: false,
    emailPromptVisible: false,
  }
}

// ── UI ──

type WidgetUI = {
  root: HTMLElement
  toggle: HTMLButtonElement
  panel: HTMLElement
  close: HTMLButtonElement
  messages: HTMLElement
  form: HTMLFormElement
  textarea: HTMLTextAreaElement
  sendButton: HTMLButtonElement
  nameInput: HTMLInputElement
  emailInput: HTMLInputElement
  identityRow: HTMLElement
}

function renderWidget(config: WidgetConfig): WidgetUI {
  const root = document.createElement('div')
  root.id = 'lr-widget'
  root.setAttribute('data-position', config.position)
  root.style.setProperty('--lr-primary', config.primaryColor)

  const styleEl = document.createElement('style')
  styleEl.textContent = STYLES
  root.appendChild(styleEl)

  const side = config.position === 'BOTTOM_LEFT' ? 'left' : 'right'

  root.innerHTML += `
    <style>#lr-widget .lr-panel, #lr-widget .lr-toggle { ${side}: 20px; }</style>
    <button class="lr-toggle" aria-label="Open chat">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      </svg>
    </button>
    <div class="lr-panel" role="dialog" aria-label="Chat" style="display:none">
      <div class="lr-header">
        <div>
          <div class="lr-title">${escapeHtml(config.displayName)}</div>
          <div class="lr-sub">${escapeHtml(config.workspaceName)}</div>
        </div>
        <button class="lr-close" aria-label="Close">×</button>
      </div>
      <div class="lr-messages" role="log" aria-live="polite"></div>
      <form class="lr-form">
        <div class="lr-identity" style="display:none">
          <input class="lr-name" type="text" placeholder="Name (optional)" autocomplete="name" />
          <input class="lr-email" type="email" placeholder="Email" autocomplete="email" />
        </div>
        <div class="lr-compose">
          <textarea class="lr-textarea" rows="1" placeholder="Type a message…" required></textarea>
          <button class="lr-send" type="submit" aria-label="Send">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
            </svg>
          </button>
        </div>
        <div class="lr-foot">Powered by Lead Rush</div>
      </form>
    </div>
  `

  document.body.appendChild(root)

  const messagesEl = root.querySelector<HTMLElement>('.lr-messages')!
  // Show greeting as the first system message — real SYSTEM message from server
  // will still arrive and dedupe via messageIds.
  const greetingEl = document.createElement('div')
  greetingEl.className = 'lr-msg lr-msg--system'
  greetingEl.textContent = config.greeting
  messagesEl.appendChild(greetingEl)

  return {
    root,
    toggle: root.querySelector<HTMLButtonElement>('.lr-toggle')!,
    panel: root.querySelector<HTMLElement>('.lr-panel')!,
    close: root.querySelector<HTMLButtonElement>('.lr-close')!,
    messages: messagesEl,
    form: root.querySelector<HTMLFormElement>('.lr-form')!,
    textarea: root.querySelector<HTMLTextAreaElement>('.lr-textarea')!,
    sendButton: root.querySelector<HTMLButtonElement>('.lr-send')!,
    nameInput: root.querySelector<HTMLInputElement>('.lr-name')!,
    emailInput: root.querySelector<HTMLInputElement>('.lr-email')!,
    identityRow: root.querySelector<HTMLElement>('.lr-identity')!,
  }
}

function appendMessage(ui: WidgetUI, state: ReturnType<typeof makeState>, msg: ChatMessage) {
  if (state.messageIds.has(msg.id)) return
  state.messageIds.add(msg.id)

  const el = document.createElement('div')
  el.className = 'lr-msg lr-msg--' + msg.sender.toLowerCase()
  el.textContent = msg.body
  ui.messages.appendChild(el)
  ui.messages.scrollTop = ui.messages.scrollHeight
}

function appendLocalSystemMessage(ui: WidgetUI, body: string) {
  const el = document.createElement('div')
  el.className = 'lr-msg lr-msg--system'
  el.textContent = body
  ui.messages.appendChild(el)
  ui.messages.scrollTop = ui.messages.scrollHeight
}

function setEmailPromptVisibility(ui: WidgetUI, show: boolean) {
  ui.identityRow.style.display = show ? 'grid' : 'none'
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c] as string))
}

// ── Styles (scoped under #lr-widget) ──

const STYLES = `
#lr-widget, #lr-widget * {
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Inter', sans-serif;
  line-height: 1.4;
}
#lr-widget {
  --lr-primary: #5E6AD2;
  --lr-bg: #0E0E11;
  --lr-surface: #15151a;
  --lr-fg: #f0f0f2;
  --lr-muted: #9a9aa6;
  --lr-border: rgba(255,255,255,0.08);
  color: var(--lr-fg);
}
#lr-widget .lr-toggle {
  position: fixed;
  bottom: 20px;
  z-index: 2147483647;
  width: 52px; height: 52px;
  border-radius: 50%;
  border: none;
  background: var(--lr-primary);
  color: white;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 10px 30px -6px rgba(0,0,0,0.4), 0 0 0 4px rgba(255,255,255,0.06);
  transition: transform 150ms cubic-bezier(0.22,1,0.36,1);
}
#lr-widget .lr-toggle:hover { transform: translateY(-2px); }
#lr-widget .lr-toggle svg { width: 22px; height: 22px; }

#lr-widget .lr-panel {
  position: fixed;
  bottom: 90px;
  z-index: 2147483647;
  width: 360px;
  max-width: calc(100vw - 32px);
  height: 520px;
  max-height: calc(100vh - 120px);
  background: var(--lr-bg);
  color: var(--lr-fg);
  border: 1px solid var(--lr-border);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 24px 48px -16px rgba(0,0,0,0.55);
  display: flex;
  flex-direction: column;
  animation: lr-fade 220ms cubic-bezier(0.22,1,0.36,1) both;
}
@keyframes lr-fade {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}

#lr-widget .lr-header {
  padding: 14px 16px;
  background: var(--lr-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
#lr-widget .lr-title { font-weight: 600; font-size: 14px; }
#lr-widget .lr-sub { font-size: 12px; opacity: 0.85; }
#lr-widget .lr-close {
  appearance: none;
  background: rgba(255,255,255,0.15);
  border: none; color: white;
  width: 28px; height: 28px; border-radius: 50%;
  cursor: pointer; font-size: 18px;
  display: flex; align-items: center; justify-content: center;
}
#lr-widget .lr-close:hover { background: rgba(255,255,255,0.25); }

#lr-widget .lr-messages {
  flex: 1; overflow-y: auto;
  padding: 14px;
  display: flex; flex-direction: column; gap: 8px;
  background: var(--lr-bg);
}
#lr-widget .lr-msg {
  max-width: 80%;
  padding: 8px 12px;
  border-radius: 14px;
  font-size: 13px;
  white-space: pre-wrap;
  word-wrap: break-word;
}
#lr-widget .lr-msg--visitor {
  align-self: flex-end;
  background: var(--lr-primary);
  color: white;
  border-bottom-right-radius: 4px;
}
#lr-widget .lr-msg--agent {
  align-self: flex-start;
  background: rgba(255,255,255,0.05);
  color: var(--lr-fg);
  border: 1px solid var(--lr-border);
  border-bottom-left-radius: 4px;
}
#lr-widget .lr-msg--system {
  align-self: center;
  font-size: 11px;
  color: var(--lr-muted);
  font-style: italic;
  padding: 4px 8px;
}

#lr-widget .lr-form {
  border-top: 1px solid var(--lr-border);
  background: var(--lr-surface);
  padding: 10px;
}
#lr-widget .lr-identity {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-bottom: 8px;
}
#lr-widget .lr-identity input {
  appearance: none;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--lr-border);
  background: var(--lr-bg);
  color: var(--lr-fg);
  font-size: 12px;
  outline: none;
}
#lr-widget .lr-identity input:focus { border-color: var(--lr-primary); }
#lr-widget .lr-compose {
  display: flex;
  gap: 6px;
  align-items: flex-end;
}
#lr-widget .lr-textarea {
  flex: 1;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid var(--lr-border);
  background: var(--lr-bg);
  color: var(--lr-fg);
  font-size: 13px;
  resize: none;
  max-height: 100px;
  outline: none;
  font-family: inherit;
}
#lr-widget .lr-textarea:focus { border-color: var(--lr-primary); }
#lr-widget .lr-send {
  appearance: none;
  border: none;
  background: var(--lr-primary);
  color: white;
  width: 36px; height: 36px;
  border-radius: 10px;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: filter 150ms;
}
#lr-widget .lr-send:hover { filter: brightness(1.1); }
#lr-widget .lr-send:disabled { opacity: 0.5; pointer-events: none; }
#lr-widget .lr-send svg { width: 16px; height: 16px; }
#lr-widget .lr-foot {
  text-align: center;
  font-size: 10px;
  color: var(--lr-muted);
  padding: 6px 0 2px;
}
`
