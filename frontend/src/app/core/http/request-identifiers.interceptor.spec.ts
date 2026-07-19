import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { BASE_PATH } from '../../api/generated';
import {
  CLIENT_SESSION_ID_HEADER,
  CORRELATION_ID_HEADER,
  isValidRequestIdentifier,
} from '../../../request-identifiers';
import { requestIdentifiersInterceptor } from './request-identifiers.interceptor';

describe('requestIdentifiersInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: BASE_PATH, useValue: '/api' },
        provideHttpClient(withInterceptors([requestIdentifiersInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('adds a fresh correlation ID and a stable tab ID to API and auth requests', async () => {
    http.get('/api/items').subscribe();
    await waitForInterceptor();
    const apiRequest = controller.expectOne('/api/items');
    const firstCorrelationId = apiRequest.request.headers.get(CORRELATION_ID_HEADER);
    const clientSessionId = apiRequest.request.headers.get(CLIENT_SESSION_ID_HEADER);
    apiRequest.flush({});

    http.get('/auth/me').subscribe();
    await waitForInterceptor();
    const authRequest = controller.expectOne('/auth/me');

    expect(isValidRequestIdentifier(firstCorrelationId)).toBe(true);
    expect(isValidRequestIdentifier(clientSessionId)).toBe(true);
    expect(authRequest.request.headers.get(CORRELATION_ID_HEADER)).not.toBe(firstCorrelationId);
    expect(authRequest.request.headers.get(CLIENT_SESSION_ID_HEADER)).toBe(clientSessionId);
    authRequest.flush({});
  });

  it('does not attach identifiers to third-party requests', () => {
    http.get('https://example.com/api/items').subscribe();

    const request = controller.expectOne('https://example.com/api/items');
    expect(request.request.headers.has(CORRELATION_ID_HEADER)).toBe(false);
    expect(request.request.headers.has(CLIENT_SESSION_ID_HEADER)).toBe(false);
    request.flush({});
  });

  it('covers interceptor-backed logout requests', async () => {
    http.post('/auth/logout', null).subscribe();
    await waitForInterceptor();

    const request = controller.expectOne('/auth/logout');
    expect(isValidRequestIdentifier(request.request.headers.get(CORRELATION_ID_HEADER))).toBe(true);
    expect(isValidRequestIdentifier(request.request.headers.get(CLIENT_SESSION_ID_HEADER))).toBe(
      true,
    );
    request.flush(null, { status: 204, statusText: 'No Content' });
  });
});

describe('requestIdentifiersInterceptor during SSR', () => {
  it('adds only a correlation ID to the absolute backend API base', async () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'server' },
        { provide: BASE_PATH, useValue: 'http://backend:8080/api' },
        provideHttpClient(withInterceptors([requestIdentifiersInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    const http = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);

    http.get('http://backend:8080/api/items').subscribe();
    await waitForInterceptor();
    const request = controller.expectOne('http://backend:8080/api/items');

    expect(isValidRequestIdentifier(request.request.headers.get(CORRELATION_ID_HEADER))).toBe(true);
    expect(request.request.headers.has(CLIENT_SESSION_ID_HEADER)).toBe(false);
    request.flush({});
    controller.verify();
  });
});

async function waitForInterceptor(): Promise<void> {
  await new Promise((resolve) => globalThis.setTimeout(resolve, 35));
}
