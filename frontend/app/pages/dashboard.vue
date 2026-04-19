<script setup lang="ts">
import { Badge } from '~/components/ui/badge'
import {
  Users,
  Activity,
  Mail,
  CheckSquare,
  ArrowUpRight,
  ArrowRight,
  TrendingUp,
  BarChart3,
} from 'lucide-vue-next'

definePageMeta({
  middleware: 'auth',
})

const authStore = useAuthStore()
const { data: overview, isLoading } = useDashboardOverview()
const { data: sequences } = useSequencePerformance()

function pct(rate: number): string {
  return (rate * 100).toFixed(1) + '%'
}

function num(n: number): string {
  return n.toLocaleString()
}

function statusBadgeVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'ACTIVE': return 'default'
    case 'DRAFT': return 'secondary'
    case 'PAUSED': return 'outline'
    default: return 'secondary'
  }
}
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Page header -->
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          Welcome back{{ authStore.user?.name ? ', ' + authStore.user.name.split(' ')[0] : '' }}
        </p>
      </div>
      <NuxtLink
        to="/analytics"
        class="inline-flex items-center gap-1 text-sm text-primary hover:underline"
      >
        Detailed reports
        <ArrowUpRight class="h-3.5 w-3.5" />
      </NuxtLink>
    </div>

    <!-- Loading -->
    <div v-if="isLoading" class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground">
      Loading analytics…
    </div>

    <div v-else-if="overview" class="space-y-5">
      <!-- Metric cards -->
      <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <!-- Total Contacts -->
        <div class="glass hairline rounded-xl p-5 transition-colors hover:bg-white/2">
          <div class="flex items-start justify-between">
            <div class="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
              <Users class="h-4 w-4 text-primary" />
            </div>
            <span class="text-xs uppercase tracking-wider text-muted-foreground">Contacts</span>
          </div>
          <div class="mt-4">
            <div class="text-3xl font-semibold tracking-tight tabular-nums">{{ num(overview.totalContacts) }}</div>
            <p class="text-xs text-muted-foreground mt-1.5">
              <span v-if="overview.contactsAddedLast7Days > 0" class="text-emerald-400">
                +{{ num(overview.contactsAddedLast7Days) }}
              </span>
              <span v-else>+0</span>
              this week
            </p>
          </div>
        </div>

        <!-- Active Sequences -->
        <div class="glass hairline rounded-xl p-5 transition-colors hover:bg-white/2">
          <div class="flex items-start justify-between">
            <div class="h-9 w-9 rounded-lg bg-sky-500/10 flex items-center justify-center">
              <Activity class="h-4 w-4 text-sky-400" />
            </div>
            <span class="text-xs uppercase tracking-wider text-muted-foreground">Sequences</span>
          </div>
          <div class="mt-4">
            <div class="text-3xl font-semibold tracking-tight tabular-nums">{{ num(overview.activeSequences) }}</div>
            <p class="text-xs text-muted-foreground mt-1.5">
              {{ num(overview.totalEnrollments) }} enrollments
            </p>
          </div>
        </div>

        <!-- Emails Sent -->
        <div class="glass hairline rounded-xl p-5 transition-colors hover:bg-white/2">
          <div class="flex items-start justify-between">
            <div class="h-9 w-9 rounded-lg bg-violet-500/10 flex items-center justify-center">
              <Mail class="h-4 w-4 text-violet-400" />
            </div>
            <span class="text-xs uppercase tracking-wider text-muted-foreground">Emails · 30d</span>
          </div>
          <div class="mt-4">
            <div class="text-3xl font-semibold tracking-tight tabular-nums">{{ num(overview.emailsSent) }}</div>
            <p class="text-xs text-muted-foreground mt-1.5">
              {{ num(overview.emailsOpened) }} opened · {{ num(overview.emailsClicked) }} clicked
            </p>
          </div>
        </div>

        <!-- Pending Tasks -->
        <div class="glass hairline rounded-xl p-5 transition-colors hover:bg-white/2">
          <div class="flex items-start justify-between">
            <div class="h-9 w-9 rounded-lg bg-amber-500/10 flex items-center justify-center">
              <CheckSquare class="h-4 w-4 text-amber-400" />
            </div>
            <span class="text-xs uppercase tracking-wider text-muted-foreground">Tasks</span>
          </div>
          <div class="mt-4">
            <div class="text-3xl font-semibold tracking-tight tabular-nums">{{ num(overview.pendingTasks) }}</div>
            <NuxtLink
              to="/tasks"
              class="inline-flex items-center gap-1 text-xs text-primary hover:underline mt-1.5"
            >
              View all
              <ArrowRight class="h-3 w-3" />
            </NuxtLink>
          </div>
        </div>
      </div>

      <!-- Engagement rates -->
      <div class="glass hairline rounded-xl p-6">
        <div class="flex items-center justify-between mb-5">
          <div class="flex items-center gap-2">
            <TrendingUp class="h-4 w-4 text-muted-foreground" />
            <h2 class="text-sm font-semibold tracking-tight">Engagement</h2>
          </div>
          <span class="text-xs text-muted-foreground">Last 30 days</span>
        </div>
        <div class="grid grid-cols-3 divide-x" style="--tw-divide-opacity: 1; border-color: hsl(240 5% 100% / 0.06);">
          <div class="pr-6">
            <p class="text-xs uppercase tracking-wider text-muted-foreground">Open rate</p>
            <p class="text-2xl font-semibold tracking-tight tabular-nums mt-1.5">{{ pct(overview.openRate) }}</p>
          </div>
          <div class="px-6" style="border-left: 1px solid hsl(240 5% 100% / 0.06);">
            <p class="text-xs uppercase tracking-wider text-muted-foreground">Click rate</p>
            <p class="text-2xl font-semibold tracking-tight tabular-nums mt-1.5">{{ pct(overview.clickRate) }}</p>
          </div>
          <div class="pl-6" style="border-left: 1px solid hsl(240 5% 100% / 0.06);">
            <p class="text-xs uppercase tracking-wider text-muted-foreground">Reply rate</p>
            <p class="text-2xl font-semibold tracking-tight tabular-nums mt-1.5">{{ pct(overview.replyRate) }}</p>
          </div>
        </div>
      </div>

      <!-- Sequence performance -->
      <div class="glass hairline rounded-xl overflow-hidden">
        <div class="flex items-center justify-between px-6 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <div class="flex items-center gap-2">
            <BarChart3 class="h-4 w-4 text-muted-foreground" />
            <div>
              <h2 class="text-sm font-semibold tracking-tight">Sequence performance</h2>
              <p class="text-xs text-muted-foreground">All-time engagement per sequence</p>
            </div>
          </div>
        </div>

        <!-- Empty -->
        <div v-if="!sequences?.length" class="flex flex-col items-center justify-center py-14 px-6 text-center">
          <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mb-4">
            <Activity class="h-5 w-5 text-muted-foreground" />
          </div>
          <h3 class="text-sm font-semibold tracking-tight">No sequences yet</h3>
          <p class="text-sm text-muted-foreground mt-1 max-w-xs">
            Create a sequence to start tracking outreach engagement.
          </p>
          <NuxtLink
            to="/sequences"
            class="inline-flex items-center gap-1 text-sm text-primary hover:underline mt-4"
          >
            Go to sequences
            <ArrowRight class="h-3.5 w-3.5" />
          </NuxtLink>
        </div>

        <!-- Table -->
        <div v-else class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-left text-xs uppercase tracking-wider text-muted-foreground">
                <th class="px-6 py-3 font-medium">Sequence</th>
                <th class="px-4 py-3 font-medium">Status</th>
                <th class="px-4 py-3 font-medium text-right">Enrolled</th>
                <th class="px-4 py-3 font-medium text-right">Sent</th>
                <th class="px-4 py-3 font-medium text-right">Open</th>
                <th class="px-4 py-3 font-medium text-right">Click</th>
                <th class="px-6 py-3 font-medium text-right">Reply</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="seq in sequences"
                :key="seq.sequenceId"
                class="cursor-pointer transition-colors hover:bg-white/5"
                style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
                @click="navigateTo(`/sequences/${seq.sequenceId}`)"
              >
                <td class="px-6 py-3 font-medium">{{ seq.sequenceName }}</td>
                <td class="px-4 py-3">
                  <Badge :variant="statusBadgeVariant(seq.status)">{{ seq.status }}</Badge>
                </td>
                <td class="px-4 py-3 text-right tabular-nums">{{ num(seq.totalEnrolled) }}</td>
                <td class="px-4 py-3 text-right tabular-nums">{{ num(seq.emailsSent) }}</td>
                <td class="px-4 py-3 text-right tabular-nums">{{ pct(seq.openRate) }}</td>
                <td class="px-4 py-3 text-right tabular-nums">{{ pct(seq.clickRate) }}</td>
                <td class="px-6 py-3 text-right tabular-nums">{{ pct(seq.replyRate) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
