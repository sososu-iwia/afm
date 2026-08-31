import { useCallback, useEffect, useState } from 'react'
import { Input, Modal, Select, Spin, message } from 'antd'
import {
  TeamOutlined, StopOutlined, CheckCircleOutlined, SearchOutlined,
  ExclamationCircleOutlined, SafetyCertificateOutlined, LogoutOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { adminApi, type AdminUser, type AdminSession } from '../api/admin'
import { formatDateTime } from '../i18n/format'

const ROLE_VALUES = ['APPLICANT', 'COMMISSION_MEMBER', 'CHAIRMAN', 'SECRETARY', 'MANAGER', 'ADMIN']

/** Только цвета: подписи ролей и статусов берутся из словаря при рендере. */
const STATUS_STYLE: Record<string, { bg: string; color: string }> = {
  ACTIVE: { bg: '#e8f5e9', color: '#1a7a4a' },
  BLOCKED: { bg: '#ffebee', color: '#d32f2f' },
  DISABLED: { bg: '#f5f5f5', color: '#6e6e73' },
  PENDING_VERIFICATION: { bg: '#fff8e1', color: '#8a6100' },
}

export default function AdminUsersPage() {
  const { t, i18n } = useTranslation()
  const roleOptions = ROLE_VALUES.map((value) => ({ value, label: t(`roles.${value}`) }))
  const statusOptions = Object.keys(STATUS_STYLE).map((value) => ({ value, label: t(`accountStatus.${value}`) }))
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<string | undefined>()
  const [statusFilter, setStatusFilter] = useState<string | undefined>()
  const [busyId, setBusyId] = useState<string | null>(null)
  const [sessionsOf, setSessionsOf] = useState<AdminUser | null>(null)
  const [sessions, setSessions] = useState<AdminSession[]>([])
  const [sessionsLoading, setSessionsLoading] = useState(false)

  const fetchUsers = useCallback(() => {
    adminApi.listUsers({
      search: search.trim() || undefined,
      role: roleFilter,
      status: statusFilter,
      page: 1,
      size: 100,
    })
      .then((res) => {
        setUsers(res.data.content ?? [])
        setLoadError(null)
      })
      .catch(() => setLoadError(t('admin.loadFailed')))
      .finally(() => setLoading(false))
  }, [search, roleFilter, statusFilter, t])

  useEffect(() => { fetchUsers() }, [fetchUsers])

  const reload = () => {
    setLoading(true)
    setLoadError(null)
    fetchUsers()
  }

  const runAction = async (user: AdminUser, action: () => Promise<unknown>, done: string) => {
    setBusyId(user.id)
    try {
      await action()
      message.success(done)
      fetchUsers()
    } catch {
      message.error(t('admin.actionFailed'))
    } finally {
      setBusyId(null)
    }
  }

  const confirmAndRun = (
    user: AdminUser,
    title: string,
    content: string,
    action: () => Promise<unknown>,
    done: string,
  ) => {
    Modal.confirm({
      title,
      content,
      okText: t('common.confirm'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      centered: true,
      onOk: () => runAction(user, action, done),
    })
  }

  const openSessions = async (user: AdminUser) => {
    setSessionsOf(user)
    setSessionsLoading(true)
    try {
      const res = await adminApi.sessions(user.id)
      setSessions(res.data ?? [])
    } catch {
      setSessions([])
      message.error(t('admin.sessionsFailed'))
    } finally {
      setSessionsLoading(false)
    }
  }

  const smallButton = (tone: 'default' | 'danger' = 'default'): React.CSSProperties => ({
    display: 'inline-flex', alignItems: 'center', gap: 6,
    padding: '6px 12px', borderRadius: 8, cursor: 'pointer', fontSize: 12, fontWeight: 600,
    border: `1px solid ${tone === 'danger' ? '#ffcdd2' : 'var(--separator)'}`,
    background: tone === 'danger' ? '#fff5f5' : 'var(--bg-elevated)',
    color: tone === 'danger' ? '#d32f2f' : 'var(--label-primary)',
  })

  return (
    <div style={{ width: '100%' }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 26, fontWeight: 700, color: 'var(--label-primary)', letterSpacing: '-0.5px', margin: 0 }}>
          <TeamOutlined style={{ marginRight: 10, color: 'var(--accent)' }} />
          {t('admin.title')}
        </h1>
        <p style={{ margin: '4px 0 0', color: 'var(--label-secondary)', fontSize: 14 }}>
          {t('admin.subtitle')}
        </p>
      </div>

      <div style={{
        display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center', marginBottom: 20,
      }}>
        <Input
          allowClear
          prefix={<SearchOutlined style={{ color: 'var(--label-tertiary)' }} />}
          placeholder={t('admin.searchShort')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ flex: '1 1 240px', maxWidth: 320, minWidth: 180 }}
        />
        <Select
          allowClear placeholder={t('profile.role')} style={{ flex: '0 1 200px', minWidth: 150 }}
          value={roleFilter} onChange={setRoleFilter} options={roleOptions}
        />
        <Select
          allowClear placeholder={t('common.status')} style={{ flex: '0 1 200px', minWidth: 150 }}
          value={statusFilter} onChange={setStatusFilter} options={statusOptions}
        />
      </div>

      <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 12, overflowX: 'auto' }}>
        <div style={{
          display: 'grid', gridTemplateColumns: '1.6fr 1.2fr 1.3fr 1fr 1.6fr',
          minWidth: 900, padding: '11px 22px', background: 'var(--bg-secondary)',
          borderBottom: '1px solid var(--separator)',
        }}>
          {[t('admin.user'), t('profile.phone'), t('profile.role'), t('common.status'), t('common.actions')].map((h) => (
            <div key={h} style={{
              fontSize: 11, fontWeight: 700, color: 'var(--label-tertiary)',
              textTransform: 'uppercase', letterSpacing: 0.5,
            }}>{h}</div>
          ))}
        </div>

        {loading ? (
          <div style={{ padding: 60, textAlign: 'center' }}><Spin /></div>
        ) : loadError ? (
          <div style={{ padding: 60, textAlign: 'center' }}>
            <ExclamationCircleOutlined style={{ fontSize: 28, color: 'var(--red)', marginBottom: 12 }} />
            <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--label-primary)', marginBottom: 14 }}>{loadError}</div>
            <button onClick={reload} style={{
              height: 36, padding: '0 18px', borderRadius: 9, border: 'none',
              background: 'var(--accent)', color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer',
            }}>{t('common.retry')}</button>
          </div>
        ) : users.length === 0 ? (
          <div style={{ padding: 60, textAlign: 'center', color: 'var(--label-secondary)', fontSize: 14 }}>
            {t('admin.notFound')}
          </div>
        ) : (
          users.map((user, index) => {
            const status = STATUS_STYLE[user.accountStatus] ?? STATUS_STYLE.ACTIVE
            const busy = busyId === user.id
            return (
              <div key={user.id} style={{
                display: 'grid', gridTemplateColumns: '1.6fr 1.2fr 1.3fr 1fr 1.6fr',
                minWidth: 900, padding: '14px 22px', alignItems: 'center',
                borderBottom: index < users.length - 1 ? '1px solid var(--separator)' : 'none',
                opacity: busy ? 0.5 : 1,
              }}>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 500, color: 'var(--label-primary)' }}>
                    {user.fullName || '—'}
                  </div>
                  {user.emailMasked && (
                    <div style={{ fontSize: 12, color: 'var(--label-tertiary)', marginTop: 2 }}>{user.emailMasked}</div>
                  )}
                </div>
                <div style={{ fontSize: 13, color: 'var(--label-secondary)', fontVariantNumeric: 'tabular-nums' }}>
                  {user.phoneMasked}
                </div>
                <div>
                  <Select
                    size="small"
                    value={user.role}
                    style={{ width: 175 }}
                    options={roleOptions}
                    disabled={busy}
                    onChange={(role) => confirmAndRun(
                      user,
                      t('admin.roleTitle'),
                      `${user.fullName || t('admin.user')} ${t('admin.willGetRole')} «${t(`roles.${role}`)}» ${t('admin.andRights')}.`,
                      () => adminApi.changeRole(user.id, role),
                      t('admin.roleChanged'),
                    )}
                  />
                </div>
                <div>
                  <span style={{
                    display: 'inline-block', padding: '3px 10px', borderRadius: 999,
                    background: status.bg, color: status.color, fontSize: 12, fontWeight: 600,
                  }}>
                    {t(`accountStatus.${user.accountStatus}`)}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  {user.accountStatus === 'BLOCKED' ? (
                    <button
                      disabled={busy}
                      style={smallButton()}
                      onClick={() => runAction(user, () => adminApi.unblock(user.id), t('admin.unblocked'))}
                    >
                      <CheckCircleOutlined /> {t('admin.unblock')}
                    </button>
                  ) : (
                    <button
                      disabled={busy}
                      style={smallButton('danger')}
                      onClick={() => confirmAndRun(
                        user,
                        t('admin.blockTitle'),
                        t('admin.blockText'),
                        () => adminApi.block(user.id),
                        t('admin.blocked'),
                      )}
                    >
                      <StopOutlined /> {t('admin.block')}
                    </button>
                  )}
                  <button disabled={busy} style={smallButton()} onClick={() => openSessions(user)}>
                    <SafetyCertificateOutlined /> {t('admin.sessions')}
                  </button>
                </div>
              </div>
            )
          })
        )}
      </div>

      <Modal
        open={Boolean(sessionsOf)}
        onCancel={() => setSessionsOf(null)}
        title={`${t('admin.sessionsOf')} — ${sessionsOf?.fullName ?? ''}`}
        centered
        footer={null}
        width={640}
      >
        {sessionsLoading ? (
          <div style={{ padding: 40, textAlign: 'center' }}><Spin /></div>
        ) : sessions.length === 0 ? (
          <div style={{ padding: '24px 0', color: 'var(--label-secondary)', fontSize: 14 }}>
            {t('admin.noSessions')}
          </div>
        ) : (
          <>
            <div style={{ maxHeight: 320, overflowY: 'auto' }}>
              {sessions.map((session) => (
                <div key={session.id} style={{
                  padding: '12px 0', borderBottom: '1px solid var(--separator)',
                }}>
                  <div style={{ fontSize: 13, color: 'var(--label-primary)' }}>
                    {session.userAgent || t('admin.unknownDevice')}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--label-tertiary)', marginTop: 3 }}>
                    {t('admin.signedIn')}: {formatDateTime(session.issuedAt, i18n.language)}
                    {session.revokedAt && ` · ${t('admin.revoked')}`}
                  </div>
                </div>
              ))}
            </div>
            <button
              onClick={() => {
                if (!sessionsOf) return
                confirmAndRun(
                  sessionsOf,
                  t('admin.revokeTitle'),
                  t('admin.revokeText'),
                  () => adminApi.revokeSessions(sessionsOf.id),
                  t('admin.revoked_done'),
                )
                setSessionsOf(null)
              }}
              style={{ ...smallButton('danger'), marginTop: 16, height: 34 }}
            >
              <LogoutOutlined /> {t('admin.revokeAll')}
            </button>
          </>
        )}
      </Modal>
    </div>
  )
}
