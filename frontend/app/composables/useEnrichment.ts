import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'

export interface EnrichmentProvider {
  id: string
  providerKey: string
  displayName: string
  enabled: boolean
  priority: number
  hasApiKey: boolean
  requiresApiKey: boolean
  callsThisMonth: number
  lastUsedAt: string | null
  lastError: string | null
}

export interface EnrichmentResult {
  id: string
  contactId: string
  providerKey: string
  status: string
  foundEmail: string | null
  foundPhone: string | null
  foundTitle: string | null
  foundLinkedinUrl: string | null
  confidenceScore: number | null
  errorMessage: string | null
  enrichedAt: string
}

export interface BulkEnrichmentResult {
  total: number
  succeeded: number
  notFound: number
  errored: number
}

export interface UpdateProviderDto {
  providerKey: string
  apiKey?: string
  enabled?: boolean
  priority?: number
}

export function useEnrichmentProviders() {
  return useQuery({
    queryKey: ['enrichment-providers'],
    queryFn: async (): Promise<EnrichmentProvider[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: EnrichmentProvider[] }>('/enrichment/providers')
      return res.data
    },
  })
}

export function useUpdateEnrichmentProvider() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: UpdateProviderDto): Promise<EnrichmentProvider> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: EnrichmentProvider }>('/enrichment/providers', {
        method: 'PUT',
        body: dto,
      })
      return res.data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['enrichment-providers'] }),
  })
}

export function useEnrichContact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (contactId: string): Promise<EnrichmentResult | null> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: EnrichmentResult | null }>(`/enrichment/contacts/${contactId}`, {
        method: 'POST',
      })
      return res.data
    },
    onSuccess: (_data, contactId) => {
      queryClient.invalidateQueries({ queryKey: ['contact', ref(contactId)] })
      queryClient.invalidateQueries({ queryKey: ['enrichment-results', ref(contactId)] })
    },
  })
}

export function useBulkEnrich() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (contactIds: string[]): Promise<BulkEnrichmentResult> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: BulkEnrichmentResult }>('/enrichment/bulk', {
        method: 'POST',
        body: { contactIds },
      })
      return res.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contacts'] })
    },
  })
}

export function useEnrichmentResults(contactId: Ref<string | null>) {
  return useQuery({
    queryKey: ['enrichment-results', contactId] as const,
    queryFn: async (): Promise<EnrichmentResult[]> => {
      if (!contactId.value) return []
      const { $api } = useNuxtApp()
      const res = await $api<{ data: EnrichmentResult[] }>(`/enrichment/contacts/${contactId.value}/results`)
      return res.data
    },
    enabled: computed(() => !!contactId.value),
  })
}
