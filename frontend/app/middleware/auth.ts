// Redirects unauthenticated visitors to /auth/login.

function isTokenStructurallyValid(token: string): boolean {
  try {
    const [, payload] = token.split('.')
    if (!payload) return false
    // atob handles base64 but JWTs use base64url — convert and pad.
    const b64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = b64 + '==='.slice((b64.length + 3) % 4)
    const json = atob(padded)
    const claims = JSON.parse(json) as { exp?: number }
    if (!claims.exp) return false
    const nowSec = Math.floor(Date.now() / 1000)
    // 30s clock-skew grace so a just-about-to-expire token doesn't flicker off
    return claims.exp - 30 > nowSec
  } catch {
    return false
  }
}

export default defineNuxtRouteMiddleware((to, _from) => {
  const tokenCookie = useCookie('accessToken')
  const token = tokenCookie.value

  const valid = !!token && isTokenStructurallyValid(token)
  if (valid) return

  // Stale/expired/malformed token — clear it so the login page doesn't loop
  // back via guest middleware ("they have a token → send to dashboard").
  if (token) {
    tokenCookie.value = null
    const refreshCookie = useCookie('refreshToken')
    refreshCookie.value = null
  }

  // Preserve the intended destination (path + query) so login can send the user
  // back there on success. Skipped for roots that would just redirect again.
  const skipPreserve = to.path === '/' || to.path === '/dashboard' || to.path.startsWith('/auth/')
  if (skipPreserve) return navigateTo('/auth/login')

  const next = encodeURIComponent(to.fullPath)
  return navigateTo(`/auth/login?next=${next}`)
})
