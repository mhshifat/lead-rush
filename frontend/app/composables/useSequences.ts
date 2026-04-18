import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { SequenceMapper } from '~/entities/sequence/sequence.mapper'
import type { SequenceEntity, EnrollmentEntity } from '~/entities/sequence/sequence.entity'
import type {
  SequenceApiDto,
  CreateSequenceDto,
  CreateStepDto,
  EnrollRequestDto,
  EnrollmentApiDto,
} from '~/types/api/sequence.dto'

export function useSequences() {
  return useQuery({
    queryKey: ['sequences'],
    queryFn: async (): Promise<SequenceEntity[]> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: SequenceApiDto[] }>('/sequences')
      return SequenceMapper.toEntityList(response.data)
    },
  })
}

export function useSequence(id: Ref<string | null>) {
  return useQuery({
    queryKey: ['sequence', id] as const,
    queryFn: async (): Promise<SequenceEntity | null> => {
      if (!id.value) return null
      const { $api } = useNuxtApp()
      const response = await $api<{ data: SequenceApiDto }>(`/sequences/${id.value}`)
      return SequenceMapper.toEntity(response.data)
    },
    enabled: computed(() => !!id.value),
  })
}

export function useCreateSequence() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: CreateSequenceDto): Promise<SequenceEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: SequenceApiDto }>('/sequences', {
        method: 'POST',
        body: dto,
      })
      return SequenceMapper.toEntity(response.data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sequences'] })
    },
  })
}

export function useActivateSequence() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<SequenceEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: SequenceApiDto }>(`/sequences/${id}/activate`, {
        method: 'POST',
      })
      return SequenceMapper.toEntity(response.data)
    },
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ['sequences'] })
      queryClient.invalidateQueries({ queryKey: ['sequence', id] })
    },
  })
}

export function usePauseSequence() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<SequenceEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: SequenceApiDto }>(`/sequences/${id}/pause`, {
        method: 'POST',
      })
      return SequenceMapper.toEntity(response.data)
    },
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ['sequences'] })
      queryClient.invalidateQueries({ queryKey: ['sequence', id] })
    },
  })
}

export function useAddSequenceStep() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ sequenceId, dto }: { sequenceId: string; dto: CreateStepDto }): Promise<SequenceEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: SequenceApiDto }>(`/sequences/${sequenceId}/steps`, {
        method: 'POST',
        body: dto,
      })
      return SequenceMapper.toEntity(response.data)
    },
    onSuccess: (_data, { sequenceId }) => {
      queryClient.invalidateQueries({ queryKey: ['sequence', sequenceId] })
      queryClient.invalidateQueries({ queryKey: ['sequences'] })
    },
  })
}

export function useDeleteSequenceStep() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ sequenceId, stepId }: { sequenceId: string; stepId: string }): Promise<SequenceEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: SequenceApiDto }>(`/sequences/${sequenceId}/steps/${stepId}`, {
        method: 'DELETE',
      })
      return SequenceMapper.toEntity(response.data)
    },
    onSuccess: (_data, { sequenceId }) => {
      queryClient.invalidateQueries({ queryKey: ['sequence', sequenceId] })
    },
  })
}

/**
 * Fetch all enrollments for a specific contact.
 * Shows which sequences the contact is enrolled in + their progress.
 */
export function useContactEnrollments(contactId: Ref<string | null>) {
  return useQuery({
    queryKey: ['contact-enrollments', contactId] as const,
    queryFn: async (): Promise<EnrollmentEntity[]> => {
      if (!contactId.value) return []
      const { $api } = useNuxtApp()
      const response = await $api<{ data: EnrollmentApiDto[] }>(`/contacts/${contactId.value}/enrollments`)
      return response.data.map(SequenceMapper.toEnrollmentEntity)
    },
    enabled: computed(() => !!contactId.value),
  })
}

export function useEnrollContact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ sequenceId, dto }: { sequenceId: string; dto: EnrollRequestDto }): Promise<EnrollmentEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: EnrollmentApiDto }>(`/sequences/${sequenceId}/enrollments`, {
        method: 'POST',
        body: dto,
      })
      return SequenceMapper.toEnrollmentEntity(response.data)
    },
    onSuccess: (_data, { sequenceId, dto }) => {
      queryClient.invalidateQueries({ queryKey: ['sequence', sequenceId] })
      queryClient.invalidateQueries({ queryKey: ['sequences'] })
      queryClient.invalidateQueries({ queryKey: ['contact-enrollments', ref(dto.contactId)] })
    },
  })
}
