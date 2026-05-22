import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideHighchartsTheme } from '@ui';

import { provideApi } from './api/generated/provide-api';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch()),
    provideApi('/api'),
    provideRouter(routes),
    provideClientHydration(withEventReplay()),
    provideHighchartsTheme(),
  ],
};
