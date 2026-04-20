<script setup lang="ts">
import { toast } from 'vue-sonner'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Eye, EyeOff } from 'lucide-vue-next'

definePageMeta({
  layout: 'auth',
  middleware: 'guest',
})
useHead({ title: 'Sign in' })

const authStore = useAuthStore()
const route = useRoute()
const runtimeConfig = useRuntimeConfig()

const email = ref('')
const password = ref('')
const showPassword = ref(false)
const isLoading = ref(false)
const errorMessage = ref('')

const lastUsed = ref<{ email: string; provider: string } | null>(null)

// OAuth entry-point URL. Derived from the API base by stripping the /api/v1
// suffix because the OAuth authorize endpoint lives at /api/v1/auth/oauth2/...
// but the frontend's API client already hits /api/v1/auth/login etc. directly.
function oauthUrl(provider: 'google' | 'github'): string {
  const base = (runtimeConfig.public.apiBaseUrl as string).replace(/\/api\/v1\/?$/, '')
  return `${base}/api/v1/auth/oauth2/authorize/${provider}`
}

onMounted(() => {
  lastUsed.value = authStore.getLastUsedAuth()

  if (route.query.activated === 'true') {
    toast.success('Account activated! You can now sign in.')
  }

  // OAuth failure redirects land here with an ?error= code. Surface a generic
  // message — the code itself goes in the console for support triage.
  const err = route.query.error as string | undefined
  if (err) {
    console.warn('[auth] OAuth error code:', err)
    errorMessage.value = err === 'oauth_denied'
      ? 'You cancelled the sign-in on the provider\'s page.'
      : 'Sign-in didn\'t complete. Please try again.'
  }
})

async function handleSubmit() {
  errorMessage.value = ''
  isLoading.value = true

  try {
    const result = await authStore.login(email.value, password.value)

    if (result.success) {
      // Only trust paths starting with "/" to avoid open-redirect via ?next=https://...
      const next = route.query.next as string | undefined
      const target = next && next.startsWith('/') ? next : '/dashboard'
      navigateTo(target)
    } else if (result.activationRequired) {
      navigateTo(`/auth/activate-notice?email=${encodeURIComponent(email.value)}&sentAt=${result.lastActivationEmailSentAt}`)
    }
  } catch (error: any) {
    const errorData = error.data
    if (errorData?.error?.message) {
      errorMessage.value = errorData.error.message
    } else {
      errorMessage.value = 'Something went wrong. Please try again.'
    }
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="glass rounded-2xl p-8 space-y-6">
    <div class="space-y-1.5 text-center">
      <h1 class="text-2xl font-semibold tracking-tight">Sign in to Lead Rush</h1>
      <p class="text-sm text-muted-foreground">Welcome back</p>
    </div>

    <div class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <a
          :href="oauthUrl('google')"
          class="relative inline-flex items-center justify-center gap-2 rounded-md border border-input bg-background px-4 py-2 text-sm font-medium hover:bg-accent hover:text-accent-foreground transition-colors"
        >
          <svg class="w-4 h-4" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
            <path fill="#FBBC05" d="M5.84 14.1c-.22-.66-.35-1.36-.35-2.1s.13-1.44.35-2.1V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.83z"/>
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.83C6.71 7.31 9.14 5.38 12 5.38z"/>
          </svg>
          Google
          <Badge
            v-if="lastUsed?.provider === 'GOOGLE'"
            variant="secondary"
            class="absolute -top-2 -right-2 text-xs"
          >
            Last used
          </Badge>
        </a>
        <a
          :href="oauthUrl('github')"
          class="relative inline-flex items-center justify-center gap-2 rounded-md border border-input bg-background px-4 py-2 text-sm font-medium hover:bg-accent hover:text-accent-foreground transition-colors"
        >
          <svg class="w-4 h-4" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">
            <path d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.1.79-.25.79-.56v-2.17c-3.2.7-3.88-1.37-3.88-1.37-.52-1.32-1.28-1.67-1.28-1.67-1.04-.71.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.56-.29-5.25-1.28-5.25-5.69 0-1.26.45-2.28 1.19-3.08-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11.1 11.1 0 012.9-.39c.98 0 1.97.13 2.9.39 2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.24 2.76.12 3.05.74.8 1.19 1.82 1.19 3.08 0 4.42-2.69 5.39-5.26 5.67.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56C20.22 21.39 23.5 17.08 23.5 12 23.5 5.65 18.35.5 12 .5z"/>
          </svg>
          GitHub
          <Badge
            v-if="lastUsed?.provider === 'GITHUB'"
            variant="secondary"
            class="absolute -top-2 -right-2 text-xs"
          >
            Last used
          </Badge>
        </a>
      </div>

      <div class="flex items-center gap-3">
        <span class="flex-1 border-t border-white/10" />
        <span class="text-xs uppercase tracking-wider text-muted-foreground">or continue with email</span>
        <span class="flex-1 border-t border-white/10" />
      </div>

      <form @submit.prevent="handleSubmit" class="space-y-4">
        <div v-if="errorMessage" class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
          {{ errorMessage }}
        </div>

        <div class="space-y-2">
          <Label for="email">Email</Label>
          <div class="relative">
            <Input
              id="email"
              v-model="email"
              type="email"
              placeholder="you@example.com"
              required
              autocomplete="email"
            />
            <Badge
              v-if="lastUsed?.provider === 'LOCAL'"
              variant="secondary"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-xs"
            >
              Last used
            </Badge>
          </div>
        </div>

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <Label for="password">Password</Label>
            <NuxtLink
              to="/auth/forgot-password"
              class="text-sm text-muted-foreground underline-offset-4 hover:underline"
            >
              Forgot password?
            </NuxtLink>
          </div>
          <div class="relative">
            <Input
              id="password"
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="Enter your password"
              required
              autocomplete="current-password"
              class="pr-10"
            />
            <button
              type="button"
              class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
              :aria-label="showPassword ? 'Hide password' : 'Show password'"
              @click="showPassword = !showPassword"
            >
              <EyeOff v-if="showPassword" class="h-4 w-4" />
              <Eye v-else class="h-4 w-4" />
            </button>
          </div>
        </div>

        <Button type="submit" class="w-full" :disabled="isLoading">
          <template v-if="isLoading">Signing in...</template>
          <template v-else>Sign in</template>
        </Button>
      </form>

      <p class="text-center text-sm text-muted-foreground">
        Don't have an account?
        <NuxtLink to="/auth/register" class="underline underline-offset-4 hover:text-primary">
          Sign up
        </NuxtLink>
      </p>
    </div>
  </div>
</template>
