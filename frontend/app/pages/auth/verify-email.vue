<script setup lang="ts">
import { Button } from '~/components/ui/button'

definePageMeta({
  layout: 'auth',
  middleware: 'guest',
})
useHead({ title: 'Email verified' })

const route = useRoute()
const { $api } = useNuxtApp()

type Status = 'verifying' | 'success' | 'error'
const status = ref<Status>('verifying')
const errorMessage = ref('')

onMounted(async () => {
  const token = route.query.token as string | undefined
  if (!token) {
    status.value = 'error'
    errorMessage.value = 'Missing activation token.'
    return
  }

  try {
    await $api(`/auth/verify-email?token=${encodeURIComponent(token)}`, {
      method: 'GET',
      _skipGlobalError: true,
    } as any)
    status.value = 'success'
    setTimeout(() => navigateTo('/auth/login?activated=true'), 1200)
  } catch (err: any) {
    status.value = 'error'
    errorMessage.value = err?.data?.error?.message ?? 'Could not activate your account.'
  }
})
</script>

<template>
  <div class="glass rounded-2xl p-8 space-y-6 text-center">
    <div v-if="status === 'verifying'" class="space-y-3">
      <h1 class="text-2xl font-semibold tracking-tight">Activating your account…</h1>
      <p class="text-sm text-muted-foreground">Hold tight, this only takes a second.</p>
    </div>

    <div v-else-if="status === 'success'" class="space-y-3">
      <h1 class="text-2xl font-semibold tracking-tight">Account activated</h1>
      <p class="text-sm text-muted-foreground">Redirecting you to sign in…</p>
    </div>

    <div v-else class="space-y-4">
      <h1 class="text-2xl font-semibold tracking-tight">Activation failed</h1>
      <p class="text-sm text-destructive">{{ errorMessage }}</p>
      <Button as-child variant="outline" class="w-full">
        <NuxtLink to="/auth/login">Back to sign in</NuxtLink>
      </Button>
    </div>
  </div>
</template>
