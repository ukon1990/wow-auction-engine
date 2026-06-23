import { GameLocale } from '@api/generated';

export function englishGameLocale(name: string): GameLocale {
  return {
    en_US: name,
    en_GB: name,
  };
}
