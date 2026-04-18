export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'MEMBER' | 'VIEWER'

export const ROLE_OPTIONS: Array<{ value: WorkspaceRole; label: string; description: string }> = [
  { value: 'OWNER',   label: 'Owner',   description: 'Full control, including billing' },
  { value: 'ADMIN',   label: 'Admin',   description: 'Manage members + all data' },
  { value: 'MANAGER', label: 'Manager', description: 'Manage team work + view reports' },
  { value: 'MEMBER',  label: 'Member',  description: 'Standard access to contacts, deals, sequences' },
  { value: 'VIEWER',  label: 'Viewer',  description: 'Read-only access' },
]

export interface MemberEntity {
  membershipId: string
  userId: string
  name: string
  email: string
  avatarUrl: string | null
  role: WorkspaceRole
  joinedAt: Date
}

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED'

export interface InvitationEntity {
  id: string
  email: string
  role: WorkspaceRole
  status: InvitationStatus
  invitedByName: string | null
  workspaceName: string | null
  expiresAt: Date
  acceptedAt: Date | null
  createdAt: Date
}

export interface FullWorkspaceEntity {
  id: string
  name: string
  slug: string
  logoUrl: string | null
  role: WorkspaceRole
  memberCount: number
  createdAt: Date
}
