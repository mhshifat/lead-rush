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
  Plus, ClipboardList, ListChecks, Workflow, MessageSquareText, Calendar,
} from 'lucide-vue-next'

definePageMeta({
  middleware: 'auth',
})

const { data: forms, isLoading } = useForms()
const { data: sequences } = useSequences()
const createMutation = useCreateForm()

const dialogOpen = ref(false)
const form = ref({
  name: '',
  description: '',
  successMessage: "Thank you! We'll be in touch.",
  autoEnrollSequenceId: '',
})
const createErrors = useFieldErrors()

// Active sequences can be auto-enroll targets
const activeSequences = computed(() => (sequences.value ?? []).filter(s => s.status === 'ACTIVE'))

watch(() => form.value.name, v => { if (v.trim()) createErrors.remove('name') })
watch(dialogOpen, (open) => { if (open) createErrors.clear() })

async function handleCreate() {
  createErrors.clear()
  if (!form.value.name.trim()) createErrors.set('name', 'Name is required.')
  if (Object.keys(createErrors.map).length) return
  try {
    await createMutation.mutateAsync({
      name: form.value.name,
      description: form.value.description || undefined,
      successMessage: form.value.successMessage,
      autoEnrollSequenceId: form.value.autoEnrollSequenceId || undefined,
      fields: JSON.stringify([
        { key: 'firstName', label: 'First Name', type: 'text', required: true },
        { key: 'email', label: 'Email', type: 'email', required: true },
      ]),
    })
    toast.success('Form created')
    dialogOpen.value = false
    form.value = { name: '', description: '', successMessage: "Thank you! We'll be in touch.", autoEnrollSequenceId: '' }
  } catch (error: any) {
    createErrors.fromServerError(error, 'Failed to create form')
  }
}

function fieldCount(fields: any): number {
  if (Array.isArray(fields)) return fields.length
  if (typeof fields === 'string') {
    try {
      const parsed = JSON.parse(fields)
      return Array.isArray(parsed) ? parsed.length : 0
    } catch { return 0 }
  }
  return 0
}

function sequenceName(id: string | null | undefined): string | null {
  if (!id) return null
  return sequences.value?.find(s => s.id === id)?.name ?? null
}

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
  }).format(new Date(iso))
}

