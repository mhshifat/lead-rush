import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { CompanyMapper } from '~/entities/company/company.mapper'
import type { CompanyEntity } from '~/entities/company/company.entity'
import type { CompanyApiDto, CreateCompanyDto } from '~/types/api/company.dto'
import type { PageDto } from '~/types/api/contact.dto'

export interface CompanyFilters {
  search?: string
  page?: number
  size?: number
  sort?: string
}

export interface PaginatedCompanies {
  items: CompanyEntity[]
  totalElements: number
  totalPages: number
  currentPage: number
  pageSize: number
}

export function useCompanies(filters: Ref<CompanyFilters>) {
  return useQuery({
    queryKey: ['companies', filters] as const,
    queryFn: async (): Promise<PaginatedCompanies> => {
      const { $api } = useNuxtApp()
      const params: Record<string, any> = {}
      if (filters.value.search) params.search = filters.value.search
      if (filters.value.page !== undefined) params.page = filters.value.page
      if (filters.value.size !== undefined) params.size = filters.value.size
      if (filters.value.sort) params.sort = filters.value.sort

      const response = await $api<{ data: PageDto<CompanyApiDto> }>('/companies', { params })

      return {
        items: CompanyMapper.toEntityList(response.data.content),
        totalElements: response.data.totalElements,
        totalPages: response.data.totalPages,
        currentPage: response.data.number,
        pageSize: response.data.size,
      }
    },
  })
}

export function useCompany(id: Ref<string | null>) {
  return useQuery({
    queryKey: ['company', id] as const,
    queryFn: async (): Promise<CompanyEntity | null> => {
      if (!id.value) return null
      const { $api } = useNuxtApp()
      const response = await $api<{ data: CompanyApiDto }>(`/companies/${id.value}`)
      return CompanyMapper.toEntity(response.data)
    },
    enabled: computed(() => !!id.value),
  })
}

export function useCreateCompany() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (dto: CreateCompanyDto): Promise<CompanyEntity> => {
      const { $api } = useNuxtApp()
      const response = await $api<{ data: CompanyApiDto }>('/companies', {
        method: 'POST',
        body: dto,
      })
      return CompanyMapper.toEntity(response.data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['companies'] })
    },
  })
}
