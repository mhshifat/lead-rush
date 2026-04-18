import type { EmailTemplateApiDto } from '~/types/api/email-template.dto'
import type { EmailTemplateEntity } from './email-template.entity'

export const EmailTemplateMapper = {
  toEntity(dto: EmailTemplateApiDto): EmailTemplateEntity {
    return {
      id: dto.id,
      name: dto.name,
      subject: dto.subject,
      bodyHtml: dto.bodyHtml,
      bodyText: dto.bodyText,
      createdAt: new Date(dto.createdAt),
      updatedAt: new Date(dto.updatedAt),
    }
  },

  toEntityList(dtos: EmailTemplateApiDto[]): EmailTemplateEntity[] {
    return dtos.map(EmailTemplateMapper.toEntity)
  },
}
