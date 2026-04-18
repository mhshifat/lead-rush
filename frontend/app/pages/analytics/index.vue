<!--
  Analytics index — tabbed view of the four reports:
    • Sequences (funnel per sequence)
    • Pipelines (stage distribution + value)
    • Contacts (growth + lifecycle snapshot)
    • Mailboxes (send volume + bounce rate)
-->
<script setup lang="ts">
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '~/components/ui/card'
import { Badge } from '~/components/ui/badge'
import { Button } from '~/components/ui/button'

definePageMeta({ middleware: 'auth' })

type Tab = 'sequences' | 'pipelines' | 'contacts' | 'mailboxes'
const tab = ref<Tab>('sequences')

const { data: sequences } = useSequencePerformance()
const selectedSequenceId = ref<string | null>(null)
const sequenceIdRef = computed(() => selectedSequenceId.value)
const { data: funnel, isFetching: funnelLoading } = useSequenceFunnel(sequenceIdRef)

const { data: pipelineReports } = usePipelineReports()

const growthDays = ref(30)
const { data: growth } = useContactGrowth(growthDays)

const { data: mailboxHealth } = useMailboxHealth()

// Auto-select the first sequence when the list loads
watch(sequences, (list) => {
  if (!selectedSequenceId.value && list?.length) {
    selectedSequenceId.value = list[0]!.sequenceId
  }
}, { immediate: true })

// ── Helpers ──
function pct(n: number): string {
  return (n * 100).toFixed(1) + '%'
}
function num(n: number): string {
  return n.toLocaleString()
}
function money(n: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(n)
}
function shortDate(iso: string): string {
  const d = new Date(iso + 'T00:00:00')
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' }).format(d)
}

// Max per-step send count across the funnel — used to normalize bar widths
const funnelMax = computed(() => {
  const steps = funnel.value?.steps ?? []
  return Math.max(1, ...steps.map(s => s.sent))
})

const growthPoints = computed(() => growth.value?.series.map(s => s.count) ?? [])

