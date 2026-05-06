import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { randomUUID } from 'node:crypto';
import { join } from 'node:path';
import {
  buildLoginUrl,
  buildLogoutUrl,
  callbackUri,
  changePassword,
  clearOAuthStateCookie,
  clearSessionCookie,
  confirmSignUp,
  createOpaqueState,
  createPkcePair,
  authenticateWithPassword,
  exchangeCodeForTokens,
  getUserFromAccessToken,
  getRequestOrigin,
  readAuthConfig,
  readOAuthState,
  readSession,
  refreshSession,
  sessionNeedsRefresh,
  signUpWithPassword,
  writeOAuthStateCookie,
  writeSessionCookie,
  type AuthConfig,
  type SessionPayload,
} from './auth-session';
import { resolveBackendOrigin } from './backend-origin';

const browserDistFolder = join(import.meta.dirname, '../browser');
const backendOrigin = resolveBackendOrigin();
const hopByHopHeaders = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
  'content-length',
]);

const app = express();
const angularApp = new AngularNodeAppEngine();
const authConfig = readAuthConfig();

app.use('/auth', express.json({ limit: '16kb' }));

const requestIdHeader = 'x-request-id';
const maxErrorMessageLength = 256;
const maxStackLines = 6;

registerCompactProcessErrorLogging();

