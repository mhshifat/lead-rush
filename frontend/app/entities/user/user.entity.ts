/**
 * User entity — what the frontend uses everywhere.
 * Components never see the raw API shape, only this.
 */
export interface UserEntity {
  id: string
  email: string
  name: string
  avatarUrl: string | null
  hasPassword: boolean
  primaryProvider: 'LOCAL' | 'GOOGLE' | 'GITHUB'
  lastUsedProvider: 'LOCAL' | 'GOOGLE' | 'GITHUB' | null
}