const lifecycleTotal = computed(() => (growth.value?.byLifecycle ?? []).reduce((s, x) => s + x.count, 0))
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-start justify-between">
      <div>
        <h1 class="text-3xl font-bold">Analytics</h1>
        <p class="text-sm text-muted-foreground">Drill into sequences, pipelines, contacts, and mailbox health.</p>
      </div>
      <NuxtLink to="/dashboard" class="text-sm text-muted-foreground hover:text-foreground">
        ← Back to dashboard
      </NuxtLink>
    </div>

    <!-- Tab bar -->
    <div class="flex gap-1 border-b">
      <button
        v-for="t in (['sequences','pipelines','contacts','mailboxes'] as Tab[])"
        :key="t"
        class="px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors capitalize"
        :class="tab === t
          ? 'border-primary text-foreground'
          : 'border-transparent text-muted-foreground hover:text-foreground'"
        @click="tab = t"
      >{{ t }}</button>
    </div>

    <!-- ── SEQUENCES: funnel per sequence ── -->
    <div v-if="tab === 'sequences'" class="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Sequence funnel</CardTitle>
          <CardDescription>
            Step-by-step engagement. Wide drop-offs signal where leads disengage.
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div v-if="!sequences?.length" class="text-sm text-muted-foreground py-4">
            No sequences yet.
            <NuxtLink to="/sequences" class="text-primary hover:underline">Create one →</NuxtLink>
          </div>
          <template v-else>
            <div class="space-y-2">
              <label class="text-xs font-medium">Sequence</label>
              <select
                v-model="selectedSequenceId"
                class="flex h-9 w-full md:w-96 rounded-md border border-input bg-transparent px-3 text-sm"
              >
                <option v-for="s in sequences" :key="s.sequenceId" :value="s.sequenceId">
                  {{ s.sequenceName }} ({{ s.status }})
                </option>
              </select>
            </div>

            <div v-if="funnelLoading" class="text-sm text-muted-foreground py-4">Loading funnel…</div>
            <div v-else-if="!funnel?.steps.length" class="text-sm text-muted-foreground py-4">
              This sequence has no steps yet.
            </div>
            <div v-else class="space-y-3">
              <div
                v-for="step in funnel.steps"
                :key="step.stepId"
                class="rounded-md border p-3 space-y-2"
              >
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <span class="text-xs text-muted-foreground w-8">#{{ step.stepOrder }}</span>
                    <Badge variant="outline" class="text-xs">{{ step.stepType }}</Badge>
                    <span class="text-sm font-medium">{{ step.label }}</span>
                  </div>
                  <span v-if="step.stepType !== 'EMAIL'" class="text-xs text-muted-foreground">
                    {{ num(step.sent) }} executed
                  </span>
                </div>

                <template v-if="step.stepType === 'EMAIL'">
                  <SharedFunnelBar :value="step.sent" :max="funnelMax" label="Sent" />
                  <SharedFunnelBar
                    :value="step.opened"
                    :max="funnelMax"
                    :label="`Opened (${pct(step.openRate)})`"
                    color="bg-blue-500"
                  />
                  <SharedFunnelBar
                    :value="step.clicked"
                    :max="funnelMax"
                    :label="`Clicked (${pct(step.clickRate)})`"
                    color="bg-green-500"
                  />
                  <SharedFunnelBar
                    v-if="step.replied > 0"
                    :value="step.replied"
                    :max="funnelMax"
                    :label="`Replied (${pct(step.replyRate)})`"
                    color="bg-purple-500"
                  />
                  <div v-if="step.skipped + step.failed > 0" class="flex gap-3 text-xs text-muted-foreground pt-1">
                    <span v-if="step.skipped > 0">↷ {{ num(step.skipped) }} skipped</span>
                    <span v-if="step.failed > 0" class="text-destructive">✕ {{ num(step.failed) }} failed</span>
                  </div>
                </template>
              </div>
            </div>
          </template>
        </CardContent>
      </Card>
    </div>

    <!-- ── PIPELINES ── -->
    <div v-if="tab === 'pipelines'" class="space-y-4">
      <div v-if="!pipelineReports?.length" class="text-sm text-muted-foreground">
        No pipelines yet.
        <NuxtLink to="/pipelines" class="text-primary hover:underline">Create one →</NuxtLink>
      </div>
      <Card v-for="p in pipelineReports" :key="p.pipelineId">
        <CardHeader>
          <div class="flex items-start justify-between">
            <div>
              <CardTitle>{{ p.pipelineName }}</CardTitle>
              <CardDescription>{{ num(p.totalDeals) }} deals · {{ money(p.totalValue) }} total value</CardDescription>
            </div>
            <NuxtLink :to="`/pipelines/${p.pipelineId}`" class="text-sm text-primary hover:underline">View board →</NuxtLink>
          </div>
        </CardHeader>
        <CardContent>
          <div v-if="!p.stages.length" class="text-sm text-muted-foreground">No stages configured.</div>
          <div v-else class="space-y-2">
            <div
              v-for="stage in p.stages"
              :key="stage.stageId"
              class="rounded-md border p-3"
            >
              <div class="flex items-center justify-between mb-2">
                <div class="flex items-center gap-2">
                  <span
                    class="w-3 h-3 rounded-full"
                    :style="{ backgroundColor: stage.color ?? '#9ca3af' }"
                  />
                  <span class="font-medium text-sm">{{ stage.stageName }}</span>
                  <Badge v-if="stage.probability !== null" variant="outline" class="text-xs">
                    {{ stage.probability }}% win
                  </Badge>
                </div>
                <span class="text-xs font-mono">
                  {{ num(stage.dealCount) }} deal{{ stage.dealCount === 1 ? '' : 's' }}
                  · {{ money(stage.totalValue) }}
                </span>
              </div>
              <SharedFunnelBar
                :value="stage.dealCount"
                :max="Math.max(1, ...p.stages.map(s => s.dealCount))"
                label="Deals in stage"
              />
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- ── CONTACTS: growth + lifecycle ── -->
    <div v-if="tab === 'contacts'" class="space-y-4">
      <Card>
        <CardHeader>
          <div class="flex items-start justify-between">
            <div>
              <CardTitle>Contact growth</CardTitle>
              <CardDescription>
                {{ num(growth?.totalAdded ?? 0) }} new contacts in the last {{ growth?.windowDays ?? 30 }} days
              </CardDescription>
            </div>
            <div class="flex gap-1">
              <Button
                v-for="d in [7, 30, 90]"
                :key="d"
                size="sm"
                :variant="growthDays === d ? 'default' : 'outline'"
                @click="growthDays = d"
              >{{ d }}d</Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div v-if="!growth || growth.series.length === 0" class="text-sm text-muted-foreground py-4">
            No data yet.
          </div>
          <div v-else>
            <SharedSparkline :points="growthPoints" />
            <div class="flex justify-between mt-2 text-xs text-muted-foreground">
              <span>{{ growth.series.length > 0 ? shortDate(growth.series[0]!.date) : '' }}</span>
              <span>{{ growth.series.length > 0 ? shortDate(growth.series[growth.series.length - 1]!.date) : '' }}</span>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card v-if="growth?.byLifecycle.length">
        <CardHeader>
          <CardTitle>By lifecycle stage</CardTitle>
          <CardDescription>Distribution of all contacts in this workspace.</CardDescription>
        </CardHeader>
        <CardContent class="space-y-2">
          <div v-for="slice in growth.byLifecycle" :key="slice.stage">
            <SharedFunnelBar
              :value="slice.count"
              :max="lifecycleTotal"
              :label="slice.stage"
              :suffix="`(${Math.round((slice.count / lifecycleTotal) * 100)}%)`"
            />
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- ── MAILBOXES ── -->
    <div v-if="tab === 'mailboxes'" class="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Mailbox health</CardTitle>
          <CardDescription>Send volume + bounce rate over the last {{ mailboxHealth?.windowDays ?? 30 }} days.</CardDescription>
        </CardHeader>
        <CardContent>
          <div v-if="!mailboxHealth?.mailboxes.length" class="text-sm text-muted-foreground py-4">
            No mailboxes connected.
            <NuxtLink to="/mailboxes" class="text-primary hover:underline">Connect one →</NuxtLink>
          </div>
          <div v-else class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b text-left text-muted-foreground">
                  <th class="p-2 font-medium">Mailbox</th>
                  <th class="p-2 font-medium">Status</th>
                  <th class="p-2 font-medium text-right">Sent (30d)</th>
                  <th class="p-2 font-medium text-right">Failed</th>
                  <th class="p-2 font-medium text-right">Bounced</th>
                  <th class="p-2 font-medium text-right">Bounce rate</th>
                  <th class="p-2 font-medium text-right">Today</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="mb in mailboxHealth.mailboxes" :key="mb.mailboxId" class="border-b">
                  <td class="p-2">
                    <div class="font-medium">{{ mb.name }}</div>
                    <div class="text-xs text-muted-foreground">{{ mb.email }}</div>
                  </td>
                  <td class="p-2">
                    <Badge
                      :variant="mb.status === 'ACTIVE' ? 'default' : 'outline'"
                      class="text-xs"
                    >{{ mb.status }}</Badge>
                  </td>
                  <td class="p-2 text-right font-mono">{{ num(mb.sent) }}</td>
                  <td class="p-2 text-right font-mono">{{ num(mb.failed) }}</td>
                  <td class="p-2 text-right font-mono">{{ num(mb.bounced) }}</td>
                  <td
                    class="p-2 text-right font-mono"
                    :class="mb.bounceRate > 0.05 ? 'text-destructive font-semibold' : ''"
                  >
                    {{ pct(mb.bounceRate) }}
                  </td>
                  <td class="p-2 text-right text-xs text-muted-foreground">
                    {{ num(mb.sendsToday ?? 0) }}<template v-if="mb.dailyLimit"> / {{ num(mb.dailyLimit) }}</template>
                  </td>
                </tr>
              </tbody>
            </table>
            <p class="text-xs text-muted-foreground mt-3">
              Bounce rates above 5% (red) can hurt deliverability — investigate the mailbox
              or clean the list before sending more.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
