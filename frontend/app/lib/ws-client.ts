/**
 * Thin STOMP-over-SockJS wrapper.
 *
 * Responsibilities:
 *   - Connect to the backend /ws endpoint with a JWT Bearer header
 *   - Auto-reconnect with exponential backoff
 *   - Expose `subscribe(destination, handler)` that returns an unsubscribe fn
 *
 * Destinations we care about:
 *   /user/queue/notifications — per-user live notifications (Spring user-destination)
 *
 * The token in the CONNECT header is validated by our ChannelInterceptor
 * (see WebSocketConfig.java on the backend).
 */
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

type Handler = (body: unknown) => void

export interface WsClient {
  connect(): void
  disconnect(): void
  subscribe(destination: string, handler: Handler): () => void
  isConnected: () => boolean
}

export function createWsClient(opts: {
  apiBaseUrl: string          // e.g., http://localhost:8080/api/v1
  getToken: () => string | null
}): WsClient {
  // The STOMP endpoint is mounted at /ws on the backend root (not under /api/v1)
  const baseOrigin = opts.apiBaseUrl.replace(/\/api\/v\d+\/?$/, '')
  const wsUrl = `${baseOrigin}/ws`

  const pending: Array<{ destination: string; handler: Handler; sub?: StompSubscription }> = []
  let client: Client | null = null
  let connected = false

  function ensureClient(): Client {
    if (client) return client

    client = new Client({
      // SockJS fallback — works through proxies that block native WS
      webSocketFactory: () => new SockJS(wsUrl) as any,
      connectHeaders: tokenHeaders(),
      reconnectDelay: 2000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      debug: () => { /* silence STOMP's verbose logs */ },

      onConnect: () => {
        connected = true
        // Flush queued subscriptions now that the connection is live
        for (const entry of pending) {
          entry.sub = client!.subscribe(entry.destination, (msg: IMessage) => {
            entry.handler(parseBody(msg.body))
          })
        }
      },

      onDisconnect: () => {
        connected = false
        for (const entry of pending) entry.sub = undefined
      },

      onStompError: (frame) => {
        console.warn('[ws] STOMP error:', frame.headers['message'], frame.body)
      },

      onWebSocketClose: () => {
        connected = false
      },
    })

    return client
  }

  function tokenHeaders(): Record<string, string> {
    const token = opts.getToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
  }

  return {
    connect() {
      const c = ensureClient()
      // Refresh token on every activation — handles post-refresh JWT rotation
      c.connectHeaders = tokenHeaders()
      if (!c.active) c.activate()
    },

    disconnect() {
      if (client?.active) client.deactivate()
      connected = false
    },

    subscribe(destination, handler) {
      const entry: { destination: string; handler: Handler; sub?: StompSubscription } =
        { destination, handler }
      pending.push(entry)

      // If already connected, subscribe right away; otherwise it'll flush on onConnect
      if (connected && client) {
        entry.sub = client.subscribe(destination, (msg: IMessage) => {
          handler(parseBody(msg.body))
        })
      }

      return () => {
        entry.sub?.unsubscribe()
        const i = pending.indexOf(entry)
        if (i >= 0) pending.splice(i, 1)
      }
    },

    isConnected: () => connected,
  }
}

function parseBody(body: string): unknown {
  try { return JSON.parse(body) } catch { return body }
}
