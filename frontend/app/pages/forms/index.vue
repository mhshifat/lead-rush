<!--
  Forms list page — create forms that can be embedded on landing pages.
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'

definePageMeta({
  middleware: 'auth',
})

const { data: forms, isLoading } = useForms()
const { data: sequences } = useSequences()
const createMutation = useCreateForm()

const dialogOpen = ref(false)
const form = ref({
  name: '',
  description: '',
  successMessage: 'Thank you! We\'ll be in touch.',
  autoEnrollSequenceId: '',
})
const createErrors = useFieldErrors()

// Active sequences can be auto-enroll targets
const activeSequences = computed(() => (sequences.value ?? []).filter(s => s.status === 'ACTIVE'))

watch(() => form.value.name, v => { if (v.trim()) createErrors.remove('name') })
watch(dialogOpen, (open) => { if (open) createErrors.clear() })

async function handleCreate() {
  createErrors.clear()
  if (!form.value.name.trim()) createErrors.set('name', 'Name is required.')
  if (Object.keys(createErrors.map).length) return
  try {
    await createMutation.mutateAsync({
      name: form.value.name,
      description: form.value.description || undefined,
      successMessage: form.value.successMessage,
      autoEnrollSequenceId: form.value.autoEnrollSequenceId || undefined,
      // Start with sensible default fields — user can edit later
      fields: JSON.stringify([
        { key: 'firstName', label: 'First Name', type: 'text', required: true },
        { key: 'email', label: 'Email', type: 'email', required: true },
      ]),
    })
    toast.success('Form created')
    dialogOpen.value = false
    form.value = { name: '', description: '', successMessage: 'Thank you! We\'ll be in touch.', autoEnrollSequenceId: '' }
  } catch (error: any) {
    createErrors.fromServerError(error, 'Failed to create form')
  }
}

function fieldCount(fields: any): number {
  if (Array.isArray(fields)) return fields.length
  if (typeof fields === 'string') {
    try {
      const parsed = JSON.parse(fields)
      return Array.isArray(parsed) ? parsed.length : 0
    } catch { return 0 }
  }
  return 0
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold">Forms</h1>
        <p class="text-sm text-muted-foreground">Collect data from landing pages. Auto-creates contacts on submit.</p>
      </div>
      <Dialog v-model:open="dialogOpen">
        <DialogTrigger as-child>
          <Button>+ New Form</Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create Form</DialogTitle>
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
                placeholder="Newsletter Signup"
                :class="createErrors.has('name') ? 'border-destructive' : ''"
              />
              <SharedFormError :message="createErrors.get('name')" />
            </div>
            <div class="space-y-2">
              <Label for="description">Description</Label>
              <Input id="description" v-model="form.description" />
            </div>
            <div class="space-y-2">
              <Label for="successMessage">Success Message</Label>
              <Input id="successMessage" v-model="form.successMessage" />
            </div>
            <div class="space-y-2">
              <Label for="autoEnrollSequence">Auto-enroll in Sequence (optional)</Label>
              <select
                id="autoEnrollSequence"
                v-model="form.autoEnrollSequenceId"
                class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              >
                <option value="">Don't auto-enroll</option>
                <option v-for="s in activeSequences" :key="s.id" :value="s.id">{{ s.name }}</option>
              </select>
              <p v-if="!activeSequences.length" class="text-xs text-muted-foreground">
                No active sequences available. Activate a sequence to use this feature.
              </p>
            </div>
            <p class="text-xs text-muted-foreground">
              Default fields (First Name, Email) will be added. You can customize them later.
            </p>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="dialogOpen = false">Cancel</Button>
            <Button @click="handleCreate" :disabled="createMutation.isPending.value">Create</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">Loading...</div>

    <div v-else-if="!forms?.length" class="text-center py-12 text-muted-foreground">
      No forms yet. Create one to embed on landing pages.
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <Card v-for="f in forms" :key="f.id">
        <CardHeader>
          <CardTitle class="text-base">{{ f.name }}</CardTitle>
          <CardDescription v-if="f.description">{{ f.description }}</CardDescription>
        </CardHeader>
        <CardContent>
          <div class="text-sm text-muted-foreground">
            {{ fieldCount(f.fields) }} field{{ fieldCount(f.fields) === 1 ? '' : 's' }}
            <span v-if="f.autoEnrollSequenceId">· Auto-enrolls</span>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
