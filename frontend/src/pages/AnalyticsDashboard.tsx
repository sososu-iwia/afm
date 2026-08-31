import { useEffect, useMemo, useState } from 'react'
import { Alert, Spin } from 'antd'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, LineChart, Line } from 'recharts'
import { useTranslation } from 'react-i18next'
import { applicationApi, type AnalyticsSummary, type DecisionRate, type RegionMetric, type TrendMetric } from '../api/applications'
import { formatDate } from '../i18n/format'

/** Ключи словаря: подписи подставляются при рендере. */
const STATUS_LABEL_KEYS: Record<string, string> = {
  DRAFT: 'analyticsExtra.drafts', SUBMITTED: 'analyticsExtra.sent', IN_REVIEW: 'queue.inReview',
  ADDITIONAL_DOCUMENTS_REQUESTED: 'status.documentsRequested', APPROVED: 'queue.approvedCount',
  REJECTED: 'analyticsExtra.rejected', WITHDRAWN: 'analyticsExtra.withdrawn',
}
const STATUS_COLORS: Record<string, string> = {
  DRAFT: '#8e8e93', SUBMITTED: '#f59e0b', IN_REVIEW: '#0071e3',
  ADDITIONAL_DOCUMENTS_REQUESTED: '#ff6d00', APPROVED: '#1a7a4a',
  REJECTED: '#d32f2f', WITHDRAWN: '#c7c7cc',
}
const moneyMln = (value: number) => Number((Number(value || 0) / 1_000_000).toFixed(1))

function Kpi({ label, value, hint, color }: { label: string; value: string | number; hint: string; color?: string }) {
  return <div className="metric-block"><span>{label}</span><strong style={{ color }}>{value}</strong><small>{hint}</small></div>
}

const pluralDecisionsKey = (n: number) => {
  const mod10 = n % 10
  const mod100 = n % 100
  if (mod10 === 1 && mod100 !== 11) return 'analyticsExtra.decisions2'
  return 'analyticsExtra.decisions5'
}

