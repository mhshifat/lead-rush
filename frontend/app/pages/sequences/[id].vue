<!--
  Sequence Detail Page — the sequence builder.
  - Shows list of steps in order
  - Add new step (EMAIL or DELAY)
  - Delete step
  - Activate/pause sequence
  - Enroll contacts
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'

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
const enrollMutation = useEnrollContact()

// ── Add step dialog ──
const stepDialogOpen = ref(false)
const newStep = ref({
  stepType: 'EMAIL' as 'EMAIL' | 'DELAY' | 'CALL' | 'TASK',
  delayDays: 0,
  emailTemplateId: '',
  subjectOverride: '',
  taskDescription: '',
  skipIfPreviousOpened: false,
  skipIfPreviousClicked: false,
})

async function handleAddStep() {
  if (newStep.value.stepType === 'EMAIL' && !newStep.value.emailTemplateId) {
    toast.error('Please select an email template')
    return
  }

  try {
    await addStepMutation.mutateAsync({
      sequenceId: sequenceId.value,
      dto: {
        stepType: newStep.value.stepType,
        delayDays: newStep.value.delayDays,
        emailTemplateId: newStep.value.emailTemplateId || undefined,
        subjectOverride: newStep.value.subjectOverride || undefined,
        taskDescription: newStep.value.taskDescription || undefined,
        skipIfPreviousOpened: newStep.value.skipIfPreviousOpened,
        skipIfPreviousClicked: newStep.value.skipIfPreviousClicked,
      },
    })
    toast.success('Step added')
    stepDialogOpen.value = false
    newStep.value = {
      stepType: 'EMAIL', delayDays: 0, emailTemplateId: '', subjectOverride: '',
      taskDescription: '', skipIfPreviousOpened: false, skipIfPreviousClicked: false,
    }
  } catch (error: any) {
    toast.error(error?.data?.error?.message || 'Failed to add step')
  }
}

