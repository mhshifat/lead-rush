<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Checkbox } from '~/components/ui/checkbox'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '~/components/ui/select'
import { toast } from 'vue-sonner'
import {
  ArrowLeft, ArrowUpRight, Save, Send, EyeOff, Trash2,
  LayoutPanelTop, AlignLeft, AlignCenter, AlignRight,
  Image as ImageIcon, ClipboardList, MousePointer,
  ChevronUp, ChevronDown, ChevronRight, X, MousePointerClick, Settings, SlidersHorizontal,
  Heading as HeadingIcon, Minus, Space, Video, LayoutGrid, Quote, BarChart2, HelpCircle,
  AlertCircle, Columns3, ListChecks, ListOrdered, DollarSign, Building2, Code2, Plus,
} from 'lucide-vue-next'

definePageMeta({
  middleware: 'auth',
  layout: 'default',
})

const route = useRoute()
const pageId = computed(() => route.params.id as string)

const { data: page, isLoading } = useLandingPage(pageId)
useHead(() => ({ title: page.value?.name ? `Edit · ${page.value.name}` : 'Edit landing page' }))
const { data: forms } = useForms()
const updateMutation = useUpdateLandingPage()
const publishMutation = usePublishLandingPage()

// ────────────────────────────────────────────────────────────────────────────
// Block type registry
// Each block has: icon, human label, category (for palette grouping), and
// a defaultProps object. Preview + properties-form rendering below dispatch
// off `block.type`.
// ────────────────────────────────────────────────────────────────────────────

type BlockType =
  | 'hero' | 'heading' | 'text' | 'columns' | 'checklist'
  | 'image' | 'video'
  | 'cta' | 'form' | 'pricing'
  | 'features' | 'testimonial' | 'stats' | 'faq' | 'steps' | 'logos'
  | 'divider' | 'spacer' | 'embed'

interface Block {
  id: string
  type: BlockType
  props: Record<string, any>
}

type PaletteCategory = 'content' | 'media' | 'cta' | 'social-proof' | 'layout'

const blockIcon: Record<BlockType, any> = {
  hero:        LayoutPanelTop,
  heading:     HeadingIcon,
  text:        AlignLeft,
  columns:     Columns3,
  checklist:   ListChecks,
  image:       ImageIcon,
  video:       Video,
  cta:         MousePointer,
  form:        ClipboardList,
  pricing:     DollarSign,
  features:    LayoutGrid,
  testimonial: Quote,
  stats:       BarChart2,
  faq:         HelpCircle,
  steps:       ListOrdered,
  logos:       Building2,
  divider:     Minus,
  spacer:      Space,
  embed:       Code2,
}

const blockDefinitions: Array<{
  type: BlockType
  label: string
  category: PaletteCategory
  defaultProps: Record<string, any>
}> = [
  // CONTENT
  {
    type: 'hero',
    category: 'content',
    label: 'Hero',
    defaultProps: {
      title: 'Your headline',
      subtitle: 'A supporting subheadline goes here.',
      ctaText: 'Get started',
      ctaUrl: '#',
      alignment: 'center',
      bg: 'subtle',
    },
  },
  {
    type: 'heading',
    category: 'content',
    label: 'Heading',
    defaultProps: {
      text: 'A bold section heading',
      level: 2,
      alignment: 'left',
    },
  },
  {
    type: 'text',
    category: 'content',
    label: 'Text',
    defaultProps: {
      content: 'Add your text content here.',
      alignment: 'left',
      size: 'md',
    },
  },
  {
    type: 'columns',
    category: 'content',
    label: 'Columns',
    defaultProps: {
      columns: 2,
      gap: 'md',
      items: [
        { title: 'First column', body: 'Describe your first point here.' },
        { title: 'Second column', body: 'Describe your second point here.' },
      ],
    },
  },
  {
    type: 'checklist',
    category: 'content',
    label: 'Checklist',
    defaultProps: {
      heading: '',
      columns: 1,
      items: [
        { text: 'First benefit' },
        { text: 'Second benefit' },
        { text: 'Third benefit' },
      ],
    },
  },

  // MEDIA
  {
    type: 'image',
    category: 'media',
    label: 'Image',
    defaultProps: {
      src: 'https://via.placeholder.com/800x400',
      alt: 'Image description',
      width: 'full',
      rounded: true,
      link: '',
    },
  },
  {
    type: 'video',
    category: 'media',
    label: 'Video',
    defaultProps: {
      url: '',
      aspect: '16/9',
    },
  },

  // CTA
  {
    type: 'cta',
    category: 'cta',
    label: 'CTA Button',
    defaultProps: {
      text: 'Click here',
      url: '#',
      variant: 'primary',
      size: 'md',
      alignment: 'center',
      newTab: false,
    },
  },
  {
    type: 'form',
    category: 'cta',
    label: 'Form',
    defaultProps: {
      heading: '',
      description: '',
      formId: '',
      submitText: 'Submit',
    },
  },
  {
    type: 'pricing',
    category: 'cta',
    label: 'Pricing',
    defaultProps: {
      heading: 'Simple pricing',
      subtitle: 'Choose the plan that fits your team.',
      plans: [
        {
          name: 'Starter', price: '$19', period: '/mo',
          features: ['Up to 3 users', 'Basic analytics', 'Email support'],
          ctaText: 'Start free', ctaUrl: '#', featured: false,
        },
        {
          name: 'Pro', price: '$49', period: '/mo',
          features: ['Unlimited users', 'Advanced analytics', 'Priority support', 'Custom integrations'],
          ctaText: 'Try Pro', ctaUrl: '#', featured: true,
        },
        {
          name: 'Enterprise', price: 'Custom', period: '',
          features: ['SSO & SAML', 'Dedicated manager', 'SLA & uptime'],
          ctaText: 'Contact sales', ctaUrl: '#', featured: false,
        },
      ],
    },
  },

  // SOCIAL PROOF
  {
    type: 'features',
    category: 'social-proof',
    label: 'Features',
    defaultProps: {
      heading: 'Why teams choose us',
      columns: 3,
      items: [
        { icon: '⚡', title: 'Fast', description: 'Blazing-fast performance out of the box.' },
        { icon: '🔒', title: 'Secure', description: 'Enterprise-grade security and compliance.' },
        { icon: '❤️', title: 'Loved', description: 'Thousands of happy customers worldwide.' },
      ],
    },
  },
  {
    type: 'testimonial',
    category: 'social-proof',
    label: 'Testimonial',
    defaultProps: {
      quote: "This product completely changed how our team works.",
      authorName: 'Jane Doe',
      authorRole: 'CEO, Acme Inc.',
      avatarUrl: '',
    },
  },
  {
    type: 'stats',
    category: 'social-proof',
    label: 'Stats',
    defaultProps: {
      items: [
        { value: '10k+', label: 'Active users' },
        { value: '99.9%', label: 'Uptime' },
        { value: '4.9/5', label: 'Average rating' },
      ],
    },
  },
  {
    type: 'faq',
    category: 'social-proof',
    label: 'FAQ',
    defaultProps: {
      heading: 'Frequently asked questions',
      items: [
        { question: 'How does pricing work?', answer: 'We charge per seat, per month.' },
        { question: 'Can I cancel anytime?',  answer: 'Yes, no lock-in contracts.' },
      ],
    },
  },
  {
    type: 'steps',
    category: 'social-proof',
    label: 'Steps',
    defaultProps: {
      heading: 'How it works',
      items: [
        { title: 'Sign up',  body: 'Create your account in under a minute.' },
        { title: 'Configure', body: 'Connect your tools and set your goals.' },
        { title: 'Grow',      body: 'Watch the leads roll in automatically.' },
      ],
    },
  },
  {
    type: 'logos',
    category: 'social-proof',
    label: 'Logos',
    defaultProps: {
      heading: 'Trusted by teams at',
      logos: [
        { src: '', alt: 'Company A' },
        { src: '', alt: 'Company B' },
        { src: '', alt: 'Company C' },
        { src: '', alt: 'Company D' },
      ],
    },
  },

  // LAYOUT
  {
    type: 'divider',
    category: 'layout',
    label: 'Divider',
    defaultProps: {
      size: 'md',
    },
  },
  {
    type: 'spacer',
    category: 'layout',
    label: 'Spacer',
    defaultProps: {
      size: 'md',
    },
  },
  {
    type: 'embed',
    category: 'layout',
    label: 'HTML embed',
    defaultProps: {
      html: '<!-- paste any HTML, iframe, or embed code here -->',
    },
  },
]

