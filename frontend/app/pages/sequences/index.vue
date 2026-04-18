<!--
  Sequences List Page — shows all outreach sequences with status and stats.
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

const { data: sequences, isLoading } = useSequences()
const { data: mailboxes } = useMailboxes()
const createMutation = useCreateSequence()

const dialogOpen = ref(false)
const form = ref({ name: '', description: '', defaultMailboxId: '' })

async function handleCreate() {
  if (!form.value.name.trim()) {
    toast.error('Name is required')
    return
  }

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
    toast.error(error?.data?.error?.message || 'Failed to create sequence')
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
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold">Sequences</h1>
        <p class="text-sm text-muted-foreground">Multi-step outreach campaigns</p>
      </div>
      <Dialog v-model:open="dialogOpen">
        <DialogTrigger as-child>
          <Button>+ New Sequence</Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create Sequence</DialogTitle>
          </DialogHeader>
          <div class="space-y-4">
            <div class="space-y-2">
              <Label for="name">Name *</Label>
              <Input id="name" v-model="form.name" placeholder="Outbound Q2 Campaign" />
            </div>
            <div class="space-y-2">
              <Label for="description">Description</Label>
              <Input id="description" v-model="form.description" placeholder="Optional description" />
            </div>
            <div class="space-y-2">
              <Label for="mailbox">Default Mailbox</Label>
              <select
                id="mailbox"
                v-model="form.defaultMailboxId"
                class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              >
                <option value="">Choose at enrollment time</option>
                <option v-for="mb in mailboxes" :key="mb.id" :value="mb.id">
                  {{ mb.name }} ({{ mb.email }})
                </option>
              </select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="dialogOpen = false">Cancel</Button>
            <Button @click="handleCreate" :disabled="createMutation.isPending.value">Create</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">Loading sequences...</div>

    <div v-else-if="!sequences?.length" class="text-center py-12">
      <p class="text-muted-foreground">No sequences yet. Create one to start automating outreach.</p>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <Card
        v-for="seq in sequences"
        :key="seq.id"
        class="cursor-pointer hover:border-primary transition-colors"
        @click="navigateTo(`/sequences/${seq.id}`)"
      >
        <CardHeader>
          <div class="flex items-start justify-between">
            <div>
              <CardTitle class="text-base">{{ seq.name }}</CardTitle>
              <CardDescription v-if="seq.description">{{ seq.description }}</CardDescription>
            </div>
            <Badge :variant="statusBadgeVariant(seq.status)">{{ seq.status }}</Badge>
          </div>
        </CardHeader>
        <CardContent>
          <div class="grid grid-cols-3 gap-2 text-sm">
            <div>
              <div class="text-xs text-muted-foreground">Steps</div>
              <div class="font-medium">{{ seq.steps.length }}</div>
            </div>
            <div>
              <div class="text-xs text-muted-foreground">Enrolled</div>
              <div class="font-medium">{{ seq.totalEnrolled }}</div>
            </div>
            <div>
              <div class="text-xs text-muted-foreground">Replied</div>
              <div class="font-medium">{{ seq.totalReplied }}</div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
