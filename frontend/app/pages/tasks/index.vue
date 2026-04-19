<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Badge } from '~/components/ui/badge'
import { toast } from 'vue-sonner'
import {
  Phone, MessageSquare, Handshake, ClipboardList,
  CheckCircle2, Trash2, Calendar, ArrowUpRight, Workflow, AlertTriangle,
  ListTodo, Check,
} from 'lucide-vue-next'
import type { TaskFilters } from '~/composables/useTasks'

definePageMeta({
  middleware: 'auth',
})

const filters = ref<TaskFilters>({
  status: 'PENDING',
  page: 0,
  size: 50,
})

const { data: tasksPage, isLoading } = useTasks(filters)
const completeMutation = useCompleteTask()
const deleteMutation = useDeleteTask()

const STATUS_TABS = [
  { key: 'PENDING',   label: 'Pending' },
  { key: 'COMPLETED', label: 'Completed' },
  { key: undefined,   label: 'All' },
] as const

function setStatus(status: string | undefined) {
  filters.value = { ...filters.value, status, page: 0 }
}

async function handleComplete(id: string, title: string) {
  try {
    await completeMutation.mutateAsync(id)
    toast.success(`Completed: ${title}`)
  } catch {
    toast.error('Failed to complete task')
  }
}

async function handleDelete(id: string, title: string) {
  const ok = await useConfirm().ask({
    title: `Delete task "${title}"?`,
    confirmLabel: 'Delete',
    variant: 'destructive',
  })
  if (!ok) return
  try {
    await deleteMutation.mutateAsync(id)
    toast.success('Task deleted')
  } catch {
    toast.error('Failed to delete task')
  }
}

function taskTypeIcon(type: string) {
  switch (type) {
    case 'CALL': return Phone
    case 'LINKEDIN_MESSAGE': return MessageSquare
    case 'LINKEDIN_CONNECT': return Handshake
    case 'MANUAL':
    default: return ClipboardList
  }
}

function taskTypeLabel(type: string): string {
  switch (type) {
    case 'CALL': return 'Call'
    case 'LINKEDIN_MESSAGE': return 'LinkedIn DM'
    case 'LINKEDIN_CONNECT': return 'LinkedIn connect'
    case 'MANUAL': return 'Manual'
    default: return type
  }
}

function statusBadgeVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'PENDING':   return 'default'
    case 'COMPLETED': return 'secondary'
    case 'CANCELLED': return 'outline'
    default: return 'secondary'
  }
}

function formatDate(date: string | null): string {
  if (!date) return ''
  return new Intl.DateTimeFormat('en-US', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(date))
}

function isOverdue(dueAt: string | null, status: string): boolean {
  if (!dueAt || status !== 'PENDING') return false
  return new Date(dueAt).getTime() < Date.now()
}

