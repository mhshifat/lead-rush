<script setup lang="ts">
import { Badge } from '~/components/ui/badge'
import { Button } from '~/components/ui/button'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '~/components/ui/select'
import {
  ArrowLeft, Workflow, KanbanSquare, Users, Mail,
  Activity, ArrowRight, AlertCircle,
} from 'lucide-vue-next'

definePageMeta({ middleware: 'auth' })
useHead({ title: 'Analytics' })

type Tab = 'sequences' | 'pipelines' | 'contacts' | 'mailboxes'
const tab = ref<Tab>('sequences')

const TABS: Array<{ key: Tab; label: string; icon: any }> = [
  { key: 'sequences', label: 'Sequences', icon: Workflow },
  { key: 'pipelines', label: 'Pipelines', icon: KanbanSquare },
  { key: 'contacts',  label: 'Contacts',  icon: Users },
  { key: 'mailboxes', label: 'Mailboxes', icon: Mail },
]

const { data: sequences } = useSequencePerformance()
const selectedSequenceId = ref<string | null>(null)
const sequenceIdRef = computed(() => selectedSequenceId.value)
const { data: funnel, isFetching: funnelLoading } = useSequenceFunnel(sequenceIdRef)

const { data: pipelineReports } = usePipelineReports()

const growthDays = ref(30)
const { data: growth } = useContactGrowth(growthDays)

const { data: mailboxHealth } = useMailboxHealth()

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
    style: 'currency', currency: 'USD', maximumFractionDigits: 0,
  }).format(n)
}
function shortDate(iso: string): string {
  const d = new Date(iso + 'T00:00:00')
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' }).format(d)
}

const funnelMax = computed(() => {
  const steps = funnel.value?.steps ?? []
  return Math.max(1, ...steps.map(s => s.sent))
})

const growthPoints = computed(() => growth.value?.series.map(s => s.count) ?? [])
const lifecycleTotal = computed(() => (growth.value?.byLifecycle ?? []).reduce((s, x) => s + x.count, 0))

