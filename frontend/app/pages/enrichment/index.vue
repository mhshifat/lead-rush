<!--
  Enrichment Providers settings page.
  Shows all available providers. Users can enable/disable, set API key, reorder priority.
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import type { EnrichmentProvider } from '~/composables/useEnrichment'

definePageMeta({
  middleware: 'auth',
})

const { data: providers, isLoading } = useEnrichmentProviders()
const updateMutation = useUpdateEnrichmentProvider()

// ── API key edit dialog ──
const keyDialogOpen = ref(false)
const editingProvider = ref<EnrichmentProvider | null>(null)
const apiKeyInput = ref('')

function openKeyDialog(provider: EnrichmentProvider) {
  editingProvider.value = provider
  apiKeyInput.value = ''
  keyDialogOpen.value = true
}

async function handleSaveKey() {
  if (!editingProvider.value) return
  try {
    await updateMutation.mutateAsync({
      providerKey: editingProvider.value.providerKey,
      apiKey: apiKeyInput.value,
    })
    toast.success('API key updated')
    keyDialogOpen.value = false
  } catch {
    toast.error('Failed to update API key')
  }
}

async function handleToggleEnabled(provider: EnrichmentProvider) {
  // Don't allow enabling if provider requires a key and has none
  if (!provider.enabled && provider.requiresApiKey && !provider.hasApiKey) {
    toast.error('Set an API key before enabling this provider')
    return
  }
  try {
    await updateMutation.mutateAsync({
      providerKey: provider.providerKey,
      enabled: !provider.enabled,
    })
    toast.success(provider.enabled ? 'Disabled' : 'Enabled')
  } catch {
    toast.error('Failed to update provider')
  }
}

async function handleChangePriority(provider: EnrichmentProvider, direction: 'up' | 'down') {
  const delta = direction === 'up' ? -10 : 10
  try {
    await updateMutation.mutateAsync({
      providerKey: provider.providerKey,
      priority: Math.max(0, provider.priority + delta),
    })
  } catch {
    toast.error('Failed to reorder')
  }
}

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat('en-US', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso))
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-3xl font-bold">Enrichment Providers</h1>
      <p class="text-sm text-muted-foreground">
        Configure data providers. Enriching a contact runs the <strong>waterfall</strong> —
        providers are tried in priority order (lowest first) until one returns data.
      </p>
    </div>

    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">Loading...</div>

    <div v-else class="space-y-3">
      <Card v-for="p in providers" :key="p.providerKey">
        <CardContent class="pt-6">
          <div class="flex items-start justify-between gap-4">
            <div class="flex-1">
              <div class="flex items-center gap-3 mb-1">
                <h3 class="font-medium">{{ p.displayName }}</h3>
                <Badge v-if="p.enabled" variant="default">Enabled</Badge>
                <Badge v-else variant="outline">Disabled</Badge>
                <Badge variant="secondary" class="text-xs">Priority: {{ p.priority }}</Badge>
              </div>
              <p class="text-xs text-muted-foreground font-mono">{{ p.providerKey }}</p>

              <div class="mt-3 grid grid-cols-3 gap-4 text-sm">
                <div>
                  <div class="text-xs text-muted-foreground">API Key</div>
                  <div class="font-medium">
                    <span v-if="!p.requiresApiKey">Not required</span>
                    <span v-else-if="p.hasApiKey" class="text-green-600">Configured ✓</span>
                    <span v-else class="text-destructive">Missing</span>
                  </div>
                </div>
                <div>
                  <div class="text-xs text-muted-foreground">This month</div>
                  <div class="font-medium">{{ p.callsThisMonth }} calls</div>
                </div>
                <div>
                  <div class="text-xs text-muted-foreground">Last used</div>
                  <div class="text-xs">{{ formatDate(p.lastUsedAt) }}</div>
                </div>
              </div>

              <div v-if="p.lastError" class="mt-2 rounded-md bg-destructive/10 p-2 text-xs text-destructive">
                {{ p.lastError }}
              </div>
            </div>

            <div class="flex flex-col gap-2">
              <!-- Priority arrows -->
              <div class="flex gap-1">
                <Button size="sm" variant="outline" @click="handleChangePriority(p, 'up')" title="Higher priority">↑</Button>
                <Button size="sm" variant="outline" @click="handleChangePriority(p, 'down')" title="Lower priority">↓</Button>
              </div>
              <Button
                v-if="p.requiresApiKey"
                size="sm"
                variant="outline"
                @click="openKeyDialog(p)"
              >
                {{ p.hasApiKey ? 'Change Key' : 'Set API Key' }}
              </Button>
              <Button
                size="sm"
                :variant="p.enabled ? 'outline' : 'default'"
                @click="handleToggleEnabled(p)"
              >
                {{ p.enabled ? 'Disable' : 'Enable' }}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- API key dialog -->
    <Dialog v-model:open="keyDialogOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ editingProvider?.displayName }} API Key</DialogTitle>
          <CardDescription>
            The key is encrypted before storage (AES-256-GCM).
            Leaving it blank will remove the existing key.
          </CardDescription>
        </DialogHeader>
        <div class="space-y-2">
          <Label for="apiKey">API Key</Label>
          <Input id="apiKey" v-model="apiKeyInput" type="password" placeholder="Paste your API key" />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="keyDialogOpen = false">Cancel</Button>
          <Button @click="handleSaveKey" :disabled="updateMutation.isPending.value">Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
