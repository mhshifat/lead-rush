<!--
  Contact Detail Page — shows full contact info + enrollments + enroll button.

  This is where the outreach loop completes:
    Contact detail → "Enroll in Sequence" button → pick ACTIVE sequence → enroll
    → SequenceExecutionJob runs every 60s → first email gets sent → contact
    eventually receives the full sequence based on delay_days between steps.
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger, DialogDescription } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'

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

// ── Score adjustment dialog ──
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

// ── Enrich button ──
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

// ── Enroll dialog state ──
const enrollDialogOpen = ref(false)
const selectedSequenceId = ref('')
const selectedMailboxId = ref('')

// Only show ACTIVE sequences (DRAFT can't accept enrollments, PAUSED is stopped)
const enrollableSequences = computed(() =>
  (sequences.value ?? []).filter(s => s.status === 'ACTIVE')
)

// Exclude sequences the contact is already actively enrolled in
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
  if (!confirm(`Delete ${contact.value.fullName}? This cannot be undone.`)) return

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

function eventIcon(type: string): string {
  switch (type) {
    case 'EMAIL_SENT': return '📤'
    case 'EMAIL_OPENED': return '👁️'
    case 'EMAIL_CLICKED': return '🔗'
    case 'EMAIL_REPLIED': return '↩️'
    case 'ENROLLED': return '🎯'
    case 'SEQUENCE_COMPLETED': return '✅'
    case 'UNSUBSCRIBED': return '🚫'
    case 'TASK_CREATED': return '📋'
    case 'TASK_COMPLETED': return '✔️'
    default: return '•'
  }
}
</script>

