import type {
  WorkspaceApiDto,
  MemberApiDto,
  InvitationApiDto,
} from '~/types/api/workspace.dto'
import type {
  FullWorkspaceEntity,
  MemberEntity,
  InvitationEntity,
  WorkspaceRole,
  InvitationStatus,
} from './member.entity'

export const WorkspaceAdminMapper = {
  toFullEntity(dto: WorkspaceApiDto): FullWorkspaceEntity {
    return {
      id: dto.id,
      name: dto.name,
      slug: dto.slug,
      logoUrl: dto.logoUrl,
      role: dto.role as WorkspaceRole,
      memberCount: dto.memberCount,
      createdAt: new Date(dto.createdAt),
    }
  },

  toFullEntityList(dtos: WorkspaceApiDto[]): FullWorkspaceEntity[] {
    return dtos.map(WorkspaceAdminMapper.toFullEntity)
  },

  toMember(dto: MemberApiDto): MemberEntity {
    return {
      membershipId: dto.membershipId,
      userId: dto.userId,
      name: dto.name,
      email: dto.email,
      avatarUrl: dto.avatarUrl,
      role: dto.role as WorkspaceRole,
      joinedAt: new Date(dto.joinedAt),
    }
  },

  toMemberList(dtos: MemberApiDto[]): MemberEntity[] {
    return dtos.map(WorkspaceAdminMapper.toMember)
  },

  toInvitation(dto: InvitationApiDto): InvitationEntity {
    return {
      id: dto.id,
      email: dto.email,
      role: dto.role as WorkspaceRole,
      status: dto.status as InvitationStatus,
      invitedByName: dto.invitedByName,
      workspaceName: dto.workspaceName,
      expiresAt: new Date(dto.expiresAt),
      acceptedAt: dto.acceptedAt ? new Date(dto.acceptedAt) : null,
      createdAt: new Date(dto.createdAt),
    }
  },

  toInvitationList(dtos: InvitationApiDto[]): InvitationEntity[] {
    return dtos.map(WorkspaceAdminMapper.toInvitation)
  },
}
