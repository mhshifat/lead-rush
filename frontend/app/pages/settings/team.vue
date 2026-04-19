<!--
  Team Settings page
  - Edit workspace name/logo
  - List members + change role + remove
  - Invite teammates + view/revoke pending invitations
-->
<script setup lang="ts">
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '~/components/ui/card'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import { ROLE_OPTIONS, type WorkspaceRole, type MemberEntity, type InvitationEntity } from '~/entities/workspace/member.entity'

definePageMeta({ middleware: 'auth' })

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
watch(currentWs, (ws) => { if (ws) wsName.value = ws.name }, { immediate: true })

async function handleSaveName() {
  if (!wsName.value.trim()) { toast.error('Name is required'); return }
  try {
    await updateWsMutation.mutateAsync({ name: wsName.value.trim() })
    toast.success('Workspace updated')
  } catch {
    toast.error('Failed to update workspace')
  }
}

// ── Invite dialog ──
const inviteOpen = ref(false)
const inviteEmail = ref('')
const inviteRole = ref<WorkspaceRole>('MEMBER')

async function handleInvite() {
  if (!inviteEmail.value.trim()) { toast.error('Email is required'); return }
  try {
    await inviteMutation.mutateAsync({ email: inviteEmail.value.trim(), role: inviteRole.value })
    toast.success('Invitation sent')
    inviteOpen.value = false
    inviteEmail.value = ''
    inviteRole.value = 'MEMBER'
  } catch (error: any) {
    toast.error(error?.data?.error?.message ?? 'Failed to send invitation')
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
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-3xl font-bold">Team</h1>
      <p class="text-sm text-muted-foreground">Manage the workspace, members, and pending invitations.</p>
    </div>

    <!-- Workspace info -->
    <Card>
      <CardHeader>
        <CardTitle>Workspace</CardTitle>
        <CardDescription>Shown in the sidebar and on invitation emails.</CardDescription>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="space-y-2">
          <Label for="wsName">Name</Label>
          <div class="flex gap-2">
            <Input id="wsName" v-model="wsName" :disabled="!isManager" />
            <Button
              :disabled="!isManager || updateWsMutation.isPending.value || wsName === currentWs?.name"
              @click="handleSaveName"
            >Save</Button>
          </div>
        </div>
        <p v-if="!isManager" class="text-xs text-muted-foreground">Only owners and admins can edit workspace settings.</p>
      </CardContent>
    </Card>

    <!-- Members -->
    <Card>
      <CardHeader>
        <div class="flex items-start justify-between">
          <div>
            <CardTitle>Members</CardTitle>
            <CardDescription>{{ members?.length ?? 0 }} in this workspace</CardDescription>
          </div>
          <Button v-if="isManager" @click="inviteOpen = true">+ Invite</Button>
        </div>
      </CardHeader>
      <CardContent>
        <div v-if="!members?.length" class="text-sm text-muted-foreground py-4">No members yet.</div>
        <ul v-else class="divide-y">
          <li v-for="m in members" :key="m.membershipId" class="flex items-center gap-4 py-3">
            <div class="h-10 w-10 rounded-full bg-muted flex items-center justify-center text-sm font-semibold shrink-0">
              <img v-if="m.avatarUrl" :src="m.avatarUrl" :alt="m.name" class="h-full w-full rounded-full object-cover" />
              <span v-else>{{ initials(m.name) }}</span>
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium truncate">
                {{ m.name }}
                <span v-if="m.userId === authStore.user?.id" class="text-xs text-muted-foreground">(you)</span>
              </p>
              <p class="text-xs text-muted-foreground truncate">{{ m.email }}</p>
            </div>
            <div class="flex items-center gap-2">
              <select
                :value="m.role"
                :disabled="!isManager || m.userId === authStore.user?.id"
                class="h-8 rounded-md border border-input bg-transparent px-2 text-xs"
                @change="(e) => handleRoleChange(m, (e.target as HTMLSelectElement).value as WorkspaceRole)"
              >
                <option
                  v-for="opt in ROLE_OPTIONS"
                  :key="opt.value"
                  :value="opt.value"
                  :disabled="(opt.value === 'OWNER' || opt.value === 'ADMIN') && !isOwner"
                >
                  {{ opt.label }}
                </option>
              </select>
              <Button
                v-if="isManager && m.userId !== authStore.user?.id"
                size="sm"
                variant="outline"
                class="text-destructive"
                @click="handleRemove(m)"
              >Remove</Button>
            </div>
          </li>
        </ul>
      </CardContent>
    </Card>

    <!-- Pending invitations -->
    <Card v-if="invitations?.length">
      <CardHeader>
        <CardTitle>Invitations</CardTitle>
        <CardDescription>Sent invitations that haven't been accepted yet.</CardDescription>
      </CardHeader>
      <CardContent>
        <ul class="divide-y">
          <li v-for="inv in invitations" :key="inv.id" class="flex items-center gap-4 py-3">
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium truncate">{{ inv.email }}</p>
              <p class="text-xs text-muted-foreground">
                Invited {{ formatDate(inv.createdAt) }} · expires {{ formatDate(inv.expiresAt) }}
                <span v-if="inv.invitedByName"> · by {{ inv.invitedByName }}</span>
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
              class="text-destructive"
              @click="handleRevoke(inv)"
            >Revoke</Button>
          </li>
        </ul>
      </CardContent>
    </Card>

    <!-- Invite dialog -->
    <Dialog v-model:open="inviteOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Invite a teammate</DialogTitle>
          <CardDescription>They'll receive an email with a link that expires in 7 days.</CardDescription>
        </DialogHeader>
        <div class="space-y-4">
          <div class="space-y-2">
            <Label for="inviteEmail">Email *</Label>
            <Input id="inviteEmail" v-model="inviteEmail" type="email" placeholder="teammate@company.com" />
          </div>
          <div class="space-y-2">
            <Label for="inviteRole">Role</Label>
            <select
              id="inviteRole"
              v-model="inviteRole"
              class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm"
            >
              <option
                v-for="opt in ROLE_OPTIONS"
                :key="opt.value"
                :value="opt.value"
                :disabled="(opt.value === 'OWNER' || opt.value === 'ADMIN') && !isOwner"
              >
                {{ opt.label }} — {{ opt.description }}
              </option>
            </select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="inviteOpen = false">Cancel</Button>
          <Button :disabled="inviteMutation.isPending.value" @click="handleInvite">Send invite</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
