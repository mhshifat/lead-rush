<!--
  Email Template Editor Page
  - List all templates
  - Create/edit templates with variable helpers
  - Supports {{firstName}}, {{lastName}}, {{fullName}}, {{companyName}}, {{title}}, {{email}}
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'

definePageMeta({
  middleware: 'auth',
})

const { data: templates, isLoading } = useEmailTemplates()
const createMutation = useCreateEmailTemplate()
const updateMutation = useUpdateEmailTemplate()
const deleteMutation = useDeleteEmailTemplate()

// AI
const { data: aiStatus } = useAIStatus()
const generateEmailMutation = useGenerateEmail()
const suggestSubjectsMutation = useSuggestSubjects()
const aiReady = computed(() => aiStatus.value?.ready ?? false)

// For the "Generate email" dialog — pull up to 50 contacts to pick from
const contactFilters = ref({ page: 0, size: 50, sort: 'createdAt,desc' })
const { data: contactsPage } = useContacts(contactFilters)
const aiDialogOpen = ref(false)
const aiContactId = ref('')
const aiValueProp = ref('')
const aiTone = ref('')
const aiLength = ref<'SHORT' | 'MEDIUM' | 'LONG'>('MEDIUM')

// For subject-line suggestions
const subjectSuggestions = ref<string[]>([])

async function handleGenerateEmail() {
  if (!aiContactId.value) { toast.error('Pick a contact to personalize for'); return }
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
    toast.error(error?.data?.error?.message ?? 'Failed to generate email')
  }
}

async function handleSuggestSubjects() {
  if (!form.value.subject.trim()) { toast.error('Enter a subject first, then ask for variants'); return }
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

// ── Editor state ──
const dialogOpen = ref(false)
const editingId = ref<string | null>(null)

const form = ref({
  name: '',
  subject: '',
  bodyHtml: '',
  bodyText: '',
})

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
  dialogOpen.value = true
}

function insertVariable(variable: string) {
  form.value.bodyHtml += variable
}

async function handleSave() {
  if (!form.value.name.trim() || !form.value.subject.trim()) {
    toast.error('Name and subject are required')
    return
  }

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
    toast.error(error?.data?.error?.message || 'Failed to save template')
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
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold">Email Templates</h1>
        <p class="text-sm text-muted-foreground">Reusable templates for sequences and manual sends</p>
      </div>
      <Dialog v-model:open="dialogOpen">
        <DialogTrigger as-child>
          <Button @click="openCreateDialog">+ New Template</Button>
        </DialogTrigger>
        <DialogContent class="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{{ editingId ? 'Edit Template' : 'New Template' }}</DialogTitle>
          </DialogHeader>

          <div class="space-y-4">
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
              <Input id="name" v-model="form.name" placeholder="Intro Email" />
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
              <Input id="subject" v-model="form.subject" placeholder="Hi {{firstName}}, quick question" />
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
                class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm font-mono"
                placeholder="<p>Hi {{firstName}},</p><p>I noticed you work at {{companyName}}...</p>"
              />
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
          <div class="space-y-2">
            <Label for="aiContact">Personalize for *</Label>
            <select
              id="aiContact"
              v-model="aiContactId"
              class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm"
            >
              <option value="">Pick a contact…</option>
              <option
                v-for="c in contactsPage?.items ?? []"
                :key="c.id"
                :value="c.id"
              >
                {{ c.fullName }}{{ c.title ? ' · ' + c.title : '' }}{{ c.companyName ? ' · ' + c.companyName : '' }}
              </option>
            </select>
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

    <!-- Template list -->
    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">
      Loading templates...
    </div>

    <div v-else-if="!templates?.length" class="text-center py-12">
      <p class="text-muted-foreground">No templates yet. Create one to start building sequences.</p>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <Card v-for="tpl in templates" :key="tpl.id">
        <CardHeader>
          <CardTitle class="text-base">{{ tpl.name }}</CardTitle>
        </CardHeader>
        <CardContent class="space-y-3">
          <p class="text-sm font-medium truncate">{{ tpl.subject }}</p>
          <p class="text-xs text-muted-foreground line-clamp-3">
            {{ tpl.bodyText || tpl.bodyHtml?.replace(/<[^>]*>/g, '') || '(empty)' }}
          </p>
          <div class="flex gap-2 pt-2">
            <Button size="sm" variant="outline" class="flex-1" @click="openEditDialog(tpl)">Edit</Button>
            <Button size="sm" variant="outline" class="flex-1 text-destructive" @click="handleDelete(tpl.id, tpl.name)">
              Delete
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
