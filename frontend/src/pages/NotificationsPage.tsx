import { useEffect, useState } from 'react'
import { Spin } from 'antd'
import {
  BellOutlined, MessageOutlined, MailOutlined,
  ExclamationCircleOutlined, CheckCircleFilled, ClockCircleFilled,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { notificationApi, type AppNotification } from '../api/notifications'
import { formatDateTime } from '../i18n/format'

export default function NotificationsPage() {
  const { t, i18n } = useTranslation()
  const [items, setItems] = useState<AppNotification[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    notificationApi.mine()
      .then((res) => {
        setItems(res.data ?? [])
        setLoadError(null)
      })
      .catch(() => setLoadError(t('notifications.loadFailed')))
      .finally(() => setLoading(false))
  }, [t])

  const retry = () => {
    setLoading(true)
    setLoadError(null)
    notificationApi.mine()
      .then((res) => { setItems(res.data ?? []); setLoadError(null) })
      .catch(() => setLoadError(t('notifications.loadFailed')))
      .finally(() => setLoading(false))
  }

  return (
    <div style={{ width: '100%' }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 26, fontWeight: 700, color: 'var(--label-primary)', letterSpacing: '-0.5px', margin: 0 }}>
          <BellOutlined style={{ marginRight: 10, color: 'var(--accent)' }} />
          {t('notifications.title')}
        </h1>
        <p style={{ margin: '4px 0 0', color: 'var(--label-secondary)', fontSize: 14 }}>
          {t('notifications.subtitle')}
        </p>
      </div>

      <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 12 }}>
        {loading ? (
          <div style={{ padding: 60, textAlign: 'center' }}><Spin /></div>
        ) : loadError ? (
          <div style={{ padding: 60, textAlign: 'center' }}>
            <ExclamationCircleOutlined style={{ fontSize: 28, color: 'var(--red)', marginBottom: 12 }} />
            <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--label-primary)', marginBottom: 14 }}>{loadError}</div>
            <button onClick={retry} style={{
              height: 36, padding: '0 18px', borderRadius: 9, border: 'none',
              background: 'var(--accent)', color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer',
            }}>{t('common.retry')}</button>
          </div>
        ) : items.length === 0 ? (
          <div style={{ padding: 60, textAlign: 'center' }}>
            <BellOutlined style={{ fontSize: 30, color: 'var(--label-tertiary)', marginBottom: 12 }} />
            <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--label-primary)', marginBottom: 6 }}>
              {t('notifications.empty')}
            </div>
            <div style={{ fontSize: 14, color: 'var(--label-secondary)' }}>
              {t('notifications.emptyHint')}
            </div>
          </div>
        ) : (
          items.map((item, index) => {
            const delivered = item.status === 'SENT' || item.status === 'DELIVERED'
            return (
              <div key={item.id} style={{
                display: 'flex', gap: 14, padding: '16px 22px', alignItems: 'flex-start',
                borderBottom: index < items.length - 1 ? '1px solid var(--separator)' : 'none',
              }}>
                <span style={{
                  width: 34, height: 34, borderRadius: 9, flexShrink: 0,
                  display: 'grid', placeItems: 'center',
                  background: 'var(--bg-secondary)', color: 'var(--label-secondary)', fontSize: 15,
                }}>
                  {item.channel === 'EMAIL' ? <MailOutlined /> : <MessageOutlined />}
                </span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', gap: 10, alignItems: 'baseline', flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--label-primary)' }}>
                      {i18n.exists(`notificationEvents.${item.eventType}`)
                        ? t(`notificationEvents.${item.eventType}`)
                        : (item.subject || item.eventType)}
                    </span>
                    <span style={{ fontSize: 12, color: 'var(--label-tertiary)' }}>
                      {formatDateTime(item.sentAt ?? item.createdAt, i18n.language)}
                    </span>
                  </div>
                  <div style={{ fontSize: 13, color: 'var(--label-secondary)', marginTop: 5, lineHeight: 1.55 }}>
                    {item.body}
                  </div>
                </div>
                <span style={{
                  display: 'inline-flex', alignItems: 'center', gap: 5, flexShrink: 0,
                  fontSize: 12, fontWeight: 600,
                  color: delivered ? 'var(--accent)' : 'var(--label-tertiary)',
                }}>
                  {delivered ? <CheckCircleFilled /> : <ClockCircleFilled />}
                  {delivered ? t('notifications.sent') : t('notifications.pending')}
                </span>
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
