import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Input, InputNumber, message, Modal, Select, Spin } from 'antd'
import {
  ArrowLeftOutlined, CheckCircleOutlined, CloseCircleOutlined,
  FileSearchOutlined, FilePdfOutlined, RobotOutlined, GlobalOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '../store/authStore'
import { applicationApi, type ApplicationProcessing } from '../api/applications'
import { downloadBlobResponse } from '../api/download'
import { getDocumentLanguage } from '../i18n/documentLanguage'
import { formatAmount, formatDate } from '../i18n/format'

type BackendStatus = 'DRAFT' | 'SUBMITTED' | 'WITHDRAWN' | 'IN_REVIEW' | 'ADDITIONAL_DOCUMENTS_REQUESTED' | 'APPROVED' | 'REJECTED'

type Detail = {
  id: string
  applicationNumber: string
  applicantName: string
  status: BackendStatus
  iinOrBin: string
  region: string
  productionType: string
  landArea: number
  requestedAmount: number
  createdAt: string
  publicVisible: boolean
  publishedAt: string | null
  documents: { id: string; originalFileName: string; documentType: string }[]
  decisions: { decisionType: string; reason: string; decidedByName: string }[]
}

type DecisionAction = 'approve' | 'reject' | 'documents' | null

/** Возвращает ключ склонения: сам перевод делает компонент, где доступен t. */
const pluralFilesKey = (n: number) => {
  const mod10 = n % 10
  const mod100 = n % 100
  if (mod10 === 1 && mod100 !== 11) return 'plural.file1'
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return 'plural.file2'
  return 'plural.file5'
}

const DOCUMENT_VALUES = [
  'IIN_CERTIFICATE', 'LAND_CERTIFICATE', 'TAX_CERTIFICATE',
  'BANK_STATEMENT', 'BUSINESS_PLAN', 'OTHER',
] as const

/** Ключ перевода для типа документа; BANK_STATEMENT исторически хранится как выписка. */
const documentLabelKey = (value: string) =>
  value === 'BANK_STATEMENT' ? 'docTypes.FINANCIAL_STATEMENT' : `docTypes.${value}`

const STATUS_STYLE: Record<BackendStatus, { key: string; bg: string; color: string }> = {
  DRAFT: { key: 'status.draft', bg: '#f2f2f7', color: '#6e6e73' },
  SUBMITTED: { key: 'status.submitted', bg: '#fff8e1', color: '#f59e0b' },
  IN_REVIEW: { key: 'status.review', bg: '#e8f4fd', color: '#0071e3' },
  ADDITIONAL_DOCUMENTS_REQUESTED: { key: 'status.documentsRequested', bg: '#fff3e0', color: '#ff6d00' },
  APPROVED: { key: 'status.approved', bg: '#e8f5e9', color: '#1a7a4a' },
  REJECTED: { key: 'status.rejected', bg: '#ffebee', color: '#d32f2f' },
  WITHDRAWN: { key: 'status.withdrawn', bg: '#f5f5f5', color: '#9e9e9e' },
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

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--label-tertiary)', textTransform: 'uppercase', letterSpacing: 0.8, marginBottom: 16, borderLeft: '3px solid var(--accent)', paddingLeft: 10 }}>
      {children}
    </div>
  )
}

