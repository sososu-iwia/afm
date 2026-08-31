import type { SupportedLanguage } from './index'

/** Меняет язык и запоминает выбор, чтобы он пережил перезагрузку. */
export const changeAppLanguage = (
  i18n: { changeLanguage: (lng: string) => void },
  language: SupportedLanguage,
) => {
  i18n.changeLanguage(language)
  localStorage.setItem('afm-lang', language)
}
