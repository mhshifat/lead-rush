// Hydrate the auth store on client boot — Pinia is in-memory, so state is lost
// on page refresh even though the JWT cookie survives. A 401 from /auth/me also
// triggers api.ts's logout handler, which clears the stale cookie and bounces
// to /auth/login.
import type { AuthResponseDto, ApiSuccessDto } from '~/types/api/auth.dto'

export default defineNuxtPlugin(async (nuxtApp) => {
  const token = useCookie('accessToken').value
  if (!token) return

  const auth = useAuthStore()
  if (auth.user) return

  try {
    const { $api } = nuxtApp as unknown as { $api: typeof $fetch }
    const res = await $api<ApiSuccessDto<AuthResponseDto>>('/auth/me')
    auth.hydrate(res.data)
  } catch {
    // 401 handled in api.ts' onResponseError.
  }
})
