<!--
  PUBLIC landing page renderer — /p/{slug}.

  This page is:
    - SSR'd so search engines can crawl it
    - Public (no auth)
    - Fetches page data + form schemas from /api/v1/public/pages/{slug}
    - Renders each block based on its type
    - Handles form submission via /api/v1/public/pages/submit

  This is a special layout — we want it BARE (no sidebar/topbar).
-->
<script setup lang="ts">
import { toast } from 'vue-sonner'

// Use "auth" layout as a "no chrome" layout — it's centered without sidebar
definePageMeta({
  layout: false,       // No layout at all — this is a public landing page
})

const route = useRoute()
const slug = route.params.slug as string
const config = useRuntimeConfig()

// Fetch via SSR (auto runs on server, hydrates on client)
const { data: pageResponse, error } = await useFetch<{ data: PublicPageResponse }>(
  `${config.public.apiBaseUrl}/public/pages/${slug}`
)

interface PublicPageResponse {
  name: string
  slug: string
  metaTitle: string | null
  metaDescription: string | null
  blocks: any[]
  forms: Array<{
    id: string
    name: string
    fields: Array<{ key: string; label: string; type: string; required?: boolean }>
    successMessage: string | null
    successRedirectUrl: string | null
  }>
}

// SEO
useHead(() => {
  const p = pageResponse.value?.data
  if (!p) return {}
  return {
    title: p.metaTitle ?? p.name,
    meta: [
      p.metaDescription ? { name: 'description', content: p.metaDescription } : null,
    ].filter((m): m is { name: string; content: string } => m !== null),
  }
})

// ── Form submission state (per-form, keyed by formId) ──
const formValues = ref<Record<string, Record<string, string>>>({})
const submittingFormId = ref<string | null>(null)
const submittedForms = ref<Set<string>>(new Set())

// One error-bag per form on the page. Field keys are dynamic (from the form
// schema) so we scope errors under `${formId}.${fieldKey}` to keep forms
// independent when the page renders more than one.
const formErrors: Record<string, ReturnType<typeof useFieldErrors>> = {}
function errorsFor(formId: string) {
  if (!formErrors[formId]) formErrors[formId] = useFieldErrors()
  return formErrors[formId]
}

function getFormDef(formId: string) {
  return pageResponse.value?.data.forms.find(f => f.id === formId) ?? null
}

function getFieldsForForm(formDef: any): Array<{ key: string; label: string; type: string; required?: boolean }> {
  if (!formDef?.fields) return []
  if (Array.isArray(formDef.fields)) return formDef.fields
  if (typeof formDef.fields === 'string') {
    try { return JSON.parse(formDef.fields) } catch { return [] }
  }
  return []
}

/** Always returns a value bucket for a form — initializing on first access.
 *  Matters for v-model, which needs a non-undefined record to bind into. */
function valuesFor(formId: string): Record<string, string> {
  let bucket = formValues.value[formId]
  if (!bucket) {
    bucket = {}
    const fields = getFieldsForForm(getFormDef(formId))
    fields.forEach(f => { bucket![f.key] = '' })
    formValues.value[formId] = bucket
  }
  return bucket
}

// Eagerly populate buckets for every form on the page so v-model has a stable target
watchEffect(() => {
  const forms = pageResponse.value?.data.forms ?? []
  forms.forEach(f => valuesFor(f.id))
})

async function handleSubmit(formId: string) {
  const fields = getFieldsForForm(getFormDef(formId))
  const values = formValues.value[formId] ?? {}
  const errors = errorsFor(formId)

  // Check required fields — flag each offending field individually under its
  // own name so the error renders inline next to that input.
  errors.clear()
  for (const f of fields) {
    if (f.required && !values[f.key]?.trim()) {
      errors.set(f.key, `${f.label} is required.`)
    }
  }
  if (Object.keys(errors.map).length) return

  submittingFormId.value = formId
  try {
    const query = new URLSearchParams(window.location.search)
    const response = await $fetch<{ data: { message: string; redirectUrl?: string } }>(
      `${config.public.apiBaseUrl}/public/pages/submit`,
      {
        method: 'POST',
        body: {
          formId,
          landingPageId: undefined,    // we could pass the page ID here
          data: values,
          referrer: document.referrer || undefined,
          utmSource: query.get('utm_source') || undefined,
          utmMedium: query.get('utm_medium') || undefined,
          utmCampaign: query.get('utm_campaign') || undefined,
        },
      }
    )
    submittedForms.value.add(formId)

    if (response.data.redirectUrl) {
      window.location.href = response.data.redirectUrl
    } else {
      toast.success(response.data.message)
    }
  } catch (err: any) {
    errors.fromServerError(err, 'Submission failed')
  } finally {
    submittingFormId.value = null
  }
}

