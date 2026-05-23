import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideRouter, withRouterConfig } from '@angular/router';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideHighchartsTheme } from '@ui';

import { provideApi } from './api/generated/provide-api';
import { routes } from './app.routes';
import { selectedRealmFormatLocale } from './core/services/locale.service';
import { RealmSelectionService } from './core/services/realm-selection.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    {
      provide: LOCALE_ID,
      useFactory: selectedRealmFormatLocale,
    },
    provideAppInitializer(() => inject(RealmSelectionService).hydrateStoredSelectionFromApi()),
    provideHttpClient(withFetch()),
    provideApi('/api'),
    provideRouter(
      routes,
      withRouterConfig({
        onSameUrlNavigation: 'reload',
      }),
    ),
    provideClientHydration(withEventReplay()),
    provideHighchartsTheme(),
  ],
};
