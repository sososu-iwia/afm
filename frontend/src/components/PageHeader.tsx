import type { ReactNode } from 'react'
import { Space, Typography } from 'antd'

type Chip = { label: string; color?: string }

type PageHeaderProps = {
  title: string
  subtitle: string
  actions?: ReactNode
  chips?: Chip[]
  icon?: ReactNode
  accent?: string
}

// Приятные плоские тона для чипов — без оранжевого
const CHIP_PALETTE: Record<string, { bg: string; color: string; border: string }> = {
  green:  { bg: '#e9f5ee', color: '#1f5c3a', border: '#b3d9c2' },
  blue:   { bg: '#e8f1fa', color: '#1a4480', border: '#a3c0e8' },
  teal:   { bg: '#e5f4f3', color: '#1a6060', border: '#8ecfcc' },
  sage:   { bg: '#edf2eb', color: '#3a5c3a', border: '#b3cbb0' },
  violet: { bg: '#f0eaf9', color: '#4a2a7a', border: '#c4a8ea' },
  sky:    { bg: '#e5f0f9', color: '#185278', border: '#90c0e8' },
  // маппим стандартные antd-цвета на нашу палитру
  gold:   { bg: '#edf2eb', color: '#3a5c3a', border: '#b3cbb0' },   // sage вместо gold
  purple: { bg: '#f0eaf9', color: '#4a2a7a', border: '#c4a8ea' },
  cyan:   { bg: '#e5f4f3', color: '#1a6060', border: '#8ecfcc' },
}

const chipStyle = (color = 'green') => CHIP_PALETTE[color] ?? CHIP_PALETTE.green


export default function PageHeader({ title, subtitle, actions, chips = [], icon, accent = '#2d6a4f' }: PageHeaderProps) {
  return (
    <section style={{
      position: 'relative',
      background: '#fff',
      borderRadius: 0,
      border: 'none',
      borderBottom: '1px solid var(--separator)',
      boxShadow: 'none',
      overflow: 'hidden',
      padding: '0 0 24px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: 20,
    }}>
      {/* Основной текстовый блок */}
      <div style={{ position: 'relative', zIndex: 1, minWidth: 0, flex: 1 }}>

        {/* Кикер + иконка страницы */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
          {icon && (
            <div style={{
              width: 28, height: 28,
              borderRadius: 7,
              background: `${accent}18`,
              display: 'grid', placeItems: 'center',
              fontSize: 14,
              color: accent,
              flexShrink: 0,
            }}>
              {icon}
            </div>
          )}
          <Typography.Text style={{
            fontSize: 11,
            fontWeight: 700,
            letterSpacing: 1.4,
            textTransform: 'uppercase',
            color: accent,
            opacity: 0.75,
          }}>
            КЕҢ ДАЛА 2
          </Typography.Text>
        </div>

        <Typography.Title level={2} style={{
          margin: '0 0 5px',
          color: '#122017',
          fontWeight: 700,
          lineHeight: 1.15,
          fontSize: 28,
        }}>
          {title}
        </Typography.Title>

        <Typography.Text style={{
          display: 'block',
          color: '#5a6e62',
          lineHeight: 1.65,
          fontSize: 14,
        }}>
          {subtitle}
        </Typography.Text>

        {chips.length > 0 && (
          <Space wrap size={[8, 8]} style={{ marginTop: 14 }}>
            {chips.map((chip) => {
              const s = chipStyle(chip.color)
              return (
                <span
                  key={chip.label}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    height: 26,
                    padding: '0 12px',
                    borderRadius: 999,
                    background: s.bg,
                    color: s.color,
                    border: `1px solid ${s.border}`,
                    fontSize: 12,
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                  }}
                >
                  {chip.label}
                </span>
              )
            })}
          </Space>
        )}
      </div>

      {/* Кнопки действий */}
      {actions && (
        <div style={{ position: 'relative', zIndex: 1, flexShrink: 0 }}>
          {actions}
        </div>
      )}
    </section>
  )
}
