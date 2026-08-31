import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Modal, message } from 'antd'
import {
  ArrowLeftOutlined, FileTextOutlined, CheckCircleFilled,
  ClockCircleFilled, ExclamationCircleFilled, CloseCircleFilled, RollbackOutlined,
  SendOutlined, DeleteOutlined,
} from '@ant-design/icons'
import { applicationApi, type BackendApplication } from '../api/applications'
import { formatAmount, formatDate, formatDateTime } from '../i18n/format'

const STATUS_CONFIG: Record<string, { key: string; bg: string; color: string; icon: React.ReactNode }> = {
  draft:                          { key: 'status.draft', bg: '#f2f2f7', color: '#6e6e73', icon: <FileTextOutlined /> },
  submitted:                      { key: 'status.submitted', bg: '#fff8e1', color: '#f59e0b', icon: <ClockCircleFilled /> },
  in_review:                      { key: 'status.review', bg: '#e8f4fd', color: '#0071e3', icon: <ClockCircleFilled /> },
  additional_documents_requested: { key: 'status.documentsRequested', bg: '#fff3e0', color: '#ff6d00', icon: <ExclamationCircleFilled /> },
  approved:                       { key: 'status.approved', bg: '#e8f5e9', color: '#1a7a4a', icon: <CheckCircleFilled /> },
  rejected:                       { key: 'status.rejected', bg: '#ffebee', color: '#d32f2f', icon: <CloseCircleFilled /> },
}

function normalize(status: string) {
  return status.toLowerCase()
}

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div style={{ padding: '14px 0', borderBottom: '1px solid var(--separator)' }}>
      <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--label-tertiary)', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 4 }}>
        {label}
      </div>
      <div style={{ fontSize: 15, color: 'var(--label-primary)', fontWeight: 500 }}>{value || '—'}</div>
    </div>
  )
}

type StatusHistoryItem = {
  id: string
  previousStatus: string | null
  newStatus: string
  reason: string | null
  comment: string | null
  createdAt: string
}

type Completeness = {
  complete: boolean
  missingFields: string[]
  missingDocuments: string[]
  message: string | null
}

const FIELD_LABEL_KEYS: Record<string, string> = {
  iinOrBin: 'fields.iinOrBin',
  region: 'fields.region',
  productionType: 'fields.productionType',
  landArea: 'fields.landArea',
  requestedAmount: 'fields.requestedAmount',
}



/** Запасные подписи, если для статуса нет ключа перевода. */


