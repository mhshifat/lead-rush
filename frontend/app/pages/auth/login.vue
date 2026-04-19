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

const authStore = useAuthStore()
const route = useRoute()

const email = ref('')
const password = ref('')
const showPassword = ref(false)
const isLoading = ref(false)
const errorMessage = ref('')

const lastUsed = ref<{ email: string; provider: string } | null>(null)

onMounted(() => {
  lastUsed.value = authStore.getLastUsedAuth()

  if (route.query.activated === 'true') {
    toast.success('Account activated! You can now sign in.')
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
        <Button variant="outline" disabled class="relative">
          Google
          <Badge
            v-if="lastUsed?.provider === 'GOOGLE'"
            variant="secondary"
            class="absolute -top-2 -right-2 text-xs"
          >
            Last used
          </Badge>
        </Button>
        <Button variant="outline" disabled class="relative">
          GitHub
          <Badge
            v-if="lastUsed?.provider === 'GITHUB'"
            variant="secondary"
            class="absolute -top-2 -right-2 text-xs"
          >
            Last used
          </Badge>
        </Button>
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
