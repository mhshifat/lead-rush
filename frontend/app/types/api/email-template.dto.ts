export interface EmailTemplateApiDto {
  id: string
  name: string
  subject: string
  bodyHtml: string | null
  bodyText: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateEmailTemplateDto {
  name: string
  subject: string
  bodyHtml?: string
  bodyText?: string
}
