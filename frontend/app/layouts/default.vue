<!--
  Default Layout — platform pages (authenticated).

  Linear-style chrome:
    - Sidebar: lifted from the page with hairline borders, no hard edges
    - Nav items: monochrome SVG icons + subtle active indicator on the left edge
    - Topbar: transparent hairline strip, notifications bell on the right
    - Animated background sits behind everything with the "subtle" variant
-->
<script setup lang="ts">
import { Button } from '~/components/ui/button'

const authStore = useAuthStore()

// Navigation items — icon paths drawn inline as SVG so they track text color.
type NavItem = { label: string; path: string; icon: string }
const navItems: NavItem[] = [
  { label: 'Dashboard',     path: '/dashboard',     icon: 'M3 12l9-9 9 9v9a2 2 0 01-2 2h-4v-6H9v6H5a2 2 0 01-2-2v-9z' },
  { label: 'Contacts',      path: '/contacts',      icon: 'M16 11a4 4 0 10-8 0 4 4 0 008 0zm6 9a8 8 0 10-16 0' },
  { label: 'Pipelines',     path: '/pipelines',     icon: 'M4 5h4v14H4V5m6 4h4v10h-4V9m6-4h4v14h-4V5' },
  { label: 'Sequences',     path: '/sequences',     icon: 'M4 6h16v12H4V6m0 0l8 7 8-7' },
  { label: 'Templates',     path: '/templates',     icon: 'M4 4h16v4H4V4m0 6h10v10H4V10m12 0h4v10h-4V10' },
  { label: 'Mailboxes',     path: '/mailboxes',     icon: 'M3 8l9 6 9-6m-18 0v10h18V8m-18 0l9-5 9 5' },
  { label: 'Landing Pages', path: '/landing-pages', icon: 'M3 5h18v4H3V5m0 6h12v8H3v-8m14 0h4v8h-4v-8' },
  { label: 'Forms',         path: '/forms',         icon: 'M5 4h14v16H5V4m3 4h8m-8 4h8m-8 4h5' },
  { label: 'Enrichment',    path: '/enrichment',    icon: 'M12 3l3 7h7l-5.5 4 2 7L12 17l-6.5 4 2-7L2 10h7l3-7z' },
  { label: 'Lead Scoring',  path: '/lead-scoring',  icon: 'M12 2v20M2 12h20M5 5l14 14M19 5L5 19' },
  { label: 'Analytics',     path: '/analytics',     icon: 'M4 19V5m6 14V9m6 10v-6m6 6V13' },
  { label: 'Chat',          path: '/chat',          icon: 'M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z' },
  { label: 'Tasks',         path: '/tasks',         icon: 'M9 11l3 3L22 4m-6 11v4a2 2 0 01-2 2H5a2 2 0 01-2-2V6a2 2 0 012-2h8' },
  { label: 'Team',          path: '/settings/team', icon: 'M17 20h5v-2a4 4 0 00-3-3.87M9 20H4v-2a4 4 0 013-3.87m6 5.87V14a4 4 0 00-8 0v.13M15 7a3 3 0 11-6 0 3 3 0 016 0z' },
]

function handleLogout() {
  authStore.logout()
}

function initials(name: string | undefined): string {
  if (!name) return '·'
  return name.split(' ').map(p => p[0]).slice(0, 2).join('').toUpperCase()
}

const route = useRoute()
function isActive(path: string): boolean {
  if (path === '/dashboard') return route.path === path
  return route.path === path || route.path.startsWith(path + '/')
}

// Lock html/body scroll while this app-shell layout is mounted, so only
// <main> in the layout scrolls. Removed again on unmount so landing/auth
// pages (which use layout:false or auth layout) scroll normally.
onMounted(() => document.documentElement.classList.add('no-body-scroll'))
onBeforeUnmount(() => document.documentElement.classList.remove('no-body-scroll'))
</script>

<template>
  <!--
    App shell: chrome (sidebar + topbar) stays pinned, only the content area scrolls.
    Locking the outer container to h-screen + overflow-hidden prevents the whole
    page from scrolling — so short pages like a near-empty dashboard don't trigger
    a page-level scrollbar just because a sticky header nudged the layout past 100vh.
  -->
  <div class="relative h-screen overflow-hidden">
    <SharedAnimatedBackground variant="subtle" />

    <!-- Sidebar -->
    <aside class="fixed top-0 left-0 h-screen w-60 flex flex-col z-20"
           style="background: hsl(240 6% 7% / 0.7); backdrop-filter: blur(12px); border-right: 1px solid hsl(240 5% 100% / 0.06);">
      <!-- Logo + workspace switcher -->
      <div class="p-4 space-y-3" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
        <NuxtLink to="/" class="flex items-center gap-2 group">
          <SharedAppLogo with-wordmark />
        </NuxtLink>
        <SharedWorkspaceSwitcher />
      </div>

      <!-- Navigation -->
      <nav class="flex-1 p-2 space-y-0.5 overflow-y-auto">
        <NuxtLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="relative flex items-center gap-3 px-3 py-1.5 rounded-md text-sm transition-colors"
          :class="isActive(item.path)
            ? 'text-foreground bg-white/5'
            : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
        >
          <!-- Active indicator stripe on the left -->
          <span
            class="absolute left-0 top-1/2 -translate-y-1/2 h-5 w-0.5 rounded-r-full bg-primary transition-opacity"
            :class="isActive(item.path) ? 'opacity-100' : 'opacity-0'"
          />
          <svg
            class="w-4 h-4 shrink-0"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path :d="item.icon" />
          </svg>
          <span>{{ item.label }}</span>
        </NuxtLink>
      </nav>

      <!-- User info + logout -->
      <div class="p-3" style="border-top: 1px solid hsl(240 5% 100% / 0.06);">
        <div v-if="authStore.user" class="flex items-center gap-3 p-2 rounded-md">
          <div class="w-8 h-8 rounded-full bg-linear-to-br from-indigo-400 to-purple-500 text-white text-xs font-semibold flex items-center justify-center shrink-0">
            {{ initials(authStore.user.name) }}
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-sm font-medium truncate">{{ authStore.user.name }}</p>
            <p class="text-xs text-muted-foreground truncate">{{ authStore.user.email }}</p>
          </div>
        </div>
        <Button variant="outline" size="sm" class="w-full mt-2" @click="handleLogout">
          Sign out
        </Button>
      </div>
    </aside>

    <!-- Main content — this column is the full viewport height and ONLY
         the <main> within it is allowed to scroll. -->
    <div class="ml-60 h-screen flex flex-col">
      <header
        class="shrink-0 z-10 flex items-center justify-end gap-3 px-6 py-3"
        style="background: hsl(240 6% 6% / 0.55); backdrop-filter: blur(10px); border-bottom: 1px solid hsl(240 5% 100% / 0.06);"
      >
        <SharedNotificationBell />
      </header>
      <main class="flex-1 overflow-y-auto p-6 enter-fade-up">
        <slot />
      </main>
    </div>
  </div>
</template>
