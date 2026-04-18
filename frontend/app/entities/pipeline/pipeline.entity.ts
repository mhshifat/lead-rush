export type StageType = 'OPEN' | 'WON' | 'LOST'

export interface StageEntity {
  id: string
  name: string
  color: string | null
  winProbability: number
  displayOrder: number
  stageType: StageType
}

export interface PipelineEntity {
  id: string
  name: string
  description: string | null
  isDefault: boolean
  displayOrder: number
  stages: StageEntity[]
  createdAt: Date
  updatedAt: Date
}
