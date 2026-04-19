<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger, DialogDescription,
} from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import {
  Plus, Workflow, ArrowRight,
} from 'lucide-vue-next'

definePageMeta({
  middleware: 'auth',
})

const { data: pipelines, isLoading } = usePipelines()
const createMutation = useCreatePipeline()

const dialogOpen = ref(false)
const form = ref({ name: '', description: '' })
const createErrors = useFieldErrors()

watch(() => form.value.name, v => { if (v.trim()) createErrors.remove('name') })
watch(dialogOpen, (open) => { if (open) createErrors.clear() })

async function handleCreate() {
  createErrors.clear()
  if (!form.value.name.trim()) createErrors.set('name', 'Name is required.')
  if (Object.keys(createErrors.map).length) return
  try {
    const p = await createMutation.mutateAsync(form.value)
    toast.success('Pipeline created')
    dialogOpen.value = false
    form.value = { name: '', description: '' }
    navigateTo(`/pipelines/${p.id}`)
  } catch (error: any) {
    createErrors.fromServerError(error, 'Failed to create pipeline')
  }
}

// Assign a subtle tint per stage type so the stage pills in the preview read
// as "progress path" instead of a flat list. No icons — a red "error-looking"
// icon next to "Closed Lost" makes the card look like it's warning the user.
function stageDotClass(stageType: string | null | undefined): string {
  if (stageType === 'WON')  return 'bg-emerald-400'
  if (stageType === 'LOST') return 'bg-muted-foreground/60'
  return 'bg-primary'
}
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Page header -->
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Pipelines</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          Track deals through customisable stages.
        </p>
      </div>
      <Dialog v-model:open="dialogOpen">
        <DialogTrigger as-child>
          <Button class="gap-1.5">
            <Plus class="h-4 w-4" />
            New pipeline
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create pipeline</DialogTitle>
            <DialogDescription>
              Four default stages (Discovery, Proposal, Closed Won, Closed Lost) are added
              automatically. You can rename, reorder, or recolour them later.
            </DialogDescription>
          </DialogHeader>
          <div class="space-y-4">
            <div
              v-if="createErrors.has('_form')"
              class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
            >
              {{ createErrors.get('_form') }}
            </div>
            <div class="space-y-2">
              <Label for="name">Name *</Label>
              <Input
                id="name"
                v-model="form.name"
                placeholder="Sales pipeline"
                :class="createErrors.has('name') ? 'border-destructive' : ''"
              />
              <SharedFormError :message="createErrors.get('name')" />
            </div>
            <div class="space-y-2">
              <Label for="description">Description <span class="text-muted-foreground font-normal">(optional)</span></Label>
              <Input id="description" v-model="form.description" placeholder="e.g. SMB deals under $50k" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="dialogOpen = false">Cancel</Button>
            <Button @click="handleCreate" :disabled="createMutation.isPending.value">
              {{ createMutation.isPending.value ? 'Creating…' : 'Create' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <!-- Loading -->
    <div
      v-if="isLoading"
      class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground"
    >
      Loading…
    </div>

    <!-- Empty state -->
    <div
      v-else-if="!pipelines?.length"
      class="glass hairline rounded-xl py-14 px-6 text-center"
    >
      <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-4">
        <Workflow class="h-5 w-5 text-muted-foreground" />
      </div>
      <h3 class="text-sm font-semibold tracking-tight">No pipelines yet</h3>
      <p class="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
        Create a pipeline to start moving deals through stages in a Kanban view.
      </p>
      <Button class="mt-5 gap-1.5" @click="dialogOpen = true">
        <Plus class="h-4 w-4" />
        New pipeline
      </Button>
    </div>

    <!-- Pipeline grid -->
    <div
      v-else
      class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
    >
      <button
        v-for="p in pipelines"
        :key="p.id"
        type="button"
        class="group text-left glass hairline rounded-xl p-5 transition-colors hover:bg-white/2 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
        @click="navigateTo(`/pipelines/${p.id}`)"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="flex items-start gap-3 min-w-0">
            <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <Workflow class="h-4 w-4 text-primary" />
            </div>
            <div class="min-w-0">
              <h3 class="font-semibold tracking-tight truncate">{{ p.name }}</h3>
              <p v-if="p.description" class="text-xs text-muted-foreground mt-0.5 truncate">
                {{ p.description }}
              </p>
              <p v-else class="text-xs text-muted-foreground mt-0.5">
                {{ p.stages.length }} stage{{ p.stages.length === 1 ? '' : 's' }}
              </p>
            </div>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <Badge v-if="p.isDefault" variant="secondary" class="text-xs">Default</Badge>
            <ArrowRight class="h-4 w-4 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
          </div>
        </div>

        <!-- Stage preview — small coloured dots + labels showing the flow.
             Deliberately neutral: no alarming icons next to "Closed Lost". -->
        <div class="mt-4 flex flex-wrap items-center gap-x-2 gap-y-1.5">
          <template v-for="(stage, idx) in p.stages" :key="stage.id">
            <span class="inline-flex items-center gap-1.5 text-xs">
              <span
                class="h-1.5 w-1.5 rounded-full"
                :class="stageDotClass(stage.stageType)"
              />
              <span class="text-muted-foreground">{{ stage.name }}</span>
            </span>
            <span
              v-if="idx < p.stages.length - 1"
              class="text-muted-foreground/40"
            >·</span>
          </template>
        </div>
      </button>
    </div>
  </div>
</template>
