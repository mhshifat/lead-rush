<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger, DialogDescription } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import {
  ArrowLeft, Sparkles, UserPlus, Trash2, TrendingUp,
  Mail, Phone, Globe, Linkedin, Twitter,
  AtSign, Activity, Briefcase, History, Clock,
  Send, Eye, MousePointerClick, Reply, Target, CheckCircle2,
  XCircle, ClipboardList, Check,
} from 'lucide-vue-next'

definePageMeta({
  middleware: 'auth',
})

const route = useRoute()
const contactId = computed(() => route.params.id as string)

const { data: contact, isLoading, isError } = useContact(contactId)
const { data: enrollments } = useContactEnrollments(contactId)
const { data: timeline } = useContactTimeline(contactId)
const { data: deals } = useContactDeals(contactId)
const { data: sequences } = useSequences()
const { data: mailboxes } = useMailboxes()

const deleteMutation = useDeleteContact()
const enrollMutation = useEnrollContact()
const enrichMutation = useEnrichContact()

const { data: scoreHistory } = useLeadScoreHistory(contactId)
const adjustScoreMutation = useAdjustScore()

const adjustDialogOpen = ref(false)
const adjustPoints = ref<number>(10)
const adjustReason = ref('')

async function handleAdjustScore() {
  if (!contact.value) return
  if (!adjustPoints.value) {
    toast.error('Enter a points delta')
    return
  }
  try {
    await adjustScoreMutation.mutateAsync({
      contactId: contact.value.id,
      dto: { pointsDelta: Number(adjustPoints.value), reason: adjustReason.value || undefined },
    })
    toast.success('Score adjusted')
    adjustDialogOpen.value = false
    adjustPoints.value = 10
    adjustReason.value = ''
  } catch {
    toast.error('Failed to adjust score')
  }
}

async function handleEnrich() {
  if (!contact.value) return
  try {
    const result = await enrichMutation.mutateAsync(contact.value.id)
    if (!result) {
      toast.error('No enrichment providers are enabled. Configure them in Settings → Enrichment.')
    } else if (result.status === 'SUCCESS') {
      toast.success(`Enriched via ${result.providerKey}`)
    } else if (result.status === 'NOT_FOUND') {
      toast.message('No data found for this contact')
    } else {
      toast.error(`Enrichment ${result.status.toLowerCase()}: ${result.errorMessage ?? ''}`)
    }
  } catch (error: any) {
    toast.error(error?.data?.error?.message || 'Enrichment failed')
  }
}

const enrollDialogOpen = ref(false)
const selectedSequenceId = ref('')
const selectedMailboxId = ref('')

const enrollableSequences = computed(() =>
  (sequences.value ?? []).filter(s => s.status === 'ACTIVE')
)

const enrollableSequencesFiltered = computed(() => {
  const enrolledIds = new Set(
    (enrollments.value ?? [])
      .filter(e => e.status === 'ACTIVE' || e.status === 'PAUSED')
      .map(e => e.sequenceId)
  )
  return enrollableSequences.value.filter(s => !enrolledIds.has(s.id))
})

async function handleEnroll() {
  if (!selectedSequenceId.value) {
    toast.error('Please select a sequence')
    return
  }
  try {
    await enrollMutation.mutateAsync({
      sequenceId: selectedSequenceId.value,
      dto: {
        contactId: contactId.value,
        mailboxId: selectedMailboxId.value || undefined,
      },
    })
    toast.success('Contact enrolled! First email will be sent within 60 seconds.')
    enrollDialogOpen.value = false
    selectedSequenceId.value = ''
    selectedMailboxId.value = ''
  } catch (error: any) {
    toast.error(error?.data?.error?.message || 'Failed to enroll contact')
  }
}

async function handleDelete() {
  if (!contact.value) return
  const ok = await useConfirm().ask({
    title: `Delete ${contact.value.fullName}?`,
    description: 'This cannot be undone.',
    confirmLabel: 'Delete',
    variant: 'destructive',
  })
  if (!ok) return
  try {
    await deleteMutation.mutateAsync(contact.value.id)
    toast.success('Contact deleted')
    navigateTo('/contacts')
  } catch (error: any) {
    toast.error(error?.data?.error?.message || 'Failed to delete contact')
  }
}

function stageBadgeVariant(stage: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (stage) {
    case 'LEAD': return 'secondary'
    case 'LOST': return 'destructive'
    case 'CONTACTED': return 'outline'
    default: return 'default'
  }
}

function enrollmentStatusVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'ACTIVE':
    case 'COMPLETED':
    case 'REPLIED': return 'default'
    case 'PAUSED': return 'secondary'
    case 'FAILED':
    case 'BOUNCED':
    case 'UNSUBSCRIBED': return 'destructive'
    default: return 'outline'
  }
}

function formatDate(date: Date | null): string {
  if (!date) return '—'
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }).format(date)
}

function formatDateString(iso: string): string {
  return formatDate(new Date(iso))
}

function formatCurrency(amount: number | null, currency: string | null): string {
  if (amount === null || amount === undefined) return '—'
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency || 'USD',
      maximumFractionDigits: 0,
    }).format(amount)
  } catch {
    return `${amount}`
  }
}

function eventIconComponent(type: string) {
  switch (type) {
    case 'EMAIL_SENT': return Send
    case 'EMAIL_OPENED': return Eye
    case 'EMAIL_CLICKED': return MousePointerClick
    case 'EMAIL_REPLIED': return Reply
    case 'ENROLLED': return Target
    case 'SEQUENCE_COMPLETED': return CheckCircle2
    case 'UNSUBSCRIBED': return XCircle
    case 'TASK_CREATED': return ClipboardList
    case 'TASK_COMPLETED': return Check
    default: return Activity
  }
}
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Back link -->
    <NuxtLink
      to="/contacts"
      class="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
    >
      <ArrowLeft class="h-3.5 w-3.5" />
      Back to contacts
    </NuxtLink>

    <!-- Loading -->
    <div v-if="isLoading" class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground">
      Loading contact…
    </div>

    <!-- Error -->
    <div v-else-if="isError || !contact" class="glass hairline rounded-xl py-16 text-center text-sm text-destructive">
      Contact not found
    </div>

    <div v-else class="space-y-5">
      <!-- Header -->
      <div class="glass hairline rounded-xl p-6">
        <div class="flex items-start justify-between gap-4 flex-wrap">
          <div class="flex items-center gap-4 min-w-0">
            <div
              class="h-14 w-14 shrink-0 rounded-full flex items-center justify-center text-lg font-semibold text-white"
              style="background: linear-gradient(135deg, hsl(240 4% 95% / 0.08), hsl(var(--primary) / 0.35));"
            >
              {{ contact.firstName.charAt(0) }}{{ contact.lastName?.charAt(0) ?? '' }}
            </div>
            <div class="min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <h1 class="text-xl font-semibold tracking-tight truncate">{{ contact.fullName }}</h1>
                <Badge :variant="stageBadgeVariant(contact.lifecycleStage)">
                  {{ contact.lifecycleStage }}
                </Badge>
              </div>
              <p class="text-sm text-muted-foreground truncate mt-0.5">
                <span v-if="contact.title">{{ contact.title }}</span>
                <span v-if="contact.title && contact.companyName"> · </span>
                <span v-if="contact.companyName">{{ contact.companyName }}</span>
                <span v-if="!contact.title && !contact.companyName">—</span>
              </p>
            </div>
          </div>

          <div class="flex items-center gap-2 flex-wrap">
            <Button
              size="sm" variant="outline"
              class="h-9 px-3 gap-1.5"
              @click="handleEnrich"
              :disabled="enrichMutation.isPending.value"
            >
              <Sparkles class="h-3.5 w-3.5" />
              {{ enrichMutation.isPending.value ? 'Enriching…' : 'Enrich' }}
            </Button>

            <Dialog v-model:open="enrollDialogOpen">
              <DialogTrigger as-child>
                <Button size="sm" class="h-9 px-3 gap-1.5" :disabled="!contact.primaryEmail">
                  <UserPlus class="h-3.5 w-3.5" />
                  Enroll
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Enroll {{ contact.fullName }}</DialogTitle>
                  <DialogDescription>
                    Pick a sequence and the mailbox to send from. First email ships within 60 seconds.
                  </DialogDescription>
                </DialogHeader>

                <div class="space-y-4">
                  <div v-if="!contact.primaryEmail" class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
                    This contact has no email address. Add one before enrolling.
                  </div>

                  <div class="space-y-2">
                    <Label for="sequence">Sequence</Label>
                    <select
                      id="sequence"
                      v-model="selectedSequenceId"
                      class="w-full rounded-md bg-background px-3 py-2 text-sm border border-input focus:outline-none focus:ring-2 focus:ring-ring"
                    >
                      <option value="">Select a sequence</option>
                      <option
                        v-for="seq in enrollableSequencesFiltered"
                        :key="seq.id"
                        :value="seq.id"
                      >
                        {{ seq.name }} ({{ seq.steps.length }} steps)
                      </option>
                    </select>
                    <p v-if="!enrollableSequencesFiltered.length" class="text-xs text-muted-foreground">
                      No available sequences. Create and activate one, or this contact may already be enrolled everywhere.
                    </p>
                  </div>

                  <div class="space-y-2">
                    <Label for="mailbox">Mailbox <span class="text-muted-foreground font-normal">(optional)</span></Label>
                    <select
                      id="mailbox"
                      v-model="selectedMailboxId"
                      class="w-full rounded-md bg-background px-3 py-2 text-sm border border-input focus:outline-none focus:ring-2 focus:ring-ring"
                    >
                      <option value="">Use sequence default</option>
                      <option v-for="mb in mailboxes" :key="mb.id" :value="mb.id">
                        {{ mb.name }} ({{ mb.email }})
                      </option>
                    </select>
                  </div>
                </div>

                <DialogFooter>
                  <Button variant="outline" @click="enrollDialogOpen = false">Cancel</Button>
                  <Button
                    @click="handleEnroll"
                    :disabled="!selectedSequenceId || enrollMutation.isPending.value"
                  >
                    {{ enrollMutation.isPending.value ? 'Enrolling…' : 'Enroll' }}
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>

            <Button
              variant="outline"
              size="sm"
              class="h-9 px-3 gap-1.5 text-destructive hover:text-destructive hover:bg-destructive/10"
              @click="handleDelete"
              :disabled="deleteMutation.isPending.value"
            >
              <Trash2 class="h-3.5 w-3.5" />
              Delete
            </Button>
          </div>
        </div>

        <div v-if="contact.tags.length > 0" class="flex flex-wrap gap-1.5 mt-5">
          <Badge v-for="tag in contact.tags" :key="tag.id" variant="secondary">
            {{ tag.name }}
          </Badge>
        </div>
      </div>

      <!-- Contact info + Details grid -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
        <!-- Contact info -->
        <div class="glass hairline rounded-xl p-6">
          <div class="flex items-center gap-2 mb-4">
            <AtSign class="h-4 w-4 text-muted-foreground" />
            <h2 class="text-sm font-semibold tracking-tight">Contact information</h2>
          </div>

          <div class="space-y-4 text-sm">
            <div v-if="contact.emails.length">
              <p class="text-xs uppercase tracking-wider text-muted-foreground mb-2">Email</p>
              <ul class="space-y-1.5">
                <li v-for="email in contact.emails" :key="email.id" class="flex items-center gap-2 flex-wrap">
                  <Mail class="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                  <span>{{ email.email }}</span>
                  <Badge v-if="email.isPrimary" variant="outline" class="text-xs">Primary</Badge>
                  <Badge variant="secondary" class="text-xs">{{ email.emailType }}</Badge>
                </li>
              </ul>
            </div>

            <div v-if="contact.phones.length">
              <p class="text-xs uppercase tracking-wider text-muted-foreground mb-2">Phone</p>
              <ul class="space-y-1.5">
                <li v-for="phone in contact.phones" :key="phone.id" class="flex items-center gap-2 flex-wrap">
                  <Phone class="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                  <span>{{ phone.phone }}</span>
                  <Badge v-if="phone.isPrimary" variant="outline" class="text-xs">Primary</Badge>
                  <Badge variant="secondary" class="text-xs">{{ phone.phoneType }}</Badge>
                </li>
              </ul>
            </div>

            <div v-if="contact.website || contact.linkedinUrl || contact.twitterUrl">
              <p class="text-xs uppercase tracking-wider text-muted-foreground mb-2">Links</p>
              <ul class="space-y-1.5">
                <li v-if="contact.website">
                  <a :href="contact.website" target="_blank" class="inline-flex items-center gap-2 text-primary hover:underline">
                    <Globe class="h-3.5 w-3.5" />
                    Website
                  </a>
                </li>
                <li v-if="contact.linkedinUrl">
                  <a :href="contact.linkedinUrl" target="_blank" class="inline-flex items-center gap-2 text-primary hover:underline">
                    <Linkedin class="h-3.5 w-3.5" />
                    LinkedIn
                  </a>
                </li>
                <li v-if="contact.twitterUrl">
                  <a :href="contact.twitterUrl" target="_blank" class="inline-flex items-center gap-2 text-primary hover:underline">
                    <Twitter class="h-3.5 w-3.5" />
                    Twitter
                  </a>
                </li>
              </ul>
            </div>

            <div v-if="!contact.emails.length && !contact.phones.length && !contact.website && !contact.linkedinUrl && !contact.twitterUrl" class="text-muted-foreground text-sm">
              No contact info yet.
            </div>
          </div>
        </div>

        <!-- Details -->
        <div class="glass hairline rounded-xl p-6">
          <div class="flex items-center gap-2 mb-4">
            <TrendingUp class="h-4 w-4 text-muted-foreground" />
            <h2 class="text-sm font-semibold tracking-tight">Details</h2>
          </div>

          <dl class="space-y-3 text-sm">
            <div class="flex items-center justify-between">
              <dt class="text-muted-foreground">Lead score</dt>
              <dd class="flex items-center gap-2">
                <span class="font-semibold text-base tabular-nums">{{ contact.leadScore }}</span>
                <Button size="sm" variant="outline" class="h-7 text-xs px-2" @click="adjustDialogOpen = true">
                  Adjust
                </Button>
              </dd>
            </div>
            <div class="flex items-center justify-between">
              <dt class="text-muted-foreground">Source</dt>
              <dd>{{ contact.source ?? '—' }}</dd>
            </div>
            <div class="flex items-center justify-between">
              <dt class="text-muted-foreground">Last contacted</dt>
              <dd>{{ formatDate(contact.lastContactedAt) }}</dd>
            </div>
            <div class="flex items-center justify-between">
              <dt class="text-muted-foreground">Created</dt>
              <dd>{{ formatDate(contact.createdAt) }}</dd>
            </div>
            <div class="flex items-center justify-between">
              <dt class="text-muted-foreground">Updated</dt>
              <dd>{{ formatDate(contact.updatedAt) }}</dd>
            </div>
          </dl>
        </div>
      </div>

      <!-- Sequence enrollments -->
      <div class="glass hairline rounded-xl overflow-hidden">
        <div class="flex items-center gap-2 px-6 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <Activity class="h-4 w-4 text-muted-foreground" />
          <h2 class="text-sm font-semibold tracking-tight">Sequence enrollments</h2>
        </div>
        <div v-if="!enrollments?.length" class="px-6 py-10 text-center text-sm text-muted-foreground">
          Not enrolled in any sequences yet.
        </div>
        <ul v-else>
          <li
            v-for="enrollment in enrollments"
            :key="enrollment.id"
            class="px-6 py-3 cursor-pointer hover:bg-white/5 transition-colors"
            style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
            @click="navigateTo(`/sequences/${enrollment.sequenceId}`)"
          >
            <div class="flex items-center gap-2 mb-1">
              <span class="font-medium text-sm">{{ enrollment.sequenceName }}</span>
              <Badge :variant="enrollmentStatusVariant(enrollment.status)">
                {{ enrollment.status }}
              </Badge>
            </div>
            <div class="text-xs text-muted-foreground flex flex-wrap gap-x-3 gap-y-1">
              <span>Step {{ enrollment.currentStepIndex + 1 }}</span>
              <span v-if="enrollment.mailboxEmail">via {{ enrollment.mailboxEmail }}</span>
              <span>Enrolled {{ formatDate(enrollment.enrolledAt) }}</span>
              <span v-if="enrollment.nextExecutionAt">
                Next: {{ formatDate(enrollment.nextExecutionAt) }}
              </span>
            </div>
          </li>
        </ul>
      </div>

      <!-- Deals -->
      <div class="glass hairline rounded-xl overflow-hidden">
        <div class="flex items-center gap-2 px-6 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <Briefcase class="h-4 w-4 text-muted-foreground" />
          <h2 class="text-sm font-semibold tracking-tight">Deals</h2>
        </div>
        <div v-if="!deals?.length" class="px-6 py-10 text-center text-sm text-muted-foreground">
          No deals linked to this contact yet.
        </div>
        <ul v-else>
          <li
            v-for="deal in deals"
            :key="deal.id"
            class="px-6 py-3 cursor-pointer hover:bg-white/5 transition-colors"
            style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
            @click="navigateTo(`/pipelines/${deal.pipelineId}`)"
          >
            <div class="flex items-center gap-2 mb-1">
              <span class="font-medium text-sm truncate">{{ deal.name }}</span>
              <Badge variant="outline">{{ deal.stageName }}</Badge>
            </div>
            <div class="text-xs text-muted-foreground flex flex-wrap gap-x-3 gap-y-1">
              <span v-if="deal.valueAmount" class="font-medium text-primary tabular-nums">
                {{ formatCurrency(deal.valueAmount, deal.valueCurrency) }}
              </span>
              <span v-if="deal.expectedCloseAt">
                Expected close: {{ formatDate(deal.expectedCloseAt) }}
              </span>
            </div>
          </li>
        </ul>
      </div>

      <!-- Lead score history -->
      <div class="glass hairline rounded-xl overflow-hidden">
        <div class="flex items-center gap-2 px-6 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <History class="h-4 w-4 text-muted-foreground" />
          <h2 class="text-sm font-semibold tracking-tight">Lead score history</h2>
        </div>
        <div v-if="!scoreHistory?.length" class="px-6 py-10 text-center text-sm text-muted-foreground">
          No score changes yet. Scores update when rules fire or you adjust manually.
        </div>
        <ul v-else>
          <li
            v-for="log in scoreHistory"
            :key="log.id"
            class="px-6 py-3 flex items-start justify-between gap-4"
            style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
          >
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1">
                <Badge :variant="log.pointsDelta >= 0 ? 'secondary' : 'destructive'" class="font-mono tabular-nums">
                  {{ log.pointsDelta >= 0 ? '+' : '' }}{{ log.pointsDelta }}
                </Badge>
                <span class="font-medium text-sm">
                  {{ log.ruleName ?? 'Manual adjustment' }}
                </span>
              </div>
              <div class="text-xs text-muted-foreground">
                <span v-if="log.reason">{{ log.reason }} · </span>
                <span class="tabular-nums">{{ log.scoreBefore }} → {{ log.scoreAfter }}</span>
              </div>
            </div>
            <time class="text-xs text-muted-foreground whitespace-nowrap">
              {{ formatDate(log.createdAt) }}
            </time>
          </li>
        </ul>
      </div>

      <!-- Adjust score dialog -->
      <Dialog v-model:open="adjustDialogOpen">
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Adjust score</DialogTitle>
            <DialogDescription>
              Add or subtract points. Use a negative number to deduct.
            </DialogDescription>
          </DialogHeader>
          <div class="space-y-4">
            <div class="space-y-2">
              <Label for="adjustPoints">Points</Label>
              <Input
                id="adjustPoints"
                v-model.number="adjustPoints"
                type="number"
              />
            </div>
            <div class="space-y-2">
              <Label for="adjustReason">Reason</Label>
              <Input
                id="adjustReason"
                v-model="adjustReason"
                type="text"
                placeholder="e.g. attended webinar"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="adjustDialogOpen = false">Cancel</Button>
            <Button @click="handleAdjustScore" :disabled="adjustScoreMutation.isPending.value">
              {{ adjustScoreMutation.isPending.value ? 'Applying…' : 'Apply' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <!-- Activity timeline -->
      <div class="glass hairline rounded-xl overflow-hidden">
        <div class="flex items-center gap-2 px-6 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <Clock class="h-4 w-4 text-muted-foreground" />
          <h2 class="text-sm font-semibold tracking-tight">Activity timeline</h2>
        </div>
        <div v-if="!timeline?.length" class="px-6 py-10 text-center text-sm text-muted-foreground">
          No activity yet. Emails sent, opened, and clicked will appear here.
        </div>
        <ol v-else class="px-6 py-5 space-y-5">
          <li
            v-for="(event, idx) in timeline"
            :key="event.id + event.type + event.occurredAt"
            class="relative flex gap-4"
          >
            <div class="relative shrink-0">
              <span class="h-8 w-8 rounded-full bg-white/5 flex items-center justify-center">
                <component :is="eventIconComponent(event.type)" class="h-4 w-4 text-muted-foreground" />
              </span>
              <span
                v-if="idx < timeline.length - 1"
                class="absolute left-1/2 -translate-x-1/2 top-8 w-px h-[calc(100%+1.25rem)]"
                style="background: hsl(240 5% 100% / 0.08);"
              />
            </div>
            <div class="flex-1 min-w-0 pb-1">
              <div class="flex items-start justify-between gap-4">
                <p class="text-sm font-medium">{{ event.title }}</p>
                <time class="text-xs text-muted-foreground whitespace-nowrap">
                  {{ formatDateString(event.occurredAt) }}
                </time>
              </div>
              <p v-if="event.description" class="text-xs text-muted-foreground mt-0.5">
                {{ event.description }}
              </p>
            </div>
          </li>
        </ol>
      </div>
    </div>
  </div>
</template>
