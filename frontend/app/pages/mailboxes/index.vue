<!--
  Mailbox Management Page
  - List connected mailboxes
  - Connect new mailbox (SMTP form)
  - Test / Delete existing mailboxes
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger, DialogDescription } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'

definePageMeta({
  middleware: 'auth',
})
useHead({ title: 'Mailboxes' })

const { data: mailboxes, isLoading } = useMailboxes()
const connectMutation = useConnectMailbox()
const testMutation = useTestMailbox()
const deleteMutation = useDeleteMailbox()
const deliverabilityMutation = useDeliverabilityCheck()

// ── Deliverability dialog state ──
const deliverabilityDialogOpen = ref(false)
const deliverabilityResult = ref<any>(null)
const deliverabilityDomain = ref('')

async function handleCheckDeliverability(email: string) {
  const domain = email.split('@')[1]
  if (!domain) return
  deliverabilityDomain.value = domain
  deliverabilityDialogOpen.value = true
  try {
    const result = await deliverabilityMutation.mutateAsync({ domain })
    deliverabilityResult.value = result
  } catch {
    toast.error('Failed to check deliverability')
    deliverabilityResult.value = null
  }
}

function deliverabilityBadgeVariant(status: string | null): 'default' | 'secondary' | 'destructive' | 'outline' {
  if (status === 'PASS') return 'default'
  if (status === 'NOT_FOUND' || status === 'FAIL') return 'destructive'
  return 'outline'
}

// ── Connect dialog state ──
const dialogOpen = ref(false)
const provider = ref<'SMTP' | 'GMAIL' | 'OUTLOOK'>('GMAIL')

const form = ref({
  name: '',
  email: '',
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  dailyLimit: 100,
})
const connectErrors = useFieldErrors()

// Touch-to-clear per field.
watch(() => form.value.name, v => { if (v.trim()) connectErrors.remove('name') })
watch(() => form.value.email, v => { if (v.trim()) connectErrors.remove('email') })
watch(() => form.value.smtpHost, v => { if (v.trim()) connectErrors.remove('smtpHost') })
watch(() => form.value.smtpPort, v => { if (v) connectErrors.remove('smtpPort') })
watch(() => form.value.smtpPassword, v => { if (v) connectErrors.remove('smtpPassword') })
watch(dialogOpen, (open) => { if (open) connectErrors.clear() })

// Presets — auto-fill host/port based on provider choice
function applyProviderPreset(p: 'SMTP' | 'GMAIL' | 'OUTLOOK') {
  provider.value = p
  if (p === 'GMAIL') {
    form.value.smtpHost = 'smtp.gmail.com'
    form.value.smtpPort = 587
  } else if (p === 'OUTLOOK') {
    form.value.smtpHost = 'smtp.office365.com'
    form.value.smtpPort = 587
  } else {
    form.value.smtpHost = ''
    form.value.smtpPort = 587
  }
}

async function handleConnect() {
  connectErrors.clear()
  if (!form.value.name.trim()) connectErrors.set('name', 'Name is required.')
  if (!form.value.email.trim()) connectErrors.set('email', 'From email is required.')
  if (!form.value.smtpHost.trim()) connectErrors.set('smtpHost', 'SMTP host is required.')
  if (!form.value.smtpPort) connectErrors.set('smtpPort', 'Port is required.')
  if (!form.value.smtpPassword) connectErrors.set('smtpPassword', 'Password is required.')
  if (Object.keys(connectErrors.map).length) return

  try {
    await connectMutation.mutateAsync({
      name: form.value.name,
      email: form.value.email,
      provider: provider.value,
      smtpHost: form.value.smtpHost,
      smtpPort: form.value.smtpPort,
      smtpUsername: form.value.smtpUsername || form.value.email,
      smtpPassword: form.value.smtpPassword,
      dailyLimit: form.value.dailyLimit,
    })
    toast.success('Mailbox connected successfully!')
    dialogOpen.value = false
    form.value = { name: '', email: '', smtpHost: '', smtpPort: 587, smtpUsername: '', smtpPassword: '', dailyLimit: 100 }
  } catch (error: any) {
    connectErrors.fromServerError(error, 'Failed to connect mailbox')
  }
}

async function handleTest(id: string) {
  try {
    const connected = await testMutation.mutateAsync(id)
    if (connected) {
      toast.success('Connection successful')
    } else {
      toast.error('Connection failed')
    }
  } catch {
    toast.error('Connection test failed')
  }
}

