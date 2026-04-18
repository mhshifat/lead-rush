import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { ContactMapper } from '~/entities/contact/contact.mapper'
import type { ContactEntity } from '~/entities/contact/contact.entity'
import type { ContactApiDto, CreateContactDto, PageDto } from '~/types/api/contact.dto'

export interface ContactFilters {
  search?: string
  lifecycleStage?: string
  companyId?: string
  minScore?: number
  page?: number
  size?: number
  sort?: string
}

export interface PaginatedContacts {
  items: ContactEntity[]
  totalElements: number
  totalPages: number
  currentPage: number
  pageSize: number
}

export function useContacts(filters: Ref<ContactFilters>) {
  return useQuery({
    queryKey: ['contacts', filters] as const,
    queryFn: async (): Promise<PaginatedContacts> => {
      const { $api } = useNuxtApp()

      const params: Record<string, any> = {}
      if (filters.value.search) params.search = filters.value.search
      if (filters.value.lifecycleStage) params.lifecycleStage = filters.value.lifecycleStage
      if (filters.value.companyId) params.companyId = filters.value.companyId
      if (filters.value.minScore !== undefined) params.minScore = filters.value.minScore
      if (filters.value.page !== undefined) params.page = filters.value.page
      if (filters.value.size !== undefined) params.size = filters.value.size
      if (filters.value.sort) params.sort = filters.value.sort

      const response = await $api<{ data: PageDto<ContactApiDto> }>('/contacts', {
        params,
      })

      const page = response.data
      return {
        items: ContactMapper.toEntityList(page.content),
        totalElements: page.totalElements,
        totalPages: page.totalPages,
        currentPage: page.number,
        pageSize: page.size,
      }
    },
  })
}

/** Fetch a single contact by ID. */
export function useContact(id: Ref<string | null>) {
  return useQuery({
    queryKey: ['contact', id] as const,
    queryFn: async (): Promise<ContactEntity | null> => {
      if (!id.value) return null
      const { $api } = useNuxtApp()
      const response = await $api<{ data: ContactApiDto }>(`/contacts/${id.value}`)
      return ContactMapper.toEntity(response.data)
    },
    enabled: computed(() => !!id.value),
  })
}

export function useCreateContact() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (dto: CreateContactDto): Promise<ContactEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: ContactApiDto }>('/contacts', {
        method: 'POST',
        body: dto,
      })
      return ContactMapper.toEntity(response.data)
    },

    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contacts'] })
    },
  })
}

export function useUpdateContact() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      id,
      updates,
    }: {
      id: string
      updates: Partial<Pick<ContactEntity, 'firstName' | 'lastName' | 'title' | 'lifecycleStage' | 'avatarUrl' | 'website' | 'linkedinUrl' | 'twitterUrl'>>
    }): Promise<ContactEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: ContactApiDto }>(`/contacts/${id}`, {
        method: 'PUT',
        body: updates,
      })
      return ContactMapper.toEntity(response.data)
    },

    onMutate: async ({ id, updates }) => {
      await queryClient.cancelQueries({ queryKey: ['contact', id] })

      const previousContact = queryClient.getQueryData<ContactEntity>(['contact', id])

      if (previousContact) {
        queryClient.setQueryData<ContactEntity>(['contact', id], {
          ...previousContact,
          ...updates,
        } as ContactEntity)
      }

      return { previousContact }
    },

    onError: (_err, { id }, context) => {
      if (context?.previousContact) {
        queryClient.setQueryData(['contact', id], context.previousContact)
      }
    },

    onSettled: (_data, _err, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['contact', id] })
      queryClient.invalidateQueries({ queryKey: ['contacts'] })
    },
  })
}

export function useDeleteContact() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/contacts/${id}`, {
        method: 'DELETE',
      })
    },

    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contacts'] })
    },
  })
}
