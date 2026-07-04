import { computed, DestroyRef, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { ActivatedRoute, NavigationEnd, ParamMap, Params, Router } from '@angular/router';
import { combineLatest, filter, map, startWith } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RealmSelectionService } from '@core/services/realm-selection.service';

export const QUERY_PARAM_MAPPER = new InjectionToken<(paramMap: ParamMap) => unknown>(
  'QUERY_PARAM_MAPPER',
);

export const TO_QUERY_PARAMS_MAPPER = new InjectionToken<
  (obj: object) => Params | null | undefined
>('TO_QUERY_PARAMS_MAPPER', {
  providedIn: 'root',
  factory: () => defaultQueryParamsMapper,
});

export type Region = 'eu' | 'us' | 'kr' | 'tw';
@Injectable(
  /*{
  providedIn: 'root',
}*/
)
export class QueryService<QueryParam> {
  readonly region = signal<Region>('eu');
  readonly realmSlug = signal<string | undefined>(undefined);
  readonly queryParams = signal<QueryParam | null>(null);
  private readonly toQueryParamsMapper = inject(TO_QUERY_PARAMS_MAPPER);
  private readonly paramMapper = inject(QUERY_PARAM_MAPPER) as (paramMap: ParamMap) => QueryParam;
  private readonly realmSelectionService = inject(RealmSelectionService);
  readonly locale = computed(() => this.realmSelectionService.selected()?.locale);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    combineLatest([
      this.router.events.pipe(
        takeUntilDestroyed(this.destroyRef),
        startWith(null),
        filter((event) => event === null || event instanceof NavigationEnd),
        map(() => this.findRegionAndRealmSlug(this.router.routerState.root)),
        filter((data) => data?.region !== undefined && data?.realmSlug !== undefined),
      ),
    ]).subscribe(([data]) =>
      // already filtered away if not set
      this.updateStateFromQuery(data!.region, data!.realmSlug, data!.queryParamMap),
    );
  }

  navigateWithState(state: QueryParam): void {
    if (!this.router || !this.region() || !this.realmSlug()) return;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.toQueryParamsMapper(state!),
      replaceUrl: true,
    });
  }

  findRegionAndRealmSlug(
    route: ActivatedRoute,
  ): { region: Region; realmSlug: string; queryParamMap: ParamMap } | undefined {
    let current: ActivatedRoute | null = route;
    let region: Region | undefined;
    let realmSlug: string | undefined;
    let queryRoute: ActivatedRoute = route;

    while (current) {
      const snapshot = current.snapshot.paramMap;
      const routeRegion = snapshot.get('region') as Region;
      const routeRealm = snapshot.get('realm');
      if (routeRegion && routeRealm) {
        region = routeRegion;
        realmSlug = routeRealm;
      }
      queryRoute = current;
      current = current.firstChild;
    }

    if (!region || !realmSlug) return undefined;

    return { region, realmSlug, queryParamMap: queryRoute.snapshot.queryParamMap };
  }

  private updateStateFromQuery(region: Region, realmSlug: string, queryParamMap: ParamMap) {
    this.region.set(region);
    this.realmSlug.set(realmSlug);
    this.queryParams.set(this.paramMapper(queryParamMap));
  }
}

export const defaultQueryParamsMapper = (obj: object): Params | null | undefined => ({
  ...obj,
});
