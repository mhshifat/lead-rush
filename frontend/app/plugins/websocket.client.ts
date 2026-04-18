/**
 * Client-only plugin that wires the WebSocket connection to the auth store.
 *
 *   - Starts/stops the connection when the user logs in / out
 *   - Subscribes to /user/queue/notifications and pipes arrivals into the Pinia store
 *   - Filename ends in `.client.ts` so Nuxt skips it during SSR (WebSockets don't run on server)
 */
import { createWsClient, type WsClient } from '~/lib/ws-client'
import type { NotificationApiDto } from '~/types/api/notification.dto'

declare module '#app' {
  interface NuxtApp {
    $ws: WsClient
  }
}

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig()

  const ws = createWsClient({
    apiBaseUrl: config.public.apiBaseUrl as string,
    getToken: () => useCookie('accessToken').value ?? null,
  })

  let unsubscribeNotifications: (() => void) | null = null

  function connectAndSubscribe() {
    ws.connect()

    if (!unsubscribeNotifications) {
      unsubscribeNotifications = ws.subscribe('/user/queue/notifications', (payload) => {
        const store = useNotificationsStore()
        store.pushIncoming(payload as NotificationApiDto)
      })
    }
  }

  function teardown() {
    unsubscribeNotifications?.()
    unsubscribeNotifications = null
    ws.disconnect()
    useNotificationsStore().reset()
  }

  // React to login state changes — watch the access-token cookie reactively
  const token = useCookie('accessToken')
  watch(token, (value) => {
    if (value) connectAndSubscribe()
    else teardown()
  }, { immediate: true })

  return {
    provide: { ws },
  }
})
