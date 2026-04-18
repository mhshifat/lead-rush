import type { NotificationApiDto } from '~/types/api/notification.dto'
import type { NotificationEntity, NotificationType } from './notification.entity'

export const NotificationMapper = {
  toEntity(dto: NotificationApiDto): NotificationEntity {
    return {
      id: dto.id,
      type: dto.type as NotificationType,
      title: dto.title,
      body: dto.body,
      linkPath: dto.linkPath,
      metadata: dto.metadata ?? null,
      readAt: dto.readAt ? new Date(dto.readAt) : null,
      read: dto.read,
      createdAt: new Date(dto.createdAt),
    }
  },

  toEntityList(dtos: NotificationApiDto[]): NotificationEntity[] {
    return dtos.map(NotificationMapper.toEntity)
  },
}
