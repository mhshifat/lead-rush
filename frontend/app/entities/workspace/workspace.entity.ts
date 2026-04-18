export interface WorkspaceEntity {
  id: string
  name: string
  slug: string
  logoUrl: string | null
  role: 'OWNER' | 'ADMIN' | 'MANAGER' | 'MEMBER' | 'VIEWER'
}
