export type TriggerType =
  | 'CONTACT_CREATED'
  | 'CONTACT_UPDATED'
  | 'EMAIL_OPENED'
  | 'EMAIL_CLICKED'
  | 'EMAIL_REPLIED'
  | 'FORM_SUBMITTED'
  | 'ENROLLED'

export type ConditionOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'CONTAINS'
  | 'STARTS_WITH'
  | 'ENDS_WITH'
  | 'GREATER_THAN'
  | 'LESS_THAN'

export interface LeadScoreRuleEntity {
  id: string
  name: string
  description: string | null
  triggerType: TriggerType
  conditionField: string | null
  conditionOperator: ConditionOperator | null
  conditionValue: string | null
  points: number
  enabled: boolean
  createdAt: Date
  updatedAt: Date
}

export interface LeadScoreLogEntity {
  id: string
  contactId: string
  ruleId: string | null
  ruleName: string | null
  pointsDelta: number
  scoreBefore: number
  scoreAfter: number
  triggerType: TriggerType | null
  reason: string | null
  createdAt: Date
}

/** UI-friendly options for the rule form — labels + descriptions shown to the user. */
export const TRIGGER_OPTIONS: Array<{ value: TriggerType; label: string; description: string }> = [
  { value: 'CONTACT_CREATED', label: 'Contact created', description: 'A new contact is added' },
  { value: 'CONTACT_UPDATED', label: 'Lifecycle stage changed', description: 'Contact moves between stages' },
  { value: 'EMAIL_OPENED', label: 'Email opened', description: 'Contact opens a sequence email' },
  { value: 'EMAIL_CLICKED', label: 'Email clicked', description: 'Contact clicks a link in an email' },
  { value: 'FORM_SUBMITTED', label: 'Form submitted', description: 'Contact submits a form' },
  { value: 'ENROLLED', label: 'Enrolled in sequence', description: 'Contact is added to a sequence' },
]

export const CONDITION_OPERATOR_OPTIONS: Array<{ value: ConditionOperator; label: string }> = [
  { value: 'EQUALS', label: 'equals' },
  { value: 'NOT_EQUALS', label: 'does not equal' },
  { value: 'CONTAINS', label: 'contains' },
  { value: 'STARTS_WITH', label: 'starts with' },
  { value: 'ENDS_WITH', label: 'ends with' },
  { value: 'GREATER_THAN', label: 'greater than' },
  { value: 'LESS_THAN', label: 'less than' },
]

/** Fields on a Contact that can be used in rule conditions. */
export const CONDITION_FIELD_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'title', label: 'Title' },
  { value: 'firstName', label: 'First name' },
  { value: 'lastName', label: 'Last name' },
  { value: 'email', label: 'Email' },
  { value: 'lifecycleStage', label: 'Lifecycle stage' },
  { value: 'source', label: 'Source' },
  { value: 'leadScore', label: 'Lead score' },
  { value: 'website', label: 'Website' },
  { value: 'linkedinUrl', label: 'LinkedIn URL' },
  { value: 'companyName', label: 'Company name' },
  { value: 'companyDomain', label: 'Company domain' },
  { value: 'companyIndustry', label: 'Company industry' },
]
