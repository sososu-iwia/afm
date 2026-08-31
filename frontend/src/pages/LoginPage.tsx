import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  InfoCircleOutlined,
  KeyOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { normalizeUserRole, useAuthStore, type UserRole } from '../store/authStore'
import { authApi } from '../api/auth'
import Logo from '../components/Logo'
import LanguageSwitcher from '../components/LanguageSwitcher'

const KZFlag = () => (
  <svg width="22" height="15" viewBox="0 0 22 15" fill="none" style={{ borderRadius: 2, flexShrink: 0 }}>
    <rect width="22" height="15" fill="#00AFCA" />
    <circle cx="11" cy="7.5" r="3" fill="#F0E040" />
    <rect x="3" y="3" width="1.5" height="9" fill="#F0E040" />
  </svg>
)

const GREEN = '#2d6a4f'

const DEMO_USERS: { role: UserRole; phone: string; label: string }[] = [
  { role: 'applicant', phone: '77000000001', label: 'Заявитель' },
  { role: 'chairman', phone: '77000000002', label: 'Председатель' },
  { role: 'member', phone: '77000000003', label: 'Член комиссии' },
  { role: 'secretary', phone: '77000000004', label: 'Секретарь' },
]

const demoPhoneFor = (role: UserRole) =>
  (DEMO_USERS.find((u) => u.role === role)?.phone ?? '').slice(1)

type Mode = 'login' | 'register'
// Вход: 0=телефон, 1=код. Регистрация: 0=имя, 1=телефон, 2=код.
// Роль пользователя приходит в ответе сервера, поэтому выбирать её на входе не нужно.

