import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withRouterConfig } from '@angular/router';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideHighchartsTheme } from '@ui';

import { provideApi } from './api/generated/provide-api';
import { routes } from './app.routes';
import { selectedRealmFormatLocale } from './core/services/locale.service';
import { RealmSelectionService } from './core/services/realm-selection.service';
import { requestIdentifiersInterceptor } from './core/http/request-identifiers.interceptor';
import { ClientSessionIdService } from './core/http/client-session-id.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAppInitializer(() => inject(ClientSessionIdService).initialize()),
    {
      provide: LOCALE_ID,
      useFactory: selectedRealmFormatLocale,
    },
    provideAppInitializer(() => inject(RealmSelectionService).hydrateStoredSelectionFromApi()),
    provideHttpClient(withFetch(), withInterceptors([requestIdentifiersInterceptor])),
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
