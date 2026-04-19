<!--
  API keys settings page.
  - Generate a new key (plaintext shown ONCE in a dialog)
  - List keys with prefix + last-used
  - Revoke keys
-->
<script setup lang="ts">
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import type { ApiKeyApiDto } from '~/types/api/api-key.dto'

definePageMeta({ middleware: 'auth' })

const { data: keys, isLoading } = useApiKeys()
const createMutation = useCreateApiKey()
const revokeMutation = useRevokeApiKey()

const createOpen = ref(false)
const newName = ref('')

const revealOpen = ref(false)
const revealedKey = ref<string>('')

async function handleCreate() {
  if (!newName.value.trim()) { toast.error('Name is required'); return }
  try {
    const result = await createMutation.mutateAsync({ name: newName.value.trim() })
    revealedKey.value = result.plaintext ?? ''
    newName.value = ''
    createOpen.value = false
    revealOpen.value = true
  } catch (error: any) {
    toast.error(error?.data?.error?.message ?? 'Failed to create key')
  }
}

async function handleRevoke(k: ApiKeyApiDto) {
  const ok = await useConfirm().ask({
    title: `Revoke "${k.name}"?`,
    description: 'Any extension or integration using this key will stop working immediately.',
    confirmLabel: 'Revoke',
    variant: 'destructive',
  })
  if (!ok) return
  try {
    await revokeMutation.mutateAsync(k.id)
    toast.success('Key revoked')
  } catch {
    toast.error('Failed to revoke')
  }
}

async function copyKey() {
  await navigator.clipboard.writeText(revealedKey.value)
  toast.success('Copied to clipboard')
}

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso))
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-start justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">API keys</h1>
        <p class="text-sm text-muted-foreground">
          Long-lived tokens for the browser extension and integrations. Each key inherits your role and workspace.
        </p>
      </div>
      <Button @click="createOpen = true">+ New key</Button>
    </div>

    <Card>
      <CardContent class="pt-6">
        <div v-if="isLoading" class="text-sm text-muted-foreground py-6 text-center">Loading...</div>
        <div v-else-if="!keys?.length" class="text-sm text-muted-foreground py-10 text-center">
          No API keys yet. Create one to connect the Chrome extension.
        </div>
        <ul v-else class="divide-y" style="--tw-divide-opacity: 1;">
          <li
            v-for="k in keys"
            :key="k.id"
            class="flex items-center gap-4 py-3"
            style="border-top: 1px solid hsl(240 5% 100% / 0.06);"
          >
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2">
                <p class="font-medium text-sm">{{ k.name }}</p>
                <Badge v-if="k.revokedAt" variant="destructive" class="text-xs">Revoked</Badge>
                <Badge v-else variant="secondary" class="text-xs">Active</Badge>
              </div>
              <p class="text-xs text-muted-foreground font-mono mt-0.5">
                lr_{{ k.keyPrefix }}•••••••••••••••••••••••••••••
              </p>
              <p class="text-xs text-muted-foreground mt-0.5">
                Last used {{ formatDate(k.lastUsedAt) }} · created {{ formatDate(k.createdAt) }}
              </p>
            </div>
            <Button
              v-if="!k.revokedAt"
              size="sm"
              variant="outline"
              class="text-destructive"
              @click="handleRevoke(k)"
            >Revoke</Button>
          </li>
        </ul>
      </CardContent>
    </Card>

    <!-- Create dialog -->
    <Dialog v-model:open="createOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Generate an API key</DialogTitle>
          <CardDescription>
            Name it after where you'll use it. The full key is shown only once on the next screen.
          </CardDescription>
        </DialogHeader>
        <div class="space-y-4">
          <div class="space-y-2">
            <Label for="name">Name *</Label>
            <Input id="name" v-model="newName" placeholder="e.g., My Chrome extension" @keyup.enter="handleCreate" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="createOpen = false">Cancel</Button>
          <Button :disabled="createMutation.isPending.value" @click="handleCreate">Generate</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Reveal dialog — plaintext shown once -->
    <Dialog v-model:open="revealOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Key generated</DialogTitle>
          <CardDescription>
            Copy it now — you won't see it again. Paste it into the Lead Rush browser extension's settings.
          </CardDescription>
        </DialogHeader>
        <div class="rounded-md hairline bg-white/5 p-3 font-mono text-sm break-all">
          {{ revealedKey }}
        </div>
        <DialogFooter>
          <Button variant="outline" @click="revealOpen = false">Close</Button>
          <Button @click="copyKey">Copy</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
