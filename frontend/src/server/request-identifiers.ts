import type express from 'express';
import { randomUUID } from 'node:crypto';

import {
  CLIENT_SESSION_ID_HEADER,
  CORRELATION_ID_HEADER,
  isValidRequestIdentifier,
} from '../request-identifiers';

export interface RequestIdentifiers {
  readonly correlationId: string;
  readonly clientSessionId: string | null;
}

const responseLocalsKey = 'requestIdentifiers';

export function requestIdentifierMiddleware(
  req: express.Request,
  res: express.Response,
  next: express.NextFunction,
): void {
  const identifiers = acceptRequestIdentifiers(req.headers);
  res.locals[responseLocalsKey] = identifiers;
  setIdentifierResponseHeaders(res, identifiers);
  next();
}

export function acceptRequestIdentifiers(
  headers: Record<string, string | string[] | undefined>,
  generateCorrelationId: () => string = randomUUID,
): RequestIdentifiers {
  return {
    correlationId: readValidHeader(headers, CORRELATION_ID_HEADER) ?? generateCorrelationId(),
    clientSessionId: readValidHeader(headers, CLIENT_SESSION_ID_HEADER),
  };
}

export function getRequestIdentifiers(res: express.Response): RequestIdentifiers {
  const value: unknown = res.locals[responseLocalsKey];
  if (isRequestIdentifiers(value)) {
    return value;
  }
  throw new Error('Request identifier middleware was not registered');
}

export function setIdentifierResponseHeaders(
  res: express.Response,
  identifiers: RequestIdentifiers,
): void {
  res.setHeader(CORRELATION_ID_HEADER, identifiers.correlationId);
  if (identifiers.clientSessionId) {
    res.setHeader(CLIENT_SESSION_ID_HEADER, identifiers.clientSessionId);
  }
}

function readValidHeader(
  headers: Record<string, string | string[] | undefined>,
  header: string,
): string | null {
  const value = headers[header.toLowerCase()];
  const candidate = Array.isArray(value) ? value.find(isValidRequestIdentifier) : value;
  return isValidRequestIdentifier(candidate) ? candidate : null;
}

function isRequestIdentifiers(value: unknown): value is RequestIdentifiers {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Partial<RequestIdentifiers>;
  return (
    isValidRequestIdentifier(candidate.correlationId) &&
    (candidate.clientSessionId === null || isValidRequestIdentifier(candidate.clientSessionId))
  );
}
