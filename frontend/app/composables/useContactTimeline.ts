import { useQuery } from '@tanstack/vue-query'

export interface ActivityEvent {
  id: string
  type: string
  title: string
  description: string | null
  sequenceId: string | null
  sequenceName: string | null
  stepExecutionId: string | null
  occurredAt: string
}

export function useContactTimeline(contactId: Ref<string | null>) {
  return useQuery({
    queryKey: ['contact-timeline', contactId] as const,
    queryFn: async (): Promise<ActivityEvent[]> => {
      if (!contactId.value) return []
      const { $api } = useNuxtApp()
      const res = await $api<{ data: ActivityEvent[] }>(`/contacts/${contactId.value}/timeline`)
      return res.data
    },
    enabled: computed(() => !!contactId.value),
  })
}