const totals = computed(() => {
  const list = forms.value ?? []
  const autoEnrolled = list.filter(f => !!f.autoEnrollSequenceId).length
  return {
    total: list.length,
    autoEnrolled,
    standalone: list.length - autoEnrolled,
  }
})
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Header -->
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Forms</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          Collect data from landing pages — auto-creates contacts on submit.
        </p>
      </div>
      <Dialog v-model:open="dialogOpen">
        <DialogTrigger as-child>
          <Button class="gap-1.5">
            <Plus class="h-4 w-4" />
            New form
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create form</DialogTitle>
            <DialogDescription>
              Default fields (First name, Email) are added — you can customise them later.
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
                placeholder="Newsletter signup"
                :class="createErrors.has('name') ? 'border-destructive' : ''"
              />
              <SharedFormError :message="createErrors.get('name')" />
            </div>
            <div class="space-y-2">
              <Label for="description">Description <span class="text-muted-foreground font-normal">(optional)</span></Label>
              <Input id="description" v-model="form.description" placeholder="e.g. homepage hero form" />
            </div>
            <div class="space-y-2">
              <Label for="successMessage">Success message</Label>
              <Input id="successMessage" v-model="form.successMessage" />
            </div>
            <div class="space-y-2">
              <Label for="autoEnrollSequence">Auto-enroll in sequence <span class="text-muted-foreground font-normal">(optional)</span></Label>
              <Select v-model="form.autoEnrollSequenceId">
                <SelectTrigger id="autoEnrollSequence" class="w-full">
                  <SelectValue placeholder="Don't auto-enroll" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="s in activeSequences" :key="s.id" :value="s.id">
                    {{ s.name }}
                  </SelectItem>
                </SelectContent>
              </Select>
              <p v-if="!activeSequences.length" class="text-xs text-muted-foreground">
                No active sequences yet. Activate a sequence to enable auto-enrollment.
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

    <!-- Summary strip -->
    <div
      v-if="forms?.length"
      class="glass hairline rounded-xl grid grid-cols-3 divide-x divide-white/5"
    >
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Forms</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.total }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Auto-enrolling</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.autoEnrolled }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Standalone</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.standalone }}</p>
      </div>
    </div>

    <!-- Loading -->
    <div
      v-if="isLoading"
      class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground"
    >
      Loading forms…
    </div>

    <!-- Empty state -->
    <div
      v-else-if="!forms?.length"
      class="glass hairline rounded-xl py-14 px-6 text-center"
    >
      <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-4">
        <ClipboardList class="h-5 w-5 text-muted-foreground" />
      </div>
      <h3 class="text-sm font-semibold tracking-tight">No forms yet</h3>
      <p class="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
        A form captures visitor details, creates a contact, and can auto-enroll them in a sequence.
      </p>
      <Button class="mt-5 gap-1.5" @click="dialogOpen = true">
        <Plus class="h-4 w-4" />
        New form
      </Button>
    </div>

    <!-- Grid of form cards -->
    <div
      v-else
      class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
    >
      <div
        v-for="f in forms"
        :key="f.id"
        class="glass hairline rounded-xl p-5 transition-colors hover:bg-white/2 flex flex-col"
      >
        <!-- Header: icon + name/description + auto-enroll badge -->
        <div class="flex items-start justify-between gap-3">
          <div class="flex items-start gap-3 min-w-0">
            <div class="relative h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <ClipboardList class="h-4 w-4 text-primary" />
              <span
                v-if="f.autoEnrollSequenceId"
                class="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full bg-emerald-400 ring-2 ring-background"
                title="Auto-enrolls into a sequence"
              />
            </div>
            <div class="min-w-0">
              <h3 class="font-semibold tracking-tight truncate">{{ f.name }}</h3>
              <p v-if="f.description" class="text-xs text-muted-foreground mt-0.5 truncate">
                {{ f.description }}
              </p>
              <p v-else class="text-xs text-muted-foreground mt-0.5">
                Standalone form
              </p>
            </div>
          </div>
          <Badge
            v-if="f.autoEnrollSequenceId"
            variant="default"
            class="text-xs shrink-0"
          >
            Auto-enroll
          </Badge>
        </div>

        <!-- Metrics strip -->
        <div
          class="mt-4 grid grid-cols-2 gap-3 pt-4"
          style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
        >
          <div class="flex items-center gap-2">
            <ListChecks class="h-3.5 w-3.5 text-muted-foreground shrink-0" />
            <div class="min-w-0">
              <p class="text-xs text-muted-foreground">Fields</p>
              <p class="text-sm font-semibold tabular-nums">{{ fieldCount(f.fields) }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <Workflow class="h-3.5 w-3.5 text-muted-foreground shrink-0" />
            <div class="min-w-0">
              <p class="text-xs text-muted-foreground">Sequence</p>
              <p class="text-sm font-semibold truncate" :title="sequenceName(f.autoEnrollSequenceId) ?? ''">
                {{ sequenceName(f.autoEnrollSequenceId) ?? '—' }}
              </p>
            </div>
          </div>
        </div>

        <!-- Success message preview -->
        <div
          v-if="f.successMessage"
          class="mt-3 pt-3 flex items-start gap-2 text-xs text-muted-foreground"
          style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
        >
          <MessageSquareText class="h-3.5 w-3.5 shrink-0 mt-0.5" />
          <p class="line-clamp-2 italic">"{{ f.successMessage }}"</p>
        </div>

        <!-- Footer: created date -->
        <div
          class="mt-3 pt-3 flex items-center justify-between text-xs text-muted-foreground"
          style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
        >
          <span class="inline-flex items-center gap-1">
            <Calendar class="h-3 w-3" />
            Created {{ formatDate(f.createdAt) }}
          </span>
          <span class="font-mono text-[10px] opacity-60">{{ f.id.slice(0, 8) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
