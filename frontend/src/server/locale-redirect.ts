import type express from 'express';

import {
  appLocaleFromPath,
  blizzardLocaleToAppLocale,
  isAppLocale,
  localizedPath,
  LOCALE_OVERRIDE_COOKIE_KEY,
  normalizeAppLocale,
  stripLocalePrefix,
  type AppLocale,
} from '../app/core/services/locale-support';

const REALM_PATH = /^\/(?<region>us|eu|kr|tw)\/(?<slug>[^/?#]+)/i;
const API_OR_AUTH_PATH = /^\/(?:api|auth)(?:\/|$)/;
const STATIC_FILE_PATH = /\/[^/?#]+\.[a-z0-9]{2,8}(?:[?#].*)?$/i;
const LOCALE_LIKE_PATH = /^\/[a-z]{2}(?:-[a-z]{2})?(?:\/|$)/i;
const REALM_REGION_PREFIX = /^\/(?:us|eu|kr|tw)(?:\/|$)/i;

export interface LocaleRedirectDecision {
  readonly status: 302;
  readonly location: string;
}

export interface RealmLocaleLookupResult {
  readonly locale?: string;
}

export async function resolveLocaleRedirect(
  req: express.Request,
  backendOrigin: string,
): Promise<LocaleRedirectDecision | null> {
  const path = externalPathname(req.originalUrl || req.url || req.path || '/');
  if (shouldSkipLocaleRedirect(path)) return null;

  const existing = appLocaleFromPath(path);
  if (existing) return null;

  if (isUnsupportedLocalePrefix(path)) {
    return redirectTo(localizedPath(stripLocalePrefix(path), 'en'), req.originalUrl, path);
  }

  const realmMatch = path.match(REALM_PATH);
  if (realmMatch?.groups) {
    const { region, slug } = realmMatch.groups;
    const realmLocale = await fetchRealmLocale(backendOrigin, region, slug);
    if (realmLocale) {
      return redirectTo(
        localizedPath(path, blizzardLocaleToAppLocale(realmLocale)),
        req.originalUrl,
        path,
      );
    }
  }

  const preferred = readLocaleOverrideCookie(req) ?? appLocaleFromAcceptLanguage(req) ?? 'en';
  return redirectTo(localizedPath(path, preferred), req.originalUrl, path);
}

function externalPathname(url: string): string {
  return new URL(url, 'http://localhost').pathname || '/';
}

function isUnsupportedLocalePrefix(path: string): boolean {
  return LOCALE_LIKE_PATH.test(path) && !REALM_REGION_PREFIX.test(path);
}

export function shouldSkipLocaleRedirect(path: string): boolean {
  return API_OR_AUTH_PATH.test(path) || STATIC_FILE_PATH.test(path);
}

export function appLocaleFromAcceptLanguage(
  req: Pick<express.Request, 'headers'>,
): AppLocale | null {
  const raw = req.headers['accept-language'];
  const header = Array.isArray(raw) ? raw.join(',') : raw;
  if (!header) return null;
  return (
    header
      .split(',')
      .map((part) => {
        const [tag, ...params] = part.trim().split(';');
        const qParam = params.find((param) => param.trim().startsWith('q='));
        const q = qParam ? Number(qParam.split('=')[1]) : 1;
        return { locale: normalizeAppLocale(tag), q: Number.isFinite(q) ? q : 0 };
      })
      .filter((entry): entry is { locale: AppLocale; q: number } => Boolean(entry.locale))
      .sort((a, b) => b.q - a.q)[0]?.locale ?? null
  );
}

export function readLocaleOverrideCookie(req: Pick<express.Request, 'headers'>): AppLocale | null {
  const raw = req.headers.cookie;
  if (!raw) return null;
  for (const part of raw.split(';')) {
    const [key, value] = part.trim().split('=');
    if (key === LOCALE_OVERRIDE_COOKIE_KEY && isAppLocale(value)) {
      return value;
    }
  }
  return null;
}

async function fetchRealmLocale(
  backendOrigin: string,
  region: string,
  slug: string,
): Promise<string | null> {
  try {
    const response = await fetch(
      new URL(`/api/realms/${region.toLowerCase()}/${encodeURIComponent(slug)}`, backendOrigin),
      { headers: { Accept: 'application/json' } },
    );
    if (!response.ok) return null;
    const body = (await response.json()) as { realm?: RealmLocaleLookupResult };
    return body.realm?.locale ?? null;
  } catch {
    return null;
  }
}

function redirectTo(
  localizedPathname: string,
  originalUrl: string,
  originalPath: string,
): LocaleRedirectDecision {
  return {
    status: 302,
    location: `${localizedPathname}${originalUrl.slice(originalPath.length)}`,
  };
}
