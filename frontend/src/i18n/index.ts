import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import ru from './ru'
import kz from './kz'
import en from './en'

export const SUPPORTED_LANGUAGES = ['ru', 'kz', 'en'] as const
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number]

const stored = localStorage.getItem('afm-lang')

i18n
  .use(initReactI18next)
  .init({
    resources: {
      ru: { translation: ru },
      kz: { translation: kz },
      en: { translation: en },
    },
    lng: SUPPORTED_LANGUAGES.includes(stored as SupportedLanguage) ? stored! : 'ru',
    supportedLngs: SUPPORTED_LANGUAGES,
    fallbackLng: 'ru',
    interpolation: { escapeValue: false },
    returnObjects: true,
  })

export default i18n
