import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { EmailTemplateMapper } from '~/entities/email-template/email-template.mapper'
import type { EmailTemplateEntity } from '~/entities/email-template/email-template.entity'
import type { EmailTemplateApiDto, CreateEmailTemplateDto } from '~/types/api/email-template.dto'

export function useEmailTemplates() {
  return useQuery({
    queryKey: ['email-templates'],
    queryFn: async (): Promise<EmailTemplateEntity[]> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: EmailTemplateApiDto[] }>('/email-templates')
      return EmailTemplateMapper.toEntityList(response.data)
    },
  })
}

export function useEmailTemplate(id: Ref<string | null>) {
  return useQuery({
    queryKey: ['email-template', id] as const,
    queryFn: async (): Promise<EmailTemplateEntity | null> => {
      if (!id.value) return null
      const { $api } = useNuxtApp()
      const response = await $api<{ data: EmailTemplateApiDto }>(`/email-templates/${id.value}`)
      return EmailTemplateMapper.toEntity(response.data)
    },
    enabled: computed(() => !!id.value),
  })
}

export function useCreateEmailTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: CreateEmailTemplateDto): Promise<EmailTemplateEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: EmailTemplateApiDto }>('/email-templates', {
        method: 'POST',
        body: dto,
      })
      return EmailTemplateMapper.toEntity(response.data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-templates'] })
    },
  })
}

export function useUpdateEmailTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, dto }: { id: string; dto: CreateEmailTemplateDto }): Promise<EmailTemplateEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: EmailTemplateApiDto }>(`/email-templates/${id}`, {
        method: 'PUT',
        body: dto,
      })
      return EmailTemplateMapper.toEntity(response.data)
    },
    onSuccess: (_data, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['email-templates'] })
      queryClient.invalidateQueries({ queryKey: ['email-template', id] })
    },
  })
}

export function useDeleteEmailTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      const { $api } = useNuxtApp()
      await $api(`/email-templates/${id}`, { method: 'DELETE' })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-templates'] })
    },
  })
}
