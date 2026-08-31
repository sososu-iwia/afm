import { apiClient } from './client'
import type { PageResponse } from './applications'

export type AuditLog = {
  id: string
  actor: string
  actorName: string | null
  actorRole: string
  action: string
  result: string
  entityType: string
  entityId: string
  source: string
  failureCode: string | null
  occurredAt: string
  ip: string | null
  correlationId: string | null
}

export const auditApi = {
  list: (params?: {
    actor?: string
    action?: string
    entityType?: string
    result?: string
    page?: number
    size?: number
  }) => apiClient.get<PageResponse<AuditLog>>('/admin/audit', { params }),
}
