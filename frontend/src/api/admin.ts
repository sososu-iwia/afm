import { apiClient } from './client'
import type { PageResponse } from './applications'
import type { UserRole } from '../store/authStore'

export type AccountStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'BLOCKED' | 'DISABLED'

/** Сервер отдаёт телефон и почту уже маскированными — сырые контакты админке не нужны. */
export type AdminUser = {
  id: string
  phoneMasked: string
  fullName: string
  emailMasked: string | null
  role: string
  accountStatus: AccountStatus
  verified: boolean
  verifiedAt: string | null
  createdAt: string
  updatedAt: string
}

export type AdminSession = {
  id: string
  familyId: string
  issuedAt: string
  expiresAt: string | null
  lastUsedAt: string | null
  revokedAt: string | null
  revokeReason: string | null
  userAgent: string | null
  ipAddress: string | null
}

export const adminApi = {
  listUsers: (params?: { role?: string; status?: string; search?: string; page?: number; size?: number }) =>
    apiClient.get<PageResponse<AdminUser>>('/admin/users', { params }),

  block: (userId: string) => apiClient.patch<AdminUser>(`/admin/users/${userId}/block`),
  unblock: (userId: string) => apiClient.patch<AdminUser>(`/admin/users/${userId}/unblock`),
  disable: (userId: string) => apiClient.patch<AdminUser>(`/admin/users/${userId}/disable`),

  changeRole: (userId: string, role: UserRole | string) =>
    apiClient.patch<AdminUser>(`/admin/users/${userId}/role`, { role }),

  sessions: (userId: string) => apiClient.get<AdminSession[]>(`/admin/users/${userId}/sessions`),
  revokeSessions: (userId: string) => apiClient.delete(`/admin/users/${userId}/sessions`),
}
