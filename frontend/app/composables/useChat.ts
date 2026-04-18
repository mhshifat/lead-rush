import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import type {
  ChatConversationSummaryDto,
  ChatConversationDetailDto,
  ChatMessageDto,
  ChatWidgetConfigDto,
  UpdateWidgetDto,
} from '~/types/api/chat.dto'

interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export function useChatConversations() {
  return useQuery({
    queryKey: ['chat', 'conversations'],
    queryFn: async (): Promise<Page<ChatConversationSummaryDto>> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: Page<ChatConversationSummaryDto> }>('/chat/conversations?size=50')
      return res.data
    },
  })
}

export function useChatUnreadCount() {
  return useQuery({
    queryKey: ['chat', 'unread-count'],
    queryFn: async (): Promise<number> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: { count: number } }>('/chat/conversations/unread-count')
      return res.data.count
    },
    refetchInterval: 30_000,
  })
}

export function useChatConversation(id: Ref<string | null>) {
  return useQuery({
    queryKey: ['chat', 'conversation', id] as const,
    queryFn: async (): Promise<ChatConversationDetailDto | null> => {
      if (!id.value) return null
      const { $api } = useNuxtApp()
      const res = await $api<{ data: ChatConversationDetailDto }>(`/chat/conversations/${id.value}`)
      return res.data
    },
    enabled: computed(() => !!id.value),
  })
}

export function useSendChatMessage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (args: { id: string; message: string }): Promise<ChatMessageDto> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: ChatMessageDto }>(
        `/chat/conversations/${args.id}/messages`,
        { method: 'POST', body: { message: args.message } }
      )
      return res.data
    },
    onSuccess: (_data, args) => {
      qc.invalidateQueries({ queryKey: ['chat', 'conversation', ref(args.id)] })
      qc.invalidateQueries({ queryKey: ['chat', 'conversations'] })
    },
  })
}

export function useCloseChatConversation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/chat/conversations/${id}/close`, { method: 'POST' })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['chat', 'conversations'] })
    },
  })
}

// ── Widget config ──

export function useChatWidgetConfig() {
  return useQuery({
    queryKey: ['chat', 'widget'],
    queryFn: async (): Promise<ChatWidgetConfigDto> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: ChatWidgetConfigDto }>('/chat/widget')
      return res.data
    },
  })
}

export function useUpdateChatWidget() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (dto: UpdateWidgetDto): Promise<ChatWidgetConfigDto> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: ChatWidgetConfigDto }>('/chat/widget', {
        method: 'PUT', body: dto,
      })
      return res.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['chat', 'widget'] }),
  })
}
