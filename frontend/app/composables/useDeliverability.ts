import { useMutation } from '@tanstack/vue-query'
import type { DeliverabilityCheckApiDto } from '~/types/api/deliverability.dto'

/**
 * Check SPF/DKIM/DMARC for a domain.
 * Returns cached result if < 24 hours old; otherwise does a fresh DNS lookup.
 */
export function useDeliverabilityCheck() {
  return useMutation({
    mutationFn: async ({ domain, dkimSelector }: { domain: string; dkimSelector?: string }) => {
      const { $api } = useNuxtApp()
      const params: Record<string, string> = { domain }
      if (dkimSelector) params.dkimSelector = dkimSelector
      const response = await $api<{ data: DeliverabilityCheckApiDto }>('/deliverability/check', { params })
      return response.data
    },
  })
}
