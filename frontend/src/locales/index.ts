import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

/** 持久化的语言偏好 key */
const LOCALE_KEY = 'web3-locale'

export function loadLocale(): string {
  const saved = localStorage.getItem(LOCALE_KEY)
  return saved === 'en' ? 'en' : 'zh'
}

export function saveLocale(locale: string) {
  localStorage.setItem(LOCALE_KEY, locale)
}

const i18n = createI18n({
  legacy: false,
  locale: loadLocale(),
  fallbackLocale: 'zh',
  messages: {
    zh: zhCN,
    en: enUS,
  },
})

export default i18n
