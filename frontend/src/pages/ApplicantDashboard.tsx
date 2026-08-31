import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Modal, Space, message } from 'antd'
import { PlusOutlined, DeleteOutlined, FileTextOutlined, CheckCircleFilled, ClockCircleFilled, ExclamationCircleFilled, ArrowRightOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { applicationApi, type BackendApplication } from '../api/applications'
import type { Application } from '../types/application'
import { formatAmount, formatDate } from '../i18n/format'


const STATUS_CONFIG: Record<string, { bg: string; color: string; icon: React.ReactNode }> = {
  draft:             { bg: '#f2f2f7', color: '#6e6e73', icon: <FileTextOutlined /> },
  submitted:         { bg: '#fff3e0', color: '#f57c00', icon: <ClockCircleFilled /> },
  review:            { bg: '#e3f0ff', color: '#0071e3', icon: <ClockCircleFilled /> },
  documentsRequested:{ bg: '#fff3e0', color: '#ff9500', icon: <ExclamationCircleFilled /> },
  approved:          { bg: '#e8f5e9', color: '#34c759', icon: <CheckCircleFilled /> },
  rejected:          { bg: '#ffebee', color: '#ff3b30', icon: <ExclamationCircleFilled /> },
}

export default function ApplicantDashboard() {
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()
  const [applications, setApplications] = useState<Application[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(() => {
    applicationApi.getApplications({ page: 1, size: 20 })
      .then((res) => setApplications((res.data.content ?? []).map((app: BackendApplication) => ({
        id: app.applicationNumber || app.id,
        realId: app.id,
        applicantName: '',
        iinBin: app.iinOrBin,
        productType: app.productionType,
        region: app.region,
        landArea: Number(app.landArea),
        requestedAmount: Number(app.requestedAmount),
        status: app.status.toLowerCase().replace('in_review', 'review').replace('additional_documents_requested', 'documentsRequested') as Application['status'],
        submittedAt: formatDate(app.createdAt, i18n.language),
        documents: [],
      }))))
      .catch(() => setApplications([]))
      .finally(() => setLoading(false))
  }, [i18n.language])

  useEffect(() => { load() }, [load])

  const totalAmount = applications.reduce((s, a) => s + a.requestedAmount, 0)
  const activeCount = applications.filter((a) => !['approved', 'rejected'].includes(a.status)).length
  const approvedCount = applications.filter((a) => a.status === 'approved').length

  const handleDelete = (app: Application) => {
    if (app.status !== 'draft') return
    Modal.confirm({
      title: t('appCard.deleteDraftTitle'),
      content: `${t('appCard.application')} ${app.id} — ${t('appCard.deleteDraftText')}.`,
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      centered: true,
      onOk: async () => {
        try {
          await applicationApi.deleteDraft(app.realId)
          setApplications((prev) => prev.filter((a) => a.realId !== app.realId))
          message.success(t('appCard.draftDeleted'))
        } catch {
          message.error(t('appCard.deleteDraftFailed'))
        }
      },
    })
  }

  return (
    <Space direction="vertical" size={24} style={{ width: '100%' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ fontSize: 28, fontWeight: 700, color: 'var(--label-primary)', letterSpacing: '-0.5px', margin: 0 }}>
            {t('applicant.title')}
          </h1>
          <p style={{ margin: '6px 0 0', color: 'var(--label-secondary)', fontSize: 15 }}>
            {t('applicant.subtitle')}
          </p>
        </div>
        <button
          onClick={() => navigate('/applicant/new')}
          style={{
            display: 'flex', alignItems: 'center', gap: 8,
            height: 44, padding: '0 20px',
            background: 'var(--accent)', color: '#fff',
            border: 'none', borderRadius: 12,
            fontSize: 15, fontWeight: 600, cursor: 'pointer',
            boxShadow: '0 2px 12px rgba(26,122,74,0.25)',
            transition: 'all 0.15s',
          }}
        >
          <PlusOutlined />
          {t('applicant.newApplication')}
        </button>
      </div>

      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16 }}>
        {[
          { label: t('applicant.activeApplications'), value: activeCount, color: 'var(--accent)' },
          { label: t('status.approved'), value: approvedCount, color: 'var(--green)' },
          { label: t('applicant.colAmount'), value: `${(totalAmount / 1_000_000).toFixed(1)} ${t('common.mln')}`, color: 'var(--blue)' },
        ].map((s) => (
          <div key={s.label} style={{
            background: 'var(--bg-elevated)',
            borderRadius: 8,
            border: '1px solid var(--separator)',
            boxShadow: 'none',
            padding: '20px 24px',
          }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--label-tertiary)', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 8 }}>
              {s.label}
            </div>
            <div style={{ fontSize: 30, fontWeight: 700, color: s.color, letterSpacing: '-1px' }}>
              {s.value}
            </div>
          </div>
        ))}
      </div>

      {/* Applications list */}
      <div style={{
        background: 'var(--bg-elevated)',
        borderRadius: 8,
        border: '1px solid var(--separator)',
        boxShadow: 'none',
        overflow: 'hidden',
        overflowX: 'auto',
      }}>
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '18px 24px',
          borderBottom: '1px solid var(--separator)',
        }}>
          <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--label-primary)' }}>
            {t('applicant.myApplications')}
          </span>
          <span style={{
            fontSize: 13, fontWeight: 500, color: 'var(--label-secondary)',
            background: 'var(--bg-secondary)', padding: '2px 10px', borderRadius: 999,
          }}>
            {applications.length}
          </span>
        </div>

        {loading ? (
          <div style={{ padding: 48, textAlign: 'center', color: 'var(--label-tertiary)' }}>
            {t('common.loading')}
          </div>
        ) : applications.length === 0 ? (
          <div style={{ padding: 48, textAlign: 'center' }}>
            <div style={{ width: 48, height: 48, margin: '0 auto 14px', borderRadius: 14, display: 'grid', placeItems: 'center', background: 'var(--bg-secondary)', color: 'var(--label-tertiary)', fontSize: 21 }}><FileTextOutlined /></div>
            <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--label-primary)', marginBottom: 6 }}>
              {t('appCard.noApplications')}
            </div>
            <div style={{ fontSize: 14, color: 'var(--label-secondary)', marginBottom: 20 }}>
              {t('appCard.submitFirst')}
            </div>
            <button
              onClick={() => navigate('/applicant/new')}
              style={{
                height: 40, padding: '0 20px',
                background: 'var(--accent)', color: '#fff',
                border: 'none', borderRadius: 10,
                fontSize: 14, fontWeight: 600, cursor: 'pointer',
              }}
            >
              {t('appCard.createApplication')}
            </button>
          </div>
        ) : (
          <div>
            {/* Table header */}
            <div style={{
              display: 'grid',
              gridTemplateColumns: '120px 1fr 130px 120px 110px 44px',
              minWidth: 620,
              padding: '10px 24px',
              background: 'var(--bg-secondary)',
              borderBottom: '1px solid var(--separator)',
            }}>
              {[t('applicant.colNumber'), t('applicant.colProduct'), t('applicant.colAmount'), t('applicant.colStatus'), t('applicant.colDate'), ''].map((col) => (
                <div key={col} style={{
                  fontSize: 11, fontWeight: 600, color: 'var(--label-tertiary)',
                  textTransform: 'uppercase', letterSpacing: 0.5,
                }}>
                  {col}
                </div>
              ))}
            </div>

            {/* Rows */}
            {applications.map((app, idx) => {
              const cfg = STATUS_CONFIG[app.status] ?? STATUS_CONFIG.draft
              return (
                <div
                  key={app.realId}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '120px 1fr 130px 120px 110px 44px',
                    minWidth: 620,
                    padding: '16px 24px',
                    alignItems: 'center',
                    borderBottom: idx < applications.length - 1 ? '1px solid var(--separator)' : 'none',
                    transition: 'background 0.12s',
                    cursor: app.status === 'draft' ? 'default' : 'pointer',
                  }}
                  onClick={() => { if (app.status !== 'draft') navigate(`/applicant/${app.realId}`) }}
                  onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-secondary)')}
                  onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                >
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--accent)', fontVariantNumeric: 'tabular-nums' }}>
                    {app.id}
                  </div>
                  <div>
                    <div style={{ fontSize: 14, fontWeight: 500, color: 'var(--label-primary)' }}>{app.productType || '—'}</div>
                    <div style={{ fontSize: 12, color: 'var(--label-tertiary)', marginTop: 2 }}>{app.region}</div>
                  </div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--label-primary)', fontVariantNumeric: 'tabular-nums' }}>
                    {formatAmount(app.requestedAmount, i18n.language)} ₸
                  </div>
                  <div>
                    <span style={{
                      display: 'inline-flex', alignItems: 'center', gap: 5,
                      padding: '4px 10px', borderRadius: 999,
                      background: cfg.bg, color: cfg.color,
                      fontSize: 12, fontWeight: 600,
                    }}>
                      {cfg.icon} {t(`status.${app.status}`)}
                    </span>
                  </div>
                  <div style={{ fontSize: 13, color: 'var(--label-secondary)', cursor: 'pointer' }} onClick={() => navigate(`/applicant/${app.realId}`)}>
                    {app.submittedAt}
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 4 }}>
                    {app.status !== 'draft' && (
                      <button
                        onClick={() => navigate(`/applicant/${app.realId}`)}
                        title={t('appCard.openApplication')}
                        style={{
                          width: 32, height: 32, borderRadius: 8, border: 'none',
                          background: 'transparent', color: 'var(--label-tertiary)',
                          cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13,
                        }}
                        onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--bg-secondary)' }}
                        onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
                      >
                        <ArrowRightOutlined />
                      </button>
                    )}
                    {app.status === 'draft' && (
                      <button
                        onClick={(e) => { e.stopPropagation(); handleDelete(app) }}
                        title={t('appCard.deleteDraft')}
                        style={{
                          width: 32, height: 32, borderRadius: 8,
                          border: 'none', background: 'transparent',
                          color: 'var(--label-tertiary)', cursor: 'pointer',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          fontSize: 15, transition: 'all 0.15s',
                        }}
                        onMouseEnter={(e) => {
                          (e.currentTarget as HTMLButtonElement).style.background = 'rgba(255,59,48,0.08)'
                          ;(e.currentTarget as HTMLButtonElement).style.color = 'var(--red)'
                        }}
                        onMouseLeave={(e) => {
                          (e.currentTarget as HTMLButtonElement).style.background = 'transparent'
                          ;(e.currentTarget as HTMLButtonElement).style.color = 'var(--label-tertiary)'
                        }}
                      >
                        <DeleteOutlined />
                      </button>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </Space>
  )
}
