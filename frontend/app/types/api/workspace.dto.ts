/** Full workspace — returned by /workspaces/mine, /workspaces/current, etc. */
export interface WorkspaceApiDto {
  id: string
  name: string
  slug: string
  logoUrl: string | null
  role: string
  memberCount: number
  createdAt: string
}

export interface MemberApiDto {
  membershipId: string
  userId: string
  name: string
  email: string
  avatarUrl: string | null
  role: string
  joinedAt: string
}

export interface InvitationApiDto {
  id: string
  email: string
  role: string
  status: string          // PENDING, ACCEPTED, REVOKED, EXPIRED
  invitedByName: string | null
  workspaceName: string | null
  expiresAt: string
  acceptedAt: string | null
  createdAt: string
}

export interface InviteDto {
  email: string
  role?: string
}

export interface UpdateWorkspaceDto {
  name?: string
  logoUrl?: string
}

export interface CreateWorkspaceDto {
  name: string
}

export interface UpdateMemberRoleDto {
  role: string
}
