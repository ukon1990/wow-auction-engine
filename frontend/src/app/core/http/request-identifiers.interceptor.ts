import { isPlatformBrowser } from '@angular/common';
import { HttpInterceptorFn } from '@angular/common/http';
import { inject, PLATFORM_ID } from '@angular/core';
import { from, switchMap } from 'rxjs';

import { BASE_PATH } from '../../api/generated';
import { CLIENT_SESSION_ID_HEADER, CORRELATION_ID_HEADER } from '../../../request-identifiers';
import { ClientSessionIdService, generateUuid } from './client-session-id.service';

export const requestIdentifiersInterceptor: HttpInterceptorFn = (request, next) => {
  const platformId = inject(PLATFORM_ID);
  const apiBasePath = inject(BASE_PATH);
  if (!isProjectRequest(request.url, apiBasePath, isPlatformBrowser(platformId))) {
    return next(request);
  }

  const correlationId = generateUuid();
  return from(inject(ClientSessionIdService).get()).pipe(
    switchMap((clientSessionId) => {
      let headers = request.headers.set(CORRELATION_ID_HEADER, correlationId);
      if (clientSessionId) {
        headers = headers.set(CLIENT_SESSION_ID_HEADER, clientSessionId);
      }
      return next(request.clone({ headers }));
    }),
  );
};

export function isProjectRequest(url: string, apiBasePath: string, browser: boolean): boolean {
  if (matchesPathPrefix(url, '/api') || matchesPathPrefix(url, '/auth')) {
    return true;
  }
  if (matchesBaseUrl(url, apiBasePath)) {
    return true;
  }
  if (!browser) {
    return false;
  }

  try {
    const parsed = new URL(url, globalThis.location.origin);
    return (
      parsed.origin === globalThis.location.origin &&
      (matchesPathPrefix(parsed.pathname, '/api') || matchesPathPrefix(parsed.pathname, '/auth'))
    );
  } catch {
    return false;
  }
}

function matchesPathPrefix(value: string, prefix: string): boolean {
  return value === prefix || value.startsWith(`${prefix}/`) || value.startsWith(`${prefix}?`);
}

function matchesBaseUrl(url: string, baseUrl: string): boolean {
  const normalizedBase = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
  return (
    url === normalizedBase ||
    url.startsWith(`${normalizedBase}/`) ||
    url.startsWith(`${normalizedBase}?`)
  );
}
