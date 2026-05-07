import {
  apiLocaleOverrideFor,
  appLocaleToCanonicalBlizzardLocale,
  blizzardLocaleToAppLocale,
  localizedPath,
  stripLocalePrefix,
} from './locale-support';

describe('locale support', () => {
  it('maps every Blizzard locale to a collapsed app locale', () => {
    expect(blizzardLocaleToAppLocale('en_US')).toBe('en');
    expect(blizzardLocaleToAppLocale('en_GB')).toBe('en');
    expect(blizzardLocaleToAppLocale('de_DE')).toBe('de');
    expect(blizzardLocaleToAppLocale('es_ES')).toBe('es');
    expect(blizzardLocaleToAppLocale('es_MX')).toBe('es');
    expect(blizzardLocaleToAppLocale('fr_FR')).toBe('fr');
    expect(blizzardLocaleToAppLocale('it_IT')).toBe('it');
    expect(blizzardLocaleToAppLocale('ko_KR')).toBe('ko');
    expect(blizzardLocaleToAppLocale('pt_BR')).toBe('pt');
    expect(blizzardLocaleToAppLocale('pt_PT')).toBe('pt');
    expect(blizzardLocaleToAppLocale('ru_RU')).toBe('ru');
    expect(blizzardLocaleToAppLocale('zh_CN')).toBe('zh');
    expect(blizzardLocaleToAppLocale('zh_TW')).toBe('zh');
  });

  it('uses canonical Blizzard locales for override requests', () => {
    expect(appLocaleToCanonicalBlizzardLocale('en')).toBe('en_US');
    expect(appLocaleToCanonicalBlizzardLocale('es')).toBe('es_ES');
    expect(appLocaleToCanonicalBlizzardLocale('pt')).toBe('pt_BR');
    expect(appLocaleToCanonicalBlizzardLocale('zh')).toBe('zh_CN');
  });

  it('omits the API locale override when the realm already matches the app language', () => {
    expect(apiLocaleOverrideFor('en', 'en_GB')).toBeUndefined();
    expect(apiLocaleOverrideFor('es', 'es_MX')).toBeUndefined();
    expect(apiLocaleOverrideFor('pt', 'pt_PT')).toBeUndefined();
    expect(apiLocaleOverrideFor('zh', 'zh_TW')).toBeUndefined();
    expect(apiLocaleOverrideFor('fr', 'en_GB')).toBe('fr_FR');
  });

  it('switches locale prefixes while preserving the rest of the path', () => {
    expect(localizedPath('/fr/eu/hyjal/auctions', 'de')).toBe('/de/eu/hyjal/auctions');
    expect(localizedPath('/eu/hyjal/auctions', 'fr')).toBe('/fr/eu/hyjal/auctions');
    expect(localizedPath('/', 'en')).toBe('/en/');
    expect(stripLocalePrefix('/xx/eu/hyjal')).toBe('/eu/hyjal');
  });
});
