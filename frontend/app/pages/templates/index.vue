<!--
  Email Template Editor Page
  - List all templates
  - Create/edit templates with variable helpers
  - Supports {{firstName}}, {{lastName}}, {{fullName}}, {{companyName}}, {{title}}, {{email}}
-->
<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger, DialogDescription } from '~/components/ui/dialog'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '~/components/ui/select'
import { toast } from 'vue-sonner'
import { Plus, Mail, Pencil, Trash2 } from 'lucide-vue-next'

definePageMeta({
  middleware: 'auth',
})

const { data: templates, isLoading } = useEmailTemplates()
const createMutation = useCreateEmailTemplate()
const updateMutation = useUpdateEmailTemplate()
const deleteMutation = useDeleteEmailTemplate()

// AI mutations
const { data: aiStatus } = useAIStatus()
const generateEmailMutation = useGenerateEmail()
const suggestSubjectsMutation = useSuggestSubjects()
const aiReady = computed(() => aiStatus.value?.ready ?? false)

// Main editor state (declared BEFORE any handler that touches it so TS / ESLint
// don't complain about block-scoped `form` / `errors` being used before init).
const dialogOpen = ref(false)
const editingId = ref<string | null>(null)

const form = ref({
  name: '',
  subject: '',
  bodyHtml: '',
  bodyText: '',
})
const errors = useFieldErrors()

watch(() => form.value.name, v => { if (v.trim()) errors.remove('name') })
watch(() => form.value.subject, v => { if (v.trim()) errors.remove('subject') })

// AI-generate dialog state
const contactFilters = ref({ page: 0, size: 50, sort: 'createdAt,desc' })
const { data: contactsPage } = useContacts(contactFilters)
const aiDialogOpen = ref(false)
const aiContactId = ref('')
const aiValueProp = ref('')
const aiTone = ref('')
const aiLength = ref<'SHORT' | 'MEDIUM' | 'LONG'>('MEDIUM')

// Subject-line suggestions rendered under the Subject input
const subjectSuggestions = ref<string[]>([])

// Separate error bag for the AI-generate dialog so validation there stays inline
// and doesn't clobber errors on the main save form behind it.
const aiErrors = useFieldErrors()
watch(aiContactId, v => { if (v) aiErrors.remove('aiContactId') })
watch(aiDialogOpen, (open) => { if (open) aiErrors.clear() })

async function handleGenerateEmail() {
  aiErrors.clear()
  if (!aiContactId.value) aiErrors.set('aiContactId', 'Pick a contact to personalize for.')
  if (Object.keys(aiErrors.map).length) return
  try {
    const result = await generateEmailMutation.mutateAsync({
      contactId: aiContactId.value,
      valueProp: aiValueProp.value || undefined,
      tone: aiTone.value || undefined,
      length: aiLength.value,
    })
    if (result.subject) form.value.subject = result.subject
    if (result.bodyHtml) form.value.bodyHtml = result.bodyHtml
    if (result.bodyText) form.value.bodyText = result.bodyText
    aiDialogOpen.value = false
    toast.success('Email drafted — review and tweak before saving')
  } catch (error: any) {
    aiErrors.fromServerError(error, 'Failed to generate email')
  }
}

async function handleSuggestSubjects() {
  // Suggest-subjects is a button action on the main form, so reuse the main
  // `errors` instance and surface the issue next to the Subject input.
  if (!form.value.subject.trim()) {
    errors.set('subject', 'Enter a subject first, then ask for variants.')
    return
  }
  try {
    subjectSuggestions.value = await suggestSubjectsMutation.mutateAsync({
      subject: form.value.subject,
      bodyPreview: form.value.bodyText || form.value.bodyHtml?.replace(/<[^>]*>/g, '').slice(0, 500),
      count: 5,
    })
  } catch (error: any) {
    toast.error(error?.data?.error?.message ?? 'Failed to suggest variants')
  }
}

function applySubject(s: string) {
  form.value.subject = s
  subjectSuggestions.value = []
}

const AVAILABLE_VARIABLES = [
  { key: '{{firstName}}', label: 'First Name' },
  { key: '{{lastName}}', label: 'Last Name' },
  { key: '{{fullName}}', label: 'Full Name' },
  { key: '{{companyName}}', label: 'Company' },
  { key: '{{title}}', label: 'Title' },
  { key: '{{email}}', label: 'Email' },
]

function openCreateDialog() {
  editingId.value = null
  form.value = { name: '', subject: '', bodyHtml: '', bodyText: '' }
  errors.clear()
  dialogOpen.value = true
}

function openEditDialog(template: any) {
  editingId.value = template.id
  form.value = {
    name: template.name,
    subject: template.subject,
    bodyHtml: template.bodyHtml ?? '',
    bodyText: template.bodyText ?? '',
  }
  errors.clear()
  dialogOpen.value = true
}

function insertVariable(variable: string) {
  form.value.bodyHtml += variable
}

