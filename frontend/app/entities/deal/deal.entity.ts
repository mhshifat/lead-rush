export interface DealContactSummary {
  id: string
  fullName: string
  primaryEmail: string | null
}

export interface DealEntity {
  id: string
  name: string
  description: string | null
  pipelineId: string
  pipelineStageId: string
  stageName: string
  valueAmount: number | null
  valueCurrency: string | null
  ownerUserId: string | null
  expectedCloseAt: Date | null
  closedAt: Date | null
  contacts: DealContactSummary[]
  createdAt: Date
  updatedAt: Date
}
