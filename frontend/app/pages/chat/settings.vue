<!--
  Chat widget settings — tweak colors, greeting, position, email requirement,
  and grab the embed snippet.
-->
<script setup lang="ts">
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { toast } from 'vue-sonner'

definePageMeta({ middleware: 'auth' })

const config = useRuntimeConfig()
const { data: widget } = useChatWidgetConfig()
const updateMutation = useUpdateChatWidget()

// Local form state — kept separate from the query so edits don't flicker on fetches
const form = reactive({
  enabled: true,
  displayName: '',
  greeting: '',
  offlineMessage: '',
  primaryColor: '#5E6AD2',
  position: 'BOTTOM_RIGHT' as 'BOTTOM_RIGHT' | 'BOTTOM_LEFT',
  requireEmail: true,
})

watch(widget, (w) => {
  if (!w) return
  form.enabled = w.enabled
  form.displayName = w.displayName
  form.greeting = w.greeting
  form.offlineMessage = w.offlineMessage
  form.primaryColor = w.primaryColor
  form.position = (w.position as typeof form.position) ?? 'BOTTOM_RIGHT'
  form.requireEmail = w.requireEmail
}, { immediate: true })

async function handleSave() {
  try {
    await updateMutation.mutateAsync({ ...form })
    toast.success('Widget updated')
  } catch (error: any) {
    toast.error(error?.data?.error?.message ?? 'Failed to save')
  }
}

// Snippet that customers paste onto their site. `data-lr` carries workspace slug + API URL.
const apiBase = computed(() => {
  const url = config.public.apiBaseUrl as string
  return url.replace(/\/api\/v\d+\/?$/, '')
})

const embedSnippet = computed(() => {
  if (!widget.value) return ''
  return `<script async src="${apiBase.value}/widget.js"
  data-workspace="${widget.value.workspaceSlug}"
  data-api="${apiBase.value}"></${'script'}>`
})

async function copySnippet() {
  await navigator.clipboard.writeText(embedSnippet.value)
  toast.success('Snippet copied')
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-start justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Chat widget</h1>
        <p class="text-sm text-muted-foreground">
          Configure how the widget looks and behaves on your site.
        </p>
      </div>
      <NuxtLink to="/chat" class="text-sm text-primary hover:underline">← Back to chat</NuxtLink>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-5 gap-6">
      <div class="lg:col-span-3 space-y-4">
        <Card>
          <CardHeader>
            <CardTitle class="text-base">Configuration</CardTitle>
          </CardHeader>
          <CardContent class="space-y-5">
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" v-model="form.enabled" class="h-4 w-4" />
              <span>Widget enabled</span>
            </label>

            <div class="space-y-2">
              <Label for="displayName">Display name</Label>
              <Input id="displayName" v-model="form.displayName" placeholder="Support" />
            </div>

            <div class="space-y-2">
              <Label for="greeting">Greeting</Label>
              <textarea
                id="greeting"
                v-model="form.greeting"
                rows="2"
                class="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm"
              />
            </div>

            <div class="space-y-2">
              <Label for="offline">Offline message</Label>
              <textarea
                id="offline"
                v-model="form.offlineMessage"
                rows="2"
                class="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm"
              />
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-2">
                <Label for="color">Primary color</Label>
                <div class="flex gap-2 items-center">
                  <input id="color" type="color" v-model="form.primaryColor" class="h-9 w-14 rounded border border-input bg-transparent" />
                  <Input v-model="form.primaryColor" class="flex-1" />
                </div>
              </div>
              <div class="space-y-2">
                <Label for="pos">Position</Label>
                <select
                  id="pos"
                  v-model="form.position"
                  class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm"
                >
                  <option value="BOTTOM_RIGHT">Bottom right</option>
                  <option value="BOTTOM_LEFT">Bottom left</option>
                </select>
              </div>
            </div>

            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" v-model="form.requireEmail" class="h-4 w-4" />
              <span>Require visitor email before first message</span>
            </label>

            <div class="flex justify-end">
              <Button :disabled="updateMutation.isPending.value" @click="handleSave">Save changes</Button>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle class="text-base">Install</CardTitle>
            <CardDescription>Paste this snippet just before <code>&lt;/body&gt;</code> on every page you want the widget on.</CardDescription>
          </CardHeader>
          <CardContent class="space-y-3">
            <pre class="rounded-md hairline bg-white/5 p-3 text-xs font-mono overflow-x-auto whitespace-pre">{{ embedSnippet }}</pre>
            <div class="flex justify-end">
              <Button variant="outline" @click="copySnippet">Copy snippet</Button>
            </div>
          </CardContent>
        </Card>
      </div>

      <!-- Preview -->
      <div class="lg:col-span-2">
        <Card class="sticky top-4">
          <CardHeader>
            <CardTitle class="text-base">Preview</CardTitle>
          </CardHeader>
          <CardContent>
            <div class="relative h-96 rounded-md border border-dashed border-white/10 overflow-hidden">
              <div
                class="absolute flex flex-col rounded-xl shadow-2xl overflow-hidden"
                :style="{
                  bottom: '12px',
                  [form.position === 'BOTTOM_LEFT' ? 'left' : 'right']: '12px',
                  width: '240px',
                  background: 'hsl(240 6% 9%)',
                  border: '1px solid hsl(240 5% 100% / 0.08)',
                }"
              >
                <div :style="{ background: form.primaryColor, padding: '12px' }">
                  <p class="text-white text-sm font-semibold">{{ form.displayName || 'Support' }}</p>
                  <p class="text-white/80 text-xs">{{ form.greeting || 'Hi there! How can we help?' }}</p>
                </div>
                <div class="p-3 flex-1 space-y-2 text-xs text-muted-foreground">
                  <div class="hairline rounded-md p-2 bg-white/5 max-w-[85%]">
                    {{ form.greeting || 'Hi there! How can we help?' }}
                  </div>
                </div>
                <div class="p-2 text-center text-[10px] text-muted-foreground" style="border-top: 1px solid hsl(240 5% 100% / 0.06);">
                  Powered by Lead Rush
                </div>
              </div>
              <div
                class="absolute flex items-center justify-center rounded-full shadow-lg"
                :style="{
                  bottom: '12px',
                  [form.position === 'BOTTOM_LEFT' ? 'left' : 'right']: '12px',
                  width: '44px', height: '44px',
                  background: form.primaryColor,
                  transform: 'translateY(280px)',
                }"
              >
                <svg class="w-5 h-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  </div>
</template>
