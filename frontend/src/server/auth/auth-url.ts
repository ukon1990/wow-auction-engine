import type { AuthConfig } from '../../app/api/auth/auth.model';

export function buildLoginUrl(input: {
  config: AuthConfig;
  redirectUri: string;
  state: string;
  codeChallenge: string;
  screenHint?: 'signup';
}): string {
  const url = new URL('/oauth2/authorize', input.config.hostedUiBaseUrl);
  url.searchParams.set('client_id', input.config.clientId);
  url.searchParams.set('response_type', 'code');
  url.searchParams.set('scope', 'openid email profile');
  url.searchParams.set('redirect_uri', input.redirectUri);
  url.searchParams.set('state', input.state);
  url.searchParams.set('code_challenge', input.codeChallenge);
  url.searchParams.set('code_challenge_method', 'S256');

  if (input.screenHint) {
    url.searchParams.set('screen_hint', input.screenHint);
  }

  return url.toString();
}

export function buildLogoutUrl(input: { config: AuthConfig; logoutUri: string }): string {
  const url = new URL('/logout', input.config.hostedUiBaseUrl);
  url.searchParams.set('client_id', input.config.clientId);
  url.searchParams.set('logout_uri', input.logoutUri);
  return url.toString();
}
