import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { readStoredAuthSession, useAuthStore, type UserRole } from '../store/authStore'

type RoleRouteProps = {
  allowed: UserRole[]
}

const defaultPathForRole = (role: UserRole) => {
  if (role === 'applicant') return '/applicant'
  if (role === 'manager') return '/profile'
  return '/commission'
}

export default function RoleRoute({ allowed }: RoleRouteProps) {
  const location = useLocation()
  const role = useAuthStore((state) => state.role) ?? readStoredAuthSession()?.role

  if (!role) return <Navigate to="/login" replace state={{ from: location }} />
  return allowed.includes(role) ? <Outlet /> : <Navigate to={defaultPathForRole(role)} replace />
}
