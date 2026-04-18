import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { PipelineMapper } from '~/entities/pipeline/pipeline.mapper'
import type { PipelineEntity } from '~/entities/pipeline/pipeline.entity'
import type {
  PipelineApiDto,
  CreatePipelineDto,
  CreateStageDto,
} from '~/types/api/pipeline.dto'

export function usePipelines() {
  return useQuery({
    queryKey: ['pipelines'],
    queryFn: async (): Promise<PipelineEntity[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: PipelineApiDto[] }>('/pipelines')
      return PipelineMapper.toEntityList(res.data)
    },
  })
}

export function usePipeline(id: Ref<string | null>) {
  return useQuery({
    queryKey: ['pipeline', id] as const,
    queryFn: async (): Promise<PipelineEntity | null> => {
      if (!id.value) return null
      const { $api } = useNuxtApp()
      const res = await $api<{ data: PipelineApiDto }>(`/pipelines/${id.value}`)
      return PipelineMapper.toEntity(res.data)
    },
    enabled: computed(() => !!id.value),
  })
}

export function useCreatePipeline() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: CreatePipelineDto): Promise<PipelineEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: PipelineApiDto }>('/pipelines', { method: 'POST', body: dto })
      return PipelineMapper.toEntity(res.data)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pipelines'] }),
  })
}

export function useDeletePipeline() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/pipelines/${id}`, { method: 'DELETE' })
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pipelines'] }),
  })
}

export function useAddStage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ pipelineId, dto }: { pipelineId: string; dto: CreateStageDto }) => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: PipelineApiDto }>(`/pipelines/${pipelineId}/stages`, {
        method: 'POST',
        body: dto,
      })
      return PipelineMapper.toEntity(res.data)
    },
    onSuccess: (_data, { pipelineId }) => {
      queryClient.invalidateQueries({ queryKey: ['pipeline', pipelineId] })
      queryClient.invalidateQueries({ queryKey: ['pipelines'] })
    },
  })
}

export function useDeleteStage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ pipelineId, stageId }: { pipelineId: string; stageId: string }) => {
      const { $api } = useNuxtApp()
      await $api(`/pipelines/${pipelineId}/stages/${stageId}`, { method: 'DELETE' })
    },
    onSuccess: (_data, { pipelineId }) => {
      queryClient.invalidateQueries({ queryKey: ['pipeline', pipelineId] })
    },
  })
}
