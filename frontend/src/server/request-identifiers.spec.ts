import type express from 'express';

import {
  acceptRequestIdentifiers,
  getRequestIdentifiers,
  requestIdentifierMiddleware,
} from './request-identifiers';

const validCorrelationId = '8f5ee3ab-1271-4ae7-87f0-a85bc9836753';
const validClientSessionId = '26999606-3d4c-4ae5-908f-d9de6fcf715f';
const fallbackCorrelationId = 'e9a57325-91c8-40fa-8ddd-39942622d929';
const validVersion7Id = '019bf061-8b2c-7d39-8c3e-3545b30f510d';

describe(acceptRequestIdentifiers.name, () => {
  it('accepts valid UUID identifiers', () => {
    expect(
      acceptRequestIdentifiers({
        'x-correlation-id': validCorrelationId,
        'x-client-session-id': validClientSessionId,
      }),
    ).toEqual({
      correlationId: validCorrelationId,
      clientSessionId: validClientSessionId,
    });
  });

  it('replaces an invalid correlation ID and drops an invalid client-session ID', () => {
    expect(
      acceptRequestIdentifiers(
        {
          'x-correlation-id': 'bad\nlog-entry',
          'x-client-session-id': 'not-a-uuid',
        },
        () => fallbackCorrelationId,
      ),
    ).toEqual({
      correlationId: fallbackCorrelationId,
      clientSessionId: null,
    });
  });

  it('accepts version 7 UUIDs and selects the first valid repeated header value', () => {
    expect(
      acceptRequestIdentifiers({
        'x-correlation-id': ['invalid', validVersion7Id],
        'x-client-session-id': validVersion7Id,
      }),
    ).toEqual({
      correlationId: validVersion7Id,
      clientSessionId: validVersion7Id,
    });
  });

  it('generates only the required correlation ID when both headers are absent', () => {
    expect(acceptRequestIdentifiers({}, () => fallbackCorrelationId)).toEqual({
      correlationId: fallbackCorrelationId,
      clientSessionId: null,
    });
  });

  it('stores accepted identifiers and echoes their canonical response headers', () => {
    const responseHeaders = new Map<string, number | string | readonly string[]>();
    const req = {
      headers: {
        'x-correlation-id': validCorrelationId,
        'x-client-session-id': validClientSessionId,
      },
    } as express.Request;
    const res = {
      locals: {},
      setHeader: (name: string, value: number | string | readonly string[]) =>
        responseHeaders.set(name, value),
    } as unknown as express.Response;
    const next = vi.fn();

    requestIdentifierMiddleware(req, res, next);

    expect(getRequestIdentifiers(res)).toEqual({
      correlationId: validCorrelationId,
      clientSessionId: validClientSessionId,
    });
    expect(responseHeaders.get('X-Correlation-ID')).toBe(validCorrelationId);
    expect(responseHeaders.get('X-Client-Session-ID')).toBe(validClientSessionId);
    expect(next).toHaveBeenCalledOnce();
  });
});
