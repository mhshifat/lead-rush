import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { LeadScoringMapper } from '~/entities/lead-scoring/lead-scoring.mapper'
import type {
  LeadScoreRuleEntity,
  LeadScoreLogEntity,
} from '~/entities/lead-scoring/lead-scoring.entity'
import type {
  LeadScoreRuleApiDto,
  LeadScoreLogApiDto,
  CreateLeadScoreRuleDto,
  AdjustScoreDto,
} from '~/types/api/lead-scoring.dto'

// ── Rules ──

export function useLeadScoreRules() {
  return useQuery({
    queryKey: ['lead-score-rules'],
    queryFn: async (): Promise<LeadScoreRuleEntity[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: LeadScoreRuleApiDto[] }>('/lead-score/rules')
      return LeadScoringMapper.toRuleEntityList(res.data)
    },
  })
}

export function useCreateLeadScoreRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: CreateLeadScoreRuleDto): Promise<LeadScoreRuleEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: LeadScoreRuleApiDto }>('/lead-score/rules', {
        method: 'POST',
        body: dto,
      })
      return LeadScoringMapper.toRuleEntity(res.data)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['lead-score-rules'] }),
  })
}

export function useUpdateLeadScoreRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (args: { id: string; dto: CreateLeadScoreRuleDto }): Promise<LeadScoreRuleEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: LeadScoreRuleApiDto }>(`/lead-score/rules/${args.id}`, {
        method: 'PUT',
        body: args.dto,
      })
      return LeadScoringMapper.toRuleEntity(res.data)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['lead-score-rules'] }),
  })
}

export function useDeleteLeadScoreRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/lead-score/rules/${id}`, { method: 'DELETE' })
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['lead-score-rules'] }),
  })
}

// ── Score actions ──

export function useRecalculateScores() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (): Promise<{ contactsProcessed: number }> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: { contactsProcessed: number } }>('/lead-score/recalculate', {
        method: 'POST',
      })
      return res.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contacts'] })
      queryClient.invalidateQueries({ queryKey: ['contact'] })
    },
  })
}

export function useAdjustScore() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (args: { contactId: string; dto: AdjustScoreDto }): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/lead-score/contacts/${args.contactId}/adjust`, {
        method: 'POST',
        body: args.dto,
      })
    },
    onSuccess: (_data, args) => {
      queryClient.invalidateQueries({ queryKey: ['contact', ref(args.contactId)] })
      queryClient.invalidateQueries({ queryKey: ['lead-score-history', ref(args.contactId)] })
    },
  })
}

// ── History ──

export function useLeadScoreHistory(contactId: Ref<string | null>) {
  return useQuery({
    queryKey: ['lead-score-history', contactId] as const,
    queryFn: async (): Promise<LeadScoreLogEntity[]> => {
      if (!contactId.value) return []
      const { $api } = useNuxtApp()
      const res = await $api<{ data: LeadScoreLogApiDto[] }>(`/lead-score/contacts/${contactId.value}/history`)
      return LeadScoringMapper.toLogEntityList(res.data)
    },
    enabled: computed(() => !!contactId.value),
  })
}
