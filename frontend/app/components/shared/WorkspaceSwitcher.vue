<!--
  Workspace switcher dropdown — sits in the sidebar under the Lead Rush logo.
  - Shows the current workspace
  - Clicking opens a menu with the user's other workspaces
  - Picking one calls the switch-workspace endpoint, then refetches everything

  Uses the same outside-click pattern as NotificationBell to stay self-contained.
-->
<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { toast } from 'vue-sonner'

const authStore = useAuthStore()
const queryClient = useQueryClient()
const { data: workspaces } = useMyWorkspaces()

const open = ref(false)
const buttonRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const switching = ref(false)

function onClickOutside(e: MouseEvent) {
  const t = e.target as Node
  if (panelRef.value?.contains(t) || buttonRef.value?.contains(t)) return
  open.value = false
}
onMounted(() => document.addEventListener('click', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))

async function handleSwitch(workspaceId: string) {
  if (authStore.currentWorkspace?.id === workspaceId) {
    open.value = false
    return
  }
  switching.value = true
  try {
    await authStore.switchWorkspace(workspaceId)
    // All cached data is scoped to the old workspace — nuke it
    queryClient.clear()
    toast.success('Workspace switched')
    open.value = false
  } catch {
    toast.error('Failed to switch workspace')
  } finally {
    switching.value = false
  }
}

function handleCreate() {
  open.value = false
  navigateTo('/settings/workspaces/new')
}
</script>

<template>
  <div class="relative">
    <button
      ref="buttonRef"
      class="w-full text-left rounded-md hairline hover:bg-white/5 px-3 py-2 transition-colors"
      @click="open = !open"
    >
      <div class="flex items-center justify-between gap-2">
        <div class="min-w-0">
          <p class="text-xs text-muted-foreground">Workspace</p>
          <p class="text-sm font-medium truncate">
            {{ authStore.currentWorkspace?.name ?? '—' }}
          </p>
        </div>
        <span class="text-xs text-muted-foreground shrink-0">▾</span>
      </div>
    </button>

    <div
      v-if="open"
      ref="panelRef"
      class="absolute left-0 top-full mt-1 w-full min-w-56 popover-panel rounded-lg z-50 overflow-hidden"
    >
      <div class="max-h-72 overflow-y-auto">
        <button
          v-for="ws in workspaces"
          :key="ws.id"
          class="w-full text-left px-3 py-2 hover:bg-white/5 flex items-center justify-between gap-2 transition-colors"
          :disabled="switching"
          @click="handleSwitch(ws.id)"
        >
          <div class="min-w-0">
            <p class="text-sm font-medium truncate">{{ ws.name }}</p>
            <p class="text-xs text-muted-foreground">{{ ws.role }} · {{ ws.memberCount }} member{{ ws.memberCount === 1 ? '' : 's' }}</p>
          </div>
          <span
            v-if="authStore.currentWorkspace?.id === ws.id"
            class="text-xs text-primary shrink-0"
          >✓</span>
        </button>
      </div>
      <div style="border-top: 1px solid hsl(240 5% 100% / 0.06);">
        <button
          class="w-full text-left px-3 py-2 text-sm text-primary hover:bg-white/5 transition-colors"
          @click="handleCreate"
        >
          + Create workspace
        </button>
        <NuxtLink
          to="/settings/team"
          class="block px-3 py-2 text-sm hover:bg-white/5 transition-colors"
          @click="open = false"
        >
          Team settings
        </NuxtLink>
        <NuxtLink
          to="/settings/api-keys"
          class="block px-3 py-2 text-sm hover:bg-white/5 transition-colors"
          @click="open = false"
        >
          API keys
        </NuxtLink>
        <NuxtLink
          to="/settings/webhooks"
          class="block px-3 py-2 text-sm hover:bg-white/5 transition-colors"
          @click="open = false"
        >
          Webhooks
        </NuxtLink>
      </div>
    </div>
  </div>
</template>
