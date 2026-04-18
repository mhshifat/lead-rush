// Bounces already-authenticated users away from /auth/* pages.

function isTokenStructurallyValid(token: string): boolean {
  try {
    const [, payload] = token.split('.')
    if (!payload) return false
    const b64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = b64 + '==='.slice((b64.length + 3) % 4)
    const claims = JSON.parse(atob(padded)) as { exp?: number }
    if (!claims.exp) return false
    return claims.exp - 30 > Math.floor(Date.now() / 1000)
  } catch {
    return false
  }
}

export default defineNuxtRouteMiddleware((to, _from) => {
  const tokenCookie = useCookie('accessToken')
  const token = tokenCookie.value

  // No token, or one that's expired → let the guest through. Clear stale
  // cookies so the user doesn't keep getting bounced around.
  if (!token || !isTokenStructurallyValid(token)) {
    if (token) {
      tokenCookie.value = null
      useCookie('refreshToken').value = null
    }
    return
  }

  // Valid token: honor ?next=<path> from the auth middleware if present,
  // else go to the dashboard. Only accept relative paths starting with "/"
  // to avoid open-redirect via ?next=https://evil.example.com.
  const next = to.query.next as string | undefined
  if (next && next.startsWith('/')) return navigateTo(next)
  return navigateTo('/dashboard')
})
