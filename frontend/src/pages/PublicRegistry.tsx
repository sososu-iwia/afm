import { useEffect, useState } from 'react'
import { Alert, Spin, Table, Tag, Space } from 'antd'
import { GlobalOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { applicationApi } from '../api/applications'
import { useAuthStore } from '../store/authStore'
import { formatAmount, formatDate } from '../i18n/format'

type RegistryItem = {
  applicationNumber: string
  region: string
  activityType: string
  productionType: string
  approvedAmount: number
  decisionDate: string
  applicantCategory: string
  scoringCategory: string | null
}

export default function PublicRegistry() {
  const { t, i18n } = useTranslation()
  const [items, setItems] = useState<RegistryItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [page, setPage] = useState(1)
  const [totalItems, setTotalItems] = useState(0)
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)

  useEffect(() => {
    applicationApi.getPublicRegistry({ page, size: 10 })
      .then((res) => {
        const data = res.data as { content: RegistryItem[]; totalElements: number }
        setItems(data.content ?? [])
        setTotalItems(data.totalElements ?? 0)
      })
      .catch(() => setError(t('registryExtra.loadFailed')))
      .finally(() => setLoading(false))
  }, [page, t])

  const columns = [
    { title: '№', dataIndex: 'applicationNumber', width: 130, render: (v: string) => <span style={{ fontWeight: 600, color: 'var(--accent)', fontVariantNumeric: 'tabular-nums' }}>{v}</span> },
    { title: t('registry.region'), dataIndex: 'region' },
    { title: t('registry.product'), dataIndex: 'productionType' },
    {
      title: t('registry.amount'),
      dataIndex: 'approvedAmount',
      render: (v: number) => <span style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 600 }}>{formatAmount(v, i18n.language)} ₸</span>,
    },
    {
      title: t('registry.date'),
      dataIndex: 'decisionDate',
      render: (v: string) => formatDate(v, i18n.language),
    },
    {
      title: t('common.status'),
      render: () => <Tag color="green" style={{ fontSize: 12 }}>{t('status.approved')}</Tag>,
    },
  ]

  const content = (
    <Space direction="vertical" size={24} style={{ width: '100%' }}>
      {/* Header */}
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
          <GlobalOutlined style={{ fontSize: 22, color: 'var(--accent)' }} />
          <h1 style={{ fontSize: 26, fontWeight: 700, color: 'var(--label-primary)', letterSpacing: '-0.5px', margin: 0 }}>
            {t('registry.title')}
          </h1>
        </div>
        <p style={{ margin: 0, color: 'var(--label-secondary)', fontSize: 14 }}>
          {t('registry.subtitle')}
        </p>
      </div>

      {/* KPI row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 14 }}>
        {[
          { label: t('registry.approvedCount'), value: totalItems, color: 'var(--accent)' },
          { label: t('registryExtra.personalData'), value: t('registryExtra.hidden'), color: 'var(--label-primary)' },
          { label: t('registryExtra.publication'), value: t('registryExtra.byDecision'), color: 'var(--label-secondary)' },
        ].map((k) => (
          <div key={k.label} style={{
            background: 'var(--bg-elevated)', border: '1px solid var(--separator)',
            borderRadius: 14, padding: '18px 22px',
          }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--label-tertiary)', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 6 }}>{k.label}</div>
            <div style={{ fontSize: 28, fontWeight: 800, color: k.color, letterSpacing: '-1px' }}>{k.value}</div>
          </div>
        ))}
      </div>

      {/* Table */}
      {error && <Alert type="error" showIcon message={error} />}
      <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 16, overflow: 'hidden' }}>
        <div style={{ padding: '16px 24px', borderBottom: '1px solid var(--separator)' }}>
          <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--label-primary)' }}>
            Одобренные субсидии — открытые данные
          </span>
        </div>
        {loading ? (
          <div style={{ padding: 48, textAlign: 'center' }}><Spin /></div>
        ) : (
          <Table
            rowKey="applicationNumber"
            dataSource={items}
            columns={columns}
            pagination={totalItems > 10 ? {
              current: page,
              pageSize: 10,
              total: totalItems,
              onChange: (nextPage) => { setLoading(true); setPage(nextPage) },
              size: 'small',
            } : false}
            scroll={{ x: 700 }}
            style={{ margin: 0 }}
          />
        )}
      </div>

      <p style={{ color: 'var(--label-tertiary)', fontSize: 12, textAlign: 'center', margin: 0 }}>
        {t('registry.footer')}
      </p>
    </Space>
  )

  if (isAuthenticated) return content
  return <div className="public-registry-page">
    <header className="public-registry-bar"><Link to="/public/registry">Кең дала 2</Link><Link to="/login">Войти в систему</Link></header>
    <main className="public-registry-content">{content}</main>
  </div>
}
