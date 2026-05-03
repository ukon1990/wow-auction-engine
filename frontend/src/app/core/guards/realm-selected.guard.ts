import { isPlatformBrowser } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { Realm } from '../../api/generated';
import { RealmSelectionService } from '../services/realm-selection.service';

function isApiRegionCode(region: string): boolean {
  return (Object.values(Realm.RegionEnum) as readonly string[]).includes(region.toLowerCase());
}

export const realmSelectedGuard: CanActivateFn = async (route): Promise<boolean | UrlTree> => {
  const selection = inject(RealmSelectionService);
  const router = inject(Router);
  const platformId = inject(PLATFORM_ID);

  const region = route.paramMap.get('region');
  const slug = route.paramMap.get('realm');

  if (region && slug) {
    if (!isPlatformBrowser(platformId)) {
      if (!isApiRegionCode(region)) {
        return router.createUrlTree(['/']);
      }
      selection.selectPlaceholderFromUrl(region, slug);
      return true;
    }
    const ok = await selection.hydrateSelectedFromApi(region, slug);
    if (ok) return true;
    return router.createUrlTree(['/']);
  }

  const stored = selection.selected();
  if (stored) {
    return router.createUrlTree(['/', stored.region, stored.slug]);
  }

  return router.createUrlTree(['/']);
};
