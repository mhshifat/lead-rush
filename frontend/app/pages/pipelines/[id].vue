<!--
  Kanban board for a single pipeline.
  Uses native HTML5 drag-and-drop to move deals between stages.

  HOW DRAG-AND-DROP WORKS:
    draggable="true"     — makes an element draggable
    @dragstart           — fires when drag begins (we set the deal ID on dataTransfer)
    @dragover.prevent    — must preventDefault to ALLOW drop (HTML default is to reject)
    @drop                — fires on the target when user releases — we read the deal ID
    @dragend             — fires when drag finishes (cleanup visual state)

  We pair this with an OPTIMISTIC mutation — the deal card visibly moves to
  the new column INSTANTLY, then the server call happens in background.
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import type { DealEntity } from '~/entities/deal/deal.entity'
import type { StageEntity } from '~/entities/pipeline/pipeline.entity'

definePageMeta({
  middleware: 'auth',
})

const route = useRoute()
const pipelineId = computed(() => route.params.id as string)

const { data: pipeline, isLoading: pipelineLoading } = usePipeline(pipelineId)
const { data: deals } = usePipelineDeals(pipelineId)
const { data: contacts } = useContacts(ref({ size: 200, page: 0 }))

const moveMutation = useMoveDeal()
const createDealMutation = useCreateDeal()
const deleteDealMutation = useDeleteDeal()

// ── Deals grouped by stage ──
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
  event.preventDefault()        // Required to allow drop
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

  // Find current stage — bail if no change
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

function openCreateDealDialog(stageId?: string) {
  dealForm.value = {
    name: '',
    description: '',
    valueAmount: undefined,
    valueCurrency: 'USD',
    contactId: '',
    targetStageId: stageId ?? (pipeline.value?.stages[0]?.id ?? ''),
  }
  dealDialogOpen.value = true
}

