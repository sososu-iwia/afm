export type ApplicationStatus =
  | 'draft'
  | 'submitted'
  | 'review'
  | 'approved'
  | 'rejected'
  | 'documentsRequested'

export type RiskLevel = 'low' | 'medium' | 'high'

export type ApplicationDocument = {
  id: string
  name: string
  type: 'identity' | 'land' | 'tax' | 'financial' | 'other'
  status: 'uploaded' | 'missing' | 'checking' | 'accepted' | 'rejected'
  uploadedAt?: string
}

export type Application = {
  id: string
  realId: string
  applicantName: string
  iinBin: string
  productType: string
  region: string
  landArea: number
  requestedAmount: number
  status: ApplicationStatus
  submittedAt: string
  documents: ApplicationDocument[]
  score?: number
  riskLevel?: RiskLevel
}
