<!--
  Pipelines list — user can create pipelines, click through to Kanban board.
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

const { data: pipelines, isLoading } = usePipelines()
const createMutation = useCreatePipeline()

const dialogOpen = ref(false)
const form = ref({ name: '', description: '' })

async function handleCreate() {
  if (!form.value.name.trim()) {
    toast.error('Name is required')
    return
  }
  try {
    const p = await createMutation.mutateAsync(form.value)
    toast.success('Pipeline created')
    dialogOpen.value = false
    form.value = { name: '', description: '' }
    navigateTo(`/pipelines/${p.id}`)
  } catch (error: any) {
    toast.error(error?.data?.error?.message || 'Failed to create pipeline')
  }
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold">Pipelines</h1>
        <p class="text-sm text-muted-foreground">Track deals through customizable stages.</p>
      </div>
      <Dialog v-model:open="dialogOpen">
        <DialogTrigger as-child>
          <Button>+ New Pipeline</Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create Pipeline</DialogTitle>
          </DialogHeader>
          <div class="space-y-4">
            <div class="space-y-2">
              <Label for="name">Name *</Label>
              <Input id="name" v-model="form.name" placeholder="Sales Pipeline" />
            </div>
            <div class="space-y-2">
              <Label for="description">Description</Label>
              <Input id="description" v-model="form.description" />
            </div>
            <p class="text-xs text-muted-foreground">
              4 default stages (Discovery, Proposal, Closed Won, Closed Lost) will be created automatically. You can customize them later.
            </p>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="dialogOpen = false">Cancel</Button>
            <Button @click="handleCreate" :disabled="createMutation.isPending.value">
              {{ createMutation.isPending.value ? 'Creating...' : 'Create' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">Loading...</div>

    <div v-else-if="!pipelines?.length" class="text-center py-12 text-muted-foreground">
      No pipelines yet. Create one to start tracking deals.
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <Card
        v-for="p in pipelines"
        :key="p.id"
        class="cursor-pointer hover:border-primary transition-colors"
        @click="navigateTo(`/pipelines/${p.id}`)"
      >
        <CardHeader>
          <div class="flex items-start justify-between">
            <div>
              <CardTitle class="text-base">{{ p.name }}</CardTitle>
              <CardDescription v-if="p.description">{{ p.description }}</CardDescription>
            </div>
            <Badge v-if="p.isDefault" variant="secondary">Default</Badge>
          </div>
        </CardHeader>
        <CardContent>
          <p class="text-sm text-muted-foreground">
            {{ p.stages.length }} stages
          </p>
          <div class="flex gap-1 mt-2 flex-wrap">
            <Badge v-for="stage in p.stages" :key="stage.id" variant="outline" class="text-xs">
              {{ stage.name }}
            </Badge>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
