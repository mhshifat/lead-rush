<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription,
} from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import {
  Plus, Key, Trash2, Copy, AlertTriangle, Calendar, Clock, ShieldOff,
} from 'lucide-vue-next'
import type { ApiKeyApiDto } from '~/types/api/api-key.dto'

definePageMeta({ middleware: 'auth' })

const { data: keys, isLoading } = useApiKeys()
const createMutation = useCreateApiKey()
const revokeMutation = useRevokeApiKey()

const createOpen = ref(false)
const newName = ref('')
const createErrors = useFieldErrors()

watch(newName, v => { if (v.trim()) createErrors.remove('newName') })
watch(createOpen, (open) => { if (open) createErrors.clear() })

const revealOpen = ref(false)
const revealedKey = ref<string>('')

async function handleCreate() {
  createErrors.clear()
  if (!newName.value.trim()) createErrors.set('newName', 'Name is required.')
  if (Object.keys(createErrors.map).length) return
  try {
    const result = await createMutation.mutateAsync({ name: newName.value.trim() })
    revealedKey.value = result.plaintext ?? ''
    newName.value = ''
    createOpen.value = false
    revealOpen.value = true
  } catch (error: any) {
    createErrors.fromServerError(error, 'Failed to create key')
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

const totals = computed(() => {
  const list = keys.value ?? []
  const active = list.filter(k => !k.revokedAt).length
  return {
    total: list.length,
    active,
    revoked: list.length - active,
  }
})
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Header -->
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">API keys</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          Long-lived tokens for the browser extension and integrations — each key inherits your role + workspace.
        </p>
      </div>
      <Button class="gap-1.5" @click="createOpen = true">
        <Plus class="h-4 w-4" />
        New key
      </Button>
    </div>

    <!-- Summary strip -->
    <div
      v-if="keys?.length"
      class="glass hairline rounded-xl grid grid-cols-3 divide-x divide-white/5"
    >
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Keys</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.total }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Active</p>
        <p class="mt-1 text-xl font-semibold tabular-nums text-emerald-400">{{ totals.active }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Revoked</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.revoked }}</p>
      </div>
    </div>

    <!-- Loading -->
    <div
      v-if="isLoading"
      class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground"
    >
      Loading keys…
    </div>

    <!-- Empty state -->
    <div
      v-else-if="!keys?.length"
      class="glass hairline rounded-xl py-14 px-6 text-center"
    >
      <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-4">
        <Key class="h-5 w-5 text-muted-foreground" />
      </div>
      <h3 class="text-sm font-semibold tracking-tight">No API keys yet</h3>
      <p class="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
        Generate a key to connect the Lead Rush browser extension or call the public API from your own integrations.
      </p>
      <Button class="mt-5 gap-1.5" @click="createOpen = true">
        <Plus class="h-4 w-4" />
        New key
      </Button>
    </div>

    <!-- Keys list -->
    <div v-else class="glass hairline rounded-xl overflow-hidden">
      <ul>
        <li
          v-for="(k, idx) in keys"
          :key="k.id"
          class="flex items-start gap-3 px-5 py-4 transition-colors hover:bg-white/2"
          :class="k.revokedAt ? 'opacity-60' : ''"
          :style="idx > 0 ? 'border-top: 1px solid hsl(240 5% 100% / 0.05);' : ''"
        >
          <div class="relative h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
            <Key class="h-4 w-4 text-primary" />
            <span
              class="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full ring-2 ring-background"
              :class="k.revokedAt ? 'bg-muted-foreground/50' : 'bg-emerald-400'"
            />
          </div>

          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <p class="font-semibold tracking-tight truncate">{{ k.name }}</p>
              <Badge v-if="k.revokedAt" variant="destructive" class="text-xs">Revoked</Badge>
              <Badge v-else variant="default" class="text-xs">Active</Badge>
            </div>
            <p class="text-xs text-muted-foreground font-mono mt-1">
              lr_{{ k.keyPrefix }}<span class="opacity-50">•••••••••••••••••••••••••••••</span>
            </p>
            <div class="flex items-center flex-wrap gap-x-3 gap-y-1 mt-2 text-xs text-muted-foreground">
              <span class="inline-flex items-center gap-1">
                <Calendar class="h-3 w-3" />
                Created {{ formatDate(k.createdAt) }}
              </span>
              <span class="inline-flex items-center gap-1">
                <Clock class="h-3 w-3" />
                Last used {{ formatDate(k.lastUsedAt) }}
              </span>
              <span v-if="k.revokedAt" class="inline-flex items-center gap-1 text-destructive">
                <ShieldOff class="h-3 w-3" />
                Revoked {{ formatDate(k.revokedAt) }}
              </span>
            </div>
          </div>

          <Button
            v-if="!k.revokedAt"
            size="sm"
            variant="outline"
            class="h-8 gap-1 text-muted-foreground hover:text-destructive shrink-0"
            @click="handleRevoke(k)"
          >
            <Trash2 class="h-3.5 w-3.5" />
            Revoke
          </Button>
        </li>
      </ul>
    </div>

    <!-- Create dialog -->
    <Dialog v-model:open="createOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Generate an API key</DialogTitle>
          <DialogDescription>
            Name it after where you'll use it. The full key is shown only once on the next screen.
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-4">
          <div
            v-if="createErrors.has('_form')"
            class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
          >
            {{ createErrors.get('_form') }}
          </div>
          <div class="space-y-2">
            <Label for="name">Name *</Label>
            <Input
              id="name"
              v-model="newName"
              placeholder="e.g. My Chrome extension"
              :class="createErrors.has('newName') ? 'border-destructive' : ''"
              @keyup.enter="handleCreate"
            />
            <SharedFormError :message="createErrors.get('newName')" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="createOpen = false">Cancel</Button>
          <Button :disabled="createMutation.isPending.value" @click="handleCreate">
            {{ createMutation.isPending.value ? 'Generating…' : 'Generate' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Reveal dialog — plaintext shown once -->
    <Dialog v-model:open="revealOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Key generated</DialogTitle>
          <DialogDescription>
            Copy it now — you won't see it again. Paste it into the Lead Rush browser extension's settings.
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-3">
          <div class="rounded-md hairline bg-white/5 p-3 font-mono text-sm wrap-break-word">
            {{ revealedKey }}
          </div>
          <div class="flex items-start gap-2 rounded-md bg-amber-400/10 hairline border-amber-400/20 p-3 text-xs text-amber-200">
            <AlertTriangle class="h-3.5 w-3.5 mt-0.5 shrink-0" />
            <span>
              This is the only time the full key will be shown. Store it somewhere safe — if you lose it, you'll need to generate a new one.
            </span>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="revealOpen = false">Close</Button>
          <Button class="gap-1.5" @click="copyKey">
            <Copy class="h-3.5 w-3.5" />
            Copy
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