// ── Style-token helpers (mirror the editor's block config vocabulary) ──
function textAlign(v: string | undefined): string {
  return { left: 'text-left', center: 'text-center', right: 'text-right' }[v ?? ''] ?? 'text-left'
}
function heroAlign(v: string | undefined): string {
  return textAlign(v ?? 'center')
}
function heroBgClass(v: string | undefined): string {
  return {
    none: '',
    subtle: 'bg-gray-50',
    primary: 'bg-black/5',
  }[v ?? ''] ?? ''
}
function gapClassPublic(v: string | undefined): string {
  return { sm: 'gap-3', md: 'gap-6', lg: 'gap-10' }[v ?? ''] ?? 'gap-6'
}
function dividerSpacing(v: string | undefined): string {
  return { sm: '1rem 0', md: '2rem 0', lg: '3rem 0' }[v ?? ''] ?? '2rem 0'
}
function spacerHeightPublic(v: string | undefined): string {
  return { sm: '1rem', md: '2rem', lg: '3rem', xl: '5rem' }[v ?? ''] ?? '2rem'
}
function toEmbedUrl(url: string | undefined): string {
  if (!url) return ''
  const yt = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/)([\w-]+)/)
  if (yt) return `https://www.youtube.com/embed/${yt[1]}`
  const vim = url.match(/vimeo\.com\/(\d+)/)
  if (vim) return `https://player.vimeo.com/video/${vim[1]}`
  return url
}

// Touch-to-clear: remove a field's error as soon as the user starts typing.
// Using a single watcher over the whole formValues map — we inspect what
// changed and drop any error for a now-filled field.
watch(formValues, (val) => {
  for (const [formId, bucket] of Object.entries(val)) {
    const errs = formErrors[formId]
    if (!errs) continue
    for (const [k, v] of Object.entries(bucket)) {
      if (v?.trim()) errs.remove(k)
    }
  }
}, { deep: true })
</script>

