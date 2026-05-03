import {
  EnvironmentInjector,
  inject,
  Injectable,
  runInInjectionContext,
  signal,
} from '@angular/core';
import {
  ActivatedRoute,
  ActivatedRouteSnapshot,
  CanActivateFn,
  GuardResult,
  MaybeAsync,
  Route,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { NavItem } from '@ui';
import { firstValueFrom, isObservable } from 'rxjs';
import { routes, TitledRoutes } from '../../app.routes';

@Injectable({
  providedIn: 'root',
})
export class MenuService {
  readonly links = signal<NavItem[]>([
    /* Just keeping this here as a reminder for the icon match
    { id: 'dashboard', label: 'Dashboard', icon: 'dashboard' },
    { id: 'auctions', label: 'Auctions', icon: 'travel_explore' },
    { id: 'crafting', label: 'Crafting', icon: 'schema' },
    { id: 'archive', label: 'Archive', icon: 'inventory_2' },
  */
  ]);

  private readonly injector = inject(EnvironmentInjector);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);

  constructor() {
    // Once we get login etc, we should do some auth checks etc here
    this.getActiveRouteLinks(routes, this.activatedRoute.snapshot, this.router.routerState.snapshot)
      .then((links) => this.links.set(links))
      .catch((err: unknown) => console.error('Error getting active route links', err));
  }

  async getActiveRouteLinks(
    routes: TitledRoutes,
    routeSnapshot: ActivatedRouteSnapshot,
    state: RouterStateSnapshot,
    parentPath = '',
  ): Promise<NavItem[]> {
    const links: NavItem[] = [];

    for (const route of routes) {
      if (!(await this.canActivate(route, routeSnapshot, state))) {
        continue;
      }

      const path = `${parentPath ? `${parentPath}/` : ''}${route.path}`;
      links.push({
        id: path,
        label: this.getRouteTitle(route.title),
        icon: route.icon,
        routerLink: path,
        children: await this.getActiveRouteLinks(route.children || [], routeSnapshot, state),
      });
    }

    return links;
  }

  private getRouteTitle(title: Route['title']): string {
    if (typeof title === 'string') {
      return title;
    }

    return '';
  }

  private async canActivate(
    route: Route,
    routeSnapshot: ActivatedRouteSnapshot,
    state: RouterStateSnapshot,
  ): Promise<boolean> {
    if (!route.canActivate?.length) return true;
    for (const guard of route.canActivate) {
      if (typeof guard !== 'function') {
        // Not handling the deprecated guards, not gonna use them
        continue;
      }

      const canActivate = guard as CanActivateFn;
      const result = await this.resolveGuardResult(
        runInInjectionContext(this.injector, () => canActivate(routeSnapshot, state)),
      );
      return result === true;
    }
    return true;
  }

  private resolveGuardResult(result: MaybeAsync<GuardResult>): Promise<GuardResult> | GuardResult {
    if (isObservable(result)) {
      return firstValueFrom(result);
    }
    return result;
  }
}
