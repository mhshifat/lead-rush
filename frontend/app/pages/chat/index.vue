<!--
  Chat inbox — two-pane layout (conversation list + message view).
  Subscribes to /topic/chat/workspace/{workspaceId} via the authenticated WebSocket
  so new messages appear in real time.
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Badge } from '~/components/ui/badge'
import { useQueryClient } from '@tanstack/vue-query'
import { toast } from 'vue-sonner'
import type { ChatConversationSummaryDto, ChatMessageDto } from '~/types/api/chat.dto'

definePageMeta({ middleware: 'auth' })

const authStore = useAuthStore()
const route = useRoute()
const queryClient = useQueryClient()

const { data: conversationsPage, isLoading } = useChatConversations()
const selectedId = ref<string | null>(null)
const selectedIdRef = computed(() => selectedId.value)
const { data: conversation } = useChatConversation(selectedIdRef)

const sendMutation = useSendChatMessage()
const closeMutation = useCloseChatConversation()

const newMessage = ref('')
const scrollerRef = ref<HTMLElement | null>(null)

// Deep-link: /chat?conversation=uuid opens that conversation
onMounted(() => {
  const fromQuery = route.query.conversation as string | undefined
  if (fromQuery) selectedId.value = fromQuery
  else if (!selectedId.value && conversationsPage.value?.content.length) {
    selectedId.value = conversationsPage.value.content[0]!.id
  }
})

watch(conversationsPage, (page) => {
  if (!selectedId.value && page?.content.length) {
    selectedId.value = page.content[0]!.id
  }
}, { immediate: true })

// Auto-scroll to the bottom on new messages
watch(() => conversation.value?.messages.length, async () => {
  await nextTick()
  if (scrollerRef.value) scrollerRef.value.scrollTop = scrollerRef.value.scrollHeight
})

// ── Real-time subscription ──
const { $ws } = useNuxtApp() as unknown as { $ws: { subscribe: (d: string, h: (b: unknown) => void) => () => void } }
let unsubscribe: (() => void) | null = null

onMounted(() => {
  const workspaceId = authStore.currentWorkspace?.id
  if (!workspaceId || !$ws?.subscribe) return
  unsubscribe = $ws.subscribe(`/topic/chat/workspace/${workspaceId}`, (payload) => {
    const msg = payload as ChatMessageDto
    // Update the specific conversation if it's the one open
    if (selectedId.value === msg.conversationId) {
      queryClient.setQueryData(
        ['chat', 'conversation', ref(selectedId.value)],
        (prev: any) => prev ? { ...prev, messages: [...prev.messages, msg] } : prev,
      )
    }
    queryClient.invalidateQueries({ queryKey: ['chat', 'conversations'] })
    queryClient.invalidateQueries({ queryKey: ['chat', 'unread-count'] })
  })
})
onBeforeUnmount(() => unsubscribe?.())

async function handleSend() {
  if (!selectedId.value || !newMessage.value.trim()) return
  const body = newMessage.value.trim()
  newMessage.value = ''
  try {
    await sendMutation.mutateAsync({ id: selectedId.value, message: body })
  } catch (error: any) {
    toast.error(error?.data?.error?.message ?? 'Failed to send')
    newMessage.value = body
  }
}

async function handleClose() {
  if (!selectedId.value) return
  if (!confirm('Close this conversation?')) return
  try {
    await closeMutation.mutateAsync(selectedId.value)
    toast.success('Conversation closed')
  } catch {
    toast.error('Failed to close')
  }
}

function initials(nameOrEmail: string | null): string {
  if (!nameOrEmail) return '?'
  const s = nameOrEmail.trim()
  const parts = s.includes(' ') ? s.split(' ') : s.split('@')
  return parts.map(p => p[0]).slice(0, 2).join('').toUpperCase()
}

function displayName(c: ChatConversationSummaryDto): string {
  return c.visitorName || c.visitorEmail || 'Anonymous visitor'
}

