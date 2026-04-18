import { useQuery, useMutation } from '@tanstack/vue-query'
import type {
  AIStatusDto,
  GenerateEmailDto,
  GeneratedEmailApiDto,
  SubjectSuggestionsDto,
  SubjectSuggestionsResponseDto,
} from '~/types/api/ai.dto'

/** Cheap ready-check so the UI can hide AI buttons when the key isn't set. */
export function useAIStatus() {
  return useQuery({
    queryKey: ['ai', 'status'],
    queryFn: async (): Promise<AIStatusDto> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: AIStatusDto }>('/ai/status')
      return res.data
    },
    staleTime: 60_000,
  })
}

export function useGenerateEmail() {
  return useMutation({
    mutationFn: async (dto: GenerateEmailDto): Promise<GeneratedEmailApiDto> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: GeneratedEmailApiDto }>('/ai/generate-email', {
        method: 'POST',
        body: dto,
      })
      return res.data
    },
  })
}

export function useSuggestSubjects() {
  return useMutation({
    mutationFn: async (dto: SubjectSuggestionsDto): Promise<string[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: SubjectSuggestionsResponseDto }>('/ai/suggest-subject-lines', {
        method: 'POST',
        body: dto,
      })
      return res.data.suggestions
    },
  })
}
