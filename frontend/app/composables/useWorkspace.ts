import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { WorkspaceAdminMapper } from '~/entities/workspace/workspace-admin.mapper'
import type {
  FullWorkspaceEntity,
  MemberEntity,
  InvitationEntity,
} from '~/entities/workspace/member.entity'
import type {
  WorkspaceApiDto,
  MemberApiDto,
  InvitationApiDto,
  InviteDto,
  UpdateWorkspaceDto,
  CreateWorkspaceDto,
  UpdateMemberRoleDto,
} from '~/types/api/workspace.dto'

// ── My workspaces (switcher) ──

export function useMyWorkspaces() {
  return useQuery({
    queryKey: ['workspaces', 'mine'],
    queryFn: async (): Promise<FullWorkspaceEntity[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: WorkspaceApiDto[] }>('/workspaces/mine')
      return WorkspaceAdminMapper.toFullEntityList(res.data)
    },
  })
}

export function useCurrentWorkspaceDetail() {
  return useQuery({
    queryKey: ['workspaces', 'current'],
    queryFn: async (): Promise<FullWorkspaceEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: WorkspaceApiDto }>('/workspaces/current')
      return WorkspaceAdminMapper.toFullEntity(res.data)
    },
  })
}

export function useCreateWorkspace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: CreateWorkspaceDto): Promise<FullWorkspaceEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: WorkspaceApiDto }>('/workspaces', {
        method: 'POST',
        body: dto,
      })
      return WorkspaceAdminMapper.toFullEntity(res.data)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['workspaces'] }),
  })
}

export function useUpdateWorkspace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: UpdateWorkspaceDto): Promise<FullWorkspaceEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: WorkspaceApiDto }>('/workspaces/current', {
        method: 'PUT',
        body: dto,
      })
      return WorkspaceAdminMapper.toFullEntity(res.data)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['workspaces'] }),
  })
}

// ── Members ──

export function useMembers() {
  return useQuery({
    queryKey: ['workspace', 'members'],
    queryFn: async (): Promise<MemberEntity[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: MemberApiDto[] }>('/workspaces/current/members')
      return WorkspaceAdminMapper.toMemberList(res.data)
    },
  })
}

export function useUpdateMemberRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (args: { membershipId: string; dto: UpdateMemberRoleDto }): Promise<MemberEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: MemberApiDto }>(
        `/workspaces/current/members/${args.membershipId}`,
        { method: 'PUT', body: args.dto }
      )
      return WorkspaceAdminMapper.toMember(res.data)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['workspace', 'members'] }),
  })
}

export function useRemoveMember() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (membershipId: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/workspaces/current/members/${membershipId}`, { method: 'DELETE' })
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['workspace', 'members'] }),
  })
}

// ── Invitations ──

export function useInvitations() {
  return useQuery({
    queryKey: ['workspace', 'invitations'],
    queryFn: async (): Promise<InvitationEntity[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: InvitationApiDto[] }>('/workspaces/current/invitations')
      return WorkspaceAdminMapper.toInvitationList(res.data)
    },
  })
}

export function useInvite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (dto: InviteDto): Promise<InvitationEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: InvitationApiDto }>('/workspaces/current/invitations', {
        method: 'POST',
        body: dto,
      })
      return WorkspaceAdminMapper.toInvitation(res.data)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['workspace', 'invitations'] }),
  })
}

export function useRevokeInvitation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const { $api } = useNuxtApp()
      await $api(`/workspaces/current/invitations/${id}`, { method: 'DELETE' })
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['workspace', 'invitations'] }),
  })
}

// ── Invitation accept flow (public + authenticated) ──

export function usePreviewInvitation(token: Ref<string | null>) {
  return useQuery({
    queryKey: ['invitation-preview', token] as const,
    queryFn: async (): Promise<InvitationEntity | null> => {
      if (!token.value) return null
      const { $api } = useNuxtApp()
      const res = await $api<{ data: InvitationApiDto }>(`/public/invitations/${token.value}`)
      return WorkspaceAdminMapper.toInvitation(res.data)
    },
    enabled: computed(() => !!token.value),
    retry: false,
  })
}

export function useAcceptInvitation() {
  return useMutation({
    mutationFn: async (token: string): Promise<FullWorkspaceEntity> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: WorkspaceApiDto }>(`/invitations/${token}/accept`, {
        method: 'POST',
      })
      return WorkspaceAdminMapper.toFullEntity(res.data)
    },
  })
}
