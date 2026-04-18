import type { PipelineApiDto, StageApiDto } from '~/types/api/pipeline.dto'
import type { PipelineEntity, StageEntity, StageType } from './pipeline.entity'

export const PipelineMapper = {
  toEntity(dto: PipelineApiDto): PipelineEntity {
    return {
      id: dto.id,
      name: dto.name,
      description: dto.description,
      isDefault: dto.isDefault,
      displayOrder: dto.displayOrder,
      stages: dto.stages.map(PipelineMapper.toStageEntity),
      createdAt: new Date(dto.createdAt),
      updatedAt: new Date(dto.updatedAt),
    }
  },

  toEntityList(dtos: PipelineApiDto[]): PipelineEntity[] {
    return dtos.map(PipelineMapper.toEntity)
  },

  toStageEntity(dto: StageApiDto): StageEntity {
    return {
      id: dto.id,
      name: dto.name,
      color: dto.color,
      winProbability: dto.winProbability,
      displayOrder: dto.displayOrder,
      stageType: dto.stageType as StageType,
    }
  },
}
