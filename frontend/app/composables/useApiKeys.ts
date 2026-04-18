import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import type { ApiKeyApiDto, CreateApiKeyDto } from '~/types/api/api-key.dto'

export function useApiKeys() {
  return useQuery({
    queryKey: ['api-keys'],
    queryFn: async (): Promise<ApiKeyApiDto[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: ApiKeyApiDto[] }>('/api-keys')
      return res.data
    },
  })
}

export function useCreateApiKey() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: CreateApiKeyDto): Promise<ApiKeyApiDto> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: ApiKeyApiDto }>('/api-keys', {
        method: 'POST',
        body: dto,
      })
      return res.data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['api-keys'] }),
  })
}

export function useRevokeApiKey() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/api-keys/${id}`, { method: 'DELETE' })
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['api-keys'] }),
  })
}
