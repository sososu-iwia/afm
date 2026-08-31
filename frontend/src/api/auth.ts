import { apiClient } from './client'
import type { UserRole } from '../store/authStore'

type AuthResponse = {
  accessToken: string
  refreshToken: string
  user: { id: string; phone: string; fullName: string; role: UserRole | string }
}

export type UserProfile = {
  id: string
  phone: string
  fullName: string
  email: string | null
  role: UserRole
}

export const authApi = {
  register: (phone: string, fullName: string) =>
    apiClient.post('/auth/register', { phone, fullName }),

  login: (phone: string) =>
    apiClient.post('/auth/login', { phone }),

  verifyLogin: (phone: string, code: string) =>
    apiClient.post<AuthResponse>('/auth/verify', { phone, code, purpose: 'LOGIN' }),

  verifyRegister: (phone: string, code: string) =>
    apiClient.post<AuthResponse>('/auth/verify', { phone, code, purpose: 'REGISTRATION' }),

  me: () => apiClient.get<UserProfile>('/auth/me'),

  updateMe: (payload: { fullName: string; email: string }) =>
    apiClient.patch<UserProfile>('/auth/me', payload),

  logout: (refreshToken: string) =>
    apiClient.post('/auth/logout', { refreshToken }),

  refresh: (refreshToken: string) =>
    apiClient.post<AuthResponse>('/auth/refresh', { refreshToken }),
  devOtp: (phone: string) =>
    apiClient.get<{ phone: string; code: string }>(`/dev/otp/${encodeURIComponent(phone)}`),
}
