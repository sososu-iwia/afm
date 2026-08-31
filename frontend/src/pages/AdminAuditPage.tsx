import { useCallback, useEffect, useState } from 'react'
import { Input, Select, Spin } from 'antd'
import {
  FileProtectOutlined, SearchOutlined, ExclamationCircleOutlined,
  CheckCircleFilled, CloseCircleFilled,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { auditApi, type AuditLog } from '../api/audit'
import { formatDateTime } from '../i18n/format'

/** Типы сущностей, которые проставляет серверный фильтр аудита. */
const ENTITY_VALUES = ['APPLICATION', 'DOCUMENT', 'AUTH', 'APPLICATION_EXPORT', 'HTTP_REQUEST']

export default function AdminAuditPage() {
  const { t, i18n } = useTranslation()
  const [logs, setLogs] = useState<AuditLog[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actor, setActor] = useState('')
  const [entityType, setEntityType] = useState<string | undefined>()

  const fetchLogs = useCallback(() => {
    auditApi.list({
      actor: actor.trim() || undefined,
      entityType,
      page: 1,
      size: 100,
    })
      .then((res) => {
        setLogs(res.data.content ?? [])
        setLoadError(null)
      })
      .catch(() => setLoadError(t('audit.loadFailed')))
      .finally(() => setLoading(false))
  }, [actor, entityType, t])

  useEffect(() => { fetchLogs() }, [fetchLogs])

  const reload = () => {
    setLoading(true)
    setLoadError(null)
    fetchLogs()
  }

  const entityOptions = ENTITY_VALUES.map((value) => ({ value, label: t(`auditEntity.${value}`) }))

  return (
    <div style={{ width: '100%' }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 26, fontWeight: 700, color: 'var(--label-primary)', letterSpacing: '-0.5px', margin: 0 }}>
          <FileProtectOutlined style={{ marginRight: 10, color: 'var(--accent)' }} />
          {t('audit.title')}
        </h1>
        <p style={{ margin: '4px 0 0', color: 'var(--label-secondary)', fontSize: 14 }}>
          {t('audit.subtitle')}
        </p>
      </div>

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center', marginBottom: 20 }}>
        <Input
          allowClear
          prefix={<SearchOutlined style={{ color: 'var(--label-tertiary)' }} />}
          placeholder={t('audit.actorPlaceholder')}
          value={actor}
          onChange={(e) => setActor(e.target.value)}
          style={{ flex: '1 1 240px', maxWidth: 320, minWidth: 180 }}
        />
        <Select
          allowClear placeholder={t('audit.entityType')} style={{ flex: '0 1 230px', minWidth: 160 }}
          value={entityType} onChange={setEntityType} options={entityOptions}
        />
      </div>

      <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 12, overflowX: 'auto' }}>
        <div style={{
          display: 'grid', gridTemplateColumns: '170px 1.1fr 1.7fr 1fr 110px',
          minWidth: 940, padding: '11px 22px', background: 'var(--bg-secondary)',
          borderBottom: '1px solid var(--separator)',
        }}>
          {[t('audit.when'), t('audit.actor'), t('audit.action'), t('audit.entity'), t('audit.result')].map((h) => (
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
        ) : logs.length === 0 ? (
          <div style={{ padding: 60, textAlign: 'center', color: 'var(--label-secondary)', fontSize: 14 }}>
            {t('audit.empty')}
          </div>
        ) : (
          logs.map((log, index) => {
            const success = log.result === 'SUCCESS'
            return (
              <div key={log.id} style={{
                display: 'grid', gridTemplateColumns: '170px 1.1fr 1.7fr 1fr 110px',
                minWidth: 940, padding: '13px 22px', alignItems: 'center',
                borderBottom: index < logs.length - 1 ? '1px solid var(--separator)' : 'none',
              }}>
                <div style={{ fontSize: 12, color: 'var(--label-secondary)', fontVariantNumeric: 'tabular-nums' }}>
                  {formatDateTime(log.occurredAt, i18n.language)}
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13, color: 'var(--label-primary)', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {log.actorName ?? log.actor}
                  </div>
                  {log.actorRole && (
                    <div style={{ fontSize: 11, color: 'var(--label-tertiary)', marginTop: 2 }}>
                      {i18n.exists(`roles.${log.actorRole}`) ? t(`roles.${log.actorRole}`) : log.actorRole}
                    </div>
                  )}
                </div>
                <div style={{
                  fontSize: 12, color: 'var(--label-primary)', fontFamily: 'ui-monospace, monospace',
                  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                }} title={log.action}>
                  {log.action}
                </div>
                <div style={{ fontSize: 12, color: 'var(--label-secondary)' }}>
                  {i18n.exists(`auditEntity.${log.entityType}`) ? t(`auditEntity.${log.entityType}`) : log.entityType}
                </div>
                <div>
                  <span style={{
                    display: 'inline-flex', alignItems: 'center', gap: 5,
                    fontSize: 12, fontWeight: 600,
                    color: success ? 'var(--accent)' : '#d32f2f',
                  }}>
                    {success ? <CheckCircleFilled /> : <CloseCircleFilled />}
                    {success ? t('audit.success') : (log.failureCode ?? t('audit.failure'))}
                  </span>
                </div>
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
