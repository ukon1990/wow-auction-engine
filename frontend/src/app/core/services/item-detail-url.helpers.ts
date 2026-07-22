import { Params, Router, UrlTree } from '@angular/router';

import type {
  ItemDetailScope,
  ItemDetailVariantParams,
} from '@core/services/market-item-detail.service';

export interface ItemDetailOpenParams {
  readonly itemId: number;
  readonly bonusKey?: string;
  readonly modifierKey?: string;
  readonly petSpeciesId?: number;
  readonly scope?: ItemDetailScope;
  readonly recipeId?: number | null;
}

export function itemDetailVariantFromOpenParams(
  params: Pick<ItemDetailOpenParams, 'bonusKey' | 'modifierKey' | 'petSpeciesId'>,
): ItemDetailVariantParams {
  return {
    bonusKey: params.bonusKey ?? '',
    modifierKey: params.modifierKey ?? '',
    petSpeciesId: params.petSpeciesId ?? 0,
  };
}

export function itemDetailQueryParams(params: ItemDetailOpenParams): Params {
  const variant = itemDetailVariantFromOpenParams(params);
  return {
    bonusKey: variant.bonusKey,
    modifierKey: variant.modifierKey,
    petSpeciesId: variant.petSpeciesId,
    ...(params.scope === 'commodity' ? { scope: 'commodity' } : null),
    ...(params.recipeId != null && Number.isFinite(params.recipeId) && params.recipeId > 0
      ? { recipeId: params.recipeId }
      : null),
  };
}

export function buildItemDetailUrlTree(
  router: Router,
  region: string,
  realmSlug: string,
  params: ItemDetailOpenParams,
): UrlTree {
  return router.createUrlTree(['/', region, realmSlug, 'item', params.itemId], {
    queryParams: itemDetailQueryParams(params),
  });
}

export function buildItemDetailUrl(
  router: Router,
  region: string,
  realmSlug: string,
  params: ItemDetailOpenParams,
): string {
  return router.serializeUrl(buildItemDetailUrlTree(router, region, realmSlug, params));
}
