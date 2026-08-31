import type { SupportedLanguage } from './index'
import { SUPPORTED_LANGUAGES } from './index'

/** Язык генерируемых документов хранится отдельно от языка интерфейса. */
export const DOCUMENT_LANGUAGE_KEY = 'afm-doc-lang'

export const getDocumentLanguage = (fallback: string = 'ru'): SupportedLanguage => {
  const stored = localStorage.getItem(DOCUMENT_LANGUAGE_KEY)
  if (stored && (SUPPORTED_LANGUAGES as readonly string[]).includes(stored)) {
    return stored as SupportedLanguage
  }
  return (SUPPORTED_LANGUAGES as readonly string[]).includes(fallback)
    ? (fallback as SupportedLanguage)
    : 'ru'
}
