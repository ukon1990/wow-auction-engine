import {
  effect,
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

import { Realm } from '@api/generated';
import { routes, TitledRoutes } from '../../app.routes';
import { RealmSelectionService } from './realm-selection.service';

@Injectable({
  providedIn: 'root',
})
export class MenuService {
  readonly links = signal<NavItem[]>([]);

  private readonly injector = inject(EnvironmentInjector);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly realmSelection = inject(RealmSelectionService);

  constructor() {
    effect(() => {
      const selected = this.realmSelection.selected();
      this.refreshLinks(selected).catch((err: unknown) =>
        console.error('Error getting active route links', err),
      );
    });
  }

  async getActiveRouteLinks(
    routes: TitledRoutes,
    routeSnapshot: ActivatedRouteSnapshot,
    state: RouterStateSnapshot,
    parentPath = '',
    selected: Realm | null = null,
  ): Promise<NavItem[]> {
    const links: NavItem[] = [];

    for (const route of routes) {
      const isNavItem = Boolean(route.title && route.icon);
      // Layout/shell routes (e.g. `:region/:realm`) must not run their guards here: the
      // injected `ActivatedRouteSnapshot` is not the route being traversed, so guards like
      // `realmSelectedGuard` would see missing params and fail. Actual navigation still runs
      // guards normally. Only leaf routes that become top-nav entries are gated.
      if (isNavItem && !(await this.canActivate(route, routeSnapshot, state))) {
        continue;
      }
      const segment = this.resolveSegment(route.path ?? '', selected);
      if (segment === null) {
        continue;
      }
      const path = parentPath ? `${parentPath}/${segment}` : segment;

      if (!route.title || !route.icon) {
        const childLinks = await this.getActiveRouteLinks(
          route.children || [],
          routeSnapshot,
          state,
          path,
          selected,
        );
        links.push(...childLinks);
        continue;
      }

      const childLinks = await this.getActiveRouteLinks(
        route.children || [],
        routeSnapshot,
        state,
        path,
        selected,
      );
      const link: NavItem = {
        id: path,
        label: this.getRouteTitle(route.title),
        icon: route.icon,
        ...(route.loadComponent ? { routerLink: `/${path}` } : {}),
        children: childLinks,
      };

      links.push(link);
    }

    return links;
  }

  private async refreshLinks(selected: Realm | null): Promise<void> {
    const links = await this.getActiveRouteLinks(
      routes,
      this.activatedRoute.snapshot,
      this.router.routerState.snapshot,
      '',
      selected,
    );
    this.links.set(links);
  }

  private resolveSegment(path: string, selected: Realm | null): string | null {
    if (!path.includes(':')) return path;
    const segments = path.split('/');
    const resolved: string[] = [];
    for (const segment of segments) {
      if (!segment.startsWith(':')) {
        resolved.push(segment);
        continue;
      }
      const param = segment.slice(1);
      if (param === 'region') {
        if (!selected) return null;
        resolved.push(selected.region);
      } else if (param === 'realm') {
        if (!selected) return null;
        resolved.push(selected.slug);
      } else {
        return null;
      }
    }
    return resolved.join('/');
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
