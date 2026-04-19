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
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription,
} from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import {
  Plus, Target, Zap, RotateCw, Pencil, Trash2, Power, PowerOff, ArrowRight,
} from 'lucide-vue-next'
import {
  TRIGGER_OPTIONS,
  CONDITION_OPERATOR_OPTIONS,
  CONDITION_FIELD_OPTIONS,
  type LeadScoreRuleEntity,
  type TriggerType,
  type ConditionOperator,
} from '~/entities/lead-scoring/lead-scoring.entity'
import type { CreateLeadScoreRuleDto } from '~/types/api/lead-scoring.dto'

definePageMeta({ middleware: 'auth' })
useHead({ title: 'Lead scoring' })

const { data: rules, isLoading } = useLeadScoreRules()
const createMutation = useCreateLeadScoreRule()
const updateMutation = useUpdateLeadScoreRule()
const deleteMutation = useDeleteLeadScoreRule()
const recalcMutation = useRecalculateScores()

// ── Dialog state ──
const dialogOpen = ref(false)
const editing = ref<LeadScoreRuleEntity | null>(null)

const form = ref<CreateLeadScoreRuleDto>({
  name: '',
  description: '',
  triggerType: 'CONTACT_CREATED',
  conditionField: '',
  conditionOperator: '',
  conditionValue: '',
  points: 5,
  enabled: true,
})
const errors = useFieldErrors()

watch(() => form.value.name, v => { if (v.trim()) errors.remove('name') })
watch(dialogOpen, (open) => { if (open) errors.clear() })

function openCreate() {
  editing.value = null
  form.value = {
    name: '',
    description: '',
    triggerType: 'CONTACT_CREATED',
    conditionField: '',
    conditionOperator: '',
    conditionValue: '',
    points: 5,
    enabled: true,
  }
  errors.clear()
  dialogOpen.value = true
}

function openEdit(rule: LeadScoreRuleEntity) {
  editing.value = rule
  form.value = {
    name: rule.name,
    description: rule.description ?? '',
    triggerType: rule.triggerType,
    conditionField: rule.conditionField ?? '',
    conditionOperator: rule.conditionOperator ?? '',
    conditionValue: rule.conditionValue ?? '',
    points: rule.points,
    enabled: rule.enabled,
  }
  errors.clear()
  dialogOpen.value = true
}

function closeDialog() {
  dialogOpen.value = false
  editing.value = null
  errors.clear()
}

async function handleSave() {
  errors.clear()
  if (!form.value.name.trim()) errors.set('name', 'Name is required.')
  if (Object.keys(errors.map).length) return
  const payload: CreateLeadScoreRuleDto = {
    name: form.value.name.trim(),
    description: form.value.description?.trim() || undefined,
    triggerType: form.value.triggerType,
    conditionField: form.value.conditionField?.trim() || undefined,
    conditionOperator: form.value.conditionOperator?.trim() || undefined,
    conditionValue: form.value.conditionValue?.trim() || undefined,
    points: Number(form.value.points),
    enabled: form.value.enabled,
  }
  try {
    if (editing.value) {
      await updateMutation.mutateAsync({ id: editing.value.id, dto: payload })
      closeDialog()
      toast.success('Rule updated')
    } else {
      await createMutation.mutateAsync(payload)
      closeDialog()
      toast.success('Rule created')
    }
  } catch (error: any) {
    errors.fromServerError(error, 'Failed to save rule')
  }
}

// Reka-ui Select rejects empty-string values. Clear state directly from the model
// instead of feeding an empty SelectItem, which would silently abort the submit handler.
function clearConditionField() {
  form.value.conditionField = ''
  form.value.conditionOperator = ''
  form.value.conditionValue = ''
}

