export const CORRELATION_ID_HEADER = 'X-Correlation-ID';
export const CLIENT_SESSION_ID_HEADER = 'X-Client-Session-ID';

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isValidRequestIdentifier(value: string | null | undefined): value is string {
  return typeof value === 'string' && uuidPattern.test(value);
}
