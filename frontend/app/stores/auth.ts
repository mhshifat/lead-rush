import { defineStore } from 'pinia'
import type { UserEntity } from '~/entities/user/user.entity'
import type { WorkspaceEntity } from '~/entities/workspace/workspace.entity'
import { UserMapper } from '~/entities/user/user.mapper'
import { WorkspaceMapper } from '~/entities/workspace/workspace.mapper'
import { ErrorMapper } from '~/entities/error/error.mapper'
import type { AuthResponseDto, ApiSuccessDto, ApiMessageDto } from '~/types/api/auth.dto'
import type { ErrorApiDto } from '~/types/api/error.dto'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserEntity | null>(null)
  const workspaces = ref<WorkspaceEntity[]>([])

  const config = useRuntimeConfig()
  const accessToken = useCookie('accessToken', { maxAge: config.public.accessTokenTtl as number })
  const refreshToken = useCookie('refreshToken', { maxAge: config.public.refreshTokenTtl as number })

  const isLoggedIn = computed(() => !!accessToken.value)
  const hasPassword = computed(() => user.value?.hasPassword ?? false)

  const currentWorkspaceId = computed<string | null>(() => {
    if (!accessToken.value) return null
    try {
      const payload = JSON.parse(atob(accessToken.value.split('.')[1] ?? ''))
      return payload.workspaceId ?? null
    } catch {
      return null
    }
  })

  const currentWorkspace = computed(() => {
    return workspaces.value.find(w => w.id === currentWorkspaceId.value)
        ?? workspaces.value[0]
        ?? null
  })

  async function register(name: string, email: string, password: string): Promise<string> {
    const { $api } = useNuxtApp()

    const response = await $api<ApiMessageDto>('/auth/register', {
      method: 'POST',
      body: { name, email, password },
    })

    return response.message
  }

  async function login(email: string, password: string) {
    const { $api } = useNuxtApp()

    try {
      const response = await $api<ApiSuccessDto<AuthResponseDto>>('/auth/login', {
        method: 'POST',
        body: { email, password },
      })

      setAuthData(response.data)
      saveLastUsedAuth(email, 'LOCAL')

      return { success: true as const }
    } catch (error: any) {
      const errorData = error.data as ErrorApiDto | undefined
      if (errorData?.error?.category === 'ACTIVATION_REQUIRED') {
        return {
          success: false as const,
          activationRequired: true,
          lastActivationEmailSentAt: errorData.error.lastActivationEmailSentAt!,
        }
      }
      throw error
    }
  }

  async function resendActivation(email: string): Promise<string> {
    const { $api } = useNuxtApp()

    const response = await $api<ApiMessageDto>('/auth/resend-activation', {
      method: 'POST',
      body: { email },
    })

    return response.message
  }

  async function refresh() {
    const { $api } = useNuxtApp()

    if (!refreshToken.value) {
      logout()
      return
    }

    try {
      const response = await $api<ApiSuccessDto<AuthResponseDto>>('/auth/refresh', {
        method: 'POST',
        body: { refreshToken: refreshToken.value },
      })

      setAuthData(response.data)
    } catch {
      logout()
    }
  }

  // Backend re-issues the access token with the new workspaceId + role.
  // Do NOT rotate the refresh token — keep the existing one.
  async function switchWorkspace(workspaceId: string) {
    const { $api } = useNuxtApp()
    const response = await $api<ApiSuccessDto<AuthResponseDto>>('/auth/switch-workspace', {
      method: 'POST',
      body: { workspaceId },
    })

    accessToken.value = response.data.accessToken
    user.value = UserMapper.toEntity(response.data.user)
    workspaces.value = WorkspaceMapper.toEntityList(response.data.workspaces)
  }

  function logout() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    workspaces.value = []
    navigateTo('/auth/login')
  }

  function setAuthData(data: AuthResponseDto) {
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = UserMapper.toEntity(data.user)
    workspaces.value = WorkspaceMapper.toEntityList(data.workspaces)
  }

  // Rehydrate user + workspaces without touching tokens — used by auth-init on page reload.
  function hydrate(data: AuthResponseDto) {
    user.value = UserMapper.toEntity(data.user)
    workspaces.value = WorkspaceMapper.toEntityList(data.workspaces)
  }

  /**
   * Entry point for the /auth/callback page after an OAuth round-trip.
   * Tokens arrive in the URL query string (see OAuth2LoginSuccessHandler);
   * we persist them to cookies, fetch the user/workspaces via /auth/me, and
   * remember the provider for the "Last used" badge on subsequent visits.
   */
  async function consumeOAuthCallback(params: {
    accessToken: string
    refreshToken: string
    provider: string
    email: string
  }) {
    accessToken.value = params.accessToken
    refreshToken.value = params.refreshToken

    const { $api } = useNuxtApp()
    const response = await $api<ApiSuccessDto<AuthResponseDto>>('/auth/me')
    // /auth/me returns user + workspaces but no tokens; we already have those.
    user.value = UserMapper.toEntity(response.data.user)
    workspaces.value = WorkspaceMapper.toEntityList(response.data.workspaces)

    saveLastUsedAuth(params.email, params.provider)
  }

  function saveLastUsedAuth(email: string, provider: string) {
    if (import.meta.client) {
      localStorage.setItem('lastUsedAuth', JSON.stringify({
        email,
        provider,
        timestamp: new Date().toISOString(),
      }))
    }
  }

  function getLastUsedAuth(): { email: string; provider: string } | null {
    if (import.meta.client) {
      const stored = localStorage.getItem('lastUsedAuth')
      return stored ? JSON.parse(stored) : null
    }
    return null
  }

  return {
    user,
    workspaces,
    accessToken,
    refreshToken,
    isLoggedIn,
    hasPassword,
    currentWorkspace,
    register,
    login,
    resendActivation,
    refresh,
    logout,
    switchWorkspace,
    hydrate,
    consumeOAuthCallback,
    getLastUsedAuth,
  }
})
