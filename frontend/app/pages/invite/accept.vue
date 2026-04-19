<!--
  Invitation accept page — hit via the link in the invitation email.
  /invite/accept?token=XXX

  Handles three flows:
    1. Not logged in → show preview + "Sign in to accept"
    2. Logged in with the right email → accept + switch into workspace
    3. Logged in with a different email → error message
-->
<script setup lang="ts">
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Badge } from '~/components/ui/badge'
import { useQueryClient } from '@tanstack/vue-query'
import { toast } from 'vue-sonner'

// No auth middleware — this page is accessible without login (we show the preview first)
definePageMeta({ layout: 'auth' })
useHead({ title: 'Accept invite' })

const route = useRoute()
const token = computed(() => (route.query.token as string) ?? null)

const authStore = useAuthStore()
const queryClient = useQueryClient()

const { data: invitation, isLoading, isError } = usePreviewInvitation(token)
const acceptMutation = useAcceptInvitation()

const emailMismatch = computed(() => {
  if (!authStore.user || !invitation.value) return false
  return authStore.user.email.toLowerCase() !== invitation.value.email.toLowerCase()
})

async function handleAccept() {
  if (!token.value) return
  try {
    const workspace = await acceptMutation.mutateAsync(token.value)
    await authStore.switchWorkspace(workspace.id)
    queryClient.clear()
    toast.success(`Welcome to ${workspace.name}!`)
    await navigateTo('/dashboard')
  } catch (error: any) {
    toast.error(error?.data?.error?.message ?? 'Failed to accept invitation')
  }
}

function handleSignIn() {
  navigateTo(`/auth/login?next=${encodeURIComponent(route.fullPath)}&email=${encodeURIComponent(invitation.value?.email ?? '')}`)
}
</script>

<template>
  <div class="w-full max-w-md">
    <Card>
      <CardHeader>
        <CardTitle>Workspace invitation</CardTitle>
        <CardDescription v-if="!token">No invitation token provided.</CardDescription>
      </CardHeader>

      <CardContent class="space-y-4">
        <div v-if="isLoading" class="text-sm text-muted-foreground">Loading invitation…</div>

        <div v-else-if="isError || !invitation" class="text-sm text-destructive">
          This invitation is invalid or has been revoked.
        </div>

        <template v-else>
          <!-- Expired / revoked / accepted states -->
          <div
            v-if="invitation.status !== 'PENDING'"
            class="rounded-md border p-3 text-sm"
          >
            <p class="font-medium">This invitation is {{ invitation.status.toLowerCase() }}.</p>
            <p class="text-muted-foreground mt-1">
              Ask {{ invitation.invitedByName ?? 'an admin' }} to send a new one.
            </p>
          </div>

          <template v-else>
            <p class="text-sm">
              <strong>{{ invitation.invitedByName ?? 'Someone' }}</strong>
              invited you to join
              <strong>{{ invitation.workspaceName ?? 'a workspace' }}</strong>
              as a
            </p>
            <div class="flex items-center gap-2">
              <Badge>{{ invitation.role }}</Badge>
              <span class="text-sm text-muted-foreground">on Lead Rush</span>
            </div>

            <div class="rounded-md bg-muted/50 p-3 text-sm">
              <p class="text-muted-foreground">Invited email</p>
              <p class="font-medium">{{ invitation.email }}</p>
            </div>

            <!-- Flow 1: not logged in -->
            <div v-if="!authStore.isLoggedIn" class="space-y-2">
              <Button class="w-full" @click="handleSignIn">Sign in to accept</Button>
              <p class="text-xs text-muted-foreground text-center">
                Don't have an account?
                <NuxtLink
                  :to="`/auth/register?email=${encodeURIComponent(invitation.email)}`"
                  class="underline"
                >
                  Create one
                </NuxtLink>
              </p>
            </div>

            <!-- Flow 3: wrong account -->
            <div v-else-if="emailMismatch" class="space-y-2">
              <div class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
                You're signed in as <strong>{{ authStore.user?.email }}</strong>, but this
                invitation was sent to <strong>{{ invitation.email }}</strong>.
              </div>
              <Button variant="outline" class="w-full" @click="authStore.logout()">
                Sign out and use a different account
              </Button>
            </div>

            <!-- Flow 2: logged in, right email -->
            <div v-else class="space-y-2">
              <Button
                class="w-full"
                :disabled="acceptMutation.isPending.value"
                @click="handleAccept"
              >
                {{ acceptMutation.isPending.value ? 'Joining…' : 'Accept invitation' }}
              </Button>
            </div>
          </template>
        </template>
      </CardContent>
    </Card>
  </div>
</template>
