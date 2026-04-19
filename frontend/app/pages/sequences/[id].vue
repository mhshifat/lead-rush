<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Checkbox } from '~/components/ui/checkbox'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '~/components/ui/select'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger, DialogDescription,
} from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import {
  ArrowLeft, Plus, X, Play, Pause, RotateCw,
  Mail, Clock, Phone, ClipboardList, Workflow,
  UserCheck, CheckCircle2, Reply as ReplyIcon,
} from 'lucide-vue-next'

definePageMeta({
  middleware: 'auth',
})

const route = useRoute()
const sequenceId = computed(() => route.params.id as string)

const { data: sequence, isLoading } = useSequence(sequenceId)
const { data: templates } = useEmailTemplates()
const addStepMutation = useAddSequenceStep()
const deleteStepMutation = useDeleteSequenceStep()
const activateMutation = useActivateSequence()
const pauseMutation = usePauseSequence()

type StepType = 'EMAIL' | 'DELAY' | 'CALL' | 'TASK'

const stepDialogOpen = ref(false)
// Inline form errors keyed by field — rendered next to the input they belong to
// instead of as a toast (toasts sit behind the dialog overlay + users miss them).
const stepErrors = ref<Record<string, string>>({})
const newStep = ref({
  stepType: 'EMAIL' as StepType,
  delayDays: 0,
  emailTemplateId: '',
  subjectOverride: '',
  taskDescription: '',
  skipIfPreviousOpened: false,
  skipIfPreviousClicked: false,
})

// Reset errors whenever the user changes step type or touches any field.
watch(() => newStep.value.stepType, () => { stepErrors.value = {} })
watch(() => newStep.value.emailTemplateId, v => { if (v) delete stepErrors.value.emailTemplateId })
watch(() => newStep.value.taskDescription, v => { if (v?.trim()) delete stepErrors.value.taskDescription })

async function handleAddStep() {
  stepErrors.value = {}
  const s = newStep.value

  if (s.stepType === 'EMAIL' && !s.emailTemplateId) {
    stepErrors.value.emailTemplateId = 'Pick a template before adding this step.'
    return
  }
  if (s.stepType === 'DELAY' && (!s.delayDays || s.delayDays < 1)) {
    stepErrors.value.delayDays = 'Wait must be at least 1 day.'
    return
  }
  if ((s.stepType === 'CALL' || s.stepType === 'TASK') && !s.taskDescription.trim()) {
    stepErrors.value.taskDescription = 'Describe what the user should do.'
    return
  }

  try {
    await addStepMutation.mutateAsync({
      sequenceId: sequenceId.value,
      dto: {
        stepType: s.stepType,
        delayDays: s.delayDays,
        emailTemplateId: s.emailTemplateId || undefined,
        subjectOverride: s.subjectOverride || undefined,
        taskDescription: s.taskDescription || undefined,
        skipIfPreviousOpened: s.skipIfPreviousOpened,
        skipIfPreviousClicked: s.skipIfPreviousClicked,
      },
    })
    toast.success('Step added')
    stepDialogOpen.value = false
    newStep.value = {
      stepType: 'EMAIL', delayDays: 0, emailTemplateId: '', subjectOverride: '',
      taskDescription: '', skipIfPreviousOpened: false, skipIfPreviousClicked: false,
    }
    stepErrors.value = {}
  } catch (error: any) {
    // Server-side error — surface at the top of the dialog so it's always visible.
    stepErrors.value._form = error?.data?.error?.message || 'Failed to add step'
  }
}

async function handleDeleteStep(stepId: string) {
  const ok = await useConfirm().ask({
    title: 'Delete this step?',
    confirmLabel: 'Delete',
    variant: 'destructive',
  })
  if (!ok) return
  try {
    await deleteStepMutation.mutateAsync({ sequenceId: sequenceId.value, stepId })
    toast.success('Step deleted')
  } catch {
    toast.error('Failed to delete step')
  }
}

async function handleActivate() {
  try {
    await activateMutation.mutateAsync(sequenceId.value)
    toast.success('Sequence activated — contacts can now be enrolled')
  } catch (error: any) {
    toast.error(error?.data?.error?.message || 'Failed to activate')
  }
}

async function handlePause() {
  try {
    await pauseMutation.mutateAsync(sequenceId.value)
    toast.success('Sequence paused')
  } catch {
    toast.error('Failed to pause')
  }
}

function statusBadgeVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'ACTIVE': return 'default'
    case 'DRAFT': return 'secondary'
    case 'PAUSED': return 'outline'
    default: return 'secondary'
  }
}

