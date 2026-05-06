import { createCipheriv, createDecipheriv, createHash, randomBytes } from 'node:crypto';
import type express from 'express';

const sessionCookieName = '__Host-wae_session';
const oauthCookieName = '__Host-wae_oauth';
const localSessionCookieName = 'wae_session';
const localOAuthCookieName = 'wae_oauth';
const cookieMaxAgeSeconds = 30 * 24 * 60 * 60;
const oauthCookieMaxAgeSeconds = 10 * 60;
const encryptionVersion = 'v1';

export type AuthConfig = {
  clientId: string;
  hostedUiBaseUrl: string;
  sessionSecret: string;
};

export type SessionPayload = {
  accessToken: string;
  idToken?: string;
  refreshToken?: string;
  expiresAt: number;
};

export type PasswordAuthResult =
  | {
      status: 'authenticated';
      session: SessionPayload;
    }
  | {
      status: 'challenge';
      challengeName: string;
      session?: string;
    };

type OAuthStatePayload = {
  state: string;
  codeVerifier: string;
  returnTo: string;
};

type TokenResponse = {
  access_token: string;
  id_token: string;
  refresh_token?: string;
  expires_in: number;
  token_type: string;
};

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

export function createPkcePair(): { verifier: string; challenge: string } {
  const verifier = base64Url(randomBytes(32));
  const challenge = base64Url(createHash('sha256').update(verifier).digest());
  return { verifier, challenge };
}

export function createOpaqueState(): string {
  return base64Url(randomBytes(24));
}

export function encryptPayload(payload: unknown, secret: string): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', keyFromSecret(secret), iv);
  const plaintext = Buffer.from(JSON.stringify(payload), 'utf8');
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const tag = cipher.getAuthTag();
  return [encryptionVersion, base64Url(iv), base64Url(tag), base64Url(ciphertext)].join('.');
}

export function decryptPayload<T>(value: string, secret: string): T | null {
  const [version, ivValue, tagValue, ciphertextValue] = value.split('.');
  if (version !== encryptionVersion || !ivValue || !tagValue || !ciphertextValue) {
    return null;
  }

  try {
    const decipher = createDecipheriv('aes-256-gcm', keyFromSecret(secret), fromBase64Url(ivValue));
    decipher.setAuthTag(fromBase64Url(tagValue));
    const plaintext = Buffer.concat([
      decipher.update(fromBase64Url(ciphertextValue)),
      decipher.final(),
    ]);
    return JSON.parse(plaintext.toString('utf8')) as T;
  } catch {
    return null;
  }
}

export function readSession(req: express.Request, secret: string): SessionPayload | null {
  const raw = readCookie(req, sessionCookieName) ?? readCookie(req, localSessionCookieName);
  if (!raw) {
    return null;
  }
  return decryptPayload<SessionPayload>(raw, secret);
}

export function writeSessionCookie(
  res: express.Response,
  req: express.Request,
  payload: SessionPayload,
  secret: string,
): void {
  writeCookie(
    res,
    req,
    resolvedSessionCookieName(req),
    encryptPayload(payload, secret),
    cookieMaxAgeSeconds,
  );
}

export function clearSessionCookie(res: express.Response, req: express.Request): void {
  clearCookie(res, req, sessionCookieName);
  clearCookie(res, req, localSessionCookieName);
}

export function writeOAuthStateCookie(
  res: express.Response,
  req: express.Request,
  payload: OAuthStatePayload,
  secret: string,
): void {
  writeCookie(
    res,
    req,
    resolvedOAuthCookieName(req),
    encryptPayload(payload, secret),
    oauthCookieMaxAgeSeconds,
  );
}

export function readOAuthState(req: express.Request, secret: string): OAuthStatePayload | null {
  const raw = readCookie(req, oauthCookieName) ?? readCookie(req, localOAuthCookieName);
  if (!raw) {
    return null;
  }
  return decryptPayload<OAuthStatePayload>(raw, secret);
}

