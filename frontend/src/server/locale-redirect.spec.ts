import {
  resolveLocaleRedirect,
  appLocaleFromAcceptLanguage,
  shouldSkipLocaleRedirect,
} from './locale-redirect';

describe('locale redirects', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('redirects realm URLs to the realm language', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ realm: { locale: 'fr_FR' } }),
    } as Response);

    const redirect = await resolveLocaleRedirect(
      {
        path: '/eu/hyjal/auctions',
        originalUrl: '/eu/hyjal/auctions?query=ore',
        headers: {},
      } as never,
      'http://backend:8080',
    );

    expect(redirect).toEqual({
      status: 302,
      location: '/fr/eu/hyjal/auctions?query=ore',
    });
  });

  it('uses Accept-Language for non-realm URLs without a locale prefix', async () => {
    const redirect = await resolveLocaleRedirect(
      {
        path: '/login',
        originalUrl: '/login',
        headers: { 'accept-language': 'fr-CA,fr;q=0.8,en;q=0.2' },
      } as never,
      'http://backend:8080',
    );

    expect(redirect?.location).toBe('/fr/login');
  });

  it('uses the locale override cookie before Accept-Language', async () => {
    const redirect = await resolveLocaleRedirect(
      {
        path: '/',
        originalUrl: '/',
        headers: { cookie: 'wae_locale=de', 'accept-language': 'fr' },
      } as never,
      'http://backend:8080',
    );

    expect(redirect?.location).toBe('/de/');
  });

  it('redirects unsupported locale-like prefixes to English', async () => {
    const redirect = await resolveLocaleRedirect(
      {
        path: '/da/eu/hyjal',
        originalUrl: '/da/eu/hyjal',
        headers: {},
      } as never,
      'http://backend:8080',
    );

    expect(redirect?.location).toBe('/en/eu/hyjal');
  });

  it('skips API, auth, static assets, and supported locale paths', async () => {
    expect(shouldSkipLocaleRedirect('/api/health')).toBe(true);
    expect(shouldSkipLocaleRedirect('/auth/login')).toBe(true);
    expect(shouldSkipLocaleRedirect('/favicon.ico')).toBe(true);
    await expect(
      resolveLocaleRedirect(
        { path: '/fr/eu/hyjal', originalUrl: '/fr/eu/hyjal', headers: {} } as never,
        'http://backend:8080',
      ),
    ).resolves.toBeNull();
  });

  it('parses weighted Accept-Language values', () => {
    expect(
      appLocaleFromAcceptLanguage({ headers: { 'accept-language': 'en;q=0.2,es;q=0.9' } }),
    ).toBe('es');
  });
});