async function handleCreateDeal() {
  if (!dealForm.value.name.trim()) {
    toast.error('Deal name is required')
    return
  }
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
    toast.error(error?.data?.error?.message || 'Failed to create deal')
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

// ── Formatting helpers ──
function formatCurrency(amount: number | null, currency: string | null): string {
  if (amount === null) return '—'
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

function stageBorderColor(stage: StageEntity): string {
  if (stage.color) return stage.color
  if (stage.stageType === 'WON') return '#10B981'
  if (stage.stageType === 'LOST') return '#EF4444'
  return '#3B82F6'
}
</script>

<template>
  <div class="space-y-4">
    <NuxtLink to="/pipelines" class="text-sm text-muted-foreground hover:text-foreground">
      ← Back to pipelines
    </NuxtLink>

    <div v-if="pipelineLoading" class="text-center py-8 text-muted-foreground">Loading...</div>

    <div v-else-if="!pipeline" class="text-center py-8 text-destructive">Pipeline not found</div>

    <div v-else class="space-y-4">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold">{{ pipeline.name }}</h1>
          <p v-if="pipeline.description" class="text-sm text-muted-foreground">
            {{ pipeline.description }}
          </p>
        </div>
        <Dialog v-model:open="dealDialogOpen">
          <DialogTrigger as-child>
            <Button @click="openCreateDealDialog()">+ New Deal</Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Create Deal</DialogTitle>
            </DialogHeader>
            <div class="space-y-4">
              <div class="space-y-2">
                <Label for="dealName">Name *</Label>
                <Input id="dealName" v-model="dealForm.name" placeholder="Acme Corp - Enterprise deal" />
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div class="space-y-2">
                  <Label for="dealValue">Value</Label>
                  <Input id="dealValue" v-model.number="dealForm.valueAmount" type="number" min="0" />
                </div>
                <div class="space-y-2">
                  <Label for="dealCurrency">Currency</Label>
                  <Input id="dealCurrency" v-model="dealForm.valueCurrency" maxlength="3" />
                </div>
              </div>
              <div class="space-y-2">
                <Label for="dealStage">Stage</Label>
                <select
                  id="dealStage"
                  v-model="dealForm.targetStageId"
                  class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                >
                  <option v-for="s in pipeline.stages" :key="s.id" :value="s.id">{{ s.name }}</option>
                </select>
              </div>
              <div class="space-y-2">
                <Label for="dealContact">Primary Contact</Label>
                <select
                  id="dealContact"
                  v-model="dealForm.contactId"
                  class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                >
                  <option value="">No contact</option>
                  <option v-for="c in (contacts?.items ?? [])" :key="c.id" :value="c.id">
                    {{ c.fullName }}{{ c.companyName ? ' · ' + c.companyName : '' }}
                  </option>
                </select>
              </div>
              <div class="space-y-2">
                <Label for="dealDescription">Description</Label>
                <Input id="dealDescription" v-model="dealForm.description" />
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" @click="dealDialogOpen = false">Cancel</Button>
              <Button @click="handleCreateDeal" :disabled="createDealMutation.isPending.value">
                {{ createDealMutation.isPending.value ? 'Creating...' : 'Create' }}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      <!-- Kanban board — horizontal scroll for many stages -->
      <div class="overflow-x-auto">
        <div class="flex gap-4 min-w-max pb-4">
          <div
            v-for="stage in pipeline.stages"
            :key="stage.id"
            class="w-72 flex-shrink-0"
            @dragover="onDragOver($event, stage.id)"
            @dragleave="onDragLeave"
            @drop="onDrop($event, stage.id)"
          >
            <!-- Stage header -->
            <div
              class="rounded-t-md px-3 py-2 text-sm font-medium border-t-4"
              :style="{ borderTopColor: stageBorderColor(stage) }"
            >
              <div class="flex items-center justify-between">
                <span>{{ stage.name }}</span>
                <Badge variant="outline" class="text-xs">
                  {{ (dealsByStage.get(stage.id) ?? []).length }}
                </Badge>
              </div>
              <p class="text-xs text-muted-foreground mt-1">
                {{ stageTotalValue(stage) }}
              </p>
            </div>

            <!-- Stage column (drop zone) -->
            <div
              class="bg-muted/30 rounded-b-md min-h-[400px] p-2 space-y-2 border border-t-0 transition-colors"
              :class="{ 'bg-muted/60 ring-2 ring-primary': dragOverStageId === stage.id }"
            >
              <!-- Deal cards (draggable) -->
              <Card
                v-for="deal in (dealsByStage.get(stage.id) ?? [])"
                :key="deal.id"
                draggable="true"
                class="cursor-move hover:shadow-md transition-shadow"
                :class="{ 'opacity-50': draggedDealId === deal.id }"
                @dragstart="onDragStart($event, deal.id)"
                @dragend="draggedDealId = null"
              >
                <CardContent class="p-3 space-y-2">
                  <div class="flex items-start justify-between gap-2">
                    <p class="text-sm font-medium">{{ deal.name }}</p>
                    <button
                      class="text-xs text-muted-foreground hover:text-destructive"
                      @click="handleDeleteDeal(deal.id, deal.name)"
                    >✕</button>
                  </div>
                  <p v-if="deal.valueAmount" class="text-sm font-semibold text-primary">
                    {{ formatCurrency(deal.valueAmount, deal.valueCurrency) }}
                  </p>
                  <div v-if="deal.contacts.length > 0" class="flex flex-wrap gap-1">
                    <NuxtLink
                      v-for="c in deal.contacts"
                      :key="c.id"
                      :to="`/contacts/${c.id}`"
                      class="text-xs text-muted-foreground hover:text-primary truncate"
                      @click.stop
                    >
                      {{ c.fullName }}
                    </NuxtLink>
                  </div>
                </CardContent>
              </Card>

              <!-- Add deal button at bottom of each column -->
              <button
                class="w-full text-left text-xs text-muted-foreground hover:text-foreground p-2 rounded hover:bg-muted/50"
                @click="openCreateDealDialog(stage.id)"
              >
                + Add deal
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
