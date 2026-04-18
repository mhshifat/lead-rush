export interface ChatConversationSummaryDto {
  id: string
  visitorName: string | null
  visitorEmail: string | null
  contactId: string | null
  status: string
  assignedUserId: string | null
  unreadByTeam: number
  lastMessagePreview: string | null
  lastMessageAt: string | null
  createdAt: string
}

export interface ChatMessageDto {
  id: string
  conversationId: string
  sender: 'VISITOR' | 'AGENT' | 'SYSTEM'
  agentUserId: string | null
  agentName: string | null
  body: string
  createdAt: string
}

export interface ChatConversationDetailDto {
  id: string
  visitorName: string | null
  visitorEmail: string | null
  contactId: string | null
  contactName: string | null
  status: string
  assignedUserId: string | null
  sourceUrl: string | null
  messages: ChatMessageDto[]
  createdAt: string
  closedAt: string | null
}

export interface ChatWidgetConfigDto {
  workspaceSlug: string
  workspaceName: string
  enabled: boolean
  displayName: string
  greeting: string
  offlineMessage: string
  primaryColor: string
  position: 'BOTTOM_RIGHT' | 'BOTTOM_LEFT'
  requireEmail: boolean
}

export interface UpdateWidgetDto {
  enabled?: boolean
  displayName?: string
  greeting?: string
  offlineMessage?: string
  primaryColor?: string
  position?: string
  requireEmail?: boolean
}
