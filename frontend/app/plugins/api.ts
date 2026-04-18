import { ofetch } from 'ofetch'
import { ErrorMapper } from '~/entities/error/error.mapper'
import type { ErrorApiDto } from '~/types/api/error.dto'

// Provides $api: base URL, JWT attach from cookie, global error mapping.
// Token lives in a cookie (not localStorage) so SSR can read it during the first paint.
export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig()

  // On the server, prefer the internal URL (e.g. http://backend:8080 in Docker);
  // in the browser always use the public URL so requests hit the host-exposed port.
  const baseURL = import.meta.server && config.apiBaseUrlServer
    ? (config.apiBaseUrlServer as string)
    : (config.public.apiBaseUrl as string)

  const api = ofetch.create({
    baseURL,

    onRequest({ options }) {
      const token = useCookie('accessToken').value
      if (token) {
        const headers = new Headers(options.headers)
        headers.set('Authorization', `Bearer ${token}`)
        options.headers = headers
      }
    },

    onResponseError({ response, options, request }) {
      if ((options as any)._skipGlobalError) return

      // 401/403 with a present token → logout. Spring Security returns 403 (not 401)
      // for unauthenticated requests via Http403ForbiddenEntryPoint, so both are
      // treated the same. Skip the refresh endpoint to avoid redirect loops.
      if ((response.status === 401 || response.status === 403) && import.meta.client) {
        const isRefresh = typeof request === 'string' && request.includes('/auth/refresh')
        const hadToken = !!useCookie('accessToken').value
        if (hadToken && !isRefresh) {
          const auth = useAuthStore()
          auth.logout()
          return
        }
      }

      const body = response._data as ErrorApiDto | undefined
      if (!body?.error) return

      const appError = ErrorMapper.toEntity(body)

      // Forms display validation errors inline.
      if (appError.category === 'VALIDATION') return
      // Login page handles activation redirect itself.
      if (appError.category === 'ACTIVATION_REQUIRED') return

      if (import.meta.client) {
        const { handleError } = useErrorHandler()
        handleError(appError)
      }
    },
  })

  return {
    provide: { api },
  }
})
