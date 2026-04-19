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
  Plus, LayoutTemplate, Eye, UserCheck, TrendingUp,
  ArrowUpRight, Pencil, Globe, EyeOff, Trash2,
} from 'lucide-vue-next'

definePageMeta({
  middleware: 'auth',
})
useHead({ title: 'Landing pages' })

const { data: pages, isLoading } = useLandingPages()
const createMutation = useCreateLandingPage()
const publishMutation = usePublishLandingPage()
const deleteMutation = useDeleteLandingPage()

const dialogOpen = ref(false)
const form = ref({ name: '', metaTitle: '' })
const createErrors = useFieldErrors()

watch(() => form.value.name, v => { if (v.trim()) createErrors.remove('name') })
watch(dialogOpen, (open) => { if (open) createErrors.clear() })

async function handleCreate() {
  createErrors.clear()
  if (!form.value.name.trim()) createErrors.set('name', 'Name is required.')
  if (Object.keys(createErrors.map).length) return
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
    createErrors.fromServerError(error, 'Failed to create page')
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
  const ok = await useConfirm().ask({
    title: `Delete page "${name}"?`,
    description: 'This cannot be undone.',
    confirmLabel: 'Delete',
    variant: 'destructive',
  })
  if (!ok) return
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

// Coloured status dot on the card's icon tile — doubles as an at-a-glance health signal.
function statusAccent(status: string): string {
  switch (status) {
    case 'PUBLISHED': return 'bg-emerald-400'
    case 'DRAFT':     return 'bg-muted-foreground/50'
    default:          return 'bg-muted-foreground/50'
  }
}

function statusBadgeVariant(status: string): 'default' | 'secondary' | 'outline' {
  return status === 'PUBLISHED' ? 'default' : 'secondary'
}

// Totals strip above the grid.
const totals = computed(() => {
  const list = pages.value ?? []
  const published = list.filter(p => p.status === 'PUBLISHED').length
  const views = list.reduce((a, p) => a + (p.viewCount ?? 0), 0)
  const conversions = list.reduce((a, p) => a + (p.conversionCount ?? 0), 0)
  return {
    total: list.length,
    published,
    views,
    conversions,
    rate: views === 0 ? '—' : ((conversions / views) * 100).toFixed(1) + '%',
  }
})
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Header -->
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Landing pages</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          Block-based pages to capture leads — publishable, shareable, trackable.
        </p>
      </div>
      <Dialog v-model:open="dialogOpen">
        <DialogTrigger as-child>
          <Button class="gap-1.5">
            <Plus class="h-4 w-4" />
            New page
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create landing page</DialogTitle>
            <DialogDescription>
              Starts as a DRAFT — add blocks, then publish when ready.
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
                placeholder="Q2 campaign landing"
                :class="createErrors.has('name') ? 'border-destructive' : ''"
              />
              <SharedFormError :message="createErrors.get('name')" />
            </div>
            <div class="space-y-2">
              <Label for="metaTitle">Meta title <span class="text-muted-foreground font-normal">(SEO)</span></Label>
              <Input id="metaTitle" v-model="form.metaTitle" placeholder="Get 20% off this quarter" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="dialogOpen = false">Cancel</Button>
            <Button @click="handleCreate" :disabled="createMutation.isPending.value">
              {{ createMutation.isPending.value ? 'Creating…' : 'Create & edit' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <!-- Summary strip (only when there are pages) -->
    <div
      v-if="pages?.length"
      class="glass hairline rounded-xl grid grid-cols-2 md:grid-cols-4 divide-x divide-white/5"
    >
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Pages</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.total }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Published</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.published }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Total views</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.views }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Avg conversion</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ totals.rate }}</p>
      </div>
    </div>

    <!-- Loading -->
    <div
      v-if="isLoading"
      class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground"
    >
      Loading pages…
    </div>

    <!-- Empty state -->
    <div
      v-else-if="!pages?.length"
      class="glass hairline rounded-xl py-14 px-6 text-center"
    >
      <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-4">
        <LayoutTemplate class="h-5 w-5 text-muted-foreground" />
      </div>
      <h3 class="text-sm font-semibold tracking-tight">No landing pages yet</h3>
      <p class="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
        Assemble hero, form, pricing, and more blocks — then publish to share a public URL.
      </p>
      <Button class="mt-5 gap-1.5" @click="dialogOpen = true">
        <Plus class="h-4 w-4" />
        New page
      </Button>
    </div>

    <!-- Grid of page cards -->
    <div
      v-else
      class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
    >
      <div
        v-for="page in pages"
        :key="page.id"
        class="group glass hairline rounded-xl p-5 transition-colors hover:bg-white/2 flex flex-col"
      >
        <!-- Header row: icon + name/slug + status badge -->
        <div class="flex items-start justify-between gap-3">
          <div class="flex items-start gap-3 min-w-0">
            <div class="relative h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <LayoutTemplate class="h-4 w-4 text-primary" />
              <span
                class="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full ring-2 ring-background"
                :class="statusAccent(page.status)"
              />
            </div>
            <div class="min-w-0">
              <h3 class="font-semibold tracking-tight truncate">{{ page.name }}</h3>
              <p class="text-xs text-muted-foreground font-mono truncate">/p/{{ page.slug }}</p>
            </div>
          </div>
          <Badge :variant="statusBadgeVariant(page.status)" class="text-xs shrink-0">
            {{ page.status }}
          </Badge>
        </div>

        <!-- Metrics strip -->
        <div
          class="mt-4 grid grid-cols-3 gap-3 pt-4"
          style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
        >
          <div class="flex items-center gap-2">
            <Eye class="h-3.5 w-3.5 text-muted-foreground shrink-0" />
            <div class="min-w-0">
              <p class="text-xs text-muted-foreground">Views</p>
              <p class="text-sm font-semibold tabular-nums">{{ page.viewCount }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <UserCheck class="h-3.5 w-3.5 text-muted-foreground shrink-0" />
            <div class="min-w-0">
              <p class="text-xs text-muted-foreground">Submits</p>
              <p class="text-sm font-semibold tabular-nums">{{ page.conversionCount }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <TrendingUp class="h-3.5 w-3.5 text-muted-foreground shrink-0" />
            <div class="min-w-0">
              <p class="text-xs text-muted-foreground">Rate</p>
              <p class="text-sm font-semibold tabular-nums">{{ conversionRate(page.viewCount, page.conversionCount) }}</p>
            </div>
          </div>
        </div>

        <!-- Actions -->
        <div class="mt-4 flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            class="flex-1 gap-1.5"
            @click="navigateTo(`/landing-pages/${page.id}/edit`)"
          >
            <Pencil class="h-3.5 w-3.5" />
            Edit
          </Button>
          <Button
            size="sm"
            class="flex-1 gap-1.5"
            :variant="page.status === 'PUBLISHED' ? 'outline' : 'default'"
            :disabled="publishMutation.isPending.value"
            @click="handleTogglePublish(page.id, page.status)"
          >
            <component :is="page.status === 'PUBLISHED' ? EyeOff : Globe" class="h-3.5 w-3.5" />
            {{ page.status === 'PUBLISHED' ? 'Unpublish' : 'Publish' }}
          </Button>
        </div>

        <!-- Footer row: public link + delete -->
        <div
          class="mt-3 pt-3 flex items-center justify-between text-xs"
          style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
        >
          <a
            v-if="page.status === 'PUBLISHED'"
            :href="publicUrl(page.slug)"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center gap-1 text-primary hover:underline"
          >
            View live
            <ArrowUpRight class="h-3 w-3" />
          </a>
          <span v-else class="text-muted-foreground">Draft — not yet public</span>
          <button
            class="inline-flex items-center gap-1 text-muted-foreground hover:text-destructive transition-colors"
            @click="handleDelete(page.id, page.name)"
          >
            <Trash2 class="h-3 w-3" />
            Delete
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
