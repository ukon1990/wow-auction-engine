import type express from 'express';

import type { AuthConfig } from '../../app/api/auth/auth.model';

export function readAuthConfig(env: NodeJS.ProcessEnv = process.env): AuthConfig | null {
  const clientId = env['WAE_COGNITO_CLIENT_ID']?.trim();
  const hostedUiBaseUrl = env['WAE_COGNITO_HOSTED_UI_BASE_URL']?.trim();
  const sessionSecret = env['WAE_AUTH_SESSION_SECRET']?.trim();

  if (!clientId || !hostedUiBaseUrl || !sessionSecret) {
    return null;
  }

  return {
    clientId,
    hostedUiBaseUrl: hostedUiBaseUrl.replace(/\/+$/, ''),
    sessionSecret,
  };
}

export function getRequestOrigin(req: express.Request): string {
  const proto = firstHeaderValue(req.headers['x-forwarded-proto']) ?? req.protocol;
  const host = firstHeaderValue(req.headers['x-forwarded-host']) ?? req.headers.host;
  return `${proto}://${host}`;
}

export function callbackUri(req: express.Request): string {
  return `${getRequestOrigin(req)}/auth/callback`;
}

export function firstHeaderValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
