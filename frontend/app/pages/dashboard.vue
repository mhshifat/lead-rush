<!--
  Dashboard page — real analytics (contacts, emails, rates, tasks).
  Data comes from /api/v1/analytics/overview and /api/v1/analytics/sequences.
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '~/components/ui/card'
import { Badge } from '~/components/ui/badge'

definePageMeta({
  middleware: 'auth',
})

const authStore = useAuthStore()
const { data: overview, isLoading } = useDashboardOverview()
const { data: sequences } = useSequencePerformance()

// Format a ratio 0..1 as a percentage string
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
  <div class="space-y-6">
    <div class="flex items-start justify-between">
      <div>
        <h1 class="text-3xl font-bold">Dashboard</h1>
        <p class="text-sm text-muted-foreground">
          Welcome back{{ authStore.user?.name ? ', ' + authStore.user.name : '' }}.
        </p>
      </div>
      <NuxtLink to="/analytics" class="text-sm text-primary hover:underline">
        View detailed reports →
      </NuxtLink>
    </div>

    <div v-if="isLoading" class="text-center py-8 text-muted-foreground">
      Loading analytics...
    </div>

    <div v-else-if="overview" class="space-y-6">
      <!-- Top metrics row -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card>
          <CardHeader class="pb-2">
            <CardDescription>Total Contacts</CardDescription>
          </CardHeader>
          <CardContent>
            <div class="text-3xl font-bold">{{ num(overview.totalContacts) }}</div>
            <p class="text-xs text-muted-foreground mt-1">
              +{{ num(overview.contactsAddedLast7Days) }} in last 7 days
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader class="pb-2">
            <CardDescription>Active Sequences</CardDescription>
          </CardHeader>
          <CardContent>
            <div class="text-3xl font-bold">{{ num(overview.activeSequences) }}</div>
            <p class="text-xs text-muted-foreground mt-1">
              {{ num(overview.totalEnrollments) }} total enrollments
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader class="pb-2">
            <CardDescription>Emails Sent (30d)</CardDescription>
          </CardHeader>
          <CardContent>
            <div class="text-3xl font-bold">{{ num(overview.emailsSent) }}</div>
            <p class="text-xs text-muted-foreground mt-1">
              {{ num(overview.emailsOpened) }} opened, {{ num(overview.emailsClicked) }} clicked
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader class="pb-2">
            <CardDescription>Pending Tasks</CardDescription>
          </CardHeader>
          <CardContent>
            <div class="text-3xl font-bold">{{ num(overview.pendingTasks) }}</div>
            <NuxtLink to="/tasks" class="text-xs text-primary hover:underline mt-1 inline-block">
              View tasks →
            </NuxtLink>
          </CardContent>
        </Card>
      </div>

      <!-- Engagement rates -->
      <Card>
        <CardHeader>
          <CardTitle>Engagement (last 30 days)</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="grid grid-cols-3 gap-6">
            <div>
              <div class="text-sm text-muted-foreground">Open Rate</div>
              <div class="text-2xl font-bold">{{ pct(overview.openRate) }}</div>
            </div>
            <div>
              <div class="text-sm text-muted-foreground">Click Rate</div>
              <div class="text-2xl font-bold">{{ pct(overview.clickRate) }}</div>
            </div>
            <div>
              <div class="text-sm text-muted-foreground">Reply Rate</div>
              <div class="text-2xl font-bold">{{ pct(overview.replyRate) }}</div>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Sequence performance table -->
      <Card>
        <CardHeader>
          <CardTitle>Sequence Performance</CardTitle>
          <CardDescription>Per-sequence engagement metrics (all time)</CardDescription>
        </CardHeader>
        <CardContent>
          <div v-if="!sequences?.length" class="text-sm text-muted-foreground py-4">
            No sequences yet. Create one in
            <NuxtLink to="/sequences" class="text-primary hover:underline">Sequences</NuxtLink>.
          </div>
          <div v-else class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b text-left text-muted-foreground">
                  <th class="p-2 font-medium">Sequence</th>
                  <th class="p-2 font-medium">Status</th>
                  <th class="p-2 font-medium text-right">Enrolled</th>
                  <th class="p-2 font-medium text-right">Sent</th>
                  <th class="p-2 font-medium text-right">Open Rate</th>
                  <th class="p-2 font-medium text-right">Click Rate</th>
                  <th class="p-2 font-medium text-right">Reply Rate</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="seq in sequences"
                  :key="seq.sequenceId"
                  class="border-b hover:bg-muted/50 cursor-pointer"
                  @click="navigateTo(`/sequences/${seq.sequenceId}`)"
                >
                  <td class="p-2 font-medium">{{ seq.sequenceName }}</td>
                  <td class="p-2"><Badge :variant="statusBadgeVariant(seq.status)">{{ seq.status }}</Badge></td>
                  <td class="p-2 text-right">{{ num(seq.totalEnrolled) }}</td>
                  <td class="p-2 text-right">{{ num(seq.emailsSent) }}</td>
                  <td class="p-2 text-right">{{ pct(seq.openRate) }}</td>
                  <td class="p-2 text-right">{{ pct(seq.clickRate) }}</td>
                  <td class="p-2 text-right">{{ pct(seq.replyRate) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
