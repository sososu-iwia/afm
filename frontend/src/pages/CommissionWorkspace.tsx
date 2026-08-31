import { useCallback, useEffect, useMemo, useState } from 'react'
import { InputNumber, message, Select, Spin } from 'antd'
import { FilterOutlined, AuditOutlined, ArrowRightOutlined, DownloadOutlined, FileTextOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { applicationApi } from '../api/applications'
import { downloadBlobResponse } from '../api/download'
import { useAuthStore } from '../store/authStore'
import { getDocumentLanguage } from '../i18n/documentLanguage'
import { formatAmount } from '../i18n/format'

type BackendStatus = 'DRAFT' | 'SUBMITTED' | 'WITHDRAWN' | 'IN_REVIEW' | 'ADDITIONAL_DOCUMENTS_REQUESTED' | 'APPROVED' | 'REJECTED'

type CommissionItem = {
  id: string
  applicationNumber: string
  applicantName: string
  status: BackendStatus
  region: string
  productionType: string
  requestedAmount: number
  activityType?: string
  createdAt: string
}

const STATUS_STYLE: Record<BackendStatus, { key: string; bg: string; color: string }> = {
  DRAFT: { key: 'status.draft', bg: '#f2f2f7', color: '#6e6e73' },
  SUBMITTED: { key: 'status.submitted', bg: '#fff8e1', color: '#f59e0b' },
  IN_REVIEW: { key: 'status.review', bg: '#e8f4fd', color: '#0071e3' },
  ADDITIONAL_DOCUMENTS_REQUESTED: { key: 'status.documentsRequested', bg: '#fff3e0', color: '#ff6d00' },
  APPROVED: { key: 'status.approved', bg: '#e8f5e9', color: '#1a7a4a' },
  REJECTED: { key: 'status.rejected', bg: '#ffebee', color: '#d32f2f' },
  WITHDRAWN: { key: 'status.withdrawn', bg: '#f5f5f5', color: '#9e9e9e' },
}

export default function CommissionWorkspace() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const role = useAuthStore((state) => state.role)

  const [applications, setApplications] = useState<CommissionItem[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState<string | undefined>()
  const [regionFilter, setRegionFilter] = useState<string | undefined>()
  const [minAmount, setMinAmount] = useState<number | null>(null)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const canExport = role === 'chairman' || role === 'secretary' || role === 'admin'

  const handleExport = async () => {
    setExporting(true)
    try {
      const docLanguage = getDocumentLanguage()
      const response = await applicationApi.exportCommissionApplications({
        status: statusFilter,
        region: regionFilter,
        minAmount: minAmount ?? undefined,
        language: docLanguage,
      })
      downloadBlobResponse(response, `kendala-applications-${docLanguage}.xlsx`)
      message.success(t('queue.excelDownloaded'))
    } catch {
      message.error(t('queue.excelFailed'))
    } finally {
      setExporting(false)
    }
  }

  const fetchApplications = useCallback(() => {
    applicationApi.getCommissionApplications({ page: 1, size: 100 })
      .then((res) => {
        const data = res.data as { content: CommissionItem[] }
        setApplications(data.content ?? [])
      })
      .catch(() => setLoadError(t('queue.loadFailed')))
      .finally(() => setLoading(false))
  }, [t])

  useEffect(() => { fetchApplications() }, [fetchApplications])

  const retryLoad = () => {
    setLoading(true)
    setLoadError(null)
    fetchApplications()
  }

  const regionOptions = Array.from(new Set(applications.map((a) => a.region))).map((v) => ({ value: v, label: v }))
  const statusOptions = [
    { value: 'SUBMITTED', label: 'Отправлена' },
    { value: 'IN_REVIEW', label: t('queue.inReview') },
    { value: 'APPROVED', label: 'Одобрена' },
    { value: 'REJECTED', label: 'Отклонена' },
    { value: 'ADDITIONAL_DOCUMENTS_REQUESTED', label: 'Нужны документы' },
  ]

  const filtered = useMemo(() => applications.filter((a) => {
    if (statusFilter && a.status !== statusFilter) return false
    if (regionFilter && a.region !== regionFilter) return false
    if (minAmount && a.requestedAmount < minAmount) return false
    return true
  }), [applications, statusFilter, regionFilter, minAmount])

  // Stats
  const submittedCount = applications.filter((a) => a.status === 'SUBMITTED' || a.status === 'IN_REVIEW').length
  const approvedCount = applications.filter((a) => a.status === 'APPROVED').length
  const totalAmount = applications.reduce((s, a) => s + Number(a.requestedAmount), 0)

  return (
    <div style={{ width: '100%' }}>
      {/* Header */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h1 style={{ fontSize: 26, fontWeight: 700, color: 'var(--label-primary)', letterSpacing: '-0.5px', margin: 0 }}>
              <AuditOutlined style={{ marginRight: 10, color: 'var(--accent)' }} />
              {t('commission.title')}
            </h1>
            <p style={{ margin: '4px 0 0', color: 'var(--label-secondary)', fontSize: 14 }}>{t('commission.subtitle')}</p>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            {canExport && (
              <button onClick={handleExport} disabled={exporting} style={toolbarButton(false)}>
                <DownloadOutlined /> {exporting ? t('queue.exporting') : 'Excel'}
              </button>
            )}
            <button onClick={() => setFiltersOpen(!filtersOpen)} style={toolbarButton(filtersOpen)}>
              <FilterOutlined /> {t('commission.filters')}
            </button>
          </div>
        </div>

        {/* Filters */}
        {filtersOpen && (
          <div style={{
            marginTop: 16, padding: '16px 20px',
            background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 12,
            display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center',
          }}>
            <Select allowClear placeholder={t('applicant.colStatus')} style={{ width: 200 }} value={statusFilter} onChange={setStatusFilter} options={statusOptions} />
            <Select allowClear placeholder={t('applicant.colRegion')} style={{ width: 220 }} value={regionFilter} onChange={setRegionFilter} options={regionOptions} />
            <InputNumber min={0} placeholder={t('queue.amountFrom')} addonAfter="₸" value={minAmount} onChange={setMinAmount} style={{ width: 170 }} />
            <button onClick={() => { setStatusFilter(undefined); setRegionFilter(undefined); setMinAmount(null) }}
              style={{ padding: '4px 12px', borderRadius: 8, border: 'none', background: 'transparent', color: 'var(--label-tertiary)', fontSize: 13, cursor: 'pointer' }}>
              {t('queue.reset')}
            </button>
          </div>
        )}
      </div>

      {/* KPI row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 14, marginBottom: 24 }}>
        {[
          { label: t('queue.inReview'), value: submittedCount, color: '#0071e3' },
          { label: t('queue.approvedCount'), value: approvedCount, color: '#1a7a4a' },
          { label: t('queue.totalVolume'), value: `${Math.round(totalAmount / 1_000_000)} млн ₸`, color: 'var(--label-primary)' },
        ].map((k) => (
          <div key={k.label} style={{
            background: 'var(--bg-elevated)', border: '1px solid var(--separator)',
            borderRadius: 8, padding: '18px 22px',
          }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--label-tertiary)', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 6 }}>{k.label}</div>
            <div style={{ fontSize: 28, fontWeight: 800, color: k.color, letterSpacing: '-1px' }}>{k.value}</div>
          </div>
        ))}
      </div>

      {/* Applications table */}
      <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 8, overflowX: 'auto' }}>
        {/* Table header */}
        <div style={{
          display: 'grid', gridTemplateColumns: '140px 1fr 160px 150px 140px 40px',
          minWidth: 760,
          padding: '10px 24px', background: 'var(--bg-secondary)',
          borderBottom: '1px solid var(--separator)',
        }}>
          {[t('applicant.colNumber'), t('queue.regionProduct'), t('commission.applicant'), t('applicant.colAmount'), t('applicant.colStatus'), ''].map((h) => (
            <div key={h} style={{ fontSize: 11, fontWeight: 700, color: 'var(--label-tertiary)', textTransform: 'uppercase', letterSpacing: 0.5 }}>{h}</div>
          ))}
        </div>

        {loading ? (
          <div style={{ padding: 60, textAlign: 'center' }}><Spin /></div>
        ) : loadError ? (
          <div style={{ padding: 60, textAlign: 'center' }}>
            <ExclamationCircleOutlined style={{ fontSize: 30, color: 'var(--red)', marginBottom: 12 }} />
            <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--label-primary)', marginBottom: 6 }}>{t('queue.loadError')}</div>
            <div style={{ fontSize: 14, color: 'var(--label-secondary)', marginBottom: 18 }}>{loadError}</div>
            <button
              onClick={retryLoad}
              style={{
                height: 38, padding: '0 20px', background: 'var(--accent)', color: '#fff',
                border: 'none', borderRadius: 10, fontSize: 14, fontWeight: 600, cursor: 'pointer',
              }}
            >
              {t('common.retry')}
            </button>
          </div>
        ) : filtered.length === 0 ? (
          <div style={{ padding: 60, textAlign: 'center' }}>
            <FileTextOutlined style={{ fontSize: 30, color: 'var(--label-tertiary)', marginBottom: 12 }} />
            <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--label-primary)', marginBottom: 6 }}>{t('queue.noApplications')}</div>
            <div style={{ fontSize: 14, color: 'var(--label-secondary)' }}>{t('queue.appearAfterSubmit')}</div>
          </div>
        ) : (
          filtered.map((app, idx) => {
            const s = STATUS_STYLE[app.status] ?? STATUS_STYLE.SUBMITTED
            return (
              <div
                key={app.id}
                onClick={() => navigate(`/commission/${app.id}`)}
                style={{
                  display: 'grid', gridTemplateColumns: '140px 1fr 160px 150px 140px 40px',
                  minWidth: 760,
                  padding: '16px 24px', alignItems: 'center',
                  borderBottom: idx < filtered.length - 1 ? '1px solid var(--separator)' : 'none',
                  cursor: 'pointer', transition: 'background 0.12s',
                }}
                onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-secondary)')}
                onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
              >
                <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--accent)', fontVariantNumeric: 'tabular-nums' }}>
                  {app.applicationNumber}
                </div>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 500, color: 'var(--label-primary)' }}>{app.region}</div>
                  <div style={{ fontSize: 12, color: 'var(--label-tertiary)', marginTop: 2 }}>{app.productionType}</div>
                </div>
                <div style={{ fontSize: 13, color: 'var(--label-secondary)' }}>{app.applicantName || '—'}</div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--label-primary)', fontVariantNumeric: 'tabular-nums' }}>
                  {formatAmount(app.requestedAmount, i18n.language)} ₸
                </div>
                <div>
                  <span style={{
                    display: 'inline-block', padding: '3px 10px', borderRadius: 999,
                    background: s.bg, color: s.color, fontSize: 12, fontWeight: 600,
                  }}>
                    {t(s.key)}
                  </span>
                </div>
                <div style={{ color: 'var(--label-tertiary)', fontSize: 14 }}>
                  <ArrowRightOutlined />
                </div>
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}

const toolbarButton = (active: boolean): React.CSSProperties => ({
  display: 'flex', alignItems: 'center', gap: 6,
  padding: '8px 16px', borderRadius: 8,
  border: '1px solid var(--separator)',
  background: active ? 'var(--accent-light)' : 'var(--bg-elevated)',
  color: active ? 'var(--accent)' : 'var(--label-secondary)',
  fontSize: 13, fontWeight: 500, cursor: 'pointer',
})