function readRequestBody(req: express.Request): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on('data', (chunk: Buffer) => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

app.get('/auth/login', (req, res) => {
  if (!authConfig) {
    res.status(503).json({ error: 'Authentication is not configured' });
    return;
  }
  const state = createOpaqueState();
  const pkce = createPkcePair();
  const returnTo = sanitizeReturnTo(req.query['returnTo']);
  const screenHint = readQueryParam(req.query['mode']) === 'signup' ? 'signup' : undefined;
  writeOAuthStateCookie(
    res,
    req,
    {
      state,
      codeVerifier: pkce.verifier,
      returnTo,
    },
    authConfig.sessionSecret,
  );
  res.redirect(
    buildLoginUrl({
      config: authConfig,
      redirectUri: callbackUri(req),
      state,
      codeChallenge: pkce.challenge,
      screenHint,
    }),
  );
});

app.post('/auth/login', async (req, res) => {
  if (!authConfig) {
    res.status(503).json({ error: 'Authentication is not configured' });
    return;
  }
  const credentials = readCredentials(req.body);
  if (!credentials) {
    res.status(400).json({ error: 'Email and password are required' });
    return;
  }

  try {
    const result = await authenticateWithPassword({
      config: authConfig,
      email: credentials.email,
      password: credentials.password,
    });
    if (result.status !== 'authenticated') {
      res
        .status(409)
        .json({ error: `Unsupported authentication challenge: ${result.challengeName}` });
      return;
    }
    writeSessionCookie(res, req, result.session, authConfig.sessionSecret);
    res.json({ authenticated: true });
  } catch (error) {
    console.error(`Password login failed ${formatErrorForLogSafe(error)}`);
    res.status(401).json({ error: 'Invalid email or password' });
  }
});

app.post('/auth/signup', async (req, res) => {
  if (!authConfig) {
    res.status(503).json({ error: 'Authentication is not configured' });
    return;
  }
  const credentials = readCredentials(req.body);
  if (!credentials) {
    res.status(400).json({ error: 'Email and password are required' });
    return;
  }

  try {
    const result = await signUpWithPassword({
      config: authConfig,
      email: credentials.email,
      password: credentials.password,
    });
    res.status(201).json({
      confirmed: result.confirmed,
      email: credentials.email,
    });
  } catch (error) {
    console.error(`Password signup failed ${formatErrorForLogSafe(error)}`);
    res.status(400).json({ error: userSafeAuthError(error) });
  }
});

app.post('/auth/confirm', async (req, res) => {
  if (!authConfig) {
    res.status(503).json({ error: 'Authentication is not configured' });
    return;
  }
  const email = readBodyString(req.body, 'email')?.trim().toLowerCase();
  const code = readBodyString(req.body, 'code')?.trim();
  if (!email || !code) {
    res.status(400).json({ error: 'Email and confirmation code are required' });
    return;
  }

  try {
    await confirmSignUp({
      config: authConfig,
      email,
      code,
    });
    res.json({ confirmed: true });
  } catch (error) {
    console.error(`Signup confirmation failed ${formatErrorForLogSafe(error)}`);
    res.status(400).json({ error: userSafeAuthError(error) });
  }
});

app.get('/auth/callback', async (req, res) => {
  if (!authConfig) {
    res.status(503).json({ error: 'Authentication is not configured' });
    return;
  }
  const code = readQueryParam(req.query['code']);
  const state = readQueryParam(req.query['state']);
  const oauthState = readOAuthState(req, authConfig.sessionSecret);
  clearOAuthStateCookie(res, req);

  if (!code || !state || !oauthState || oauthState.state !== state) {
    res.status(400).json({ error: 'Invalid authentication callback' });
    return;
  }

  try {
    const session = await exchangeCodeForTokens({
      config: authConfig,
      code,
      codeVerifier: oauthState.codeVerifier,
      redirectUri: callbackUri(req),
    });
    writeSessionCookie(res, req, session, authConfig.sessionSecret);
    res.redirect(oauthState.returnTo);
  } catch (error) {
    console.error(`Authentication callback failed ${formatErrorForLogSafe(error)}`);
    res.status(502).json({ error: 'Authentication failed' });
  }
});

app.get('/auth/logout', (req, res) => {
  clearSessionCookie(res, req);
  if (!authConfig) {
    res.redirect('/');
    return;
  }
  res.redirect(
    buildLogoutUrl({
      config: authConfig,
      logoutUri: `${getRequestOrigin(req)}/`,
    }),
  );
});

app.get('/auth/me', async (req, res) => {
  const session = await resolveValidSession(req, res, authConfig);
  if (!session) {
    res.status(401).json({ authenticated: false });
    return;
  }
  try {
    const user = await getUserFromAccessToken({
      config: authConfig!,
      accessToken: session.accessToken,
    });
    res.json({ authenticated: true, email: user.email });
  } catch (error) {
    console.error(`Get current user failed ${formatErrorForLogSafe(error)}`);
    res.status(502).json({ error: 'Unable to read current user' });
  }
});

app.post('/auth/change-password', async (req, res) => {
  const session = await resolveValidSession(req, res, authConfig);
  if (!session) {
    res.status(401).json({ error: 'Sign in to change your password' });
    return;
  }
  const passwords = readPasswordChange(req.body);
  if (!passwords) {
    res.status(400).json({ error: 'Current password and new password are required' });
    return;
  }

  try {
    await changePassword({
      config: authConfig!,
      accessToken: session.accessToken,
      previousPassword: passwords.currentPassword,
      proposedPassword: passwords.newPassword,
    });
    res.json({ changed: true });
  } catch (error) {
    console.error(`Password change failed ${formatErrorForLogSafe(error)}`);
    res.status(400).json({ error: passwordChangeError(error) });
  }
});

app.use('/api', async (req, res) => {
  const proxyStart = performance.now();
  const requestId = readRequestId(req) ?? randomUUID();
  let backendHeadersMs = 0;
  let bodyReadMs = 0;

  try {
    const targetUrl = new URL(req.originalUrl, backendOrigin);
    const headers = new Headers();

    for (const [key, value] of Object.entries(req.headers)) {
      if (!value || ['host', 'cookie', 'authorization'].includes(key.toLowerCase())) {
        continue;
      }
      if (Array.isArray(value)) {
        for (const item of value) {
          headers.append(key, item);
        }
      } else {
        headers.set(key, value);
      }
    }
    headers.set(requestIdHeader, requestId);
    const session = await resolveValidSession(req, res, authConfig);
    if (session) {
      headers.set('Authorization', `Bearer ${session.accessToken}`);
    }
    res.setHeader('X-Request-Id', requestId);

    const bodyBuffer = ['GET', 'HEAD'].includes(req.method)
      ? undefined
      : await readRequestBody(req);
    const body = bodyBuffer
      ? (bodyBuffer.buffer.slice(
          bodyBuffer.byteOffset,
          bodyBuffer.byteOffset + bodyBuffer.byteLength,
        ) as ArrayBuffer)
      : undefined;
    const backendFetchStart = performance.now();
    const response = await fetch(targetUrl, {
      method: req.method,
      headers,
      body,
    });
    backendHeadersMs = elapsedMs(backendFetchStart);

    res.status(response.status);
    response.headers.forEach((value, key) => {
      if (hopByHopHeaders.has(key.toLowerCase())) {
        return;
      }
      res.setHeader(key, value);
    });

    if (response.body) {
      const bodyReadStart = performance.now();
      const responseBody = Buffer.from(await response.arrayBuffer());
      bodyReadMs = elapsedMs(bodyReadStart);
      const responseSendStart = performance.now();
      res.once('finish', () => {
        logApiProxyTimingSafe({
          requestId,
          method: req.method,
          path: targetUrl.pathname,
          status: response.status,
          backendHeadersMs,
          bodyReadMs,
          responseSendMs: elapsedMs(responseSendStart),
          totalMs: elapsedMs(proxyStart),
        });
      });
      res.send(responseBody);
    } else {
      const responseSendStart = performance.now();
      res.once('finish', () => {
        logApiProxyTimingSafe({
          requestId,
          method: req.method,
          path: targetUrl.pathname,
          status: response.status,
          backendHeadersMs,
          bodyReadMs,
          responseSendMs: elapsedMs(responseSendStart),
          totalMs: elapsedMs(proxyStart),
        });
      });
      res.end();
    }
  } catch (error) {
    logApiProxyFailureSafe({
      proxyStartMs: proxyStart,
      requestId,
      method: req.method,
      path: req.path,
      error,
    });
    if (res.headersSent) {
      // Response already committed; avoid next(error) so Express error handlers
      // cannot attempt another write and crash the process.
      return;
    }
    sendBadGatewayResponse(res, requestId);
  }
});

async function resolveValidSession(
  req: express.Request,
  res: express.Response,
  config: AuthConfig | null,
): Promise<SessionPayload | null> {
  if (!config) {
    return null;
  }
  const session = readSession(req, config.sessionSecret);
  if (!session) {
    return null;
  }
  if (!sessionNeedsRefresh(session)) {
    return session;
  }
  try {
    const refreshed = await refreshSession(config, session);
    if (!refreshed) {
      clearSessionCookie(res, req);
      return null;
    }
    writeSessionCookie(res, req, refreshed, config.sessionSecret);
    return refreshed;
  } catch (error) {
    console.error(`Session refresh failed ${formatErrorForLogSafe(error)}`);
    clearSessionCookie(res, req);
    return null;
  }
}

function sanitizeReturnTo(value: unknown): string {
  const raw = readQueryParam(value);
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/auth/')) {
    return '/';
  }
  return raw;
}

