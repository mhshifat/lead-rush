<!--
  Tasks page — shows tasks created by sequences (CALL, LINKEDIN_*, MANUAL)
  plus manually created tasks. User can complete or delete.
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Badge } from '~/components/ui/badge'
import { toast } from 'vue-sonner'
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

function taskTypeIcon(type: string): string {
  switch (type) {
    case 'CALL': return '📞'
    case 'LINKEDIN_MESSAGE': return '💬'
    case 'LINKEDIN_CONNECT': return '🤝'
    case 'MANUAL':
    default: return '📋'
  }
}

function statusBadgeVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'PENDING': return 'default'
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
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-3xl font-bold">Tasks</h1>
      <p class="text-sm text-muted-foreground">
        Follow-up actions — from sequences or added manually.
      </p>
    </div>

    <!-- Status filter -->
    <div class="flex gap-2">
      <Button
        size="sm"
        :variant="filters.status === 'PENDING' ? 'default' : 'outline'"
        @click="setStatus('PENDING')"
      >Pending</Button>
      <Button
        size="sm"
        :variant="filters.status === 'COMPLETED' ? 'default' : 'outline'"
        @click="setStatus('COMPLETED')"
      >Completed</Button>
      <Button
        size="sm"
        :variant="!filters.status ? 'default' : 'outline'"
        @click="setStatus(undefined)"
      >All</Button>
    </div>

    <Card>
      <CardContent class="pt-6">
        <div v-if="isLoading" class="text-center py-8 text-muted-foreground">
          Loading tasks...
        </div>

        <div v-else-if="!tasksPage?.content.length" class="text-center py-12 text-muted-foreground">
          No tasks. Sequences with CALL/LINKEDIN/TASK steps will create tasks here.
        </div>

        <ul v-else class="space-y-2">
          <li
            v-for="task in tasksPage.content"
            :key="task.id"
            class="flex items-center gap-4 p-3 rounded-md border bg-card"
          >
            <div class="text-2xl">{{ taskTypeIcon(task.taskType) }}</div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1">
                <span class="font-medium truncate">{{ task.title }}</span>
                <Badge :variant="statusBadgeVariant(task.status)">{{ task.status }}</Badge>
              </div>
              <div class="text-xs text-muted-foreground space-x-3">
                <NuxtLink
                  v-if="task.contactId"
                  :to="`/contacts/${task.contactId}`"
                  class="hover:text-foreground"
                  @click.stop
                >
                  {{ task.contactFullName ?? 'Contact' }}
                </NuxtLink>
                <span v-if="task.sequenceName">via {{ task.sequenceName }}</span>
                <span v-if="task.dueAt">due {{ formatDate(task.dueAt) }}</span>
              </div>
              <p v-if="task.description" class="text-sm text-muted-foreground mt-1">
                {{ task.description }}
              </p>
            </div>
            <div class="flex gap-2">
              <Button
                v-if="task.status === 'PENDING'"
                size="sm"
                variant="outline"
                @click="handleComplete(task.id, task.title)"
                :disabled="completeMutation.isPending.value"
              >Complete</Button>
              <Button
                size="sm"
                variant="ghost"
                class="text-destructive"
                @click="handleDelete(task.id, task.title)"
              >✕</Button>
            </div>
          </li>
        </ul>
      </CardContent>
    </Card>
  </div>
</template>
