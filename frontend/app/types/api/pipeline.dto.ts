export interface StageApiDto {
  id: string
  name: string
  color: string | null
  winProbability: number
  displayOrder: number
  stageType: string
}

export interface PipelineApiDto {
  id: string
  name: string
  description: string | null
  isDefault: boolean
  displayOrder: number
  stages: StageApiDto[]
  createdAt: string
  updatedAt: string
}

export interface CreatePipelineDto {
  name: string
  description?: string
  isDefault?: boolean
}

export interface CreateStageDto {
  name: string
  color?: string
  winProbability?: number
  stageType?: 'OPEN' | 'WON' | 'LOST'
}
