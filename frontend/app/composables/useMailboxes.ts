import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { MailboxMapper } from '~/entities/mailbox/mailbox.mapper'
import type { MailboxEntity } from '~/entities/mailbox/mailbox.entity'
import type { MailboxApiDto, CreateMailboxDto } from '~/types/api/mailbox.dto'

export function useMailboxes() {
  return useQuery({
    queryKey: ['mailboxes'],
    queryFn: async (): Promise<MailboxEntity[]> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: MailboxApiDto[] }>('/mailboxes')
      return MailboxMapper.toEntityList(response.data)
    },
  })
}

export function useConnectMailbox() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: CreateMailboxDto): Promise<MailboxEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: MailboxApiDto }>('/mailboxes', {
        method: 'POST',
        body: dto,
      })
      return MailboxMapper.toEntity(response.data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mailboxes'] })
    },
  })
}

export function useTestMailbox() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<boolean> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: { connected: boolean } }>(`/mailboxes/${id}/test`, {
        method: 'POST',
      })
      return response.data.connected
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mailboxes'] })
    },
  })
}

export function useDeleteMailbox() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      const { $api } = useNuxtApp()
      await $api(`/mailboxes/${id}`, { method: 'DELETE' })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mailboxes'] })
    },
  })
}