// Sequence top-line totals shown above the funnel.
const sequenceTotals = computed(() => {
  const steps = funnel.value?.steps ?? []
  const emails = steps.filter(s => s.stepType === 'EMAIL')
  return {
    sent: emails.reduce((a, s) => a + s.sent, 0),
    opened: emails.reduce((a, s) => a + s.opened, 0),
    clicked: emails.reduce((a, s) => a + s.clicked, 0),
    replied: emails.reduce((a, s) => a + s.replied, 0),
  }
})
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Header -->
    <div class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Analytics</h1>
        <p class="text-sm text-muted-foreground mt-0.5">
          Drill into sequences, pipelines, contacts, and mailbox health.
        </p>
      </div>
      <NuxtLink
        to="/dashboard"
        class="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
      >
        <ArrowLeft class="h-3.5 w-3.5" />
        Back to dashboard
      </NuxtLink>
    </div>

    <!-- Tab bar -->
    <div class="glass hairline rounded-xl p-1 inline-flex gap-1">
      <button
        v-for="t in TABS"
        :key="t.key"
        type="button"
        class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors"
        :class="tab === t.key
          ? 'bg-primary/15 text-primary'
          : 'text-muted-foreground hover:text-foreground hover:bg-white/5'"
        @click="tab = t.key"
      >
        <component :is="t.icon" class="h-3.5 w-3.5" />
        {{ t.label }}
      </button>
    </div>

    <!-- ── SEQUENCES: funnel per sequence ── -->
    <div v-if="tab === 'sequences'" class="space-y-4">
      <div class="glass hairline rounded-xl overflow-hidden">
        <div class="px-5 py-4 flex items-start justify-between gap-3" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <div class="flex items-start gap-3 min-w-0">
            <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <Workflow class="h-4 w-4 text-primary" />
            </div>
            <div class="min-w-0">
              <h3 class="text-sm font-semibold tracking-tight">Sequence funnel</h3>
              <p class="text-xs text-muted-foreground mt-0.5">
                Step-by-step engagement. Wide drop-offs signal where leads disengage.
              </p>
            </div>
          </div>
        </div>

        <div class="p-5 space-y-4">
          <div v-if="!sequences?.length" class="text-sm text-muted-foreground py-4 text-center">
            No sequences yet.
            <NuxtLink to="/sequences" class="text-primary hover:underline ml-1">Create one →</NuxtLink>
          </div>
          <template v-else>
            <div class="space-y-2">
              <Label class="text-xs">Sequence</Label>
              <Select
                :model-value="selectedSequenceId ?? undefined"
                @update:model-value="(v) => selectedSequenceId = typeof v === 'string' ? v : null"
              >
                <SelectTrigger class="w-full md:w-96"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="s in sequences" :key="s.sequenceId" :value="s.sequenceId">
                    {{ s.sequenceName }} · {{ s.status }}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>

            <!-- Top-line totals -->
            <div
              v-if="funnel?.steps?.length"
              class="grid grid-cols-2 md:grid-cols-4 hairline rounded-lg overflow-hidden divide-x divide-white/5"
            >
              <div class="p-3">
                <p class="text-xs text-muted-foreground">Sent</p>
                <p class="mt-0.5 text-lg font-semibold tabular-nums">{{ num(sequenceTotals.sent) }}</p>
              </div>
              <div class="p-3">
                <p class="text-xs text-muted-foreground">Opened</p>
                <p class="mt-0.5 text-lg font-semibold tabular-nums text-blue-400">{{ num(sequenceTotals.opened) }}</p>
              </div>
              <div class="p-3">
                <p class="text-xs text-muted-foreground">Clicked</p>
                <p class="mt-0.5 text-lg font-semibold tabular-nums text-emerald-400">{{ num(sequenceTotals.clicked) }}</p>
              </div>
              <div class="p-3">
                <p class="text-xs text-muted-foreground">Replied</p>
                <p class="mt-0.5 text-lg font-semibold tabular-nums text-purple-400">{{ num(sequenceTotals.replied) }}</p>
              </div>
            </div>

            <div v-if="funnelLoading" class="text-sm text-muted-foreground py-4 text-center">Loading funnel…</div>
            <div v-else-if="!funnel?.steps.length" class="text-sm text-muted-foreground py-4 text-center">
              This sequence has no steps yet.
            </div>
            <div v-else class="space-y-2">
              <div
                v-for="step in funnel.steps"
                :key="step.stepId"
                class="rounded-lg hairline p-3 space-y-2 bg-white/2"
              >
                <div class="flex items-center justify-between gap-2">
                  <div class="flex items-center gap-2 min-w-0">
                    <span class="text-xs text-muted-foreground font-mono">#{{ step.stepOrder }}</span>
                    <Badge variant="outline" class="text-xs">{{ step.stepType }}</Badge>
                    <span class="text-sm font-medium truncate">{{ step.label }}</span>
                  </div>
                  <span v-if="step.stepType !== 'EMAIL'" class="text-xs text-muted-foreground font-mono shrink-0">
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
                    color="bg-emerald-500"
                  />
                  <SharedFunnelBar
                    v-if="step.replied > 0"
                    :value="step.replied"
                    :max="funnelMax"
                    :label="`Replied (${pct(step.replyRate)})`"
                    color="bg-purple-500"
                  />
                  <div v-if="step.skipped + step.failed > 0" class="flex gap-3 text-xs pt-1">
                    <span v-if="step.skipped > 0" class="inline-flex items-center gap-1 text-muted-foreground">
                      <ArrowRight class="h-3 w-3" />
                      {{ num(step.skipped) }} skipped
                    </span>
                    <span v-if="step.failed > 0" class="inline-flex items-center gap-1 text-destructive">
                      <AlertCircle class="h-3 w-3" />
                      {{ num(step.failed) }} failed
                    </span>
                  </div>
                </template>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- ── PIPELINES ── -->
    <div v-if="tab === 'pipelines'" class="space-y-4">
      <div
        v-if="!pipelineReports?.length"
        class="glass hairline rounded-xl py-14 px-6 text-center"
      >
        <div class="h-12 w-12 rounded-full bg-white/5 flex items-center justify-center mx-auto mb-4">
          <KanbanSquare class="h-5 w-5 text-muted-foreground" />
        </div>
        <h3 class="text-sm font-semibold tracking-tight">No pipelines yet</h3>
        <p class="text-sm text-muted-foreground mt-1">
          Create a pipeline to start tracking deal-stage analytics.
        </p>
        <NuxtLink to="/pipelines" class="inline-block mt-4 text-sm text-primary hover:underline">
          Create one →
        </NuxtLink>
      </div>

      <div
        v-for="p in pipelineReports"
        :key="p.pipelineId"
        class="glass hairline rounded-xl overflow-hidden"
      >
        <div class="px-5 py-4 flex items-start justify-between gap-3" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <div class="flex items-start gap-3 min-w-0">
            <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <KanbanSquare class="h-4 w-4 text-primary" />
            </div>
            <div class="min-w-0">
              <h3 class="text-sm font-semibold tracking-tight truncate">{{ p.pipelineName }}</h3>
              <p class="text-xs text-muted-foreground mt-0.5">
                <span class="font-mono tabular-nums">{{ num(p.totalDeals) }}</span> deals
                · <span class="font-mono tabular-nums">{{ money(p.totalValue) }}</span> total value
              </p>
            </div>
          </div>
          <NuxtLink
            :to="`/pipelines/${p.pipelineId}`"
            class="inline-flex items-center gap-1 text-sm text-primary hover:underline shrink-0"
          >
            View board
            <ArrowRight class="h-3.5 w-3.5" />
          </NuxtLink>
        </div>

        <div class="p-5">
          <div v-if="!p.stages.length" class="text-sm text-muted-foreground text-center py-4">
            No stages configured.
          </div>
          <div v-else class="space-y-2">
            <div
              v-for="stage in p.stages"
              :key="stage.stageId"
              class="rounded-lg hairline p-3 bg-white/2"
            >
              <div class="flex items-center justify-between mb-2">
                <div class="flex items-center gap-2 min-w-0">
                  <span
                    class="w-2.5 h-2.5 rounded-full shrink-0"
                    :style="{ backgroundColor: stage.color ?? '#9ca3af' }"
                  />
                  <span class="font-medium text-sm truncate">{{ stage.stageName }}</span>
                  <Badge v-if="stage.probability !== null" variant="outline" class="text-xs">
                    {{ stage.probability }}% win
                  </Badge>
                </div>
                <span class="text-xs font-mono tabular-nums text-muted-foreground shrink-0">
                  {{ num(stage.dealCount) }} · {{ money(stage.totalValue) }}
                </span>
              </div>
              <SharedFunnelBar
                :value="stage.dealCount"
                :max="Math.max(1, ...p.stages.map(s => s.dealCount))"
                label="Deals in stage"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ── CONTACTS: growth + lifecycle ── -->
    <div v-if="tab === 'contacts'" class="space-y-4">
      <div class="glass hairline rounded-xl overflow-hidden">
        <div class="px-5 py-4 flex items-start justify-between gap-3" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <div class="flex items-start gap-3 min-w-0">
            <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <Activity class="h-4 w-4 text-primary" />
            </div>
            <div class="min-w-0">
              <h3 class="text-sm font-semibold tracking-tight">Contact growth</h3>
              <p class="text-xs text-muted-foreground mt-0.5">
                <span class="font-semibold tabular-nums text-foreground">{{ num(growth?.totalAdded ?? 0) }}</span>
                new contacts in the last {{ growth?.windowDays ?? 30 }} days
              </p>
            </div>
          </div>
          <div class="flex gap-1 shrink-0">
            <Button
              v-for="d in [7, 30, 90]"
              :key="d"
              size="sm"
              :variant="growthDays === d ? 'default' : 'outline'"
              class="h-7 px-2.5 text-xs"
              @click="growthDays = d"
            >{{ d }}d</Button>
          </div>
        </div>
        <div class="p-5">
          <div v-if="!growth || growth.series.length === 0" class="text-sm text-muted-foreground py-4 text-center">
            No data yet.
          </div>
          <div v-else>
            <SharedSparkline :points="growthPoints" />
            <div class="flex justify-between mt-2 text-xs text-muted-foreground font-mono tabular-nums">
              <span>{{ growth.series.length > 0 ? shortDate(growth.series[0]!.date) : '' }}</span>
              <span>{{ growth.series.length > 0 ? shortDate(growth.series[growth.series.length - 1]!.date) : '' }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="growth?.byLifecycle.length" class="glass hairline rounded-xl overflow-hidden">
        <div class="px-5 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <div class="flex items-start gap-3 min-w-0">
            <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <Users class="h-4 w-4 text-primary" />
            </div>
            <div class="min-w-0">
              <h3 class="text-sm font-semibold tracking-tight">By lifecycle stage</h3>
              <p class="text-xs text-muted-foreground mt-0.5">
                Distribution of all contacts in this workspace.
              </p>
            </div>
          </div>
        </div>
        <div class="p-5 space-y-2">
          <div v-for="slice in growth.byLifecycle" :key="slice.stage">
            <SharedFunnelBar
              :value="slice.count"
              :max="lifecycleTotal"
              :label="slice.stage"
              :suffix="`(${Math.round((slice.count / lifecycleTotal) * 100)}%)`"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- ── MAILBOXES ── -->
    <div v-if="tab === 'mailboxes'" class="space-y-4">
      <div class="glass hairline rounded-xl overflow-hidden">
        <div class="px-5 py-4" style="border-bottom: 1px solid hsl(240 5% 100% / 0.06);">
          <div class="flex items-start gap-3 min-w-0">
            <div class="h-9 w-9 shrink-0 rounded-lg bg-primary/10 flex items-center justify-center">
              <Mail class="h-4 w-4 text-primary" />
            </div>
            <div class="min-w-0">
              <h3 class="text-sm font-semibold tracking-tight">Mailbox health</h3>
              <p class="text-xs text-muted-foreground mt-0.5">
                Send volume + bounce rate over the last {{ mailboxHealth?.windowDays ?? 30 }} days.
              </p>
            </div>
          </div>
        </div>
        <div class="p-5">
          <div v-if="!mailboxHealth?.mailboxes.length" class="text-sm text-muted-foreground py-4 text-center">
            No mailboxes connected.
            <NuxtLink to="/mailboxes" class="text-primary hover:underline ml-1">Connect one →</NuxtLink>
          </div>
          <div v-else class="overflow-x-auto -mx-5">
            <table class="w-full text-sm">
              <thead>
                <tr class="text-left text-xs uppercase tracking-wider text-muted-foreground">
                  <th class="px-5 py-2 font-medium">Mailbox</th>
                  <th class="px-3 py-2 font-medium">Status</th>
                  <th class="px-3 py-2 font-medium text-right">Sent (30d)</th>
                  <th class="px-3 py-2 font-medium text-right">Failed</th>
                  <th class="px-3 py-2 font-medium text-right">Bounced</th>
                  <th class="px-3 py-2 font-medium text-right">Bounce rate</th>
                  <th class="px-5 py-2 font-medium text-right">Today</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="mb in mailboxHealth.mailboxes"
                  :key="mb.mailboxId"
                  style="border-top: 1px solid hsl(240 5% 100% / 0.05);"
                  class="hover:bg-white/2"
                >
                  <td class="px-5 py-3">
                    <div class="font-medium truncate">{{ mb.name }}</div>
                    <div class="text-xs text-muted-foreground font-mono truncate">{{ mb.email }}</div>
                  </td>
                  <td class="px-3 py-3">
                    <Badge
                      :variant="mb.status === 'ACTIVE' ? 'default' : 'outline'"
                      class="text-xs"
                    >{{ mb.status }}</Badge>
                  </td>
                  <td class="px-3 py-3 text-right font-mono tabular-nums">{{ num(mb.sent) }}</td>
                  <td class="px-3 py-3 text-right font-mono tabular-nums">{{ num(mb.failed) }}</td>
                  <td class="px-3 py-3 text-right font-mono tabular-nums">{{ num(mb.bounced) }}</td>
                  <td
                    class="px-3 py-3 text-right font-mono tabular-nums"
                    :class="mb.bounceRate > 0.05 ? 'text-destructive font-semibold' : 'text-muted-foreground'"
                  >
                    {{ pct(mb.bounceRate) }}
                  </td>
                  <td class="px-5 py-3 text-right text-xs text-muted-foreground font-mono tabular-nums">
                    {{ num(mb.sendsToday ?? 0) }}<template v-if="mb.dailyLimit"> / {{ num(mb.dailyLimit) }}</template>
                  </td>
                </tr>
              </tbody>
            </table>
            <p class="text-xs text-muted-foreground mt-4 inline-flex items-center gap-1">
              <AlertCircle class="h-3 w-3" />
              Bounce rates above 5% (red) can hurt deliverability — investigate the mailbox or clean the list before sending more.
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
