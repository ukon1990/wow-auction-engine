import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/market-browser/market-browser.page').then(
        (module) => module.MarketBrowserPage,
      ),
  },
];
