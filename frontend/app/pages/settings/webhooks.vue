<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Checkbox } from '~/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription,
} from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import {
  Plus, Webhook, Send, Pencil, Trash2, RefreshCw, Activity, Inbox,
  Shield, AlertTriangle, Copy, AlertCircle,
} from 'lucide-vue-next'
import type { WebhookEndpointApiDto, WebhookEndpointDto } from '~/types/api/webhook.dto'

definePageMeta({ middleware: 'auth' })
useHead({ title: 'Webhooks' })

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
const errors = useFieldErrors()

watch(() => form.value.url, v => { if (v?.trim()) errors.remove('url') })
watch(() => form.value.events, v => { if (v?.length) errors.remove('events') }, { deep: true })

const revealOpen = ref(false)
const revealedSecret = ref('')

// Deliveries panel
const selectedId = ref<string | null>(null)
const selectedIdRef = computed(() => selectedId.value)
const { data: deliveries } = useWebhookDeliveries(selectedIdRef)

function openCreate() {
  editingId.value = null
  form.value = { url: '', description: '', events: ['*'], enabled: true }
  errors.clear()
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
  errors.clear()
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
  errors.clear()
  if (!form.value.url?.trim()) errors.set('url', 'URL is required.')
  if (!(form.value.events?.length)) errors.set('events', 'Pick at least one event.')
  if (Object.keys(errors.map).length) return
  try {
    const dto: WebhookEndpointDto = {
      url: form.value.url!.trim(),
      description: form.value.description?.trim() || undefined,
      events: form.value.events!,
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
    errors.fromServerError(error, 'Failed to save')
  }
}

async function handleDelete(endpoint: WebhookEndpointApiDto) {
  const ok = await useConfirm().ask({
    title: `Delete webhook for ${endpoint.url}?`,
    confirmLabel: 'Delete',
    variant: 'destructive',
  })
  if (!ok) return
  try {
    await deleteMutation.mutateAsync(endpoint.id)
    toast.success('Deleted')
    if (selectedId.value === endpoint.id) selectedId.value = null
  } catch {
    toast.error('Failed to delete')
  }
}

async function handleRotate(endpoint: WebhookEndpointApiDto) {
  const ok = await useConfirm().ask({
    title: 'Rotate the signing secret?',
    description: 'The old secret will stop working immediately. Update your receiving service with the new secret right after.',
    confirmLabel: 'Rotate',
  })
  if (!ok) return
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

const totals = computed(() => {
  const list = endpoints.value ?? []
  const enabled = list.filter(e => e.enabled).length
  const failing = list.filter(e => e.consecutiveFailures > 0).length
  return { total: list.length, enabled, failing }
})

const selectedEndpoint = computed(() => endpoints.value?.find(e => e.id === selectedId.value) ?? null)
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Header -->
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Webhooks</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          Subscribe your systems to Lead Rush events — retried with exponential backoff, signed with HMAC-SHA256.
        </p>
      </div>
      <Button class="gap-1.5" @click="openCreate">
        <Plus class="h-4 w-4" />
        New webhook
      </Button>
    </div>

    <!-- Summary strip -->
    <div
      v-if="endpoints?.length"
      class="glass hairline rounded-xl grid grid-cols-3 divide-x divide-white/5"
    >
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Endpoints</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.total }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Enabled</p>
        <p class="mt-1 text-xl font-semibold tabular-nums text-emerald-400">{{ totals.enabled }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Failing</p>
        <p class="mt-1 text-xl font-semibold tabular-nums" :class="totals.failing > 0 ? 'text-destructive' : ''">
          {{ totals.failing }}
        </p>
      </div>
    </div>

    <!-- Two-pane layout -->
    <div class="grid grid-cols-1 lg:grid-cols-5 gap-4">
      <!-- ── Endpoint list ── -->
      <div class="lg:col-span-2 glass hairline rounded-xl overflow-hidden flex flex-col min-h-0">
        <div class="px-5 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <div class="flex items-start gap-3">
            <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <Webhook class="h-4 w-4 text-primary" />
            </div>
            <div class="min-w-0">
              <h3 class="text-sm font-semibold tracking-tight">Endpoints</h3>
              <p class="text-xs text-muted-foreground mt-0.5">
                Click an endpoint to view its delivery log.
              </p>
            </div>
          </div>
        </div>

        <div v-if="isLoading" class="p-6 text-sm text-muted-foreground text-center">Loading…</div>

        <div
          v-else-if="!endpoints?.length"
          class="p-10 text-center"
        >
          <div class="h-10 w-10 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-3">
            <Webhook class="h-4 w-4 text-muted-foreground" />
          </div>
          <p class="text-sm font-medium">No webhooks yet</p>
          <p class="text-xs text-muted-foreground mt-1">
            Add one to start receiving real-time events.
          </p>
        </div>

        <ul v-else>
          <li
            v-for="(e, idx) in endpoints"
            :key="e.id"
            class="px-4 py-3 cursor-pointer transition-colors"
            :class="[
              selectedId === e.id ? 'bg-primary/5' : 'hover:bg-white/2',
              !e.enabled ? 'opacity-60' : '',
            ]"
            :style="idx > 0 ? 'border-top: 1px solid hsl(240 5% 100% / 0.05);' : ''"
            @click="selectedId = e.id"
          >
            <div class="flex items-start gap-3">
              <div class="relative h-8 w-8 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
                <Webhook class="h-3.5 w-3.5 text-primary" />
                <span
                  class="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full ring-2 ring-background"
                  :class="!e.enabled
                    ? 'bg-muted-foreground/50'
                    : e.consecutiveFailures > 0 ? 'bg-destructive' : 'bg-emerald-400'"
                />
              </div>

              <div class="min-w-0 flex-1">
                <p class="text-sm font-medium font-mono truncate">{{ e.url }}</p>
                <p v-if="e.description" class="text-xs text-muted-foreground truncate mt-0.5">
                  {{ e.description }}
                </p>
                <div class="flex flex-wrap gap-1 mt-2">
                  <Badge v-if="!e.enabled" variant="destructive" class="text-[10px] h-4 px-1.5">Disabled</Badge>
                  <Badge
                    v-for="ev in e.events.slice(0, 3)"
                    :key="ev"
                    variant="secondary"
                    class="text-[10px] font-mono h-4 px-1.5"
                  >{{ ev }}</Badge>
                  <Badge
                    v-if="e.events.length > 3"
                    variant="outline"
                    class="text-[10px] h-4 px-1.5"
                  >+{{ e.events.length - 3 }}</Badge>
                </div>
                <p
                  v-if="e.consecutiveFailures > 0"
                  class="inline-flex items-center gap-1 text-xs text-destructive mt-1.5"
                >
                  <AlertTriangle class="h-3 w-3" />
                  {{ e.consecutiveFailures }} consecutive failure{{ e.consecutiveFailures === 1 ? '' : 's' }}
                </p>
              </div>
            </div>

            <div class="flex gap-1 mt-3" @click.stop>
              <Button size="sm" variant="outline" class="flex-1 h-7 gap-1 text-xs" @click="handleTest(e)">
                <Send class="h-3 w-3" />
                Test
              </Button>
              <Button size="sm" variant="outline" class="flex-1 h-7 gap-1 text-xs" @click="openEdit(e)">
                <Pencil class="h-3 w-3" />
                Edit
              </Button>
              <Button size="sm" variant="outline" class="h-7 w-7 p-0" title="Rotate secret" @click="handleRotate(e)">
                <RefreshCw class="h-3 w-3" />
              </Button>
              <Button
                size="sm"
                variant="outline"
                class="h-7 w-7 p-0 text-muted-foreground hover:text-destructive"
                title="Delete"
                @click="handleDelete(e)"
              >
                <Trash2 class="h-3 w-3" />
              </Button>
            </div>
          </li>
        </ul>
      </div>

      <!-- ── Deliveries log ── -->
      <div class="lg:col-span-3 glass hairline rounded-xl overflow-hidden flex flex-col min-h-0">
        <div class="px-5 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <div class="flex items-start gap-3">
            <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <Activity class="h-4 w-4 text-primary" />
            </div>
            <div class="min-w-0 flex-1">
              <h3 class="text-sm font-semibold tracking-tight">Delivery log</h3>
              <p class="text-xs text-muted-foreground mt-0.5 truncate">
                <template v-if="selectedEndpoint">
                  Most recent attempts for <span class="font-mono">{{ selectedEndpoint.url }}</span>
                </template>
                <template v-else>
                  Select an endpoint on the left to see its delivery history.
                </template>
              </p>
            </div>
          </div>
        </div>

        <div v-if="!selectedId" class="flex-1 flex flex-col items-center justify-center text-center p-10 min-h-65">
          <div class="h-10 w-10 rounded-full bg-white/5 flex items-center justify-center mb-3">
            <Inbox class="h-4 w-4 text-muted-foreground" />
          </div>
          <p class="text-sm font-medium">Pick an endpoint</p>
          <p class="text-xs text-muted-foreground mt-1 max-w-xs">
            Choose a webhook from the list and we'll show every attempt — successes, retries, and failures.
          </p>
        </div>

        <div
          v-else-if="!deliveries?.content.length"
          class="flex-1 flex flex-col items-center justify-center text-center p-10 min-h-65"
        >
          <div class="h-10 w-10 rounded-full bg-white/5 flex items-center justify-center mb-3">
            <Send class="h-4 w-4 text-muted-foreground" />
          </div>
          <p class="text-sm font-medium">No deliveries yet</p>
          <p class="text-xs text-muted-foreground mt-1">
            Send a test ping from the endpoint to try it out.
          </p>
        </div>

        <ul v-else class="overflow-y-auto">
          <li
            v-for="(d, idx) in deliveries.content"
            :key="d.id"
            class="px-5 py-3 transition-colors hover:bg-white/2"
            :style="idx > 0 ? 'border-top: 1px solid hsl(240 5% 100% / 0.05);' : ''"
          >
            <div class="flex items-center justify-between gap-2 mb-1">
              <div class="flex items-center gap-2 min-w-0">
                <Badge :variant="deliveryVariant(d.status)" class="text-xs">{{ d.status }}</Badge>
                <span class="text-sm font-mono truncate">{{ d.eventType }}</span>
              </div>
              <span class="text-xs text-muted-foreground shrink-0 font-mono tabular-nums">{{ formatDate(d.createdAt) }}</span>
            </div>
            <div class="text-xs text-muted-foreground flex flex-wrap gap-x-3 gap-y-0.5">
              <span class="tabular-nums">Attempt {{ d.attemptCount }}</span>
              <span v-if="d.lastStatusCode !== null" class="font-mono tabular-nums">HTTP {{ d.lastStatusCode }}</span>
              <span v-if="d.deliveredAt">Delivered {{ formatDate(d.deliveredAt) }}</span>
              <span v-else-if="d.nextAttemptAt && d.status !== 'ABANDONED'">
                Next retry {{ formatDate(d.nextAttemptAt) }}
              </span>
            </div>
            <div
              v-if="d.lastError"
              class="text-xs text-destructive mt-2 font-mono bg-destructive/10 hairline border-destructive/20 p-2 rounded wrap-break-word"
            >
              {{ d.lastError }}
            </div>
          </li>
        </ul>
      </div>
    </div>

    <!-- ── Signature verification reference ── -->
    <div class="glass hairline rounded-xl overflow-hidden">
      <div class="px-5 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
        <div class="flex items-start gap-3">
          <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
            <Shield class="h-4 w-4 text-primary" />
          </div>
          <div class="min-w-0">
            <h3 class="text-sm font-semibold tracking-tight">Verifying signatures</h3>
            <p class="text-xs text-muted-foreground mt-0.5">
              Verify every incoming delivery on your side to confirm it's really from Lead Rush.
            </p>
          </div>
        </div>
      </div>
      <div class="p-5 text-sm space-y-3 text-muted-foreground">
        <p>Every delivery carries these headers:</p>
        <div class="rounded-md hairline bg-white/2 p-3 font-mono text-xs space-y-1 wrap-break-word">
          <div><span class="text-foreground">X-LeadRush-Signature:</span> t=&lt;unix-seconds&gt;,v1=&lt;hex-hmac-sha256&gt;</div>
          <div><span class="text-foreground">X-LeadRush-Event:</span> &lt;event topic&gt;</div>
          <div><span class="text-foreground">X-LeadRush-Event-Id:</span> &lt;uuid — dedupe on retries&gt;</div>
          <div><span class="text-foreground">X-LeadRush-Delivery:</span> &lt;uuid — unique per attempt&gt;</div>
        </div>
        <p>
          Compute <code class="bg-white/5 px-1.5 py-0.5 rounded font-mono text-foreground">HMAC-SHA256(secret, `${'{t}'}.${'{rawBody}'}`)</code>
          and compare to <code class="bg-white/5 px-1.5 py-0.5 rounded font-mono text-foreground">v1</code>.
          Reject stale timestamps (e.g. older than 5 minutes).
        </p>
      </div>
    </div>

    <!-- Create / edit dialog -->
    <Dialog v-model:open="formOpen">
      <DialogContent class="max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ editingId ? 'Edit webhook' : 'New webhook' }}</DialogTitle>
          <DialogDescription>
            Receive a POST request to your endpoint whenever the chosen events fire.
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-4">
          <div
            v-if="errors.has('_form')"
            class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
          >
            {{ errors.get('_form') }}
          </div>
          <div class="space-y-2">
            <Label for="url">Endpoint URL *</Label>
            <Input
              id="url"
              v-model="form.url"
              placeholder="https://api.yourapp.com/webhooks/leadrush"
              :class="errors.has('url') ? 'border-destructive' : ''"
            />
            <SharedFormError :message="errors.get('url')" />
          </div>
          <div class="space-y-2">
            <Label for="desc">Description <span class="text-muted-foreground font-normal">(optional)</span></Label>
            <Input id="desc" v-model="form.description" placeholder="e.g. Zapier — new contact → Slack" />
          </div>

          <div class="space-y-2">
            <Label>Events</Label>
            <div
              class="flex flex-wrap gap-1.5 rounded-md p-2"
              :class="errors.has('events') ? 'border border-destructive' : 'hairline bg-white/2'"
            >
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
                :class="[
                  isSelected(topic) ? 'bg-primary text-primary-foreground' : 'hairline hover:bg-white/5',
                  isSelected('*') ? 'opacity-40 cursor-not-allowed' : '',
                ]"
                :disabled="isSelected('*')"
                @click="toggleEvent(topic)"
              >{{ topic }}</button>
            </div>
            <SharedFormError :message="errors.get('events')" />
          </div>

          <label class="flex items-center gap-2 cursor-pointer select-none">
            <Checkbox :model-value="form.enabled" @update:model-value="(v) => form.enabled = v === true" />
            <span class="text-sm">Enabled — fire on events</span>
          </label>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="formOpen = false">Cancel</Button>
          <Button
            :disabled="createMutation.isPending.value || updateMutation.isPending.value"
            @click="handleSave"
          >
            {{ createMutation.isPending.value || updateMutation.isPending.value
              ? 'Saving…'
              : editingId ? 'Update' : 'Create' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Reveal dialog — plaintext secret shown once -->
    <Dialog v-model:open="revealOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Signing secret</DialogTitle>
          <DialogDescription>
            Copy this now — you won't see it again. Store it server-side and use it to verify the HMAC signature on every incoming delivery.
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-3">
          <div class="rounded-md hairline bg-white/5 p-3 font-mono text-sm wrap-break-word">
            {{ revealedSecret }}
          </div>
          <div class="flex items-start gap-2 rounded-md bg-amber-400/10 hairline border-amber-400/20 p-3 text-xs text-amber-200">
            <AlertCircle class="h-3.5 w-3.5 mt-0.5 shrink-0" />
            <span>
              This is the only time the full secret will be shown. If you lose it, rotate the secret to generate a new one.
            </span>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="revealOpen = false">Close</Button>
          <Button class="gap-1.5" @click="copySecret">
            <Copy class="h-3.5 w-3.5" />
            Copy
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