export default function CommissionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()
  const role = useAuthStore((s) => s.role)
  const [detail, setDetail] = useState<Detail | null>(null)
  const [loading, setLoading] = useState(true)
  const [aiLoading, setAiLoading] = useState(false)
  const [aiResult, setAiResult] = useState<{
    recommendedAmount?: number | null
    score: number
    risk: string
    summary: string
    factors: string[]
    model: string
  } | null>(null)
  const [aiError, setAiError] = useState('')
  // Автопроверка по ТЗ: комплектность, OCR и детектор дублей/аномалий.
  const [checks, setChecks] = useState<ApplicationProcessing | null>(null)
  const [decisionAction, setDecisionAction] = useState<DecisionAction>(null)
  const [reason, setReason] = useState('')
  const [approvedAmount, setApprovedAmount] = useState<number | null>(null)
  const [documentTypes, setDocumentTypes] = useState<string[]>([])
  const [submittingDecision, setSubmittingDecision] = useState(false)
  const [protocolLoading, setProtocolLoading] = useState(false)
  const [publishing, setPublishing] = useState(false)

  const canDecide = role === 'chairman' || role === 'admin'
  const canPublish = role === 'chairman' || role === 'admin'
  // Бэкенд разрешает скоринг только для отправленной заявки (409 в остальных случаях).
  // Те же статусы, что и в AI_TRIGGERABLE_STATUSES на сервере: интерфейс
  // не должен быть строже бэкенда, иначе кнопка пропадает там, где запуск разрешён.
  const canRunScoring = detail?.status === 'SUBMITTED' || detail?.status === 'IN_REVIEW'
  const canRefreshConclusion = Boolean(aiResult)
  const canRequestDocs = role === 'chairman' || role === 'secretary' || role === 'admin'
  const canGenerateProtocol = role === 'secretary' || role === 'chairman' || role === 'admin'

  const applyProcessing = useCallback((
    processing: ApplicationProcessing,
    // При повторе заключения балл уже есть, поэтому опрос должен ждать именно LLM,
    // иначе он завершится мгновенно и текст останется прежним.
    waitForConclusion = false,
  ) => {
    setChecks(processing)
    const scoring = processing.scoring
    if (scoring.score == null || !scoring.riskCategory) return false
    if (waitForConclusion) {
      const llmStatus = processing.llmConclusion.status
      if (llmStatus === 'PENDING' || llmStatus === 'PROCESSING') return false
    }
    const riskLabels = { LOW: t('risk.LOW'), MEDIUM: t('risk.MEDIUM'), HIGH: t('risk.HIGH') }
    const factors = (scoring.topFactors ?? []).map((factor) =>
      factor.factor ?? factor.feature ?? t('commissionCard.modelFactor'),
    )
    setAiResult({
      score: scoring.score,
      risk: riskLabels[scoring.riskCategory],
      summary: processing.llmConclusion.text
        ?? t('commissionCard.conclusionUnavailable'),
      factors,
      recommendedAmount: scoring.recommendedAmount,
      model: [scoring.modelName, scoring.modelVersion].filter(Boolean).join(' · '),
    })
    return true
  }, [t])

  useEffect(() => {
    if (!id) return
    applicationApi.getCommissionApplicationDetail(id)
      .then((res) => setDetail(res.data as Detail))
      .catch(() => {
        message.error(t('commissionMsg.detailFailed'))
        navigate('/commission')
      })
      .finally(() => setLoading(false))
    applicationApi.getApplicationProcessing(id)
      .then((res) => applyProcessing(res.data))
      .catch(() => {})
  }, [applyProcessing, id, navigate, t])

  const handlePublishToggle = async () => {
    if (!detail) return
    const publishing = !detail.publicVisible
    setPublishing(true)
    try {
      const response = publishing
        ? await applicationApi.publishApplication(detail.id)
        : await applicationApi.unpublishApplication(detail.id)
      setDetail(response.data as Detail)
      message.success(publishing ? t('commissionMsg.published') : t('commissionMsg.unpublished'))
    } catch (error) {
      message.error(describeError(
        error,
        publishing ? t('commissionMsg.publishFailed') : t('commissionMsg.unpublishFailed'),
      ))
    } finally {
      setPublishing(false)
    }
  }

  const openDecision = (action: Exclude<DecisionAction, null>) => {
    setDecisionAction(action)
    setReason('')
    setApprovedAmount(action === 'approve' ? Number(detail?.requestedAmount ?? 0) : null)
    setDocumentTypes([])
  }

  /**
   * Сервер объясняет отказ конкретно (статус, права, лимит суммы).
   * Общий текст оставляем только тогда, когда объяснения нет.
   */
  const describeError = (error: unknown, fallback: string) => {
    const message = (error as { response?: { data?: { message?: string } } })
      ?.response?.data?.message
    return message && message.trim() ? message : fallback
  }

  const handleDecision = async () => {
    if (!detail || !decisionAction || reason.trim().length < 3) {
      message.warning(t('commissionMsg.needComment'))
      return
    }
    if (decisionAction === 'approve' && (!approvedAmount || approvedAmount <= 0)) {
      message.warning(t('commissionMsg.needAmount'))
      return
    }
    if (decisionAction === 'documents' && documentTypes.length === 0) {
      message.warning(t('commissionMsg.needDocument'))
      return
    }

    setSubmittingDecision(true)
    try {
      const response = decisionAction === 'approve'
        ? await applicationApi.approveApplication(detail.id, reason.trim(), approvedAmount as number)
        : decisionAction === 'reject'
          ? await applicationApi.rejectApplication(detail.id, reason.trim())
          : await applicationApi.requestDocuments(detail.id, reason.trim(), documentTypes)
      setDetail(response.data as Detail)
      message.success(decisionAction === 'approve' ? t('commissionMsg.approved') : decisionAction === 'reject' ? t('commissionMsg.rejected') : t('commissionMsg.docsRequested'))
      setDecisionAction(null)
    } catch (error) {
      message.error(describeError(error, t('commissionMsg.actionFailed')))
    } finally {
      setSubmittingDecision(false)
    }
  }

  const handleProtocol = async () => {
    if (!detail) return
    setProtocolLoading(true)
    try {
      const docLanguage = getDocumentLanguage()
      const response = await applicationApi.generateProtocol(detail.id, docLanguage)
      downloadBlobResponse(response, `protocol-${detail.applicationNumber}-${docLanguage}.pdf`)
      message.success(t('commissionMsg.protocolDownloaded'))
    } catch {
      message.error(t('commissionMsg.protocolOnlyFinal'))
    } finally {
      setProtocolLoading(false)
    }
  }

  const handleDocumentDownload = async (documentId: string, originalName: string) => {
    try {
      const response = await applicationApi.downloadDocument(documentId)
      downloadBlobResponse(response, originalName)
    } catch {
      message.error(t('commissionMsg.documentFailed'))
    }
  }

  /**
   * Скоринг детерминирован и привязан к ревизии, поэтому повторный расчёт
   * бессмыслен: когда балл уже есть, обновляем только текстовое заключение.
   */
  const handleAiScore = async () => {
    if (!detail) return
    setAiLoading(true)
    setAiError('')
    try {
      const refreshingConclusion = Boolean(aiResult)
      if (refreshingConclusion) {
        await applicationApi.retryLlmConclusion(detail.id)
      } else {
        await applicationApi.triggerApplicationScore(detail.id)
      }
      for (let attempt = 0; attempt < 40; attempt += 1) {
        const processing = (await applicationApi.getApplicationProcessing(detail.id)).data
        if (applyProcessing(processing, refreshingConclusion)) return
        if (processing.overallStatus === 'FAILED') {
          throw new Error(processing.llmConclusion.errorMessage || t('commissionMsg.aiCannotProcess'))
        }
        await new Promise((resolve) => window.setTimeout(resolve, 750))
      }
      throw new Error(t('commissionMsg.aiSlow'))
    } catch (error) {
      const text = error instanceof Error ? error.message : t('commissionMsg.aiFailed')
      setAiError(text)
      message.error(text)
    } finally {
      setAiLoading(false)
    }
  }

  if (loading) return <div style={{ padding: 80, textAlign: 'center' }}><Spin size="large" /></div>
  if (!detail) return null

  const s = STATUS_STYLE[detail.status]
  const isFinal = detail.status === 'APPROVED' || detail.status === 'REJECTED'

  return (
    <div style={{ width: '100%', maxWidth: 1000 }}>
      {/* Back */}
      <button onClick={() => navigate('/commission')} style={{
        display: 'flex', alignItems: 'center', gap: 8,
        background: 'none', border: 'none', cursor: 'pointer',
        color: 'var(--label-secondary)', fontSize: 14, padding: '0 0 20px',
      }}>
        <ArrowLeftOutlined /> {t('commissionCard.allApplications')}
      </button>

      {/* Header card */}
      <div style={{
        background: 'var(--bg-elevated)', border: '1px solid var(--separator)',
        borderRadius: 8, padding: '24px 28px', marginBottom: 20,
        display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16,
      }}>
        <div>
          <div style={{ fontSize: 13, color: 'var(--label-tertiary)', marginBottom: 4 }}>{t('commissionCard.committeeApplication')}</div>
          <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--label-primary)', letterSpacing: '-0.5px', marginBottom: 10 }}>
            {detail.applicationNumber}
          </div>
          <span style={{
            display: 'inline-block', padding: '5px 14px', borderRadius: 999,
            background: s.bg, color: s.color, fontSize: 13, fontWeight: 600,
          }}>
            {t(s.key)}
          </span>
        </div>

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {canGenerateProtocol && isFinal && (
            <button onClick={handleProtocol} disabled={protocolLoading} style={btn('ghost')}>
              <FilePdfOutlined /> {protocolLoading ? t('commissionCard.generating') : t('commissionCard.protocol')}
            </button>
          )}
          {canRequestDocs && !isFinal && (
            <button onClick={() => openDecision('documents')} style={btn('secondary')}>
              <FileSearchOutlined /> {t('commission.requestDocs')}
            </button>
          )}
          {canPublish && detail.status === 'APPROVED' && (
            <button onClick={handlePublishToggle} disabled={publishing} style={btn(detail.publicVisible ? 'ghost' : 'primary')}>
              <GlobalOutlined /> {publishing
                ? t('commissionCard.saving')
                : detail.publicVisible ? t('commissionCard.unpublish') : t('commissionCard.publish')}
            </button>
          )}
          {canDecide && !isFinal && (
            <>
              <button onClick={() => openDecision('reject')} style={btn('danger')}>
                <CloseCircleOutlined /> {t('commission.reject')}
              </button>
              <button onClick={() => openDecision('approve')} style={btn('primary')}>
                <CheckCircleOutlined /> {t('commission.approve')}
              </button>
            </>
          )}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 16, marginBottom: 16 }}>
        {/* Left: applicant data */}
        <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 8, padding: '24px 28px' }}>
          <SectionTitle>{t('commissionCard.applicantData')}</SectionTitle>
          <Field label={t('commissionCard.iinBin')} value={detail.iinOrBin} />
          <Field label={t('commissionCard.region')} value={detail.region} />
          <Field label={t('commissionCard.productionType')} value={detail.productionType} />
          <Field label={t('commissionCard.landArea')} value={detail.landArea ? `${detail.landArea} га` : null} />
        </div>

        {/* Right: financial */}
        <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 8, padding: '24px 28px' }}>
          <SectionTitle>{t('commissionCard.finance')}</SectionTitle>
          <div style={{ padding: '12px 0 20px', borderBottom: '1px solid var(--separator)', marginBottom: 4 }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--label-tertiary)', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 6 }}>{t('commission.requestedAmount')}</div>
            <div style={{ fontSize: 30, fontWeight: 800, color: 'var(--accent)', letterSpacing: '-1px' }}>
              {formatAmount(detail.requestedAmount, i18n.language)} ₸
            </div>
          </div>
          <Field label={t('commissionCard.submittedAt')} value={detail.createdAt
            ? formatDate(detail.createdAt, i18n.language, { day: 'numeric', month: 'long', year: 'numeric' })
            : '—'} />
          <Field label={t('commissionCard.documentsAttached')} value={`${detail.documents.length} ${t(pluralFilesKey(detail.documents.length))}`} />
        </div>
      </div>

      {/* Автопроверка: комплектность, OCR, дубли и аномалии (ТЗ 1.2 и 1.3) */}
      {checks && (
        <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 8, padding: '24px 28px', marginBottom: 16 }}>
          <SectionTitle>{t('commissionCard.autoCheck')}</SectionTitle>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: 14, marginTop: 4 }}>
            <CheckTile
              label={t('commissionCard.completeness')}
              ok={checks.completeness?.complete ?? null}
              okText={t('commissionCard.packageComplete')}
              failText={checks.completeness?.missingDocuments?.length
                ? `${t('commissionCard.missingCount')}: ${checks.completeness.missingDocuments.length}`
                : t('commissionCard.hasRemarks')}
            />
            <CheckTile
              label={t('commissionCard.ocrCheck')}
              ok={checks.ocr?.status === 'SKIPPED' ? null : checks.ocr?.status === 'COMPLETED'}
              okText={`${t('commissionCard.processed')}: ${checks.ocr?.documentsProcessed ?? 0}`}
              failText={checks.ocr?.documentsFailed ? `${t('commissionCard.unreadable')}: ${checks.ocr.documentsFailed}` : t('commissionCard.notPerformed')}
            />
            <CheckTile
              label={t('commissionCard.duplicateCheck')}
              ok={checks.duplicateCheck.status === 'COMPLETED'
                ? checks.duplicateCheck.hasDuplicates === false
                : null}
              okText={t('commissionCard.noDuplicates')}
              failText={checks.duplicateCheck.hasDuplicates
                ? `Совпадение: ${checks.duplicateCheck.duplicateType ?? t('commissionCard.found')}`
                : t('commissionCard.checkIncomplete')}
            />
          </div>

          {(checks.duplicateCheck.flags.length > 0 || checks.duplicateCheck.anomalies.length > 0) && (
            <div style={{ marginTop: 16, padding: '12px 16px', background: '#fff8e1', border: '1px solid #ffe0a3', borderRadius: 8 }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: '#8a6100', textTransform: 'uppercase', letterSpacing: 0.4, marginBottom: 8 }}>
                {t('commissionCard.flagsAnomalies')}
              </div>
              <ul style={{ margin: 0, paddingLeft: 18, color: '#6b4d00', fontSize: 13, lineHeight: 1.7 }}>
                {[...checks.duplicateCheck.flags, ...checks.duplicateCheck.anomalies].map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* AI Scoring section */}
      <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 8, padding: '24px 28px', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20, flexWrap: 'wrap', gap: 12 }}>
          <div>
            <SectionTitle>{t('commissionCard.aiScoring')}</SectionTitle>
            <div style={{ fontSize: 13, color: 'var(--label-tertiary)', marginTop: -10 }}>{t('commissionCard.aiScoringHint')}</div>
          </div>
          {(canRunScoring || canRefreshConclusion) && (
          <button onClick={handleAiScore} disabled={aiLoading} style={{
            display: 'flex', alignItems: 'center', gap: 8,
            padding: '9px 20px', borderRadius: 10, cursor: aiLoading ? 'default' : 'pointer',
            border: '1px solid var(--separator)',
            background: aiResult ? 'var(--accent-light)' : 'var(--bg-secondary)',
            color: aiResult ? 'var(--accent)' : 'var(--label-primary)',
            fontSize: 13, fontWeight: 600, opacity: aiLoading ? 0.6 : 1,
          }}>
            <RobotOutlined /> {aiLoading
              ? t('commissionCard.analyzing')
              : aiResult ? t('commissionCard.refreshConclusion') : t('commissionCard.runAnalysis')}
          </button>
          )}
        </div>

        {aiLoading && (
          <div style={{ textAlign: 'center', padding: '24px 0', color: 'var(--label-tertiary)' }}>
            <Spin /> <span style={{ marginLeft: 10 }}>
              {aiResult ? t('commissionCard.aiWriting') : t('commissionCard.modelAnalysing')}
            </span>
          </div>
        )}

        {!aiLoading && !aiResult && (
          <div style={{
            textAlign: 'center', padding: '32px 20px',
            border: '2px dashed var(--separator)', borderRadius: 12,
            color: 'var(--label-tertiary)', fontSize: 14,
          }}>
            <RobotOutlined style={{ fontSize: 32, marginBottom: 10, display: 'block' }} />
            {canRunScoring
              ? t('commissionCard.pressRun')
              : t('commissionCard.scoringOnlySubmitted')}
          </div>
        )}

        {!aiLoading && aiError && (
          <div style={{ marginTop: 12, color: '#b42318', fontSize: 13 }}>{aiError}</div>
        )}

        {!aiLoading && aiResult && (
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 24, flexWrap: 'wrap', marginBottom: 20 }}>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 48, fontWeight: 900, color: aiResult.score >= 75 ? '#1a7a4a' : aiResult.score >= 50 ? '#f59e0b' : '#d32f2f', letterSpacing: '-2px' }}>
                  {aiResult.score}
                </div>
                <div style={{ fontSize: 12, color: 'var(--label-tertiary)' }}>{t('commissionCard.outOf100')}</div>
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ height: 10, background: 'var(--separator)', borderRadius: 5, overflow: 'hidden', marginBottom: 8 }}>
                  <div style={{
                    height: '100%', borderRadius: 5, transition: 'width 0.8s ease',
                    width: `${aiResult.score}%`,
                    background: aiResult.score >= 75 ? '#1a7a4a' : aiResult.score >= 50 ? '#f59e0b' : '#d32f2f',
                  }} />
                </div>
                <span style={{
                  fontSize: 13, fontWeight: 700, padding: '3px 10px', borderRadius: 6,
                  background: aiResult.score >= 75 ? '#e8f5e9' : aiResult.score >= 50 ? '#fff8e1' : '#ffebee',
                  color: aiResult.score >= 75 ? '#1a7a4a' : aiResult.score >= 50 ? '#f59e0b' : '#d32f2f',
                }}>
                  {aiResult.risk} {t('risk.suffix')}
                </span>
              </div>

              {/* Рекомендованный моделью лимит — ТЗ требует показывать его комиссии. */}
              {aiResult.recommendedAmount != null && (
                <div style={{
                  textAlign: 'right', paddingLeft: 20, borderLeft: '1px solid var(--separator)',
                  minWidth: 170,
                }}>
                  <div style={{
                    fontSize: 11, fontWeight: 600, color: 'var(--label-tertiary)',
                    textTransform: 'uppercase', letterSpacing: 0.4, marginBottom: 4,
                  }}>
                    {t('commissionCard.recommendedAmount')}
                  </div>
                  <div style={{ fontSize: 19, fontWeight: 700, color: 'var(--accent)', fontVariantNumeric: 'tabular-nums' }}>
                    {formatAmount(aiResult.recommendedAmount, i18n.language)} ₸
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--label-tertiary)', marginTop: 2 }}>
                    {t('commissionCard.recommendedHint')}
                  </div>
                </div>
              )}
            </div>

            <div style={{ padding: '16px', borderRadius: 12, background: 'var(--bg-secondary)', marginBottom: 16 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--label-secondary)', marginBottom: 6 }}>{t('commissionCard.aiConclusionLabel')}</div>
              <div style={{ fontSize: 14, color: 'var(--label-primary)', lineHeight: 1.6 }}>{aiResult.summary}</div>
              {aiResult.model && <div style={{ fontSize: 11, color: 'var(--label-tertiary)', marginTop: 8 }}>{t('commissionCard.model')}: {aiResult.model}</div>}
            </div>

            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {aiResult.factors.map((f) => (
                <span key={f} style={{
                  padding: '5px 12px', borderRadius: 999,
                  background: '#e8f5e9', color: '#1a7a4a',
                  fontSize: 12, fontWeight: 600,
                }}>
                  {f}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Documents */}
      {detail.documents.length > 0 && (
        <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 16, padding: '24px 28px', marginBottom: 16 }}>
          <SectionTitle>{t('commissionCard.documents')} · {detail.documents.length}</SectionTitle>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {detail.documents.map((doc) => (
              <button key={doc.id} type="button" onClick={() => handleDocumentDownload(doc.id, doc.originalFileName)} style={{
                display: 'flex', alignItems: 'center', gap: 8,
                padding: '8px 14px', borderRadius: 10,
                border: '1px solid var(--separator)', background: 'var(--bg-secondary)',
                fontSize: 13, color: 'var(--label-primary)', cursor: 'pointer',
              }}>
                <FileSearchOutlined style={{ color: '#0071e3' }} />
                {doc.originalFileName}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Decisions history */}
      {detail.decisions.length > 0 && (
        <div style={{ background: 'var(--bg-elevated)', border: '1px solid var(--separator)', borderRadius: 16, padding: '24px 28px' }}>
          <SectionTitle>{t('commissionCard.decisionHistory')}</SectionTitle>
          {detail.decisions.map((d, i) => (
            <div key={i} style={{ padding: '14px 16px', borderRadius: 10, border: '1px solid var(--separator)', marginBottom: 10 }}>
              <div style={{ fontWeight: 600, color: 'var(--label-primary)', fontSize: 14, marginBottom: 4 }}>{d.decisionType}</div>
              <div style={{ fontSize: 13, color: 'var(--label-secondary)', marginBottom: 4 }}>{d.reason}</div>
              <div style={{ fontSize: 12, color: 'var(--label-tertiary)' }}>— {d.decidedByName}</div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={decisionAction !== null}
        title={decisionAction === 'approve' ? t('commissionCard.approveTitle') : decisionAction === 'reject' ? t('commissionCard.rejectTitle') : t('commissionCard.docsTitle')}
        okText={decisionAction === 'approve' ? t('commission.approve') : decisionAction === 'reject' ? t('commission.reject') : t('commissionCard.sendRequest')}
        cancelText={t('common.cancel')}
        okButtonProps={{ danger: decisionAction === 'reject', loading: submittingDecision }}
        onOk={handleDecision}
        onCancel={() => setDecisionAction(null)}
        centered
      >
        {decisionAction === 'approve' && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 7 }}>{t('commissionCard.approvedAmount')}</div>
            <InputNumber<number>
              min={1}
              max={Number(detail.requestedAmount)}
              value={approvedAmount}
              onChange={setApprovedAmount}
              addonAfter="₸"
              style={{ width: '100%' }}
            />
          </div>
        )}
        {decisionAction === 'documents' && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 7 }}>{t('commissionCard.whichDocuments')}</div>
            <Select mode="multiple" value={documentTypes} onChange={setDocumentTypes} options={DOCUMENT_VALUES.map((value) => ({ value, label: t(documentLabelKey(value)) }))}
              style={{ width: '100%' }} placeholder={t('commissionCard.whichDocuments')} />
          </div>
        )}
        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 7 }}>
          {decisionAction === 'documents' ? t('commissionCard.whatToFix') : t('commissionCard.decisionReason')}
        </div>
        <Input.TextArea value={reason} onChange={(event) => setReason(event.target.value)} rows={4} maxLength={2000} showCount placeholder={t('commissionCard.commentPlaceholder')} />
      </Modal>
    </div>
  )
}

