<script setup lang="ts">
/**
 * OAuth2 callback landing page.
 *
 * Flow:
 *   Backend success handler 302s here with tokens + email + provider in the
 *   query string. We persist tokens, call /auth/me to hydrate the user, save
 *   `lastUsedAuth` to localStorage for the "Last used" badge on /auth/login,
 *   then hard-navigate to the dashboard — the hard nav also strips the query
 *   string from the browser history.
 *
 * Failure path:
 *   A missing / malformed query lands us on this page with no tokens. We
 *   bounce to /auth/login?error=oauth_missing rather than silently eating it.
 */
definePageMeta({
  layout: 'auth',
  // Guest middleware would kick signed-in users away; we explicitly skip it
  // because we want to complete the OAuth handshake before any redirect.
  middleware: [],
})

const route = useRoute()
const authStore = useAuthStore()

const status = ref<'working' | 'error'>('working')
const message = ref('Completing sign-in…')

onMounted(async () => {
  const accessToken = String(route.query.accessToken ?? '')
  const refreshToken = String(route.query.refreshToken ?? '')
  const provider = String(route.query.provider ?? '')
  const email = String(route.query.email ?? '')

  if (!accessToken || !refreshToken || !provider) {
    status.value = 'error'
    message.value = 'Missing authentication data.'
    await navigateTo('/auth/login?error=oauth_missing', { replace: true })
    return
  }

  try {
    await authStore.consumeOAuthCallback({ accessToken, refreshToken, provider, email })
    // Hard navigate so the middleware + any SSR-sensitive consumers re-evaluate
    // with the new cookie present. external: true also drops the ?accessToken=...
    // query from the URL so it doesn't linger in history.
    await navigateTo('/dashboard', { replace: true, external: false })
  } catch (err) {
    console.error('[auth/callback] consumeOAuthCallback failed', err)
    status.value = 'error'
    message.value = 'Sign-in completed, but we couldn\'t load your profile. Please try again.'
    setTimeout(() => navigateTo('/auth/login?error=oauth_hydrate', { replace: true }), 1500)
  }
})
</script>

<template>
  <div class="glass rounded-2xl p-8 space-y-4 text-center">
    <div class="flex justify-center">
      <div
        v-if="status === 'working'"
        class="w-10 h-10 rounded-full border-2 border-primary/20 border-t-primary animate-spin"
      />
      <div v-else class="text-destructive text-2xl">⚠</div>
    </div>
    <h1 class="text-xl font-semibold tracking-tight">
      {{ status === 'working' ? 'Finishing up…' : 'Something went wrong' }}
    </h1>
    <p class="text-sm text-muted-foreground">{{ message }}</p>
  </div>
</template>
