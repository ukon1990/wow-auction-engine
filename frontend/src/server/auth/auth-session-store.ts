import type express from 'express';

import { clearCookie, readCookie, shouldUseSecureCookie, writeCookie } from './auth-cookie';
import { decryptPayload, encryptPayload } from './auth-crypto';
import type { OAuthStatePayload, SessionPayload } from '../../app/api/auth/auth.model';

const sessionCookieName = '__Host-wae_session';
const oauthCookieName = '__Host-wae_oauth';
const localSessionCookieName = 'wae_session';
const localOAuthCookieName = 'wae_oauth';
const cookieMaxAgeSeconds = 30 * 24 * 60 * 60;
const oauthCookieMaxAgeSeconds = 10 * 60;

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

export function sessionNeedsRefresh(session: SessionPayload, now = Date.now()): boolean {
  return now >= session.expiresAt - 60_000;
}

function resolvedSessionCookieName(req: express.Request): string {
  return shouldUseSecureCookie(req) ? sessionCookieName : localSessionCookieName;
}

function resolvedOAuthCookieName(req: express.Request): string {
  return shouldUseSecureCookie(req) ? oauthCookieName : localOAuthCookieName;
}