function btn(variant: 'primary' | 'danger' | 'secondary' | 'ghost'): React.CSSProperties {
  const base: React.CSSProperties = {
    display: 'inline-flex', alignItems: 'center', gap: 6,
    padding: '8px 16px', borderRadius: 10, fontSize: 13, fontWeight: 600,
    cursor: 'pointer', transition: 'all 0.15s',
  }
  if (variant === 'primary') return { ...base, background: 'var(--accent)', color: '#fff', border: 'none' }
  if (variant === 'danger') return { ...base, background: '#fff0f0', color: '#d32f2f', border: '1px solid #ffcdd2' }
  if (variant === 'secondary') return { ...base, background: 'var(--bg-secondary)', color: 'var(--label-primary)', border: '1px solid var(--separator)' }
  return { ...base, background: 'transparent', color: 'var(--label-secondary)', border: '1px solid var(--separator)' }
}

/** Плитка автопроверки: null — проверка не выполнялась, поэтому не красим её отказом. */
function CheckTile({ label, ok, okText, failText }: {
  label: string
  ok: boolean | null | undefined
  okText: string
  failText: string
}) {
  const neutral = ok === null || ok === undefined
  const color = neutral ? 'var(--label-tertiary)' : ok ? 'var(--accent)' : '#b42318'
  const background = neutral ? 'var(--bg-secondary)' : ok ? 'var(--accent-light)' : '#fdeceb'
  return (
    <div style={{ border: '1px solid var(--separator)', borderRadius: 10, padding: '14px 16px', background }}>
      <div style={{ fontSize: 12, color: 'var(--label-tertiary)', marginBottom: 6 }}>{label}</div>
      <div style={{ fontSize: 14, fontWeight: 600, color }}>
        {neutral ? failText : ok ? okText : failText}
      </div>
    </div>
  )
}
