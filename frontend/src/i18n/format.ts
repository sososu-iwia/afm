/**
 * Форматирование чисел и дат по выбранному языку интерфейса.
 *
 * Ни голая локаль 'ru-RU', ни toLocaleString() без аргументов не подходят:
 * первая игнорирует выбор пользователя, вторая берёт настройку браузера.
 */
import type { SupportedLanguage } from './index'

const INTL_LOCALES: Record<SupportedLanguage, string> = {
  ru: 'ru-RU',
  kz: 'kk-KZ',
  en: 'en-US',
}

export const intlLocale = (language: string): string =>
  INTL_LOCALES[language as SupportedLanguage] ?? INTL_LOCALES.ru

export const formatAmount = (value: number | string | null | undefined, language: string): string => {
  if (value == null || value === '') return '—'
  return Number(value).toLocaleString(intlLocale(language))
}

export const formatDate = (
  value: string | null | undefined,
  language: string,
  options?: Intl.DateTimeFormatOptions,
): string => {
  if (!value) return '—'
  return new Date(value).toLocaleDateString(intlLocale(language), options)
}

export const formatDateTime = (value: string | null | undefined, language: string): string => {
  if (!value) return '—'
  return new Date(value).toLocaleString(intlLocale(language))
}