const paletteCategories: Array<{ key: PaletteCategory; label: string }> = [
  { key: 'content',      label: 'Content' },
  { key: 'media',        label: 'Media' },
  { key: 'cta',          label: 'CTAs & Forms' },
  { key: 'social-proof', label: 'Social proof' },
  { key: 'layout',       label: 'Layout' },
]

const blocksByCategory = computed(() => {
  return paletteCategories.map(cat => ({
    ...cat,
    defs: blockDefinitions.filter(b => b.category === cat.key),
  }))
})

// ────────────────────────────────────────────────────────────────────────────
// Page state
// ────────────────────────────────────────────────────────────────────────────

const blocks = ref<Block[]>([])
const selectedBlockId = ref<string | null>(null)
const pageName = ref('')
const pageSlug = ref('')
const metaTitle = ref('')
const metaDescription = ref('')

// Collapsible side panels — collapsed by default so the preview gets maximum width.
const paletteOpen = ref(false)
const settingsOpen = ref(false)
const propertiesOpen = ref(false)

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

// crypto.randomUUID() throws on insecure contexts (HTTP non-localhost). Fallback keeps the editor working in any deploy.
function genId(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID()
    }
  } catch {}
  return 'b_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 10)
}

function addBlock(type: BlockType, insertAt?: number) {
  const def = blockDefinitions.find(d => d.type === type)
  if (!def) return
  const newBlock: Block = {
    id: genId(),
    type,
    props: JSON.parse(JSON.stringify(def.defaultProps)),
  }
  if (typeof insertAt === 'number' && insertAt >= 0 && insertAt <= blocks.value.length) {
    const next = [...blocks.value]
    next.splice(insertAt, 0, newBlock)
    blocks.value = next
  } else {
    blocks.value = [...blocks.value, newBlock]
  }
  selectedBlockId.value = newBlock.id
}

// ── Drag and drop ──
// Tracked via module-scoped refs instead of dataTransfer — the native API is quirky across browsers
// (Firefox can drop the payload, some browsers restrict reads during drop). dataTransfer is still
// set for the OS drag image, but the actual payload lookup uses these refs.
const dragOverIndex = ref<number | null>(null)
const draggingPaletteType = ref<BlockType | null>(null)
const draggingBlockId = ref<string | null>(null)

function onPaletteDragStart(e: DragEvent, type: BlockType) {
  draggingPaletteType.value = type
  draggingBlockId.value = null
  if (e.dataTransfer) {
    e.dataTransfer.setData('text/plain', `palette:${type}`)
    e.dataTransfer.effectAllowed = 'copy'
  }
}

function onPaletteDragEnd() {
  draggingPaletteType.value = null
  dragOverIndex.value = null
}

function onBlockDragStart(e: DragEvent, id: string) {
  draggingBlockId.value = id
  draggingPaletteType.value = null
  if (e.dataTransfer) {
    e.dataTransfer.setData('text/plain', `block:${id}`)
    e.dataTransfer.effectAllowed = 'move'
  }
}

function onBlockDragEnd() {
  draggingBlockId.value = null
  dragOverIndex.value = null
}

function onDragOverZone(e: DragEvent, index: number) {
  e.preventDefault()
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = draggingPaletteType.value ? 'copy' : 'move'
  }
  dragOverIndex.value = index
}

function onDropZone(e: DragEvent, index: number) {
  e.preventDefault()
  dragOverIndex.value = null

  if (draggingPaletteType.value) {
    addBlock(draggingPaletteType.value, index)
  } else if (draggingBlockId.value) {
    const id = draggingBlockId.value
    const fromIdx = blocks.value.findIndex(b => b.id === id)
    if (fromIdx < 0) return
    const next = [...blocks.value]
    const [moved] = next.splice(fromIdx, 1)
    if (!moved) return
    const targetIdx = fromIdx < index ? index - 1 : index
    next.splice(targetIdx, 0, moved)
    blocks.value = next
  }

  draggingPaletteType.value = null
  draggingBlockId.value = null
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

// Array prop helpers for features / stats / faq.
function updateItemAt(key: string, idx: number, patch: Record<string, any>) {
  if (!selectedBlock.value) return
  const arr = [...(selectedBlock.value.props[key] ?? [])]
  arr[idx] = { ...arr[idx], ...patch }
  selectedBlock.value.props[key] = arr
}
function addItem(key: string, blank: Record<string, any>) {
  if (!selectedBlock.value) return
  const arr = [...(selectedBlock.value.props[key] ?? [])]
  arr.push({ ...blank })
  selectedBlock.value.props[key] = arr
}
function removeItem(key: string, idx: number) {
  if (!selectedBlock.value) return
  const arr = [...(selectedBlock.value.props[key] ?? [])]
  arr.splice(idx, 1)
  selectedBlock.value.props[key] = arr
}

// ────────────────────────────────────────────────────────────────────────────
// Style-token mappers (pill groups)
// ────────────────────────────────────────────────────────────────────────────
const ALIGN_OPTIONS = [
  { value: 'left', icon: AlignLeft, label: 'Left' },
  { value: 'center', icon: AlignCenter, label: 'Center' },
  { value: 'right', icon: AlignRight, label: 'Right' },
] as const

function alignClass(v: string): string {
  return { left: 'text-left', center: 'text-center', right: 'text-right' }[v] ?? 'text-left'
}
function textSizeClass(v: string): string {
  return { sm: 'text-xs', md: 'text-sm', lg: 'text-base' }[v] ?? 'text-sm'
}
function spacerHeight(v: string): string {
  return { sm: '1rem', md: '2rem', lg: '3rem', xl: '5rem' }[v] ?? '2rem'
}
function dividerMargin(v: string): string {
  return { sm: '0.5rem 0', md: '1rem 0', lg: '1.5rem 0' }[v] ?? '1rem 0'
}
function imageWidthClass(v: string): string {
  return {
    sm: 'max-w-xs', md: 'max-w-md', lg: 'max-w-xl', full: 'max-w-full',
  }[v] ?? 'max-w-full'
}
function heroBg(v: string): string {
  return {
    none: '',
    subtle: 'bg-white/[0.02]',
    primary: 'bg-primary/10',
  }[v] ?? ''
}
function gapClass(v: string): string {
  return { sm: 'gap-2', md: 'gap-4', lg: 'gap-6' }[v] ?? 'gap-4'
}
function youtubeEmbed(url: string): string | null {
  if (!url) return null
  // Handle youtube.com/watch?v=, youtu.be/, vimeo.com/
  const yt = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/)([\w-]+)/)
  if (yt) return `https://www.youtube.com/embed/${yt[1]}`
  const vim = url.match(/vimeo\.com\/(\d+)/)
  if (vim) return `https://player.vimeo.com/video/${vim[1]}`
  return url
}

