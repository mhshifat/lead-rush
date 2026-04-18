export interface SequenceApiDto {
  id: string
  name: string
  description: string | null
  status: string

  defaultMailboxId: string | null
  defaultMailboxEmail: string | null

  totalEnrolled: number
  totalCompleted: number
  totalReplied: number

  steps: StepApiDto[]

  createdAt: string
  updatedAt: string
}

export interface StepApiDto {
  id: string
  stepOrder: number
  stepType: string
  delayDays: number
  emailTemplateId: string | null
  emailTemplateName: string | null
  subjectOverride: string | null
  bodyHtmlOverride: string | null
  taskDescription: string | null
  skipIfPreviousOpened: boolean
  skipIfPreviousClicked: boolean
}

export interface CreateSequenceDto {
  name: string
  description?: string
  defaultMailboxId?: string
}

export interface CreateStepDto {
  stepType: 'EMAIL' | 'DELAY' | 'CALL' | 'TASK' | 'LINKEDIN_MESSAGE' | 'LINKEDIN_CONNECT'
  delayDays?: number
  emailTemplateId?: string
  subjectOverride?: string
  bodyHtmlOverride?: string
  taskDescription?: string
  skipIfPreviousOpened?: boolean
  skipIfPreviousClicked?: boolean
}

export interface EnrollRequestDto {
  contactId: string
  mailboxId?: string
}

export interface EnrollmentApiDto {
  id: string
  sequenceId: string
  sequenceName: string
  contactId: string
  contactFullName: string
  contactEmail: string | null
  mailboxId: string | null
  mailboxEmail: string | null
  currentStepIndex: number
  nextExecutionAt: string | null
  status: string
  enrolledAt: string
  completedAt: string | null
}