export function clearOAuthStateCookie(res: express.Response, req: express.Request): void {
  clearCookie(res, req, oauthCookieName);
  clearCookie(res, req, localOAuthCookieName);
}

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

export async function signUpWithPassword(input: {
  config: AuthConfig;
  email: string;
  password: string;
}): Promise<{ confirmed: boolean }> {
  const response = await cognitoJson(input.config, 'AWSCognitoIdentityProviderService.SignUp', {
    ClientId: input.config.clientId,
    Username: input.email,
    Password: input.password,
    UserAttributes: [
      {
        Name: 'email',
        Value: input.email,
      },
    ],
  });
  return {
    confirmed: Boolean(response['UserConfirmed']),
  };
}

export async function confirmSignUp(input: {
  config: AuthConfig;
  email: string;
  code: string;
}): Promise<void> {
  await cognitoJson(input.config, 'AWSCognitoIdentityProviderService.ConfirmSignUp', {
    ClientId: input.config.clientId,
    Username: input.email,
    ConfirmationCode: input.code,
  });
}

export async function authenticateWithPassword(input: {
  config: AuthConfig;
  email: string;
  password: string;
}): Promise<PasswordAuthResult> {
  const response = await cognitoJson(
    input.config,
    'AWSCognitoIdentityProviderService.InitiateAuth',
    {
      AuthFlow: 'USER_PASSWORD_AUTH',
      ClientId: input.config.clientId,
      AuthParameters: {
        USERNAME: input.email,
        PASSWORD: input.password,
      },
    },
  );

  const result = response['AuthenticationResult'];
  if (isRecord(result)) {
    return {
      status: 'authenticated',
      session: sessionFromAuthenticationResult(result),
    };
  }

  return {
    status: 'challenge',
    challengeName: String(response['ChallengeName'] ?? 'UNKNOWN'),
    session: typeof response['Session'] === 'string' ? response['Session'] : undefined,
  };
}

export async function getUserFromAccessToken(input: {
  config: AuthConfig;
  accessToken: string;
}): Promise<{ email: string | null }> {
  const response = await cognitoJson(input.config, 'AWSCognitoIdentityProviderService.GetUser', {
    AccessToken: input.accessToken,
  });
  const attributes = Array.isArray(response['UserAttributes']) ? response['UserAttributes'] : [];
  return {
    email: readCognitoAttribute(attributes, 'email'),
  };
}

export async function changePassword(input: {
  config: AuthConfig;
  accessToken: string;
  previousPassword: string;
  proposedPassword: string;
}): Promise<void> {
  await cognitoJson(input.config, 'AWSCognitoIdentityProviderService.ChangePassword', {
    AccessToken: input.accessToken,
    PreviousPassword: input.previousPassword,
    ProposedPassword: input.proposedPassword,
  });
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

export function sessionNeedsRefresh(session: SessionPayload, now = Date.now()): boolean {
  return now >= session.expiresAt - 60_000;
}

export function getRequestOrigin(req: express.Request): string {
  const proto = firstHeaderValue(req.headers['x-forwarded-proto']) ?? req.protocol;
  const host = firstHeaderValue(req.headers['x-forwarded-host']) ?? req.headers.host;
  return `${proto}://${host}`;
}

export function callbackUri(req: express.Request): string {
  return `${getRequestOrigin(req)}/auth/callback`;
}

function writeCookie(
  res: express.Response,
  req: express.Request,
  name: string,
  value: string,
  maxAgeSeconds: number,
): void {
  const secure = shouldUseSecureCookie(req);
  res.setHeader(
    'Set-Cookie',
    appendSetCookie(
      res,
      [
        `${name}=${encodeURIComponent(value)}`,
        'Path=/',
        'HttpOnly',
        'SameSite=Lax',
        `Max-Age=${maxAgeSeconds}`,
        secure ? 'Secure' : '',
      ]
        .filter(Boolean)
        .join('; '),
    ),
  );
}

function clearCookie(res: express.Response, req: express.Request, name: string): void {
  const secure = shouldUseSecureCookie(req);
  res.setHeader(
    'Set-Cookie',
    appendSetCookie(
      res,
      [`${name}=`, 'Path=/', 'HttpOnly', 'SameSite=Lax', 'Max-Age=0', secure ? 'Secure' : '']
        .filter(Boolean)
        .join('; '),
    ),
  );
}

function appendSetCookie(res: express.Response, cookie: string): string[] {
  const current = res.getHeader('Set-Cookie');
  if (!current) {
    return [cookie];
  }
  if (Array.isArray(current)) {
    return [...current.map(String), cookie];
  }
  return [String(current), cookie];
}

function readCookie(req: express.Request, name: string): string | null {
  const cookieHeader = req.headers.cookie;
  if (!cookieHeader) {
    return null;
  }
  for (const part of cookieHeader.split(';')) {
    const [rawName, ...rawValue] = part.trim().split('=');
    if (rawName === name) {
      return decodeURIComponent(rawValue.join('='));
    }
  }
  return null;
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
    throw new Error(`Cognito token request failed with ${response.status}`);
  }
  const tokenResponse = (await response.json()) as TokenResponse;
  return {
    accessToken: tokenResponse.access_token,
    refreshToken: tokenResponse.refresh_token,
    expiresAt: Date.now() + tokenResponse.expires_in * 1000,
  };
}