async function handleDelete(id: string, email: string) {
  const ok = await useConfirm().ask({
    title: `Remove mailbox "${email}"?`,
    description: "The mailbox will stop sending emails immediately.",
    confirmLabel: 'Remove',
    variant: 'destructive',
  })
  if (!ok) return
  try {
    await deleteMutation.mutateAsync(id)
    toast.success('Mailbox removed')
  } catch {
    toast.error('Failed to remove mailbox')
  }
}

function statusBadgeVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'ACTIVE': return 'default'
    case 'PAUSED': return 'secondary'
    case 'ERROR': return 'destructive'
    default: return 'outline'
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- Page header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold">Mailboxes</h1>
        <p class="text-sm text-muted-foreground">
          Connected sending accounts for outreach
        </p>
      </div>
      <Dialog v-model:open="dialogOpen">
        <DialogTrigger as-child>
          <Button>+ Connect Mailbox</Button>
        </DialogTrigger>
        <DialogContent class="max-w-md">
          <DialogHeader>
            <DialogTitle>Connect a Mailbox</DialogTitle>
            <DialogDescription>
              For Gmail: use an App Password (not your regular password).
              <a href="https://myaccount.google.com/apppasswords" target="_blank" class="underline">Generate one here</a>.
            </DialogDescription>
          </DialogHeader>

          <!-- Provider tabs -->
          <div class="grid grid-cols-3 gap-2">
            <Button
              :variant="provider === 'GMAIL' ? 'default' : 'outline'"
              size="sm"
              @click="applyProviderPreset('GMAIL')"
            >Gmail</Button>
            <Button
              :variant="provider === 'OUTLOOK' ? 'default' : 'outline'"
              size="sm"
              @click="applyProviderPreset('OUTLOOK')"
            >Outlook</Button>
            <Button
              :variant="provider === 'SMTP' ? 'default' : 'outline'"
              size="sm"
              @click="applyProviderPreset('SMTP')"
            >Custom SMTP</Button>
          </div>

          <div
            v-if="connectErrors.has('_form')"
            class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
          >
            {{ connectErrors.get('_form') }}
          </div>
          <div class="space-y-3">
            <div class="space-y-2">
              <Label for="name">Name *</Label>
              <Input
                id="name"
                v-model="form.name"
                placeholder="Sales Inbox"
                :class="connectErrors.has('name') ? 'border-destructive' : ''"
              />
              <SharedFormError :message="connectErrors.get('name')" />
            </div>
            <div class="space-y-2">
              <Label for="email">From Email *</Label>
              <Input
                id="email"
                v-model="form.email"
                type="email"
                placeholder="sales@company.com"
                :class="connectErrors.has('email') ? 'border-destructive' : ''"
              />
              <SharedFormError :message="connectErrors.get('email')" />
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div class="space-y-2">
                <Label for="smtpHost">SMTP Host *</Label>
                <Input
                  id="smtpHost"
                  v-model="form.smtpHost"
                  :class="connectErrors.has('smtpHost') ? 'border-destructive' : ''"
                />
                <SharedFormError :message="connectErrors.get('smtpHost')" />
              </div>
              <div class="space-y-2">
                <Label for="smtpPort">Port *</Label>
                <Input
                  id="smtpPort"
                  v-model.number="form.smtpPort"
                  type="number"
                  :class="connectErrors.has('smtpPort') ? 'border-destructive' : ''"
                />
                <SharedFormError :message="connectErrors.get('smtpPort')" />
              </div>
            </div>
            <div class="space-y-2">
              <Label for="smtpUsername">Username (defaults to email)</Label>
              <Input id="smtpUsername" v-model="form.smtpUsername" :placeholder="form.email" />
            </div>
            <div class="space-y-2">
              <Label for="smtpPassword">Password / App Password *</Label>
              <Input
                id="smtpPassword"
                v-model="form.smtpPassword"
                type="password"
                :class="connectErrors.has('smtpPassword') ? 'border-destructive' : ''"
              />
              <SharedFormError :message="connectErrors.get('smtpPassword')" />
            </div>
            <div class="space-y-2">
              <Label for="dailyLimit">Daily Send Limit</Label>
              <Input id="dailyLimit" v-model.number="form.dailyLimit" type="number" />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" @click="dialogOpen = false">Cancel</Button>
            <Button @click="handleConnect" :disabled="connectMutation.isPending.value">
              {{ connectMutation.isPending.value ? 'Testing & connecting...' : 'Connect' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <!-- Mailbox list -->
    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">
      Loading mailboxes...
    </div>

    <div v-else-if="!mailboxes?.length" class="text-center py-12">
      <p class="text-muted-foreground mb-4">
        No mailboxes connected yet. Connect one to start sending outreach emails.
      </p>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <Card v-for="mb in mailboxes" :key="mb.id">
        <CardHeader>
          <div class="flex items-start justify-between">
            <div>
              <CardTitle class="text-base">{{ mb.name }}</CardTitle>
              <CardDescription>{{ mb.email }}</CardDescription>
            </div>
            <Badge :variant="statusBadgeVariant(mb.status)">{{ mb.status }}</Badge>
          </div>
        </CardHeader>
        <CardContent class="space-y-3 text-sm">
          <div class="flex justify-between">
            <span class="text-muted-foreground">Provider</span>
            <span>{{ mb.provider }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-muted-foreground">SMTP</span>
            <span class="text-xs">{{ mb.smtpHost }}:{{ mb.smtpPort }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-muted-foreground">Today</span>
            <span>{{ mb.sendsToday }} / {{ mb.dailyLimit }}</span>
          </div>
          <div v-if="mb.lastError" class="text-xs text-destructive">
            {{ mb.lastError }}
          </div>
          <div class="grid grid-cols-3 gap-2 pt-2">
            <Button size="sm" variant="outline" @click="handleTest(mb.id)" :disabled="testMutation.isPending.value">
              Test
            </Button>
            <Button size="sm" variant="outline" @click="handleCheckDeliverability(mb.email)">
              DNS
            </Button>
            <Button size="sm" variant="outline" class="text-destructive" @click="handleDelete(mb.id, mb.email)">
              Remove
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Deliverability dialog (SPF/DKIM/DMARC check results) -->
    <Dialog v-model:open="deliverabilityDialogOpen">
      <DialogContent class="max-w-lg">
        <DialogHeader>
          <DialogTitle>DNS Deliverability — {{ deliverabilityDomain }}</DialogTitle>
          <DialogDescription>
            Sender authentication records that protect your deliverability.
            <a href="https://www.cloudflare.com/learning/dns/dns-records/dns-spf-record/" target="_blank" class="underline">
              Learn more
            </a>
          </DialogDescription>
        </DialogHeader>

        <div v-if="deliverabilityMutation.isPending.value" class="text-center py-8 text-muted-foreground">
          Checking DNS...
        </div>

        <div v-else-if="deliverabilityResult" class="space-y-4">
          <!-- SPF -->
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="font-medium">SPF</span>
              <Badge :variant="deliverabilityBadgeVariant(deliverabilityResult.spfStatus)">
                {{ deliverabilityResult.spfStatus ?? 'UNKNOWN' }}
              </Badge>
            </div>
            <div v-if="deliverabilityResult.spfRecord" class="text-xs font-mono bg-muted p-2 rounded break-all">
              {{ deliverabilityResult.spfRecord }}
            </div>
            <p v-else class="text-xs text-muted-foreground">
              No SPF record found. Add one to authorize sending servers.
            </p>
          </div>

          <!-- DKIM -->
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="font-medium">
                DKIM
                <span class="text-xs text-muted-foreground font-normal">(selector: {{ deliverabilityResult.dkimSelector }})</span>
              </span>
              <Badge :variant="deliverabilityBadgeVariant(deliverabilityResult.dkimStatus)">
                {{ deliverabilityResult.dkimStatus ?? 'UNKNOWN' }}
              </Badge>
            </div>
            <div v-if="deliverabilityResult.dkimRecord" class="text-xs font-mono bg-muted p-2 rounded break-all">
              {{ deliverabilityResult.dkimRecord.substring(0, 200) }}{{ deliverabilityResult.dkimRecord.length > 200 ? '...' : '' }}
            </div>
            <p v-else class="text-xs text-muted-foreground">
              No DKIM record found at selector. Different mail providers use different selectors.
            </p>
          </div>

          <!-- DMARC -->
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="font-medium">DMARC</span>
              <Badge :variant="deliverabilityBadgeVariant(deliverabilityResult.dmarcStatus)">
                {{ deliverabilityResult.dmarcStatus ?? 'UNKNOWN' }}
              </Badge>
            </div>
            <div v-if="deliverabilityResult.dmarcRecord" class="text-xs font-mono bg-muted p-2 rounded break-all">
              {{ deliverabilityResult.dmarcRecord }}
            </div>
            <p v-else class="text-xs text-muted-foreground">
              No DMARC record found. Add one to tell receivers what to do with unauthenticated mail.
            </p>
          </div>
        </div>

        <DialogFooter>
          <Button @click="deliverabilityDialogOpen = false">Close</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
