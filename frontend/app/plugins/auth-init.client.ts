// Client-side boot: probe an authenticated endpoint so a stale cookie triggers
// the api.ts 401 handler (logout + redirect) before pages start rendering.
import type { WorkspaceApiDto } from '~/types/api/workspace.dto'

export default defineNuxtPlugin(async (nuxtApp) => {
  const token = useCookie('accessToken').value
  if (!token) return

  const auth = useAuthStore()
  if (auth.user) return

  try {
    const { $api } = nuxtApp as unknown as { $api: typeof $fetch }
    await $api<{ data: WorkspaceApiDto[] }>('/workspaces/mine')
  } catch {
    // 401 handled in api.ts' onResponseError.
  }
})
