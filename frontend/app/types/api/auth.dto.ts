// Auth endpoint response; switch-workspace sets refreshToken=null (access-only re-issue).
export interface AuthResponseDto {
  accessToken: string
  refreshToken: string | null
  user: {
    id: string
    email: string
    name: string
    avatarUrl: string | null
    hasPassword: boolean
    primaryProvider: string
    lastUsedProvider: string | null
  }
  workspaces: Array<{
    id: string
    name: string
    slug: string
    logoUrl: string | null
    role: string
  }>
}

export interface ApiSuccessDto<T> {
  success: true
  data: T
  message?: string
}

export interface ApiMessageDto {
  success: true
  message: string
}
