/**
 * Contact API DTOs — mirror backend JSON exactly.
 *
 * Remember: backend sends camelCase (Jackson converts Java camelCase → JSON camelCase).
 * So our DTOs use camelCase directly matching the backend response.
 */

export interface ContactApiDto {
  id: string
  firstName: string
  lastName: string | null
  fullName: string
  title: string | null

  companyId: string | null
  companyName: string | null

  lifecycleStage: string | null
  leadScore: number
  source: string | null

  avatarUrl: string | null
  website: string | null
  linkedinUrl: string | null
  twitterUrl: string | null

  primaryEmail: string | null
  primaryPhone: string | null

  emails: Array<{
    id: string
    email: string
    emailType: string
    primary: boolean
    verificationStatus: string
    source: string | null
  }>

  phones: Array<{
    id: string
    phone: string
    phoneType: string
    primary: boolean
  }>

  tags: Array<{
    id: string
    name: string
    color: string | null
  }>

  lastContactedAt: string | null
  createdAt: string
  updatedAt: string
}

/** Create contact request — what we send to the backend. */
export interface CreateContactDto {
  firstName: string
  lastName?: string
  title?: string
  companyName?: string
  lifecycleStage?: string
  source?: string
  avatarUrl?: string
  website?: string
  linkedinUrl?: string
  twitterUrl?: string
  emails?: Array<{ email: string; emailType?: string; primary?: boolean }>
  phones?: Array<{ phone: string; phoneType?: string; primary?: boolean }>
  tags?: string[]
}

/** Paginated response wrapper from Spring Data. */
export interface PageDto<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number        // current page (0-indexed)
  size: number          // page size
}
