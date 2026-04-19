<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '~/components/ui/select'
import { toast } from 'vue-sonner'
import {
  ArrowLeft, ArrowUpRight, Save, Send, EyeOff,
  LayoutPanelTop, AlignLeft, Image as ImageIcon, ClipboardList, MousePointer,
  ChevronUp, ChevronDown, X, MousePointerClick, Settings, SlidersHorizontal, AlertCircle,
} from 'lucide-vue-next'

definePageMeta({
  middleware: 'auth',
  layout: 'default',
})

const route = useRoute()
const pageId = computed(() => route.params.id as string)

const { data: page, isLoading } = useLandingPage(pageId)
const { data: forms } = useForms()
const updateMutation = useUpdateLandingPage()
const publishMutation = usePublishLandingPage()

type BlockType = 'hero' | 'text' | 'image' | 'form' | 'cta'

interface Block {
  id: string
  type: BlockType
  props: Record<string, any>
}

// Lucide icon map — one proper icon per block type so the palette doesn't
// rely on emoji (which render inconsistently across OSes).
const blockIcon: Record<BlockType, any> = {
  hero: LayoutPanelTop,
  text: AlignLeft,
  image: ImageIcon,
  form: ClipboardList,
  cta: MousePointer,
}

const blockDefinitions: Array<{ type: BlockType; label: string; defaultProps: Record<string, any> }> = [
  {
    type: 'hero',
    label: 'Hero',
    defaultProps: {
      title: 'Your headline',
      subtitle: 'A supporting subheadline goes here.',
      ctaText: 'Get started',
      ctaUrl: '#',
    },
  },
  {
    type: 'text',
    label: 'Text',
    defaultProps: { content: 'Add your text content here.' },
  },
  {
    type: 'image',
    label: 'Image',
    defaultProps: {
      src: 'https://via.placeholder.com/800x400',
      alt: 'Image description',
    },
  },
  {
    type: 'form',
    label: 'Form',
    defaultProps: { formId: '', submitText: 'Submit' },
  },
  {
    type: 'cta',
    label: 'CTA Button',
    defaultProps: { text: 'Click here', url: '#' },
  },
]

const blocks = ref<Block[]>([])
const selectedBlockId = ref<string | null>(null)
const pageName = ref('')
const pageSlug = ref('')
const metaTitle = ref('')
const metaDescription = ref('')