export default function ApplicationDetailPage() {
  const { t, i18n } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [app, setApp] = useState<BackendApplication | null>(null)
  const [loading, setLoading] = useState(true)
  // История статусов — требование ТЗ: заявитель должен видеть путь заявки.
  const [history, setHistory] = useState<StatusHistoryItem[]>([])
  // Комплектность пакета — ТЗ требует называть недостающее конкретно.
  const [completeness, setCompleteness] = useState<Completeness | null>(null)

  useEffect(() => {
    if (!id) return
    applicationApi.getApplications({ page: 1, size: 100 })
      .then((res) => {
        const items: BackendApplication[] = res.data.content ?? []
        const found = items.find((a) => a.id === id)
        if (found) setApp(found)
        else navigate('/applicant')
      })
      .catch(() => navigate('/applicant'))
      .finally(() => setLoading(false))

    applicationApi.getStatusHistory(id)
      .then((res) => setHistory((res.data as StatusHistoryItem[]) ?? []))
      .catch(() => setHistory([]))

    applicationApi.getCompleteness(id)
      .then((res) => setCompleteness(res.data as Completeness))
      .catch(() => setCompleteness(null))
  }, [id, navigate])

  const handleSubmit = async () => {
    if (!app) return
    try {
      await applicationApi.submitApplication(app.id)
      message.success(t('appCard.submitted'))
      setApp((prev) => prev ? { ...prev, status: 'SUBMITTED' } : null)
    } catch {
      message.error(t('appCard.submitFailed'))
    }
  }

  /** Отозвать можно только черновик или отправленную заявку — так же, как на сервере. */
  const handleWithdraw = () => {
    if (!app) return
    Modal.confirm({
      title: t('appCard.withdrawTitle'),
      content: t('appCard.withdrawText'),
      okText: t('appCard.withdrawOk'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      centered: true,
      onOk: async () => {
        try {
          await applicationApi.withdrawApplication(app.id)
          setApp((prev) => (prev ? { ...prev, status: 'WITHDRAWN' } : null))
          message.success(t('appCard.withdrawn'))
        } catch {
          message.error(t('appCard.withdrawFailed'))
        }
      },
    })
  }

  const handleDelete = async () => {
    if (!app) return
    try {
      await applicationApi.deleteDraft(app.id)
      message.success(t('appCard.draftDeleted'))
      navigate('/applicant')
    } catch {
      message.error(t('appCard.deleteFailed'))
    }
  }

  if (loading) return (
    <div style={{ padding: 80, textAlign: 'center', color: 'var(--label-tertiary)' }}>{t('common.loading')}</div>
  )
  if (!app) return null

  const statusKey = normalize(app.status)
  const cfg = STATUS_CONFIG[statusKey] ?? STATUS_CONFIG.draft
  const isDraft = statusKey === 'draft'
  // Черновик удаляют, а отправленную — отзывают: разные действия, разный смысл.
  const canWithdraw = app.status === 'SUBMITTED'
  const isApproved = statusKey === 'approved'
  const isRejected = statusKey === 'rejected'

  const TIMELINE = [
    { key: 'draft', label: t('status.draft'), done: true },
    { key: 'submitted', label: t('status.submitted'), done: ['submitted','in_review','additional_documents_requested','approved','rejected'].includes(statusKey) },
    { key: 'in_review', label: t('status.review'), done: ['in_review','additional_documents_requested','approved','rejected'].includes(statusKey) },
    { key: 'approved', label: isRejected ? t('status.rejected') : t('status.approved'), done: isApproved || isRejected },
  ]

  return (
    <div style={{ width: '100%', maxWidth: 900 }}>
      {/* Back */}
      <button
        onClick={() => navigate('/applicant')}
        style={{
          display: 'flex', alignItems: 'center', gap: 8,
          background: 'none', border: 'none', cursor: 'pointer',
          color: 'var(--label-secondary)', fontSize: 14, padding: '0 0 20px',
        }}
      >
        <ArrowLeftOutlined /> {t('appCard.myApplications')}
      </button>

      {/* Header */}
      <div style={{
        background: 'var(--bg-elevated)', border: '1px solid var(--separator)',
        borderRadius: 8, padding: '24px 28px', marginBottom: 16,
      }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <div style={{ fontSize: 13, color: 'var(--label-tertiary)', marginBottom: 4 }}>{t('appCard.application')}</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: 'var(--label-primary)', letterSpacing: '-0.5px', marginBottom: 10 }}>
              {app.applicationNumber || app.id.slice(0, 8).toUpperCase()}
            </div>
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: 6,
              padding: '5px 12px', borderRadius: 999,
              background: cfg.bg, color: cfg.color, fontSize: 13, fontWeight: 600,
            }}>
              {cfg.icon} {t(cfg.key)}
            </span>
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {isDraft && (
              <>
                <button onClick={handleDelete} style={{
                  display: 'flex', alignItems: 'center', gap: 6,
                  padding: '8px 16px', borderRadius: 10, cursor: 'pointer',
                  border: '1px solid #ffcdd2', background: '#fff0f0', color: '#d32f2f',
                  fontSize: 13, fontWeight: 600,
                }}>
                  <DeleteOutlined /> {t('common.delete')}
                </button>
                <button onClick={handleSubmit} style={{
                  display: 'flex', alignItems: 'center', gap: 6,
                  padding: '8px 20px', borderRadius: 10, cursor: 'pointer',
                  border: 'none', background: 'var(--accent)', color: '#fff',
                  fontSize: 13, fontWeight: 600,
                }}>
                  <SendOutlined /> {t('common.send')}
                </button>
              </>
            )}
            {canWithdraw && (
              <button onClick={handleWithdraw} style={{
                display: 'flex', alignItems: 'center', gap: 6,
                padding: '8px 16px', borderRadius: 10, cursor: 'pointer',
                border: '1px solid var(--separator)', background: 'var(--bg-elevated)',
                color: 'var(--label-secondary)', fontSize: 13, fontWeight: 600,
              }}>
                <RollbackOutlined /> {t('appCard.withdraw')}
              </button>
            )}
          </div>
        </div>

        {/* Progress timeline */}
        <div style={{ display: 'flex', alignItems: 'center', marginTop: 28, gap: 0 }}>
          {TIMELINE.map((step, i) => (
            <div key={step.key} style={{ display: 'flex', alignItems: 'center', flex: i < TIMELINE.length - 1 ? 1 : 'none' }}>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                <div style={{
                  width: 28, height: 28, borderRadius: '50%', flexShrink: 0,
                  background: step.done ? (isRejected && i === 3 ? '#d32f2f' : 'var(--accent)') : 'var(--separator)',
                  display: 'grid', placeItems: 'center',
                  color: step.done ? '#fff' : 'var(--label-tertiary)',
                  fontSize: 13, fontWeight: 700, transition: 'all 0.3s',
                }}>
                  {step.done ? (isRejected && i === 3 ? '✗' : '✓') : i + 1}
                </div>
                <span style={{ fontSize: 11, color: step.done ? 'var(--label-primary)' : 'var(--label-tertiary)', fontWeight: step.done ? 600 : 400, whiteSpace: 'nowrap' }}>
                  {step.label}
                </span>
              </div>
              {i < TIMELINE.length - 1 && (
                <div style={{ flex: 1, height: 2, background: step.done ? 'var(--accent)' : 'var(--separator)', margin: '0 4px', marginBottom: 20, transition: 'background 0.3s' }} />
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Details */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 16 }}>
        <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 8, padding: '24px 28px' }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--label-tertiary)', textTransform: 'uppercase', letterSpacing: 0.7, marginBottom: 4 }}>
            {t('appCard.mainData')}
          </div>
          <Field label={t('fields.iinOrBin')} value={app.iinOrBin} />
          <Field label={t('fields.region')} value={app.region} />
          <Field label={t('appCard.productionType')} value={app.productionType} />
          <Field label={t('fields.landArea')} value={app.landArea ? `${app.landArea} га` : null} />
          <Field label={t('appCard.submittedAt')} value={formatDate(app.createdAt, i18n.language, { day: 'numeric', month: 'long', year: 'numeric' })} />
        </div>

        <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 8, padding: '24px 28px' }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--label-tertiary)', textTransform: 'uppercase', letterSpacing: 0.7, marginBottom: 4 }}>
            {t('appCard.financeData')}
          </div>
          <div style={{ padding: '20px 0', borderBottom: '1px solid var(--separator)' }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--label-tertiary)', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 6 }}>{t('appCard.requestedAmount')}</div>
            <div style={{ fontSize: 28, fontWeight: 800, color: 'var(--accent)', letterSpacing: '-1px' }}>
              {formatAmount(app.requestedAmount, i18n.language)} ₸
            </div>
          </div>
          {isApproved && (
            <div style={{ padding: '16px 0' }}>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '10px 16px', borderRadius: 10, background: '#e8f5e9', color: '#1a7a4a' }}>
                <CheckCircleFilled style={{ fontSize: 18 }} />
                <span style={{ fontSize: 14, fontWeight: 600 }}>{t('appCard.approvedNotice')}</span>
              </div>
            </div>
          )}
          {isRejected && (
            <div style={{ padding: '16px 0' }}>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '10px 16px', borderRadius: 10, background: '#ffebee', color: '#d32f2f' }}>
                <CloseCircleFilled style={{ fontSize: 18 }} />
                <span style={{ fontSize: 14, fontWeight: 600 }}>{t('appCard.rejectedNotice')}</span>
              </div>
            </div>
          )}
        </div>
      </div>

      {completeness && (
        <div style={{
          marginTop: 16, background: 'var(--bg-elevated)',
          border: '1px solid var(--separator)', borderRadius: 8, padding: '20px 28px',
        }}>
          <div style={{
            fontSize: 12, fontWeight: 700, color: 'var(--label-tertiary)',
            textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 14,
            borderLeft: `3px solid ${completeness.complete ? 'var(--accent)' : '#f0a202'}`, paddingLeft: 10,
          }}>
            {t('appCard.completeness')}
          </div>
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 8,
            padding: '8px 14px', borderRadius: 9,
            background: completeness.complete ? 'var(--accent-light)' : '#fff8e1',
            color: completeness.complete ? 'var(--accent)' : '#8a6100',
            fontSize: 14, fontWeight: 600,
          }}>
            {completeness.complete ? <CheckCircleFilled /> : <ExclamationCircleFilled />}
            {completeness.complete ? t('appCard.complete') : t('appCard.incomplete')}
          </div>

          {(completeness.missingFields.length > 0 || completeness.missingDocuments.length > 0) && (
            <ul style={{ margin: '14px 0 0', paddingLeft: 18, color: 'var(--label-secondary)', fontSize: 13, lineHeight: 1.8 }}>
              {completeness.missingFields.map((field) => (
                <li key={field}>{t('appCard.fillField')}: {t(FIELD_LABEL_KEYS[field] ?? field)}</li>
              ))}
              {completeness.missingDocuments.map((doc) => (
                <li key={doc}>{t('appCard.attachDocument')}: {t(`docTypes.${doc}`, doc)}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      {history.length > 0 && (
        <div style={{
          marginTop: 16, background: 'var(--bg-elevated)',
          border: '1px solid var(--separator)', borderRadius: 8, padding: '24px 28px',
        }}>
          <div style={{
            fontSize: 12, fontWeight: 700, color: 'var(--label-tertiary)',
            textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 18,
            borderLeft: '3px solid var(--accent)', paddingLeft: 10,
          }}>
            {t('appCard.history')}
          </div>
          <ol style={{ margin: 0, padding: 0, listStyle: 'none' }}>
            {history.map((item, index) => (
              <li key={item.id} style={{ display: 'flex', gap: 14, paddingBottom: index === history.length - 1 ? 0 : 18 }}>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flexShrink: 0 }}>
                  <span style={{
                    width: 10, height: 10, borderRadius: '50%', marginTop: 5,
                    background: index === history.length - 1 ? 'var(--accent)' : 'var(--separator)',
                  }} />
                  {index < history.length - 1 && (
                    <span style={{ flex: 1, width: 1, background: 'var(--separator)', marginTop: 4 }} />
                  )}
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--label-primary)' }}>
                    {t(`historyLabels.${item.newStatus}`, item.newStatus)}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--label-tertiary)', marginTop: 2 }}>
                    {formatDateTime(item.createdAt, i18n.language)}
                  </div>
                  {item.comment && (
                    <div style={{ fontSize: 13, color: 'var(--label-secondary)', marginTop: 6, lineHeight: 1.5 }}>
                      {item.comment}
                    </div>
                  )}
                </div>
              </li>
            ))}
          </ol>
        </div>
      )}
    </div>
  )
}
