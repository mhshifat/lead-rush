<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Checkbox } from '~/components/ui/checkbox'
import { Eye, EyeOff } from 'lucide-vue-next'

definePageMeta({
  layout: 'auth',
  middleware: 'guest',
})
useHead({ title: 'Create account' })

const authStore = useAuthStore()
const runtimeConfig = useRuntimeConfig()

// OAuth entry-point URL — strips the /api/v1 suffix from the configured API
// base because the authorize endpoint lives under /api/v1/auth/oauth2/...
function oauthUrl(provider: 'google' | 'github'): string {
  const base = (runtimeConfig.public.apiBaseUrl as string).replace(/\/api\/v1\/?$/, '')
  return `${base}/api/v1/auth/oauth2/authorize/${provider}`
}

const firstName = ref('')
const lastName = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const acceptedTerms = ref(false)

const showPassword = ref(false)
const showConfirmPassword = ref(false)

const isLoading = ref(false)
const errorMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})

async function handleSubmit() {
  errorMessage.value = ''
  fieldErrors.value = {}

  if (password.value !== confirmPassword.value) {
    fieldErrors.value.confirmPassword = 'Passwords do not match'
    return
  }

  isLoading.value = true
  try {
    const name = `${firstName.value.trim()} ${lastName.value.trim()}`.trim()
    await authStore.register(name, email.value, password.value)
    navigateTo(`/auth/activate-notice?email=${encodeURIComponent(email.value)}&sentAt=${new Date().toISOString()}`)
  } catch (error: any) {
    const errorData = error.data
    if (errorData?.error?.category === 'VALIDATION' && errorData.error.details) {
      for (const detail of errorData.error.details) {
        fieldErrors.value[detail.field] = detail.message
      }
    } else if (errorData?.error?.message) {
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
      <h1 class="text-2xl font-semibold tracking-tight">Create an account</h1>
      <p class="text-sm text-muted-foreground">Get started with Lead Rush</p>
    </div>

    <div class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <a
          :href="oauthUrl('google')"
          class="inline-flex items-center justify-center gap-2 rounded-md border border-input bg-background px-4 py-2 text-sm font-medium hover:bg-accent hover:text-accent-foreground transition-colors"
        >
          <svg class="w-4 h-4" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
            <path fill="#FBBC05" d="M5.84 14.1c-.22-.66-.35-1.36-.35-2.1s.13-1.44.35-2.1V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.83z"/>
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.83C6.71 7.31 9.14 5.38 12 5.38z"/>
          </svg>
          Google
        </a>
        <a
          :href="oauthUrl('github')"
          class="inline-flex items-center justify-center gap-2 rounded-md border border-input bg-background px-4 py-2 text-sm font-medium hover:bg-accent hover:text-accent-foreground transition-colors"
        >
          <svg class="w-4 h-4" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">
            <path d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.1.79-.25.79-.56v-2.17c-3.2.7-3.88-1.37-3.88-1.37-.52-1.32-1.28-1.67-1.28-1.67-1.04-.71.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.56-.29-5.25-1.28-5.25-5.69 0-1.26.45-2.28 1.19-3.08-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11.1 11.1 0 012.9-.39c.98 0 1.97.13 2.9.39 2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.24 2.76.12 3.05.74.8 1.19 1.82 1.19 3.08 0 4.42-2.69 5.39-5.26 5.67.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56C20.22 21.39 23.5 17.08 23.5 12 23.5 5.65 18.35.5 12 .5z"/>
          </svg>
          GitHub
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

        <!-- First + Last name -->
        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-2">
            <Label for="firstName">First name</Label>
            <Input
              id="firstName"
              v-model="firstName"
              type="text"
              placeholder="Jane"
              required
              autocomplete="given-name"
            />
            <p v-if="fieldErrors.firstName" class="text-sm text-destructive">{{ fieldErrors.firstName }}</p>
          </div>
          <div class="space-y-2">
            <Label for="lastName">Last name</Label>
            <Input
              id="lastName"
              v-model="lastName"
              type="text"
              placeholder="Doe"
              required
              autocomplete="family-name"
            />
            <p v-if="fieldErrors.lastName" class="text-sm text-destructive">{{ fieldErrors.lastName }}</p>
          </div>
        </div>

        <!-- Email -->
        <div class="space-y-2">
          <Label for="email">Email</Label>
          <Input
            id="email"
            v-model="email"
            type="email"
            placeholder="you@example.com"
            required
            autocomplete="email"
          />
          <p v-if="fieldErrors.email" class="text-sm text-destructive">{{ fieldErrors.email }}</p>
        </div>

        <!-- Password + confirm -->
        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-2">
            <Label for="password">Password</Label>
            <div class="relative">
              <Input
                id="password"
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="At least 8 characters"
                required
                autocomplete="new-password"
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
            <p v-if="fieldErrors.password" class="text-sm text-destructive">{{ fieldErrors.password }}</p>
          </div>

          <div class="space-y-2">
            <Label for="confirmPassword">Confirm password</Label>
            <div class="relative">
              <Input
                id="confirmPassword"
                v-model="confirmPassword"
                :type="showConfirmPassword ? 'text' : 'password'"
                placeholder="Re-enter password"
                required
                autocomplete="new-password"
                class="pr-10"
              />
              <button
                type="button"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                :aria-label="showConfirmPassword ? 'Hide password' : 'Show password'"
                @click="showConfirmPassword = !showConfirmPassword"
              >
                <EyeOff v-if="showConfirmPassword" class="h-4 w-4" />
                <Eye v-else class="h-4 w-4" />
              </button>
            </div>
            <p v-if="fieldErrors.confirmPassword" class="text-sm text-destructive">{{ fieldErrors.confirmPassword }}</p>
          </div>
        </div>

        <!-- Terms + privacy -->
        <label class="flex items-start gap-2 cursor-pointer select-none">
          <Checkbox
            v-model="acceptedTerms"
            class="mt-0.5"
          />
          <span class="text-sm text-muted-foreground leading-snug">
            I agree to the
            <NuxtLink to="/terms" class="underline underline-offset-4 hover:text-foreground">Terms of Service</NuxtLink>
            and
            <NuxtLink to="/privacy" class="underline underline-offset-4 hover:text-foreground">Privacy Policy</NuxtLink>
          </span>
        </label>

        <Button type="submit" class="w-full" :disabled="isLoading || !acceptedTerms">
          <template v-if="isLoading">Creating account...</template>
          <template v-else>Create account</template>
        </Button>
      </form>

      <p class="text-center text-sm text-muted-foreground">
        Already have an account?
        <NuxtLink to="/auth/login" class="underline underline-offset-4 hover:text-primary">
          Sign in
        </NuxtLink>
      </p>
    </div>
  </div>
</template>
