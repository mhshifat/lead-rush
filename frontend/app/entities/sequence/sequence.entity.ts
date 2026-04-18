export type SequenceStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED'
export type StepType = 'EMAIL' | 'DELAY' | 'CALL' | 'TASK' | 'LINKEDIN_MESSAGE' | 'LINKEDIN_CONNECT'
export type EnrollmentStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'REPLIED' | 'BOUNCED' | 'UNSUBSCRIBED' | 'FAILED'

export interface SequenceStepEntity {
  id: string
  stepOrder: number
  stepType: StepType
  delayDays: number
  emailTemplateId: string | null
  emailTemplateName: string | null
  subjectOverride: string | null
  bodyHtmlOverride: string | null
  taskDescription: string | null
  skipIfPreviousOpened: boolean
  skipIfPreviousClicked: boolean
}

export interface SequenceEntity {
  id: string
  name: string
  description: string | null
  status: SequenceStatus
  defaultMailboxId: string | null
  defaultMailboxEmail: string | null
  totalEnrolled: number
  totalCompleted: number
  totalReplied: number
  steps: SequenceStepEntity[]
  createdAt: Date
  updatedAt: Date
}

export interface EnrollmentEntity {
  id: string
  sequenceId: string
  sequenceName: string
  contactId: string
  contactFullName: string
  contactEmail: string | null
  mailboxId: string | null
  mailboxEmail: string | null
  currentStepIndex: number
  nextExecutionAt: Date | null
  status: EnrollmentStatus
  enrolledAt: Date
  completedAt: Date | null
}
