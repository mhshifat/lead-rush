export interface NotificationApiDto {
  id: string
  type: string
  title: string
  body: string | null
  linkPath: string | null
  metadata: Record<string, unknown> | null
  readAt: string | null
  read: boolean
  createdAt: string
}