<template>
  <div>
    <div v-if="error" class="min-h-screen flex items-center justify-center">
      <p class="text-muted-foreground">Page not found.</p>
    </div>

    <div v-else-if="pageResponse?.data" class="min-h-screen bg-white text-gray-900">
      <div class="max-w-3xl mx-auto px-6 py-12 space-y-8">
        <template v-for="block in pageResponse.data.blocks" :key="block.id">
          <!-- HERO -->
          <section
            v-if="block.type === 'hero'"
            class="py-12 px-6 rounded-lg"
            :class="[
              heroAlign(block.props.alignment),
              heroBgClass(block.props.bg),
            ]"
          >
            <h1 class="text-5xl font-bold text-gray-900">{{ block.props.title }}</h1>
            <p class="mt-4 text-xl text-gray-600">{{ block.props.subtitle }}</p>
            <a
              v-if="block.props.ctaText"
              :href="block.props.ctaUrl || '#'"
              class="mt-6 inline-block px-6 py-3 bg-black text-white rounded-md text-lg font-medium hover:bg-gray-800"
            >
              {{ block.props.ctaText }}
            </a>
          </section>

          <!-- HEADING -->
          <component
            v-else-if="block.type === 'heading'"
            :is="'h' + (block.props.level || 2)"
            :class="[
              textAlign(block.props.alignment),
              block.props.level === 1 ? 'text-4xl font-bold'
                : block.props.level === 2 ? 'text-3xl font-semibold'
                : 'text-2xl font-semibold',
              'text-gray-900',
            ]"
          >
            {{ block.props.text }}
          </component>

          <!-- TEXT -->
          <section
            v-else-if="block.type === 'text'"
            :class="[
              textAlign(block.props.alignment),
              block.props.size === 'sm' ? 'text-sm'
                : block.props.size === 'lg' ? 'text-xl'
                : 'text-base',
            ]"
          >
            <p class="text-gray-700 whitespace-pre-wrap">{{ block.props.content }}</p>
          </section>

          <!-- COLUMNS -->
          <section v-else-if="block.type === 'columns'">
            <div
              class="grid"
              :class="gapClassPublic(block.props.gap)"
              :style="{ gridTemplateColumns: `repeat(${block.props.columns || 2}, minmax(0, 1fr))` }"
            >
              <div v-for="(item, i) in (block.props.items || [])" :key="i">
                <h3 v-if="item.title" class="text-lg font-semibold text-gray-900">{{ item.title }}</h3>
                <p v-if="item.body" class="mt-2 text-gray-700 whitespace-pre-wrap">{{ item.body }}</p>
              </div>
            </div>
          </section>

          <!-- CHECKLIST -->
          <section v-else-if="block.type === 'checklist'">
            <h3 v-if="block.props.heading" class="text-2xl font-semibold text-gray-900 mb-4">{{ block.props.heading }}</h3>
            <ul
              class="grid gap-3"
              :style="{ gridTemplateColumns: `repeat(${block.props.columns || 1}, minmax(0, 1fr))` }"
            >
              <li v-for="(item, i) in (block.props.items || [])" :key="i" class="flex items-start gap-3">
                <span class="h-5 w-5 rounded-full bg-green-100 text-green-700 text-xs font-bold flex items-center justify-center shrink-0 mt-0.5">✓</span>
                <span class="text-gray-800">{{ item.text }}</span>
              </li>
            </ul>
          </section>

          <!-- IMAGE -->
          <section v-else-if="block.type === 'image'" class="flex justify-center">
            <a v-if="block.props.link" :href="block.props.link">
              <img :src="block.props.src" :alt="block.props.alt" class="max-w-full" :class="block.props.rounded ? 'rounded-lg' : ''" />
            </a>
            <img v-else :src="block.props.src" :alt="block.props.alt" class="max-w-full" :class="block.props.rounded ? 'rounded-lg' : ''" />
          </section>

          <!-- VIDEO -->
          <section v-else-if="block.type === 'video' && block.props.url">
            <div class="relative w-full overflow-hidden rounded-lg bg-black" :style="{ aspectRatio: block.props.aspect || '16/9' }">
              <iframe
                :src="toEmbedUrl(block.props.url)"
                class="w-full h-full"
                frameborder="0"
                allowfullscreen
              />
            </div>
          </section>

          <!-- CTA -->
          <section
            v-else-if="block.type === 'cta'"
            :class="textAlign(block.props.alignment)"
            class="py-6"
          >
            <a
              :href="block.props.url || '#'"
              :target="block.props.newTab ? '_blank' : undefined"
              :rel="block.props.newTab ? 'noopener noreferrer' : undefined"
              class="inline-block rounded-md font-medium transition-colors"
              :class="[
                block.props.variant === 'outline'
                  ? 'border-2 border-black text-black hover:bg-black hover:text-white'
                  : 'bg-black text-white hover:bg-gray-800',
                block.props.size === 'sm' ? 'px-4 py-2 text-sm'
                  : block.props.size === 'lg' ? 'px-8 py-4 text-lg'
                  : 'px-6 py-3 text-base',
              ]"
            >
              {{ block.props.text }}
            </a>
          </section>

          <!-- PRICING -->
          <section v-else-if="block.type === 'pricing'" class="py-6">
            <h2 v-if="block.props.heading" class="text-3xl font-bold text-center text-gray-900">{{ block.props.heading }}</h2>
            <p v-if="block.props.subtitle" class="mt-2 text-lg text-center text-gray-600">{{ block.props.subtitle }}</p>
            <div
              class="mt-8 grid gap-4"
              :style="{ gridTemplateColumns: `repeat(${Math.min((block.props.plans || []).length || 1, 3)}, minmax(0, 1fr))` }"
            >
              <div
                v-for="(plan, i) in (block.props.plans || [])"
                :key="i"
                class="rounded-lg p-6 flex flex-col"
                :class="plan.featured
                  ? 'bg-black text-white ring-2 ring-black'
                  : 'bg-white border border-gray-200 text-gray-900'"
              >
                <p class="text-sm font-semibold uppercase tracking-wider" :class="plan.featured ? 'text-gray-300' : 'text-gray-500'">{{ plan.name }}</p>
                <p class="mt-2">
                  <span class="text-4xl font-bold">{{ plan.price }}</span>
                  <span :class="plan.featured ? 'text-gray-300' : 'text-gray-500'">{{ plan.period }}</span>
                </p>
                <ul class="mt-4 space-y-2 flex-1">
                  <li v-for="(feat, fi) in (plan.features || [])" :key="fi" class="flex items-start gap-2 text-sm">
                    <span :class="plan.featured ? 'text-green-400' : 'text-green-600'">✓</span>
                    <span>{{ feat }}</span>
                  </li>
                </ul>
                <a
                  :href="plan.ctaUrl || '#'"
                  class="mt-6 block text-center px-4 py-2 rounded-md font-medium transition-colors"
                  :class="plan.featured
                    ? 'bg-white text-black hover:bg-gray-100'
                    : 'bg-black text-white hover:bg-gray-800'"
                >{{ plan.ctaText }}</a>
              </div>
            </div>
          </section>

          <!-- FEATURES -->
          <section v-else-if="block.type === 'features'" class="py-6">
            <h2 v-if="block.props.heading" class="text-3xl font-bold text-center text-gray-900 mb-8">{{ block.props.heading }}</h2>
            <div class="grid gap-6" :style="{ gridTemplateColumns: `repeat(${block.props.columns || 3}, minmax(0, 1fr))` }">
              <div v-for="(item, i) in (block.props.items || [])" :key="i" class="text-center">
                <div class="text-4xl">{{ item.icon }}</div>
                <h3 class="mt-3 text-lg font-semibold text-gray-900">{{ item.title }}</h3>
                <p class="mt-2 text-gray-600">{{ item.description }}</p>
              </div>
            </div>
          </section>

          <!-- TESTIMONIAL -->
          <section v-else-if="block.type === 'testimonial'" class="py-6">
            <blockquote class="rounded-lg bg-gray-50 p-8">
              <p class="text-xl italic text-gray-800">"{{ block.props.quote }}"</p>
              <footer class="mt-4 flex items-center gap-3">
                <img v-if="block.props.avatarUrl" :src="block.props.avatarUrl" class="h-12 w-12 rounded-full object-cover" />
                <div>
                  <p class="font-semibold text-gray-900">{{ block.props.authorName }}</p>
                  <p class="text-sm text-gray-600">{{ block.props.authorRole }}</p>
                </div>
              </footer>
            </blockquote>
          </section>

          <!-- STATS -->
          <section v-else-if="block.type === 'stats'" class="py-6">
            <div
              class="grid gap-6"
              :style="{ gridTemplateColumns: `repeat(${(block.props.items || []).length || 1}, minmax(0, 1fr))` }"
            >
              <div v-for="(item, i) in (block.props.items || [])" :key="i" class="text-center">
                <p class="text-4xl font-bold tabular-nums text-gray-900">{{ item.value }}</p>
                <p class="mt-1 text-sm text-gray-600">{{ item.label }}</p>
              </div>
            </div>
          </section>

          <!-- FAQ -->
          <section v-else-if="block.type === 'faq'" class="py-6">
            <h2 v-if="block.props.heading" class="text-3xl font-bold text-gray-900 mb-6">{{ block.props.heading }}</h2>
            <div class="space-y-3">
              <details v-for="(item, i) in (block.props.items || [])" :key="i" class="rounded-lg border border-gray-200 p-4">
                <summary class="font-semibold text-gray-900 cursor-pointer">{{ item.question }}</summary>
                <p class="mt-3 text-gray-700">{{ item.answer }}</p>
              </details>
            </div>
          </section>

          <!-- STEPS -->
          <section v-else-if="block.type === 'steps'" class="py-6">
            <h2 v-if="block.props.heading" class="text-3xl font-bold text-center text-gray-900 mb-8">{{ block.props.heading }}</h2>
            <ol class="space-y-6">
              <li v-for="(item, i) in ((block.props.items || []) as any[])" :key="i" class="flex items-start gap-4">
                <div class="h-10 w-10 rounded-full bg-black text-white text-lg font-bold flex items-center justify-center shrink-0">
                  {{ i + 1 }}
                </div>
                <div>
                  <h3 class="text-lg font-semibold text-gray-900">{{ item.title }}</h3>
                  <p class="mt-1 text-gray-700">{{ item.body }}</p>
                </div>
              </li>
            </ol>
          </section>

          <!-- LOGOS -->
          <section v-else-if="block.type === 'logos'" class="py-6">
            <p v-if="block.props.heading" class="text-sm uppercase tracking-widest text-gray-500 text-center mb-6">
              {{ block.props.heading }}
            </p>
            <div class="flex flex-wrap items-center justify-center gap-8">
              <div v-for="(logo, i) in (block.props.logos || [])" :key="i">
                <img v-if="logo.src" :src="logo.src" :alt="logo.alt" class="h-8 object-contain opacity-60 hover:opacity-100 transition-opacity" />
                <span v-else class="text-gray-400 font-medium">{{ logo.alt || 'Logo' }}</span>
              </div>
            </div>
          </section>

          <!-- DIVIDER -->
          <hr
            v-else-if="block.type === 'divider'"
            class="border-gray-200"
            :style="{ margin: dividerSpacing(block.props.size) }"
          />

          <!-- SPACER -->
          <div
            v-else-if="block.type === 'spacer'"
            :style="{ height: spacerHeightPublic(block.props.size) }"
          />

          <!-- EMBED — raw HTML (workspace owner's responsibility) -->
          <section v-else-if="block.type === 'embed'" class="w-full" v-html="block.props.html" />

          <!-- FORM -->
          <section v-else-if="block.type === 'form' && block.props.formId" class="py-6">
            <div v-if="submittedForms.has(block.props.formId)" class="text-center py-8 bg-green-50 rounded-lg">
              <p class="text-lg font-medium text-green-800">
                {{ getFormDef(block.props.formId)?.successMessage ?? 'Thanks!' }}
              </p>
            </div>
            <form
              v-else-if="getFormDef(block.props.formId)"
              @submit.prevent="handleSubmit(block.props.formId)"
              class="max-w-md mx-auto space-y-4 p-6 border rounded-lg bg-gray-50"
            >
              <div
                v-if="errorsFor(block.props.formId).has('_form')"
                class="rounded-md bg-red-50 border border-red-200 p-3 text-sm text-red-700"
              >
                {{ errorsFor(block.props.formId).get('_form') }}
              </div>
              <template
                v-for="field in getFieldsForForm(getFormDef(block.props.formId))"
                :key="field.key"
              >
                <div class="space-y-1">
                  <label :for="`${block.props.formId}-${field.key}`" class="block text-sm font-medium">
                    {{ field.label }}
                    <span v-if="field.required" class="text-red-500">*</span>
                  </label>
                  <textarea
                    v-if="field.type === 'textarea'"
                    :id="`${block.props.formId}-${field.key}`"
                    :required="field.required"
                    v-model="valuesFor(block.props.formId)[field.key]"
                    class="w-full rounded-md px-3 py-2 text-sm"
                    :class="errorsFor(block.props.formId).has(field.key)
                      ? 'border border-red-500'
                      : 'border border-gray-300'"
                    rows="4"
                  />
                  <input
                    v-else
                    :id="`${block.props.formId}-${field.key}`"
                    :type="field.type === 'email' ? 'email' : field.type === 'tel' ? 'tel' : 'text'"
                    :required="field.required"
                    v-model="valuesFor(block.props.formId)[field.key]"
                    class="w-full rounded-md px-3 py-2 text-sm"
                    :class="errorsFor(block.props.formId).has(field.key)
                      ? 'border border-red-500'
                      : 'border border-gray-300'"
                  />
                  <p
                    v-if="errorsFor(block.props.formId).has(field.key)"
                    class="text-xs text-red-600"
                  >
                    {{ errorsFor(block.props.formId).get(field.key) }}
                  </p>
                </div>
              </template>
              <button
                type="submit"
                class="w-full px-4 py-3 bg-black text-white rounded-md font-medium hover:bg-gray-800 disabled:opacity-50"
                :disabled="submittingFormId === block.props.formId"
              >
                {{ submittingFormId === block.props.formId
                  ? 'Submitting...'
                  : (block.props.submitText ?? 'Submit') }}
              </button>
            </form>
          </section>
        </template>
      </div>
    </div>
  </div>
</template>