function readQueryParam(value: unknown): string | null {
  if (Array.isArray(value)) {
    return readQueryParam(value[0]);
  }
  return typeof value === 'string' && value.trim() ? value : null;
}

function readCredentials(value: unknown): { email: string; password: string } | null {
  if (!isRecord(value)) {
    return null;
  }
  const email = readBodyString(value, 'email')?.trim().toLowerCase();
  const password = readBodyString(value, 'password');
  if (!email || !password) {
    return null;
  }
  return { email, password };
}

function readPasswordChange(
  value: unknown,
): { currentPassword: string; newPassword: string } | null {
  if (!isRecord(value)) {
    return null;
  }
  const currentPassword = readBodyString(value, 'currentPassword');
  const newPassword = readBodyString(value, 'newPassword');
  if (!currentPassword || !newPassword) {
    return null;
  }
  return { currentPassword, newPassword };
}

function readBodyString(value: unknown, key: string): string | null {
  if (!isRecord(value)) {
    return null;
  }
  const item = value[key];
  return typeof item === 'string' ? item : null;
}

function userSafeAuthError(error: unknown): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return 'Authentication request failed';
}

function passwordChangeError(error: unknown): string {
  if (error instanceof Error && /notauthorized|incorrect|previouspassword/i.test(error.message)) {
    return 'Current password is incorrect';
  }
  return userSafeAuthError(error);
}