export default function LoginPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const setSession = useAuthStore((state) => state.setSession)

  const [mode, setMode] = useState<Mode>('login')
  const [page, setPage] = useState(0)
  const [dir, setDir] = useState<1 | -1>(1) // animation direction
  const [animating, setAnimating] = useState(false)

  // Поле пустое: номер вводит пользователь, а демо-номера подставляются
  // из вспомогательного блока под формой.
  const [phone, setPhone] = useState('')
  const [name, setName] = useState('')
  const [otp, setOtp] = useState(['', '', '', '', '', ''])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [devOtp, setDevOtp] = useState('')

  const containerRef = useRef<HTMLDivElement>(null)
  const otpRefs = useRef<Array<HTMLInputElement | null>>([])

  const totalPages = mode === 'login' ? 2 : 3
  const isOtpStep = page === totalPages - 1
  const fullPhone = `+7${phone.replace(/\D/g, '')}`

  const goTo = (next: number, direction: 1 | -1 = 1) => {
    if (animating) return
    setDir(direction)
    setAnimating(true)
    setError('')
    setTimeout(() => {
      setPage(next)
      setAnimating(false)
    }, 220)
  }

  const switchMode = (m: Mode) => {
    setMode(m)
    setPage(0)
    setPhone('')
    setName('')
    setOtp(['', '', '', '', '', ''])
    setError('')
    setDevOtp('')
  }



  /**
   * Сервер объясняет отказ конкретно (формат номера, длина имени),
   * поэтому показываем его сообщение вместо общего «попробуйте позже».
   */
  const describeError = (error: unknown, fallback: string) => {
    const response = (error as { response?: { data?: { message?: string } } })?.response
    const message = response?.data?.message
    return message && message.trim() ? message : fallback
  }

  const handleSendOTP = async () => {
    const digits = phone.replace(/\D/g, '')

    if (!digits) {
      setError(t('login.errors.phoneRequired'))
      return
    }

    if (digits.length !== 10) {
      setError(t('login.errors.phoneFormat'))
      return
    }

    if (mode === 'register' && !name.trim()) {
      setError(t('login.errors.nameRequired'))
      return
    }

    setLoading(true)
    setError('')

    try {
      const res = await authApi.demoLogin(fullPhone)

      const { accessToken, refreshToken, user } = res.data
      const normalizedRole = normalizeUserRole(user.role)

      setSession(
        user.phone,
        normalizedRole,
        accessToken,
        refreshToken
      )

      navigate(
        normalizedRole === 'applicant'
          ? '/applicant'
          : normalizedRole === 'manager'
            ? '/profile'
            : '/commission'
      )
    } catch (error) {
      setError(
        describeError(
          error,
          'Не удалось выполнить демонстрационный вход'
        )
      )
    } finally {
      setLoading(false)
    }
  }

  const handleVerify = async () => {
    const code = otp.join('')
    if (code.length < 6) { setError(t('login.errors.codeRequired')); return }
    setLoading(true)
    setError('')
    try {
      const res = mode === 'login'
        ? await authApi.verifyLogin(fullPhone, code)
        : await authApi.verifyRegister(fullPhone, code)
      const { accessToken, refreshToken, user } = res.data
      const normalizedRole = normalizeUserRole(user.role)
      setSession(user.phone, normalizedRole, accessToken, refreshToken)
      navigate(normalizedRole === 'applicant' ? '/applicant' : normalizedRole === 'manager' ? '/profile' : '/commission')
    } catch (error) {
      setError(describeError(error, t('login.errors.invalidCode')))
    } finally {
      setLoading(false)
    }
  }

  const handleOtpChange = (index: number, value: string) => {
    const digits = value.replace(/\D/g, '')
    if (!digits && value) return
    const next = [...otp]
    if (!digits) {
      next[index] = ''
      setOtp(next)
      return
    }
    digits.slice(0, 6 - index).split('').forEach((digit, offset) => {
      next[index + offset] = digit
    })
    setOtp(next)
    otpRefs.current[Math.min(index + digits.length, 5)]?.focus()
  }

  const handleOtpKey = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !otp[index] && index > 0) otpRefs.current[index - 1]?.focus()
    if (e.key === 'ArrowLeft' && index > 0) otpRefs.current[index - 1]?.focus()
    if (e.key === 'ArrowRight' && index < 5) otpRefs.current[index + 1]?.focus()
    if (e.key === 'Enter') handleVerify()
  }

  const handleOtpPaste = (e: React.ClipboardEvent) => {
    const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6)
    if (text) {
      e.preventDefault()
      const next = ['', '', '', '', '', '']
      text.split('').forEach((digit, index) => { next[index] = digit })
      setOtp(next)
      otpRefs.current[Math.min(text.length, 5)]?.focus()
    }
  }

  const useDevOtp = () => {
    if (!devOtp) return
    setOtp(devOtp.slice(0, 6).split(''))
    setError('')
    otpRefs.current[5]?.focus()
  }

  // Pages for login: 0=choose role & see demo, 1=enter phone, 2=enter OTP
  // Pages for register: 0=enter name, 1=enter phone, 2=enter OTP
  const renderPage = () => {
    if (mode === 'login') {
      if (page === 0) return (
        <div>
          <div style={{ fontSize: 22, fontWeight: 700, color: '#111', marginBottom: 6, letterSpacing: '-0.5px' }}>
            {t('login.signIn')}
          </div>
          <div style={{ fontSize: 14, color: '#999', marginBottom: 24 }}>
            {t('login.signInHint')}
          </div>
          <div style={{ fontSize: 13, fontWeight: 500, color: '#444', marginBottom: 8 }}>{t('login.phone')}</div>
          <div style={{
            display: 'flex', alignItems: 'center', height: 52,
            border: '1.5px solid #e8e8e8', borderRadius: 12,
            overflow: 'hidden', background: '#fff', marginBottom: 20,
          }}>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '0 14px', borderRight: '1.5px solid #f0f0f0',
              background: '#fafafa', height: '100%', flexShrink: 0,
            }}>
              <KZFlag />
              <span style={{ fontWeight: 600, color: '#333', fontSize: 15 }}>+7</span>
            </div>
            <input
              type="tel"
              placeholder="700 000 0000"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') handleSendOTP() }}
              autoFocus
              style={{
                flex: 1, border: 'none', outline: 'none',
                padding: '0 16px', fontSize: 15, color: '#333', background: 'transparent',
              }}
            />
          </div>
          {error && <div style={{ color: '#c0392b', fontSize: 13, marginBottom: 12 }}>{error}</div>}
          <button
            onClick={handleSendOTP}
            disabled={loading}
            style={{
              width: '100%', height: 52, background: GREEN, color: '#fff',
              border: 'none', borderRadius: 12, fontSize: 15, fontWeight: 600,
              cursor: loading ? 'default' : 'pointer', opacity: loading ? 0.7 : 1,
            }}
          >
            {loading ? t('login.sending') : t('login.getSmsCode')}
          </button>

          {/* Демо-номера — вспомогательный блок и только в сборке для разработки. */}
          {import.meta.env.DEV && (
            <details style={{ marginTop: 22 }}>
              <summary style={{
                cursor: 'pointer', fontSize: 13, fontWeight: 500,
                color: '#8a8a8a', listStyle: 'none', userSelect: 'none',
              }}>
                {t('login.demoAccess')}
              </summary>
              <div style={{ fontSize: 12, color: '#aaa', margin: '10px 0 12px', lineHeight: 1.5 }}>
                {t('login.demoHint')}
              </div>
              <div style={{ display: 'grid', gap: 6 }}>
                {DEMO_USERS.map((u) => {
                  const active = phone === demoPhoneFor(u.role)
                  return (
                    <button
                      key={u.role}
                      type="button"
                      onClick={() => setPhone(demoPhoneFor(u.role))}
                      style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        gap: 12, padding: '9px 12px', borderRadius: 8,
                        border: `1px solid ${active ? GREEN : '#ececec'}`,
                        background: active ? '#f3f9f6' : '#fbfbfb',
                        cursor: 'pointer', textAlign: 'left',
                      }}
                    >
                      <span style={{ fontSize: 13, color: active ? GREEN : '#555', fontWeight: active ? 600 : 400 }}>
                        {t(`login.roles.${u.role}`)}
                      </span>
                      <span style={{ fontSize: 12, color: '#999', fontVariantNumeric: 'tabular-nums' }}>
                        +7 {u.phone.slice(1)}
                      </span>
                    </button>
                  )
                })}
              </div>
            </details>
          )}
        </div>
      )

      if (page === 1) return <OtpPage phone={phone} devOtp={devOtp} otp={otp} loading={loading} error={error}
        onChange={handleOtpChange} onKey={handleOtpKey} onPaste={handleOtpPaste} onSubmit={handleVerify}
        onUseDevOtp={useDevOtp} inputRefs={otpRefs} submitLabel={t('login.verify')}
        onResend={async () => { await authApi.login(fullPhone).catch(() => {}); setOtp(['', '', '', '', '', '']) }}
      />
    }

    // Register flow
    if (page === 0) return (
      <div>
        <div style={{ fontSize: 22, fontWeight: 700, color: '#111', marginBottom: 6, letterSpacing: '-0.5px' }}>
          {t('login.yourName')}
        </div>
        <div style={{ fontSize: 14, color: '#999', marginBottom: 24 }}>
          {t('login.nameHint')}
        </div>
        <div style={{ fontSize: 13, fontWeight: 500, color: '#444', marginBottom: 8 }}>{t('login.name')}</div>
        <input
          type="text"
          placeholder={t('login.namePlaceholder')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && name.trim()) goTo(1) }}
          autoFocus
          style={{
            width: '100%', height: 52, borderRadius: 12,
            border: '1.5px solid #e8e8e8', padding: '0 16px',
            fontSize: 15, color: '#333', background: '#fff', marginBottom: 24,
            outline: 'none',
          }}
        />
        {error && <div style={{ color: '#c0392b', fontSize: 13, marginBottom: 12 }}>{error}</div>}
        <button
          onClick={() => { if (!name.trim()) { setError(t('login.errors.nameRequired')); return }; goTo(1) }}
          style={{
            width: '100%', height: 52, background: GREEN, color: '#fff',
            border: 'none', borderRadius: 12, fontSize: 15, fontWeight: 600, cursor: 'pointer',
          }}
        >
          {t('login.continue')} <ArrowRightOutlined />
        </button>
      </div>
    )

    if (page === 1) return (
      <div>
        <div style={{ fontSize: 22, fontWeight: 700, color: '#111', marginBottom: 6, letterSpacing: '-0.5px' }}>
          {t('login.phone')}
        </div>
        <div style={{ fontSize: 14, color: '#999', marginBottom: 24 }}>
          {t('login.phoneRegisterHint')}
        </div>
        <div style={{ fontSize: 13, fontWeight: 500, color: '#444', marginBottom: 8 }}>{t('login.phone')}</div>
        <div style={{
          display: 'flex', alignItems: 'center', height: 52,
          border: '1.5px solid #e8e8e8', borderRadius: 12,
          overflow: 'hidden', marginBottom: 24,
        }}>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 8,
            padding: '0 14px', borderRight: '1.5px solid #f0f0f0',
            background: '#fafafa', height: '100%', flexShrink: 0,
          }}>
            <KZFlag />
            <span style={{ fontWeight: 600, color: '#333', fontSize: 15 }}>+7</span>
          </div>
          <input
            type="tel"
            placeholder="700 000 0000"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSendOTP() }}
            autoFocus
            style={{
              flex: 1, border: 'none', outline: 'none',
              padding: '0 16px', fontSize: 15, color: '#333', background: 'transparent',
            }}
          />
        </div>
        {error && <div style={{ color: '#c0392b', fontSize: 13, marginBottom: 12 }}>{error}</div>}
        <button
          onClick={handleSendOTP}
          disabled={loading}
          style={{
            width: '100%', height: 52, background: GREEN, color: '#fff',
            border: 'none', borderRadius: 12, fontSize: 15, fontWeight: 600,
            cursor: loading ? 'default' : 'pointer', opacity: loading ? 0.7 : 1,
          }}
        >
          {loading ? t('login.sending') : t('login.getSmsCode')}
        </button>
      </div>
    )

    if (page === 2) return <OtpPage phone={phone} devOtp={devOtp} otp={otp} loading={loading} error={error}
      onChange={handleOtpChange} onKey={handleOtpKey} onPaste={handleOtpPaste} onSubmit={handleVerify}
      onUseDevOtp={useDevOtp} inputRefs={otpRefs} submitLabel={t('login.verifyRegister')}
      onResend={async () => { await authApi.register(fullPhone, name).catch(() => {}); setOtp(['', '', '', '', '', '']) }}
    />

    return null
  }

  return (
    <div className="login-page" style={{
      position: 'relative', height: '100vh',
      overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      {/* Background */}
      <img
        src="https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=1400&q=80"
        alt=""
        style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', zIndex: 0 }}
      />
      <div style={{
        position: 'absolute', inset: 0,
        background: 'linear-gradient(160deg, rgba(20,60,35,0.9) 0%, rgba(30,80,50,0.87) 100%)',
        zIndex: 1,
      }} />

      {/* Top bar */}
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, zIndex: 3,
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '20px 32px',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Logo size={36} />
          <div>
            <div style={{ fontSize: 14, fontWeight: 700, color: '#fff', lineHeight: 1.2 }}>Кең дала 2</div>
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.5)' }}>АО «АКК»</div>
          </div>
        </div>
        <LanguageSwitcher variant="light" />
      </div>

      {/* Content */}
      <div className="login-content" style={{
        position: 'relative', zIndex: 2, width: '100%', maxWidth: 1100,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        gap: 48, padding: '80px 20px 40px', flexWrap: 'wrap',
      }}>
        {/* Hero */}
        <div style={{ flex: '1 1 300px', minWidth: 260, maxWidth: 420, color: '#fff' }} className="login-hero">
          <div style={{ fontSize: 36, fontWeight: 800, lineHeight: 1.1, marginBottom: 16, letterSpacing: '-0.5px' }}>
            {t('login.heroTitle')}
          </div>
          <div style={{ fontSize: 15, lineHeight: 1.8, color: 'rgba(255,255,255,0.85)', marginBottom: 28 }}>
            {t('login.heroSub')}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {(t('login.heroItems', { returnObjects: true }) as string[]).map((text) => (
              <div key={text} style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
                <div style={{
                  width: 22, height: 22, borderRadius: 999,
                  background: 'rgba(255,255,255,0.2)',
                  display: 'grid', placeItems: 'center',
                  fontSize: 12, color: '#fff', flexShrink: 0, marginTop: 2,
                }}>✓</div>
                <div style={{ fontSize: 14, color: 'rgba(255,255,255,0.9)', lineHeight: 1.6 }}>{text}</div>
              </div>
            ))}
          </div>
        </div>

        {/* Card */}
        <div className="login-card" style={{ flex: '0 1 420px', minWidth: 300, width: '100%', maxWidth: 420 }}>
          <div className="login-card-shell" style={{
            background: '#fff', borderRadius: 12, overflow: 'hidden',
            boxShadow: '0 24px 80px rgba(0,0,0,0.28)',
          }}>
            {/* Tabs */}
            <div style={{ display: 'flex', borderBottom: '1px solid #f0f0f0' }}>
              {(['login', 'register'] as Mode[]).map((m) => (
                <button
                  key={m}
                  onClick={() => switchMode(m)}
                  style={{
                    flex: 1, height: 48, border: 'none', cursor: 'pointer',
                    background: mode === m ? '#fff' : '#fafafa',
                    color: mode === m ? GREEN : '#aaa',
                    fontWeight: mode === m ? 700 : 500, fontSize: 14,
                    borderBottom: mode === m ? `2px solid ${GREEN}` : '2px solid transparent',
                    transition: 'all 0.15s',
                  }}
                >
                  {m === 'login' ? t('login.tabLogin') : t('login.tabRegister')}
                </button>
              ))}
            </div>

            {/* Page body with slide animation */}
            <div className="login-card-body" style={{ padding: '32px 36px 28px', position: 'relative', overflow: 'hidden' }}>
              {/* Back button */}
              {page > 0 && (
                <button
                  onClick={() => goTo(page - 1, -1)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 6,
                    background: 'none', border: 'none', cursor: 'pointer',
                    color: '#999', fontSize: 13, padding: '0 0 16px',
                  }}
                >
                  <ArrowLeftOutlined /> {t('login.back')}
                </button>
              )}

              <div
                ref={containerRef}
                style={{
                  opacity: animating ? 0 : 1,
                  transform: animating ? `translateX(${dir * 30}px)` : 'translateX(0)',
                  transition: 'opacity 0.2s ease, transform 0.2s ease',
                }}
              >
                {/* Step indicator */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 24 }}>
                  {Array.from({ length: totalPages }).map((_, i) => (
                    <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div style={{
                        width: i === page ? 28 : 22, height: 22, borderRadius: 6,
                        display: 'grid', placeItems: 'center',
                        background: i < page ? GREEN : i === page ? '#f0f7f4' : '#f5f5f5',
                        border: `1.5px solid ${i === page ? GREEN : i < page ? GREEN : '#e8e8e8'}`,
                        fontSize: 11, fontWeight: 700,
                        color: i < page ? '#fff' : i === page ? GREEN : '#bbb',
                        transition: 'all 0.25s',
                      }}>
                        {i < page ? '✓' : i + 1}
                      </div>
                    </div>
                  ))}
                  <span style={{ fontSize: 12, color: '#bbb', marginLeft: 4 }}>
                    {t('login.step', { current: page + 1, total: totalPages })}
                  </span>
                </div>

                {renderPage()}
              </div>
            </div>

            {/* Подсказка про срок действия кода — только там, где код вводят. */}
            {isOtpStep && (
            <div className="login-card-footer" style={{
              margin: '0 36px 28px',
              display: 'flex', alignItems: 'flex-start', gap: 8,
              background: '#f7f9f7', border: '1px solid #e0ede6',
              borderRadius: 10, padding: '10px 14px',
            }}>
              <InfoCircleOutlined style={{ fontSize: 13, color: '#aaa', flexShrink: 0, marginTop: 2 }} />
              <span style={{ fontSize: 12, color: '#aaa', lineHeight: 1.6 }}>
                {t('login.codeHint')}
              </span>
            </div>
            )}
          </div>
        </div>
      </div>

      {/* Footer */}
      <div style={{
        position: 'absolute', bottom: 20, left: 0, right: 0,
        textAlign: 'center', zIndex: 3, fontSize: 11, color: 'rgba(255,255,255,0.25)',
      }}>
        © 2026 АО «Аграрная кредитная корпорация»
      </div>

      <style>{`
        @media (max-width: 640px) {
          .login-page { min-height: 100svh !important; height: auto !important; overflow-x: hidden !important; overflow-y: auto !important; }
          .login-content { box-sizing: border-box; width: 100vw !important; max-width: 100vw !important; padding: 88px 16px 36px !important; align-items: flex-start !important; }
          .login-hero { display: none !important; }
          .login-card { box-sizing: border-box; flex: 0 0 auto !important; width: calc(100vw - 32px) !important; min-width: 0 !important; max-width: none !important; }
          .login-card-shell { border-radius: 10px !important; }
          .login-card-body { padding: 28px 20px 24px !important; }
          .login-card-footer { margin: 0 20px 24px !important; }
          .otp-row { gap: 6px !important; }
          .otp-input { width: calc((100% - 30px) / 6) !important; min-width: 0 !important; height: 52px !important; }
        }
      `}</style>
    </div>
  )
}