async function handleSave() {
  errors.clear()
  if (!form.value.name.trim()) errors.set('name', 'Name is required.')
  if (!form.value.subject.trim()) errors.set('subject', 'Subject is required.')
  if (!form.value.bodyHtml.trim() && !form.value.bodyText.trim()) {
    errors.set('bodyHtml', 'Add either an HTML body or a plain-text fallback.')
  }
  if (Object.keys(errors.map).length) return

  try {
    if (editingId.value) {
      await updateMutation.mutateAsync({ id: editingId.value, dto: form.value })
      toast.success('Template updated')
    } else {
      await createMutation.mutateAsync(form.value)
      toast.success('Template created')
    }
    dialogOpen.value = false
  } catch (error: any) {
    errors.fromServerError(error, 'Failed to save template')
  }
}

async function handleDelete(id: string, name: string) {
  const ok = await useConfirm().ask({
    title: `Delete template "${name}"?`,
    confirmLabel: 'Delete',
    variant: 'destructive',
  })
  if (!ok) return
  try {
    await deleteMutation.mutateAsync(id)
    toast.success('Template deleted')
  } catch {
    toast.error('Failed to delete template')
  }
}
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Email templates</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          Reusable templates with variables like
          <code class="bg-muted px-1 rounded text-xs">{{ '{' + '{firstName}' + '}' }}</code>
          for sequences and manual sends.
        </p>
      </div>
      <Dialog v-model:open="dialogOpen">
        <DialogTrigger as-child>
          <Button class="gap-1.5" @click="openCreateDialog">
            <Plus class="h-4 w-4" />
            New template
          </Button>
        </DialogTrigger>
        <DialogContent class="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{{ editingId ? 'Edit template' : 'New template' }}</DialogTitle>
            <DialogDescription>
              Write once, reuse across every sequence + manual send.
            </DialogDescription>
          </DialogHeader>

          <div class="space-y-4">
            <!-- Top-of-form banner for server-side errors. -->
            <div
              v-if="errors.has('_form')"
              class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
            >
              {{ errors.get('_form') }}
            </div>

            <div class="flex items-center justify-between gap-2">
              <p class="text-xs text-muted-foreground">
                Use <code class="bg-muted px-1">{{ '{' + '{firstName}' + '}' }}</code> etc. to personalize.
              </p>
              <Button
                v-if="aiReady"
                size="sm"
                variant="outline"
                type="button"
                @click="aiDialogOpen = true"
              >
                ✨ Generate with AI
              </Button>
            </div>

            <div class="space-y-2">
              <Label for="name">Name *</Label>
              <Input
                id="name"
                v-model="form.name"
                placeholder="Intro Email"
                :class="errors.has('name') ? 'border-destructive' : ''"
              />
              <SharedFormError :message="errors.get('name')" />
            </div>

            <div class="space-y-2">
              <div class="flex items-center justify-between">
                <Label for="subject">Subject *</Label>
                <Button
                  v-if="aiReady"
                  size="sm"
                  variant="ghost"
                  type="button"
                  class="h-7 text-xs"
                  :disabled="suggestSubjectsMutation.isPending.value"
                  @click="handleSuggestSubjects"
                >
                  ✨ Suggest variants
                </Button>
              </div>
              <Input
                id="subject"
                v-model="form.subject"
                placeholder="Hi {{firstName}}, quick question"
                :class="errors.has('subject') ? 'border-destructive' : ''"
              />
              <SharedFormError :message="errors.get('subject')" />
              <div v-if="subjectSuggestions.length" class="space-y-1 rounded-md border p-2">
                <p class="text-xs text-muted-foreground px-1">Click one to use it:</p>
                <button
                  v-for="(s, idx) in subjectSuggestions"
                  :key="idx"
                  type="button"
                  class="w-full text-left px-2 py-1 text-sm rounded hover:bg-muted"
                  @click="applySubject(s)"
                >{{ s }}</button>
              </div>
            </div>

            <div class="space-y-2">
              <Label>Variables (click to insert)</Label>
              <div class="flex flex-wrap gap-2">
                <Button
                  v-for="v in AVAILABLE_VARIABLES"
                  :key="v.key"
                  size="sm"
                  variant="outline"
                  type="button"
                  @click="insertVariable(v.key)"
                >
                  {{ v.key }}
                </Button>
              </div>
            </div>

            <div class="space-y-2">
              <Label for="bodyHtml">Body (HTML) *</Label>
              <textarea
                id="bodyHtml"
                v-model="form.bodyHtml"
                rows="10"
                class="w-full rounded-md border bg-background px-3 py-2 text-sm font-mono"
                :class="errors.has('bodyHtml') ? 'border-destructive' : 'border-input'"
                placeholder="<p>Hi {{firstName}},</p><p>I noticed you work at {{companyName}}...</p>"
              />
              <SharedFormError :message="errors.get('bodyHtml')" />
            </div>

            <div class="space-y-2">
              <Label for="bodyText">Plain Text Fallback</Label>
              <textarea
                id="bodyText"
                v-model="form.bodyText"
                rows="4"
                class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                placeholder="Hi {{firstName}}, I noticed you work at {{companyName}}..."
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" @click="dialogOpen = false">Cancel</Button>
            <Button @click="handleSave" :disabled="createMutation.isPending.value || updateMutation.isPending.value">
              {{ editingId ? 'Update' : 'Create' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <!-- AI generator dialog -->
    <Dialog v-model:open="aiDialogOpen">
      <DialogContent class="max-w-lg">
        <DialogHeader>
          <DialogTitle>Generate an email with AI</DialogTitle>
        </DialogHeader>
        <div class="space-y-4">
          <div
            v-if="aiErrors.has('_form')"
            class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
          >
            {{ aiErrors.get('_form') }}
          </div>
          <div class="space-y-2">
            <Label for="aiContact">Personalize for *</Label>
            <Select v-model="aiContactId">
              <SelectTrigger
                id="aiContact"
                class="w-full"
                :class="aiErrors.has('aiContactId') ? 'border-destructive' : ''"
              >
                <SelectValue placeholder="Pick a contact…" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="c in contactsPage?.items ?? []"
                  :key="c.id"
                  :value="c.id"
                >
                  {{ c.fullName }}{{ c.title ? ' · ' + c.title : '' }}{{ c.companyName ? ' · ' + c.companyName : '' }}
                </SelectItem>
              </SelectContent>
            </Select>
            <SharedFormError :message="aiErrors.get('aiContactId')" />
            <p class="text-xs text-muted-foreground">
              The AI uses this contact's first name, title, and company as context — then replaces them with
              <code class="bg-muted px-1">{{ '{' + '{firstName}' + '}' }}</code> variables in the output so the template stays reusable.
            </p>
          </div>

          <div class="space-y-2">
            <Label for="aiValueProp">Your value proposition</Label>
            <textarea
              id="aiValueProp"
              v-model="aiValueProp"
              rows="3"
              class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              placeholder="We help mid-market SaaS companies cut churn by 20% by automating lifecycle emails."
            />
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-2">
              <Label for="aiTone">Tone</Label>
              <Input id="aiTone" v-model="aiTone" placeholder="e.g., friendly, direct" />
            </div>
            <div class="space-y-2">
              <Label for="aiLength">Length</Label>
              <select
                id="aiLength"
                v-model="aiLength"
                class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm"
              >
                <option value="SHORT">Short (~80 words)</option>
                <option value="MEDIUM">Medium (~140 words)</option>
                <option value="LONG">Long (~220 words)</option>
              </select>
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="aiDialogOpen = false">Cancel</Button>
          <Button
            :disabled="generateEmailMutation.isPending.value || !aiContactId"
            @click="handleGenerateEmail"
          >
            {{ generateEmailMutation.isPending.value ? 'Drafting…' : 'Generate' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Loading -->
    <div
      v-if="isLoading"
      class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground"
    >
      Loading templates…
    </div>

    <!-- Empty state -->
    <div
      v-else-if="!templates?.length"
      class="glass hairline rounded-xl py-14 px-6 text-center"
    >
      <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-4">
        <Mail class="h-5 w-5 text-muted-foreground" />
      </div>
      <h3 class="text-sm font-semibold tracking-tight">No templates yet</h3>
      <p class="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
        Templates are reusable email bodies with variables like
        <code class="bg-muted px-1 rounded text-xs">{{ '{' + '{firstName}' + '}' }}</code>
        that sequences use to personalise every send.
      </p>
      <Button class="mt-5 gap-1.5" @click="openCreateDialog">
        <Plus class="h-4 w-4" />
        New template
      </Button>
    </div>

    <!-- Grid of templates -->
    <div
      v-else
      class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
    >
      <div
        v-for="tpl in templates"
        :key="tpl.id"
        class="glass hairline rounded-xl p-5 flex flex-col transition-colors hover:bg-white/2"
      >
        <div class="flex items-start gap-3 min-w-0">
          <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
            <Mail class="h-4 w-4 text-primary" />
          </div>
          <div class="min-w-0 flex-1">
            <h3 class="font-semibold tracking-tight truncate">{{ tpl.name }}</h3>
            <p class="text-xs text-muted-foreground mt-0.5 truncate" :title="tpl.subject">
              {{ tpl.subject }}
            </p>
          </div>
        </div>

        <!-- Body preview — 3-line clamp on the plain-text version -->
        <p
          class="mt-4 text-xs text-muted-foreground line-clamp-3 flex-1"
          style="min-height: 3.6em;"
        >
          {{ tpl.bodyText || tpl.bodyHtml?.replace(/<[^>]*>/g, '') || '(empty)' }}
        </p>

        <!-- Actions — icon + label, tight row -->
        <div
          class="mt-4 flex gap-1"
          style="border-top: 1px solid hsl(240 5% 100% / 0.05); padding-top: 12px;"
        >
          <Button
            size="sm"
            variant="ghost"
            class="flex-1 h-8 gap-1.5"
            @click="openEditDialog(tpl)"
          >
            <Pencil class="h-3.5 w-3.5" />
            Edit
          </Button>
          <Button
            size="sm"
            variant="ghost"
            class="flex-1 h-8 gap-1.5 text-destructive hover:text-destructive hover:bg-destructive/10"
            @click="handleDelete(tpl.id, tpl.name)"
          >
            <Trash2 class="h-3.5 w-3.5" />
            Delete
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>
