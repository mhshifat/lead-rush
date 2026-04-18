<!--
  Full notifications list — linked from the bell dropdown's "See all" footer.
  Uses the same Pinia store so data stays in sync with real-time pushes.
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Badge } from '~/components/ui/badge'
import { notificationIcon, type NotificationEntity } from '~/entities/notification/notification.entity'

definePageMeta({ middleware: 'auth' })

const store = useNotificationsStore()

onMounted(() => store.refresh())

async function handleClick(n: NotificationEntity) {
  if (!n.read) await store.markRead(n.id)
  if (n.linkPath) await navigateTo(n.linkPath)
}

function formatDate(date: Date): string {
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }).format(date)
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-start justify-between">
      <div>
        <h1 class="text-3xl font-bold">Notifications</h1>
        <p class="text-sm text-muted-foreground">Recent activity across the workspace.</p>
      </div>
      <Button
        v-if="store.unreadCount > 0"
        variant="outline"
        @click="store.markAllRead()"
      >
        Mark all as read
      </Button>
    </div>

    <Card>
      <CardContent class="pt-6">
        <div v-if="store.loading && !store.items.length" class="text-center py-8 text-muted-foreground">
          Loading...
        </div>
        <div v-else-if="!store.items.length" class="text-center py-16 text-muted-foreground">
          No notifications yet.
        </div>
        <ul v-else class="divide-y">
          <li
            v-for="n in store.items"
            :key="n.id"
            class="flex items-start gap-4 py-4 cursor-pointer hover:bg-muted/30 -mx-6 px-6 rounded"
            :class="{ 'bg-muted/20': !n.read }"
            @click="handleClick(n)"
          >
            <span class="text-2xl flex-shrink-0">{{ notificationIcon(n.type) }}</span>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2">
                <p class="font-medium">{{ n.title }}</p>
                <Badge v-if="!n.read" variant="default" class="text-xs">New</Badge>
              </div>
              <p v-if="n.body" class="text-sm text-muted-foreground mt-0.5">{{ n.body }}</p>
              <p class="text-xs text-muted-foreground mt-1">{{ formatDate(n.createdAt) }}</p>
            </div>
          </li>
        </ul>
      </CardContent>
    </Card>
  </div>
</template>
