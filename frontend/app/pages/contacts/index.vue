<script setup lang="ts">
import { useDebounceFn } from '@vueuse/core'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import { Search, Plus, Users, ChevronLeft, ChevronRight, AlertCircle } from 'lucide-vue-next'
import type { ContactFilters } from '~/composables/useContacts'

definePageMeta({
  middleware: 'auth',
})

const searchInput = ref('')
const filters = ref<ContactFilters>({
  page: 0,
  size: 20,
  sort: 'createdAt,desc',
})

// 300ms debounce so the API isn't hit on every keystroke.
const updateSearch = useDebounceFn((value: string) => {
  filters.value = { ...filters.value, search: value || undefined, page: 0 }
}, 300)

watch(searchInput, (newValue) => updateSearch(newValue))

function setLifecycleStage(stage: string | undefined) {
  filters.value = { ...filters.value, lifecycleStage: stage, page: 0 }
}

function changePage(delta: number) {
  filters.value = { ...filters.value, page: (filters.value.page ?? 0) + delta }
}

const { data: contactsPage, isLoading, isError } = useContacts(filters)

const createDialogOpen = ref(false)
const newContact = ref({
  firstName: '',
  lastName: '',
  email: '',
  companyName: '',
  title: '',
})

const createErrors = useFieldErrors()

// Touch-to-clear: remove the error the moment the user starts fixing the field.
watch(() => newContact.value.firstName, v => { if (v.trim()) createErrors.remove('firstName') })

// Reset errors every time the dialog opens (a stale error from a previous attempt
// would otherwise linger until the next submit).
watch(createDialogOpen, (open) => { if (open) createErrors.clear() })

const createMutation = useCreateContact()

async function handleCreate() {
  createErrors.clear()
  if (!newContact.value.firstName.trim()) createErrors.set('firstName', 'First name is required.')
  if (Object.keys(createErrors.map).length) return

  try {
    await createMutation.mutateAsync({
      firstName: newContact.value.firstName,
      lastName: newContact.value.lastName || undefined,
      title: newContact.value.title || undefined,
      companyName: newContact.value.companyName || undefined,
      emails: newContact.value.email
        ? [{ email: newContact.value.email, emailType: 'WORK', primary: true }]
        : undefined,
    })
    toast.success('Contact created')
    createDialogOpen.value = false
    // Reset form
    newContact.value = { firstName: '', lastName: '', email: '', companyName: '', title: '' }
  } catch (error: any) {
    createErrors.fromServerError(error, 'Failed to create contact')
  }
}

const stageFilters: Array<{ value: string | undefined; label: string }> = [
  { value: undefined, label: 'All' },
  { value: 'LEAD', label: 'Lead' },
  { value: 'QUALIFIED', label: 'Qualified' },
  { value: 'CUSTOMER', label: 'Customer' },
]

