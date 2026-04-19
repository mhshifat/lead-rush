<!--
  Create a new workspace. On success, switch into it immediately.
-->
<script setup lang="ts">
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { useQueryClient } from '@tanstack/vue-query'
import { toast } from 'vue-sonner'

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
</script>

<template>
  <div class="max-w-lg mx-auto">
    <NuxtLink to="/settings/team" class="text-sm text-muted-foreground hover:text-foreground">
      ← Back
    </NuxtLink>

    <Card class="mt-4">
      <CardHeader>
        <CardTitle>Create workspace</CardTitle>
        <CardDescription>
          Workspaces are fully isolated — contacts, sequences, and settings don't cross between them.
        </CardDescription>
      </CardHeader>
      <CardContent class="space-y-4">
        <div
          v-if="errors.has('_form')"
          class="rounded-md bg-destructive/10 p-4 text-sm text-destructive"
        >
          {{ errors.get('_form') }}
        </div>
        <div class="space-y-2">
          <Label for="name">Name *</Label>
          <Input
            id="name"
            v-model="name"
            placeholder="Acme Inc."
            :class="errors.has('name') ? 'border-destructive' : ''"
          />
          <SharedFormError :message="errors.get('name')" />
        </div>
        <div class="flex justify-end gap-2">
          <Button variant="outline" @click="navigateTo('/settings/team')">Cancel</Button>
          <Button :disabled="createMutation.isPending.value" @click="handleCreate">Create</Button>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
