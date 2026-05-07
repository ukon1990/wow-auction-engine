import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { inject, Injectable, LOCALE_ID, PLATFORM_ID, signal } from '@angular/core';

import { RealmSelectionService } from './realm-selection.service';
import {
  apiLocaleOverrideFor,
  appLocaleFromPath,
  AppLocale,
  localizedPath,
  LOCALE_OPTIONS,
  LOCALE_OVERRIDE_COOKIE_KEY,
  LOCALE_OVERRIDE_STORAGE_KEY,
  LocaleOption,
  normalizeAppLocale,
  SUPPORTED_APP_LOCALES,
} from './locale-support';

@Injectable({ providedIn: 'root' })
export class LocaleService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly document = inject(DOCUMENT);
  private readonly angularLocaleId = inject(LOCALE_ID);
  private readonly realmSelection = inject(RealmSelectionService);

  private readonly activeLocaleSignal = signal<AppLocale>(this.detectActiveLocale());
  private readonly pathLocaleSignal = signal<AppLocale | null>(null);

  readonly supportedLocales = SUPPORTED_APP_LOCALES;
  readonly localeOptions: readonly LocaleOption[] = LOCALE_OPTIONS;
  readonly activeLocale = (): AppLocale => this.pathLocaleSignal() ?? this.activeLocaleSignal();

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      queueMicrotask(() => this.syncPathLocale());
      setTimeout(() => this.syncPathLocale());
    }
  }

  apiLocaleOverride(): string | undefined {
    return apiLocaleOverrideFor(this.activeLocale(), this.realmSelection.selected()?.locale);
  }

  dataLocaleCacheKey(): string {
    return (
      this.apiLocaleOverride() ?? this.realmSelection.selected()?.locale ?? this.activeLocale()
    );
  }

  formatLocale(): string {
    return this.activeLocale();
  }

  switchLocale(locale: AppLocale): void {
    this.persistOverride(locale);
    if (!isPlatformBrowser(this.platformId)) {
      this.activeLocaleSignal.set(locale);
      return;
    }
    const location = this.document.location;
    const nextPath = localizedPath(location.pathname, locale);
    const nextUrl = `${nextPath}${location.search}${location.hash}`;
    this.pathLocaleSignal.set(locale);
    this.activeLocaleSignal.set(locale);
    location.assign(nextUrl);
  }

  private detectActiveLocale(): AppLocale {
    if (isPlatformBrowser(this.platformId)) {
      const pathLocale = this.localeFromBrowserPath();
      if (pathLocale) return pathLocale;
      const stored = this.readStoredOverride();
      if (stored) return stored;
    }
    return normalizeAppLocale(this.angularLocaleId) ?? 'en';
  }

  private localeFromBrowserPath(): AppLocale | null {
    return isPlatformBrowser(this.platformId)
      ? appLocaleFromPath(this.document.location.pathname)
      : null;
  }

  private syncPathLocale(): void {
    this.pathLocaleSignal.set(this.localeFromBrowserPath());
  }

  private readStoredOverride(): AppLocale | null {
    try {
      return normalizeAppLocale(window.localStorage.getItem(LOCALE_OVERRIDE_STORAGE_KEY));
    } catch {
      return null;
    }
  }

  private persistOverride(locale: AppLocale): void {
    if (!isPlatformBrowser(this.platformId)) return;
    try {
      window.localStorage.setItem(LOCALE_OVERRIDE_STORAGE_KEY, locale);
    } catch {
      // Ignore storage failures; the URL still selects the locale.
    }
    try {
      this.document.cookie = `${LOCALE_OVERRIDE_COOKIE_KEY}=${locale}; Path=/; Max-Age=31536000; SameSite=Lax`;
    } catch {
      // Cookie persistence is best-effort for server redirects.
    }
  }
}
