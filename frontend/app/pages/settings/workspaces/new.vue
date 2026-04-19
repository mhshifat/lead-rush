<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { useQueryClient } from '@tanstack/vue-query'
import { toast } from 'vue-sonner'
import { ArrowLeft, Building2, Sparkles, Lock, Database } from 'lucide-vue-next'

definePageMeta({ middleware: 'auth' })

const name = ref('')
const authStore = useAuthStore()
const queryClient = useQueryClient()
const createMutation = useCreateWorkspace()
const errors = useFieldErrors()

watch(name, v => { if (v.trim()) errors.remove('name') })

async function handleCreate() {
  errors.clear()
  if (!name.value.trim()) errors.set('name', 'Name is required.')
  if (Object.keys(errors.map).length) return
  try {
    const created = await createMutation.mutateAsync({ name: name.value.trim() })
    await authStore.switchWorkspace(created.id)
    queryClient.clear()
    toast.success(`Created ${created.name}`)
    await navigateTo('/dashboard')
  } catch (error: any) {
    errors.fromServerError(error, 'Failed to create workspace')
  }
}

// Selling points shown beneath the form so the page feels intentional, not blank.
const HIGHLIGHTS = [
  { icon: Lock,     label: 'Fully isolated',    desc: 'Contacts, sequences, and settings stay inside this workspace.' },
  { icon: Database, label: 'Independent data',  desc: 'No data crosses between workspaces — perfect for clients or projects.' },
  { icon: Sparkles, label: 'Switch any time',   desc: 'Use the sidebar switcher to jump between workspaces you belong to.' },
]
</script>

<template>
  <div class="max-w-2xl mx-auto enter-fade-up">
    <!-- Back link -->
    <NuxtLink
      to="/settings/team"
      class="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors mb-4"
    >
      <ArrowLeft class="h-3.5 w-3.5" />
      Back to team
    </NuxtLink>

    <!-- Form card -->
    <div class="glass hairline rounded-xl overflow-hidden">
      <div class="px-6 py-5" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
        <div class="flex items-start gap-3">
          <div class="h-10 w-10 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
            <Building2 class="h-5 w-5 text-primary" />
          </div>
          <div class="min-w-0">
            <h1 class="text-lg font-semibold tracking-tight">Create workspace</h1>
            <p class="text-sm text-muted-foreground mt-0.5">
              Workspaces are fully isolated — contacts, sequences, and settings don't cross between them.
            </p>
          </div>
        </div>
      </div>

      <div class="p-6 space-y-4">
        <div
          v-if="errors.has('_form')"
          class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
        >
          {{ errors.get('_form') }}
        </div>
        <div class="space-y-2">
          <Label for="name">Workspace name *</Label>
          <Input
            id="name"
            v-model="name"
            placeholder="Acme Inc."
            :class="errors.has('name') ? 'border-destructive' : ''"
            @keyup.enter="handleCreate"
          />
          <SharedFormError :message="errors.get('name')" />
          <p class="text-xs text-muted-foreground">
            You can rename it any time from <span class="text-foreground">Team settings</span>.
          </p>
        </div>
      </div>

      <div
        class="px-6 py-4 flex items-center justify-end gap-2"
        style="border-top: 1px solid hsl(240 5% 100% / 0.06);"
      >
        <Button variant="outline" @click="navigateTo('/settings/team')">Cancel</Button>
        <Button class="gap-1.5" :disabled="createMutation.isPending.value" @click="handleCreate">
          {{ createMutation.isPending.value ? 'Creating…' : 'Create workspace' }}
        </Button>
      </div>
    </div>

    <!-- Highlights row -->
    <div class="mt-5 grid grid-cols-1 sm:grid-cols-3 gap-3">
      <div
        v-for="h in HIGHLIGHTS"
        :key="h.label"
        class="glass hairline rounded-xl p-4"
      >
        <div class="h-8 w-8 rounded-lg bg-primary/10 flex items-center justify-center mb-3">
          <component :is="h.icon" class="h-4 w-4 text-primary" />
        </div>
        <p class="text-sm font-semibold tracking-tight">{{ h.label }}</p>
        <p class="text-xs text-muted-foreground mt-1 leading-relaxed">{{ h.desc }}</p>
      </div>
    </div>
  </div>
</template>
