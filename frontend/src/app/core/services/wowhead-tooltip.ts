import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { NavigationStart, Router } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';

import { RealmSelectionService } from '@core/services/realm-selection.service';
import { LocaleService } from '@core/services/locale.service';
import {
  getWowheadTooltipUrl,
  wowheadLocaleFromBlizzardLocale,
  type WowheadTooltipType,
} from '@core/utils/wowhead-tooltip-url';
import { formatCurrencyPart, hasCurrencyValue, type CurrencyAmount } from '@ui';

export interface WowheadTooltipOverlay {
  readonly safeHtml: SafeHtml;
  readonly leftPx: number;
  readonly topPx: number;
  readonly describedById: string;
}

interface WowheadTooltipJson {
  readonly tooltip?: string;
}

@Injectable({
  providedIn: 'root',
})
export class WowheadTooltipService {
  private static nextId = 0;

  private static readonly AUTO_DISMISS_MS = 10_000;

  private readonly http = inject(HttpClient);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly realmSelection = inject(RealmSelectionService);
  private readonly localeService = inject(LocaleService);
  private readonly router = inject(Router);

  private readonly cache = new Map<string, string>();

  /** Bumped on every {@link clear} and at the start of each {@link show} so stale async work cannot republish the overlay. */
  private latestOverlayId = 0;
  private autoDismissTimer: ReturnType<typeof setTimeout> | null = null;

  readonly loading = signal(false);
  readonly active = signal<WowheadTooltipOverlay | null>(null);

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationStart => e instanceof NavigationStart))
      .subscribe(() => this.clear());
  }

  clear(): void {
    if (this.autoDismissTimer !== null) {
      clearTimeout(this.autoDismissTimer);
      this.autoDismissTimer = null;
    }
    const tip = this.active();
    if (tip?.describedById && typeof document !== 'undefined') {
      document
        .querySelector(`[aria-describedby="${tip.describedById}"]`)
        ?.removeAttribute('aria-describedby');
    }
    this.latestOverlayId++;
    this.active.set(null);
  }

  /**
   * Loads (or reads from cache) the Wowhead tooltip HTML and shows the overlay.
   */
  async show(options: {
    readonly wowheadType: WowheadTooltipType | string;
    readonly id: number;
    readonly bonusIds?: readonly number[];
    readonly isClassic: boolean;
    readonly currentBuyout?: CurrencyAmount | null;
    readonly event: MouseEvent | FocusEvent;
    readonly describedById: string;
  }): Promise<void> {
    const myOverlayId = ++this.latestOverlayId;

    const locale = wowheadLocaleFromBlizzardLocale(
      this.localeService.apiLocaleOverride() ?? this.realmSelection.selected()?.locale,
    );
    let url = getWowheadTooltipUrl(options.isClassic, options.id, options.wowheadType, locale);
    const bonus = options.bonusIds?.filter((b) => b > 0) ?? [];
    if (bonus.length) {
      url += `&bonus=${bonus.join(':')}`;
    }

    const { leftPx, topPx } = pointerOffset(options.event);
    const composeTooltipHtml = (html: string): SafeHtml =>
      this.sanitizer.bypassSecurityTrustHtml(appendCurrentBuyout(html, options.currentBuyout));

    const publish = (rawHtml: string): void => {
      if (myOverlayId !== this.latestOverlayId) return;
      if (this.autoDismissTimer !== null) {
        clearTimeout(this.autoDismissTimer);
        this.autoDismissTimer = null;
      }
      this.active.set({
        safeHtml: composeTooltipHtml(rawHtml),
        leftPx,
        topPx,
        describedById: options.describedById,
      });
      this.autoDismissTimer = setTimeout(() => {
        this.autoDismissTimer = null;
        this.clear();
      }, WowheadTooltipService.AUTO_DISMISS_MS);
    };

    const cached = this.cache.get(url);
    if (cached) {
      publish(cached);
      return;
    }

    this.loading.set(true);
    try {
      const body = await firstValueFrom(this.http.get<WowheadTooltipJson>(url));
      const raw = body.tooltip ?? '';
      if (myOverlayId !== this.latestOverlayId) return;
      this.cache.set(url, raw);
      publish(raw);
    } finally {
      this.loading.set(false);
    }
  }

  static nextDescribedById(): string {
    WowheadTooltipService.nextId += 1;
    return `wowhead-tip-${WowheadTooltipService.nextId}`;
  }
}

function appendCurrentBuyout(
  html: string,
  currentBuyout: CurrencyAmount | null | undefined,
): string {
  if (!hasCurrencyValue(currentBuyout)) return html;

  return `${html}${renderCurrentBuyout(currentBuyout)}`;
}

function renderCurrentBuyout(amount: CurrencyAmount): string {
  return `<table class="whtt-app-market"><tbody><tr><td><div class="whtt-current-buyout">${$localize`:@@tooltip.currentBuyout:Current Buyout:`} ${renderMoney(amount)}</div></td></tr></tbody></table>`;
}

function renderMoney(amount: CurrencyAmount): string {
  return [
    amount.gold ? `<span class="moneygold">${formatCurrencyPart(amount.gold)}</span>` : '',
    amount.silver ? `<span class="moneysilver">${formatCurrencyPart(amount.silver)}</span>` : '',
    amount.copper ? `<span class="moneycopper">${formatCurrencyPart(amount.copper)}</span>` : '',
  ].join('');
}

function pointerOffset(event: MouseEvent | FocusEvent): { leftPx: number; topPx: number } {
  if (event instanceof MouseEvent) {
    return { leftPx: event.clientX + 30, topPx: event.clientY };
  }
  const el = event.target;
  if (el instanceof HTMLElement) {
    const r = el.getBoundingClientRect();
    return { leftPx: r.left + r.width + 12, topPx: r.top };
  }
  return { leftPx: 0, topPx: 0 };
}