function stepIconComponent(stepType: string) {
  switch (stepType) {
    case 'EMAIL': return Mail
    case 'DELAY': return Clock
    case 'CALL': return Phone
    case 'TASK': return ClipboardList
    default: return Workflow
  }
}

function stepAccentClass(stepType: string) {
  switch (stepType) {
    case 'EMAIL': return 'text-primary bg-primary/10'
    case 'DELAY': return 'text-amber-400 bg-amber-400/10'
    case 'CALL': return 'text-emerald-400 bg-emerald-400/10'
    case 'TASK': return 'text-sky-400 bg-sky-400/10'
    default: return 'text-muted-foreground bg-white/5'
  }
}
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Back link -->
    <NuxtLink
      to="/sequences"
      class="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
    >
      <ArrowLeft class="h-3.5 w-3.5" />
      Back to sequences
    </NuxtLink>

    <!-- Loading / not found -->
    <div v-if="isLoading" class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground">
      Loading sequence…
    </div>
    <div v-else-if="!sequence" class="glass hairline rounded-xl py-16 text-center text-sm text-destructive">
      Sequence not found
    </div>

    <div v-else class="space-y-5">
      <!-- Header -->
      <div class="glass hairline rounded-xl p-6">
        <div class="flex items-start justify-between gap-4 flex-wrap">
          <div class="min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <h1 class="text-xl font-semibold tracking-tight truncate">{{ sequence.name }}</h1>
              <Badge :variant="statusBadgeVariant(sequence.status)">{{ sequence.status }}</Badge>
            </div>
            <p v-if="sequence.description" class="text-sm text-muted-foreground mt-0.5">
              {{ sequence.description }}
            </p>
            <p v-if="sequence.defaultMailboxEmail" class="text-xs text-muted-foreground mt-1">
              Sending from <span class="text-foreground">{{ sequence.defaultMailboxEmail }}</span>
            </p>
          </div>

          <div class="flex gap-2 shrink-0">
            <Button
              v-if="sequence.status === 'DRAFT'"
              class="gap-1.5"
              :disabled="activateMutation.isPending.value || sequence.steps.length === 0"
              @click="handleActivate"
            >
              <Play class="h-3.5 w-3.5" />
              Activate
            </Button>
            <Button
              v-if="sequence.status === 'ACTIVE'"
              variant="outline"
              class="gap-1.5"
              :disabled="pauseMutation.isPending.value"
              @click="handlePause"
            >
              <Pause class="h-3.5 w-3.5" />
              Pause
            </Button>
            <Button
              v-if="sequence.status === 'PAUSED'"
              class="gap-1.5"
              :disabled="activateMutation.isPending.value"
              @click="handleActivate"
            >
              <RotateCw class="h-3.5 w-3.5" />
              Resume
            </Button>
          </div>
        </div>

        <!-- Metrics -->
        <div
          class="grid grid-cols-3 mt-5 pt-5"
          style="border-top: 1px solid hsl(240 5% 100% / 0.06);"
        >
          <div class="flex items-center gap-3">
            <div class="h-9 w-9 rounded-lg bg-sky-500/10 flex items-center justify-center">
              <UserCheck class="h-4 w-4 text-sky-400" />
            </div>
            <div>
              <p class="text-xs uppercase tracking-wider text-muted-foreground">Enrolled</p>
              <p class="text-xl font-semibold tracking-tight tabular-nums">{{ sequence.totalEnrolled }}</p>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <div class="h-9 w-9 rounded-lg bg-emerald-500/10 flex items-center justify-center">
              <CheckCircle2 class="h-4 w-4 text-emerald-400" />
            </div>
            <div>
              <p class="text-xs uppercase tracking-wider text-muted-foreground">Completed</p>
              <p class="text-xl font-semibold tracking-tight tabular-nums">{{ sequence.totalCompleted }}</p>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <div class="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
              <ReplyIcon class="h-4 w-4 text-primary" />
            </div>
            <div>
              <p class="text-xs uppercase tracking-wider text-muted-foreground">Replied</p>
              <p class="text-xl font-semibold tracking-tight tabular-nums">{{ sequence.totalReplied }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Steps -->
      <div class="glass hairline rounded-xl overflow-hidden">
        <div
          class="flex items-center justify-between px-6 py-4"
          style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);"
        >
          <div class="flex items-center gap-2">
            <Workflow class="h-4 w-4 text-muted-foreground" />
            <div>
              <h2 class="text-sm font-semibold tracking-tight">Steps</h2>
              <p class="text-xs text-muted-foreground">
                Runs in order. EMAIL sends the message, DELAY waits, CALL/TASK creates manual to-dos.
              </p>
            </div>
          </div>
          <Dialog v-model:open="stepDialogOpen">
            <DialogTrigger as-child>
              <Button
                size="sm"
                class="h-8 px-3 gap-1.5"
                :disabled="sequence.status === 'ACTIVE'"
              >
                <Plus class="h-3.5 w-3.5" />
                Add step
              </Button>
            </DialogTrigger>

            <DialogContent>
              <DialogHeader>
                <DialogTitle>Add step</DialogTitle>
                <DialogDescription>Steps execute top-to-bottom. Delay steps stall the sequence; email steps send a message.</DialogDescription>
              </DialogHeader>
              <div class="space-y-4">
                <!-- Top-level form error (from server) -->
                <div
                  v-if="stepErrors._form"
                  class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
                >
                  {{ stepErrors._form }}
                </div>

                <!-- Step type picker -->
                <div class="space-y-2">
                  <Label>Step type</Label>
                  <div class="grid grid-cols-4 gap-2">
                    <button
                      v-for="t in (['EMAIL', 'DELAY', 'CALL', 'TASK'] as const)"
                      :key="t"
                      type="button"
                      class="flex flex-col items-center gap-1 rounded-md border p-2 transition-colors"
                      :class="newStep.stepType === t
                        ? 'border-primary bg-primary/10 text-foreground'
                        : 'border-input text-muted-foreground hover:bg-white/5'"
                      @click="newStep.stepType = t"
                    >
                      <component :is="stepIconComponent(t)" class="h-4 w-4" />
                      <span class="text-xs">{{ t }}</span>
                    </button>
                  </div>
                </div>

                <!-- DELAY -->
                <div v-if="newStep.stepType === 'DELAY'" class="space-y-2">
                  <Label for="delayDays">Wait (days)</Label>
                  <Input
                    id="delayDays"
                    v-model.number="newStep.delayDays"
                    type="number"
                    min="1"
                    :class="stepErrors.delayDays ? 'border-destructive' : ''"
                  />
                  <p v-if="stepErrors.delayDays" class="text-xs text-destructive">
                    {{ stepErrors.delayDays }}
                  </p>
                </div>

                <!-- EMAIL -->
                <template v-if="newStep.stepType === 'EMAIL'">
                  <div class="space-y-2">
                    <Label for="stepDelay">Delay before sending <span class="text-muted-foreground font-normal">(days)</span></Label>
                    <Input id="stepDelay" v-model.number="newStep.delayDays" type="number" min="0" />
                  </div>
                  <div class="space-y-2">
                    <Label for="emailTemplate">Email template *</Label>
                    <Select v-model="newStep.emailTemplateId">
                      <SelectTrigger
                        id="emailTemplate"
                        class="w-full"
                        :class="stepErrors.emailTemplateId ? 'border-destructive' : ''"
                      >
                        <SelectValue placeholder="Select a template" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem v-for="t in templates" :key="t.id" :value="t.id">
                          {{ t.name }}
                        </SelectItem>
                      </SelectContent>
                    </Select>
                    <p v-if="stepErrors.emailTemplateId" class="text-xs text-destructive">
                      {{ stepErrors.emailTemplateId }}
                    </p>
                    <p
                      v-else-if="!templates?.length"
                      class="text-xs text-muted-foreground"
                    >
                      No templates yet —
                      <NuxtLink to="/templates" class="text-primary hover:underline">
                        create one first
                      </NuxtLink>.
                    </p>
                  </div>
                  <div class="space-y-2">
                    <Label for="subjectOverride">Subject override <span class="text-muted-foreground font-normal">(optional)</span></Label>
                    <Input id="subjectOverride" v-model="newStep.subjectOverride" placeholder="Leave blank to use the template's subject" />
                  </div>
                  <div class="rounded-md hairline p-3 space-y-2.5">
                    <p class="text-xs font-medium">Skip this email if the contact…</p>
                    <label class="flex items-center gap-2 text-sm cursor-pointer">
                      <Checkbox v-model="newStep.skipIfPreviousOpened" />
                      <span>opened an earlier email in this sequence</span>
                    </label>
                    <label class="flex items-center gap-2 text-sm cursor-pointer">
                      <Checkbox v-model="newStep.skipIfPreviousClicked" />
                      <span>clicked a link in an earlier email in this sequence</span>
                    </label>
                    <p class="text-xs text-muted-foreground">
                      Useful for follow-ups — stop pestering a lead who's already engaged.
                    </p>
                  </div>
                </template>

                <!-- CALL / TASK -->
                <template v-if="newStep.stepType === 'CALL' || newStep.stepType === 'TASK'">
                  <div class="space-y-2">
                    <Label for="delayForTask">Delay before task <span class="text-muted-foreground font-normal">(days)</span></Label>
                    <Input id="delayForTask" v-model.number="newStep.delayDays" type="number" min="0" />
                  </div>
                  <div class="space-y-2">
                    <Label for="taskDescription">Task description</Label>
                    <Input
                      id="taskDescription"
                      v-model="newStep.taskDescription"
                      placeholder="What should the user do?"
                      :class="stepErrors.taskDescription ? 'border-destructive' : ''"
                    />
                    <p v-if="stepErrors.taskDescription" class="text-xs text-destructive">
                      {{ stepErrors.taskDescription }}
                    </p>
                  </div>
                </template>
              </div>
              <DialogFooter>
                <Button variant="outline" @click="stepDialogOpen = false">Cancel</Button>
                <Button @click="handleAddStep" :disabled="addStepMutation.isPending.value">
                  {{ addStepMutation.isPending.value ? 'Adding…' : 'Add step' }}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>

        <!-- Empty state -->
        <div
          v-if="!sequence.steps.length"
          class="flex flex-col items-center justify-center py-14 px-6 text-center"
        >
          <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mb-4">
            <Workflow class="h-5 w-5 text-muted-foreground" />
          </div>
          <h3 class="text-sm font-semibold tracking-tight">No steps yet</h3>
          <p class="text-sm text-muted-foreground mt-1 max-w-sm">
            Add an EMAIL step to send the first message, then DELAY steps between follow-ups.
          </p>
          <Button
            class="mt-5 gap-1.5"
            :disabled="sequence.status === 'ACTIVE'"
            @click="stepDialogOpen = true"
          >
            <Plus class="h-4 w-4" />
            Add first step
          </Button>
        </div>

        <!-- Steps — vertical timeline -->
        <ol v-else class="px-6 py-5 space-y-5">
          <li
            v-for="(step, idx) in sequence.steps"
            :key="step.id"
            class="relative flex gap-4"
          >
            <div class="relative shrink-0">
              <span
                class="h-10 w-10 rounded-full flex items-center justify-center"
                :class="stepAccentClass(step.stepType)"
              >
                <component :is="stepIconComponent(step.stepType)" class="h-4 w-4" />
              </span>
              <span
                v-if="idx < sequence.steps.length - 1"
                class="absolute left-1/2 -translate-x-1/2 top-10 w-px h-[calc(100%+1.25rem)]"
                style="background: hsl(240 5% 100% / 0.08);"
              />
            </div>
            <div class="flex-1 min-w-0 pb-1">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="text-xs uppercase tracking-wider text-muted-foreground tabular-nums">
                      Step {{ step.stepOrder }}
                    </span>
                    <Badge variant="outline" class="text-xs">{{ step.stepType }}</Badge>
                    <span
                      v-if="step.delayDays > 0"
                      class="inline-flex items-center gap-1 text-xs text-muted-foreground"
                    >
                      <Clock class="h-3 w-3" />
                      Wait {{ step.delayDays }}d
                    </span>
                  </div>
                  <p class="mt-1 text-sm">
                    <template v-if="step.stepType === 'EMAIL'">
                      Template: <span class="font-medium">{{ step.emailTemplateName ?? '—' }}</span>
                      <span v-if="step.subjectOverride" class="text-muted-foreground">
                        · subject override: "{{ step.subjectOverride }}"
                      </span>
                    </template>
                    <template v-else-if="step.stepType === 'DELAY'">
                      <span class="text-muted-foreground">
                        Sequence pauses for {{ step.delayDays }} day{{ step.delayDays === 1 ? '' : 's' }} before the next step.
                      </span>
                    </template>
                    <template v-else>
                      <span class="text-muted-foreground">
                        {{ step.taskDescription ?? 'No description' }}
                      </span>
                    </template>
                  </p>
                  <div
                    v-if="step.skipIfPreviousOpened || step.skipIfPreviousClicked"
                    class="mt-2 flex gap-1 flex-wrap"
                  >
                    <Badge v-if="step.skipIfPreviousOpened" variant="secondary" class="text-xs">
                      Skip if previously opened
                    </Badge>
                    <Badge v-if="step.skipIfPreviousClicked" variant="secondary" class="text-xs">
                      Skip if previously clicked
                    </Badge>
                  </div>
                </div>
                <button
                  class="shrink-0 h-8 w-8 rounded-md flex items-center justify-center text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                  title="Delete step"
                  :disabled="sequence.status === 'ACTIVE'"
                  @click="handleDeleteStep(step.id)"
                >
                  <X class="h-4 w-4" />
                </button>
              </div>
            </div>
          </li>
        </ol>
      </div>
    </div>
  </div>
</template>
