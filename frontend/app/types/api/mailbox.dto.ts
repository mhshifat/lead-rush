export interface MailboxApiDto {
  id: string
  name: string
  email: string
  provider: string
  smtpHost: string | null
  smtpPort: number | null
  smtpUsername: string | null
  dailyLimit: number
  sendsToday: number
  status: string
  lastError: string | null
  lastTestedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateMailboxDto {
  name: string
  email: string
  provider: 'SMTP' | 'GMAIL' | 'OUTLOOK'
  smtpHost: string
  smtpPort: number
  smtpUsername: string
  smtpPassword: string
  dailyLimit?: number
}