async function handleDelete(rule: LeadScoreRuleEntity) {
  const ok = await useConfirm().ask({
    title: `Delete rule "${rule.name}"?`,
    confirmLabel: 'Delete',
    variant: 'destructive',
  })
  if (!ok) return
  try {
    await deleteMutation.mutateAsync(rule.id)
    toast.success('Rule deleted')
  } catch {
    toast.error('Failed to delete rule')
  }
}

async function handleToggle(rule: LeadScoreRuleEntity) {
  try {
    await updateMutation.mutateAsync({
      id: rule.id,
      dto: {
        name: rule.name,
        description: rule.description ?? undefined,
        triggerType: rule.triggerType,
        conditionField: rule.conditionField ?? undefined,
        conditionOperator: rule.conditionOperator ?? undefined,
        conditionValue: rule.conditionValue ?? undefined,
        points: rule.points,
        enabled: !rule.enabled,
      },
    })
  } catch {
    toast.error('Failed to update')
  }
}

async function handleRecalculate() {
  const ok = await useConfirm().ask({
    title: 'Recalculate every lead score?',
    description: 'Every contact will have their score reset to zero and CONTACT_CREATED rules replayed. This may take a while on large workspaces.',
    confirmLabel: 'Recalculate',
  })
  if (!ok) return
  try {
    const result = await recalcMutation.mutateAsync()
    toast.success(`Recalculated ${result.contactsProcessed} contacts`)
  } catch {
    toast.error('Failed to recalculate')
  }
}

function triggerLabel(trigger: TriggerType): string {
  return TRIGGER_OPTIONS.find(t => t.value === trigger)?.label ?? trigger
}

function operatorLabel(op: ConditionOperator | null | undefined): string {
  if (!op) return ''
  return CONDITION_OPERATOR_OPTIONS.find(o => o.value === op)?.label ?? op
}

function fieldLabel(field: string | null | undefined): string {
  if (!field) return ''
  return CONDITION_FIELD_OPTIONS.find(f => f.value === field)?.label ?? field
}

