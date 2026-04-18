import type { MailboxApiDto } from '~/types/api/mailbox.dto'
import type { MailboxEntity, MailboxProvider, MailboxStatus } from './mailbox.entity'

export const MailboxMapper = {
  toEntity(dto: MailboxApiDto): MailboxEntity {
    return {
      id: dto.id,
      name: dto.name,
      email: dto.email,
      provider: dto.provider as MailboxProvider,
      smtpHost: dto.smtpHost,
      smtpPort: dto.smtpPort,
      smtpUsername: dto.smtpUsername,
      dailyLimit: dto.dailyLimit,
      sendsToday: dto.sendsToday,
      status: dto.status as MailboxStatus,
      lastError: dto.lastError,
      lastTestedAt: dto.lastTestedAt ? new Date(dto.lastTestedAt) : null,
      createdAt: new Date(dto.createdAt),
      updatedAt: new Date(dto.updatedAt),
    }
  },

  toEntityList(dtos: MailboxApiDto[]): MailboxEntity[] {
    return dtos.map(MailboxMapper.toEntity)
  },
}
