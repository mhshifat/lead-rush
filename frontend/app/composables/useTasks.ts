import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'

export interface TaskEntity {
  id: string
  title: string
  description: string | null
  taskType: string
  contactId: string | null
  contactFullName: string | null
  sequenceId: string | null
  sequenceName: string | null
  dueAt: string | null
  status: string
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface TaskPage {
  content: TaskEntity[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface TaskFilters {
  status?: string
  page?: number
  size?: number
}

export function useTasks(filters: Ref<TaskFilters>) {
  return useQuery({
    queryKey: ['tasks', filters] as const,
    queryFn: async (): Promise<TaskPage> => {
      const { $api } = useNuxtApp()
      const params: Record<string, any> = {}
      if (filters.value.status) params.status = filters.value.status
      if (filters.value.page !== undefined) params.page = filters.value.page
      if (filters.value.size !== undefined) params.size = filters.value.size
      const res = await $api<{ data: TaskPage }>('/tasks', { params })
      return res.data
    },
  })
}

export function useCompleteTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/tasks/${id}/complete`, { method: 'POST' })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      queryClient.invalidateQueries({ queryKey: ['analytics', 'overview'] })
    },
  })
}

export function useDeleteTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/tasks/${id}`, { method: 'DELETE' })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
    },
  })
}
