import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Flex, Spin, Typography } from 'antd'
import ProtectedRoute from './components/ProtectedRoute'
import RoleRoute from './components/RoleRoute'
import AppLayout from './layouts/AppLayout'
import type { UserRole } from './store/authStore'

const applicantRoles: UserRole[] = ['applicant']
const commissionRoles: UserRole[] = ['chairman', 'member', 'secretary', 'admin']
const analyticsRoles: UserRole[] = ['chairman', 'secretary', 'admin']
const adminRoles: UserRole[] = ['admin']

const AnalyticsDashboard = lazy(() => import('./pages/AnalyticsDashboard'))
const ApplicantDashboard = lazy(() => import('./pages/ApplicantDashboard'))
const ApplicationFormPage = lazy(() => import('./pages/ApplicationFormPage'))
const ApplicationDetailPage = lazy(() => import('./pages/ApplicationDetailPage'))
const CommissionWorkspace = lazy(() => import('./pages/CommissionWorkspace'))
const CommissionDetailPage = lazy(() => import('./pages/CommissionDetailPage'))
const LoginPage = lazy(() => import('./pages/LoginPage'))
const PublicRegistry = lazy(() => import('./pages/PublicRegistry'))
const ProfilePage = lazy(() => import('./pages/ProfilePage'))
const NotificationsPage = lazy(() => import('./pages/NotificationsPage'))
const AdminUsersPage = lazy(() => import('./pages/AdminUsersPage'))
const AdminAuditPage = lazy(() => import('./pages/AdminAuditPage'))

function App() {
  return (
    <BrowserRouter>
      <Suspense
        fallback={(
          <Flex align="center" justify="center" style={{ minHeight: '100vh', flexDirection: 'column', gap: 12 }}>
            <Spin size="large" />
            <Typography.Text type="secondary">Загружаем интерфейс...</Typography.Text>
          </Flex>
        )}
      >
        <Routes>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/public/registry" element={<PublicRegistry />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route element={<RoleRoute allowed={applicantRoles} />}>
                <Route path="/applicant" element={<ApplicantDashboard />} />
                <Route path="/applicant/new" element={<ApplicationFormPage />} />
                <Route path="/applicant/:id" element={<ApplicationDetailPage />} />
              </Route>
              <Route element={<RoleRoute allowed={commissionRoles} />}>
                <Route path="/commission" element={<CommissionWorkspace />} />
                <Route path="/commission/:id" element={<CommissionDetailPage />} />
              </Route>
              <Route element={<RoleRoute allowed={analyticsRoles} />}>
                <Route path="/analytics" element={<AnalyticsDashboard />} />
              </Route>
              <Route element={<RoleRoute allowed={adminRoles} />}>
                <Route path="/admin/users" element={<AdminUsersPage />} />
                <Route path="/admin/audit" element={<AdminAuditPage />} />
              </Route>
              <Route path="/registry" element={<PublicRegistry />} />
              <Route path="/notifications" element={<NotificationsPage />} />
              <Route path="/profile" element={<ProfilePage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}

export default App
