<!--
  Lead Scoring Rules management page.
  Rules define "when TRIGGER happens (and optional CONDITION matches), add POINTS".
-->
<script setup lang="ts">
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
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

async function handleSave() {
  errors.clear()
  if (!form.value.name.trim()) errors.set('name', 'Name is required.')
  if (Object.keys(errors.map).length) return
  // Strip empty condition fields so backend treats them as null
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
      toast.success('Rule updated')
    } else {
      await createMutation.mutateAsync(payload)
      toast.success('Rule created')
    }
    dialogOpen.value = false
  } catch (error: any) {
    errors.fromServerError(error, 'Failed to save rule')
  }
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

function operatorLabel(op: ConditionOperator | null): string {
  if (!op) return ''
  return CONDITION_OPERATOR_OPTIONS.find(o => o.value === op)?.label ?? op
}

function fieldLabel(field: string | null): string {
  if (!field) return ''
  return CONDITION_FIELD_OPTIONS.find(f => f.value === field)?.label ?? field
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-start justify-between">
      <div>
        <h1 class="text-3xl font-bold">Lead Scoring</h1>
        <p class="text-sm text-muted-foreground">
          Rules automatically update a contact's lead score when something happens.
          Higher scores = hotter leads.
        </p>
      </div>
      <div class="flex gap-2">
        <Button variant="outline" @click="handleRecalculate" :disabled="recalcMutation.isPending.value">
          Recalculate all
        </Button>
        <Button @click="openCreate">+ New rule</Button>
      </div>
    </div>

    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">Loading...</div>

    <div v-else-if="!rules?.length" class="text-center py-16 border border-dashed rounded-lg">
      <p class="text-muted-foreground mb-4">No scoring rules yet.</p>
      <Button @click="openCreate">Create your first rule</Button>
    </div>

    <div v-else class="space-y-3">
      <Card v-for="rule in rules" :key="rule.id">
        <CardContent class="pt-6">
          <div class="flex items-start justify-between gap-4">
            <div class="flex-1">
              <div class="flex items-center gap-3 mb-1">
                <h3 class="font-medium">{{ rule.name }}</h3>
                <Badge v-if="rule.enabled" variant="default">Enabled</Badge>
                <Badge v-else variant="outline">Disabled</Badge>
                <Badge :variant="rule.points >= 0 ? 'secondary' : 'destructive'" class="font-mono">
                  {{ rule.points >= 0 ? '+' : '' }}{{ rule.points }} pts
                </Badge>
              </div>

              <p v-if="rule.description" class="text-sm text-muted-foreground mb-2">
                {{ rule.description }}
              </p>

              <div class="text-sm">
                <span class="text-muted-foreground">When</span>
                <span class="font-medium ml-1">{{ triggerLabel(rule.triggerType) }}</span>
                <template v-if="rule.conditionField && rule.conditionOperator">
                  <span class="text-muted-foreground ml-2">and</span>
                  <span class="font-medium ml-1">{{ fieldLabel(rule.conditionField) }}</span>
                  <span class="text-muted-foreground ml-1">{{ operatorLabel(rule.conditionOperator) }}</span>
                  <span class="font-medium ml-1">"{{ rule.conditionValue }}"</span>
                </template>
              </div>
            </div>

            <div class="flex gap-2">
              <Button size="sm" :variant="rule.enabled ? 'outline' : 'default'" @click="handleToggle(rule)">
                {{ rule.enabled ? 'Disable' : 'Enable' }}
              </Button>
              <Button size="sm" variant="outline" @click="openEdit(rule)">Edit</Button>
              <Button size="sm" variant="outline" class="text-destructive" @click="handleDelete(rule)">
                Delete
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Create/edit dialog -->
    <Dialog v-model:open="dialogOpen">
      <DialogContent class="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle>{{ editing ? 'Edit rule' : 'New scoring rule' }}</DialogTitle>
          <CardDescription>
            Rules run automatically whenever the trigger event happens.
          </CardDescription>
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
              placeholder="e.g., CEO opened an email"
              :class="errors.has('name') ? 'border-destructive' : ''"
            />
            <SharedFormError :message="errors.get('name')" />
          </div>

          <div class="space-y-2">
            <Label for="description">Description</Label>
            <Input id="description" v-model="form.description" placeholder="Optional explanation" />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <Label for="trigger">Trigger *</Label>
              <select
                id="trigger"
                v-model="form.triggerType"
                class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm"
              >
                <option v-for="t in TRIGGER_OPTIONS" :key="t.value" :value="t.value">
                  {{ t.label }}
                </option>
              </select>
            </div>

            <div class="space-y-2">
              <Label for="points">Points *</Label>
              <Input id="points" v-model.number="form.points" type="number" placeholder="e.g., 10 or -5" />
            </div>
          </div>

          <div class="rounded-md border p-3 space-y-3">
            <p class="text-xs text-muted-foreground">
              <strong>Condition (optional).</strong> Leave blank to fire for every trigger event.
            </p>
            <div class="grid grid-cols-3 gap-2">
              <div class="space-y-1">
                <Label for="condField" class="text-xs">Field</Label>
                <select
                  id="condField"
                  v-model="form.conditionField"
                  class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm"
                >
                  <option value="">(none)</option>
                  <option v-for="f in CONDITION_FIELD_OPTIONS" :key="f.value" :value="f.value">
                    {{ f.label }}
                  </option>
                </select>
              </div>
              <div class="space-y-1">
                <Label for="condOp" class="text-xs">Operator</Label>
                <select
                  id="condOp"
                  v-model="form.conditionOperator"
                  :disabled="!form.conditionField"
                  class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm"
                >
                  <option value="">(none)</option>
                  <option v-for="o in CONDITION_OPERATOR_OPTIONS" :key="o.value" :value="o.value">
                    {{ o.label }}
                  </option>
                </select>
              </div>
              <div class="space-y-1">
                <Label for="condValue" class="text-xs">Value</Label>
                <Input
                  id="condValue"
                  v-model="form.conditionValue"
                  :disabled="!form.conditionField"
                  placeholder="e.g., CEO"
                />
              </div>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <input id="enabled" v-model="form.enabled" type="checkbox" class="h-4 w-4" />
            <Label for="enabled" class="font-normal">Enabled</Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="dialogOpen = false">Cancel</Button>
          <Button @click="handleSave" :disabled="createMutation.isPending.value || updateMutation.isPending.value">
            {{ editing ? 'Update' : 'Create' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
