import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import type {
  WebhookEndpointApiDto,
  WebhookDeliveryApiDto,
  WebhookEndpointDto,
} from '~/types/api/webhook.dto'

interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export function useWebhooks() {
  return useQuery({
    queryKey: ['webhooks'],
    queryFn: async (): Promise<WebhookEndpointApiDto[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: WebhookEndpointApiDto[] }>('/webhooks')
      return res.data
    },
  })
}

export function useWebhookEventTypes() {
  return useQuery({
    queryKey: ['webhook-event-types'],
    queryFn: async (): Promise<string[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: string[] }>('/webhooks/event-types')
      return res.data
    },
    staleTime: 5 * 60_000,
  })
}

export function useCreateWebhook() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (dto: WebhookEndpointDto): Promise<WebhookEndpointApiDto> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: WebhookEndpointApiDto }>('/webhooks', {
        method: 'POST', body: dto,
      })
      return res.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['webhooks'] }),
  })
}

export function useUpdateWebhook() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (args: { id: string; dto: WebhookEndpointDto }): Promise<WebhookEndpointApiDto> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: WebhookEndpointApiDto }>(`/webhooks/${args.id}`, {
        method: 'PUT', body: args.dto,
      })
      return res.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['webhooks'] }),
  })
}

export function useDeleteWebhook() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/webhooks/${id}`, { method: 'DELETE' })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['webhooks'] }),
  })
}

export function useRotateWebhookSecret() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<WebhookEndpointApiDto> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: WebhookEndpointApiDto }>(`/webhooks/${id}/rotate-secret`, {
        method: 'POST',
      })
      return res.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['webhooks'] }),
  })
}

export function useTestWebhook() {
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/webhooks/${id}/test`, { method: 'POST' })
    },
  })
}

export function useWebhookDeliveries(endpointId: Ref<string | null>) {
  return useQuery({
    queryKey: ['webhook-deliveries', endpointId] as const,
    queryFn: async (): Promise<Page<WebhookDeliveryApiDto>> => {
      if (!endpointId.value) return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 0 }
      const { $api } = useNuxtApp()
      const res = await $api<{ data: Page<WebhookDeliveryApiDto> }>(
        `/webhooks/${endpointId.value}/deliveries?size=20`
      )
      return res.data
    },
    enabled: computed(() => !!endpointId.value),
  })
}
