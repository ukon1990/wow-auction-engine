import type express from 'express';

import { formatErrorForLogSafe } from '../server-log';
import {
  clearSessionCookie,
  readSession,
  refreshSession,
  sessionNeedsRefresh,
  writeSessionCookie,
  type AuthConfig,
  type SessionPayload,
} from './auth-session';

export async function resolveValidSession(
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
