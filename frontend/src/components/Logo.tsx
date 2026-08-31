type LogoProps = {
  size?: number
  /** Скруглённая подложка. Выключается, когда знак ставится на цветной фон. */
  withBackdrop?: boolean
  title?: string
}

/**
 * Фирменный знак: росток над возделанным полем.
 * Векторный, поэтому одинаково чёткий и в боковом меню (28px), и на экране входа (64px).
 */
export default function Logo({ size = 32, withBackdrop = true, title = 'Кең дала 2' }: LogoProps) {
  const uid = `logo-${size}-${withBackdrop ? 'b' : 'p'}`
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      role="img"
      aria-label={title}
      style={{ display: 'block', flexShrink: 0 }}
    >
      <defs>
        <linearGradient id={`${uid}-bg`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#1d5138" />
          <stop offset="100%" stopColor="#0d2a1c" />
        </linearGradient>
        <linearGradient id={`${uid}-leaf`} x1="0.2" y1="0" x2="0.9" y2="1">
          <stop offset="0%" stopColor="#f2fbf2" />
          <stop offset="55%" stopColor="#cfe9bd" />
          <stop offset="100%" stopColor="#8fc47a" />
        </linearGradient>
        <linearGradient id={`${uid}-field`} x1="0" y1="0" x2="1" y2="0.6">
          <stop offset="0%" stopColor="#eaf6e4" />
          <stop offset="50%" stopColor="#a8d38e" />
          <stop offset="100%" stopColor="#5aa15f" />
        </linearGradient>
      </defs>

      {withBackdrop && <rect width="64" height="64" rx="15" fill={`url(#${uid}-bg)`} />}

      {/* Поле: три борозды, сходящиеся к горизонту */}
      <g fill="none" stroke={`url(#${uid}-field)`} strokeLinecap="round">
        <path d="M13 49c6-11 17-16 30-15.5" strokeWidth="5.4" />
        <path d="M17.5 51.5c6.5-8.5 15-12.5 26.5-12.6" strokeWidth="4" opacity="0.9" />
        <path d="M23 53.5c5.5-5.6 12-8.4 21-8.6" strokeWidth="2.9" opacity="0.75" />
      </g>

      {/* Росток: крупный лист вправо, малый влево, стебель */}
      <path
        d="M32.6 32.2c0-8.6 5.4-15.2 14.2-17.2 1 9.4-3.6 16.4-14.2 17.2z"
        fill={`url(#${uid}-leaf)`}
      />
      <path
        d="M30.4 32.4c-5.6-1.4-9-5.6-9.6-12 6 1 9.4 4.9 9.6 12z"
        fill={`url(#${uid}-leaf)`}
        opacity="0.92"
      />
      <path
        d="M31.6 33.6c0-4.6 2-9.2 6-13"
        fill="none"
        stroke={`url(#${uid}-leaf)`}
        strokeWidth="2.1"
        strokeLinecap="round"
        opacity="0.85"
      />
    </svg>
  )
}
