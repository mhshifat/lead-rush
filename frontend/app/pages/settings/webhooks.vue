<!--
  Webhooks settings page.
  - List endpoints + status
  - Create / edit / delete
  - Rotate signing secret
  - Send a test ping
  - View delivery log per endpoint
-->
<script setup lang="ts">
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import type { WebhookEndpointApiDto, WebhookEndpointDto } from '~/types/api/webhook.dto'

definePageMeta({ middleware: 'auth' })

const { data: endpoints, isLoading } = useWebhooks()
const { data: eventTypes } = useWebhookEventTypes()

const createMutation = useCreateWebhook()
const updateMutation = useUpdateWebhook()
const deleteMutation = useDeleteWebhook()
const rotateMutation = useRotateWebhookSecret()
const testMutation = useTestWebhook()

// ── Dialog state ──
const formOpen = ref(false)
const editingId = ref<string | null>(null)
const form = ref<WebhookEndpointDto>({
  url: '',
  description: '',
  events: ['*'],
  enabled: true,
})

const revealOpen = ref(false)
const revealedSecret = ref('')

// Deliveries panel
const selectedId = ref<string | null>(null)
const selectedIdRef = computed(() => selectedId.value)
const { data: deliveries } = useWebhookDeliveries(selectedIdRef)

function openCreate() {
  editingId.value = null
  form.value = { url: '', description: '', events: ['*'], enabled: true }
  formOpen.value = true
}

function openEdit(endpoint: WebhookEndpointApiDto) {
  editingId.value = endpoint.id
  form.value = {
    url: endpoint.url,
    description: endpoint.description ?? '',
    events: endpoint.events.length ? [...endpoint.events] : ['*'],
    enabled: endpoint.enabled,
  }
  formOpen.value = true
}

function toggleEvent(topic: string) {
  const list = form.value.events ?? []
  if (topic === '*') {
    form.value.events = list.includes('*') ? [] : ['*']
    return
  }
  if (list.includes('*')) {
    form.value.events = [topic]
    return
  }
  form.value.events = list.includes(topic) ? list.filter(e => e !== topic) : [...list, topic]
}

function isSelected(topic: string): boolean {
  const list = form.value.events ?? []
  return list.includes('*') ? topic === '*' : list.includes(topic)
}

async function handleSave() {
  if (!form.value.url?.trim()) { toast.error('URL is required'); return }
  if (!(form.value.events?.length)) { toast.error('Pick at least one event'); return }
  try {
    const dto: WebhookEndpointDto = {
      url: form.value.url.trim(),
      description: form.value.description?.trim() || undefined,
      events: form.value.events,
      enabled: form.value.enabled,
    }
    if (editingId.value) {
      await updateMutation.mutateAsync({ id: editingId.value, dto })
      toast.success('Webhook updated')
    } else {
      const created = await createMutation.mutateAsync(dto)
      revealedSecret.value = created.secret ?? ''
      revealOpen.value = true
    }
    formOpen.value = false
  } catch (error: any) {
    toast.error(error?.data?.error?.message ?? 'Failed to save')
  }
}

async function handleDelete(endpoint: WebhookEndpointApiDto) {
  if (!confirm(`Delete webhook for ${endpoint.url}?`)) return
  try {
    await deleteMutation.mutateAsync(endpoint.id)
    toast.success('Deleted')
    if (selectedId.value === endpoint.id) selectedId.value = null
  } catch {
    toast.error('Failed to delete')
  }
}

async function handleRotate(endpoint: WebhookEndpointApiDto) {
  if (!confirm('Rotate the signing secret? The old secret will stop working immediately.')) return
  try {
    const updated = await rotateMutation.mutateAsync(endpoint.id)
    revealedSecret.value = updated.secret ?? ''
    revealOpen.value = true
  } catch {
    toast.error('Failed to rotate secret')
  }
}

async function handleTest(endpoint: WebhookEndpointApiDto) {
  try {
    await testMutation.mutateAsync(endpoint.id)
    toast.success('Test ping queued — check deliveries in a few seconds')
    selectedId.value = endpoint.id
  } catch (error: any) {
    toast.error(error?.data?.error?.message ?? 'Failed to send test')
  }
}

