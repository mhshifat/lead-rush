<!--
  Block Editor — create/edit a landing page's blocks.

  LAYOUT:
    Left panel:  Block palette (click to add)
    Center:      Live preview + click a block to select it
    Right panel: Properties of the selected block (edit title, subtitle, image URL, etc.)

  DATA FLOW:
    Page is loaded with `blocks` = JSON array of { id, type, props }
    We store it as a reactive array locally (`blocks`)
    Click "Save" → PUT /landing-pages/{id} with JSON.stringify(blocks)
    Click "Publish" → POST /landing-pages/{id}/publish

  BLOCK TYPES:
    hero     — title + subtitle + CTA button
    text     — paragraph text (simple)
    image    — img src + alt text
    form     — references a form_id, shows a dropdown of forms to pick
    cta      — standalone button block
-->
<script setup lang="ts">
import { Card, CardContent } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { toast } from 'vue-sonner'

definePageMeta({
  middleware: 'auth',
  layout: 'default',
})

// ── Page state ──
const route = useRoute()
const pageId = computed(() => route.params.id as string)

const { data: page, isLoading } = useLandingPage(pageId)
const { data: forms } = useForms()
const updateMutation = useUpdateLandingPage()
const publishMutation = usePublishLandingPage()

// ── Block types (block palette) ──
type BlockType = 'hero' | 'text' | 'image' | 'form' | 'cta'

interface Block {
  id: string
  type: BlockType
  props: Record<string, any>
}

const blockDefinitions: Array<{ type: BlockType; icon: string; label: string; defaultProps: Record<string, any> }> = [
  {
    type: 'hero',
    icon: '🎯',
    label: 'Hero',
    defaultProps: {
      title: 'Your Headline',
      subtitle: 'A supporting subheadline goes here.',
      ctaText: 'Get Started',
      ctaUrl: '#',
    },
  },
  {
    type: 'text',
    icon: '📝',
    label: 'Text',
    defaultProps: { content: 'Add your text content here.' },
  },
  {
    type: 'image',
    icon: '🖼️',
    label: 'Image',
    defaultProps: {
      src: 'https://via.placeholder.com/800x400',
      alt: 'Image description',
    },
  },
  {
    type: 'form',
    icon: '📋',
    label: 'Form',
    defaultProps: { formId: '', submitText: 'Submit' },
  },
  {
    type: 'cta',
    icon: '🔘',
    label: 'CTA Button',
    defaultProps: { text: 'Click here', url: '#' },
  },
]

// ── Local state — reactive copy of the page's blocks ──
const blocks = ref<Block[]>([])
const selectedBlockId = ref<string | null>(null)
const pageName = ref('')
const pageSlug = ref('')
const metaTitle = ref('')
const metaDescription = ref('')

// Sync local state from server data when page loads
watch(page, (loaded) => {
  if (!loaded) return
  pageName.value = loaded.name
  pageSlug.value = loaded.slug
  metaTitle.value = loaded.metaTitle ?? ''
  metaDescription.value = loaded.metaDescription ?? ''
  try {
    // backend returns blocks as a JSON object (via @JsonRawValue) or string
    const parsed = typeof loaded.blocks === 'string'
      ? JSON.parse(loaded.blocks)
      : (loaded.blocks ?? [])
    blocks.value = Array.isArray(parsed) ? parsed : []
  } catch {
    blocks.value = []
  }
}, { immediate: true })

const selectedBlock = computed(() =>
  blocks.value.find(b => b.id === selectedBlockId.value) ?? null
)

// ── Block operations ──
function addBlock(type: BlockType) {
  const def = blockDefinitions.find(d => d.type === type)!
  const newBlock: Block = {
    id: crypto.randomUUID(),
    type,
    props: { ...def.defaultProps },
  }
  blocks.value.push(newBlock)
  selectedBlockId.value = newBlock.id
}

