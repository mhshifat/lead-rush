/**
 * Contact entity — what the frontend uses everywhere.
 * Components never see the raw API DTO, only this.
 *
 * KEY DIFFERENCES from ContactApiDto:
 *   - Dates are Date objects (not strings)
 *   - Enums are typed unions (not arbitrary strings)
 *   - Derived fields are computed in the mapper (fullName, primaryEmail)
 */
export type LifecycleStage = 'LEAD' | 'CONTACTED' | 'QUALIFIED' | 'OPPORTUNITY' | 'CUSTOMER' | 'LOST'

export type ContactSource = 'MANUAL' | 'CSV_IMPORT' | 'FORM' | 'LINKEDIN' | 'API' | 'ENRICHMENT'

export interface ContactEmailEntity {
  id: string
  email: string
  emailType: 'WORK' | 'PERSONAL' | 'OTHER'
  isPrimary: boolean
  verificationStatus: 'UNKNOWN' | 'VALID' | 'INVALID' | 'CATCH_ALL'
}

export interface ContactPhoneEntity {
  id: string
  phone: string
  phoneType: 'WORK' | 'MOBILE' | 'PERSONAL' | 'OTHER'
  isPrimary: boolean
}

export interface ContactTagEntity {
  id: string
  name: string
  color: string | null
}

export interface ContactEntity {
  id: string
  firstName: string
  lastName: string | null
  fullName: string
  title: string | null

  companyId: string | null
  companyName: string | null

  lifecycleStage: LifecycleStage
  leadScore: number
  source: ContactSource | null

  avatarUrl: string | null
  website: string | null
  linkedinUrl: string | null
  twitterUrl: string | null

  primaryEmail: string | null
  primaryPhone: string | null

  emails: ContactEmailEntity[]
  phones: ContactPhoneEntity[]
  tags: ContactTagEntity[]

  lastContactedAt: Date | null
  createdAt: Date
  updatedAt: Date
}
