<script setup lang="ts">
import { toast } from 'vue-sonner'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '~/components/ui/card'
import { Button } from '~/components/ui/button'

definePageMeta({
  layout: 'auth',
  middleware: 'guest',
})

const authStore = useAuthStore()
const route = useRoute()

const email = route.query.email as string
const sentAt = route.query.sentAt as string

const COOLDOWN_SECONDS = 5 * 60
const secondsRemaining = ref(0)
const canResend = computed(() => secondsRemaining.value <= 0)
const isResending = ref(false)

function startCountdown(fromTimestamp: string) {
  const sentTime = new Date(fromTimestamp).getTime()
  const now = Date.now()
  const elapsedSeconds = Math.floor((now - sentTime) / 1000)
  secondsRemaining.value = Math.max(0, COOLDOWN_SECONDS - elapsedSeconds)
}

const timer = setInterval(() => {
  if (secondsRemaining.value > 0) {
    secondsRemaining.value--
  }
}, 1000)

onUnmounted(() => clearInterval(timer))

if (sentAt) {
  startCountdown(sentAt)
}

const formattedTime = computed(() => {
  const mins = Math.floor(secondsRemaining.value / 60)
  const secs = secondsRemaining.value % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
})

async function resendActivation() {
  if (!canResend.value || isResending.value) return

  isResending.value = true
  try {
    await authStore.resendActivation(email)
    toast.success('Activation email sent!')
    startCountdown(new Date().toISOString())
  } catch (error: any) {
    const errorData = error.data
    if (errorData?.error?.message) {
      toast.error(errorData.error.message)
    } else {
      toast.error('Failed to resend activation email')
    }
  } finally {
    isResending.value = false
  }
}
</script>

<template>
  <Card>
    <CardHeader class="space-y-1">
      <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-muted">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 text-muted-foreground" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
        </svg>
      </div>
      <CardTitle class="text-2xl text-center">Check your email</CardTitle>
      <CardDescription class="text-center">
        We sent an activation link to
        <strong class="text-foreground">{{ email }}</strong>
      </CardDescription>
    </CardHeader>

    <CardContent class="space-y-4">
      <Button
        variant="outline"
        class="w-full"
        :disabled="!canResend || isResending"
        @click="resendActivation"
      >
        <template v-if="isResending">Sending...</template>
        <template v-else-if="canResend">Resend activation email</template>
        <template v-else>Resend in {{ formattedTime }}</template>
      </Button>

      <p class="text-center text-xs text-muted-foreground">
        Didn't receive it? Check your spam folder.
      </p>

      <div class="pt-2">
        <NuxtLink
          to="/auth/login"
          class="block text-center text-sm text-muted-foreground underline-offset-4 hover:underline"
        >
          Back to sign in
        </NuxtLink>
      </div>
    </CardContent>
  </Card>
</template>
