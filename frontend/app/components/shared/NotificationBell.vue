<!--
  Notification bell — shows unread count badge, dropdown with recent activity.
  Lives in the default layout. Connects to the notifications store for both
  REST-loaded history and real-time WebSocket pushes.
-->
<script setup lang="ts">
import { notificationIcon, type NotificationEntity } from '~/entities/notification/notification.entity'

const store = useNotificationsStore()
const authStore = useAuthStore()

const open = ref(false)
const panelRef = ref<HTMLElement | null>(null)
const buttonRef = ref<HTMLElement | null>(null)

// Close panel on outside click (tiny manual implementation vs pulling in a Popover primitive)
function onClickOutside(e: MouseEvent) {
  const target = e.target as Node
  if (panelRef.value?.contains(target) || buttonRef.value?.contains(target)) return
  open.value = false
}

onMounted(() => {
  document.addEventListener('click', onClickOutside)
  // Initial load — only once logged in
  if (authStore.isLoggedIn) store.refresh()
})
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))

// If the user logs in while the bell is mounted, refresh
watch(() => authStore.isLoggedIn, (loggedIn) => {
  if (loggedIn) store.refresh()
})

async function handleClick(n: NotificationEntity) {
  if (!n.read) await store.markRead(n.id)
  if (n.linkPath) await navigateTo(n.linkPath)
  open.value = false
}

async function handleMarkAll() {
  await store.markAllRead()
}

function formatTime(date: Date): string {
  const now = Date.now()
  const diffMs = now - date.getTime()
  const mins = Math.floor(diffMs / 60_000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}d ago`
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' }).format(date)
}
</script>

<template>
  <div class="relative">
    <button
      ref="buttonRef"
      class="relative h-9 w-9 rounded-md hairline hover:bg-white/5 flex items-center justify-center transition-colors"
      @click="open = !open"
      aria-label="Notifications"
    >
      <svg
        class="w-4 h-4 text-muted-foreground"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
      <span
        v-if="store.unreadCount > 0"
        class="absolute -top-1 -right-1 min-w-4 h-4 rounded-full bg-primary text-primary-foreground text-[10px] font-semibold flex items-center justify-center px-1 glow-primary"
      >
        {{ store.unreadCount > 99 ? '99+' : store.unreadCount }}
      </span>
    </button>

    <!-- Dropdown panel -->
    <div
      v-if="open"
      ref="panelRef"
      class="absolute right-0 mt-2 w-96 max-h-128 popover-panel rounded-xl z-50 flex flex-col overflow-hidden"
    >
      <div class="flex items-center justify-between px-4 py-3" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
        <h3 class="font-semibold text-sm tracking-tight">Notifications</h3>
        <button
          v-if="store.unreadCount > 0"
          class="text-xs text-primary hover:underline"
          @click="handleMarkAll"
        >
          Mark all read
        </button>
      </div>

      <div class="flex-1 overflow-y-auto">
        <div v-if="store.loading" class="px-4 py-8 text-sm text-center text-muted-foreground">
          Loading...
        </div>
        <div v-else-if="!store.items.length" class="px-4 py-8 text-sm text-center text-muted-foreground">
          No notifications yet
        </div>
        <ul v-else>
          <li
            v-for="n in store.items.slice(0, 10)"
            :key="n.id"
            class="px-4 py-3 hover:bg-white/5 cursor-pointer transition-colors"
            :class="{ 'bg-primary/5': !n.read }"
            style="border-top: 1px solid hsl(240 5% 100% / 0.04);"
            @click="handleClick(n)"
          >
            <div class="flex items-start gap-3">
              <span class="text-lg shrink-0">{{ notificationIcon(n.type) }}</span>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium flex items-center gap-2">
                  {{ n.title }}
                  <span v-if="!n.read" class="w-1.5 h-1.5 rounded-full bg-primary shrink-0" />
                </p>
                <p v-if="n.body" class="text-xs text-muted-foreground truncate">{{ n.body }}</p>
                <p class="text-xs text-muted-foreground mt-0.5">{{ formatTime(n.createdAt) }}</p>
              </div>
            </div>
          </li>
        </ul>
      </div>

      <div class="px-4 py-2 text-center" style="border-top: 1px solid hsl(240 5% 100% / 0.06);">
        <NuxtLink
          to="/notifications"
          class="text-xs text-primary hover:underline"
          @click="open = false"
        >
          See all notifications
        </NuxtLink>
      </div>
    </div>
  </div>
</template>
