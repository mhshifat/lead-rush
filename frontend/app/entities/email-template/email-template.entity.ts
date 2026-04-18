export interface EmailTemplateEntity {
  id: string
  name: string
  subject: string
  bodyHtml: string | null
  bodyText: string | null
  createdAt: Date
  updatedAt: Date
}