<template>
  <div class="space-y-6">
    <NuxtLink to="/contacts" class="text-sm text-muted-foreground hover:text-foreground">
      ← Back to contacts
    </NuxtLink>

    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">
      Loading contact...
    </div>

    <div v-else-if="isError || !contact" class="text-center py-8 text-destructive">
      Contact not found
    </div>

    <div v-else class="space-y-6">
      <!-- Header card -->
      <Card>
        <CardContent class="pt-6">
          <div class="flex items-start justify-between">
            <div class="flex items-center gap-4">
              <div class="h-16 w-16 rounded-full bg-muted flex items-center justify-center text-2xl font-bold text-muted-foreground">
                {{ contact.firstName.charAt(0) }}{{ contact.lastName?.charAt(0) ?? '' }}
              </div>
              <div>
                <h1 class="text-2xl font-bold">{{ contact.fullName }}</h1>
                <p v-if="contact.title" class="text-muted-foreground">{{ contact.title }}</p>
                <p v-if="contact.companyName" class="text-sm text-muted-foreground">
                  at {{ contact.companyName }}
                </p>
              </div>
            </div>

            <div class="flex items-center gap-2">
              <Badge :variant="stageBadgeVariant(contact.lifecycleStage)">
                {{ contact.lifecycleStage }}
              </Badge>

              <!-- ENRICH BUTTON -->
              <Button size="sm" variant="outline" @click="handleEnrich" :disabled="enrichMutation.isPending.value">
                {{ enrichMutation.isPending.value ? 'Enriching...' : '✨ Enrich' }}
              </Button>

              <!-- ENROLL IN SEQUENCE BUTTON -->
              <Dialog v-model:open="enrollDialogOpen">
                <DialogTrigger as-child>
                  <Button size="sm" :disabled="!contact.primaryEmail">
                    Enroll in Sequence
                  </Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Enroll {{ contact.fullName }}</DialogTitle>
                    <DialogDescription>
                      Pick a sequence and the mailbox to send from. The first email will be sent within 60 seconds.
                    </DialogDescription>
                  </DialogHeader>

                  <div class="space-y-4">
                    <div v-if="!contact.primaryEmail" class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
                      This contact has no email address. Add one before enrolling.
                    </div>

                    <div class="space-y-2">
                      <Label for="sequence">Sequence *</Label>
                      <select
                        id="sequence"
                        v-model="selectedSequenceId"
                        class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
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
                        No available sequences. Create and activate a sequence first, or this contact is already enrolled in all active sequences.
                      </p>
                    </div>

                    <div class="space-y-2">
                      <Label for="mailbox">Mailbox (optional — uses sequence default)</Label>
                      <select
                        id="mailbox"
                        v-model="selectedMailboxId"
                        class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
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
                      {{ enrollMutation.isPending.value ? 'Enrolling...' : 'Enroll' }}
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>

              <Button variant="destructive" size="sm" @click="handleDelete" :disabled="deleteMutation.isPending.value">
                Delete
              </Button>
            </div>
          </div>

          <!-- Tags -->
          <div v-if="contact.tags.length > 0" class="flex flex-wrap gap-2 mt-4">
            <Badge v-for="tag in contact.tags" :key="tag.id" variant="secondary">
              {{ tag.name }}
            </Badge>
          </div>
        </CardContent>
      </Card>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <!-- Contact info -->
        <Card>
          <CardHeader>
            <CardTitle class="text-base">Contact Information</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4 text-sm">
            <div v-if="contact.emails.length > 0">
              <div class="font-medium mb-1">Email Addresses</div>
              <div v-for="email in contact.emails" :key="email.id" class="flex items-center gap-2">
                <span>{{ email.email }}</span>
                <Badge v-if="email.isPrimary" variant="outline" class="text-xs">Primary</Badge>
                <Badge variant="secondary" class="text-xs">{{ email.emailType }}</Badge>
              </div>
            </div>

            <div v-if="contact.phones.length > 0">
              <div class="font-medium mb-1">Phone Numbers</div>
              <div v-for="phone in contact.phones" :key="phone.id" class="flex items-center gap-2">
                <span>{{ phone.phone }}</span>
                <Badge v-if="phone.isPrimary" variant="outline" class="text-xs">Primary</Badge>
                <Badge variant="secondary" class="text-xs">{{ phone.phoneType }}</Badge>
              </div>
            </div>

            <div v-if="contact.website || contact.linkedinUrl || contact.twitterUrl" class="space-y-1">
              <div class="font-medium">Links</div>
              <a v-if="contact.website" :href="contact.website" target="_blank" class="block text-primary hover:underline">
                Website
              </a>
              <a v-if="contact.linkedinUrl" :href="contact.linkedinUrl" target="_blank" class="block text-primary hover:underline">
                LinkedIn
              </a>
              <a v-if="contact.twitterUrl" :href="contact.twitterUrl" target="_blank" class="block text-primary hover:underline">
                Twitter
              </a>
            </div>
          </CardContent>
        </Card>

        <!-- Metadata -->
        <Card>
          <CardHeader>
            <CardTitle class="text-base">Details</CardTitle>
          </CardHeader>
          <CardContent class="space-y-3 text-sm">
            <div class="flex items-center justify-between">
              <span class="text-muted-foreground">Lead Score</span>
              <div class="flex items-center gap-2">
                <span class="font-medium text-base">{{ contact.leadScore }}</span>
                <Button size="sm" variant="outline" class="h-6 text-xs px-2" @click="adjustDialogOpen = true">
                  Adjust
                </Button>
              </div>
            </div>
            <div class="flex justify-between">
              <span class="text-muted-foreground">Source</span>
              <span>{{ contact.source ?? '—' }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-muted-foreground">Last Contacted</span>
              <span>{{ formatDate(contact.lastContactedAt) }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-muted-foreground">Created</span>
              <span>{{ formatDate(contact.createdAt) }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-muted-foreground">Updated</span>
              <span>{{ formatDate(contact.updatedAt) }}</span>
            </div>
          </CardContent>
        </Card>
      </div>

      <!-- SEQUENCE ENROLLMENTS -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Sequence Enrollments</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="!enrollments?.length" class="text-sm text-muted-foreground py-4">
            This contact is not enrolled in any sequences yet.
          </div>
          <ul v-else class="space-y-2">
            <li
              v-for="enrollment in enrollments"
              :key="enrollment.id"
              class="flex items-center justify-between p-3 rounded-md border bg-card hover:border-primary cursor-pointer"
              @click="navigateTo(`/sequences/${enrollment.sequenceId}`)"
            >
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 mb-1">
                  <span class="font-medium">{{ enrollment.sequenceName }}</span>
                  <Badge :variant="enrollmentStatusVariant(enrollment.status)">
                    {{ enrollment.status }}
                  </Badge>
                </div>
                <div class="text-xs text-muted-foreground space-x-3">
                  <span>Step {{ enrollment.currentStepIndex + 1 }}</span>
                  <span v-if="enrollment.mailboxEmail">via {{ enrollment.mailboxEmail }}</span>
                  <span>Enrolled {{ formatDate(enrollment.enrolledAt) }}</span>
                  <span v-if="enrollment.nextExecutionAt">
                    • Next: {{ formatDate(enrollment.nextExecutionAt) }}
                  </span>
                </div>
              </div>
            </li>
          </ul>
        </CardContent>
      </Card>

      <!-- DEALS -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Deals</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="!deals?.length" class="text-sm text-muted-foreground py-4">
            No deals linked to this contact yet.
          </div>
          <ul v-else class="space-y-2">
            <li
              v-for="deal in deals"
              :key="deal.id"
              class="flex items-center justify-between p-3 rounded-md border bg-card hover:border-primary cursor-pointer"
              @click="navigateTo(`/pipelines/${deal.pipelineId}`)"
            >
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <span class="font-medium truncate">{{ deal.name }}</span>
                  <Badge variant="outline">{{ deal.stageName }}</Badge>
                </div>
                <div class="text-xs text-muted-foreground mt-1 space-x-3">
                  <span v-if="deal.valueAmount" class="font-medium text-primary">
                    {{ formatCurrency(deal.valueAmount, deal.valueCurrency) }}
                  </span>
                  <span v-if="deal.expectedCloseAt">
                    Expected close: {{ formatDate(deal.expectedCloseAt) }}
                  </span>
                </div>
              </div>
            </li>
          </ul>
        </CardContent>
      </Card>

      <!-- LEAD SCORE HISTORY -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Lead Score History</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="!scoreHistory?.length" class="text-sm text-muted-foreground py-4">
            No score changes yet. Scores update when scoring rules fire or you adjust manually.
          </div>
          <ul v-else class="space-y-2">
            <li
              v-for="log in scoreHistory"
              :key="log.id"
              class="flex items-center justify-between p-3 rounded-md border bg-card"
            >
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 mb-1">
                  <Badge :variant="log.pointsDelta >= 0 ? 'secondary' : 'destructive'" class="font-mono">
                    {{ log.pointsDelta >= 0 ? '+' : '' }}{{ log.pointsDelta }}
                  </Badge>
                  <span class="font-medium text-sm">
                    {{ log.ruleName ?? 'Manual adjustment' }}
                  </span>
                </div>
                <div class="text-xs text-muted-foreground">
                  {{ log.reason }}
                  <span class="ml-2">• {{ log.scoreBefore }} → {{ log.scoreAfter }}</span>
                </div>
              </div>
              <time class="text-xs text-muted-foreground whitespace-nowrap ml-4">
                {{ formatDate(log.createdAt) }}
              </time>
            </li>
          </ul>
        </CardContent>
      </Card>

      <!-- Adjust score dialog -->
      <Dialog v-model:open="adjustDialogOpen">
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Adjust score manually</DialogTitle>
            <DialogDescription>
              Add or subtract points. Use negative numbers to deduct.
            </DialogDescription>
          </DialogHeader>
          <div class="space-y-3">
            <div class="space-y-2">
              <Label for="adjustPoints">Points delta *</Label>
              <input
                id="adjustPoints"
                v-model.number="adjustPoints"
                type="number"
                class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm"
              />
            </div>
            <div class="space-y-2">
              <Label for="adjustReason">Reason</Label>
              <input
                id="adjustReason"
                v-model="adjustReason"
                type="text"
                placeholder="e.g., attended webinar"
                class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="adjustDialogOpen = false">Cancel</Button>
            <Button @click="handleAdjustScore" :disabled="adjustScoreMutation.isPending.value">
              Apply
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <!-- ACTIVITY TIMELINE -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Activity Timeline</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="!timeline?.length" class="text-sm text-muted-foreground py-4">
            No activity yet. Activity will appear here as emails are sent, opened, and clicked.
          </div>
          <ol v-else class="relative border-l border-muted ml-3 space-y-4 py-2">
            <li v-for="event in timeline" :key="event.id + event.type + event.occurredAt" class="ml-6">
              <span class="absolute -left-3 flex items-center justify-center w-6 h-6 bg-card border rounded-full">
                <span class="text-xs">{{ eventIcon(event.type) }}</span>
              </span>
              <div class="flex items-start justify-between gap-4">
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium">{{ event.title }}</p>
                  <p v-if="event.description" class="text-xs text-muted-foreground">
                    {{ event.description }}
                  </p>
                </div>
                <time class="text-xs text-muted-foreground whitespace-nowrap">
                  {{ formatDateString(event.occurredAt) }}
                </time>
              </div>
            </li>
          </ol>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
