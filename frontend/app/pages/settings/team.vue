<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '~/components/ui/select'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription,
} from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import {
  Plus, Building2, Users, Mail, Trash2, Crown, ShieldCheck, User as UserIcon,
  Eye, Calendar, Send,
} from 'lucide-vue-next'
import { ROLE_OPTIONS, type WorkspaceRole, type MemberEntity, type InvitationEntity } from '~/entities/workspace/member.entity'

definePageMeta({ middleware: 'auth' })
useHead({ title: 'Team' })

const authStore = useAuthStore()

const { data: currentWs } = useCurrentWorkspaceDetail()
const { data: members } = useMembers()
const { data: invitations } = useInvitations()

const updateWsMutation = useUpdateWorkspace()
const updateRoleMutation = useUpdateMemberRole()
const removeMutation = useRemoveMember()
const inviteMutation = useInvite()
const revokeMutation = useRevokeInvitation()

const isManager = computed(() => {
  const role = authStore.currentWorkspace?.role
  return role === 'OWNER' || role === 'ADMIN'
})
const isOwner = computed(() => authStore.currentWorkspace?.role === 'OWNER')

// ── Workspace name edit ──
const wsName = ref('')
const wsErrors = useFieldErrors()
watch(currentWs, (ws) => { if (ws) wsName.value = ws.name }, { immediate: true })
watch(wsName, v => { if (v.trim()) wsErrors.remove('wsName') })

async function handleSaveName() {
  wsErrors.clear()
  if (!wsName.value.trim()) wsErrors.set('wsName', 'Name is required.')
  if (Object.keys(wsErrors.map).length) return
  try {
    await updateWsMutation.mutateAsync({ name: wsName.value.trim() })
    toast.success('Workspace updated')
  } catch (error: any) {
    wsErrors.fromServerError(error, 'Failed to update workspace')
  }
}

// ── Invite dialog ──
const inviteOpen = ref(false)
const inviteEmail = ref('')
const inviteRole = ref<WorkspaceRole>('MEMBER')
const inviteErrors = useFieldErrors()

watch(inviteEmail, v => { if (v.trim()) inviteErrors.remove('inviteEmail') })
watch(inviteOpen, (open) => { if (open) inviteErrors.clear() })

async function handleInvite() {
  inviteErrors.clear()
  if (!inviteEmail.value.trim()) inviteErrors.set('inviteEmail', 'Email is required.')
  if (Object.keys(inviteErrors.map).length) return
  try {
    await inviteMutation.mutateAsync({ email: inviteEmail.value.trim(), role: inviteRole.value })
    toast.success('Invitation sent')
    inviteOpen.value = false
    inviteEmail.value = ''
    inviteRole.value = 'MEMBER'
  } catch (error: any) {
    inviteErrors.fromServerError(error, 'Failed to send invitation')
  }
}

// ── Member role change ──
async function handleRoleChange(member: MemberEntity, role: WorkspaceRole) {
  if (role === member.role) return
  try {
    await updateRoleMutation.mutateAsync({
      membershipId: member.membershipId,
      dto: { role },
    })
    toast.success(`${member.name} is now ${role.toLowerCase()}`)
  } catch (error: any) {
    toast.error(error?.data?.error?.message ?? 'Failed to update role')
  }
}

async function handleRemove(member: MemberEntity) {
  const ok = await useConfirm().ask({
    title: `Remove ${member.name}?`,
    description: 'They will lose access to this workspace immediately.',
    confirmLabel: 'Remove',
    variant: 'destructive',
  })
  if (!ok) return
  try {
    await removeMutation.mutateAsync(member.membershipId)
    toast.success(`${member.name} removed`)
  } catch (error: any) {
    toast.error(error?.data?.error?.message ?? 'Failed to remove member')
  }
}

async function handleRevoke(inv: InvitationEntity) {
  const ok = await useConfirm().ask({
    title: `Revoke invitation to ${inv.email}?`,
    confirmLabel: 'Revoke',
    variant: 'destructive',
  })
  if (!ok) return
  try {
    await revokeMutation.mutateAsync(inv.id)
    toast.success('Invitation revoked')
  } catch (error: any) {
    toast.error(error?.data?.error?.message ?? 'Failed to revoke')
  }
}

function initials(name: string): string {
  return name.split(' ').map(p => p[0]).slice(0, 2).join('').toUpperCase()
}

function formatDate(d: Date): string {
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(d)
}

// Visual hierarchy for roles — owner gets a crown, admin a shield, the rest a user icon.
function roleIcon(role: WorkspaceRole) {
  if (role === 'OWNER') return Crown
  if (role === 'ADMIN') return ShieldCheck
  if (role === 'VIEWER') return Eye
  return UserIcon
}
function roleAccent(role: WorkspaceRole): string {
  if (role === 'OWNER') return 'text-amber-400'
  if (role === 'ADMIN') return 'text-primary'
  return 'text-muted-foreground'
}

