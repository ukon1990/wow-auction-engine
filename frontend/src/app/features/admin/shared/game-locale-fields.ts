import { GameLocale } from '@api/generated';

export type GameLocaleFieldKey = keyof GameLocale;

export interface GameLocaleField {
  readonly key: GameLocaleFieldKey;
  readonly label: string;
}

export const GAME_LOCALE_FIELDS: readonly GameLocaleField[] = [
  { key: 'en_US', label: 'English (US)' },
  { key: 'en_GB', label: 'English (GB)' },
  { key: 'de_DE', label: 'Deutsch' },
  { key: 'es_ES', label: 'Español (ES)' },
  { key: 'es_MX', label: 'Español (MX)' },
  { key: 'fr_FR', label: 'Français' },
  { key: 'it_IT', label: 'Italiano' },
  { key: 'ko_KR', label: '한국어' },
  { key: 'pt_BR', label: 'Português (BR)' },
  { key: 'pt_PT', label: 'Português (PT)' },
  { key: 'ru_RU', label: 'Русский' },
  { key: 'zh_CN', label: '中文 (简体)' },
  { key: 'zh_TW', label: '中文 (繁體)' },
];

export function emptyGameLocale(): GameLocale {
  return {};
}

export function hasEnglishGameLocale(locale: GameLocale): boolean {
  return Boolean(locale.en_US?.trim() || locale.en_GB?.trim());
}

export function updateGameLocaleField(
  locale: GameLocale,
  key: GameLocaleFieldKey,
  value: string,
): GameLocale {
  return {
    ...locale,
    [key]: value.length > 0 ? value : null,
  };
}
