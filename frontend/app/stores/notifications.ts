import { defineStore } from 'pinia'
import { NotificationMapper } from '~/entities/notification/notification.mapper'
import type { NotificationEntity } from '~/entities/notification/notification.entity'
import type { NotificationApiDto } from '~/types/api/notification.dto'

/**
 * Client-side notification state.
 *
 * Data flow:
 *   1. On login / app boot → fetch recent notifications via REST
 *   2. Open WebSocket → receive real-time pushes to /user/queue/notifications
 *   3. Push arrivals go to the front of the list; unread count re-derives
 *
 * The REST API is the source of truth — the WebSocket is a latency optimization.
 */
export const useNotificationsStore = defineStore('notifications', () => {
  const items = ref<NotificationEntity[]>([])
  const loading = ref(false)

  const unreadCount = computed(() => items.value.filter(n => !n.read).length)

  async function refresh() {
    loading.value = true
    try {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: { content: NotificationApiDto[] } }>('/notifications?page=0&size=30')
      items.value = NotificationMapper.toEntityList(res.data.content)
    } finally {
      loading.value = false
    }
  }

  /** Called by the WebSocket listener when a push arrives. */
  function pushIncoming(dto: NotificationApiDto) {
    const entity = NotificationMapper.toEntity(dto)
    // Dedupe in case REST and WS both deliver the same notification
    if (items.value.some(n => n.id === entity.id)) return
    items.value.unshift(entity)
  }

  async function markRead(id: string) {
    const { $api } = useNuxtApp()
    await $api(`/notifications/${id}/read`, { method: 'PUT' })
    const match = items.value.find(n => n.id === id)
    if (match) {
      match.read = true
      match.readAt = new Date()
    }
  }

  async function markAllRead() {
    const { $api } = useNuxtApp()
    await $api('/notifications/read-all', { method: 'PUT' })
    const now = new Date()
    items.value.forEach(n => { n.read = true; n.readAt = now })
  }

  function reset() {
    items.value = []
  }

  return {
    items,
    loading,
    unreadCount,
    refresh,
    pushIncoming,
    markRead,
    markAllRead,
    reset,
  }
})