// Totals shown above the rule list.
const totals = computed(() => {
  const list = rules.value ?? []
  const enabled = list.filter(r => r.enabled).length
  const positive = list.filter(r => r.enabled && r.points > 0).reduce((a, r) => a + r.points, 0)
  const negative = list.filter(r => r.enabled && r.points < 0).reduce((a, r) => a + r.points, 0)
  return {
    total: list.length,
    enabled,
    positive,
    negative,
  }
})
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Header -->
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Lead scoring</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          Rules update a contact's score when something happens — higher score = hotter lead.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Button
          variant="outline"
          class="gap-1.5"
          :disabled="recalcMutation.isPending.value"
          @click="handleRecalculate"
        >
          <RotateCw class="h-4 w-4" :class="recalcMutation.isPending.value ? 'animate-spin' : ''" />
          Recalculate all
        </Button>
        <Button class="gap-1.5" @click="openCreate">
          <Plus class="h-4 w-4" />
          New rule
        </Button>
      </div>
    </div>

    <!-- Summary strip -->
    <div
      v-if="rules?.length"
      class="glass hairline rounded-xl grid grid-cols-2 md:grid-cols-4 divide-x divide-white/5"
    >
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Rules</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.total }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Enabled</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.enabled }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Max add</p>
        <p class="mt-1 text-xl font-semibold tabular-nums text-emerald-400">
          +{{ totals.positive }}
        </p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Max deduct</p>
        <p class="mt-1 text-xl font-semibold tabular-nums text-destructive">
          {{ totals.negative }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <div
      v-if="isLoading"
      class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground"
    >
      Loading rules…
    </div>

    <!-- Empty state -->
    <div
      v-else-if="!rules?.length"
      class="glass hairline rounded-xl py-14 px-6 text-center"
    >
      <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-4">
        <Target class="h-5 w-5 text-muted-foreground" />
      </div>
      <h3 class="text-sm font-semibold tracking-tight">No scoring rules yet</h3>
      <p class="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
        A rule says "when X happens, add Y points." Stack a few to surface your hottest leads automatically.
      </p>
      <Button class="mt-5 gap-1.5" @click="openCreate">
        <Plus class="h-4 w-4" />
        Create your first rule
      </Button>
    </div>

    <!-- Rule list -->
    <div v-else class="space-y-3">
      <div
        v-for="rule in rules"
        :key="rule.id"
        class="glass hairline rounded-xl p-4 transition-colors hover:bg-white/2"
        :class="rule.enabled ? '' : 'opacity-60'"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex items-start gap-3 min-w-0 flex-1">
            <div class="relative h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <Zap class="h-4 w-4 text-primary" />
              <span
                class="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full ring-2 ring-background"
                :class="rule.enabled ? 'bg-emerald-400' : 'bg-muted-foreground/50'"
              />
            </div>
            <div class="min-w-0 flex-1">
              <!-- Title + badges -->
              <div class="flex items-center gap-2 flex-wrap">
                <h3 class="font-semibold tracking-tight truncate">{{ rule.name }}</h3>
                <Badge
                  :variant="rule.points >= 0 ? 'secondary' : 'destructive'"
                  class="font-mono text-xs tabular-nums"
                >
                  {{ rule.points >= 0 ? '+' : '' }}{{ rule.points }} pts
                </Badge>
                <Badge v-if="!rule.enabled" variant="outline" class="text-xs">Disabled</Badge>
              </div>

              <!-- Description -->
              <p v-if="rule.description" class="text-xs text-muted-foreground mt-1">
                {{ rule.description }}
              </p>

              <!-- Rule formula: trigger → condition → points -->
              <div class="mt-3 flex items-center flex-wrap gap-1.5 text-xs">
                <span class="inline-flex items-center gap-1 px-2 py-1 rounded-md hairline bg-white/2">
                  <span class="text-muted-foreground">When</span>
                  <span class="font-medium">{{ triggerLabel(rule.triggerType) }}</span>
                </span>
                <template v-if="rule.conditionField && rule.conditionOperator">
                  <ArrowRight class="h-3 w-3 text-muted-foreground" />
                  <span class="inline-flex items-center gap-1 px-2 py-1 rounded-md hairline bg-white/2">
                    <span class="font-medium">{{ fieldLabel(rule.conditionField) }}</span>
                    <span class="text-muted-foreground">{{ operatorLabel(rule.conditionOperator) }}</span>
                    <span class="font-medium">"{{ rule.conditionValue }}"</span>
                  </span>
                </template>
                <ArrowRight class="h-3 w-3 text-muted-foreground" />
                <span
                  class="inline-flex items-center gap-1 px-2 py-1 rounded-md font-mono tabular-nums"
                  :class="rule.points >= 0 ? 'bg-emerald-400/10 text-emerald-400' : 'bg-destructive/10 text-destructive'"
                >
                  {{ rule.points >= 0 ? '+' : '' }}{{ rule.points }} pts
                </span>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="flex items-center gap-1 shrink-0">
            <Button
              size="sm"
              variant="outline"
              class="h-8 w-8 p-0"
              :title="rule.enabled ? 'Disable' : 'Enable'"
              @click="handleToggle(rule)"
            >
              <component :is="rule.enabled ? PowerOff : Power" class="h-3.5 w-3.5" />
            </Button>
            <Button
              size="sm"
              variant="outline"
              class="h-8 w-8 p-0"
              title="Edit"
              @click="openEdit(rule)"
            >
              <Pencil class="h-3.5 w-3.5" />
            </Button>
            <Button
              size="sm"
              variant="outline"
              class="h-8 w-8 p-0 text-muted-foreground hover:text-destructive"
              title="Delete"
              @click="handleDelete(rule)"
            >
              <Trash2 class="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
      </div>
    </div>

    <!-- Create / edit dialog -->
    <Dialog v-model:open="dialogOpen">
      <DialogContent class="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle>{{ editing ? 'Edit rule' : 'New scoring rule' }}</DialogTitle>
          <DialogDescription>
            Rules run automatically whenever the trigger event happens.
          </DialogDescription>
        </DialogHeader>

        <div class="space-y-4 py-2">
          <div
            v-if="errors.has('_form')"
            class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
          >
            {{ errors.get('_form') }}
          </div>

          <div class="space-y-2">
            <Label for="name">Name *</Label>
            <Input
              id="name"
              v-model="form.name"
              placeholder="e.g. CEO opened an email"
              :class="errors.has('name') ? 'border-destructive' : ''"
            />
            <SharedFormError :message="errors.get('name')" />
          </div>

          <div class="space-y-2">
            <Label for="description">Description <span class="text-muted-foreground font-normal">(optional)</span></Label>
            <Input id="description" v-model="form.description" placeholder="Why this rule exists" />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <Label for="trigger">Trigger *</Label>
              <Select v-model="form.triggerType">
                <SelectTrigger id="trigger" class="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="t in TRIGGER_OPTIONS" :key="t.value" :value="t.value">
                    {{ t.label }}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-2">
              <Label for="points">Points *</Label>
              <Input id="points" v-model.number="form.points" type="number" placeholder="e.g. 10 or -5" />
              <p class="text-xs text-muted-foreground">Use negatives to deduct points.</p>
            </div>
          </div>

          <div class="rounded-md hairline p-3 space-y-3 bg-white/2">
            <div class="flex items-center justify-between">
              <p class="text-xs text-muted-foreground">
                <strong class="text-foreground font-medium">Condition</strong> — optional. Leave blank to fire on every trigger.
              </p>
              <button
                v-if="form.conditionField"
                type="button"
                class="text-xs text-primary hover:underline"
                @click="clearConditionField"
              >Clear</button>
            </div>
            <div class="grid grid-cols-3 gap-2">
              <div class="space-y-1">
                <Label for="condField" class="text-xs">Field</Label>
                <Select
                  :model-value="form.conditionField || undefined"
                  @update:model-value="(v: any) => form.conditionField = typeof v === 'string' ? v : ''"
                >
                  <SelectTrigger id="condField" class="w-full"><SelectValue placeholder="(none)" /></SelectTrigger>
                  <SelectContent>
                    <SelectItem v-for="f in CONDITION_FIELD_OPTIONS" :key="f.value" :value="f.value">
                      {{ f.label }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div class="space-y-1">
                <Label for="condOp" class="text-xs">Operator</Label>
                <Select
                  :model-value="form.conditionOperator || undefined"
                  :disabled="!form.conditionField"
                  @update:model-value="(v: any) => form.conditionOperator = typeof v === 'string' ? v : ''"
                >
                  <SelectTrigger id="condOp" class="w-full"><SelectValue placeholder="(none)" /></SelectTrigger>
                  <SelectContent>
                    <SelectItem v-for="o in CONDITION_OPERATOR_OPTIONS" :key="o.value" :value="o.value">
                      {{ o.label }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div class="space-y-1">
                <Label for="condValue" class="text-xs">Value</Label>
                <Input
                  id="condValue"
                  v-model="form.conditionValue"
                  :disabled="!form.conditionField"
                  placeholder="e.g. CEO"
                />
              </div>
            </div>
          </div>

          <label class="flex items-center gap-2 cursor-pointer select-none">
            <Checkbox :model-value="form.enabled" @update:model-value="(v) => form.enabled = v === true" />
            <span class="text-sm">Enabled — rule fires for new events</span>
          </label>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="dialogOpen = false">Cancel</Button>
          <Button
            :disabled="createMutation.isPending.value || updateMutation.isPending.value"
            @click="handleSave"
          >
            {{ createMutation.isPending.value || updateMutation.isPending.value
              ? 'Saving…'
              : editing ? 'Update' : 'Create' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
