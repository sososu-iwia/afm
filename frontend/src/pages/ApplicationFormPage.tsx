import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Upload,
  message,
} from 'antd'
import { ArrowLeftOutlined, InboxOutlined, SendOutlined, FormOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type { UploadFile } from 'antd/es/upload/interface'
import { applicationApi, type CreateApplicationPayload, type DocumentType } from '../api/applications'
import PageHeader from '../components/PageHeader'

const { Dragger } = Upload

const regions = [
  'Акмолинская область',
  'Костанайская область',
  'Туркестанская область',
  'Алматинская область',
  'Северо-Казахстанская область',
]

const productTypes = [
  'Пшеница',
  'Ячмень',
  'Масличные культуры',
  'Овощи',
  'Молочная продукция',
]

const DOCUMENT_TYPE_VALUES: DocumentType[] = [
  'IIN_CERTIFICATE', 'LAND_CERTIFICATE', 'TAX_CERTIFICATE',
  'BANK_STATEMENT', 'BUSINESS_PLAN', 'OTHER',
]

/** BANK_STATEMENT исторически описан как банковская выписка. */
const documentLabelKey = (value: DocumentType) =>
  value === 'BANK_STATEMENT' ? 'docTypes.FINANCIAL_STATEMENT' : `docTypes.${value}`

type PendingFile = UploadFile & { documentType: DocumentType }

export default function ApplicationFormPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [form] = Form.useForm<CreateApplicationPayload>()
  const [files, setFiles] = useState<PendingFile[]>([])
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (values: CreateApplicationPayload) => {
    setSubmitting(true)
    try {
      const res = await applicationApi.createApplication(values)
      const applicationId = res.data.id
      const uploads = await Promise.allSettled(
        files
          .filter((f) => f.originFileObj)
          .map((f) => applicationApi.uploadDocument(applicationId, f.originFileObj!, f.documentType)),
      )
      if (uploads.some((result) => result.status === 'rejected')) {
        message.warning(t('formExtra.docsFailed'))
      } else {
        message.success(`${values.productType.toLowerCase()} — ${t('form.successDraft')}`)
      }
      navigate(`/applicant/${applicationId}`)
    } catch {
      message.error(t('formExtra.saveFailed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Space direction="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        title={t('form.title')}
        subtitle={t('form.subtitle')}
        accent="#2c4a7c"
        icon={<FormOutlined />}
        chips={[
          { label: t('form.chips.iinBin'), color: 'green' },
          { label: t('form.chips.formats'), color: 'blue' },
          { label: t('form.chips.size'), color: 'sky' },
        ]}
        actions={(
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/applicant')}>
            {t('form.back')}
          </Button>
        )}
      />

      <Row gutter={[20, 20]}>
        <Col xs={24} lg={15}>
          <Card title={t('form.applicationData')}>
            <Form
              form={form}
              layout="vertical"
              requiredMark="optional"
              onFinish={handleSubmit}
            >
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item
                    label={t('form.iinBin')}
                    name="iinBin"
                    rules={[
                      { required: true, message: t('form.iinBinRequired') },
                      { pattern: /^\d{12}$/, message: t('form.iinBinPattern') },
                    ]}
                  >
                    <Input inputMode="numeric" maxLength={12} placeholder={t('form.iinBinPlaceholder')} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    label={t('form.region')}
                    name="region"
                    rules={[{ required: true, message: t('form.regionRequired') }]}
                  >
                    <Select
                      placeholder={t('form.regionPlaceholder')}
                      options={regions.map((region) => ({ value: region, label: region }))}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item
                    label={t('form.productType')}
                    name="productType"
                    rules={[{ required: true, message: t('form.productRequired') }]}
                  >
                    <Select
                      placeholder={t('form.productPlaceholder')}
                      options={productTypes.map((type) => ({ value: type, label: type }))}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    label={t('form.landArea')}
                    name="landArea"
                    rules={[{ required: true, message: t('form.landAreaRequired') }]}
                  >
                    <InputNumber<number> min={1} addonAfter="га" style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item
                label={t('form.requestedAmount')}
                name="requestedAmount"
                rules={[{ required: true, message: t('form.amountRequired') }]}
              >
                <InputNumber<number>
                  min={100000}
                  step={100000}
                  addonAfter="₸"
                  style={{ width: '100%' }}
                  formatter={(value) => `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ' ')}
                  parser={(value) => Number(value?.replace(/\s/g, '') || 0)}
                />
              </Form.Item>

              <Space wrap>
                <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={submitting}>
                  {submitting ? t('form.saving') : t('form.save')}
                </Button>
                <Button onClick={() => form.resetFields()}>{t('form.clear')}</Button>
              </Space>
            </Form>
          </Card>
        </Col>

        <Col xs={24} lg={9}>
          <Card title={t('form.documents')}>
            <Dragger
              multiple
              fileList={files}
              beforeUpload={(file) => {
                const isAllowed = ['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)
                const isSmallEnough = file.size / 1024 / 1024 < 10

                if (!isAllowed) {
                  message.error(t('form.errorOnly'))
                  return Upload.LIST_IGNORE
                }

                if (!isSmallEnough) {
                  message.error(t('form.errorSize'))
                  return Upload.LIST_IGNORE
                }

                return false
              }}
              onChange={({ fileList }) => setFiles(fileList.map((file) => {
                const existing = files.find((item) => item.uid === file.uid)
                return { ...file, documentType: existing?.documentType ?? 'OTHER' }
              }))}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">{t('form.dragFiles')}</p>
              <p className="ant-upload-hint">{t('form.dragHint')}</p>
            </Dragger>
            {files.length > 0 && (
              <div style={{ marginTop: 14, display: 'grid', gap: 10 }}>
                {files.map((file) => (
                  <div key={file.uid} style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 190px', gap: 10, alignItems: 'center' }}>
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 13 }}>{file.name}</span>
                    <Select<DocumentType>
                      value={file.documentType}
                      options={DOCUMENT_TYPE_VALUES.map((value) => ({ value, label: t(documentLabelKey(value)) }))}
                      onChange={(documentType) => setFiles((current) => current.map((item) => item.uid === file.uid ? { ...item, documentType } : item))}
                      aria-label={`Тип документа ${file.name}`}
                    />
                  </div>
                ))}
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </Space>
  )
}
