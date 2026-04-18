export type MailboxProvider = 'SMTP' | 'GMAIL' | 'OUTLOOK'
export type MailboxStatus = 'ACTIVE' | 'PAUSED' | 'ERROR'

export interface MailboxEntity {
  id: string
  name: string
  email: string
  provider: MailboxProvider
  smtpHost: string | null
  smtpPort: number | null
  smtpUsername: string | null
  dailyLimit: number
  sendsToday: number
  status: MailboxStatus
  lastError: string | null
  lastTestedAt: Date | null
  createdAt: Date
  updatedAt: Date
}
