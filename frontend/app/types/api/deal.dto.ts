export interface DealApiDto {
  id: string
  name: string
  description: string | null
  pipelineId: string
  pipelineStageId: string
  stageName: string
  valueAmount: number | null
  valueCurrency: string | null
  ownerUserId: string | null
  expectedCloseAt: string | null
  closedAt: string | null
  contacts: Array<{
    id: string
    fullName: string
    primaryEmail: string | null
  }>
  createdAt: string
  updatedAt: string
}

export interface CreateDealDto {
  name: string
  pipelineId: string
  pipelineStageId?: string
  description?: string
  valueAmount?: number
  valueCurrency?: string
  expectedCloseAt?: string
  contactIds?: string[]
}

export interface UpdateDealDto {
  name?: string
  description?: string
  valueAmount?: number
  valueCurrency?: string
  expectedCloseAt?: string
  pipelineStageId?: string
}