function removeBlock(id: string) {
  blocks.value = blocks.value.filter(b => b.id !== id)
  if (selectedBlockId.value === id) selectedBlockId.value = null
}

function moveBlock(id: string, direction: 'up' | 'down') {
  const idx = blocks.value.findIndex(b => b.id === id)
  if (idx < 0) return
  const newIdx = direction === 'up' ? idx - 1 : idx + 1
  if (newIdx < 0 || newIdx >= blocks.value.length) return
  const reordered = [...blocks.value]
  const a = reordered[idx]
  const b = reordered[newIdx]
  if (!a || !b) return
  reordered[idx] = b
  reordered[newIdx] = a
  blocks.value = reordered
}

function updateBlockProp(key: string, value: any) {
  if (!selectedBlock.value) return
  selectedBlock.value.props[key] = value
}

// ── Save & publish ──
async function handleSave() {
  try {
    await updateMutation.mutateAsync({
      id: pageId.value,
      dto: {
        name: pageName.value,
        slug: pageSlug.value || undefined,
        metaTitle: metaTitle.value || undefined,
        metaDescription: metaDescription.value || undefined,
        blocks: JSON.stringify(blocks.value),
      },
    })
    toast.success('Saved')
  } catch (error: any) {
    toast.error(error?.data?.error?.message || 'Failed to save')
  }
}

async function handleTogglePublish() {
  if (!page.value) return
  try {
    // Save first to make sure latest blocks go live
    await updateMutation.mutateAsync({
      id: pageId.value,
      dto: {
        name: pageName.value,
        slug: pageSlug.value || undefined,
        metaTitle: metaTitle.value || undefined,
        metaDescription: metaDescription.value || undefined,
        blocks: JSON.stringify(blocks.value),
      },
    })
    await publishMutation.mutateAsync({
      id: pageId.value,
      publish: page.value.status === 'DRAFT',
    })
    toast.success(page.value.status === 'DRAFT' ? 'Published' : 'Unpublished')
  } catch (error: any) {
    toast.error(error?.data?.error?.message || 'Failed to update status')
  }
}

function publicUrl(): string {
  if (import.meta.client) {
    return `${window.location.origin}/p/${pageSlug.value}`
  }
  return `/p/${pageSlug.value}`
}
</script>

