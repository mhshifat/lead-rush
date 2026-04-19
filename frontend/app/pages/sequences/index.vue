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
  Plus, Activity, ArrowRight, Workflow, UserCheck, Reply as ReplyIcon,
} from 'lucide-vue-next'

definePageMeta({
  middleware: 'auth',
})

const { data: sequences, isLoading } = useSequences()
const { data: mailboxes } = useMailboxes()
const createMutation = useCreateSequence()

const dialogOpen = ref(false)
const form = ref({ name: '', description: '', defaultMailboxId: '' })
const createErrors = useFieldErrors()

watch(() => form.value.name, v => { if (v.trim()) createErrors.remove('name') })
watch(dialogOpen, (open) => { if (open) createErrors.clear() })

async function handleCreate() {
  createErrors.clear()
  if (!form.value.name.trim()) createErrors.set('name', 'Name is required.')
  if (Object.keys(createErrors.map).length) return

  try {
    const sequence = await createMutation.mutateAsync({
      name: form.value.name,
      description: form.value.description || undefined,
      defaultMailboxId: form.value.defaultMailboxId || undefined,
    })
    toast.success('Sequence created')
    dialogOpen.value = false
    form.value = { name: '', description: '', defaultMailboxId: '' }
    navigateTo(`/sequences/${sequence.id}`)
  } catch (error: any) {
    createErrors.fromServerError(error, 'Failed to create sequence')
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

// Colored dot for the sequence-status tile so it doubles as an at-a-glance health signal.
function statusAccent(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'bg-emerald-400'
    case 'DRAFT':  return 'bg-muted-foreground/50'
    case 'PAUSED': return 'bg-amber-400'
    default: return 'bg-muted-foreground/50'
  }
}
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Header -->
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Sequences</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          Multi-step outreach campaigns — email, delay, and manual task steps chained together.
        </p>
      </div>
      <Dialog v-model:open="dialogOpen">
        <DialogTrigger as-child>
          <Button class="gap-1.5">
            <Plus class="h-4 w-4" />
            New sequence
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create sequence</DialogTitle>
            <DialogDescription>
              Starts as a DRAFT — you can add and reorder steps before activating.
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
                placeholder="Outbound Q2 campaign"
                :class="createErrors.has('name') ? 'border-destructive' : ''"
              />
              <SharedFormError :message="createErrors.get('name')" />
            </div>
            <div class="space-y-2">
              <Label for="description">Description <span class="text-muted-foreground font-normal">(optional)</span></Label>
              <Input id="description" v-model="form.description" placeholder="e.g. cold outreach to CTOs" />
            </div>
            <div class="space-y-2">
              <Label for="mailbox">Default mailbox</Label>
              <Select v-model="form.defaultMailboxId">
                <SelectTrigger id="mailbox" class="w-full">
                  <SelectValue placeholder="Choose at enrollment time" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="mb in mailboxes" :key="mb.id" :value="mb.id">
                    {{ mb.name }} ({{ mb.email }})
                  </SelectItem>
                </SelectContent>
              </Select>
              <p class="text-xs text-muted-foreground">
                Leave blank to pick a sending mailbox per-enrollment.
              </p>
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
      Loading sequences…
    </div>

    <!-- Empty state -->
    <div
      v-else-if="!sequences?.length"
      class="glass hairline rounded-xl py-14 px-6 text-center"
    >
      <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-4">
        <Activity class="h-5 w-5 text-muted-foreground" />
      </div>
      <h3 class="text-sm font-semibold tracking-tight">No sequences yet</h3>
      <p class="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
        A sequence chains emails, delays, and tasks to automate a multi-touch outreach motion.
      </p>
      <Button class="mt-5 gap-1.5" @click="dialogOpen = true">
        <Plus class="h-4 w-4" />
        New sequence
      </Button>
    </div>

    <!-- Grid of sequence cards -->
    <div
      v-else
      class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
    >
      <button
        v-for="seq in sequences"
        :key="seq.id"
        type="button"
        class="group text-left glass hairline rounded-xl p-5 transition-colors hover:bg-white/2 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
        @click="navigateTo(`/sequences/${seq.id}`)"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="flex items-start gap-3 min-w-0">
            <div class="relative h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <Activity class="h-4 w-4 text-primary" />
              <!-- Status dot sits on top-right of the icon tile. -->
              <span
                class="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full ring-2 ring-background"
                :class="statusAccent(seq.status)"
              />
            </div>
            <div class="min-w-0">
              <h3 class="font-semibold tracking-tight truncate">{{ seq.name }}</h3>
              <p v-if="seq.description" class="text-xs text-muted-foreground mt-0.5 truncate">
                {{ seq.description }}
              </p>
              <p v-else class="text-xs text-muted-foreground mt-0.5">
                Created as a draft
              </p>
            </div>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <Badge :variant="statusBadgeVariant(seq.status)" class="text-xs">
              {{ seq.status }}
            </Badge>
            <ArrowRight class="h-4 w-4 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
          </div>
        </div>

        <!-- Metrics strip -->
        <div
          class="mt-4 grid grid-cols-3 gap-3 pt-4"
          style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
        >
          <div class="flex items-center gap-2">
            <Workflow class="h-3.5 w-3.5 text-muted-foreground shrink-0" />
            <div class="min-w-0">
              <p class="text-xs text-muted-foreground">Steps</p>
              <p class="text-sm font-semibold tabular-nums">{{ seq.steps.length }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <UserCheck class="h-3.5 w-3.5 text-muted-foreground shrink-0" />
            <div class="min-w-0">
              <p class="text-xs text-muted-foreground">Enrolled</p>
              <p class="text-sm font-semibold tabular-nums">{{ seq.totalEnrolled }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <ReplyIcon class="h-3.5 w-3.5 text-muted-foreground shrink-0" />
            <div class="min-w-0">
              <p class="text-xs text-muted-foreground">Replied</p>
              <p class="text-sm font-semibold tabular-nums">{{ seq.totalReplied }}</p>
            </div>
          </div>
        </div>
      </button>
    </div>
  </div>
</template>
