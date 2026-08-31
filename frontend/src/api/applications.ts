import { apiClient } from './client'

export type CreateApplicationPayload = {
  iinBin: string
  productType: string
  region: string
  landArea: number
  requestedAmount: number
}

export type DocumentType = 'IIN_CERTIFICATE' | 'LAND_CERTIFICATE' | 'TAX_CERTIFICATE' | 'BANK_STATEMENT' | 'BUSINESS_PLAN' | 'OTHER'

export type PageResponse<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export type BackendApplication = { id: string; applicationNumber: string; applicantId: string; status: string; iinOrBin: string; region: string; productionType: string; landArea: number; requestedAmount: number; createdAt: string; updatedAt: string }
export type AiFactor = {
  factor?: string
  feature?: string
  weight?: number
  direction?: string
  impact?: string
  value?: string | number | null
}

export type ApplicationProcessing = {
  applicationId: string
  overallStatus: string
  completeness?: {
    complete: boolean
    missingFields: string[]
    missingDocuments: string[]
    message: string | null
  }
  ocr?: {
    status: string
    documentsTotal: number
    documentsProcessed: number
    documentsFailed: number
  }
  scoring: {
    status: string
    score: number | null
    riskCategory: 'LOW' | 'MEDIUM' | 'HIGH' | null
    modelName: string | null
    modelVersion: string | null
    recommendedAmount: number | null
    topFactors: AiFactor[]
  }
  llmConclusion: {
    status: string
    text: string | null
    errorCode: string | null
    errorMessage: string | null
  }
  duplicateCheck: {
    status: string
    hasDuplicates: boolean | null
    duplicateType?: string | null
    matchedApplicationId?: string | null
    flags: string[]
    anomalies: string[]
  }
}

export type AnalyticsSummary = {
  totalApplications: number
  applicationsByStatus: Record<string, number>
  totalRequestedAmount: number
  totalApprovedAmount: number
  averageRequestedAmount: number
  averageProcessingHours: number
  approvedDecisions: number
  rejectedDecisions: number
  additionalDocumentsRequestedDecisions: number
  completedAiScoringApplications: number
  failedAiTasks: number
}

export type RegionMetric = { region: string; applicationCount: number; requestedAmount: number; approvedAmount: number }
export type TrendMetric = { date: string; applicationCount: number; requestedAmount: number }
export type DecisionRate = { approvedApplications: number; rejectedApplications: number; finalApplications: number; approvalRatePercent: number; rejectionRatePercent: number }

export const applicationApi = {
  getApplications: (params?: { page?: number; size?: number }) => apiClient.get<PageResponse<BackendApplication>>('/applications', { params }),
  createApplication: (payload: CreateApplicationPayload) =>
    apiClient.post<BackendApplication>('/applications', {
      iinOrBin: payload.iinBin,
      productionType: payload.productType,
      region: payload.region,
      landArea: payload.landArea,
      requestedAmount: payload.requestedAmount,
    }),
  uploadDocument: (applicationId: string, file: File, documentType: DocumentType) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('documentType', documentType)

    return apiClient.post(`/applications/${applicationId}/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  submitApplication: (id: string) =>
    apiClient.post(`/applications/${id}/submit`),

  withdrawApplication: (id: string) =>
    apiClient.post(`/applications/${id}/withdraw`),
  deleteDraft: (id: string) => apiClient.delete(`/applications/${id}`),

  getCompleteness: (id: string) =>
    apiClient.get(`/applications/${id}/completeness`),

  getStatusHistory: (id: string) =>
    apiClient.get(`/applications/${id}/status-history`),

  approveApplication: (id: string, reason: string, approvedAmount: number) =>
    apiClient.post(`/commission/applications/${id}/approve`, { reason, approvedAmount }),

  rejectApplication: (id: string, reason: string) =>
    apiClient.post(`/commission/applications/${id}/reject`, { reason }),

  requestDocuments: (id: string, reason: string, documentTypes: string[]) =>
    apiClient.post(`/commission/applications/${id}/request-documents`, { reason, documentTypes }),

  getCommissionApplications: (params?: { page?: number; size?: number; status?: string; region?: string; minAmount?: number }) =>
    apiClient.get('/commission/applications', { params }),

  publishApplication: (id: string) =>
    apiClient.patch(`/commission/applications/${id}/publish`),

  unpublishApplication: (id: string) =>
    apiClient.patch(`/commission/applications/${id}/unpublish`),

  getCommissionApplicationDetail: (id: string) =>
    apiClient.get(`/commission/applications/${id}`),

  exportCommissionApplications: (params?: { status?: string; region?: string; minAmount?: number; language?: string }) =>
    apiClient.get<Blob>('/commission/applications/export', { params, responseType: 'blob' }),

  generateProtocol: (id: string, language: string) =>
    apiClient.post<Blob>(`/commission/applications/${id}/generate-protocol`, undefined, {
      params: { language },
      responseType: 'blob',
    }),

  downloadDocument: (id: string) =>
    apiClient.get<Blob>(`/documents/${id}/download`, { responseType: 'blob' }),

  getPublicRegistry: (params?: { page?: number; size?: number }) =>
    apiClient.get('/public/approved-applications', { params }),

  getPublicAnalytics: () =>
    apiClient.get('/public/analytics'),

  getAnalyticsSummary: () => apiClient.get<AnalyticsSummary>('/analytics/summary'),
  getAnalyticsRegions: () => apiClient.get<PageResponse<RegionMetric>>('/analytics/applications-by-region', { params: { page: 1, size: 100 } }),
  getAnalyticsTrend: () => apiClient.get<PageResponse<TrendMetric>>('/analytics/application-trend', { params: { page: 1, size: 100 } }),
  getAnalyticsDecisionRate: () => apiClient.get<DecisionRate>('/analytics/approval-rate'),

  triggerApplicationScore: (id: string) =>
    apiClient.post(`/applications/${id}/score`, undefined, {
      headers: { 'Idempotency-Key': `score-${id}-${crypto.randomUUID()}` },
    }),

  retryLlmConclusion: (id: string) =>
    apiClient.post(`/applications/${id}/llm-conclusion/retry`),

  getApplicationProcessing: (id: string) =>
    apiClient.get<ApplicationProcessing>(`/applications/${id}/processing`),
}
