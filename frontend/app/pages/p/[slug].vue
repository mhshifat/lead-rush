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

    <div v-else-if="pageResponse?.data" class="min-h-screen bg-white">
      <div class="max-w-3xl mx-auto px-6 py-12 space-y-8">
        <template v-for="block in pageResponse.data.blocks" :key="block.id">
          <!-- HERO -->
          <section v-if="block.type === 'hero'" class="text-center py-12">
            <h1 class="text-5xl font-bold">{{ block.props.title }}</h1>
            <p class="mt-4 text-xl text-gray-600">{{ block.props.subtitle }}</p>
            <a
              v-if="block.props.ctaText"
              :href="block.props.ctaUrl || '#'"
              class="mt-6 inline-block px-6 py-3 bg-black text-white rounded-md text-lg font-medium hover:bg-gray-800"
            >
              {{ block.props.ctaText }}
            </a>
          </section>

          <!-- TEXT -->
          <section v-else-if="block.type === 'text'" class="prose">
            <p class="text-lg text-gray-700 whitespace-pre-wrap">{{ block.props.content }}</p>
          </section>

          <!-- IMAGE -->
          <section v-else-if="block.type === 'image'" class="flex justify-center">
            <img :src="block.props.src" :alt="block.props.alt" class="max-w-full rounded-lg" />
          </section>

          <!-- CTA -->
          <section v-else-if="block.type === 'cta'" class="text-center py-6">
            <a
              :href="block.props.url || '#'"
              class="inline-block px-6 py-3 bg-black text-white rounded-md text-lg font-medium hover:bg-gray-800"
            >
              {{ block.props.text }}
            </a>
          </section>

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
