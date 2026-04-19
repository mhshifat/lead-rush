<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Badge } from '~/components/ui/badge'
import { Input } from '~/components/ui/input'
import { useQueryClient } from '@tanstack/vue-query'
import { toast } from 'vue-sonner'
import {
  Settings, ArrowRight, MessageSquare, Search, Send, X,
  Inbox, ExternalLink, User as UserIcon,
} from 'lucide-vue-next'
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

// ── Filter + search over the conversation list ──
const statusFilter = ref<'ALL' | 'OPEN' | 'CLOSED'>('ALL')
const searchQuery = ref('')

const filteredConversations = computed(() => {
  const all = conversationsPage.value?.content ?? []
  const q = searchQuery.value.trim().toLowerCase()
  return all.filter(c => {
    if (statusFilter.value !== 'ALL' && c.status !== statusFilter.value) return false
    if (!q) return true
    const hay = `${c.visitorName ?? ''} ${c.visitorEmail ?? ''} ${c.lastMessagePreview ?? ''}`.toLowerCase()
    return hay.includes(q)
  })
})

const openCount = computed(() => (conversationsPage.value?.content ?? []).filter(c => c.status === 'OPEN').length)
const closedCount = computed(() => (conversationsPage.value?.content ?? []).filter(c => c.status === 'CLOSED').length)

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
  const ok = await useConfirm().ask({
    title: 'Close this conversation?',
    confirmLabel: 'Close',
  })
  if (!ok) return
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
  if (mins < 1) return 'now'
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
  <div class="space-y-4 enter-fade-up">
    <!-- Header -->
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Chat</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          Live conversations from your embedded widget.
        </p>
      </div>
      <NuxtLink
        to="/chat/settings"
        class="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
      >
        <Settings class="h-3.5 w-3.5" />
        Widget settings
        <ArrowRight class="h-3.5 w-3.5" />
      </NuxtLink>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-[22rem_1fr] gap-4 h-[calc(100vh-180px)] min-h-[500px]">
      <!-- ── Conversation list ── -->
      <div class="glass hairline rounded-xl overflow-hidden flex flex-col min-h-0">
        <!-- List header: title + total + filter pills -->
        <div class="px-4 py-3" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-2 min-w-0">
              <Inbox class="h-4 w-4 text-muted-foreground" />
              <h3 class="text-sm font-semibold tracking-tight">Conversations</h3>
            </div>
            <span class="text-xs text-muted-foreground tabular-nums">
              {{ conversationsPage?.totalElements ?? 0 }}
            </span>
          </div>

          <!-- Status filter pills -->
          <div class="flex items-center gap-1 mb-2">
            <button
              v-for="opt in [
                { key: 'ALL',    label: 'All',    count: conversationsPage?.totalElements ?? 0 },
                { key: 'OPEN',   label: 'Open',   count: openCount },
                { key: 'CLOSED', label: 'Closed', count: closedCount },
              ]"
              :key="opt.key"
              type="button"
              class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium transition-colors"
              :class="statusFilter === (opt.key as any)
                ? 'bg-primary/15 text-primary'
                : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
              @click="statusFilter = opt.key as any"
            >
              {{ opt.label }}
              <span class="tabular-nums opacity-70">{{ opt.count }}</span>
            </button>
          </div>

          <!-- Search -->
          <div class="relative">
            <Search class="h-3.5 w-3.5 text-muted-foreground absolute left-2.5 top-1/2 -translate-y-1/2" />
            <Input
              v-model="searchQuery"
              placeholder="Search conversations…"
              class="h-8 pl-8 text-xs"
            />
          </div>
        </div>

        <!-- List body -->
        <div class="flex-1 overflow-y-auto min-h-0">
          <div v-if="isLoading" class="p-4 text-sm text-muted-foreground text-center">
            Loading…
          </div>
          <div
            v-else-if="!conversationsPage?.content.length"
            class="p-8 text-center"
          >
            <div class="h-10 w-10 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-3">
              <MessageSquare class="h-4 w-4 text-muted-foreground" />
            </div>
            <p class="text-sm font-medium">No conversations yet</p>
            <p class="text-xs text-muted-foreground mt-1">
              Embed the widget on your site to start capturing chats.
            </p>
            <NuxtLink to="/chat/settings" class="inline-block mt-3 text-xs text-primary hover:underline">
              Get widget code →
            </NuxtLink>
          </div>
          <div
            v-else-if="!filteredConversations.length"
            class="p-8 text-center text-xs text-muted-foreground"
          >
            No conversations match your filters.
          </div>
          <ul v-else>
            <li
              v-for="c in filteredConversations"
              :key="c.id"
              class="px-4 py-3 cursor-pointer transition-colors"
              :class="selectedId === c.id ? 'bg-primary/5' : 'hover:bg-white/5'"
              style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
              @click="selectedId = c.id"
            >
              <div class="flex items-start gap-3">
                <div class="relative shrink-0">
                  <div class="w-9 h-9 rounded-full bg-linear-to-br from-indigo-400 to-purple-500 text-white text-xs font-semibold flex items-center justify-center">
                    {{ initials(c.visitorName ?? c.visitorEmail) }}
                  </div>
                  <span
                    class="absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full ring-2 ring-background"
                    :class="c.status === 'OPEN' ? 'bg-emerald-400' : 'bg-muted-foreground/40'"
                  />
                </div>
                <div class="flex-1 min-w-0">
                  <div class="flex items-center justify-between gap-2 mb-0.5">
                    <p class="text-sm font-medium truncate">{{ displayName(c) }}</p>
                    <span class="text-xs text-muted-foreground shrink-0 tabular-nums">{{ formatTime(c.lastMessageAt) }}</span>
                  </div>
                  <p class="text-xs text-muted-foreground truncate">{{ c.lastMessagePreview ?? 'No messages yet' }}</p>
                  <div v-if="c.unreadByTeam > 0 && c.status === 'OPEN'" class="flex items-center gap-1.5 mt-1">
                    <Badge variant="default" class="text-[10px] h-4 px-1.5">
                      {{ c.unreadByTeam }} unread
                    </Badge>
                  </div>
                </div>
              </div>
            </li>
          </ul>
        </div>
      </div>

      <!-- ── Conversation detail ── -->
      <div class="glass hairline rounded-xl overflow-hidden flex flex-col min-h-0">
        <!-- Empty state when nothing selected -->
        <div
          v-if="!selectedId || !conversation"
          class="flex-1 flex flex-col items-center justify-center text-center px-6"
        >
          <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mb-4">
            <MessageSquare class="h-5 w-5 text-muted-foreground" />
          </div>
          <h3 class="text-sm font-semibold tracking-tight">Select a conversation</h3>
          <p class="text-sm text-muted-foreground mt-1 max-w-sm">
            Pick a chat from the list on the left to read messages and reply in real time.
          </p>
        </div>

        <template v-else>
          <!-- Detail header -->
          <div
            class="px-5 py-3 flex items-center justify-between gap-3"
            style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);"
          >
            <div class="flex items-center gap-3 min-w-0">
              <div class="relative shrink-0">
                <div class="w-10 h-10 rounded-full bg-linear-to-br from-indigo-400 to-purple-500 text-white text-sm font-semibold flex items-center justify-center">
                  {{ initials(conversation.visitorName ?? conversation.visitorEmail) }}
                </div>
                <span
                  class="absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full ring-2 ring-background"
                  :class="conversation.status === 'OPEN' ? 'bg-emerald-400' : 'bg-muted-foreground/40'"
                />
              </div>
              <div class="min-w-0">
                <p class="font-semibold tracking-tight truncate">
                  {{ conversation.visitorName ?? conversation.visitorEmail ?? 'Anonymous visitor' }}
                </p>
                <p class="text-xs text-muted-foreground truncate">
                  <span v-if="conversation.visitorEmail && conversation.visitorName">{{ conversation.visitorEmail }}</span>
                  <span v-if="conversation.visitorEmail && conversation.visitorName && conversation.sourceUrl"> · </span>
                  <span v-if="conversation.sourceUrl" class="font-mono">{{ conversation.sourceUrl }}</span>
                  <span v-if="!conversation.visitorEmail && !conversation.sourceUrl">No identifying details</span>
                </p>
              </div>
            </div>
            <div class="flex items-center gap-2 shrink-0">
              <NuxtLink
                v-if="conversation.contactId"
                :to="`/contacts/${conversation.contactId}`"
                class="inline-flex items-center gap-1 text-xs text-primary hover:underline"
              >
                <UserIcon class="h-3 w-3" />
                Contact
                <ArrowRight class="h-3 w-3" />
              </NuxtLink>
              <Button
                v-if="conversation.status === 'OPEN'"
                size="sm"
                variant="outline"
                class="gap-1 h-8"
                @click="handleClose"
              >
                <X class="h-3.5 w-3.5" />
                Close
              </Button>
              <Badge v-else variant="outline" class="text-xs">Closed</Badge>
            </div>
          </div>

          <!-- Messages scroll area -->
          <div ref="scrollerRef" class="flex-1 overflow-y-auto p-5 space-y-3 min-h-0">
            <div
              v-for="msg in conversation.messages"
              :key="msg.id"
              class="flex"
              :class="msg.sender === 'AGENT' ? 'justify-end' : msg.sender === 'SYSTEM' ? 'justify-center' : 'justify-start'"
            >
              <!-- System / event message -->
              <div
                v-if="msg.sender === 'SYSTEM'"
                class="text-xs text-muted-foreground italic px-3 py-1 rounded-full hairline bg-white/2"
              >
                {{ msg.body }}
              </div>

              <!-- Visitor or agent message bubble -->
              <div v-else class="flex items-end gap-2 max-w-[75%]" :class="msg.sender === 'AGENT' ? 'flex-row-reverse' : ''">
                <div
                  v-if="msg.sender === 'VISITOR'"
                  class="w-7 h-7 rounded-full bg-linear-to-br from-indigo-400 to-purple-500 text-white text-[10px] font-semibold flex items-center justify-center shrink-0"
                >
                  {{ initials(conversation.visitorName ?? conversation.visitorEmail) }}
                </div>
                <div
                  class="rounded-2xl px-3.5 py-2 text-sm"
                  :class="msg.sender === 'AGENT'
                    ? 'bg-primary text-primary-foreground rounded-br-sm'
                    : 'hairline bg-white/5 rounded-bl-sm'"
                >
                  <p class="whitespace-pre-wrap wrap-break-word">{{ msg.body }}</p>
                  <div
                    class="text-[10px] mt-1 opacity-60 tabular-nums"
                    :class="msg.sender === 'AGENT' ? 'text-right' : 'text-left'"
                  >
                    <template v-if="msg.sender === 'AGENT' && msg.agentName">{{ msg.agentName }} · </template>
                    {{ formatFullTime(msg.createdAt) }}
                  </div>
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
              rows="1"
              placeholder="Reply to this visitor — Enter to send, Shift+Enter for new line"
              class="flex-1 max-h-32 rounded-md border border-input bg-transparent px-3 py-2 text-sm resize-none focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary/40"
              @keydown.enter.exact.prevent="handleSend"
            />
            <Button
              :disabled="!newMessage.trim() || sendMutation.isPending.value"
              class="h-9 gap-1.5"
              @click="handleSend"
            >
              <Send class="h-3.5 w-3.5" />
              Send
            </Button>
          </div>
          <div
            v-else
            class="p-4 text-center text-xs text-muted-foreground inline-flex items-center justify-center gap-1.5"
            style="border-top: 1px solid hsl(240 5% 100% / 0.06);"
          >
            <X class="h-3 w-3" />
            This conversation is closed.
            <a
              v-if="conversation.sourceUrl"
              :href="conversation.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="text-primary hover:underline inline-flex items-center gap-0.5 ml-2"
            >
              Visit page <ExternalLink class="h-3 w-3" />
            </a>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
