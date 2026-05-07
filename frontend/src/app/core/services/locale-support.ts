export const SUPPORTED_APP_LOCALES = [
  'en',
  'de',
  'es',
  'fr',
  'it',
  'ko',
  'pt',
  'ru',
  'zh',
] as const;

export type AppLocale = (typeof SUPPORTED_APP_LOCALES)[number];

export interface LocaleOption {
  readonly id: AppLocale;
  readonly label: string;
}

export const LOCALE_OPTIONS: readonly LocaleOption[] = [
  { id: 'en', label: 'English' },
  { id: 'de', label: 'Deutsch' },
  { id: 'es', label: 'Español' },
  { id: 'fr', label: 'Français' },
  { id: 'it', label: 'Italiano' },
  { id: 'ko', label: '한국어' },
  { id: 'pt', label: 'Português' },
  { id: 'ru', label: 'Русский' },
  { id: 'zh', label: '中文' },
];

export const LOCALE_OVERRIDE_STORAGE_KEY = 'wae.localeOverride';
export const LOCALE_OVERRIDE_COOKIE_KEY = 'wae_locale';

const supportedSet = new Set<string>(SUPPORTED_APP_LOCALES);
const realmRegionSet = new Set(['us', 'eu', 'kr', 'tw']);

const blizzardToAppLocale: Record<string, AppLocale> = {
  en_us: 'en',
  en_gb: 'en',
  de_de: 'de',
  es_es: 'es',
  es_mx: 'es',
  fr_fr: 'fr',
  it_it: 'it',
  ko_kr: 'ko',
  pt_br: 'pt',
  pt_pt: 'pt',
  ru_ru: 'ru',
  zh_cn: 'zh',
  zh_tw: 'zh',
};

const canonicalBlizzardLocale: Record<AppLocale, string> = {
  en: 'en_US',
  de: 'de_DE',
  es: 'es_ES',
  fr: 'fr_FR',
  it: 'it_IT',
  ko: 'ko_KR',
  pt: 'pt_BR',
  ru: 'ru_RU',
  zh: 'zh_CN',
};

export function isAppLocale(value: string | null | undefined): value is AppLocale {
  return Boolean(value && supportedSet.has(value.toLowerCase()));
}

export function normalizeAppLocale(value: string | null | undefined): AppLocale | null {
  if (!value) return null;
  const language = value.toLowerCase().split(/[-_]/)[0];
  return isAppLocale(language) ? language : null;
}

export function blizzardLocaleToAppLocale(value: string | null | undefined): AppLocale {
  if (!value) return 'en';
  return blizzardToAppLocale[value.toLowerCase()] ?? normalizeAppLocale(value) ?? 'en';
}

export function appLocaleToCanonicalBlizzardLocale(locale: AppLocale): string {
  return canonicalBlizzardLocale[locale];
}

export function apiLocaleOverrideFor(
  activeAppLocale: AppLocale,
  realmLocale: string | null | undefined,
): string | undefined {
  return blizzardLocaleToAppLocale(realmLocale) === activeAppLocale
    ? undefined
    : appLocaleToCanonicalBlizzardLocale(activeAppLocale);
}

export function appLocaleFromPath(pathname: string): AppLocale | null {
  const [, firstSegment] = pathname.split('/');
  return isAppLocale(firstSegment) ? firstSegment : null;
}

export function stripLocalePrefix(pathname: string): string {
  const parts = pathname.split('/');
  const firstSegment = parts[1] ?? '';
  const normalizedSegment = firstSegment.toLowerCase();
  const unsupportedLocaleLike =
    /^[a-z]{2}(?:-[a-z]{2})?$/i.test(firstSegment) && !realmRegionSet.has(normalizedSegment);
  if (isAppLocale(firstSegment) || unsupportedLocaleLike) {
    const stripped = `/${parts.slice(2).join('/')}`;
    return stripped === '/' ? '/' : stripped.replace(/\/+$/, '') || '/';
  }
  return pathname || '/';
}

export function localizedPath(pathname: string, locale: AppLocale): string {
  const stripped = stripLocalePrefix(pathname);
  return stripped === '/' ? `/${locale}/` : `/${locale}${stripped}`;
}
