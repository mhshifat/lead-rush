<!--
  Register Page — creates an account, then redirects to activate-notice.
  Does NOT log the user in (they must activate via email first).
-->
<script setup lang="ts">
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'

definePageMeta({
  layout: 'auth',
  middleware: 'guest',
})

const authStore = useAuthStore()

// ── Form state ──
const name = ref('')
const email = ref('')
const password = ref('')
const isLoading = ref(false)
const errorMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})

async function handleSubmit() {
  errorMessage.value = ''
  fieldErrors.value = {}
  isLoading.value = true

  try {
    await authStore.register(name.value, email.value, password.value)

    // Success — redirect to activation notice (NOT dashboard)
    navigateTo(`/auth/activate-notice?email=${encodeURIComponent(email.value)}&sentAt=${new Date().toISOString()}`)
  } catch (error: any) {
    const errorData = error.data
    if (errorData?.error?.category === 'VALIDATION' && errorData.error.details) {
      // Field-level errors — display inline
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
      <!-- OAuth buttons -->
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
        <!-- Global error -->
        <div v-if="errorMessage" class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
          {{ errorMessage }}
        </div>

        <!-- Name -->
        <div class="space-y-2">
          <Label for="name">Name</Label>
          <Input
            id="name"
            v-model="name"
            type="text"
            placeholder="Your name"
            required
            autocomplete="name"
          />
          <p v-if="fieldErrors.name" class="text-sm text-destructive">{{ fieldErrors.name }}</p>
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

        <!-- Password -->
        <div class="space-y-2">
          <Label for="password">Password</Label>
          <Input
            id="password"
            v-model="password"
            type="password"
            placeholder="At least 8 characters"
            required
            autocomplete="new-password"
          />
          <p v-if="fieldErrors.password" class="text-sm text-destructive">{{ fieldErrors.password }}</p>
        </div>

        <Button type="submit" class="w-full" :disabled="isLoading">
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
