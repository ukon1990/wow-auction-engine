import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';

import { WowheadItemTooltipDirective } from '@core/directives/wowhead-item-tooltip';
import { ItemDetailModalService } from '@core/services/item-detail-modal.service';
import {
  buildItemDetailUrl,
  type ItemDetailOpenParams,
} from '@core/services/item-detail-url.helpers';
import type { ItemDetailScope } from '@core/services/market-item-detail.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import {
  copperToCurrencyAmount,
  type CurrencyAmount,
  qualityToneClasses,
  SymbolIconComponent,
  type ItemQuality,
} from '@ui';

export type ItemLinkMode = 'modal' | 'page';

@Component({
  selector: 'app-item-link',
  imports: [SymbolIconComponent, WowheadItemTooltipDirective],
  template: `
    <a [href]="canonicalHref()" [class]="linkClass()" (click)="onActivate($event)">
      @if (stacked()) {
        <div class="flex min-w-0 items-center gap-3">
          @if (showIcon()) {
            <div [class]="iconClass()">
              @if (iconUrl()) {
                <img class="h-6 w-6 rounded-sm object-cover" [src]="iconUrl()" [alt]="iconAlt()" />
              } @else {
                <ee-symbol-icon class="text-[18px]" name="deployed_code" />
              }
            </div>
          }
          <span
            appWowheadItemTooltip
            [itemId]="itemId()"
            linkType="item"
            [bonusKey]="bonusKey()"
            [currentBuyout]="currentBuyout()"
            [class]="nameClass()"
            >{{ name() }}</span
          >
        </div>
        <ng-content />
      } @else {
        @if (showIcon()) {
          <div [class]="iconClass()">
            @if (iconUrl()) {
              <img class="h-6 w-6 rounded-sm object-cover" [src]="iconUrl()" [alt]="iconAlt()" />
            } @else {
              <ee-symbol-icon class="text-[18px]" name="deployed_code" />
            }
          </div>
        }
        <span class="flex min-w-0 flex-col">
          <span
            appWowheadItemTooltip
            [itemId]="itemId()"
            linkType="item"
            [bonusKey]="bonusKey()"
            [currentBuyout]="currentBuyout()"
            [class]="nameClass()"
            >{{ name() }}</span
          >
          <ng-content />
        </span>
      }
    </a>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemLinkComponent {
  readonly itemId = input.required<number>();
  readonly name = input.required<string>();
  readonly iconUrl = input<string | null | undefined>(undefined);
  readonly quality = input<ItemQuality | undefined>(undefined);
  readonly bonusKey = input<string>('');
  readonly modifierKey = input<string>('');
  readonly petSpeciesId = input<number>(0);
  readonly scope = input<ItemDetailScope | undefined>(undefined);
  readonly recipeId = input<number | null | undefined>(undefined);
  readonly buyoutCopper = input<number | null | undefined>(undefined);
  readonly buyout = input<CurrencyAmount | null | undefined>(undefined);
  readonly linkMode = input<ItemLinkMode>('modal');
  readonly showIcon = input(false);
  readonly stacked = input(false);
  readonly layoutClass = input(
    'flex min-w-0 items-center gap-3 rounded no-underline text-inherit outline-none transition hover:bg-white/5 focus-visible:ring-2 focus-visible:ring-primary/60',
  );

  private readonly router = inject(Router);
  private readonly modal = inject(ItemDetailModalService);
  private readonly realmSelection = inject(RealmSelectionService);

  protected readonly linkClass = computed(() => this.layoutClass());

  protected readonly nameClass = computed(() => {
    const quality = this.quality();
    const tone = quality != null ? qualityToneClasses(quality).split(' ')[0] : 'text-on-surface';
    return `truncate text-sm font-semibold ${tone}`;
  });

  protected readonly iconClass = computed(() => {
    const quality = this.quality();
    const tone = quality != null ? qualityToneClasses(quality) : 'border-white/10 text-on-surface';
    return `flex h-8 w-8 shrink-0 items-center justify-center rounded border bg-surface ${tone}`;
  });

  protected iconAlt(): string {
    return `${this.name()} icon`;
  }

  protected currentBuyout(): CurrencyAmount | null {
    const amount = this.buyout();
    if (amount) return amount;
    const copper = this.buyoutCopper();
    return copper != null ? copperToCurrencyAmount(copper) : null;
  }

  protected canonicalHref(): string {
    const realm = this.realmContext();
    if (!realm) return '#';
    return buildItemDetailUrl(this.router, realm.region, realm.slug, this.openParams());
  }

  protected onActivate(event: MouseEvent): void {
    if (this.isModifiedClick(event)) return;
    event.preventDefault();
    if (this.linkMode() === 'page') {
      void this.router.navigateByUrl(this.canonicalHref());
      return;
    }
    this.modal.open(this.openParams());
  }

  private openParams(): ItemDetailOpenParams {
    return {
      itemId: this.itemId(),
      bonusKey: this.bonusKey(),
      modifierKey: this.modifierKey(),
      petSpeciesId: this.petSpeciesId(),
      scope: this.scope(),
      recipeId: this.recipeId(),
    };
  }

  private realmContext(): { region: string; slug: string } | null {
    const fromUrl = this.parseRealmFromUrl();
    if (fromUrl) return fromUrl;
    const selected = this.realmSelection.selected();
    if (!selected) return null;
    return { region: selected.region, slug: selected.slug };
  }

  private parseRealmFromUrl(): { region: string; slug: string } | null {
    const segments = this.router.url.split('?')[0].split('/').filter(Boolean);
    const region = segments[0]?.toLowerCase();
    const slug = segments[1];
    if (!region || !slug) return null;
    if (!['us', 'eu', 'kr', 'tw'].includes(region)) return null;
    return { region, slug };
  }

  private isModifiedClick(event: MouseEvent): boolean {
    return event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey;
  }
}
