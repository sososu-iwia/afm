import { useEffect, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Tooltip, Typography } from 'antd'
import {
  BarChartOutlined, FileTextOutlined, GlobalOutlined,
  LogoutOutlined, TeamOutlined, UserOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined, CloseOutlined, MenuOutlined, SettingOutlined, FileProtectOutlined, BellOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { readStoredAuthSession, useAuthStore, ROLE_LABELS } from '../store/authStore'
import Logo from '../components/Logo'
import LanguageSwitcher from '../components/LanguageSwitcher'
import { changeAppLanguage } from '../i18n/changeLanguage'
import { SUPPORTED_LANGUAGES, type SupportedLanguage } from '../i18n'

const ROLE_COLORS: Record<string, string> = {
  applicant: '#1a7a4a',
  chairman: '#0071e3',
  member: '#7d3ce5',
  secretary: '#d46b08',
  admin: '#5e5ce6',
  manager: '#6e6e73',
}

export default function AppLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const phone = useAuthStore((s) => s.phone) ?? readStoredAuthSession()?.phone ?? null
  const role = useAuthStore((s) => s.role)
  const clearSession = useAuthStore((s) => s.clearSession)
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const { t, i18n } = useTranslation()
  const isApplicant = role === 'applicant'
  const canUseCommission = role === 'chairman' || role === 'member' || role === 'secretary' || role === 'admin'
  const canViewAnalytics = role === 'chairman' || role === 'secretary' || role === 'admin'
  const isAdmin = role === 'admin'

  // Close on resize to desktop
  useEffect(() => {
    const handler = () => { if (window.innerWidth >= 768) setMobileOpen(false) }
    window.addEventListener('resize', handler)
    return () => window.removeEventListener('resize', handler)
  }, [])

  // В свёрнутом меню места на сегменты нет — там язык переключается по кругу.
  const cycleLanguage = () => {
    const index = SUPPORTED_LANGUAGES.indexOf(i18n.language as SupportedLanguage)
    const next = SUPPORTED_LANGUAGES[(index + 1) % SUPPORTED_LANGUAGES.length]
    changeAppLanguage(i18n, next)
  }

  const handleLogout = () => { clearSession(); navigate('/login') }

  const navItems = [
    ...(isApplicant ? [{ path: '/applicant', icon: <FileTextOutlined />, label: t('nav.myApplications') }] : []),
    ...(canUseCommission ? [{ path: '/commission', icon: <TeamOutlined />, label: t('nav.commission') }] : []),
    ...(canViewAnalytics ? [{ path: '/analytics', icon: <BarChartOutlined />, label: t('nav.analytics') }] : []),
    ...(isAdmin ? [
      { path: '/admin/users', icon: <SettingOutlined />, label: t('nav.users') },
      { path: '/admin/audit', icon: <FileProtectOutlined />, label: t('nav.audit') },
    ] : []),
    { path: '/notifications', icon: <BellOutlined />, label: t('nav.notifications') },
    { path: '/registry', icon: <GlobalOutlined />, label: t('nav.publicRegistry') },
    { path: '/profile', icon: <UserOutlined />, label: t('nav.profile') },
  ]

  const roleColor = ROLE_COLORS[role ?? 'applicant'] ?? '#1a7a4a'
  // login.roles покрывает четыре демо-роли; для admin/manager остаётся русская подпись.
  const roleLabel = role
    ? (i18n.exists(`login.roles.${role}`) ? t(`login.roles.${role}`) : ROLE_LABELS[role])
    : ''
  const initials = phone ? phone.slice(-4) : 'KD'
  const w = collapsed ? 64 : 240

  const renderSidebarContent = (isMobile = false) => (
    <>
      {/* Logo */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 10,
        padding: collapsed && !isMobile ? '18px 14px' : '18px 20px',
        borderBottom: '1px solid var(--separator)', height: 64,
        overflow: 'hidden', flexShrink: 0,
        justifyContent: collapsed && !isMobile ? 'center' : 'flex-start',
      }}>
        <Logo size={32} />
        {(!collapsed || isMobile) && (
          <div style={{ overflow: 'hidden' }}>
            <Typography.Text style={{ display: 'block', fontWeight: 700, color: 'var(--label-primary)', fontSize: 14, lineHeight: 1.2 }}>
              Кең дала 2
            </Typography.Text>
            <Typography.Text style={{ display: 'block', fontSize: 11, color: 'var(--label-tertiary)', lineHeight: 1.3 }}>
              АКК
            </Typography.Text>
          </div>
        )}
        {isMobile && (
          <button onClick={() => setMobileOpen(false)} style={{
            marginLeft: 'auto', background: 'none', border: 'none',
            color: 'var(--label-tertiary)', cursor: 'pointer', fontSize: 18, padding: 4,
          }}>
            <CloseOutlined />
          </button>
        )}
      </div>

      {/* User */}
      {(!collapsed || isMobile) && (
        <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--separator)', flexShrink: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Avatar size={34} style={{ background: roleColor, flexShrink: 0, fontSize: 12, fontWeight: 600 }}>
              {initials}
            </Avatar>
            <div style={{ minWidth: 0 }}>
              <Typography.Text style={{ display: 'block', fontWeight: 600, fontSize: 13, color: 'var(--label-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {phone ?? '—'}
              </Typography.Text>
              <span style={{
                display: 'inline-block', marginTop: 2,
                fontSize: 11, fontWeight: 500,
                color: roleColor, background: `${roleColor}15`,
                borderRadius: 4, padding: '1px 6px',
              }}>
                {roleLabel}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Nav */}
      <nav style={{ flex: 1, padding: '10px 8px', display: 'flex', flexDirection: 'column', gap: 2, overflowY: 'auto' }}>
        {navItems.map((item) => {
          const active = location.pathname === item.path
          return (
            <Tooltip key={item.path} title={collapsed && !isMobile ? item.label : ''} placement="right">
              <Link
                to={item.path}
                onClick={() => setMobileOpen(false)}
                style={{
                  display: 'flex', alignItems: 'center',
                  gap: 10,
                  padding: collapsed && !isMobile ? '10px 0' : '11px 12px',
                  borderRadius: 10,
                  color: active ? 'var(--accent)' : 'var(--label-secondary)',
                  background: active ? 'var(--accent-light)' : 'transparent',
                  fontWeight: active ? 600 : 400,
                  fontSize: 14, textDecoration: 'none',
                  transition: 'all 0.15s',
                  justifyContent: collapsed && !isMobile ? 'center' : 'flex-start',
                }}
                onMouseEnter={(e) => { if (!active) (e.currentTarget as HTMLElement).style.background = 'var(--bg-secondary)' }}
                onMouseLeave={(e) => { if (!active) (e.currentTarget as HTMLElement).style.background = 'transparent' }}
              >
                <span style={{ fontSize: 17, flexShrink: 0 }}>{item.icon}</span>
                {(!collapsed || isMobile) && <span>{item.label}</span>}
              </Link>
            </Tooltip>
          )
        })}
      </nav>

      {/* Bottom */}
      <div style={{ padding: '10px 8px 16px', borderTop: '1px solid var(--separator)', display: 'flex', flexDirection: 'column', gap: 2, flexShrink: 0 }}>
        {collapsed && !isMobile ? (
          <Tooltip title={t('profile.lang')} placement="right">
            <button onClick={cycleLanguage} style={{
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              padding: '9px 0', borderRadius: 10, border: 'none', background: 'transparent',
              color: 'var(--label-secondary)', fontSize: 14, cursor: 'pointer', width: '100%',
            }}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-secondary)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
            >
              <GlobalOutlined style={{ fontSize: 16 }} />
            </button>
          </Tooltip>
        ) : (
          <div style={{ padding: '6px 12px 8px' }}>
            <div style={{
              fontSize: 11, fontWeight: 600, color: 'var(--label-tertiary)',
              textTransform: 'uppercase', letterSpacing: 0.4, marginBottom: 6,
            }}>
              {t('profile.lang')}
            </div>
            <LanguageSwitcher size="sm" />
          </div>
        )}

        <button onClick={handleLogout} style={{
          display: 'flex', alignItems: 'center', gap: 10,
          padding: collapsed && !isMobile ? '9px 0' : '9px 12px',
          borderRadius: 10, border: 'none', background: 'transparent',
          color: 'var(--red)', fontSize: 14, cursor: 'pointer',
          width: '100%', justifyContent: collapsed && !isMobile ? 'center' : 'flex-start',
        }}
          onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255,59,48,0.06)')}
          onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
        >
          <LogoutOutlined style={{ fontSize: 16 }} />
          {(!collapsed || isMobile) && <span>{t('nav.logout')}</span>}
        </button>

        {!isMobile && (
          <button onClick={() => setCollapsed(!collapsed)} style={{
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            padding: 6, marginTop: 4,
            borderRadius: 8, border: 'none', background: 'transparent',
            color: 'var(--label-tertiary)', fontSize: 14, cursor: 'pointer',
            width: '100%',
          }}
            onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-secondary)')}
            onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
          >
            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          </button>
        )}
      </div>
    </>
  )

  return (
    <div className="mac-shell" style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg)' }}>
      {/* Desktop sidebar */}
      <aside className="desktop-sidebar mac-sidebar" style={{
        width: w, minWidth: w, maxWidth: w,
          background: 'rgba(250,250,252,0.88)',
        backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)',
        borderRight: '1px solid var(--separator)',
        display: 'flex', flexDirection: 'column',
        position: 'sticky', top: 0, height: '100vh',
        overflow: 'hidden',
        transition: 'width 0.25s cubic-bezier(0.4,0,0.2,1)',
        zIndex: 100,
      }}>
        {renderSidebarContent()}
      </aside>

      {/* Mobile overlay */}
      {mobileOpen && (
        <div
          onClick={() => setMobileOpen(false)}
          style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
            zIndex: 200, backdropFilter: 'blur(2px)',
          }}
        />
      )}

      {/* Mobile drawer */}
      <aside className="mobile-sidebar" style={{
        position: 'fixed', top: 0, left: 0, bottom: 0,
        width: 280, zIndex: 201,
        background: '#fff',
        borderRight: '1px solid var(--separator)',
        display: 'flex', flexDirection: 'column',
        transform: mobileOpen ? 'translateX(0)' : 'translateX(-100%)',
        transition: 'transform 0.28s cubic-bezier(0.4,0,0.2,1)',
        boxShadow: mobileOpen ? '4px 0 32px rgba(0,0,0,0.15)' : 'none',
      }}>
        {renderSidebarContent(true)}
      </aside>

      {/* Main */}
      <main className="mac-main" style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        {/* Mobile top bar */}
        <div className="mobile-topbar" style={{
          display: 'flex', alignItems: 'center', gap: 12,
          padding: '0 16px', height: 56,
          borderBottom: '1px solid var(--separator)',
          background: 'rgba(255,255,255,0.9)',
          backdropFilter: 'blur(12px)',
          position: 'sticky', top: 0, zIndex: 50,
        }}>
          <button onClick={() => setMobileOpen(true)} style={{
            background: 'none', border: 'none', cursor: 'pointer',
            color: 'var(--label-primary)', fontSize: 20, padding: 4, display: 'flex', alignItems: 'center',
          }}>
            <MenuOutlined />
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Logo size={26} />
            <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--label-primary)' }}>Кең дала 2</span>
          </div>
        </div>

        <div className="app-content" style={{ flex: 1 }}>
          <Outlet />
        </div>
      </main>

      <style>{`
        @media (min-width: 768px) {
          .mobile-sidebar { display: none !important; }
          .mobile-topbar { display: none !important; }
        }
        @media (max-width: 767px) {
          .desktop-sidebar { display: none !important; }
        }
      `}</style>
    </div>
  )
}
