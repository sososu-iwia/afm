import { Tag } from 'antd'
import { useTranslation } from 'react-i18next'
import type { ApplicationStatus } from '../types/application'

const statusColor: Record<ApplicationStatus, string> = {
  draft: 'default',
  submitted: 'blue',
  review: 'gold',
  approved: 'green',
  rejected: 'red',
  documentsRequested: 'orange',
}

type StatusBadgeProps = {
  status: ApplicationStatus
}

export default function StatusBadge({ status }: StatusBadgeProps) {
  const { t } = useTranslation()
  return <Tag color={statusColor[status]}>{t(`status.${status}`)}</Tag>
}
