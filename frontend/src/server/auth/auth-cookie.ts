import { parse, serialize } from 'cookie';
import type express from 'express';

import { firstHeaderValue } from './auth-config';

const cookiePath = '/';

export function readCookie(req: express.Request, name: string): string | null {
  const cookieHeader = req.headers.cookie;
  if (!cookieHeader) {
    return null;
  }

  try {
    return parse(cookieHeader)[name] ?? null;
  } catch {
    return null;
  }
}

export function writeCookie(
  res: express.Response,
  req: express.Request,
  name: string,
  value: string,
  maxAgeSeconds: number,
): void {
  setCookie(
    res,
    serialize(name, value, {
      path: cookiePath,
      httpOnly: true,
      sameSite: 'lax',
      maxAge: maxAgeSeconds,
      secure: shouldUseSecureCookie(req),
    }),
  );
}

export function clearCookie(res: express.Response, req: express.Request, name: string): void {
  setCookie(
    res,
    serialize(name, '', {
      path: cookiePath,
      httpOnly: true,
      sameSite: 'lax',
      maxAge: 0,
      secure: shouldUseSecureCookie(req),
    }),
  );
}

export function shouldUseSecureCookie(req: express.Request): boolean {
  return firstHeaderValue(req.headers['x-forwarded-proto']) === 'https' || req.secure;
}

function setCookie(res: express.Response, cookie: string): void {
  res.setHeader('Set-Cookie', appendSetCookie(res, cookie));
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
