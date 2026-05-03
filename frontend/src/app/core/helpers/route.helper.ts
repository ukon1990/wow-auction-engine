import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { TitledRoutes } from '../../app.routes';
import { NavItem } from '@ui';

export function getActiveRouteLinks(
  routes: TitledRoutes,
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
): NavItem[] {
  return routes
    .filter((route) => {
      if (!route.canActivate?.length) return true;
      return route.canActivate.every((guard) => guard(route, state));
    })
    .map((route) => {
      return {
        id: route.path,
        label: route.title,
        icon: route.icon,
        routerLink: route.path,
        children: getActiveRouteLinks(route.children || [], route, state),
      };
    });
}