function stageBadgeVariant(stage: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (stage) {
    case 'LEAD': return 'secondary'
    case 'CONTACTED': return 'outline'
    case 'QUALIFIED': return 'default'
    case 'OPPORTUNITY': return 'default'
    case 'CUSTOMER': return 'default'
    case 'LOST': return 'destructive'
    default: return 'secondary'
  }
}
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Page header -->
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Contacts</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          {{ contactsPage?.totalElements ?? 0 }} {{ (contactsPage?.totalElements ?? 0) === 1 ? 'contact' : 'contacts' }}
        </p>
      </div>
      <Dialog v-model:open="createDialogOpen">
        <DialogTrigger as-child>
          <Button class="gap-1.5">
            <Plus class="h-4 w-4" />
            New contact
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create contact</DialogTitle>
          </DialogHeader>
          <div class="space-y-4">
            <div
              v-if="createErrors.has('_form')"
              class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
            >
              {{ createErrors.get('_form') }}
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-2">
                <Label for="firstName">First name *</Label>
                <Input
                  id="firstName"
                  v-model="newContact.firstName"
                  required
                  :class="createErrors.has('firstName') ? 'border-destructive' : ''"
                />
                <SharedFormError :message="createErrors.get('firstName')" />
              </div>
              <div class="space-y-2">
                <Label for="lastName">Last name</Label>
                <Input id="lastName" v-model="newContact.lastName" />
              </div>
            </div>
            <div class="space-y-2">
              <Label for="email">Email</Label>
              <Input id="email" v-model="newContact.email" type="email" />
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-2">
                <Label for="companyName">Company</Label>
                <Input id="companyName" v-model="newContact.companyName" />
              </div>
              <div class="space-y-2">
                <Label for="title">Title</Label>
                <Input id="title" v-model="newContact.title" />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="createDialogOpen = false">Cancel</Button>
            <Button @click="handleCreate" :disabled="createMutation.isPending.value">
              {{ createMutation.isPending.value ? 'Creating…' : 'Create' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <!-- Toolbar -->
    <div class="glass hairline rounded-xl px-3 py-2.5 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
      <div class="relative md:max-w-sm md:flex-1">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
        <Input
          v-model="searchInput"
          placeholder="Search contacts…"
          class="pl-9 h-9 border-0 bg-transparent focus-visible:ring-0 focus-visible:ring-offset-0"
        />
      </div>
      <div class="flex items-center gap-1 overflow-x-auto">
        <button
          v-for="stage in stageFilters"
          :key="stage.value ?? 'all'"
          class="px-3 py-1.5 rounded-full text-xs font-medium tracking-tight transition-colors whitespace-nowrap"
          :class="filters.lifecycleStage === stage.value
            ? 'bg-primary text-primary-foreground'
            : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
          @click="setLifecycleStage(stage.value)"
        >
          {{ stage.label }}
        </button>
      </div>
    </div>

    <!-- Contacts list -->
    <div class="glass hairline rounded-xl overflow-hidden">
      <!-- Loading -->
      <div v-if="isLoading" class="flex items-center justify-center py-16 text-sm text-muted-foreground">
        Loading…
      </div>

      <!-- Error -->
      <div v-else-if="isError" class="flex flex-col items-center justify-center py-16 gap-2">
        <AlertCircle class="h-8 w-8 text-destructive/70" />
        <p class="text-sm text-destructive">Failed to load contacts</p>
      </div>

      <!-- Empty -->
      <div v-else-if="!contactsPage?.items.length" class="flex flex-col items-center justify-center py-20 px-6 text-center">
        <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mb-4">
          <Users class="h-5 w-5 text-muted-foreground" />
        </div>
        <h3 class="text-base font-semibold tracking-tight">No contacts yet</h3>
        <p class="text-sm text-muted-foreground mt-1 max-w-xs">
          Add your first contact to start building your pipeline.
        </p>
        <Button class="mt-5 gap-1.5" @click="createDialogOpen = true">
          <Plus class="h-4 w-4" />
          New contact
        </Button>
      </div>

      <!-- Table -->
      <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="text-left text-xs uppercase tracking-wider text-muted-foreground">
              <th class="px-4 py-3 font-medium">Name</th>
              <th class="px-4 py-3 font-medium">Email</th>
              <th class="px-4 py-3 font-medium">Company</th>
              <th class="px-4 py-3 font-medium">Title</th>
              <th class="px-4 py-3 font-medium">Stage</th>
              <th class="px-4 py-3 font-medium text-right">Score</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="contact in contactsPage.items"
              :key="contact.id"
              class="cursor-pointer transition-colors hover:bg-white/5"
              style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
              @click="navigateTo(`/contacts/${contact.id}`)"
            >
              <td class="px-4 py-3 font-medium">{{ contact.fullName }}</td>
              <td class="px-4 py-3 text-muted-foreground">{{ contact.primaryEmail ?? '—' }}</td>
              <td class="px-4 py-3">{{ contact.companyName ?? '—' }}</td>
              <td class="px-4 py-3 text-muted-foreground">{{ contact.title ?? '—' }}</td>
              <td class="px-4 py-3">
                <Badge :variant="stageBadgeVariant(contact.lifecycleStage)">
                  {{ contact.lifecycleStage }}
                </Badge>
              </td>
              <td class="px-4 py-3 text-right tabular-nums">{{ contact.leadScore }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div
        v-if="contactsPage && contactsPage.totalPages > 1"
        class="flex items-center justify-between px-4 py-3"
        style="border-top: 1px solid hsl(240 5% 100% / 0.06);"
      >
        <p class="text-xs text-muted-foreground">
          Page {{ contactsPage.currentPage + 1 }} of {{ contactsPage.totalPages }}
        </p>
        <div class="flex gap-1">
          <Button
            size="sm"
            variant="ghost"
            class="h-8 gap-1"
            :disabled="contactsPage.currentPage === 0"
            @click="changePage(-1)"
          >
            <ChevronLeft class="h-4 w-4" />
            Previous
          </Button>
          <Button
            size="sm"
            variant="ghost"
            class="h-8 gap-1"
            :disabled="contactsPage.currentPage >= contactsPage.totalPages - 1"
            @click="changePage(1)"
          >
            Next
            <ChevronRight class="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>
