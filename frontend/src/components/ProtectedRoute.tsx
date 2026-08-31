import { Navigate, Outlet } from 'react-router-dom'
import { readStoredAuthSession, useAuthStore } from '../store/authStore'

export default function ProtectedRoute() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated) || Boolean(readStoredAuthSession())

  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />
}