<template>
  <div class="space-y-4">
    <NuxtLink to="/landing-pages" class="text-sm text-muted-foreground hover:text-foreground">
      ← Back to pages
    </NuxtLink>

    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">Loading...</div>

    <div v-else-if="!page" class="text-center py-8 text-destructive">Page not found</div>

    <div v-else class="space-y-4">
      <!-- Top bar -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <Input v-model="pageName" class="text-lg font-bold w-64" />
          <Badge :variant="page.status === 'PUBLISHED' ? 'default' : 'secondary'">
            {{ page.status }}
          </Badge>
        </div>
        <div class="flex items-center gap-2">
          <a
            v-if="page.status === 'PUBLISHED'"
            :href="publicUrl()"
            target="_blank"
            class="text-sm text-primary hover:underline"
          >
            View live page ↗
          </a>
          <Button variant="outline" @click="handleSave" :disabled="updateMutation.isPending.value">
            {{ updateMutation.isPending.value ? 'Saving...' : 'Save' }}
          </Button>
          <Button @click="handleTogglePublish" :disabled="publishMutation.isPending.value">
            {{ page.status === 'DRAFT' ? 'Publish' : 'Unpublish' }}
          </Button>
        </div>
      </div>

      <!-- Three-column editor layout -->
      <div class="grid grid-cols-12 gap-4" style="min-height: 600px;">
        <!-- LEFT: Block palette + page settings -->
        <div class="col-span-3 space-y-4">
          <Card>
            <CardContent class="p-4 space-y-3">
              <h3 class="text-sm font-medium">Add Block</h3>
              <div class="grid grid-cols-2 gap-2">
                <button
                  v-for="def in blockDefinitions"
                  :key="def.type"
                  class="p-3 border rounded-md text-center hover:border-primary hover:bg-muted/50 transition"
                  @click="addBlock(def.type)"
                >
                  <div class="text-2xl">{{ def.icon }}</div>
                  <div class="text-xs mt-1">{{ def.label }}</div>
                </button>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardContent class="p-4 space-y-3">
              <h3 class="text-sm font-medium">Page Settings</h3>
              <div class="space-y-2">
                <Label for="slug">URL Slug</Label>
                <Input id="slug" v-model="pageSlug" />
                <p class="text-xs text-muted-foreground">/p/{{ pageSlug }}</p>
              </div>
              <div class="space-y-2">
                <Label for="metaTitle">Meta Title</Label>
                <Input id="metaTitle" v-model="metaTitle" />
              </div>
              <div class="space-y-2">
                <Label for="metaDescription">Meta Description</Label>
                <Input id="metaDescription" v-model="metaDescription" />
              </div>
            </CardContent>
          </Card>
        </div>

        <!-- CENTER: Preview / block list -->
        <div class="col-span-6">
          <Card>
            <CardContent class="p-4">
              <h3 class="text-sm font-medium mb-3">Preview</h3>
              <div
                v-if="!blocks.length"
                class="text-center py-16 text-sm text-muted-foreground border-2 border-dashed rounded-md"
              >
                Click a block in the left panel to start building your page.
              </div>
              <div v-else class="space-y-2">
                <div
                  v-for="(block, idx) in blocks"
                  :key="block.id"
                  class="p-3 rounded-md border cursor-pointer transition"
                  :class="selectedBlockId === block.id ? 'border-primary ring-2 ring-primary/20' : 'hover:border-primary/50'"
                  @click="selectedBlockId = block.id"
                >
                  <div class="flex items-center justify-between mb-1">
                    <div class="flex items-center gap-2">
                      <span class="text-sm">{{ blockDefinitions.find(d => d.type === block.type)?.icon }}</span>
                      <span class="text-xs font-medium uppercase">{{ block.type }}</span>
                    </div>
                    <div class="flex gap-1" @click.stop>
                      <button
                        class="text-xs text-muted-foreground hover:text-foreground p-1"
                        :disabled="idx === 0"
                        @click="moveBlock(block.id, 'up')"
                      >↑</button>
                      <button
                        class="text-xs text-muted-foreground hover:text-foreground p-1"
                        :disabled="idx === blocks.length - 1"
                        @click="moveBlock(block.id, 'down')"
                      >↓</button>
                      <button
                        class="text-xs text-muted-foreground hover:text-destructive p-1"
                        @click="removeBlock(block.id)"
                      >✕</button>
                    </div>
                  </div>

                  <!-- Block preview rendering -->
                  <div v-if="block.type === 'hero'" class="py-4 text-center">
                    <h2 class="text-xl font-bold">{{ block.props.title }}</h2>
                    <p class="text-sm text-muted-foreground mt-1">{{ block.props.subtitle }}</p>
                    <button class="mt-2 px-3 py-1 text-xs rounded bg-primary text-primary-foreground">
                      {{ block.props.ctaText }}
                    </button>
                  </div>

                  <div v-else-if="block.type === 'text'" class="text-sm">
                    {{ block.props.content }}
                  </div>

                  <div v-else-if="block.type === 'image'" class="flex justify-center">
                    <img :src="block.props.src" :alt="block.props.alt" class="max-h-32 object-contain rounded" />
                  </div>

                  <div v-else-if="block.type === 'form'" class="text-xs text-muted-foreground">
                    <span v-if="block.props.formId">
                      Form: <strong>{{ forms?.find(f => f.id === block.props.formId)?.name ?? 'Unknown' }}</strong>
                    </span>
                    <span v-else class="text-destructive">⚠️ No form selected</span>
                  </div>

                  <div v-else-if="block.type === 'cta'" class="text-center py-2">
                    <button class="px-4 py-2 rounded bg-primary text-primary-foreground text-sm">
                      {{ block.props.text }}
                    </button>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        <!-- RIGHT: Selected block properties -->
        <div class="col-span-3">
          <Card>
            <CardContent class="p-4">
              <h3 class="text-sm font-medium mb-3">
                {{ selectedBlock ? selectedBlock.type.toUpperCase() + ' Properties' : 'Properties' }}
              </h3>

              <div v-if="!selectedBlock" class="text-xs text-muted-foreground">
                Click a block in the preview to edit it.
              </div>

              <div v-else class="space-y-3">
                <!-- Hero props -->
                <template v-if="selectedBlock.type === 'hero'">
                  <div class="space-y-1">
                    <Label>Title</Label>
                    <Input :model-value="selectedBlock.props.title" @update:model-value="updateBlockProp('title', $event)" />
                  </div>
                  <div class="space-y-1">
                    <Label>Subtitle</Label>
                    <Input :model-value="selectedBlock.props.subtitle" @update:model-value="updateBlockProp('subtitle', $event)" />
                  </div>
                  <div class="space-y-1">
                    <Label>CTA Text</Label>
                    <Input :model-value="selectedBlock.props.ctaText" @update:model-value="updateBlockProp('ctaText', $event)" />
                  </div>
                  <div class="space-y-1">
                    <Label>CTA URL</Label>
                    <Input :model-value="selectedBlock.props.ctaUrl" @update:model-value="updateBlockProp('ctaUrl', $event)" />
                  </div>
                </template>

                <!-- Text props -->
                <template v-else-if="selectedBlock.type === 'text'">
                  <div class="space-y-1">
                    <Label>Content</Label>
                    <textarea
                      class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                      rows="6"
                      :value="selectedBlock.props.content"
                      @input="updateBlockProp('content', ($event.target as HTMLTextAreaElement).value)"
                    />
                  </div>
                </template>

                <!-- Image props -->
                <template v-else-if="selectedBlock.type === 'image'">
                  <div class="space-y-1">
                    <Label>Image URL</Label>
                    <Input :model-value="selectedBlock.props.src" @update:model-value="updateBlockProp('src', $event)" />
                  </div>
                  <div class="space-y-1">
                    <Label>Alt Text</Label>
                    <Input :model-value="selectedBlock.props.alt" @update:model-value="updateBlockProp('alt', $event)" />
                  </div>
                </template>

                <!-- Form props -->
                <template v-else-if="selectedBlock.type === 'form'">
                  <div class="space-y-1">
                    <Label>Form</Label>
                    <select
                      class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                      :value="selectedBlock.props.formId"
                      @change="updateBlockProp('formId', ($event.target as HTMLSelectElement).value)"
                    >
                      <option value="">Select a form</option>
                      <option v-for="f in forms" :key="f.id" :value="f.id">{{ f.name }}</option>
                    </select>
                    <NuxtLink v-if="!forms?.length" to="/forms" class="text-xs text-primary hover:underline">
                      Create a form first →
                    </NuxtLink>
                  </div>
                  <div class="space-y-1">
                    <Label>Submit Button Text</Label>
                    <Input :model-value="selectedBlock.props.submitText" @update:model-value="updateBlockProp('submitText', $event)" />
                  </div>
                </template>

                <!-- CTA props -->
                <template v-else-if="selectedBlock.type === 'cta'">
                  <div class="space-y-1">
                    <Label>Button Text</Label>
                    <Input :model-value="selectedBlock.props.text" @update:model-value="updateBlockProp('text', $event)" />
                  </div>
                  <div class="space-y-1">
                    <Label>URL</Label>
                    <Input :model-value="selectedBlock.props.url" @update:model-value="updateBlockProp('url', $event)" />
                  </div>
                </template>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  </div>
</template>
