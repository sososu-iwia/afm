import { create } from 'zustand'

export type UserRole = 'applicant' | 'chairman' | 'member' | 'secretary' | 'admin' | 'manager'
export const normalizeUserRole = (role: string): UserRole => {
  const normalized = role.toLowerCase().replace('commission_', '')
  if (normalized === 'applicant' || normalized === 'chairman' || normalized === 'member'
    || normalized === 'secretary' || normalized === 'admin' || normalized === 'manager') {
    return normalized
  }
  throw new Error(`Unsupported user role: ${role}`)
}

export const ROLE_LABELS: Record<UserRole, string> = {
  applicant: 'Заявитель',
  chairman: 'Председатель комиссии',
  member: 'Член комиссии',
  secretary: 'Секретарь',
  admin: 'Администратор',
  manager: 'Менеджер',
}

type AuthSession = {
  phone: string
  accessToken: string
  refreshToken: string
  role: UserRole
}

export const readStoredAuthSession = (): AuthSession | null => {
  if (typeof window === 'undefined') return null

  const savedSession = window.localStorage.getItem('afm-auth-session')
  if (!savedSession) return null

  try {
    const session = JSON.parse(savedSession) as Partial<AuthSession>
    if (!session.phone || !session.accessToken || !session.refreshToken || !session.role) {
      window.localStorage.removeItem('afm-auth-session')
      return null
    }
    return { ...session, role: normalizeUserRole(session.role) } as AuthSession
  } catch {
    window.localStorage.removeItem('afm-auth-session')
    return null
  }
}

const parsedSession = readStoredAuthSession()

type AuthState = {
  phone: string | null
  accessToken: string | null
  refreshToken: string | null
  role: UserRole | null
  isAuthenticated: boolean
  setSession: (phone: string, role: UserRole, accessToken?: string, refreshToken?: string) => void
  clearSession: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  phone: parsedSession?.phone ?? null,
  accessToken: parsedSession?.accessToken ?? null,
  refreshToken: parsedSession?.refreshToken ?? null,
  role: parsedSession?.role ?? null,
  isAuthenticated: Boolean(parsedSession),
  setSession: (phone, role, accessToken = '', refreshToken = '') => {
    if (!accessToken || !refreshToken) return
    window.localStorage.setItem('afm-auth-session', JSON.stringify({ phone, accessToken, refreshToken, role }))
    set({ phone, accessToken, refreshToken, role, isAuthenticated: true })
  },
  clearSession: () => {
    window.localStorage.removeItem('afm-auth-session')
    set({ phone: null, accessToken: null, refreshToken: null, role: null, isAuthenticated: false })
  },
}))
