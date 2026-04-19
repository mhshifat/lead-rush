<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Eye, EyeOff } from 'lucide-vue-next'

definePageMeta({
  layout: 'auth',
  middleware: 'guest',
})

const authStore = useAuthStore()

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
        <Button variant="outline" disabled>Google</Button>
        <Button variant="outline" disabled>GitHub</Button>
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
          <input
            v-model="acceptedTerms"
            type="checkbox"
            required
            class="mt-0.5 h-4 w-4 rounded border border-input bg-background accent-primary cursor-pointer"
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
