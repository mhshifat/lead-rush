export interface LeadScoreRuleApiDto {
  id: string
  name: string
  description: string | null
  triggerType: string
  conditionField: string | null
  conditionOperator: string | null
  conditionValue: string | null
  points: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateLeadScoreRuleDto {
  name: string
  description?: string
  triggerType: string
  conditionField?: string
  conditionOperator?: string
  conditionValue?: string
  points: number
  enabled?: boolean
}

export interface LeadScoreLogApiDto {
  id: string
  contactId: string
  ruleId: string | null
  ruleName: string | null
  pointsDelta: number
  scoreBefore: number
  scoreAfter: number
  triggerType: string | null
  reason: string | null
  createdAt: string
}

export interface AdjustScoreDto {
  pointsDelta: number
  reason?: string
}
