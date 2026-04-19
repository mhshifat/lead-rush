<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '~/components/ui/select'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger, DialogDescription,
} from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import {
  ArrowLeft, Plus, X, User, Trophy, XOctagon,
} from 'lucide-vue-next'
import type { DealEntity } from '~/entities/deal/deal.entity'
import type { StageEntity } from '~/entities/pipeline/pipeline.entity'

definePageMeta({
  middleware: 'auth',
})

const route = useRoute()
const pipelineId = computed(() => route.params.id as string)

const { data: pipeline, isLoading: pipelineLoading } = usePipeline(pipelineId)
useHead(() => ({ title: pipeline.value?.name ?? 'Pipeline' }))
const { data: deals } = usePipelineDeals(pipelineId)
const { data: contacts } = useContacts(ref({ size: 200, page: 0 }))

const moveMutation = useMoveDeal()
const createDealMutation = useCreateDeal()
const deleteDealMutation = useDeleteDeal()

const dealsByStage = computed(() => {
  const map = new Map<string, DealEntity[]>()
  if (pipeline.value) {
    pipeline.value.stages.forEach(s => map.set(s.id, []))
  }
  if (deals.value) {
    deals.value.forEach(d => {
      const arr = map.get(d.pipelineStageId) ?? []
      arr.push(d)
      map.set(d.pipelineStageId, arr)
    })
  }
  return map
})

const pipelineTotal = computed(() => {
  const total = (deals.value ?? []).reduce((sum, d) => sum + (d.valueAmount ?? 0), 0)
  return formatCurrency(total, 'USD')
})

// ── Drag state ──
const draggedDealId = ref<string | null>(null)
const dragOverStageId = ref<string | null>(null)