function readRequestId(req: express.Request): string | null {
  const value = req.headers[requestIdHeader];
  if (Array.isArray(value)) {
    return value.find((item) => item.trim()) ?? null;
  }
  return value?.trim() || null;
}

function elapsedMs(start: number): number {
  return Math.round(performance.now() - start);
}

function logApiProxyTiming(timing: {
  requestId: string;
  method: string;
  path: string;
  status: number;
  backendHeadersMs: number;
  bodyReadMs: number;
  responseSendMs: number;
  totalMs: number;
}): void {
  console.info(
    `API proxy completed in ${timing.totalMs}ms ` +
      `(requestId=${timing.requestId} method=${timing.method} path=${timing.path} ` +
      `status=${timing.status} backendHeaders=${timing.backendHeadersMs}ms ` +
      `bodyRead=${timing.bodyReadMs}ms responseSend=${timing.responseSendMs}ms)`,
  );
}

function logApiProxyTimingSafe(timing: Parameters<typeof logApiProxyTiming>[0]): void {
  try {
    logApiProxyTiming(timing);
  } catch (logError) {
    console.error(`API proxy timing log failed ${formatErrorForLogSafe(logError)}`);
  }
}

function logApiProxyFailureSafe(context: {
  proxyStartMs: number;
  requestId: string;
  method: string;
  path: string;
  error: unknown;
}): void {
  try {
    console.error(
      `API proxy failed in ${elapsedMs(context.proxyStartMs)}ms ` +
        `(requestId=${context.requestId} method=${context.method} path=${context.path} ` +
        `error=${formatErrorForLogSafe(context.error)})`,
    );
  } catch (logError) {
    console.error(
      `API proxy failed (logging error: ${formatErrorForLogSafe(logError)}) ` +
        `requestId=${context.requestId}`,
    );
  }
}

function logSsrRequestStartSafe(context: {
  requestId: string;
  method: string;
  path: string;
  host: string | undefined;
}): void {
  try {
    console.info(
      `SSR request started ` +
        `(requestId=${context.requestId} method=${context.method} path=${context.path} ` +
        `host=${context.host ?? 'unknown'})`,
    );
  } catch (logError) {
    console.error(`SSR request start log failed ${formatErrorForLogSafe(logError)}`);
  }
}

function logSsrCompletionSafe(context: {
  ssrStartMs: number;
  requestId: string;
  method: string;
  path: string;
  status: number;
}): void {
  try {
    console.info(
      `SSR request completed in ${elapsedMs(context.ssrStartMs)}ms ` +
        `(requestId=${context.requestId} method=${context.method} path=${context.path} ` +
        `status=${context.status})`,
    );
  } catch (logError) {
    console.error(`SSR request completion log failed ${formatErrorForLogSafe(logError)}`);
  }
}

function logSsrFailureSafe(context: {
  ssrStartMs: number;
  requestId: string;
  method: string;
  path: string;
  error: unknown;
}): void {
  try {
    console.error(
      `SSR request failed in ${elapsedMs(context.ssrStartMs)}ms ` +
        `(requestId=${context.requestId} method=${context.method} path=${context.path} ` +
        `error=${formatErrorForLogSafe(context.error)})`,
    );
  } catch (logError) {
    console.error(
      `SSR request failed (logging error: ${formatErrorForLogSafe(logError)}) ` +
        `requestId=${context.requestId}`,
    );
  }
}