async function handleDeleteStep(stepId: string) {
  if (!confirm('Delete this step?')) return
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

function stepIcon(stepType: string): string {
  switch (stepType) {
    case 'EMAIL': return '📧'
    case 'DELAY': return '⏱️'
    case 'CALL': return '📞'
    case 'TASK': return '📋'
    default: return '•'
  }
}
</script>

<template>
  <div class="space-y-6">
    <NuxtLink to="/sequences" class="text-sm text-muted-foreground hover:text-foreground">
      ← Back to sequences
    </NuxtLink>

    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">Loading sequence...</div>

    <div v-else-if="!sequence" class="text-center py-8 text-destructive">Sequence not found</div>

    <div v-else class="space-y-6">
      <!-- Header -->
      <Card>
        <CardContent class="pt-6">
          <div class="flex items-start justify-between">
            <div>
              <div class="flex items-center gap-3 mb-1">
                <h1 class="text-2xl font-bold">{{ sequence.name }}</h1>
                <Badge :variant="statusBadgeVariant(sequence.status)">{{ sequence.status }}</Badge>
              </div>
              <p v-if="sequence.description" class="text-muted-foreground">{{ sequence.description }}</p>
              <p v-if="sequence.defaultMailboxEmail" class="text-sm text-muted-foreground mt-2">
                Sending from: {{ sequence.defaultMailboxEmail }}
              </p>
            </div>
            <div class="flex gap-2">
              <Button
                v-if="sequence.status === 'DRAFT'"
                @click="handleActivate"
                :disabled="activateMutation.isPending.value || sequence.steps.length === 0"
              >Activate</Button>
              <Button
                v-if="sequence.status === 'ACTIVE'"
                variant="outline"
                @click="handlePause"
                :disabled="pauseMutation.isPending.value"
              >Pause</Button>
              <Button
                v-if="sequence.status === 'PAUSED'"
                @click="handleActivate"
                :disabled="activateMutation.isPending.value"
              >Resume</Button>
            </div>
          </div>

          <!-- Stats -->
          <div class="grid grid-cols-3 gap-4 mt-6 pt-6 border-t">
            <div>
              <div class="text-xs text-muted-foreground">Enrolled</div>
              <div class="text-2xl font-bold">{{ sequence.totalEnrolled }}</div>
            </div>
            <div>
              <div class="text-xs text-muted-foreground">Completed</div>
              <div class="text-2xl font-bold">{{ sequence.totalCompleted }}</div>
            </div>
            <div>
              <div class="text-xs text-muted-foreground">Replied</div>
              <div class="text-2xl font-bold">{{ sequence.totalReplied }}</div>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Steps -->
      <Card>
        <CardHeader>
          <div class="flex items-center justify-between">
            <CardTitle>Steps</CardTitle>
            <Dialog v-model:open="stepDialogOpen">
              <DialogTrigger as-child>
                <Button size="sm" :disabled="sequence.status === 'ACTIVE'">+ Add Step</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Add Step</DialogTitle>
                </DialogHeader>
                <div class="space-y-4">
                  <div class="space-y-2">
                    <Label>Step Type</Label>
                    <div class="grid grid-cols-4 gap-2">
                      <Button
                        v-for="t in ['EMAIL', 'DELAY', 'CALL', 'TASK']"
                        :key="t"
                        size="sm"
                        :variant="newStep.stepType === t ? 'default' : 'outline'"
                        @click="newStep.stepType = t as any"
                      >{{ t }}</Button>
                    </div>
                  </div>

                  <div v-if="newStep.stepType === 'DELAY'" class="space-y-2">
                    <Label for="delayDays">Wait (days)</Label>
                    <Input id="delayDays" v-model.number="newStep.delayDays" type="number" min="1" />
                  </div>

                  <template v-if="newStep.stepType === 'EMAIL'">
                    <div class="space-y-2">
                      <Label for="stepDelay">Delay before sending (days)</Label>
                      <Input id="stepDelay" v-model.number="newStep.delayDays" type="number" min="0" />
                    </div>
                    <div class="space-y-2">
                      <Label for="emailTemplate">Email Template *</Label>
                      <select
                        id="emailTemplate"
                        v-model="newStep.emailTemplateId"
                        class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                      >
                        <option value="">Select template</option>
                        <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }}</option>
                      </select>
                    </div>
                    <div class="space-y-2">
                      <Label for="subjectOverride">Subject Override (optional)</Label>
                      <Input id="subjectOverride" v-model="newStep.subjectOverride" placeholder="Leave empty to use template subject" />
                    </div>
                    <div class="rounded-md border p-3 space-y-2">
                      <p class="text-xs font-medium">Skip this email if the contact…</p>
                      <label class="flex items-center gap-2 text-sm">
                        <input type="checkbox" v-model="newStep.skipIfPreviousOpened" class="h-4 w-4" />
                        <span>opened an earlier email in this sequence</span>
                      </label>
                      <label class="flex items-center gap-2 text-sm">
                        <input type="checkbox" v-model="newStep.skipIfPreviousClicked" class="h-4 w-4" />
                        <span>clicked a link in an earlier email in this sequence</span>
                      </label>
                      <p class="text-xs text-muted-foreground">
                        Use for follow-ups — stop pestering a lead who's already engaged.
                      </p>
                    </div>
                  </template>

                  <template v-if="newStep.stepType === 'CALL' || newStep.stepType === 'TASK'">
                    <div class="space-y-2">
                      <Label for="delayForTask">Delay before task (days)</Label>
                      <Input id="delayForTask" v-model.number="newStep.delayDays" type="number" min="0" />
                    </div>
                    <div class="space-y-2">
                      <Label for="taskDescription">Task Description</Label>
                      <Input id="taskDescription" v-model="newStep.taskDescription" placeholder="What should the user do?" />
                    </div>
                  </template>
                </div>
                <DialogFooter>
                  <Button variant="outline" @click="stepDialogOpen = false">Cancel</Button>
                  <Button @click="handleAddStep" :disabled="addStepMutation.isPending.value">Add Step</Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          </div>
        </CardHeader>
        <CardContent>
          <div v-if="!sequence.steps.length" class="text-center py-8 text-muted-foreground">
            No steps yet. Add an EMAIL step to send the first email, then DELAY steps for waits.
          </div>
          <ol v-else class="space-y-2">
            <li
              v-for="step in sequence.steps"
              :key="step.id"
              class="flex items-center gap-4 p-3 rounded-md border bg-card"
            >
              <div class="flex-shrink-0 w-8 h-8 rounded-full bg-muted flex items-center justify-center text-sm font-medium">
                {{ step.stepOrder }}
              </div>
              <div class="text-2xl">{{ stepIcon(step.stepType) }}</div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <Badge variant="outline">{{ step.stepType }}</Badge>
                  <span v-if="step.delayDays > 0" class="text-xs text-muted-foreground">
                    Wait {{ step.delayDays }} day{{ step.delayDays > 1 ? 's' : '' }}
                  </span>
                </div>
                <div class="mt-1 text-sm">
                  <span v-if="step.stepType === 'EMAIL'">
                    Template: <strong>{{ step.emailTemplateName ?? '—' }}</strong>
                    <span v-if="step.subjectOverride" class="text-muted-foreground">
                      (subject: "{{ step.subjectOverride }}")
                    </span>
                  </span>
                  <span v-else-if="step.stepType === 'DELAY'">
                    Wait {{ step.delayDays }} days
                  </span>
                  <span v-else class="text-muted-foreground">{{ step.taskDescription ?? 'No description' }}</span>
                </div>
                <div v-if="step.skipIfPreviousOpened || step.skipIfPreviousClicked" class="mt-1 flex gap-1 flex-wrap">
                  <Badge v-if="step.skipIfPreviousOpened" variant="secondary" class="text-xs">
                    skip if previously opened
                  </Badge>
                  <Badge v-if="step.skipIfPreviousClicked" variant="secondary" class="text-xs">
                    skip if previously clicked
                  </Badge>
                </div>
              </div>
              <Button
                size="sm"
                variant="ghost"
                class="text-destructive"
                @click="handleDeleteStep(step.id)"
                :disabled="sequence.status === 'ACTIVE'"
              >✕</Button>
            </li>
          </ol>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
