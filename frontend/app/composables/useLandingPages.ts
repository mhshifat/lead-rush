import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'

export interface LandingPage {
  id: string
  name: string
  slug: string
  metaTitle: string | null
  metaDescription: string | null
  blocks: any              // JSON array, returned as-is by backend
  status: 'DRAFT' | 'PUBLISHED'
  publishedAt: string | null
  viewCount: number
  conversionCount: number
  createdAt: string
  updatedAt: string
}

export interface CreateLandingPageDto {
  name: string
  slug?: string
  metaTitle?: string
  metaDescription?: string
  blocks?: string          // JSON string
}

export interface Form {
  id: string
  name: string
  description: string | null
  fields: any              // JSON array
  successRedirectUrl: string | null
  successMessage: string | null
  autoEnrollSequenceId: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateFormDto {
  name: string
  description?: string
  fields?: string          // JSON string
  successRedirectUrl?: string
  successMessage?: string
  autoEnrollSequenceId?: string
}

// ── Landing Pages ──

export function useLandingPages() {
  return useQuery({
    queryKey: ['landing-pages'],
    queryFn: async (): Promise<LandingPage[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: LandingPage[] }>('/landing-pages')
      return res.data
    },
  })
}

export function useLandingPage(id: Ref<string | null>) {
  return useQuery({
    queryKey: ['landing-page', id] as const,
    queryFn: async (): Promise<LandingPage | null> => {
      if (!id.value) return null
      const { $api } = useNuxtApp()
      const res = await $api<{ data: LandingPage }>(`/landing-pages/${id.value}`)
      return res.data
    },
    enabled: computed(() => !!id.value),
  })
}

export function useCreateLandingPage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: CreateLandingPageDto): Promise<LandingPage> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: LandingPage }>('/landing-pages', { method: 'POST', body: dto })
      return res.data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['landing-pages'] }),
  })
}

export function useUpdateLandingPage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, dto }: { id: string; dto: CreateLandingPageDto }): Promise<LandingPage> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: LandingPage }>(`/landing-pages/${id}`, { method: 'PUT', body: dto })
      return res.data
    },
    onSuccess: (_data, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['landing-page', id] })
      queryClient.invalidateQueries({ queryKey: ['landing-pages'] })
    },
  })
}

export function usePublishLandingPage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, publish }: { id: string; publish: boolean }): Promise<LandingPage> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: LandingPage }>(
        `/landing-pages/${id}/${publish ? 'publish' : 'unpublish'}`,
        { method: 'POST' }
      )
      return res.data
    },
    onSuccess: (_data, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['landing-page', id] })
      queryClient.invalidateQueries({ queryKey: ['landing-pages'] })
    },
  })
}

export function useDeleteLandingPage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/landing-pages/${id}`, { method: 'DELETE' })
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['landing-pages'] }),
  })
}

// ── Forms ──

export function useForms() {
  return useQuery({
    queryKey: ['forms'],
    queryFn: async (): Promise<Form[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: Form[] }>('/forms')
      return res.data
    },
  })
}

export function useCreateForm() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: CreateFormDto): Promise<Form> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: Form }>('/forms', { method: 'POST', body: dto })
      return res.data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['forms'] }),
  })
}
