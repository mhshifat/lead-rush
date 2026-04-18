import type { CompanyApiDto, CreateCompanyDto } from '~/types/api/company.dto'
import type { CompanyEntity } from './company.entity'

export const CompanyMapper = {
  toEntity(dto: CompanyApiDto): CompanyEntity {
    return {
      id: dto.id,
      name: dto.name,
      domain: dto.domain,
      industry: dto.industry,
      companySize: dto.companySize,
      annualRevenue: dto.annualRevenue,
      description: dto.description,
      website: dto.website,
      logoUrl: dto.logoUrl,
      phone: dto.phone,
      address: dto.address,
      city: dto.city,
      state: dto.state,
      country: dto.country,
      zipCode: dto.zipCode,
      contactCount: dto.contactCount ?? 0,
      createdAt: new Date(dto.createdAt),
      updatedAt: new Date(dto.updatedAt),
    }
  },

  toEntityList(dtos: CompanyApiDto[]): CompanyEntity[] {
    return dtos.map(CompanyMapper.toEntity)
  },

  toCreateDto(entity: Partial<CompanyEntity> & { name: string }): CreateCompanyDto {
    return {
      name: entity.name,
      domain: entity.domain ?? undefined,
      industry: entity.industry ?? undefined,
      companySize: entity.companySize ?? undefined,
      annualRevenue: entity.annualRevenue ?? undefined,
      description: entity.description ?? undefined,
      website: entity.website ?? undefined,
      logoUrl: entity.logoUrl ?? undefined,
      phone: entity.phone ?? undefined,
      address: entity.address ?? undefined,
      city: entity.city ?? undefined,
      state: entity.state ?? undefined,
      country: entity.country ?? undefined,
      zipCode: entity.zipCode ?? undefined,
    }
  },
}