function OtpPage({
  phone, devOtp, otp, loading, error,
  onChange, onKey, onPaste, onSubmit, onResend, onUseDevOtp, inputRefs, submitLabel,
}: {
  phone: string
  devOtp: string
  otp: string[]
  loading: boolean
  error: string
  onChange: (i: number, v: string) => void
  onKey: (i: number, e: React.KeyboardEvent) => void
  onPaste: (e: React.ClipboardEvent) => void
  onSubmit: () => void
  onResend: () => void
  onUseDevOtp: () => void
  inputRefs: React.RefObject<Array<HTMLInputElement | null>>
  submitLabel: string
}) {
  const { t } = useTranslation()
  return (
    <div>
      <div style={{ fontSize: 22, fontWeight: 700, color: '#111', marginBottom: 6, letterSpacing: '-0.5px' }}>
        {t('login.smsCode')}
      </div>
      <div style={{ fontSize: 14, color: '#999', marginBottom: 20 }}>
        {t('login.codeSentTo')} <strong style={{ color: '#333' }}>+7 {phone}</strong>
      </div>
      {devOtp && (
        <button type="button" onClick={onUseDevOtp} style={{
          width: '100%', textAlign: 'left', cursor: 'pointer',
          background: '#eef8f1', border: '1px solid #b7dfc1',
          borderRadius: 10, padding: '10px 14px', marginBottom: 16,
          color: '#24613e', fontSize: 13,
        }}>
          <KeyOutlined style={{ marginRight: 6 }} />{t('login.devCode')}: <strong style={{ letterSpacing: 4, fontSize: 15 }}>{devOtp}</strong>
          <span style={{ float: 'right', fontWeight: 600 }}>{t('login.useCode')}</span>
        </button>
      )}
      <div className="otp-row" style={{ display: 'flex', gap: 8, marginBottom: 24, justifyContent: 'space-between' }} onPaste={onPaste}>
        {otp.map((val, i) => (
          <input
            key={i}
            ref={(element) => { inputRefs.current[i] = element }}
            type="tel"
            inputMode="numeric"
            pattern="[0-9]*"
            autoComplete={i === 0 ? 'one-time-code' : 'off'}
            maxLength={1}
            value={val}
            onChange={(e) => onChange(i, e.target.value)}
            onKeyDown={(e) => onKey(i, e)}
            autoFocus={i === 0}
            className="otp-input"
            style={{
              width: 48, height: 56, textAlign: 'center',
              fontSize: 22, fontWeight: 700,
              border: `1.5px solid ${val ? GREEN : '#e0e0e0'}`,
              borderRadius: 12, outline: 'none', color: '#111',
              background: val ? '#f0f7f4' : '#fafafa',
              transition: 'border-color 0.15s, background 0.15s',
              flexShrink: 0,
            }}
          />
        ))}
      </div>
      {error && <div style={{ color: '#c0392b', fontSize: 13, marginBottom: 12 }}>{error}</div>}
      <button
        onClick={onSubmit}
        disabled={loading}
        style={{
          width: '100%', height: 52, background: GREEN, color: '#fff',
          border: 'none', borderRadius: 12, fontSize: 15, fontWeight: 600,
          cursor: loading ? 'default' : 'pointer', opacity: loading ? 0.7 : 1,
          marginBottom: 14,
        }}
      >
        {loading ? t('login.checking') : submitLabel}
      </button>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#999' }}>
        <span>{t('login.noSms')}</span>
        <button onClick={onResend} style={{ background: 'none', border: 'none', color: GREEN, fontWeight: 600, cursor: 'pointer', fontSize: 13 }}>
          {t('login.resend')}
        </button>
      </div>
    </div>
  )
}
