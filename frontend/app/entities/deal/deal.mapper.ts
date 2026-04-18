import type { DealApiDto } from '~/types/api/deal.dto'
import type { DealEntity } from './deal.entity'

export const DealMapper = {
  toEntity(dto: DealApiDto): DealEntity {
    return {
      id: dto.id,
      name: dto.name,
      description: dto.description,
      pipelineId: dto.pipelineId,
      pipelineStageId: dto.pipelineStageId,
      stageName: dto.stageName,
      valueAmount: dto.valueAmount,
      valueCurrency: dto.valueCurrency,
      ownerUserId: dto.ownerUserId,
      expectedCloseAt: dto.expectedCloseAt ? new Date(dto.expectedCloseAt) : null,
      closedAt: dto.closedAt ? new Date(dto.closedAt) : null,
      contacts: dto.contacts.map(c => ({
        id: c.id,
        fullName: c.fullName,
        primaryEmail: c.primaryEmail,
      })),
      createdAt: new Date(dto.createdAt),
      updatedAt: new Date(dto.updatedAt),
    }
  },

  toEntityList(dtos: DealApiDto[]): DealEntity[] {
    return dtos.map(DealMapper.toEntity)
  },
}