watch(page, (loaded) => {
  if (!loaded) return
  pageName.value = loaded.name
  pageSlug.value = loaded.slug
  metaTitle.value = loaded.metaTitle ?? ''
  metaDescription.value = loaded.metaDescription ?? ''
  try {
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
  <div class="space-y-5 enter-fade-up">
    <!-- Back link -->
    <NuxtLink
      to="/landing-pages"
      class="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
    >
      <ArrowLeft class="h-3.5 w-3.5" />
      Back to pages
    </NuxtLink>

    <!-- Loading / not-found -->
    <div v-if="isLoading" class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground">
      Loading…
    </div>
    <div v-else-if="!page" class="glass hairline rounded-xl py-16 text-center text-sm text-destructive">
      Page not found
    </div>

    <div v-else class="space-y-5">
      <!-- Top bar -->
      <div class="glass hairline rounded-xl px-4 py-3 flex items-center justify-between gap-3 flex-wrap">
        <div class="flex items-center gap-3 min-w-0 flex-1">
          <Input
            v-model="pageName"
            class="text-base font-semibold h-9 max-w-xs border-transparent bg-transparent hover:border-input focus:border-input"
          />
          <Badge :variant="page.status === 'PUBLISHED' ? 'default' : 'secondary'">
            {{ page.status }}
          </Badge>
        </div>
        <div class="flex items-center gap-2">
          <a
            v-if="page.status === 'PUBLISHED'"
            :href="publicUrl()"
            target="_blank"
            class="inline-flex items-center gap-1 text-sm text-primary hover:underline mr-2"
          >
            View live
            <ArrowUpRight class="h-3.5 w-3.5" />
          </a>
          <Button
            variant="outline"
            class="gap-1.5"
            :disabled="updateMutation.isPending.value"
            @click="handleSave"
          >
            <Save class="h-3.5 w-3.5" />
            {{ updateMutation.isPending.value ? 'Saving…' : 'Save' }}
          </Button>
          <Button
            class="gap-1.5"
            :disabled="publishMutation.isPending.value"
            @click="handleTogglePublish"
          >
            <component :is="page.status === 'DRAFT' ? Send : EyeOff" class="h-3.5 w-3.5" />
            {{ page.status === 'DRAFT' ? 'Publish' : 'Unpublish' }}
          </Button>
        </div>
      </div>

      <!-- Three-column editor -->
      <div class="grid grid-cols-12 gap-4" style="min-height: 600px;">
        <!-- LEFT: block palette + page settings -->
        <div class="col-span-3 space-y-4">
          <!-- Block palette -->
          <div class="glass hairline rounded-xl overflow-hidden">
            <div class="px-4 py-3" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
              <div class="flex items-center gap-2">
                <MousePointerClick class="h-4 w-4 text-muted-foreground" />
                <h3 class="text-sm font-semibold tracking-tight">Add block</h3>
              </div>
            </div>
            <div class="p-3 grid grid-cols-2 gap-2">
              <button
                v-for="def in blockDefinitions"
                :key="def.type"
                type="button"
                class="group flex flex-col items-center gap-1.5 rounded-lg hairline py-3 px-2 text-muted-foreground hover:text-foreground hover:bg-white/5 hover:border-primary/40 transition-colors"
                @click="addBlock(def.type)"
              >
                <component :is="blockIcon[def.type]" class="h-4 w-4" />
                <span class="text-xs">{{ def.label }}</span>
              </button>
            </div>
          </div>

          <!-- Page settings -->
          <div class="glass hairline rounded-xl overflow-hidden">
            <div class="px-4 py-3" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
              <div class="flex items-center gap-2">
                <Settings class="h-4 w-4 text-muted-foreground" />
                <h3 class="text-sm font-semibold tracking-tight">Page settings</h3>
              </div>
            </div>
            <div class="p-4 space-y-3">
              <div class="space-y-1.5">
                <Label for="slug" class="text-xs">URL slug</Label>
                <Input id="slug" v-model="pageSlug" class="h-9" />
                <p class="text-xs text-muted-foreground font-mono truncate">/p/{{ pageSlug }}</p>
              </div>
              <div class="space-y-1.5">
                <Label for="metaTitle" class="text-xs">Meta title</Label>
                <Input id="metaTitle" v-model="metaTitle" class="h-9" placeholder="SEO title" />
              </div>
              <div class="space-y-1.5">
                <Label for="metaDescription" class="text-xs">Meta description</Label>
                <Input id="metaDescription" v-model="metaDescription" class="h-9" placeholder="SEO description" />
              </div>
            </div>
          </div>
        </div>

        <!-- CENTER: preview / block list -->
        <div class="col-span-6">
          <div class="glass hairline rounded-xl overflow-hidden flex flex-col h-full">
            <div class="px-4 py-3 flex items-center justify-between" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
              <h3 class="text-sm font-semibold tracking-tight">Preview</h3>
              <span class="text-xs text-muted-foreground">
                {{ blocks.length }} {{ blocks.length === 1 ? 'block' : 'blocks' }}
              </span>
            </div>

            <!-- Empty state -->
            <div
              v-if="!blocks.length"
              class="flex-1 flex flex-col items-center justify-center py-16 px-6 text-center"
            >
              <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mb-4">
                <LayoutPanelTop class="h-5 w-5 text-muted-foreground" />
              </div>
              <h4 class="text-sm font-semibold tracking-tight">Start with a block</h4>
              <p class="text-sm text-muted-foreground mt-1 max-w-xs">
                Pick from the left panel to drop a hero, text, image, form, or CTA into the page.
              </p>
            </div>

            <!-- Block list -->
            <div v-else class="p-4 space-y-2">
              <div
                v-for="(block, idx) in blocks"
                :key="block.id"
                class="rounded-lg hairline p-3 cursor-pointer transition-colors"
                :class="selectedBlockId === block.id
                  ? 'ring-2 ring-primary/40 bg-primary/5'
                  : 'hover:bg-white/5'"
                @click="selectedBlockId = block.id"
              >
                <div class="flex items-center justify-between mb-2">
                  <div class="flex items-center gap-2 min-w-0">
                    <component :is="blockIcon[block.type]" class="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                    <span class="text-xs uppercase tracking-wider text-muted-foreground font-medium">
                      {{ block.type }}
                    </span>
                  </div>
                  <div class="flex items-center gap-0.5" @click.stop>
                    <button
                      class="h-6 w-6 rounded-sm flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-white/5 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                      title="Move up"
                      :disabled="idx === 0"
                      @click="moveBlock(block.id, 'up')"
                    >
                      <ChevronUp class="h-3.5 w-3.5" />
                    </button>
                    <button
                      class="h-6 w-6 rounded-sm flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-white/5 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                      title="Move down"
                      :disabled="idx === blocks.length - 1"
                      @click="moveBlock(block.id, 'down')"
                    >
                      <ChevronDown class="h-3.5 w-3.5" />
                    </button>
                    <button
                      class="h-6 w-6 rounded-sm flex items-center justify-center text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors"
                      title="Remove block"
                      @click="removeBlock(block.id)"
                    >
                      <X class="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>

                <!-- Block preview rendering -->
                <div v-if="block.type === 'hero'" class="py-4 text-center">
                  <h2 class="text-xl font-bold">{{ block.props.title }}</h2>
                  <p class="text-sm text-muted-foreground mt-1">{{ block.props.subtitle }}</p>
                  <button class="mt-3 px-3 py-1 text-xs rounded bg-primary text-primary-foreground">
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
                  <template v-if="block.props.formId">
                    Form: <strong class="text-foreground">{{ forms?.find(f => f.id === block.props.formId)?.name ?? 'Unknown' }}</strong>
                  </template>
                  <span v-else class="inline-flex items-center gap-1 text-destructive">
                    <AlertCircle class="h-3 w-3" />
                    No form selected
                  </span>
                </div>

                <div v-else-if="block.type === 'cta'" class="text-center py-2">
                  <button class="px-4 py-2 rounded bg-primary text-primary-foreground text-sm">
                    {{ block.props.text }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- RIGHT: selected block properties -->
        <div class="col-span-3">
          <div class="glass hairline rounded-xl overflow-hidden">
            <div class="px-4 py-3" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
              <div class="flex items-center gap-2">
                <SlidersHorizontal class="h-4 w-4 text-muted-foreground" />
                <h3 class="text-sm font-semibold tracking-tight">
                  {{ selectedBlock ? selectedBlock.type.toUpperCase() + ' properties' : 'Properties' }}
                </h3>
              </div>
            </div>

            <div v-if="!selectedBlock" class="p-6 text-center">
              <div class="h-10 w-10 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-3">
                <SlidersHorizontal class="h-4 w-4 text-muted-foreground" />
              </div>
              <p class="text-xs text-muted-foreground">
                Click a block in the preview to edit its properties here.
              </p>
            </div>

            <div v-else class="p-4 space-y-3">
              <!-- Hero -->
              <template v-if="selectedBlock.type === 'hero'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Title</Label>
                  <Input
                    class="h-9"
                    :model-value="selectedBlock.props.title"
                    @update:model-value="updateBlockProp('title', $event)"
                  />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Subtitle</Label>
                  <Input
                    class="h-9"
                    :model-value="selectedBlock.props.subtitle"
                    @update:model-value="updateBlockProp('subtitle', $event)"
                  />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">CTA text</Label>
                  <Input
                    class="h-9"
                    :model-value="selectedBlock.props.ctaText"
                    @update:model-value="updateBlockProp('ctaText', $event)"
                  />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">CTA URL</Label>
                  <Input
                    class="h-9"
                    :model-value="selectedBlock.props.ctaUrl"
                    @update:model-value="updateBlockProp('ctaUrl', $event)"
                  />
                </div>
              </template>

              <!-- Text -->
              <template v-else-if="selectedBlock.type === 'text'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Content</Label>
                  <textarea
                    class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                    rows="6"
                    :value="selectedBlock.props.content"
                    @input="updateBlockProp('content', ($event.target as HTMLTextAreaElement).value)"
                  />
                </div>
              </template>

              <!-- Image -->
              <template v-else-if="selectedBlock.type === 'image'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Image URL</Label>
                  <Input
                    class="h-9"
                    :model-value="selectedBlock.props.src"
                    @update:model-value="updateBlockProp('src', $event)"
                  />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Alt text</Label>
                  <Input
                    class="h-9"
                    :model-value="selectedBlock.props.alt"
                    @update:model-value="updateBlockProp('alt', $event)"
                  />
                </div>
              </template>

              <!-- Form -->
              <template v-else-if="selectedBlock.type === 'form'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Form</Label>
                  <Select
                    :model-value="selectedBlock.props.formId"
                    @update:model-value="updateBlockProp('formId', $event)"
                  >
                    <SelectTrigger class="h-9 w-full">
                      <SelectValue placeholder="Select a form" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem v-for="f in forms" :key="f.id" :value="f.id">
                        {{ f.name }}
                      </SelectItem>
                    </SelectContent>
                  </Select>
                  <NuxtLink v-if="!forms?.length" to="/forms" class="text-xs text-primary hover:underline">
                    Create a form first →
                  </NuxtLink>
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Submit button text</Label>
                  <Input
                    class="h-9"
                    :model-value="selectedBlock.props.submitText"
                    @update:model-value="updateBlockProp('submitText', $event)"
                  />
                </div>
              </template>

              <!-- CTA -->
              <template v-else-if="selectedBlock.type === 'cta'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Button text</Label>
                  <Input
                    class="h-9"
                    :model-value="selectedBlock.props.text"
                    @update:model-value="updateBlockProp('text', $event)"
                  />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">URL</Label>
                  <Input
                    class="h-9"
                    :model-value="selectedBlock.props.url"
                    @update:model-value="updateBlockProp('url', $event)"
                  />
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
