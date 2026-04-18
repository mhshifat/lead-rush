import type { AuthResponseDto } from '~/types/api/auth.dto'
import type { WorkspaceEntity } from './workspace.entity'

export const WorkspaceMapper = {
  toEntity(dto: AuthResponseDto['workspaces'][number]): WorkspaceEntity {
    return {
      id: dto.id,
      name: dto.name,
      slug: dto.slug,
      logoUrl: dto.logoUrl,
      role: dto.role as WorkspaceEntity['role'],
    }
  },

  toEntityList(dtos: AuthResponseDto['workspaces']): WorkspaceEntity[] {
    return dtos.map(WorkspaceMapper.toEntity)
  },
}
