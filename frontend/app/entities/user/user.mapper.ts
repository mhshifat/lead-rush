import type { AuthResponseDto } from '~/types/api/auth.dto'
import type { UserEntity } from './user.entity'

export const UserMapper = {
  toEntity(dto: AuthResponseDto['user']): UserEntity {
    return {
      id: dto.id,
      email: dto.email,
      name: dto.name,
      avatarUrl: dto.avatarUrl,
      hasPassword: dto.hasPassword,
      primaryProvider: dto.primaryProvider as UserEntity['primaryProvider'],
      lastUsedProvider: dto.lastUsedProvider as UserEntity['lastUsedProvider'],
    }
  },
}
