import type { AxiosResponse } from 'axios'

const fileNameFromDisposition = (header: string | undefined, fallback: string) => {
  const encoded = header?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) return decodeURIComponent(encoded)
  return header?.match(/filename="?([^";]+)"?/i)?.[1] ?? fallback
}

export const downloadBlobResponse = (response: AxiosResponse<Blob>, fallbackName: string) => {
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = fileNameFromDisposition(response.headers['content-disposition'], fallbackName)
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
