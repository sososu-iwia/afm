import { useTranslation } from 'react-i18next'
import { SUPPORTED_LANGUAGES, type SupportedLanguage } from '../i18n'
import { changeAppLanguage } from '../i18n/changeLanguage'

type Props = {
  /** 'light' — для тёмной подложки экрана входа, 'default' — для светлого интерфейса. */
  variant?: 'default' | 'light'
  size?: 'sm' | 'md'
}

/**
 * Переключатель трёх языков сегментами. Тумблер на два языка здесь не годится:
 * третий язык в нём просто недостижим.
 */
export default function LanguageSwitcher({ variant = 'default', size = 'md' }: Props) {
  const { t, i18n } = useTranslation()
  const current = (SUPPORTED_LANGUAGES as readonly string[]).includes(i18n.language)
    ? (i18n.language as SupportedLanguage)
    : 'ru'

  const light = variant === 'light'
  const pad = size === 'sm' ? '3px 8px' : '5px 11px'
  const font = size === 'sm' ? 11 : 12

  return (
    <div
      role="group"
      aria-label={t('profile.lang')}
      style={{
        display: 'inline-flex',
        padding: 2,
        gap: 2,
        borderRadius: 9,
        border: `1px solid ${light ? 'rgba(255,255,255,0.25)' : 'var(--separator)'}`,
        background: light ? 'rgba(255,255,255,0.12)' : 'var(--bg-secondary)',
      }}
    >
      {SUPPORTED_LANGUAGES.map((lng) => {
        const active = lng === current
        return (
          <button
            key={lng}
            type="button"
            onClick={() => changeAppLanguage(i18n, lng)}
            aria-pressed={active}
            title={t(`common.languages.${lng}`)}
            style={{
              padding: pad,
              borderRadius: 7,
              border: 'none',
              cursor: active ? 'default' : 'pointer',
              fontSize: font,
              fontWeight: 600,
              letterSpacing: 0.3,
              textTransform: 'uppercase',
              background: active
                ? (light ? 'rgba(255,255,255,0.92)' : 'var(--bg-elevated)')
                : 'transparent',
              color: active
                ? (light ? '#14402c' : 'var(--label-primary)')
                : (light ? 'rgba(255,255,255,0.75)' : 'var(--label-secondary)'),
              boxShadow: active && !light ? 'var(--shadow-sm)' : 'none',
              transition: 'background 0.15s, color 0.15s',
            }}
          >
            {lng === 'kz' ? 'ҚАЗ' : lng === 'ru' ? 'РУС' : 'ENG'}
          </button>
        )
      })}
    </div>
  )
}