function formatTime(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  const now = Date.now()
  const diffMs = now - d.getTime()
  const mins = Math.floor(diffMs / 60_000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}d`
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' }).format(d)
}

function formatFullTime(iso: string): string {
  return new Intl.DateTimeFormat('en-US', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso))
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-start justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Chat</h1>
        <p class="text-sm text-muted-foreground">Live conversations from your embedded widget.</p>
      </div>
      <NuxtLink to="/chat/settings" class="text-sm text-primary hover:underline">
        Widget settings →
      </NuxtLink>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-[22rem_1fr] gap-4 h-[calc(100vh-200px)] min-h-[500px]">
      <!-- Conversation list -->
      <Card class="overflow-hidden flex flex-col">
        <CardHeader class="pb-3">
          <CardTitle class="text-base">Conversations</CardTitle>
          <CardDescription v-if="!isLoading && conversationsPage">
            {{ conversationsPage.totalElements }} total
          </CardDescription>
        </CardHeader>
        <CardContent class="flex-1 overflow-y-auto p-0">
          <div v-if="isLoading" class="p-4 text-sm text-muted-foreground text-center">Loading…</div>
          <div v-else-if="!conversationsPage?.content.length" class="p-10 text-sm text-muted-foreground text-center">
            No conversations yet.<br>
            Embed the widget on your site to start capturing chats.
          </div>
          <ul v-else>
            <li
              v-for="c in conversationsPage.content"
              :key="c.id"
              class="px-4 py-3 cursor-pointer transition-colors"
              :class="selectedId === c.id ? 'bg-white/5' : 'hover:bg-white/5'"
              style="border-top: 1px solid hsl(240 5% 100% / 0.06);"
              @click="selectedId = c.id"
            >
              <div class="flex items-start gap-3">
                <div class="w-9 h-9 rounded-full bg-linear-to-br from-indigo-400 to-purple-500 text-white text-xs font-semibold flex items-center justify-center shrink-0">
                  {{ initials(c.visitorName ?? c.visitorEmail) }}
                </div>
                <div class="flex-1 min-w-0">
                  <div class="flex items-center justify-between gap-2 mb-0.5">
                    <p class="text-sm font-medium truncate">{{ displayName(c) }}</p>
                    <span class="text-xs text-muted-foreground shrink-0">{{ formatTime(c.lastMessageAt) }}</span>
                  </div>
                  <p class="text-xs text-muted-foreground truncate">{{ c.lastMessagePreview ?? 'No messages yet' }}</p>
                  <div class="flex items-center gap-1.5 mt-1">
                    <Badge v-if="c.status === 'CLOSED'" variant="outline" class="text-xs">Closed</Badge>
                    <Badge v-if="c.unreadByTeam > 0 && c.status === 'OPEN'" variant="default" class="text-xs">
                      {{ c.unreadByTeam }}
                    </Badge>
                  </div>
                </div>
              </div>
            </li>
          </ul>
        </CardContent>
      </Card>

      <!-- Conversation detail -->
      <Card class="overflow-hidden flex flex-col">
        <div v-if="!selectedId || !conversation" class="flex-1 flex items-center justify-center text-sm text-muted-foreground">
          Select a conversation
        </div>
        <template v-else>
          <!-- Header -->
          <div class="px-4 py-3 flex items-center justify-between gap-3"
               style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
            <div class="flex items-center gap-3 min-w-0">
              <div class="w-9 h-9 rounded-full bg-linear-to-br from-indigo-400 to-purple-500 text-white text-xs font-semibold flex items-center justify-center shrink-0">
                {{ initials(conversation.visitorName ?? conversation.visitorEmail) }}
              </div>
              <div class="min-w-0">
                <p class="font-medium text-sm truncate">
                  {{ conversation.visitorName ?? conversation.visitorEmail ?? 'Anonymous' }}
                </p>
                <p class="text-xs text-muted-foreground truncate">
                  <span v-if="conversation.visitorEmail && conversation.visitorName">{{ conversation.visitorEmail }} · </span>
                  <span v-if="conversation.sourceUrl">{{ conversation.sourceUrl }}</span>
                </p>
              </div>
            </div>
            <div class="flex items-center gap-2 shrink-0">
              <NuxtLink
                v-if="conversation.contactId"
                :to="`/contacts/${conversation.contactId}`"
                class="text-xs text-primary hover:underline"
              >View contact →</NuxtLink>
              <Button
                v-if="conversation.status === 'OPEN'"
                size="sm"
                variant="outline"
                @click="handleClose"
              >Close</Button>
              <Badge v-else variant="outline" class="text-xs">Closed</Badge>
            </div>
          </div>

          <!-- Messages -->
          <div ref="scrollerRef" class="flex-1 overflow-y-auto p-4 space-y-3">
            <div
              v-for="msg in conversation.messages"
              :key="msg.id"
              class="flex"
              :class="msg.sender === 'AGENT' ? 'justify-end' : 'justify-start'"
            >
              <div
                v-if="msg.sender === 'SYSTEM'"
                class="mx-auto text-xs text-muted-foreground italic"
              >{{ msg.body }}</div>
              <div
                v-else
                class="max-w-[75%] rounded-2xl px-3 py-2 text-sm"
                :class="msg.sender === 'AGENT'
                  ? 'bg-primary text-primary-foreground rounded-br-sm'
                  : 'hairline bg-white/5 rounded-bl-sm'"
              >
                <p class="whitespace-pre-wrap break-words">{{ msg.body }}</p>
                <div
                  class="text-[10px] mt-1 opacity-60"
                  :class="msg.sender === 'AGENT' ? 'text-right' : 'text-left'"
                >
                  <template v-if="msg.sender === 'AGENT' && msg.agentName">{{ msg.agentName }} · </template>
                  {{ formatFullTime(msg.createdAt) }}
                </div>
              </div>
            </div>
          </div>

          <!-- Composer -->
          <div
            v-if="conversation.status === 'OPEN'"
            class="p-3 flex items-end gap-2"
            style="border-top: 1px solid hsl(240 5% 100% / 0.06);"
          >
            <textarea
              v-model="newMessage"
              rows="2"
              placeholder="Reply to this visitor…"
              class="flex-1 rounded-md border border-input bg-transparent px-3 py-2 text-sm resize-none focus:outline-none focus:border-primary"
              @keydown.enter.exact.prevent="handleSend"
            />
            <Button :disabled="!newMessage.trim() || sendMutation.isPending.value" @click="handleSend">Send</Button>
          </div>
          <div
            v-else
            class="p-4 text-center text-xs text-muted-foreground"
            style="border-top: 1px solid hsl(240 5% 100% / 0.06);"
          >
            This conversation is closed.
          </div>
        </template>
      </Card>
    </div>
  </div>
</template>