export default function AnalyticsDashboard() {
  const { t, i18n } = useTranslation()
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [regions, setRegions] = useState<RegionMetric[]>([])
  const [trend, setTrend] = useState<TrendMetric[]>([])
  const [rates, setRates] = useState<DecisionRate | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([
      applicationApi.getAnalyticsSummary(), applicationApi.getAnalyticsRegions(),
      applicationApi.getAnalyticsTrend(), applicationApi.getAnalyticsDecisionRate(),
    ])
      .then(([summaryResponse, regionResponse, trendResponse, rateResponse]) => {
        setSummary(summaryResponse.data)
        setRegions(regionResponse.data.content ?? [])
        setTrend((trendResponse.data.content ?? []).sort((a, b) => a.date.localeCompare(b.date)))
        setRates(rateResponse.data)
      })
      .catch(() => setError(t('analyticsExtra.loadFailed')))
      .finally(() => setLoading(false))
  }, [t])

  const statusData = useMemo(() => Object.entries(summary?.applicationsByStatus ?? {})
    .filter(([, value]) => value > 0)
    .map(([status, value]) => ({
      name: t(STATUS_LABEL_KEYS[status] ?? status),
      value,
      color: STATUS_COLORS[status] ?? '#8e8e93',
    })), [summary, t])
  const regionData = regions.map((item) => ({ ...item, requestedMln: moneyMln(item.requestedAmount), approvedMln: moneyMln(item.approvedAmount) }))
  const trendData = trend.map((item) => ({ ...item, label: formatDate(`${item.date}T00:00:00`, i18n.language, { day: '2-digit', month: 'short' }) }))
  const processingDays = summary?.averageProcessingHours ? (summary.averageProcessingHours / 24).toFixed(1) : '0'

  if (loading) return <div style={{ padding: 80, textAlign: 'center' }}><Spin size="large" /></div>

  return <div style={{ width: '100%' }}>
    <header className="page-heading"><h1>{t('analytics.title')}</h1><p>{t('analytics.subtitle')}</p></header>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 20 }} />}
    <section className="metric-strip">
      <Kpi label={t('analytics.totalApplications')} value={summary?.totalApplications ?? 0} hint={t('analyticsExtra.inSystem')} />
      <Kpi label={t('analytics.totalAmount')} value={`${moneyMln(summary?.totalRequestedAmount ?? 0)} ${t('analyticsExtra.mln')}`} hint={t('analyticsExtra.totalVolume')} color="var(--accent)" />
      <Kpi label={t('queue.approvedCount')} value={`${Number(rates?.approvalRatePercent ?? 0).toFixed(1)}%`} hint={`${rates?.approvedApplications ?? 0} ${t('analyticsExtra.of')} ${rates?.finalApplications ?? 0} ${t(pluralDecisionsKey(rates?.finalApplications ?? 0))}`} color="#1a7a4a" />
      <Kpi label={t('analytics.avgTime')} value={`${processingDays} ${t('analyticsExtra.days')}`} hint={t('analyticsExtra.avgActual')} color="#0071e3" />
    </section>

    <div className="analytics-grid">
      <section className="data-section analytics-wide">
        <div className="section-heading"><h2>{t('analytics.dynamics')}</h2><span>{t('analyticsExtra.realDates')}</span></div>
        <ResponsiveContainer width="100%" height={250}>
          <LineChart data={trendData} margin={{ top: 12, right: 20, left: -12, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.06)" />
            <XAxis dataKey="label" tick={{ fontSize: 12, fill: '#8e8e93' }} axisLine={false} tickLine={false} />
            <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: '#8e8e93' }} axisLine={false} tickLine={false} />
            <Tooltip contentStyle={{ borderRadius: 6, border: '1px solid var(--separator)', fontSize: 13 }} />
            <Line type="monotone" dataKey="applicationCount" name={t('analyticsExtra.applicationsWord')} stroke="#0071e3" strokeWidth={2.5} dot={{ r: 3 }} />
          </LineChart>
        </ResponsiveContainer>
      </section>

      <section className="data-section">
        <div className="section-heading"><h2>{t('analytics.byRegion')}</h2><span>{t('analyticsExtra.requestedMln')}</span></div>
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={regionData} margin={{ top: 8, right: 10, left: -14, bottom: 18 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.06)" />
            <XAxis dataKey="region" tick={{ fontSize: 10, fill: '#8e8e93' }} angle={-12} textAnchor="end" axisLine={false} tickLine={false} />
            <YAxis tick={{ fontSize: 11, fill: '#8e8e93' }} axisLine={false} tickLine={false} />
            <Tooltip formatter={(value) => [`${value} ${t('analyticsExtra.mln')}`]} contentStyle={{ borderRadius: 6, border: '1px solid var(--separator)' }} />
            <Bar dataKey="requestedMln" fill="#1a7a4a" radius={[4, 4, 0, 0]} maxBarSize={42} />
          </BarChart>
        </ResponsiveContainer>
      </section>

      <section className="data-section">
        <div className="section-heading"><h2>{t('analytics.byStatus')}</h2><span>{summary?.totalApplications ?? 0} {t('analyticsExtra.applicationsCount')}</span></div>
        <div className="status-chart">
          <ResponsiveContainer width="58%" height={240}>
            <PieChart><Pie data={statusData} innerRadius={58} outerRadius={88} paddingAngle={2} dataKey="value" strokeWidth={0}>{statusData.map((item) => <Cell key={item.name} fill={item.color} />)}</Pie><Tooltip /></PieChart>
          </ResponsiveContainer>
          <div className="chart-legend">{statusData.map((item) => <div key={item.name}><i style={{ background: item.color }} /><span>{item.name}</span><strong>{item.value}</strong></div>)}</div>
        </div>
      </section>
    </div>

    <section className="data-section" style={{ marginTop: 20 }}>
      <div className="section-heading"><h2>{t('analyticsExtra.regions')}</h2><span>{t('analyticsExtra.regionsCompare')}</span></div>
      <div className="simple-table"><div className="simple-row simple-head"><span>{t('analytics.region')}</span><span>{t('analyticsExtra.applicationsWord')}</span><span>{t('analytics.totalAmount')}</span><span>{t('queue.approvedCount')}</span></div>{regionData.map((row) => <div className="simple-row" key={row.region}><strong>{row.region}</strong><span>{row.applicationCount}</span><span>{row.requestedMln} {t('analyticsExtra.mln')}</span><span>{row.approvedMln} {t('analyticsExtra.mln')}</span></div>)}</div>
    </section>
  </div>
}
