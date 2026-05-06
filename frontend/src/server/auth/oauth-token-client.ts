import { AuthError } from './auth-error';
import type { AuthConfig, SessionPayload } from '../../app/api/auth/auth.model';

type TokenResponse = {
  access_token?: string;
  id_token?: string;
  refresh_token?: string;
  expires_in?: number;
  token_type?: string;
};

export async function exchangeCodeForTokens(input: {
  config: AuthConfig;
  code: string;
  codeVerifier: string;
  redirectUri: string;
}): Promise<SessionPayload> {
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: input.config.clientId,
    code: input.code,
    code_verifier: input.codeVerifier,
    redirect_uri: input.redirectUri,
  });
  return requestTokens(input.config, body);
}

export async function refreshSession(
  config: AuthConfig,
  session: SessionPayload,
): Promise<SessionPayload | null> {
  if (!session.refreshToken) {
    return null;
  }

  const body = new URLSearchParams({
    grant_type: 'refresh_token',
    client_id: config.clientId,
    refresh_token: session.refreshToken,
  });
  const refreshed = await requestTokens(config, body);

  return {
    ...refreshed,
    refreshToken: refreshed.refreshToken ?? session.refreshToken,
  };
}

async function requestTokens(config: AuthConfig, body: URLSearchParams): Promise<SessionPayload> {
  const response = await fetch(new URL('/oauth2/token', config.hostedUiBaseUrl), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body,
  });

  if (!response.ok) {
    throw new AuthError(
      'token_exchange_failed',
      `Cognito token request failed with ${response.status}`,
      502,
    );
  }

  const tokenResponse = (await response.json()) as TokenResponse;
  if (!tokenResponse.access_token || typeof tokenResponse.expires_in !== 'number') {
    throw new AuthError('token_exchange_failed', 'Cognito token response was invalid', 502);
  }

  return {
    accessToken: tokenResponse.access_token,
    refreshToken: tokenResponse.refresh_token,
    expiresAt: Date.now() + tokenResponse.expires_in * 1000,
  };
}
