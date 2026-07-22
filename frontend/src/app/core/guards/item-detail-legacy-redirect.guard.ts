import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

function realmFromRoute(route: import('@angular/router').ActivatedRouteSnapshot): {
  region: string;
  realm: string;
} | null {
  let current: import('@angular/router').ActivatedRouteSnapshot | null = route;
  while (current) {
    const region = current.paramMap.get('region');
    const realm = current.paramMap.get('realm');
    if (region && realm) {
      return { region, realm };
    }
    current = current.parent;
  }
  return null;
}

export const legacyAuctionItemRedirectGuard: CanActivateFn = (route) => {
  const router = inject(Router);
  const itemId = route.paramMap.get('itemId');
  const realm = realmFromRoute(route);
  if (!itemId || !realm) {
    return router.parseUrl('/');
  }
  return router.createUrlTree(['/', realm.region, realm.realm, 'item', itemId], {
    queryParams: route.queryParams,
  });
};

export const legacyCraftingItemRedirectGuard: CanActivateFn = (route) => {
  const router = inject(Router);
  const itemId = route.paramMap.get('itemId');
  const recipeId = route.paramMap.get('recipeId');
  const realm = realmFromRoute(route);
  if (!itemId || !realm) {
    return router.parseUrl('/');
  }
  return router.createUrlTree(['/', realm.region, realm.realm, 'item', itemId], {
    queryParams: {
      ...route.queryParams,
      ...(recipeId ? { recipeId } : {}),
    },
  });
};
