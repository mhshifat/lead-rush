import { toast } from 'vue-sonner'
import type { AppError } from '~/entities/error/error.entity'

/**
 * Central error handler — decides how to display an AppError.
 *
 * System errors   → persistent toast with "Copy Reference ID" button
 * Validation      → handled inline by forms (no toast)
 * Auth errors     → simple toast + redirect to login if 401
 * Business errors → simple toast (5s auto-dismiss)
 */
export function useErrorHandler() {
  function handleError(error: AppError) {
    switch (error.category) {
      case 'SYSTEM':
        // Persistent toast — user needs time to copy the reference ID for support
        toast.error(error.message, {
          description: `Reference: ${error.correlationId ?? '—'}`,
          duration: Infinity,
          action: error.correlationId
            ? {
                label: 'Copy ID',
                onClick: () => {
                  if (error.correlationId) {
                    navigator.clipboard.writeText(error.correlationId)
                    toast.success('Reference ID copied')
                  }
                },
              }
            : undefined,
        })
        break

      case 'AUTH':
        toast.error(error.message)
        // Redirect unauthenticated users to login
        if (import.meta.client && typeof window !== 'undefined') {
          const authStore = useAuthStore()
          if (authStore.isLoggedIn) {
            authStore.logout()
          }
        }
        break

      case 'VALIDATION':
        // Don't show toast — the calling form will display field errors inline
        break

      case 'ACTIVATION_REQUIRED':
        // The login page handles this — don't show toast (it would double-notify)
        break

      case 'BUSINESS':
      default:
        toast.error(error.message, { duration: 5000 })
    }
  }

  return { handleError }
}
