import { apiClient } from './client'

export type AppNotification = {
  id: string
  channel: string | null
  eventType: string
  subject: string | null
  body: string
  status: string | null
  createdAt: string
  sentAt: string | null
}

export const notificationApi = {
  mine: () => apiClient.get<AppNotification[]>('/notifications'),
}