// ────────────────────────────────────────────────────────────────────────────
// Save & publish
// ────────────────────────────────────────────────────────────────────────────
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
    <NuxtLink
      to="/landing-pages"
      class="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
    >
      <ArrowLeft class="h-3.5 w-3.5" />
      Back to pages
    </NuxtLink>

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
          <Button variant="outline" class="gap-1.5" :disabled="updateMutation.isPending.value" @click="handleSave">
            <Save class="h-3.5 w-3.5" />
            {{ updateMutation.isPending.value ? 'Saving…' : 'Save' }}
          </Button>
          <Button class="gap-1.5" :disabled="publishMutation.isPending.value" @click="handleTogglePublish">
            <component :is="page.status === 'DRAFT' ? Send : EyeOff" class="h-3.5 w-3.5" />
            {{ page.status === 'DRAFT' ? 'Publish' : 'Unpublish' }}
          </Button>
        </div>
      </div>

      <!-- Three-column editor -->
      <div class="grid grid-cols-12 gap-4" style="min-height: 600px;">
        <!-- LEFT: block palette + page settings -->
        <div class="col-span-3 space-y-4">
          <!-- Palette, grouped by category -->
          <div class="glass hairline rounded-xl overflow-hidden">
            <button
              type="button"
              class="w-full px-4 py-3 flex items-center justify-between hover:bg-white/5 transition-colors"
              :style="paletteOpen ? 'border-bottom: 1px solid hsl(240 5% 100% / 0.06);' : ''"
              @click="paletteOpen = !paletteOpen"
            >
              <div class="flex items-center gap-2">
                <MousePointerClick class="h-4 w-4 text-muted-foreground" />
                <h3 class="text-sm font-semibold tracking-tight">Add block</h3>
              </div>
              <ChevronRight
                class="h-4 w-4 text-muted-foreground transition-transform"
                :class="paletteOpen ? 'rotate-90' : ''"
              />
            </button>
            <div v-show="paletteOpen" class="p-3 space-y-3">
              <div v-for="cat in blocksByCategory" :key="cat.key">
                <p class="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold px-1 mb-1.5">
                  {{ cat.label }}
                </p>
                <div class="grid grid-cols-2 gap-2">
                  <button
                    v-for="def in cat.defs"
                    :key="def.type"
                    type="button"
                    draggable="true"
                    class="flex flex-col items-center gap-1.5 rounded-lg hairline py-3 px-2 text-muted-foreground hover:text-foreground hover:bg-white/5 hover:border-primary/40 transition-colors cursor-grab active:cursor-grabbing"
                    @click="addBlock(def.type)"
                    @dragstart="onPaletteDragStart($event, def.type)"
                    @dragend="onPaletteDragEnd"
                  >
                    <component :is="blockIcon[def.type]" class="h-4 w-4" />
                    <span class="text-xs">{{ def.label }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Page settings -->
          <div class="glass hairline rounded-xl overflow-hidden">
            <button
              type="button"
              class="w-full px-4 py-3 flex items-center justify-between hover:bg-white/5 transition-colors"
              :style="settingsOpen ? 'border-bottom: 1px solid hsl(240 5% 100% / 0.06);' : ''"
              @click="settingsOpen = !settingsOpen"
            >
              <div class="flex items-center gap-2">
                <Settings class="h-4 w-4 text-muted-foreground" />
                <h3 class="text-sm font-semibold tracking-tight">Page settings</h3>
              </div>
              <ChevronRight
                class="h-4 w-4 text-muted-foreground transition-transform"
                :class="settingsOpen ? 'rotate-90' : ''"
              />
            </button>
            <div v-show="settingsOpen" class="p-4 space-y-3">
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
          <div
            class="glass hairline rounded-xl overflow-hidden flex flex-col h-full transition-colors"
            :class="(draggingPaletteType || draggingBlockId) && dragOverIndex !== null ? 'ring-2 ring-primary/40' : ''"
            @dragover.prevent="onDragOverZone($event, blocks.length)"
            @drop.prevent="onDropZone($event, dragOverIndex ?? blocks.length)"
          >
            <div class="px-4 py-3 flex items-center justify-between" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
              <h3 class="text-sm font-semibold tracking-tight">Preview</h3>
              <span class="text-xs text-muted-foreground">
                {{ blocks.length }} {{ blocks.length === 1 ? 'block' : 'blocks' }}
              </span>
            </div>

            <div
              v-if="!blocks.length"
              class="flex-1 flex flex-col items-center justify-center py-16 px-6 text-center"
            >
              <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mb-4">
                <LayoutPanelTop class="h-5 w-5 text-muted-foreground" />
              </div>
              <h4 class="text-sm font-semibold tracking-tight">Start with a block</h4>
              <p class="text-sm text-muted-foreground mt-1 max-w-xs">
                Click or drag from the left panel — hero, text, image, video, CTA, form, features, testimonial, stats, FAQ, divider, or spacer.
              </p>
            </div>

            <div v-else class="p-4 space-y-1">
              <!-- Drop zone before first block -->
              <div
                class="h-2 rounded transition-colors"
                :class="dragOverIndex === 0 ? 'bg-primary/40' : ''"
                @dragover.prevent.stop="onDragOverZone($event, 0)"
                @drop.prevent.stop="onDropZone($event, 0)"
              />
              <template v-for="(block, idx) in blocks" :key="block.id">
                <div
                  draggable="true"
                  class="rounded-lg hairline p-3 cursor-pointer transition-all"
                  :class="[
                    selectedBlockId === block.id ? 'ring-2 ring-primary/40 bg-primary/5' : 'hover:bg-white/5',
                    draggingBlockId === block.id ? 'opacity-40' : '',
                  ]"
                  @click="selectedBlockId = block.id"
                  @dragstart="onBlockDragStart($event, block.id)"
                  @dragend="onBlockDragEnd"
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
                <!-- HERO -->
                <div
                  v-if="block.type === 'hero'"
                  class="py-6 px-4 rounded-md"
                  :class="[alignClass(block.props.alignment), heroBg(block.props.bg)]"
                >
                  <h2 class="text-xl font-bold">{{ block.props.title }}</h2>
                  <p class="text-sm text-muted-foreground mt-1">{{ block.props.subtitle }}</p>
                  <button class="mt-3 px-3 py-1 text-xs rounded bg-primary text-primary-foreground">
                    {{ block.props.ctaText }}
                  </button>
                </div>

                <!-- HEADING -->
                <component
                  v-else-if="block.type === 'heading'"
                  :is="'h' + block.props.level"
                  :class="[
                    alignClass(block.props.alignment),
                    block.props.level === 1 ? 'text-2xl font-bold'
                      : block.props.level === 2 ? 'text-xl font-semibold'
                      : 'text-lg font-semibold',
                  ]"
                >
                  {{ block.props.text }}
                </component>

                <!-- TEXT -->
                <p
                  v-else-if="block.type === 'text'"
                  :class="[alignClass(block.props.alignment), textSizeClass(block.props.size)]"
                >
                  {{ block.props.content }}
                </p>

                <!-- COLUMNS -->
                <div
                  v-else-if="block.type === 'columns'"
                  class="grid"
                  :class="gapClass(block.props.gap)"
                  :style="{ gridTemplateColumns: `repeat(${block.props.columns}, minmax(0, 1fr))` }"
                >
                  <div v-for="(item, i) in (block.props.items as any[])" :key="i" class="rounded-md bg-white/5 p-3">
                    <p class="text-sm font-semibold">{{ item.title }}</p>
                    <p class="text-xs text-muted-foreground mt-1">{{ item.body }}</p>
                  </div>
                </div>

                <!-- CHECKLIST -->
                <div v-else-if="block.type === 'checklist'">
                  <p v-if="block.props.heading" class="text-sm font-semibold mb-2">{{ block.props.heading }}</p>
                  <div
                    class="grid gap-2"
                    :style="{ gridTemplateColumns: `repeat(${block.props.columns}, minmax(0, 1fr))` }"
                  >
                    <div v-for="(item, i) in (block.props.items as any[])" :key="i" class="flex items-start gap-2 text-sm">
                      <div class="h-4 w-4 rounded-full bg-primary/20 flex items-center justify-center shrink-0 mt-0.5">
                        <span class="text-[10px] text-primary">✓</span>
                      </div>
                      <span>{{ item.text }}</span>
                    </div>
                  </div>
                </div>

                <!-- IMAGE -->
                <div v-else-if="block.type === 'image'" class="flex justify-center">
                  <img
                    :src="block.props.src"
                    :alt="block.props.alt"
                    class="object-contain max-h-40"
                    :class="[imageWidthClass(block.props.width), block.props.rounded ? 'rounded-lg' : '']"
                  />
                </div>

                <!-- VIDEO -->
                <div v-else-if="block.type === 'video'">
                  <div
                    v-if="!block.props.url"
                    class="rounded-md bg-white/5 py-8 text-center text-xs text-muted-foreground"
                  >
                    <Video class="h-6 w-6 mx-auto mb-1 opacity-60" />
                    No video URL
                  </div>
                  <div v-else class="relative rounded-md overflow-hidden bg-black" :style="{ aspectRatio: block.props.aspect }">
                    <iframe
                      :src="youtubeEmbed(block.props.url) ?? ''"
                      class="w-full h-full"
                      frameborder="0"
                      allowfullscreen
                    />
                  </div>
                </div>

                <!-- CTA -->
                <div v-else-if="block.type === 'cta'" :class="alignClass(block.props.alignment)">
                  <button
                    class="inline-flex px-4 py-2 rounded text-sm"
                    :class="[
                      block.props.variant === 'outline'
                        ? 'border border-primary text-primary'
                        : 'bg-primary text-primary-foreground',
                      block.props.size === 'sm' ? 'text-xs px-3 py-1'
                        : block.props.size === 'lg' ? 'text-base px-5 py-2.5' : '',
                    ]"
                  >
                    {{ block.props.text }}
                  </button>
                </div>

                <!-- FORM -->
                <div v-else-if="block.type === 'form'" class="space-y-1">
                  <h4 v-if="block.props.heading" class="text-sm font-semibold">{{ block.props.heading }}</h4>
                  <p v-if="block.props.description" class="text-xs text-muted-foreground">{{ block.props.description }}</p>
                  <div v-if="block.props.formId" class="mt-2 rounded-md bg-white/5 p-2 text-xs">
                    Form: <span class="font-medium">{{ forms?.find(f => f.id === block.props.formId)?.name ?? 'Unknown' }}</span>
                  </div>
                  <div v-else class="mt-2 inline-flex items-center gap-1 text-xs text-destructive">
                    <AlertCircle class="h-3 w-3" /> No form selected
                  </div>
                </div>

                <!-- PRICING -->
                <div v-else-if="block.type === 'pricing'">
                  <h3 v-if="block.props.heading" class="text-base font-semibold text-center">{{ block.props.heading }}</h3>
                  <p v-if="block.props.subtitle" class="text-xs text-muted-foreground text-center mt-0.5">{{ block.props.subtitle }}</p>
                  <div
                    class="grid gap-2 mt-3"
                    :style="{ gridTemplateColumns: `repeat(${(block.props.plans ?? []).length || 1}, minmax(0, 1fr))` }"
                  >
                    <div
                      v-for="(plan, i) in (block.props.plans as any[])"
                      :key="i"
                      class="rounded-md p-3 text-center hairline flex flex-col"
                      :class="plan.featured ? 'bg-primary/10 border-primary/40' : 'bg-white/2'"
                    >
                      <p class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">{{ plan.name }}</p>
                      <p class="mt-1">
                        <span class="text-lg font-bold">{{ plan.price }}</span>
                        <span class="text-xs text-muted-foreground">{{ plan.period }}</span>
                      </p>
                      <ul class="mt-2 space-y-0.5 text-xs text-left flex-1">
                        <li v-for="(feat, fi) in (plan.features as string[])" :key="fi" class="flex items-start gap-1.5">
                          <span class="text-primary">✓</span><span>{{ feat }}</span>
                        </li>
                      </ul>
                      <button
                        class="mt-3 text-xs px-2 py-1 rounded"
                        :class="plan.featured ? 'bg-primary text-primary-foreground' : 'border border-primary/40 text-primary'"
                      >{{ plan.ctaText }}</button>
                    </div>
                  </div>
                </div>

                <!-- FEATURES -->
                <div v-else-if="block.type === 'features'">
                  <h3 v-if="block.props.heading" class="text-base font-semibold text-center mb-3">
                    {{ block.props.heading }}
                  </h3>
                  <div class="grid gap-3" :style="{ gridTemplateColumns: `repeat(${block.props.columns}, minmax(0, 1fr))` }">
                    <div v-for="(item, i) in block.props.items" :key="i" class="text-center">
                      <div class="text-xl">{{ item.icon }}</div>
                      <p class="text-sm font-semibold mt-1">{{ item.title }}</p>
                      <p class="text-xs text-muted-foreground mt-0.5">{{ item.description }}</p>
                    </div>
                  </div>
                </div>

                <!-- TESTIMONIAL -->
                <div v-else-if="block.type === 'testimonial'" class="py-2">
                  <p class="italic">"{{ block.props.quote }}"</p>
                  <div class="flex items-center gap-2 mt-3">
                    <div class="h-8 w-8 rounded-full bg-white/10 overflow-hidden flex items-center justify-center">
                      <img v-if="block.props.avatarUrl" :src="block.props.avatarUrl" class="h-full w-full object-cover" />
                      <Quote v-else class="h-3.5 w-3.5 text-muted-foreground" />
                    </div>
                    <div>
                      <p class="text-xs font-semibold">{{ block.props.authorName }}</p>
                      <p class="text-xs text-muted-foreground">{{ block.props.authorRole }}</p>
                    </div>
                  </div>
                </div>

                <!-- STATS -->
                <div
                  v-else-if="block.type === 'stats'"
                  class="grid gap-3 py-2"
                  :style="{ gridTemplateColumns: `repeat(${(block.props.items ?? []).length || 1}, minmax(0, 1fr))` }"
                >
                  <div v-for="(item, i) in block.props.items" :key="i" class="text-center">
                    <p class="text-xl font-bold tabular-nums">{{ item.value }}</p>
                    <p class="text-xs text-muted-foreground">{{ item.label }}</p>
                  </div>
                </div>

                <!-- FAQ -->
                <div v-else-if="block.type === 'faq'">
                  <h3 v-if="block.props.heading" class="text-base font-semibold mb-2">{{ block.props.heading }}</h3>
                  <div class="space-y-2">
                    <details v-for="(item, i) in block.props.items" :key="i" class="rounded-md hairline p-2">
                      <summary class="text-sm font-medium cursor-pointer">{{ item.question }}</summary>
                      <p class="mt-1 text-xs text-muted-foreground">{{ item.answer }}</p>
                    </details>
                  </div>
                </div>

                <!-- STEPS -->
                <div v-else-if="block.type === 'steps'">
                  <h3 v-if="block.props.heading" class="text-base font-semibold text-center mb-3">{{ block.props.heading }}</h3>
                  <div class="space-y-2">
                    <div
                      v-for="(item, i) in (block.props.items as any[])"
                      :key="i"
                      class="flex items-start gap-3 rounded-md bg-white/5 p-2"
                    >
                      <div class="h-6 w-6 rounded-full bg-primary/20 text-primary text-xs font-bold flex items-center justify-center shrink-0">
                        {{ i + 1 }}
                      </div>
                      <div>
                        <p class="text-sm font-semibold">{{ item.title }}</p>
                        <p class="text-xs text-muted-foreground mt-0.5">{{ item.body }}</p>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- LOGOS -->
                <div v-else-if="block.type === 'logos'">
                  <p v-if="block.props.heading" class="text-xs uppercase tracking-wider text-muted-foreground text-center mb-2">
                    {{ block.props.heading }}
                  </p>
                  <div class="flex flex-wrap items-center justify-center gap-4">
                    <div
                      v-for="(logo, i) in (block.props.logos as any[])"
                      :key="i"
                      class="h-10 min-w-20 rounded-md bg-white/5 flex items-center justify-center px-3"
                    >
                      <img v-if="logo.src" :src="logo.src" :alt="logo.alt" class="max-h-6 max-w-20 object-contain opacity-70" />
                      <span v-else class="text-xs text-muted-foreground">{{ logo.alt || 'Logo' }}</span>
                    </div>
                  </div>
                </div>

                <!-- DIVIDER -->
                <hr
                  v-else-if="block.type === 'divider'"
                  :style="{ margin: dividerMargin(block.props.size), borderColor: 'hsl(240 5% 100% / 0.1)' }"
                />

                <!-- SPACER -->
                <div
                  v-else-if="block.type === 'spacer'"
                  :style="{ height: spacerHeight(block.props.size) }"
                  class="bg-white/2 rounded-md flex items-center justify-center text-[10px] text-muted-foreground uppercase tracking-wider"
                >
                  Spacer · {{ block.props.size }}
                </div>

                <!-- EMBED -->
                <div
                  v-else-if="block.type === 'embed'"
                  class="rounded-md hairline bg-white/2 p-2 font-mono text-[11px] text-muted-foreground whitespace-pre-wrap break-all max-h-32 overflow-y-auto"
                >
                  <span v-if="!block.props.html">(empty HTML embed)</span>
                  <span v-else>{{ block.props.html }}</span>
                </div>
                </div>

                <!-- Drop zone after this block — drop here inserts at idx+1 -->
                <div
                  class="h-2 rounded transition-colors"
                  :class="dragOverIndex === idx + 1 ? 'bg-primary/40' : ''"
                  @dragover.prevent.stop="onDragOverZone($event, idx + 1)"
                  @drop.prevent.stop="onDropZone($event, idx + 1)"
                />
              </template>
            </div>
          </div>
        </div>

        <!-- RIGHT: properties panel -->
        <div class="col-span-3">
          <div class="glass hairline rounded-xl overflow-hidden">
            <button
              type="button"
              class="w-full px-4 py-3 flex items-center justify-between hover:bg-white/5 transition-colors"
              :style="propertiesOpen ? 'border-bottom: 1px solid hsl(240 5% 100% / 0.06);' : ''"
              @click="propertiesOpen = !propertiesOpen"
            >
              <div class="flex items-center gap-2">
                <SlidersHorizontal class="h-4 w-4 text-muted-foreground" />
                <h3 class="text-sm font-semibold tracking-tight">
                  {{ selectedBlock ? selectedBlock.type.toUpperCase() + ' properties' : 'Properties' }}
                </h3>
              </div>
              <ChevronRight
                class="h-4 w-4 text-muted-foreground transition-transform"
                :class="propertiesOpen ? 'rotate-90' : ''"
              />
            </button>

            <div v-if="propertiesOpen && !selectedBlock" class="p-6 text-center">
              <div class="h-10 w-10 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-3">
                <SlidersHorizontal class="h-4 w-4 text-muted-foreground" />
              </div>
              <p class="text-xs text-muted-foreground">
                Click a block in the preview to edit its properties here.
              </p>
            </div>

            <div v-else-if="propertiesOpen && selectedBlock" class="p-4 space-y-3">
              <!-- ────── HERO ────── -->
              <template v-if="selectedBlock.type === 'hero'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Title</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.title" @update:model-value="updateBlockProp('title', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Subtitle</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.subtitle" @update:model-value="updateBlockProp('subtitle', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">CTA text</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.ctaText" @update:model-value="updateBlockProp('ctaText', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">CTA URL</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.ctaUrl" @update:model-value="updateBlockProp('ctaUrl', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Alignment</Label>
                  <div class="grid grid-cols-3 gap-1">
                    <button
                      v-for="opt in ALIGN_OPTIONS"
                      :key="opt.value"
                      type="button"
                      class="py-1.5 rounded-md hairline flex items-center justify-center transition-colors"
                      :class="selectedBlock.props.alignment === opt.value
                        ? 'bg-primary/15 text-primary border-primary/40'
                        : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
                      @click="updateBlockProp('alignment', opt.value)"
                    >
                      <component :is="opt.icon" class="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Background</Label>
                  <Select :model-value="selectedBlock.props.bg" @update:model-value="updateBlockProp('bg', $event)">
                    <SelectTrigger class="h-9 w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">None</SelectItem>
                      <SelectItem value="subtle">Subtle</SelectItem>
                      <SelectItem value="primary">Primary tint</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </template>

              <!-- ────── HEADING ────── -->
              <template v-else-if="selectedBlock.type === 'heading'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Text</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.text" @update:model-value="updateBlockProp('text', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Level</Label>
                  <div class="grid grid-cols-3 gap-1">
                    <button
                      v-for="n in [1, 2, 3]"
                      :key="n"
                      type="button"
                      class="py-1.5 rounded-md hairline text-xs font-semibold transition-colors"
                      :class="selectedBlock.props.level === n
                        ? 'bg-primary/15 text-primary border-primary/40'
                        : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
                      @click="updateBlockProp('level', n)"
                    >H{{ n }}</button>
                  </div>
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Alignment</Label>
                  <div class="grid grid-cols-3 gap-1">
                    <button
                      v-for="opt in ALIGN_OPTIONS"
                      :key="opt.value"
                      type="button"
                      class="py-1.5 rounded-md hairline flex items-center justify-center transition-colors"
                      :class="selectedBlock.props.alignment === opt.value
                        ? 'bg-primary/15 text-primary border-primary/40'
                        : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
                      @click="updateBlockProp('alignment', opt.value)"
                    >
                      <component :is="opt.icon" class="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
              </template>

              <!-- ────── TEXT ────── -->
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
                <div class="space-y-1.5">
                  <Label class="text-xs">Alignment</Label>
                  <div class="grid grid-cols-3 gap-1">
                    <button
                      v-for="opt in ALIGN_OPTIONS"
                      :key="opt.value"
                      type="button"
                      class="py-1.5 rounded-md hairline flex items-center justify-center transition-colors"
                      :class="selectedBlock.props.alignment === opt.value
                        ? 'bg-primary/15 text-primary border-primary/40'
                        : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
                      @click="updateBlockProp('alignment', opt.value)"
                    >
                      <component :is="opt.icon" class="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Size</Label>
                  <Select :model-value="selectedBlock.props.size" @update:model-value="updateBlockProp('size', $event)">
                    <SelectTrigger class="h-9 w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="sm">Small</SelectItem>
                      <SelectItem value="md">Medium</SelectItem>
                      <SelectItem value="lg">Large</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </template>

              <!-- ────── COLUMNS ────── -->
              <template v-else-if="selectedBlock.type === 'columns'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Columns</Label>
                  <div class="grid grid-cols-3 gap-1">
                    <button
                      v-for="n in [2, 3, 4]"
                      :key="n"
                      type="button"
                      class="py-1.5 rounded-md hairline text-xs font-semibold transition-colors"
                      :class="selectedBlock.props.columns === n
                        ? 'bg-primary/15 text-primary border-primary/40'
                        : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
                      @click="updateBlockProp('columns', n)"
                    >{{ n }} cols</button>
                  </div>
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Gap</Label>
                  <Select :model-value="selectedBlock.props.gap" @update:model-value="updateBlockProp('gap', $event)">
                    <SelectTrigger class="h-9 w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="sm">Small</SelectItem>
                      <SelectItem value="md">Medium</SelectItem>
                      <SelectItem value="lg">Large</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div class="space-y-2 pt-1" style="border-top: 1px solid hsl(240 5% 100% / 0.06);">
                  <div class="flex items-center justify-between pt-2">
                    <Label class="text-xs">Cells</Label>
                    <button
                      type="button"
                      class="text-xs text-primary hover:underline"
                      @click="addItem('items', { title: 'New column', body: 'Describe it here.' })"
                    >+ Add</button>
                  </div>
                  <div
                    v-for="(item, idx) in (selectedBlock.props.items as any[])"
                    :key="idx"
                    class="rounded-md hairline p-2 space-y-1.5"
                  >
                    <div class="flex items-center justify-between">
                      <span class="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">Column {{ idx + 1 }}</span>
                      <button type="button" class="text-muted-foreground hover:text-destructive" @click="removeItem('items', idx)">
                        <Trash2 class="h-3 w-3" />
                      </button>
                    </div>
                    <Input class="h-8 text-xs" placeholder="Title" :model-value="item.title" @update:model-value="updateItemAt('items', idx, { title: $event })" />
                    <textarea
                      class="w-full rounded-md border border-input bg-background px-2 py-1.5 text-xs"
                      rows="3"
                      placeholder="Body"
                      :value="item.body"
                      @input="updateItemAt('items', idx, { body: ($event.target as HTMLTextAreaElement).value })"
                    />
                  </div>
                </div>
              </template>

              <!-- ────── CHECKLIST ────── -->
              <template v-else-if="selectedBlock.type === 'checklist'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Heading <span class="text-muted-foreground font-normal">(optional)</span></Label>
                  <Input class="h-9" :model-value="selectedBlock.props.heading" @update:model-value="updateBlockProp('heading', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Columns</Label>
                  <div class="grid grid-cols-3 gap-1">
                    <button
                      v-for="n in [1, 2, 3]"
                      :key="n"
                      type="button"
                      class="py-1.5 rounded-md hairline text-xs font-semibold transition-colors"
                      :class="selectedBlock.props.columns === n
                        ? 'bg-primary/15 text-primary border-primary/40'
                        : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
                      @click="updateBlockProp('columns', n)"
                    >{{ n }}</button>
                  </div>
                </div>
                <div class="space-y-2 pt-1" style="border-top: 1px solid hsl(240 5% 100% / 0.06);">
                  <div class="flex items-center justify-between pt-2">
                    <Label class="text-xs">Items</Label>
                    <button
                      type="button"
                      class="text-xs text-primary hover:underline"
                      @click="addItem('items', { text: 'New benefit' })"
                    >+ Add</button>
                  </div>
                  <div
                    v-for="(item, idx) in (selectedBlock.props.items as any[])"
                    :key="idx"
                    class="flex items-center gap-1.5"
                  >
                    <Input class="h-8 text-xs flex-1" placeholder="Benefit" :model-value="item.text" @update:model-value="updateItemAt('items', idx, { text: $event })" />
                    <button type="button" class="text-muted-foreground hover:text-destructive shrink-0" @click="removeItem('items', idx)">
                      <Trash2 class="h-3 w-3" />
                    </button>
                  </div>
                </div>
              </template>

              <!-- ────── IMAGE ────── -->
              <template v-else-if="selectedBlock.type === 'image'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Image URL</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.src" @update:model-value="updateBlockProp('src', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Alt text</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.alt" @update:model-value="updateBlockProp('alt', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Link <span class="text-muted-foreground font-normal">(optional)</span></Label>
                  <Input class="h-9" :model-value="selectedBlock.props.link" @update:model-value="updateBlockProp('link', $event)" placeholder="https://..." />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Width</Label>
                  <Select :model-value="selectedBlock.props.width" @update:model-value="updateBlockProp('width', $event)">
                    <SelectTrigger class="h-9 w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="sm">Small</SelectItem>
                      <SelectItem value="md">Medium</SelectItem>
                      <SelectItem value="lg">Large</SelectItem>
                      <SelectItem value="full">Full width</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <label class="flex items-center gap-2 cursor-pointer select-none">
                  <Checkbox :model-value="selectedBlock.props.rounded" @update:model-value="updateBlockProp('rounded', $event)" />
                  <span class="text-xs">Rounded corners</span>
                </label>
              </template>

              <!-- ────── VIDEO ────── -->
              <template v-else-if="selectedBlock.type === 'video'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Video URL</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.url" @update:model-value="updateBlockProp('url', $event)" placeholder="YouTube / Vimeo / embed URL" />
                  <p class="text-xs text-muted-foreground">
                    YouTube and Vimeo auto-convert to embed form.
                  </p>
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Aspect ratio</Label>
                  <Select :model-value="selectedBlock.props.aspect" @update:model-value="updateBlockProp('aspect', $event)">
                    <SelectTrigger class="h-9 w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="16/9">16:9 (widescreen)</SelectItem>
                      <SelectItem value="4/3">4:3</SelectItem>
                      <SelectItem value="1/1">1:1 (square)</SelectItem>
                      <SelectItem value="9/16">9:16 (vertical)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </template>

              <!-- ────── CTA ────── -->
              <template v-else-if="selectedBlock.type === 'cta'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Button text</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.text" @update:model-value="updateBlockProp('text', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">URL</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.url" @update:model-value="updateBlockProp('url', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Variant</Label>
                  <div class="grid grid-cols-2 gap-1">
                    <button
                      v-for="v in ['primary', 'outline']"
                      :key="v"
                      type="button"
                      class="py-1.5 rounded-md hairline text-xs font-medium transition-colors"
                      :class="selectedBlock.props.variant === v
                        ? 'bg-primary/15 text-primary border-primary/40'
                        : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
                      @click="updateBlockProp('variant', v)"
                    >{{ v }}</button>
                  </div>
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Size</Label>
                  <Select :model-value="selectedBlock.props.size" @update:model-value="updateBlockProp('size', $event)">
                    <SelectTrigger class="h-9 w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="sm">Small</SelectItem>
                      <SelectItem value="md">Medium</SelectItem>
                      <SelectItem value="lg">Large</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Alignment</Label>
                  <div class="grid grid-cols-3 gap-1">
                    <button
                      v-for="opt in ALIGN_OPTIONS"
                      :key="opt.value"
                      type="button"
                      class="py-1.5 rounded-md hairline flex items-center justify-center transition-colors"
                      :class="selectedBlock.props.alignment === opt.value
                        ? 'bg-primary/15 text-primary border-primary/40'
                        : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
                      @click="updateBlockProp('alignment', opt.value)"
                    >
                      <component :is="opt.icon" class="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
                <label class="flex items-center gap-2 cursor-pointer select-none">
                  <Checkbox :model-value="selectedBlock.props.newTab" @update:model-value="updateBlockProp('newTab', $event)" />
                  <span class="text-xs">Open in new tab</span>
                </label>
              </template>

              <!-- ────── FORM ────── -->
              <template v-else-if="selectedBlock.type === 'form'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Heading <span class="text-muted-foreground font-normal">(optional)</span></Label>
                  <Input class="h-9" :model-value="selectedBlock.props.heading" @update:model-value="updateBlockProp('heading', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Description <span class="text-muted-foreground font-normal">(optional)</span></Label>
                  <Input class="h-9" :model-value="selectedBlock.props.description" @update:model-value="updateBlockProp('description', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Form</Label>
                  <Select :model-value="selectedBlock.props.formId" @update:model-value="updateBlockProp('formId', $event)">
                    <SelectTrigger class="h-9 w-full"><SelectValue placeholder="Select a form" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem v-for="f in forms" :key="f.id" :value="f.id">{{ f.name }}</SelectItem>
                    </SelectContent>
                  </Select>
                  <NuxtLink v-if="!forms?.length" to="/forms" class="text-xs text-primary hover:underline">
                    Create a form first →
                  </NuxtLink>
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Submit button text</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.submitText" @update:model-value="updateBlockProp('submitText', $event)" />
                </div>
              </template>

              <!-- ────── PRICING ────── -->
              <template v-else-if="selectedBlock.type === 'pricing'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Heading</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.heading" @update:model-value="updateBlockProp('heading', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Subtitle</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.subtitle" @update:model-value="updateBlockProp('subtitle', $event)" />
                </div>
                <div class="space-y-2 pt-1" style="border-top: 1px solid hsl(240 5% 100% / 0.06);">
                  <div class="flex items-center justify-between pt-2">
                    <Label class="text-xs">Plans</Label>
                    <button
                      type="button"
                      class="text-xs text-primary hover:underline"
                      @click="addItem('plans', { name: 'New plan', price: '$0', period: '/mo', features: ['Feature'], ctaText: 'Choose', ctaUrl: '#', featured: false })"
                    >+ Add</button>
                  </div>
                  <div
                    v-for="(plan, idx) in (selectedBlock.props.plans as any[])"
                    :key="idx"
                    class="rounded-md hairline p-2 space-y-1.5"
                  >
                    <div class="flex items-center justify-between">
                      <span class="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">Plan {{ idx + 1 }}</span>
                      <button type="button" class="text-muted-foreground hover:text-destructive" @click="removeItem('plans', idx)">
                        <Trash2 class="h-3 w-3" />
                      </button>
                    </div>
                    <Input class="h-8 text-xs" placeholder="Name" :model-value="plan.name" @update:model-value="updateItemAt('plans', idx, { name: $event })" />
                    <div class="grid grid-cols-2 gap-1.5">
                      <Input class="h-8 text-xs" placeholder="Price" :model-value="plan.price" @update:model-value="updateItemAt('plans', idx, { price: $event })" />
                      <Input class="h-8 text-xs" placeholder="/mo" :model-value="plan.period" @update:model-value="updateItemAt('plans', idx, { period: $event })" />
                    </div>
                    <Input class="h-8 text-xs" placeholder="CTA text" :model-value="plan.ctaText" @update:model-value="updateItemAt('plans', idx, { ctaText: $event })" />
                    <Input class="h-8 text-xs" placeholder="CTA URL" :model-value="plan.ctaUrl" @update:model-value="updateItemAt('plans', idx, { ctaUrl: $event })" />
                    <div class="space-y-1">
                      <Label class="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">Features</Label>
                      <div v-for="(feat, fi) in (plan.features as string[])" :key="fi" class="flex items-center gap-1">
                        <Input
                          class="h-8 text-xs flex-1"
                          placeholder="Feature"
                          :model-value="feat"
                          @update:model-value="(v) => {
                            const feats = [...plan.features]; feats[fi] = String(v ?? '');
                            updateItemAt('plans', idx, { features: feats })
                          }"
                        />
                        <button
                          type="button"
                          class="text-muted-foreground hover:text-destructive shrink-0"
                          @click="() => {
                            const feats = [...plan.features]; feats.splice(fi, 1);
                            updateItemAt('plans', idx, { features: feats })
                          }"
                        >
                          <Trash2 class="h-3 w-3" />
                        </button>
                      </div>
                      <button
                        type="button"
                        class="text-[11px] text-primary hover:underline inline-flex items-center gap-0.5"
                        @click="() => updateItemAt('plans', idx, { features: [...plan.features, 'New feature'] })"
                      >
                        <Plus class="h-3 w-3" /> Add feature
                      </button>
                    </div>
                    <label class="flex items-center gap-2 cursor-pointer select-none pt-1">
                      <Checkbox :model-value="plan.featured" @update:model-value="(v) => updateItemAt('plans', idx, { featured: v === true })" />
                      <span class="text-xs">Featured plan</span>
                    </label>
                  </div>
                </div>
              </template>

              <!-- ────── FEATURES ────── -->
              <template v-else-if="selectedBlock.type === 'features'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Heading <span class="text-muted-foreground font-normal">(optional)</span></Label>
                  <Input class="h-9" :model-value="selectedBlock.props.heading" @update:model-value="updateBlockProp('heading', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Columns</Label>
                  <div class="grid grid-cols-2 gap-1">
                    <button
                      v-for="n in [2, 3]"
                      :key="n"
                      type="button"
                      class="py-1.5 rounded-md hairline text-xs font-semibold transition-colors"
                      :class="selectedBlock.props.columns === n
                        ? 'bg-primary/15 text-primary border-primary/40'
                        : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
                      @click="updateBlockProp('columns', n)"
                    >{{ n }} cols</button>
                  </div>
                </div>
                <div class="space-y-2 pt-1" style="border-top: 1px solid hsl(240 5% 100% / 0.06);">
                  <div class="flex items-center justify-between pt-2">
                    <Label class="text-xs">Features</Label>
                    <button
                      type="button"
                      class="text-xs text-primary hover:underline"
                      @click="addItem('items', { icon: '✨', title: 'New feature', description: 'Describe it here.' })"
                    >+ Add</button>
                  </div>
                  <div
                    v-for="(item, idx) in (selectedBlock.props.items as any[])"
                    :key="idx"
                    class="rounded-md hairline p-2 space-y-1.5"
                  >
                    <div class="flex items-center justify-between">
                      <span class="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">Item {{ idx + 1 }}</span>
                      <button type="button" class="text-muted-foreground hover:text-destructive" @click="removeItem('items', idx)">
                        <Trash2 class="h-3 w-3" />
                      </button>
                    </div>
                    <Input class="h-8 text-xs" placeholder="Icon (emoji)" :model-value="item.icon" @update:model-value="updateItemAt('items', idx, { icon: $event })" />
                    <Input class="h-8 text-xs" placeholder="Title" :model-value="item.title" @update:model-value="updateItemAt('items', idx, { title: $event })" />
                    <Input class="h-8 text-xs" placeholder="Description" :model-value="item.description" @update:model-value="updateItemAt('items', idx, { description: $event })" />
                  </div>
                </div>
              </template>

              <!-- ────── TESTIMONIAL ────── -->
              <template v-else-if="selectedBlock.type === 'testimonial'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Quote</Label>
                  <textarea
                    class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                    rows="4"
                    :value="selectedBlock.props.quote"
                    @input="updateBlockProp('quote', ($event.target as HTMLTextAreaElement).value)"
                  />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Author name</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.authorName" @update:model-value="updateBlockProp('authorName', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Author role</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.authorRole" @update:model-value="updateBlockProp('authorRole', $event)" />
                </div>
                <div class="space-y-1.5">
                  <Label class="text-xs">Avatar URL</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.avatarUrl" @update:model-value="updateBlockProp('avatarUrl', $event)" placeholder="https://..." />
                </div>
              </template>

              <!-- ────── STATS ────── -->
              <template v-else-if="selectedBlock.type === 'stats'">
                <div class="flex items-center justify-between">
                  <Label class="text-xs">Stats</Label>
                  <button
                    type="button"
                    class="text-xs text-primary hover:underline"
                    @click="addItem('items', { value: '100', label: 'Label' })"
                  >+ Add</button>
                </div>
                <div
                  v-for="(item, idx) in (selectedBlock.props.items as any[])"
                  :key="idx"
                  class="rounded-md hairline p-2 space-y-1.5"
                >
                  <div class="flex items-center justify-between">
                    <span class="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">Stat {{ idx + 1 }}</span>
                    <button type="button" class="text-muted-foreground hover:text-destructive" @click="removeItem('items', idx)">
                      <Trash2 class="h-3 w-3" />
                    </button>
                  </div>
                  <Input class="h-8 text-xs" placeholder="Value (e.g. 10k+)" :model-value="item.value" @update:model-value="updateItemAt('items', idx, { value: $event })" />
                  <Input class="h-8 text-xs" placeholder="Label" :model-value="item.label" @update:model-value="updateItemAt('items', idx, { label: $event })" />
                </div>
              </template>

              <!-- ────── FAQ ────── -->
              <template v-else-if="selectedBlock.type === 'faq'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Heading <span class="text-muted-foreground font-normal">(optional)</span></Label>
                  <Input class="h-9" :model-value="selectedBlock.props.heading" @update:model-value="updateBlockProp('heading', $event)" />
                </div>
                <div class="flex items-center justify-between pt-2" style="border-top: 1px solid hsl(240 5% 100% / 0.06);">
                  <Label class="text-xs">Questions</Label>
                  <button
                    type="button"
                    class="text-xs text-primary hover:underline"
                    @click="addItem('items', { question: 'New question?', answer: 'The answer.' })"
                  >+ Add</button>
                </div>
                <div
                  v-for="(item, idx) in (selectedBlock.props.items as any[])"
                  :key="idx"
                  class="rounded-md hairline p-2 space-y-1.5"
                >
                  <div class="flex items-center justify-between">
                    <span class="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">Q{{ idx + 1 }}</span>
                    <button type="button" class="text-muted-foreground hover:text-destructive" @click="removeItem('items', idx)">
                      <Trash2 class="h-3 w-3" />
                    </button>
                  </div>
                  <Input class="h-8 text-xs" placeholder="Question" :model-value="item.question" @update:model-value="updateItemAt('items', idx, { question: $event })" />
                  <textarea
                    class="w-full rounded-md border border-input bg-background px-2 py-1.5 text-xs"
                    rows="3"
                    placeholder="Answer"
                    :value="item.answer"
                    @input="updateItemAt('items', idx, { answer: ($event.target as HTMLTextAreaElement).value })"
                  />
                </div>
              </template>

              <!-- ────── STEPS ────── -->
              <template v-else-if="selectedBlock.type === 'steps'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Heading</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.heading" @update:model-value="updateBlockProp('heading', $event)" />
                </div>
                <div class="space-y-2 pt-1" style="border-top: 1px solid hsl(240 5% 100% / 0.06);">
                  <div class="flex items-center justify-between pt-2">
                    <Label class="text-xs">Steps</Label>
                    <button
                      type="button"
                      class="text-xs text-primary hover:underline"
                      @click="addItem('items', { title: 'New step', body: 'Describe this step.' })"
                    >+ Add</button>
                  </div>
                  <div
                    v-for="(item, idx) in (selectedBlock.props.items as any[])"
                    :key="idx"
                    class="rounded-md hairline p-2 space-y-1.5"
                  >
                    <div class="flex items-center justify-between">
                      <span class="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">Step {{ idx + 1 }}</span>
                      <button type="button" class="text-muted-foreground hover:text-destructive" @click="removeItem('items', idx)">
                        <Trash2 class="h-3 w-3" />
                      </button>
                    </div>
                    <Input class="h-8 text-xs" placeholder="Title" :model-value="item.title" @update:model-value="updateItemAt('items', idx, { title: $event })" />
                    <textarea
                      class="w-full rounded-md border border-input bg-background px-2 py-1.5 text-xs"
                      rows="2"
                      placeholder="Body"
                      :value="item.body"
                      @input="updateItemAt('items', idx, { body: ($event.target as HTMLTextAreaElement).value })"
                    />
                  </div>
                </div>
              </template>

              <!-- ────── LOGOS ────── -->
              <template v-else-if="selectedBlock.type === 'logos'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Heading</Label>
                  <Input class="h-9" :model-value="selectedBlock.props.heading" @update:model-value="updateBlockProp('heading', $event)" />
                </div>
                <div class="space-y-2 pt-1" style="border-top: 1px solid hsl(240 5% 100% / 0.06);">
                  <div class="flex items-center justify-between pt-2">
                    <Label class="text-xs">Logos</Label>
                    <button
                      type="button"
                      class="text-xs text-primary hover:underline"
                      @click="addItem('logos', { src: '', alt: 'New company' })"
                    >+ Add</button>
                  </div>
                  <div
                    v-for="(logo, idx) in (selectedBlock.props.logos as any[])"
                    :key="idx"
                    class="rounded-md hairline p-2 space-y-1.5"
                  >
                    <div class="flex items-center justify-between">
                      <span class="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">Logo {{ idx + 1 }}</span>
                      <button type="button" class="text-muted-foreground hover:text-destructive" @click="removeItem('logos', idx)">
                        <Trash2 class="h-3 w-3" />
                      </button>
                    </div>
                    <Input class="h-8 text-xs" placeholder="Image URL" :model-value="logo.src" @update:model-value="updateItemAt('logos', idx, { src: $event })" />
                    <Input class="h-8 text-xs" placeholder="Alt / fallback text" :model-value="logo.alt" @update:model-value="updateItemAt('logos', idx, { alt: $event })" />
                  </div>
                </div>
              </template>

              <!-- ────── DIVIDER ────── -->
              <template v-else-if="selectedBlock.type === 'divider'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Spacing</Label>
                  <Select :model-value="selectedBlock.props.size" @update:model-value="updateBlockProp('size', $event)">
                    <SelectTrigger class="h-9 w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="sm">Small</SelectItem>
                      <SelectItem value="md">Medium</SelectItem>
                      <SelectItem value="lg">Large</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </template>

              <!-- ────── SPACER ────── -->
              <template v-else-if="selectedBlock.type === 'spacer'">
                <div class="space-y-1.5">
                  <Label class="text-xs">Height</Label>
                  <Select :model-value="selectedBlock.props.size" @update:model-value="updateBlockProp('size', $event)">
                    <SelectTrigger class="h-9 w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="sm">Small (16px)</SelectItem>
                      <SelectItem value="md">Medium (32px)</SelectItem>
                      <SelectItem value="lg">Large (48px)</SelectItem>
                      <SelectItem value="xl">Extra large (80px)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </template>

              <!-- ────── EMBED ────── -->
              <template v-else-if="selectedBlock.type === 'embed'">
                <div class="space-y-1.5">
                  <Label class="text-xs">HTML</Label>
                  <textarea
                    class="w-full rounded-md border border-input bg-background px-2 py-1.5 text-xs font-mono"
                    rows="10"
                    placeholder="<iframe ...></iframe>"
                    :value="selectedBlock.props.html"
                    @input="updateBlockProp('html', ($event.target as HTMLTextAreaElement).value)"
                  />
                  <p class="text-[11px] text-muted-foreground">
                    Raw HTML is rendered on the published page. Only paste markup you trust.
                  </p>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