async function copySecret() {
  await navigator.clipboard.writeText(revealedSecret.value)
  toast.success('Copied to clipboard')
}

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat('en-US', {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso))
}

function deliveryVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'SUCCEEDED': return 'default'
    case 'PENDING':
    case 'IN_PROGRESS':
    case 'FAILED': return 'secondary'
    case 'ABANDONED': return 'destructive'
    default: return 'outline'
  }
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-start justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Webhooks</h1>
        <p class="text-sm text-muted-foreground">
          Subscribe your own systems to Lead Rush events. Deliveries are retried with exponential backoff
          and signed with HMAC-SHA256.
        </p>
      </div>
      <Button @click="openCreate">+ New webhook</Button>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-5 gap-6">
      <!-- Endpoint list -->
      <div class="lg:col-span-2 space-y-3">
        <Card>
          <CardContent class="pt-6">
            <div v-if="isLoading" class="text-sm text-muted-foreground py-6 text-center">Loading…</div>
            <div v-else-if="!endpoints?.length" class="text-sm text-muted-foreground py-10 text-center">
              No webhooks yet.
            </div>
            <ul v-else class="space-y-2">
              <li
                v-for="e in endpoints"
                :key="e.id"
                class="rounded-md hairline p-3 cursor-pointer transition-colors"
                :class="selectedId === e.id ? 'bg-white/5' : 'hover:bg-white/5'"
                @click="selectedId = e.id"
              >
                <div class="flex items-start justify-between gap-2">
                  <div class="min-w-0 flex-1">
                    <p class="text-sm font-medium truncate">{{ e.url }}</p>
                    <p v-if="e.description" class="text-xs text-muted-foreground truncate">{{ e.description }}</p>
                    <div class="flex flex-wrap gap-1 mt-2">
                      <Badge v-if="!e.enabled" variant="destructive" class="text-xs">Disabled</Badge>
                      <Badge v-for="ev in e.events" :key="ev" variant="secondary" class="text-xs font-mono">
                        {{ ev }}
                      </Badge>
                    </div>
                    <p v-if="e.consecutiveFailures > 0" class="text-xs text-destructive mt-1">
                      {{ e.consecutiveFailures }} consecutive failure{{ e.consecutiveFailures === 1 ? '' : 's' }}
                    </p>
                  </div>
                </div>

                <div class="flex gap-1 mt-3" @click.stop>
                  <Button size="sm" variant="outline" class="flex-1" @click="handleTest(e)">Test</Button>
                  <Button size="sm" variant="outline" class="flex-1" @click="openEdit(e)">Edit</Button>
                  <Button size="sm" variant="outline" @click="handleRotate(e)" title="Rotate secret">↻</Button>
                  <Button size="sm" variant="outline" class="text-destructive" @click="handleDelete(e)">✕</Button>
                </div>
              </li>
            </ul>
          </CardContent>
        </Card>
      </div>

      <!-- Deliveries log -->
      <div class="lg:col-span-3">
        <Card>
          <CardHeader>
            <CardTitle>Delivery log</CardTitle>
            <CardDescription>
              {{ selectedId ? 'Most recent attempts' : 'Select a webhook to see its delivery history' }}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div v-if="!selectedId" class="text-sm text-muted-foreground py-10 text-center">
              Pick an endpoint on the left to see its deliveries.
            </div>
            <div v-else-if="!deliveries?.content.length" class="text-sm text-muted-foreground py-10 text-center">
              No deliveries yet. Send a test ping to try it out.
            </div>
            <ul v-else class="space-y-2">
              <li v-for="d in deliveries.content" :key="d.id" class="rounded-md hairline p-3">
                <div class="flex items-center justify-between gap-2 mb-1">
                  <div class="flex items-center gap-2 min-w-0">
                    <Badge :variant="deliveryVariant(d.status)" class="text-xs">{{ d.status }}</Badge>
                    <span class="text-sm font-mono truncate">{{ d.eventType }}</span>
                  </div>
                  <span class="text-xs text-muted-foreground shrink-0">{{ formatDate(d.createdAt) }}</span>
                </div>
                <div class="text-xs text-muted-foreground flex flex-wrap gap-x-3 gap-y-0.5">
                  <span>Attempt {{ d.attemptCount }}</span>
                  <span v-if="d.lastStatusCode !== null">HTTP {{ d.lastStatusCode }}</span>
                  <span v-if="d.deliveredAt">Delivered {{ formatDate(d.deliveredAt) }}</span>
                  <span v-else-if="d.nextAttemptAt && d.status !== 'ABANDONED'">
                    Next retry {{ formatDate(d.nextAttemptAt) }}
                  </span>
                </div>
                <div v-if="d.lastError" class="text-xs text-destructive mt-2 font-mono bg-destructive/5 p-2 rounded">
                  {{ d.lastError }}
                </div>
              </li>
            </ul>
          </CardContent>
        </Card>
      </div>
    </div>

    <!-- Signature verification hint -->
    <Card>
      <CardHeader>
        <CardTitle class="text-base">Verifying signatures</CardTitle>
      </CardHeader>
      <CardContent class="text-sm space-y-2 text-muted-foreground">
        <p>Every delivery carries these headers:</p>
        <ul class="font-mono text-xs space-y-1 pl-4">
          <li>X-LeadRush-Signature: t=&lt;unix-seconds&gt;,v1=&lt;hex-hmac-sha256&gt;</li>
          <li>X-LeadRush-Event: &lt;event topic&gt;</li>
          <li>X-LeadRush-Event-Id: &lt;uuid — dedupe on retries&gt;</li>
          <li>X-LeadRush-Delivery: &lt;uuid — unique per attempt&gt;</li>
        </ul>
        <p>
          Compute <code class="bg-white/5 px-1">HMAC-SHA256(secret, `${'{t}'}.${'{rawBody}'}`)</code>
          and compare to <code class="bg-white/5 px-1">v1</code>. Reject stale timestamps (e.g., &gt; 5 min old).
        </p>
      </CardContent>
    </Card>

    <!-- Create/Edit dialog -->
    <Dialog v-model:open="formOpen">
      <DialogContent class="max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ editingId ? 'Edit webhook' : 'New webhook' }}</DialogTitle>
        </DialogHeader>
        <div class="space-y-4">
          <div class="space-y-2">
            <Label for="url">Endpoint URL *</Label>
            <Input id="url" v-model="form.url" placeholder="https://api.yourapp.com/webhooks/leadrush" />
          </div>
          <div class="space-y-2">
            <Label for="desc">Description</Label>
            <Input id="desc" v-model="form.description" placeholder="Zapier — new contact → Slack" />
          </div>

          <div class="space-y-2">
            <Label>Events</Label>
            <div class="flex flex-wrap gap-1.5">
              <button
                type="button"
                class="px-2.5 py-1 rounded-full text-xs font-mono transition-colors"
                :class="isSelected('*') ? 'bg-primary text-primary-foreground' : 'hairline hover:bg-white/5'"
                @click="toggleEvent('*')"
              >* (all events)</button>
              <button
                v-for="topic in eventTypes"
                :key="topic"
                type="button"
                class="px-2.5 py-1 rounded-full text-xs font-mono transition-colors"
                :class="isSelected(topic) ? 'bg-primary text-primary-foreground' : 'hairline hover:bg-white/5'"
                @click="toggleEvent(topic)"
                :disabled="isSelected('*')"
              >{{ topic }}</button>
            </div>
          </div>

          <label class="flex items-center gap-2 text-sm">
            <input type="checkbox" v-model="form.enabled" class="h-4 w-4" />
            <span>Enabled</span>
          </label>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="formOpen = false">Cancel</Button>
          <Button :disabled="createMutation.isPending.value || updateMutation.isPending.value" @click="handleSave">
            {{ editingId ? 'Update' : 'Create' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Reveal dialog — plaintext secret shown once -->
    <Dialog v-model:open="revealOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Signing secret</DialogTitle>
          <CardDescription>
            Copy this now — you won't see it again. Store it server-side and use it to verify the HMAC
            signature on every incoming delivery.
          </CardDescription>
        </DialogHeader>
        <div class="rounded-md hairline bg-white/5 p-3 font-mono text-sm break-all">
          {{ revealedSecret }}
        </div>
        <DialogFooter>
          <Button variant="outline" @click="revealOpen = false">Close</Button>
          <Button @click="copySecret">Copy</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