async function cognitoJson(
  config: AuthConfig,
  target: string,
  payload: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const response = await fetch(cognitoApiUrl(config), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-amz-json-1.1',
      'X-Amz-Target': target,
    },
    body: JSON.stringify(payload),
  });
  const body = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) {
    const message =
      readString(body, 'message') ??
      readString(body, 'Message') ??
      readString(body, '__type') ??
      `Cognito request failed with ${response.status}`;
    throw new Error(message);
  }
  return body;
}

function cognitoApiUrl(config: AuthConfig): string {
  const hostedUrl = new URL(config.hostedUiBaseUrl);
  const region = hostedUrl.hostname.match(/\.auth\.([a-z0-9-]+)\.amazoncognito\.com$/)?.[1];
  if (!region) {
    throw new Error('Unable to resolve Cognito region from hosted UI URL');
  }
  return `https://cognito-idp.${region}.amazonaws.com/`;
}

function sessionFromAuthenticationResult(result: Record<string, unknown>): SessionPayload {
  const accessToken = readString(result, 'AccessToken');
  const expiresIn = typeof result['ExpiresIn'] === 'number' ? result['ExpiresIn'] : 3600;
  if (!accessToken) {
    throw new Error('Cognito authentication response did not include tokens');
  }
  return {
    accessToken,
    refreshToken: readString(result, 'RefreshToken') ?? undefined,
    expiresAt: Date.now() + expiresIn * 1000,
  };
}

function keyFromSecret(secret: string): Buffer {
  return createHash('sha256').update(secret).digest();
}

function base64Url(value: Buffer): string {
  return value.toString('base64url');
}

function fromBase64Url(value: string): Buffer {
  return Buffer.from(value, 'base64url');
}

function firstHeaderValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function resolvedSessionCookieName(req: express.Request): string {
  return shouldUseSecureCookie(req) ? sessionCookieName : localSessionCookieName;
}

function resolvedOAuthCookieName(req: express.Request): string {
  return shouldUseSecureCookie(req) ? oauthCookieName : localOAuthCookieName;
}

function shouldUseSecureCookie(req: express.Request): boolean {
  return firstHeaderValue(req.headers['x-forwarded-proto']) === 'https' || req.secure;
}

function readString(record: Record<string, unknown>, key: string): string | null {
  const value = record[key];
  return typeof value === 'string' ? value : null;
}

function readCognitoAttribute(attributes: unknown[], name: string): string | null {
  for (const attribute of attributes) {
    if (!isRecord(attribute)) {
      continue;
    }
    if (readString(attribute, 'Name') === name) {
      return readString(attribute, 'Value');
    }
  }
  return null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
