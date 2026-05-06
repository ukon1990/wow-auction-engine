import { Route } from '@angular/router';

import { realmSelectedGuard } from '@core/guards/realm-selected.guard';

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
    title: 'Login',
    loadComponent: () => import('./features/login/login.page').then((module) => module.LoginPage),
  },
  {
    path: 'profile',
    title: 'Profile',
    loadComponent: () =>
      import('./features/profile/profile.page').then((module) => module.ProfilePage),
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
        title: 'Auctions',
        icon: 'travel_explore',
        loadComponent: () =>
          import('./features/auctions/auctions-shell.page').then((m) => m.AuctionsShellPage),
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
              marketListLabel: 'Auctions',
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
        title: 'Crafting',
        icon: 'handyman',
        loadComponent: () =>
          import('./features/crafting/crafting-shell.page').then((m) => m.CraftingShellPage),
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
              marketListLabel: 'Crafting',
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
