export type NotificationType =
  | 'TASK_ASSIGNED'
  | 'ENROLLMENT_COMPLETED'
  | 'ENROLLMENT_BOUNCED'
  | 'ENROLLMENT_UNSUBSCRIBED'
  | 'FORM_SUBMITTED'
  | 'SCORE_THRESHOLD'
  | 'SEQUENCE_STEP_SKIPPED'
  | 'DEAL_ASSIGNED'
  | 'CONTACT_REPLIED'
  | 'GENERIC'

export interface NotificationEntity {
  id: string
  type: NotificationType
  title: string
  body: string | null
  linkPath: string | null
  metadata: Record<string, unknown> | null
  readAt: Date | null
  read: boolean
  createdAt: Date
}

export function notificationIcon(type: NotificationType): string {
  switch (type) {
    case 'TASK_ASSIGNED': return '📋'
    case 'ENROLLMENT_COMPLETED': return '✅'
    case 'ENROLLMENT_BOUNCED': return '⚠️'
    case 'ENROLLMENT_UNSUBSCRIBED': return '🚫'
    case 'FORM_SUBMITTED': return '🧾'
    case 'SCORE_THRESHOLD': return '⭐'
    case 'SEQUENCE_STEP_SKIPPED': return '⏭️'
    case 'DEAL_ASSIGNED': return '💼'
    case 'CONTACT_REPLIED': return '↩️'
    default: return '🔔'
  }
}
