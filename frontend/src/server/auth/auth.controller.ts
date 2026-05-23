import express from 'express';

import { formatErrorForLogSafe } from '../server-log';
import { resolveValidSession } from './auth-session-resolver';
import {
  authErrorResponse,
  authErrorStatus,
  buildLoginUrl,
  callbackUri,
  changePassword,
  clearOAuthStateCookie,
  clearSessionCookie,
  confirmPasswordReset,
  confirmSignUp,
  createOpaqueState,
  createPkcePair,
  authenticateWithPassword,
  exchangeCodeForTokens,
  getUserFromAccessToken,
  readOAuthState,
  requestPasswordReset,
  signUpWithPassword,
  writeOAuthStateCookie,
  writeSessionCookie,
  type AuthConfig,
} from './auth-session';

export function createAuthRouter(authConfig: AuthConfig | null): express.Router {
  const router = express.Router();

  router.use(express.json({ limit: '16kb' }));
  router.use(disableAuthResponseCaching);
  router.use(logAuthRequest);

  router.get('/login', (req, res) => {
    if (!authConfig) {
      res
        .status(503)
        .json({ error: 'Authentication is not configured', code: 'auth_not_configured' });
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

  router.post('/login', async (req, res) => {
    if (!authConfig) {
      res
        .status(503)
        .json({ error: 'Authentication is not configured', code: 'auth_not_configured' });
      return;
    }
    const credentials = readCredentials(req.body);
    if (!credentials) {
      res.status(400).json({ error: 'Email and password are required', code: 'invalid_request' });
      return;
    }

    try {
      const result = await authenticateWithPassword({
        config: authConfig,
        email: credentials.email,
        password: credentials.password,
      });
      if (result.status !== 'authenticated') {
        res.status(409).json({
          error: `Unsupported authentication challenge: ${result.challengeName}`,
          code: 'unsupported_challenge',
        });
        return;
      }
      writeSessionCookie(res, req, result.session, authConfig.sessionSecret);
      res.json({ authenticated: true });
    } catch (error) {
      console.error(`Password login failed ${formatErrorForLogSafe(error)}`);
      res.status(authErrorStatus(error, 401)).json(
        authErrorResponse(error, {
          message: 'Invalid email or password',
          code: 'invalid_credentials',
        }),
      );
    }
  });

  router.post('/signup', async (req, res) => {
    if (!authConfig) {
      res
        .status(503)
        .json({ error: 'Authentication is not configured', code: 'auth_not_configured' });
      return;
    }
    const credentials = readCredentials(req.body);
    if (!credentials) {
      res.status(400).json({ error: 'Email and password are required', code: 'invalid_request' });
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
      res.status(authErrorStatus(error, 400)).json(
        authErrorResponse(error, {
          message: 'Authentication request failed',
          code: 'cognito_request_failed',
        }),
      );
    }
  });

  router.post('/confirm', async (req, res) => {
    if (!authConfig) {
      res
        .status(503)
        .json({ error: 'Authentication is not configured', code: 'auth_not_configured' });
      return;
    }
    const email = readBodyString(req.body, 'email')?.trim().toLowerCase();
    const code = readBodyString(req.body, 'code')?.trim();
    if (!email || !code) {
      res
        .status(400)
        .json({ error: 'Email and confirmation code are required', code: 'invalid_request' });
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
      res.status(authErrorStatus(error, 400)).json(
        authErrorResponse(error, {
          message: 'Authentication request failed',
          code: 'cognito_request_failed',
        }),
      );
    }
  });

  router.post('/forgot-password', async (req, res) => {
    if (!authConfig) {
      res
        .status(503)
        .json({ error: 'Authentication is not configured', code: 'auth_not_configured' });
      return;
    }
    const email = readEmail(req.body);
    if (!email) {
      res.status(400).json({ error: 'Email is required', code: 'invalid_request' });
      return;
    }

    try {
      await requestPasswordReset({
        config: authConfig,
        email,
      });
      res.json({ requested: true });
    } catch (error) {
      console.error(`Password reset request failed ${formatErrorForLogSafe(error)}`);
      res.status(authErrorStatus(error, 400)).json(
        authErrorResponse(error, {
          message: 'Authentication request failed',
          code: 'cognito_request_failed',
        }),
      );
    }
  });

  router.post('/reset-password', async (req, res) => {
    if (!authConfig) {
      res
        .status(503)
        .json({ error: 'Authentication is not configured', code: 'auth_not_configured' });
      return;
    }
    const reset = readPasswordReset(req.body);
    if (!reset) {
      res.status(400).json({
        error: 'Email, confirmation code, and password are required',
        code: 'invalid_request',
      });
      return;
    }

    try {
      await confirmPasswordReset({
        config: authConfig,
        email: reset.email,
        code: reset.code,
        password: reset.password,
      });
      res.json({ reset: true });
    } catch (error) {
      console.error(`Password reset confirmation failed ${formatErrorForLogSafe(error)}`);
      res.status(authErrorStatus(error, 400)).json(
        authErrorResponse(error, {
          message: 'Authentication request failed',
          code: 'cognito_request_failed',
        }),
      );
    }
  });

  router.get('/callback', async (req, res) => {
    if (!authConfig) {
      res
        .status(503)
        .json({ error: 'Authentication is not configured', code: 'auth_not_configured' });
      return;
    }
    const code = readQueryParam(req.query['code']);
    const state = readQueryParam(req.query['state']);
    const oauthState = readOAuthState(req, authConfig.sessionSecret);
    clearOAuthStateCookie(res, req);

    if (!code || !state || !oauthState || oauthState.state !== state) {
      res.status(400).json({ error: 'Invalid authentication callback', code: 'invalid_callback' });
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
      res.status(authErrorStatus(error, 502)).json(
        authErrorResponse(error, {
          message: 'Authentication failed',
          code: 'token_exchange_failed',
        }),
      );
    }
  });

  router.get('/logout', (req, res) => {
    clearSessionCookie(res, req);
    res.redirect('/');
  });

  router.get('/me', async (req, res) => {
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
      res.json({ authenticated: true, email: user.email, roles: user.roles });
    } catch (error) {
      console.error(`Get current user failed ${formatErrorForLogSafe(error)}`);
      res.status(authErrorStatus(error, 502)).json(
        authErrorResponse(error, {
          message: 'Unable to read current user',
          code: 'current_user_unavailable',
        }),
      );
    }
  });

  router.post('/change-password', async (req, res) => {
    const session = await resolveValidSession(req, res, authConfig);
    if (!session) {
      res.status(401).json({ error: 'Sign in to change your password', code: 'session_required' });
      return;
    }
    const passwords = readPasswordChange(req.body);
    if (!passwords) {
      res.status(400).json({
        error: 'Current password and new password are required',
        code: 'invalid_request',
      });
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
      res.status(authErrorStatus(error, 400)).json(
        authErrorResponse(error, {
          message: 'Authentication request failed',
          code: 'cognito_request_failed',
        }),
      );
    }
  });

  return router;
}

function disableAuthResponseCaching(
  _req: express.Request,
  res: express.Response,
  next: express.NextFunction,
): void {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Pragma', 'no-cache');
  res.setHeader('Expires', '0');
  next();
}

function logAuthRequest(
  req: express.Request,
  res: express.Response,
  next: express.NextFunction,
): void {
  const start = performance.now();
  res.once('finish', () => {
    try {
      console.info(
        `Auth request completed in ${elapsedMs(start)}ms ` +
          `(method=${req.method} path=${req.originalUrl} status=${res.statusCode})`,
      );
    } catch (error) {
      console.error(`Auth request log failed ${formatErrorForLogSafe(error)}`);
    }
  });
  next();
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
  const email = readEmail(value);
  const password = readBodyString(value, 'password');
  if (!email || !password) {
    return null;
  }
  return { email, password };
}

function readEmail(value: unknown): string | null {
  if (!isRecord(value)) {
    return null;
  }
  return readBodyString(value, 'email')?.trim().toLowerCase() || null;
}

function readPasswordReset(
  value: unknown,
): { email: string; code: string; password: string } | null {
  if (!isRecord(value)) {
    return null;
  }
  const email = readEmail(value);
  const code = readBodyString(value, 'code')?.trim();
  const password = readBodyString(value, 'password');
  if (!email || !code || !password) {
    return null;
  }
  return { email, code, password };
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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function elapsedMs(start: number): number {
  return Math.round(performance.now() - start);
}
