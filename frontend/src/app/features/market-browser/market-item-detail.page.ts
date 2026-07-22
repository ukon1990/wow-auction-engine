import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PageFrameComponent, SkeletonDirective } from '@ui';
import { combineLatest, distinctUntilChanged, map } from 'rxjs';

import { itemDetailQueryParams } from '@core/services/item-detail-url.helpers';
import type { ItemDetailScope } from '@core/services/market-item-detail.service';
import { MarketItemDetailPanelComponent } from './market-item-detail.panel';
import {
  formatRealmLabel,
  isRegion,
  realmAncestorRoute,
  scopeFromQuery,
  type RegionCode,
  variantEqual,
  variantFromQuery,
} from './market-item-detail.helpers';

@Component({
  selector: 'app-market-item-detail-page',
  host: {
    class: 'flex min-h-0 min-w-0 flex-1 flex-col',
  },
  imports: [RouterLink, PageFrameComponent, MarketItemDetailPanelComponent, SkeletonDirective],
  template: `
    <ee-page-frame
      [title]="pageTitle()"
      [eyebrow]="pageEyebrow()"
      [loading]="loading()"
      titleId="item-codex-title"
    >
      <nav
        class="ee-label mb-4 flex flex-wrap items-center gap-x-1 gap-y-1 text-outline select-text"
        aria-label="Breadcrumb"
        i18n-aria-label="@@itemDetail.breadcrumb"
        [eeSkeleton]="loading()"
      >
        <a
          [routerLink]="['/', regionRealm().region, regionRealm().realm]"
          class="rounded-sm hover:text-primary focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
          >{{ regionRealm().regionLabel }}</a
        >
        <span aria-hidden="true">/</span>
        <a
          [routerLink]="['/', regionRealm().region, regionRealm().realm]"
          class="rounded-sm hover:text-primary focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
          >{{ regionRealm().realmLabel }}</a
        >
        <span aria-hidden="true">/</span>
        <span class="text-on-surface" aria-current="page">{{ itemTitle() }}</span>
      </nav>

      @if (routeCtx(); as ctx) {
        <app-market-item-detail-panel
          [region]="ctx.region"
          [realmSlug]="ctx.realmSlug"
          [itemId]="ctx.itemId"
          [variant]="ctx.variant"
          [initialScope]="ctx.initialScope"
          [recipeId]="ctx.recipeId"
          linkMode="page"
          (titleChange)="onTitleChange($event)"
          (scopeChange)="onScopeChange($event)"
        />
      }
    </ee-page-frame>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketItemDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading = signal(true);
  protected readonly itemTitle = signal('Item');
  protected readonly pageTitle = computed(() => this.itemTitle());

  protected readonly routeCtx = signal<{
    region: RegionCode;
    realmSlug: string;
    itemId: number;
    variant: ReturnType<typeof variantFromQuery>;
    initialScope: ItemDetailScope;
    recipeId: number | null;
  } | null>(null);

  protected readonly regionRealm = computed(() => {
    const ctx = this.routeCtx();
    if (!ctx) {
      return { region: '', realm: '', realmLabel: '', regionLabel: '' };
    }
    return {
      region: ctx.region,
      realm: ctx.realmSlug,
      realmLabel: formatRealmLabel(ctx.realmSlug),
      regionLabel: ctx.region.toUpperCase(),
    };
  });

  constructor() {
    const realmRoute = realmAncestorRoute(this.route);

    combineLatest([realmRoute.paramMap, this.route.paramMap, this.route.queryParamMap])
      .pipe(
        map(([realmPm, itemPm, q]) => {
          const recipeRaw = q.get('recipeId');
          const recipeId = recipeRaw != null ? Number(recipeRaw) : null;
          return {
            region: realmPm.get('region'),
            realmSlug: realmPm.get('realm'),
            itemId: Number(itemPm.get('itemId')),
            variant: variantFromQuery(q),
            initialScope: scopeFromQuery(q),
            recipeId: recipeId != null && Number.isFinite(recipeId) ? recipeId : null,
          };
        }),
        distinctUntilChanged(
          (a, b) =>
            a.region === b.region &&
            a.realmSlug === b.realmSlug &&
            a.itemId === b.itemId &&
            a.initialScope === b.initialScope &&
            a.recipeId === b.recipeId &&
            variantEqual(a.variant, b.variant),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((ctx) => {
        this.loading.set(false);
        if (!isRegion(ctx.region) || !ctx.realmSlug || !Number.isFinite(ctx.itemId)) {
          this.routeCtx.set(null);
          return;
        }
        this.routeCtx.set({
          region: ctx.region,
          realmSlug: ctx.realmSlug,
          itemId: ctx.itemId,
          variant: ctx.variant,
          initialScope: ctx.initialScope,
          recipeId: ctx.recipeId,
        });
      });
  }

  protected pageEyebrow(): string {
    return $localize`:@@itemDetail.eyebrow:Item Codex`;
  }

  protected onTitleChange(title: string): void {
    this.itemTitle.set(title);
  }

  protected onScopeChange(scope: ItemDetailScope): void {
    const ctx = this.routeCtx();
    if (!ctx) return;
    void this.router.navigate(['/', ctx.region, ctx.realmSlug, 'item', ctx.itemId], {
      queryParams: itemDetailQueryParams({
        itemId: ctx.itemId,
        bonusKey: ctx.variant.bonusKey,
        modifierKey: ctx.variant.modifierKey,
        petSpeciesId: ctx.variant.petSpeciesId,
        scope,
        recipeId: ctx.recipeId,
      }),
      replaceUrl: true,
    });
  }
}
