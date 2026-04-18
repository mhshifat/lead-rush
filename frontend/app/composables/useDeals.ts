import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { DealMapper } from '~/entities/deal/deal.mapper'
import type { DealEntity } from '~/entities/deal/deal.entity'
import type { DealApiDto, CreateDealDto, UpdateDealDto } from '~/types/api/deal.dto'

/** Fetch all deals in a pipeline — feeds the Kanban board. */
export function usePipelineDeals(pipelineId: Ref<string | null>) {
  return useQuery({
    queryKey: ['pipeline-deals', pipelineId] as const,
    queryFn: async (): Promise<DealEntity[]> => {
      if (!pipelineId.value) return []
      const { $api } = useNuxtApp()
      const res = await $api<{ data: DealApiDto[] }>(`/pipelines/${pipelineId.value}/deals`)
      return DealMapper.toEntityList(res.data)
    },
    enabled: computed(() => !!pipelineId.value),
  })
}

export function useContactDeals(contactId: Ref<string | null>) {
  return useQuery({
    queryKey: ['contact-deals', contactId] as const,
    queryFn: async (): Promise<DealEntity[]> => {
      if (!contactId.value) return []
      const { $api } = useNuxtApp()
      const res = await $api<{ data: DealApiDto[] }>(`/contacts/${contactId.value}/deals`)
      return DealMapper.toEntityList(res.data)
    },
    enabled: computed(() => !!contactId.value),
  })
}

export function useCreateDeal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: CreateDealDto): Promise<DealEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: DealApiDto }>('/deals', { method: 'POST', body: dto })
      return DealMapper.toEntity(res.data)
    },
    onSuccess: (_data, dto) => {
      queryClient.invalidateQueries({ queryKey: ['pipeline-deals', ref(dto.pipelineId)] })
      queryClient.invalidateQueries({ queryKey: ['analytics', 'overview'] })
    },
  })
}

export function useUpdateDeal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, dto }: { id: string; dto: UpdateDealDto }): Promise<DealEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: DealApiDto }>(`/deals/${id}`, { method: 'PUT', body: dto })
      return DealMapper.toEntity(res.data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pipeline-deals'] })
      queryClient.invalidateQueries({ queryKey: ['contact-deals'] })
    },
  })
}

/**
 * Move a deal to a different stage — used by Kanban drag-and-drop.
 *
 * OPTIMISTIC UPDATE: we update the cache BEFORE the API responds so the card
 * "sticks" where the user dropped it instantly. If the API fails, we roll back.
 */
export function useMoveDeal() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ dealId, pipelineStageId }: { dealId: string; pipelineStageId: string }): Promise<DealEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: DealApiDto }>(`/deals/${dealId}/move`, {
        method: 'POST',
        body: { pipelineStageId },
      })
      return DealMapper.toEntity(res.data)
    },

    onMutate: async ({ dealId, pipelineStageId }) => {
      // Cancel queries in-flight so they don't overwrite our optimistic update
      await queryClient.cancelQueries({ queryKey: ['pipeline-deals'] })

      // Find the pipeline this deal belongs to from the cache
      const cacheEntries = queryClient.getQueriesData<DealEntity[]>({ queryKey: ['pipeline-deals'] })
      const snapshot: Array<[any, DealEntity[] | undefined]> = cacheEntries.map(([key, data]) => [key, data])

      // Update every matching cache entry with the new stage
      for (const [key, data] of cacheEntries) {
        if (!data) continue
        const updated = data.map(d =>
          d.id === dealId ? { ...d, pipelineStageId } : d
        )
        queryClient.setQueryData(key, updated)
      }

      return { snapshot }
    },

    onError: (_err, _vars, context) => {
      // Rollback
      if (context?.snapshot) {
        for (const [key, data] of context.snapshot) {
          queryClient.setQueryData(key, data)
        }
      }
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['pipeline-deals'] })
    },
  })
}

export function useDeleteDeal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/deals/${id}`, { method: 'DELETE' })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pipeline-deals'] })
      queryClient.invalidateQueries({ queryKey: ['contact-deals'] })
    },
  })
}
