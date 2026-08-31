import { useEffect, useState } from 'react'
import { Input, Spin, message } from 'antd'
import {
  UserOutlined, PhoneOutlined, MailOutlined, SafetyCertificateOutlined,
  GlobalOutlined, LogoutOutlined, CustomerServiceOutlined, CheckCircleFilled,
  CloseCircleFilled, EditOutlined, QuestionCircleOutlined, FileTextOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore, ROLE_LABELS } from '../store/authStore'
import { authApi, type UserProfile } from '../api/auth'
import LanguageSwitcher from '../components/LanguageSwitcher'
import { DOCUMENT_LANGUAGE_KEY } from '../i18n/documentLanguage'
import { SUPPORTED_LANGUAGES, type SupportedLanguage } from '../i18n'

const SUPPORT_PHONE = '+7 7172 55-70-00'
const SUPPORT_EMAIL = 'support@kendala.kz'

/**
 * Зеркало серверных проверок: COMMISSION_READ_ROLES, FINAL_DECISION_ROLES,
 * DOCUMENT_REQUEST_ROLES (они же решают экспорт) и @PreAuthorize на аналитике
 * и публикации в реестре. Список намеренно подробный: иначе член комиссии
 * выглядел бы так же, как заявитель, хотя видит всю очередь, а не свои заявки.
 */
const PERMISSIONS: Record<string, string[]> = {
  applicant: ['permOwnApplications'],
  member: ['permQueue'],
  secretary: ['permQueue', 'permDocs', 'permProtocol', 'permExport', 'permAnalytics'],
  chairman: ['permQueue', 'permDecide', 'permDocs', 'permProtocol', 'permExport', 'permAnalytics', 'permPublish'],
  admin: ['permQueue', 'permDecide', 'permDocs', 'permProtocol', 'permExport', 'permAnalytics', 'permPublish'],
  manager: [],
}
const ALL_PERMISSIONS = [
  'permQueue', 'permDecide', 'permDocs', 'permProtocol', 'permExport', 'permAnalytics', 'permPublish',
] as const

/** Матрица прав осмысленна только для комиссии: заявителю она показывала бы четыре «Нет доступа». */
const COMMITTEE_ROLES = ['chairman', 'member', 'secretary', 'admin', 'manager']
/** Язык документов нужен тем, кто формирует протоколы и выгрузки. */
const DOCUMENT_ROLES = ['chairman', 'secretary', 'admin']

function Section({ icon, title, subtitle, children, action }: {
  icon: React.ReactNode
  title: string
  subtitle?: string
  children: React.ReactNode
  action?: React.ReactNode
}) {
  return (
    <section style={{
      background: 'var(--bg-elevated)',
      border: '1px solid var(--separator)',
      borderRadius: 12,
      overflow: 'hidden',
    }}>
      <header style={{
        display: 'flex', alignItems: 'center', gap: 12,
        padding: '16px 22px', borderBottom: '1px solid var(--separator)',
      }}>
        <span style={{ fontSize: 16, color: 'var(--accent)', display: 'flex' }}>{icon}</span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--label-primary)' }}>{title}</div>
          {subtitle && (
            <div style={{ fontSize: 13, color: 'var(--label-tertiary)', marginTop: 2 }}>{subtitle}</div>
          )}
        </div>
        {action}
      </header>
      <div style={{ padding: '18px 22px' }}>{children}</div>
    </section>
  )
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      gap: 16, padding: '11px 0', borderBottom: '1px solid var(--separator)',
    }}>
      <span style={{ fontSize: 13, color: 'var(--label-secondary)' }}>{label}</span>
      <span style={{ fontSize: 14, fontWeight: 500, color: 'var(--label-primary)', textAlign: 'right' }}>
        {value}
      </span>
    </div>
  )
}

