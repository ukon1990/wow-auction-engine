import { mergeApplicationConfig, ApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { BASE_PATH } from './api/generated';
import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import { resolveBackendOrigin } from '../backend-origin';

const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    { provide: BASE_PATH, useValue: `${resolveBackendOrigin()}/api` },
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