// Tally pending vs overdue across the loaded page — feeds the summary strip.
const totals = computed(() => {
  const list = tasksPage.value?.content ?? []
  const pending = list.filter(t => t.status === 'PENDING').length
  const overdue = list.filter(t => isOverdue(t.dueAt, t.status)).length
  const completed = list.filter(t => t.status === 'COMPLETED').length
  return {
    total: tasksPage.value?.totalElements ?? 0,
    pending,
    overdue,
    completed,
  }
})
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Header -->
    <div>
      <h1 class="text-2xl font-semibold tracking-tight">Tasks</h1>
      <p class="text-sm text-muted-foreground mt-0.5">
        Follow-up actions — created by sequences or added manually.
      </p>
    </div>

    <!-- Status tab pills -->
    <div class="glass hairline rounded-xl p-1 inline-flex gap-1">
      <button
        v-for="opt in STATUS_TABS"
        :key="opt.label"
        type="button"
        class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors"
        :class="filters.status === opt.key
          ? 'bg-primary/15 text-primary'
          : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
        @click="setStatus(opt.key)"
      >
        {{ opt.label }}
      </button>
    </div>

    <!-- Summary strip -->
    <div
      v-if="tasksPage?.content.length"
      class="glass hairline rounded-xl grid grid-cols-2 md:grid-cols-4 divide-x divide-white/5"
    >
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Showing</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ tasksPage.content.length }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Total</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.total }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Pending</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.pending }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Overdue</p>
        <p class="mt-1 text-xl font-semibold tabular-nums" :class="totals.overdue > 0 ? 'text-destructive' : ''">
          {{ totals.overdue }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <div
      v-if="isLoading"
      class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground"
    >
      Loading tasks…
    </div>

    <!-- Empty state -->
    <div
      v-else-if="!tasksPage?.content.length"
      class="glass hairline rounded-xl py-14 px-6 text-center"
    >
      <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-4">
        <ListTodo class="h-5 w-5 text-muted-foreground" />
      </div>
      <h3 class="text-sm font-semibold tracking-tight">
        {{ filters.status === 'PENDING' ? 'No pending tasks'
          : filters.status === 'COMPLETED' ? 'No completed tasks yet'
          : 'No tasks yet' }}
      </h3>
      <p class="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
        Sequences with CALL, LINKEDIN, or TASK steps automatically create tasks here when an enrolled contact reaches them.
      </p>
    </div>

    <!-- Task list -->
    <div v-else class="glass hairline rounded-xl overflow-hidden">
      <ul>
        <li
          v-for="(task, idx) in tasksPage.content"
          :key="task.id"
          class="flex items-start gap-3 px-4 py-3 transition-colors hover:bg-white/2"
          :style="idx > 0 ? 'border-top: 1px solid hsl(240 5% 100% / 0.05);' : ''"
        >
          <!-- Type icon tile -->
          <div class="relative h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
            <component :is="taskTypeIcon(task.taskType)" class="h-4 w-4 text-primary" />
            <span
              v-if="isOverdue(task.dueAt, task.status)"
              class="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full bg-destructive ring-2 ring-background"
              title="Overdue"
            />
          </div>

          <!-- Body -->
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <span
                class="font-medium truncate"
                :class="task.status === 'COMPLETED' ? 'line-through text-muted-foreground' : ''"
              >
                {{ task.title }}
              </span>
              <Badge variant="outline" class="text-[10px] uppercase tracking-wider">
                {{ taskTypeLabel(task.taskType) }}
              </Badge>
              <Badge :variant="statusBadgeVariant(task.status)" class="text-xs">
                {{ task.status }}
              </Badge>
            </div>

            <p v-if="task.description" class="text-xs text-muted-foreground mt-1 line-clamp-2">
              {{ task.description }}
            </p>

            <!-- Meta row -->
            <div class="flex items-center flex-wrap gap-x-3 gap-y-1 mt-1.5 text-xs text-muted-foreground">
              <NuxtLink
                v-if="task.contactId"
                :to="`/contacts/${task.contactId}`"
                class="inline-flex items-center gap-1 hover:text-primary transition-colors"
                @click.stop
              >
                {{ task.contactFullName ?? 'Contact' }}
                <ArrowUpRight class="h-3 w-3" />
              </NuxtLink>
              <span v-if="task.sequenceName" class="inline-flex items-center gap-1">
                <Workflow class="h-3 w-3" />
                {{ task.sequenceName }}
              </span>
              <span
                v-if="task.dueAt"
                class="inline-flex items-center gap-1"
                :class="isOverdue(task.dueAt, task.status) ? 'text-destructive font-medium' : ''"
              >
                <component :is="isOverdue(task.dueAt, task.status) ? AlertTriangle : Calendar" class="h-3 w-3" />
                {{ isOverdue(task.dueAt, task.status) ? 'Overdue · ' : 'Due ' }}{{ formatDate(task.dueAt) }}
              </span>
            </div>
          </div>

          <!-- Actions -->
          <div class="flex items-center gap-1 shrink-0">
            <Button
              v-if="task.status === 'PENDING'"
              size="sm"
              variant="outline"
              class="gap-1 h-8"
              :disabled="completeMutation.isPending.value"
              @click="handleComplete(task.id, task.title)"
            >
              <Check class="h-3.5 w-3.5" />
              Complete
            </Button>
            <Button
              v-else-if="task.status === 'COMPLETED'"
              size="sm"
              variant="ghost"
              class="gap-1 h-8 text-muted-foreground"
              disabled
            >
              <CheckCircle2 class="h-3.5 w-3.5" />
              Done
            </Button>
            <Button
              size="sm"
              variant="outline"
              class="h-8 w-8 p-0 text-muted-foreground hover:text-destructive"
              title="Delete"
              @click="handleDelete(task.id, task.title)"
            >
              <Trash2 class="h-3.5 w-3.5" />
            </Button>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>