export default function ProfilePage() {
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()
  const storedPhone = useAuthStore((s) => s.phone)
  const role = useAuthStore((s) => s.role)
  const clearSession = useAuthStore((s) => s.clearSession)

  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [errors, setErrors] = useState<{ fullName?: string; email?: string }>({})
  const [docLang, setDocLang] = useState<SupportedLanguage>(
    () => (localStorage.getItem(DOCUMENT_LANGUAGE_KEY) as SupportedLanguage) || 'ru',
  )

  useEffect(() => {
    authApi.me()
      .then((res) => {
        setProfile(res.data)
        setFullName(res.data.fullName ?? '')
        setEmail(res.data.email ?? '')
      })
      .catch(() => message.error(t('common.saveFailed')))
      .finally(() => setLoading(false))
  }, [t])

  const roleLabel = role
    ? (i18n.exists(`login.roles.${role}`) ? t(`login.roles.${role}`) : ROLE_LABELS[role])
    : '—'
  const granted = PERMISSIONS[role ?? 'applicant'] ?? PERMISSIONS.applicant
  const showPermissions = COMMITTEE_ROLES.includes(role ?? 'applicant')
  const showDocumentLanguage = DOCUMENT_ROLES.includes(role ?? 'applicant')

  const startEdit = () => {
    setFullName(profile?.fullName ?? '')
    setEmail(profile?.email ?? '')
    setErrors({})
    setEditing(true)
  }

  const save = async () => {
    const nextErrors: typeof errors = {}
    if (!fullName.trim()) nextErrors.fullName = t('profile.fullNameRequired')
    if (email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      nextErrors.email = t('profile.emailInvalid')
    }
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSaving(true)
    try {
      const res = await authApi.updateMe({ fullName: fullName.trim(), email: email.trim() })
      setProfile(res.data)
      setEditing(false)
      message.success(t('profile.savedProfile'))
    } catch {
      message.error(t('common.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  const pickDocLang = (lng: SupportedLanguage) => {
    setDocLang(lng)
    localStorage.setItem(DOCUMENT_LANGUAGE_KEY, lng)
  }

  const handleLogout = () => { clearSession(); navigate('/login') }

  if (loading) {
    return <div style={{ padding: 60, textAlign: 'center' }}><Spin /></div>
  }

  const ghostButton: React.CSSProperties = {
    display: 'inline-flex', alignItems: 'center', gap: 7,
    height: 34, padding: '0 14px', borderRadius: 9,
    border: '1px solid var(--separator)', background: 'var(--bg-elevated)',
    color: 'var(--label-primary)', fontSize: 13, fontWeight: 500, cursor: 'pointer',
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20, width: '100%' }}>
      {/* Шапка */}
      <div>
        <h1 style={{ fontSize: 26, fontWeight: 700, color: 'var(--label-primary)', letterSpacing: '-0.5px', margin: 0 }}>
          {t('profile.title')}
        </h1>
        <p style={{ margin: '4px 0 0', color: 'var(--label-secondary)', fontSize: 14 }}>
          {t('profile.subtitle')}
        </p>
      </div>

      {/* Личные данные */}
      <Section
        icon={<UserOutlined />}
        title={t('profile.personal')}
        action={!editing ? (
          <button onClick={startEdit} style={ghostButton}>
            <EditOutlined /> {t('common.edit')}
          </button>
        ) : undefined}
      >
        {editing ? (
          <div style={{ display: 'grid', gap: 16, maxWidth: 460 }}>
            <label style={{ display: 'block' }}>
              <span style={{ display: 'block', fontSize: 13, color: 'var(--label-secondary)', marginBottom: 6 }}>
                {t('profile.fullName')}
              </span>
              <Input
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder={t('profile.fullNamePlaceholder')}
                status={errors.fullName ? 'error' : undefined}
                size="large"
              />
              {errors.fullName && (
                <span style={{ fontSize: 12, color: 'var(--red)' }}>{errors.fullName}</span>
              )}
            </label>
            <label style={{ display: 'block' }}>
              <span style={{ display: 'block', fontSize: 13, color: 'var(--label-secondary)', marginBottom: 6 }}>
                {t('profile.email')}
              </span>
              <Input
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder={t('profile.emailPlaceholder')}
                status={errors.email ? 'error' : undefined}
                size="large"
              />
              {errors.email && (
                <span style={{ fontSize: 12, color: 'var(--red)' }}>{errors.email}</span>
              )}
            </label>
            <div style={{ display: 'flex', gap: 10 }}>
              <button
                onClick={save}
                disabled={saving}
                style={{
                  height: 38, padding: '0 20px', borderRadius: 9, border: 'none',
                  background: 'var(--accent)', color: '#fff', fontSize: 14, fontWeight: 600,
                  cursor: saving ? 'default' : 'pointer', opacity: saving ? 0.7 : 1,
                }}
              >
                {saving ? t('common.loading') : t('common.save')}
              </button>
              <button onClick={() => setEditing(false)} style={{ ...ghostButton, height: 38 }}>
                {t('common.cancel')}
              </button>
            </div>
          </div>
        ) : (
          <div>
            <Row label={t('profile.fullName')} value={profile?.fullName || t('profile.notSet')} />
            <Row
              label={t('profile.email')}
              value={profile?.email || <span style={{ color: 'var(--label-tertiary)' }}>{t('profile.emailEmpty')}</span>}
            />
            <Row label={t('profile.role')} value={roleLabel} />
          </div>
        )}
      </Section>

      {/* Вход и безопасность */}
      <Section icon={<SafetyCertificateOutlined />} title={t('profile.security')}>
        <Row
          label={t('profile.phone')}
          value={<span><PhoneOutlined style={{ marginRight: 6, color: 'var(--label-tertiary)' }} />{profile?.phone ?? storedPhone ?? '—'}</span>}
        />
        <Row label={t('profile.auth')} value={t('profile.authValue')} />
        <Row
          label={t('profile.sessionStatus')}
          value={<span style={{ color: 'var(--accent)', fontWeight: 600 }}>
            <CheckCircleFilled style={{ marginRight: 6 }} />{t('profile.sessionActive')}
          </span>}
        />
      </Section>

      {/* Настройки: язык интерфейса и язык документов */}
      <Section icon={<GlobalOutlined />} title={t('profile.settings')}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, flexWrap: 'wrap',
          paddingBottom: showDocumentLanguage ? 14 : 0,
          borderBottom: showDocumentLanguage ? '1px solid var(--separator)' : 'none' }}>
          <div>
            <div style={{ fontSize: 14, color: 'var(--label-primary)' }}>{t('profile.lang')}</div>
          </div>
          <LanguageSwitcher />
        </div>
        {showDocumentLanguage && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, flexWrap: 'wrap', paddingTop: 14 }}>
          <div style={{ maxWidth: 380 }}>
            <div style={{ fontSize: 14, color: 'var(--label-primary)' }}>
              <FileTextOutlined style={{ marginRight: 7, color: 'var(--label-tertiary)' }} />
              {t('profile.docsLang')}
            </div>
            <div style={{ fontSize: 12, color: 'var(--label-tertiary)', marginTop: 3 }}>
              {t('profile.docsLangHint')}
            </div>
          </div>
          <div role="group" style={{
            display: 'inline-flex', padding: 2, gap: 2, borderRadius: 9,
            border: '1px solid var(--separator)', background: 'var(--bg-secondary)',
          }}>
            {SUPPORTED_LANGUAGES.map((lng) => {
              const active = lng === docLang
              return (
                <button
                  key={lng}
                  type="button"
                  onClick={() => pickDocLang(lng)}
                  aria-pressed={active}
                  style={{
                    padding: '5px 11px', borderRadius: 7, border: 'none',
                    cursor: active ? 'default' : 'pointer',
                    fontSize: 12, fontWeight: 600, letterSpacing: 0.3, textTransform: 'uppercase',
                    background: active ? 'var(--bg-elevated)' : 'transparent',
                    color: active ? 'var(--label-primary)' : 'var(--label-secondary)',
                    boxShadow: active ? 'var(--shadow-sm)' : 'none',
                  }}
                >
                  {lng === 'kz' ? 'ҚАЗ' : lng === 'ru' ? 'РУС' : 'ENG'}
                </button>
              )
            })}
          </div>
        </div>
        )}
      </Section>

      {/* Права доступа — только для комиссии */}
      {showPermissions && (
      <Section icon={<SafetyCertificateOutlined />} title={t('profile.permissions')} subtitle={roleLabel}>
        <div style={{ display: 'grid', gap: 2 }}>
          {ALL_PERMISSIONS.map((perm) => {
            const allowed = granted.includes(perm)
            return (
              <div key={perm} style={{
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                padding: '10px 0', borderBottom: '1px solid var(--separator)',
              }}>
                <span style={{ fontSize: 14, color: 'var(--label-primary)' }}>{t(`profile.${perm}`)}</span>
                <span style={{
                  display: 'inline-flex', alignItems: 'center', gap: 6,
                  fontSize: 13, fontWeight: 500,
                  color: allowed ? 'var(--accent)' : 'var(--label-tertiary)',
                }}>
                  {allowed ? <CheckCircleFilled /> : <CloseCircleFilled />}
                  {allowed ? t('profile.allowed') : t('profile.denied')}
                </span>
              </div>
            )
          })}
        </div>
      </Section>
      )}

      {/* Поддержка */}
      <Section icon={<CustomerServiceOutlined />} title={t('profile.support')} subtitle={t('profile.supportSubtitle')}>
        <Row
          label={t('profile.supportPhone')}
          value={<a href={`tel:${SUPPORT_PHONE.replace(/[^+\d]/g, '')}`} style={{ color: 'var(--accent)' }}>{SUPPORT_PHONE}</a>}
        />
        <Row
          label={t('profile.supportEmail')}
          value={<a href={`mailto:${SUPPORT_EMAIL}`} style={{ color: 'var(--accent)' }}>
            <MailOutlined style={{ marginRight: 6 }} />{SUPPORT_EMAIL}
          </a>}
        />
        <Row label={t('profile.supportHours')} value={t('profile.supportHoursValue')} />

        <div style={{ marginTop: 18 }}>
          <div style={{
            fontSize: 12, fontWeight: 600, color: 'var(--label-tertiary)',
            textTransform: 'uppercase', letterSpacing: 0.4, marginBottom: 10,
          }}>
            <QuestionCircleOutlined style={{ marginRight: 6 }} />{t('profile.faq')}
          </div>
          {[
            { q: t('profile.faqProgramQ'), a: t('profile.faqProgramA') },
            { q: t('profile.faqRateQ'), a: t('profile.faqRateA') },
            { q: t('profile.faqChannelsQ'), a: t('profile.faqChannelsA') },
            { q: t('profile.faqDocsQ'), a: t('profile.faqDocsA') },
            { q: t('profile.faqTermQ'), a: t('profile.faqTermA') },
            { q: t('profile.faqRejectQ'), a: t('profile.faqRejectA') },
          ].map((item) => (
            <details key={item.q} style={{ padding: '10px 0', borderBottom: '1px solid var(--separator)' }}>
              <summary style={{ cursor: 'pointer', fontSize: 14, color: 'var(--label-primary)' }}>
                {item.q}
              </summary>
              <p style={{ margin: '8px 0 0', fontSize: 13, color: 'var(--label-secondary)', lineHeight: 1.55 }}>
                {item.a}
              </p>
            </details>
          ))}
        </div>
      </Section>

      {/* Выход */}
      <button
        onClick={handleLogout}
        style={{
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          alignSelf: 'flex-start',
          height: 40, padding: '0 20px', borderRadius: 10,
          border: '1px solid rgba(255,59,48,0.25)', background: 'transparent',
          color: 'var(--red)', fontSize: 14, fontWeight: 600, cursor: 'pointer',
        }}
      >
        <LogoutOutlined /> {t('profile.logout')}
      </button>
    </div>
  )
}