function sendBadGatewayResponse(res: express.Response, requestId: string): void {
  const payload = { error: 'Bad Gateway', requestId };
  if (res.headersSent) {
    return;
  }
  try {
    res.status(502).json(payload);
    return;
  } catch {
    /* fall through */
  }
  if (res.headersSent) {
    return;
  }
  try {
    res.status(502).type('json').send(JSON.stringify(payload));
    return;
  } catch {
    /* fall through */
  }
  if (res.headersSent) {
    return;
  }
  try {
    res.status(502).type('text/plain; charset=utf-8').send('Bad Gateway');
    return;
  } catch {
    /* fall through */
  }
  if (res.headersSent) {
    return;
  }
  try {
    res.statusCode = 502;
    res.end();
  } catch {
    /* exhausted fallbacks */
  }
}

function formatErrorForLogSafe(error: unknown): string {
  try {
    return formatErrorForLog(error);
  } catch {
    return 'unknown error';
  }
}

function registerCompactProcessErrorLogging(): void {
  process.on('uncaughtException', (error) => {
    console.error(`uncaughtException ${formatErrorForLog(error)}`);
  });
  process.on('unhandledRejection', (reason) => {
    console.error(`unhandledRejection ${formatErrorForLog(reason)}`);
  });
}

function formatErrorForLog(error: unknown): string {
  if (error instanceof Error) {
    const details = compactObjectDetails(error);
    const stack = compactStack(error.stack);
    return [formatErrorNameAndMessage(error.name, error.message), details, stack]
      .filter(Boolean)
      .join(' ');
  }

  if (isRecord(error)) {
    return [
      formatErrorNameAndMessage(readString(error, 'name'), readString(error, 'message')),
      compactObjectDetails(error),
    ]
      .filter(Boolean)
      .join(' ');
  }

  return truncate(String(error), maxErrorMessageLength);
}

function formatErrorNameAndMessage(name: string | null, message: string | null): string {
  const errorName = name || 'Error';
  return message ? `${errorName}: ${truncate(message, maxErrorMessageLength)}` : errorName;
}

function compactObjectDetails(error: Error | Record<string, unknown>): string {
  const record = error as Record<string, unknown>;
  const details = [
    formatDetail(record, 'code'),
    formatDetail(record, 'status'),
    formatDetail(record, 'statusText'),
    formatDetail(record, 'url'),
    formatDetail(record, 'type'),
  ].filter(Boolean);

  return details.length ? `(${details.join(' ')})` : '';
}

function formatDetail(record: Record<string, unknown>, key: string): string | null {
  const value = record[key];
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return `${key}=${truncate(String(value), 200)}`;
  }
  return null;
}

function compactStack(stack: string | undefined): string {
  if (!stack) {
    return '';
  }
  const lines = stack
    .split('\n')
    .slice(1, maxStackLines + 1)
    .map((line) => line.trim())
    .filter(Boolean);

  return lines.length ? `stack="${lines.join(' | ')}"` : '';
}

function readString(record: Record<string, unknown>, key: string): string | null {
  const value = record[key];
  return typeof value === 'string' ? value : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function truncate(value: string, maxLength: number): string {
  return value.length > maxLength ? `${value.slice(0, maxLength - 1)}…` : value;
}

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  const ssrStart = performance.now();
  const requestId = readRequestId(req) ?? randomUUID();
  res.setHeader('X-Request-Id', requestId);
  res.once('finish', () => {
    logSsrCompletionSafe({
      ssrStartMs: ssrStart,
      requestId,
      method: req.method,
      path: req.originalUrl,
      status: res.statusCode,
    });
  });
  logSsrRequestStartSafe({
    requestId,
    method: req.method,
    path: req.originalUrl,
    host: req.headers.host,
  });

  angularApp
    .handle(req)
    .then((response) => (response ? writeResponseToNodeResponse(response, res) : next()))
    .catch((error: unknown) => {
      logSsrFailureSafe({
        ssrStartMs: ssrStart,
        requestId,
        method: req.method,
        path: req.originalUrl,
        error,
      });
      next(error);
    });
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
