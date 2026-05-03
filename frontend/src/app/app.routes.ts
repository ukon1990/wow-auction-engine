import { Route } from '@angular/router';

export type TitledRoutes = (Route & {
  icon: string;
})[];

export const routes: TitledRoutes = [
  {
    path: '',
    title: 'Auctions',
    icon: 'travel_explore',
    loadComponent: () =>
      import('./features/market-browser/market-browser.page').then(
        (module) => module.MarketBrowserPage,
      ),
  },
];