function onDragStart(event: DragEvent, dealId: string) {
  draggedDealId.value = dealId
  if (event.dataTransfer) {
    event.dataTransfer.setData('text/plain', dealId)
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onDragOver(event: DragEvent, stageId: string) {
  event.preventDefault()
  dragOverStageId.value = stageId
}

function onDragLeave() {
  dragOverStageId.value = null
}

async function onDrop(event: DragEvent, stageId: string) {
  event.preventDefault()
  dragOverStageId.value = null
  const dealId = event.dataTransfer?.getData('text/plain') ?? draggedDealId.value
  if (!dealId) return

  const deal = deals.value?.find(d => d.id === dealId)
  if (!deal || deal.pipelineStageId === stageId) return

  try {
    await moveMutation.mutateAsync({ dealId, pipelineStageId: stageId })
  } catch {
    toast.error('Failed to move deal')
  } finally {
    draggedDealId.value = null
  }
}

// ── Create deal dialog ──
const dealDialogOpen = ref(false)
const dealForm = ref({
  name: '',
  description: '',
  valueAmount: undefined as number | undefined,
  valueCurrency: 'USD',
  contactId: '',
  targetStageId: '',
})
const dealErrors = useFieldErrors()

// Touch-to-clear.
watch(() => dealForm.value.name, v => { if (v.trim()) dealErrors.remove('name') })

function openCreateDealDialog(stageId?: string) {
  dealForm.value = {
    name: '',
    description: '',
    valueAmount: undefined,
    valueCurrency: 'USD',
    contactId: '',
    targetStageId: stageId ?? (pipeline.value?.stages[0]?.id ?? ''),
  }
  dealErrors.clear()
  dealDialogOpen.value = true
}

async function handleCreateDeal() {
  dealErrors.clear()
  if (!dealForm.value.name.trim()) dealErrors.set('name', 'Deal name is required.')
  if (Object.keys(dealErrors.map).length) return
  try {
    await createDealMutation.mutateAsync({
      name: dealForm.value.name,
      pipelineId: pipelineId.value,
      pipelineStageId: dealForm.value.targetStageId || undefined,
      description: dealForm.value.description || undefined,
      valueAmount: dealForm.value.valueAmount,
      valueCurrency: dealForm.value.valueCurrency,
      contactIds: dealForm.value.contactId ? [dealForm.value.contactId] : undefined,
    })
    toast.success('Deal created')
    dealDialogOpen.value = false
  } catch (error: any) {
    dealErrors.fromServerError(error, 'Failed to create deal')
  }
}

async function handleDeleteDeal(dealId: string, name: string) {
  const ok = await useConfirm().ask({
    title: `Delete deal "${name}"?`,
    confirmLabel: 'Delete',
    variant: 'destructive',
  })
  if (!ok) return
  try {
    await deleteDealMutation.mutateAsync(dealId)
    toast.success('Deal deleted')
  } catch {
    toast.error('Failed to delete deal')
  }
}

function formatCurrency(amount: number | null, currency: string | null): string {
  if (amount === null || amount === 0) return currency === 'USD' ? '$0' : '—'
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

function stageTotalValue(stage: StageEntity): string {
  const stageDeals = dealsByStage.value.get(stage.id) ?? []
  const total = stageDeals.reduce((sum, d) => sum + (d.valueAmount ?? 0), 0)
  return formatCurrency(total, 'USD')
}

function stageAccent(stage: StageEntity): string {
  if (stage.color) return stage.color
  if (stage.stageType === 'WON') return 'rgb(16 185 129)'   // emerald-500
  if (stage.stageType === 'LOST') return 'rgb(239 68 68)'   // red-500
  return 'rgb(99 102 241)'                                  // indigo-500
}
</script>

<template>
  <div class="h-full flex flex-col gap-4 enter-fade-up min-h-0">
    <!-- Back link -->
    <NuxtLink
      to="/pipelines"
      class="shrink-0 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
    >
      <ArrowLeft class="h-3.5 w-3.5" />
      Back to pipelines
    </NuxtLink>

    <!-- Loading / not found -->
    <div v-if="pipelineLoading" class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground">
      Loading…
    </div>
    <div v-else-if="!pipeline" class="glass hairline rounded-xl py-16 text-center text-sm text-destructive">
      Pipeline not found
    </div>

    <div v-else class="flex-1 flex flex-col gap-5 min-h-0">
      <!-- Header -->
      <div class="shrink-0 flex items-end justify-between gap-4 flex-wrap">
        <div>
          <h1 class="text-2xl font-semibold tracking-tight">{{ pipeline.name }}</h1>
          <p class="text-sm text-muted-foreground mt-0.5">
            <span v-if="pipeline.description">{{ pipeline.description }} · </span>
            {{ deals?.length ?? 0 }} open {{ (deals?.length ?? 0) === 1 ? 'deal' : 'deals' }}
            · Total value
            <span class="font-medium text-foreground tabular-nums">{{ pipelineTotal }}</span>
          </p>
        </div>

        <Dialog v-model:open="dealDialogOpen">
          <DialogTrigger as-child>
            <Button class="gap-1.5" @click="openCreateDealDialog()">
              <Plus class="h-4 w-4" />
              New deal
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Create deal</DialogTitle>
              <DialogDescription>Add a new opportunity to this pipeline.</DialogDescription>
            </DialogHeader>
            <div class="space-y-4">
              <div
                v-if="dealErrors.has('_form')"
                class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
              >
                {{ dealErrors.get('_form') }}
              </div>
              <div class="space-y-2">
                <Label for="dealName">Name *</Label>
                <Input
                  id="dealName"
                  v-model="dealForm.name"
                  placeholder="Acme Corp — Enterprise deal"
                  :class="dealErrors.has('name') ? 'border-destructive' : ''"
                />
                <SharedFormError :message="dealErrors.get('name')" />
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div class="space-y-2">
                  <Label for="dealValue">Value</Label>
                  <Input id="dealValue" v-model.number="dealForm.valueAmount" type="number" min="0" placeholder="50000" />
                </div>
                <div class="space-y-2">
                  <Label for="dealCurrency">Currency</Label>
                  <Input id="dealCurrency" v-model="dealForm.valueCurrency" maxlength="3" />
                </div>
              </div>
              <div class="space-y-2">
                <Label for="dealStage">Stage</Label>
                <Select v-model="dealForm.targetStageId">
                  <SelectTrigger id="dealStage" class="w-full">
                    <SelectValue placeholder="Select a stage" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem v-for="s in pipeline.stages" :key="s.id" :value="s.id">
                      {{ s.name }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div class="space-y-2">
                <Label for="dealContact">Primary contact <span class="text-muted-foreground font-normal">(optional)</span></Label>
                <Select v-model="dealForm.contactId">
                  <SelectTrigger id="dealContact" class="w-full">
                    <SelectValue placeholder="Select a contact" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem v-for="c in (contacts?.items ?? [])" :key="c.id" :value="c.id">
                      {{ c.fullName }}{{ c.companyName ? ' · ' + c.companyName : '' }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div class="space-y-2">
                <Label for="dealDescription">Description</Label>
                <Input id="dealDescription" v-model="dealForm.description" placeholder="Short note about this deal" />
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" @click="dealDialogOpen = false">Cancel</Button>
              <Button @click="handleCreateDeal" :disabled="createDealMutation.isPending.value">
                {{ createDealMutation.isPending.value ? 'Creating…' : 'Create' }}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      <!-- Kanban board — fills remaining height; columns scroll internally. -->
      <div class="flex-1 overflow-x-auto -mx-1 min-h-0 no-scrollbar">
        <div class="flex gap-4 min-w-max h-full px-1">
          <div
            v-for="stage in pipeline.stages"
            :key="stage.id"
            class="w-72 flex-shrink-0 h-full flex flex-col glass hairline rounded-xl overflow-hidden transition-colors"
            :class="{ 'ring-1 ring-primary/50 bg-primary/3': dragOverStageId === stage.id }"
            @dragover="onDragOver($event, stage.id)"
            @dragleave="onDragLeave"
            @drop="onDrop($event, stage.id)"
          >
            <!-- Stage header — coloured accent dot + name + count -->
            <div class="shrink-0 px-4 py-3" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2 min-w-0">
                  <component
                    :is="stage.stageType === 'WON' ? Trophy : stage.stageType === 'LOST' ? XOctagon : null"
                    v-if="stage.stageType === 'WON' || stage.stageType === 'LOST'"
                    class="h-3.5 w-3.5 shrink-0"
                    :style="{ color: stageAccent(stage) }"
                  />
                  <span
                    v-else
                    class="h-2 w-2 rounded-full shrink-0"
                    :style="{ background: stageAccent(stage) }"
                  />
                  <h3 class="text-sm font-medium truncate">{{ stage.name }}</h3>
                </div>
                <Badge variant="outline" class="text-xs tabular-nums">
                  {{ (dealsByStage.get(stage.id) ?? []).length }}
                </Badge>
              </div>
              <p class="text-xs text-muted-foreground mt-1 tabular-nums">
                {{ stageTotalValue(stage) }}
              </p>
            </div>

            <!-- Deal cards — scrolls vertically when cards overflow the column height. -->
            <div class="flex-1 overflow-y-auto p-2 space-y-2 min-h-0 no-scrollbar">
              <div
                v-for="deal in (dealsByStage.get(stage.id) ?? [])"
                :key="deal.id"
                draggable="true"
                class="group rounded-lg hairline p-3 space-y-2 bg-white/2 cursor-grab hover:bg-white/5 active:cursor-grabbing transition-colors"
                :class="{ 'opacity-40': draggedDealId === deal.id }"
                @dragstart="onDragStart($event, deal.id)"
                @dragend="draggedDealId = null"
              >
                <div class="flex items-start justify-between gap-2">
                  <p class="text-sm font-medium leading-snug">{{ deal.name }}</p>
                  <button
                    class="shrink-0 opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-destructive transition-opacity"
                    title="Delete deal"
                    @click.stop="handleDeleteDeal(deal.id, deal.name)"
                  >
                    <X class="h-3.5 w-3.5" />
                  </button>
                </div>
                <p
                  v-if="deal.valueAmount"
                  class="text-sm font-semibold text-primary tabular-nums"
                >
                  {{ formatCurrency(deal.valueAmount, deal.valueCurrency) }}
                </p>
                <div v-if="deal.contacts.length" class="flex flex-wrap gap-1">
                  <NuxtLink
                    v-for="c in deal.contacts"
                    :key="c.id"
                    :to="`/contacts/${c.id}`"
                    class="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-primary truncate"
                    @click.stop
                  >
                    <User class="h-3 w-3" />
                    {{ c.fullName }}
                  </NuxtLink>
                </div>
              </div>

              <!-- Add deal — visible but subtle -->
              <button
                class="w-full flex items-center justify-center gap-1 text-xs text-muted-foreground hover:text-foreground py-2 rounded-md hover:bg-white/5 transition-colors"
                @click="openCreateDealDialog(stage.id)"
              >
                <Plus class="h-3 w-3" />
                Add deal
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
