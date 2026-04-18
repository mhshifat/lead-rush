export interface AIStatusDto {
  ready: boolean
  provider: string
}

export interface GenerateEmailDto {
  contactId: string
  valueProp?: string
  tone?: string
  length?: 'SHORT' | 'MEDIUM' | 'LONG'
}

export interface GeneratedEmailApiDto {
  subject: string | null
  bodyHtml: string | null
  bodyText: string | null
}

export interface SubjectSuggestionsDto {
  subject: string
  bodyPreview?: string
  count?: number
}

export interface SubjectSuggestionsResponseDto {
  suggestions: string[]
}