const pendingInvitations = computed(() => (invitations.value ?? []).filter(i => i.status === 'PENDING'))
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Header -->
    <div>
      <h1 class="text-2xl font-semibold tracking-tight">Team</h1>
      <p class="text-sm text-muted-foreground mt-0.5">
        Manage the workspace, members, and pending invitations.
      </p>
    </div>

    <!-- Summary strip -->
    <div class="glass hairline rounded-xl grid grid-cols-3 divide-x divide-white/5">
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Members</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ members?.length ?? 0 }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Pending invites</p>
        <p class="mt-1 text-xl font-semibold tabular-nums">{{ pendingInvitations.length }}</p>
      </div>
      <div class="p-4">
        <p class="text-xs text-muted-foreground">Your role</p>
        <p class="mt-1 text-xl font-semibold capitalize">
          {{ authStore.currentWorkspace?.role?.toLowerCase() ?? '—' }}
        </p>
      </div>
    </div>

    <!-- Workspace info -->
    <div class="glass hairline rounded-xl overflow-hidden">
      <div class="px-5 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
        <div class="flex items-start gap-3">
          <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
            <Building2 class="h-4 w-4 text-primary" />
          </div>
          <div class="min-w-0">
            <h3 class="text-sm font-semibold tracking-tight">Workspace</h3>
            <p class="text-xs text-muted-foreground mt-0.5">
              Shown in the sidebar and on invitation emails.
            </p>
          </div>
        </div>
      </div>
      <div class="p-5 space-y-3">
        <div
          v-if="wsErrors.has('_form')"
          class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
        >
          {{ wsErrors.get('_form') }}
        </div>
        <div class="space-y-2">
          <Label for="wsName" class="text-xs">Name</Label>
          <div class="flex gap-2">
            <Input
              id="wsName"
              v-model="wsName"
              :disabled="!isManager"
              :class="wsErrors.has('wsName') ? 'border-destructive' : ''"
              class="flex-1"
            />
            <Button
              :disabled="!isManager || updateWsMutation.isPending.value || wsName === currentWs?.name"
              @click="handleSaveName"
            >
              {{ updateWsMutation.isPending.value ? 'Saving…' : 'Save' }}
            </Button>
          </div>
          <SharedFormError :message="wsErrors.get('wsName')" />
        </div>
        <p v-if="!isManager" class="text-xs text-muted-foreground">
          Only owners and admins can edit workspace settings.
        </p>
      </div>
    </div>

    <!-- Members -->
    <div class="glass hairline rounded-xl overflow-hidden">
      <div class="px-5 py-4 flex items-start justify-between gap-3" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
        <div class="flex items-start gap-3 min-w-0">
          <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
            <Users class="h-4 w-4 text-primary" />
          </div>
          <div class="min-w-0">
            <h3 class="text-sm font-semibold tracking-tight">Members</h3>
            <p class="text-xs text-muted-foreground mt-0.5">
              {{ members?.length ?? 0 }} {{ members?.length === 1 ? 'person' : 'people' }} in this workspace
            </p>
          </div>
        </div>
        <Button v-if="isManager" class="gap-1.5" @click="inviteOpen = true">
          <Plus class="h-4 w-4" />
          Invite
        </Button>
      </div>
      <div>
        <div v-if="!members?.length" class="p-8 text-sm text-muted-foreground text-center">
          No members yet.
        </div>
        <ul v-else>
          <li
            v-for="(m, idx) in members"
            :key="m.membershipId"
            class="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-white/2"
            :style="idx > 0 ? 'border-top: 1px solid hsl(240 5% 100% / 0.05);' : ''"
          >
            <div class="relative shrink-0">
              <div class="h-10 w-10 rounded-full bg-linear-to-br from-indigo-400 to-purple-500 text-white text-sm font-semibold flex items-center justify-center">
                <img v-if="m.avatarUrl" :src="m.avatarUrl" :alt="m.name" class="h-full w-full rounded-full object-cover" />
                <span v-else>{{ initials(m.name) }}</span>
              </div>
              <span
                class="absolute -bottom-0.5 -right-0.5 h-4 w-4 rounded-full ring-2 ring-background bg-background flex items-center justify-center"
                :title="m.role"
              >
                <component :is="roleIcon(m.role)" class="h-2.5 w-2.5" :class="roleAccent(m.role)" />
              </span>
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <p class="text-sm font-medium truncate">{{ m.name }}</p>
                <Badge v-if="m.userId === authStore.user?.id" variant="outline" class="text-[10px] h-4 px-1.5">
                  You
                </Badge>
              </div>
              <p class="text-xs text-muted-foreground truncate">{{ m.email }}</p>
            </div>
            <div class="flex items-center gap-2 shrink-0">
              <Select
                :model-value="m.role"
                :disabled="!isManager || m.userId === authStore.user?.id"
                @update:model-value="(v: any) => typeof v === 'string' && handleRoleChange(m, v as WorkspaceRole)"
              >
                <SelectTrigger class="h-8 w-32 text-xs"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem
                    v-for="opt in ROLE_OPTIONS"
                    :key="opt.value"
                    :value="opt.value"
                    :disabled="(opt.value === 'OWNER' || opt.value === 'ADMIN') && !isOwner"
                  >
                    {{ opt.label }}
                  </SelectItem>
                </SelectContent>
              </Select>
              <Button
                v-if="isManager && m.userId !== authStore.user?.id"
                size="sm"
                variant="outline"
                class="h-8 w-8 p-0 text-muted-foreground hover:text-destructive"
                title="Remove"
                @click="handleRemove(m)"
              >
                <Trash2 class="h-3.5 w-3.5" />
              </Button>
            </div>
          </li>
        </ul>
      </div>
    </div>

    <!-- Pending invitations -->
    <div v-if="invitations?.length" class="glass hairline rounded-xl overflow-hidden">
      <div class="px-5 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
        <div class="flex items-start gap-3">
          <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
            <Mail class="h-4 w-4 text-primary" />
          </div>
          <div class="min-w-0">
            <h3 class="text-sm font-semibold tracking-tight">Invitations</h3>
            <p class="text-xs text-muted-foreground mt-0.5">
              Sent invitations that haven't been accepted yet.
            </p>
          </div>
        </div>
      </div>
      <ul>
        <li
          v-for="(inv, idx) in invitations"
          :key="inv.id"
          class="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-white/2"
          :style="idx > 0 ? 'border-top: 1px solid hsl(240 5% 100% / 0.05);' : ''"
        >
          <div class="h-9 w-9 shrink-0 rounded-full bg-white/5 flex items-center justify-center">
            <Mail class="h-4 w-4 text-muted-foreground" />
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium truncate">{{ inv.email }}</p>
            <p class="text-xs text-muted-foreground inline-flex items-center gap-2 mt-0.5">
              <span class="inline-flex items-center gap-1">
                <Calendar class="h-3 w-3" />
                Invited {{ formatDate(inv.createdAt) }}
              </span>
              <span class="opacity-60">·</span>
              <span>expires {{ formatDate(inv.expiresAt) }}</span>
              <template v-if="inv.invitedByName">
                <span class="opacity-60">·</span>
                <span>by {{ inv.invitedByName }}</span>
              </template>
            </p>
          </div>
          <Badge variant="outline" class="text-xs">{{ inv.role }}</Badge>
          <Badge
            :variant="inv.status === 'PENDING' ? 'secondary' : inv.status === 'ACCEPTED' ? 'default' : 'outline'"
            class="text-xs"
          >{{ inv.status }}</Badge>
          <Button
            v-if="inv.status === 'PENDING' && isManager"
            size="sm"
            variant="outline"
            class="h-8 gap-1 text-muted-foreground hover:text-destructive"
            @click="handleRevoke(inv)"
          >
            <Trash2 class="h-3.5 w-3.5" />
            Revoke
          </Button>
        </li>
      </ul>
    </div>

    <!-- Invite dialog -->
    <Dialog v-model:open="inviteOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Invite a teammate</DialogTitle>
          <DialogDescription>
            They'll receive an email with a link that expires in 7 days.
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-4">
          <div
            v-if="inviteErrors.has('_form')"
            class="rounded-md bg-destructive/10 p-3 text-sm text-destructive"
          >
            {{ inviteErrors.get('_form') }}
          </div>
          <div class="space-y-2">
            <Label for="inviteEmail">Email *</Label>
            <Input
              id="inviteEmail"
              v-model="inviteEmail"
              type="email"
              placeholder="teammate@company.com"
              :class="inviteErrors.has('inviteEmail') ? 'border-destructive' : ''"
              @keyup.enter="handleInvite"
            />
            <SharedFormError :message="inviteErrors.get('inviteEmail')" />
          </div>
          <div class="space-y-2">
            <Label for="inviteRole">Role</Label>
            <Select v-model="inviteRole">
              <SelectTrigger id="inviteRole" class="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="opt in ROLE_OPTIONS"
                  :key="opt.value"
                  :value="opt.value"
                  :disabled="(opt.value === 'OWNER' || opt.value === 'ADMIN') && !isOwner"
                >
                  <span class="font-medium">{{ opt.label }}</span>
                  <span class="text-muted-foreground"> — {{ opt.description }}</span>
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="inviteOpen = false">Cancel</Button>
          <Button class="gap-1.5" :disabled="inviteMutation.isPending.value" @click="handleInvite">
            <Send class="h-3.5 w-3.5" />
            {{ inviteMutation.isPending.value ? 'Sending…' : 'Send invite' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
