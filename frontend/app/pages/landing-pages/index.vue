<!--
  Landing pages list — create new, see stats, click to edit.
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

const { data: pages, isLoading } = useLandingPages()
const createMutation = useCreateLandingPage()
const publishMutation = usePublishLandingPage()
const deleteMutation = useDeleteLandingPage()

const dialogOpen = ref(false)
const form = ref({ name: '', metaTitle: '' })

async function handleCreate() {
  if (!form.value.name.trim()) {
    toast.error('Name is required')
    return
  }
  try {
    const page = await createMutation.mutateAsync({
      name: form.value.name,
      metaTitle: form.value.metaTitle || undefined,
      blocks: '[]',
    })
    toast.success('Page created')
    dialogOpen.value = false
    form.value = { name: '', metaTitle: '' }
    navigateTo(`/landing-pages/${page.id}/edit`)
  } catch (error: any) {
    toast.error(error?.data?.error?.message || 'Failed to create page')
  }
}

async function handleTogglePublish(id: string, currentStatus: string) {
  try {
    await publishMutation.mutateAsync({ id, publish: currentStatus === 'DRAFT' })
    toast.success(currentStatus === 'DRAFT' ? 'Published' : 'Unpublished')
  } catch (error: any) {
    toast.error(error?.data?.error?.message || 'Failed to update status')
  }
}

async function handleDelete(id: string, name: string) {
  if (!confirm(`Delete page "${name}"?`)) return
  try {
    await deleteMutation.mutateAsync(id)
    toast.success('Page deleted')
  } catch {
    toast.error('Failed to delete page')
  }
}

function conversionRate(views: number, conversions: number): string {
  if (views === 0) return '—'
  return ((conversions / views) * 100).toFixed(1) + '%'
}

function publicUrl(slug: string): string {
  if (import.meta.client) {
    return `${window.location.origin}/p/${slug}`
  }
  return `/p/${slug}`
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold">Landing Pages</h1>
        <p class="text-sm text-muted-foreground">Create block-based pages to capture leads.</p>
      </div>
      <Dialog v-model:open="dialogOpen">
        <DialogTrigger as-child>
          <Button>+ New Page</Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create Landing Page</DialogTitle>
          </DialogHeader>
          <div class="space-y-4">
            <div class="space-y-2">
              <Label for="name">Name *</Label>
              <Input id="name" v-model="form.name" placeholder="Q2 Campaign Landing" />
            </div>
            <div class="space-y-2">
              <Label for="metaTitle">Meta Title (SEO)</Label>
              <Input id="metaTitle" v-model="form.metaTitle" placeholder="Get 20% off this quarter" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="dialogOpen = false">Cancel</Button>
            <Button @click="handleCreate" :disabled="createMutation.isPending.value">
              {{ createMutation.isPending.value ? 'Creating...' : 'Create & Edit' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">Loading...</div>

    <div v-else-if="!pages?.length" class="text-center py-12 text-muted-foreground">
      No pages yet. Create your first landing page to start capturing leads.
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <Card v-for="page in pages" :key="page.id">
        <CardHeader>
          <div class="flex items-start justify-between">
            <div class="flex-1 min-w-0">
              <CardTitle class="text-base truncate">{{ page.name }}</CardTitle>
              <CardDescription class="font-mono text-xs truncate">/p/{{ page.slug }}</CardDescription>
            </div>
            <Badge :variant="page.status === 'PUBLISHED' ? 'default' : 'secondary'">
              {{ page.status }}
            </Badge>
          </div>
        </CardHeader>
        <CardContent class="space-y-3">
          <div class="grid grid-cols-3 gap-2 text-sm">
            <div>
              <div class="text-xs text-muted-foreground">Views</div>
              <div class="font-medium">{{ page.viewCount }}</div>
            </div>
            <div>
              <div class="text-xs text-muted-foreground">Submits</div>
              <div class="font-medium">{{ page.conversionCount }}</div>
            </div>
            <div>
              <div class="text-xs text-muted-foreground">Rate</div>
              <div class="font-medium">{{ conversionRate(page.viewCount, page.conversionCount) }}</div>
            </div>
          </div>

          <div class="flex gap-2 pt-2">
            <Button size="sm" variant="outline" class="flex-1" @click="navigateTo(`/landing-pages/${page.id}/edit`)">
              Edit
            </Button>
            <Button
              size="sm"
              :variant="page.status === 'PUBLISHED' ? 'outline' : 'default'"
              class="flex-1"
              @click="handleTogglePublish(page.id, page.status)"
              :disabled="publishMutation.isPending.value"
            >
              {{ page.status === 'PUBLISHED' ? 'Unpublish' : 'Publish' }}
            </Button>
          </div>

          <div class="flex gap-2">
            <a
              v-if="page.status === 'PUBLISHED'"
              :href="publicUrl(page.slug)"
              target="_blank"
              class="flex-1 text-center text-xs text-primary hover:underline"
            >
              Open public page ↗
            </a>
            <button
              class="text-xs text-muted-foreground hover:text-destructive"
              @click="handleDelete(page.id, page.name)"
            >✕ Delete</button>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
