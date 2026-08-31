import axios from 'axios'
import { normalizeUserRole, useAuthStore } from '../store/authStore'

const baseURL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'

export const apiClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

type RefreshResponse = {
  accessToken: string
  refreshToken: string
  user: { phone: string; role: string }
}

let refreshRequest: Promise<string> | null = null

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const request = error.config
    const isAuthRequest = typeof request?.url === 'string' && request.url.startsWith('/auth/')
    if (error.response?.status !== 401 || !request || request._retry || isAuthRequest) {
      return Promise.reject(error)
    }

    const { refreshToken, clearSession, setSession } = useAuthStore.getState()
    if (!refreshToken) {
      clearSession()
      return Promise.reject(error)
    }

    request._retry = true
    refreshRequest ??= axios.post<RefreshResponse>(`${baseURL}/auth/refresh`, { refreshToken })
      .then(({ data }) => {
        setSession(data.user.phone, normalizeUserRole(data.user.role), data.accessToken, data.refreshToken)
        return data.accessToken
      })
      .catch((refreshError) => {
        clearSession()
        throw refreshError
      })
      .finally(() => { refreshRequest = null })

    try {
      const accessToken = await refreshRequest
      request.headers.Authorization = `Bearer ${accessToken}`
      return apiClient(request)
    } catch (refreshError) {
      return Promise.reject(refreshError)
    }
  },
)
