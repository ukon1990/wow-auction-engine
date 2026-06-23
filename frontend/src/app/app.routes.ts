import { Route } from '@angular/router';

import { realmSelectedGuard } from '@core/guards/realm-selected.guard';
import {
  readCraftingBrowserQueryState,
  toCraftingBrowserQueryParams,
} from '@core/mappers/crafting-browser-query.mapper';
import {
  readMarketBrowserQueryState,
  toMarketBrowserQueryParams,
} from '@core/mappers/market-browser-query.mapper';
import {
  QUERY_PARAM_MAPPER,
  QueryService,
  TO_QUERY_PARAMS_MAPPER,
} from '@core/services/query.service';
import { AuctionItemService } from '@core/services/auction-item.service';
import { CraftingItemService } from '@core/services/crafting-item.service';
import { UserRole } from '@api/auth/auth.model';
import { userHasRoleGuard } from '@core/guards/user-has-role-guard';

export type TitledRoutes = (Route & {
  icon?: string;
  children?: TitledRoutes;
})[];

export const routes: TitledRoutes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/select-realm/select-realm.page').then((module) => module.SelectRealmPage),
  },
  {
    path: 'login',
    title: $localize`:@@route.login:Login`,
    loadComponent: () => import('./features/login/login.page').then((module) => module.LoginPage),
  },
  {
    path: 'profile',
    title: $localize`:@@route.profile:Profile`,
    loadComponent: () =>
      import('./features/profile/profile.page').then((module) => module.ProfilePage),
  },
  {
    path: 'admin',
    title: $localize`:@@route.admin.title:Admin`,
    icon: 'admin_panel_settings',
    canActivate: [userHasRoleGuard(UserRole.Admin)],
    runGuardsAndResolvers: 'always',
    children: [
      {
        path: '',
        redirectTo: 'status',
        pathMatch: 'full',
      },
      {
        path: 'status',
        title: 'Status',
        icon: 'query_stats',
        loadComponent: () =>
          import('@features/admin/status/admin-status.page').then(
            (module) => module.AdminStatusPage,
          ),
      },
      {
        path: 'users',
        title: $localize`:@@route.admin.users:Users`,
        icon: 'manage_accounts',
        loadComponent: () =>
          import('@features/admin/user-administration/user-administration.page').then(
            (module) => module.UserAdministrationPage,
          ),
      },
    ],
  },
  {
    path: ':region/:realm',
    canActivate: [realmSelectedGuard],
    children: [
      {
        path: '',
        redirectTo: 'auctions',
        pathMatch: 'full',
      },
      {
        path: 'auctions',
        title: $localize`:@@route.auctions:Auctions`,
        icon: 'travel_explore',
        loadComponent: () =>
          import('./features/auctions/auctions-shell.page').then((m) => m.AuctionsShellPage),
        providers: [
          QueryService,
          {
            provide: QUERY_PARAM_MAPPER,
            useValue: readMarketBrowserQueryState,
          },
          {
            provide: TO_QUERY_PARAMS_MAPPER,
            useValue: toMarketBrowserQueryParams,
          },
          AuctionItemService,
        ],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/market-browser/market-browser.page').then(
                (module) => module.MarketBrowserPage,
              ),
          },
          {
            path: 'item/:itemId',
            data: {
              marketListSegment: 'auctions',
              marketListLabel: $localize`:@@route.auctions:Auctions`,
            },
            loadComponent: () =>
              import('./features/market-browser/market-item-detail.page').then(
                (m) => m.MarketItemDetailPage,
              ),
          },
        ],
      },
      {
        path: 'crafting',
        title: $localize`:@@route.crafting:Crafting`,
        icon: 'handyman',
        loadComponent: () =>
          import('./features/crafting/crafting-shell.page').then((m) => m.CraftingShellPage),
        providers: [
          QueryService,
          {
            provide: QUERY_PARAM_MAPPER,
            useValue: readCraftingBrowserQueryState,
          },
          {
            provide: TO_QUERY_PARAMS_MAPPER,
            useValue: toCraftingBrowserQueryParams,
          },
          CraftingItemService,
        ],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/crafting/crafting-browser.page').then(
                (m) => m.CraftingBrowserPage,
              ),
          },
          {
            path: ':recipeId/:itemId',
            data: {
              marketListSegment: 'crafting',
              marketListLabel: $localize`:@@route.crafting:Crafting`,
            },
            loadComponent: () =>
              import('./features/market-browser/market-item-detail.page').then(
                (m) => m.MarketItemDetailPage,
              ),
          },
        ],
      },
    ],
  },
];
