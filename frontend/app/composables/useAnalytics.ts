import { useQuery } from '@tanstack/vue-query'

export interface SequenceFunnelStep {
  stepId: string
  stepOrder: number
  stepType: string
  label: string
  sent: number
  opened: number
  clicked: number
  replied: number
  skipped: number
  failed: number
  openRate: number
  clickRate: number
  replyRate: number
}

export interface SequenceFunnel {
  sequenceId: string
  sequenceName: string
  steps: SequenceFunnelStep[]
}

export interface StageBreakdown {
  stageId: string
  stageName: string
  stageOrder: number
  color: string | null
  probability: number | null
  dealCount: number
  totalValue: number
}

export interface PipelineReport {
  pipelineId: string
  pipelineName: string
  totalDeals: number
  totalValue: number
  stages: StageBreakdown[]
}

export interface ContactGrowth {
  windowDays: number
  totalAdded: number
  series: Array<{ date: string; count: number }>
  byLifecycle: Array<{ stage: string; count: number }>
}

export interface MailboxHealthRow {
  mailboxId: string
  email: string
  name: string
  status: string
  dailyLimit: number | null
  sendsToday: number | null
  sent: number
  failed: number
  bounced: number
  bounceRate: number
}

export interface MailboxHealth {
  windowDays: number
  mailboxes: MailboxHealthRow[]
}

export interface DashboardOverview {
  totalContacts: number
  contactsAddedLast7Days: number
  activeSequences: number
  totalEnrollments: number
  emailsSent: number
  emailsOpened: number
  emailsClicked: number
  emailsReplied: number
  openRate: number
  clickRate: number
  replyRate: number
  pendingTasks: number
}

export interface SequenceAnalytics {
  sequenceId: string
  sequenceName: string
  status: string
  totalEnrolled: number
  activeEnrollments: number
  completedEnrollments: number
  unsubscribedEnrollments: number
  emailsSent: number
  emailsOpened: number
  emailsClicked: number
  emailsReplied: number
  openRate: number
  clickRate: number
  replyRate: number
}

export function useDashboardOverview() {
  return useQuery({
    queryKey: ['analytics', 'overview'],
    queryFn: async (): Promise<DashboardOverview> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: DashboardOverview }>('/analytics/overview')
      return res.data
    },
  })
}

export function useSequencePerformance() {
  return useQuery({
    queryKey: ['analytics', 'sequences'],
    queryFn: async (): Promise<SequenceAnalytics[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: SequenceAnalytics[] }>('/analytics/sequences')
      return res.data
    },
  })
}

export function useSequenceFunnel(sequenceId: Ref<string | null>) {
  return useQuery({
    queryKey: ['analytics', 'sequence-funnel', sequenceId] as const,
    queryFn: async (): Promise<SequenceFunnel | null> => {
      if (!sequenceId.value) return null
      const { $api } = useNuxtApp()
      const res = await $api<{ data: SequenceFunnel }>(`/analytics/sequences/${sequenceId.value}/funnel`)
      return res.data
    },
    enabled: computed(() => !!sequenceId.value),
  })
}

export function usePipelineReports() {
  return useQuery({
    queryKey: ['analytics', 'pipelines'],
    queryFn: async (): Promise<PipelineReport[]> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: PipelineReport[] }>('/analytics/pipelines')
      return res.data
    },
  })
}

export function useContactGrowth(days: Ref<number>) {
  return useQuery({
    queryKey: ['analytics', 'contact-growth', days] as const,
    queryFn: async (): Promise<ContactGrowth> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: ContactGrowth }>('/analytics/contact-growth', {
        params: { days: days.value },
      })
      return res.data
    },
  })
}

export function useMailboxHealth() {
  return useQuery({
    queryKey: ['analytics', 'mailboxes'],
    queryFn: async (): Promise<MailboxHealth> => {
      const { $api } = useNuxtApp()
      const res = await $api<{ data: MailboxHealth }>('/analytics/mailboxes')
      return res.data
    },
  })
}
