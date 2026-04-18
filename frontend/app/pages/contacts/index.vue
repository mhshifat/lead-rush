<script setup lang="ts">
import { useDebounceFn } from '@vueuse/core'
import { Card, CardContent, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
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

const createMutation = useCreateContact()

async function handleCreate() {
  if (!newContact.value.firstName.trim()) {
    toast.error('First name is required')
    return
  }

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
    const msg = error?.data?.error?.message || 'Failed to create contact'
    toast.error(msg)
  }
}

// ── Lifecycle stage badge colors ──
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
  <div class="space-y-6">
    <!-- Page header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold">Contacts</h1>
        <p class="text-sm text-muted-foreground">
          {{ contactsPage?.totalElements ?? 0 }} total contacts
        </p>
      </div>
      <Dialog v-model:open="createDialogOpen">
        <DialogTrigger as-child>
          <Button>+ New Contact</Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create Contact</DialogTitle>
          </DialogHeader>
          <div class="space-y-4">
            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-2">
                <Label for="firstName">First Name *</Label>
                <Input id="firstName" v-model="newContact.firstName" required />
              </div>
              <div class="space-y-2">
                <Label for="lastName">Last Name</Label>
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
              {{ createMutation.isPending.value ? 'Creating...' : 'Create' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <!-- Filters -->
    <Card>
      <CardContent class="pt-6">
        <div class="flex flex-col gap-4 md:flex-row md:items-end">
          <div class="flex-1 space-y-2">
            <Label for="search">Search</Label>
            <Input
              id="search"
              v-model="searchInput"
              placeholder="Search by name..."
            />
          </div>
          <div class="space-y-2 md:w-56">
            <Label>Lifecycle Stage</Label>
            <div class="flex gap-1 flex-wrap">
              <Button
                size="sm"
                :variant="!filters.lifecycleStage ? 'default' : 'outline'"
                @click="setLifecycleStage(undefined)"
              >All</Button>
              <Button
                v-for="stage in ['LEAD', 'QUALIFIED', 'CUSTOMER']"
                :key="stage"
                size="sm"
                :variant="filters.lifecycleStage === stage ? 'default' : 'outline'"
                @click="setLifecycleStage(stage)"
              >{{ stage }}</Button>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Contacts table -->
    <Card>
      <CardContent class="pt-6">
        <div v-if="isLoading" class="text-center py-8 text-muted-foreground">
          Loading contacts...
        </div>

        <div v-else-if="isError" class="text-center py-8 text-destructive">
          Failed to load contacts
        </div>

        <div v-else-if="!contactsPage?.items.length" class="text-center py-8 text-muted-foreground">
          No contacts found. Click "New Contact" to create one.
        </div>

        <div v-else class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b text-left">
                <th class="p-2 font-medium">Name</th>
                <th class="p-2 font-medium">Email</th>
                <th class="p-2 font-medium">Company</th>
                <th class="p-2 font-medium">Title</th>
                <th class="p-2 font-medium">Stage</th>
                <th class="p-2 font-medium">Score</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="contact in contactsPage.items"
                :key="contact.id"
                class="border-b hover:bg-muted/50 cursor-pointer"
                @click="navigateTo(`/contacts/${contact.id}`)"
              >
                <td class="p-2 font-medium">{{ contact.fullName }}</td>
                <td class="p-2 text-muted-foreground">{{ contact.primaryEmail ?? '—' }}</td>
                <td class="p-2">{{ contact.companyName ?? '—' }}</td>
                <td class="p-2 text-muted-foreground">{{ contact.title ?? '—' }}</td>
                <td class="p-2">
                  <Badge :variant="stageBadgeVariant(contact.lifecycleStage)">
                    {{ contact.lifecycleStage }}
                  </Badge>
                </td>
                <td class="p-2">{{ contact.leadScore }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        <div v-if="contactsPage && contactsPage.totalPages > 1" class="flex items-center justify-between mt-4 pt-4 border-t">
          <p class="text-sm text-muted-foreground">
            Page {{ contactsPage.currentPage + 1 }} of {{ contactsPage.totalPages }}
          </p>
          <div class="flex gap-2">
            <Button
              size="sm"
              variant="outline"
              :disabled="contactsPage.currentPage === 0"
              @click="changePage(-1)"
            >Previous</Button>
            <Button
              size="sm"
              variant="outline"
              :disabled="contactsPage.currentPage >= contactsPage.totalPages - 1"
              @click="changePage(1)"
            >Next</Button>
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
