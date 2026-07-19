import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { appLocaleFromPath, stripLocalePrefix } from './app/core/services/locale-support';
import { createAuthRouter } from './server/auth/auth.controller';
import { resolveValidSession } from './server/auth/auth-session-resolver';
import { readAuthConfig } from './server/auth/auth-session';
import { formatErrorForLogSafe, registerCompactProcessErrorLogging } from './server/server-log';
import { resolveBackendOrigin } from './backend-origin';
import { resolveLocaleRedirect } from './server/locale-redirect';
import {
  getRequestIdentifiers,
  requestIdentifierMiddleware,
  setIdentifierResponseHeaders,
  type RequestIdentifiers,
} from './server/request-identifiers';
import { CLIENT_SESSION_ID_HEADER, CORRELATION_ID_HEADER } from './request-identifiers';

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
app.set('trust proxy', 1);
const angularApp = new AngularNodeAppEngine();
const authConfig = readAuthConfig();

app.use(requestIdentifierMiddleware);
app.use('/auth', createAuthRouter(authConfig));

registerCompactProcessErrorLogging();

function readRequestBody(req: express.Request): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on('data', (chunk: Buffer) => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

app.use('/api', async (req, res) => {
  const proxyStart = performance.now();
  const identifiers = getRequestIdentifiers(res);
  let backendHeadersMs = 0;
  let bodyReadMs = 0;

  try {
    const targetUrl = new URL(req.originalUrl, backendOrigin);
    const headers = new Headers();

    for (const [key, value] of Object.entries(req.headers)) {
      if (
        !value ||
        [
          'host',
          'cookie',
          'authorization',
          CORRELATION_ID_HEADER.toLowerCase(),
          CLIENT_SESSION_ID_HEADER.toLowerCase(),
        ].includes(key.toLowerCase())
      ) {
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
    headers.set(CORRELATION_ID_HEADER, identifiers.correlationId);
    if (identifiers.clientSessionId) {
      headers.set(CLIENT_SESSION_ID_HEADER, identifiers.clientSessionId);
    }
    const session = await resolveValidSession(req, res, authConfig);
    if (session) {
      headers.set('Authorization', `Bearer ${session.accessToken}`);
    }

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
      if (
        hopByHopHeaders.has(key.toLowerCase()) ||
        [CORRELATION_ID_HEADER.toLowerCase(), CLIENT_SESSION_ID_HEADER.toLowerCase()].includes(
          key.toLowerCase(),
        )
      ) {
        return;
      }
      res.setHeader(key, value);
    });
    setIdentifierResponseHeaders(res, identifiers);

    if (response.body) {
      const bodyReadStart = performance.now();
      const responseBody = Buffer.from(await response.arrayBuffer());
      bodyReadMs = elapsedMs(bodyReadStart);
      const responseSendStart = performance.now();
      res.once('finish', () => {
        logApiProxyTimingSafe({
          ...identifiers,
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
          ...identifiers,
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
      ...identifiers,
      method: req.method,
      path: req.path,
      error,
    });
    if (res.headersSent) {
      // Response already committed; avoid next(error) so Express error handlers
      // cannot attempt another write and crash the process.
      return;
    }
    sendBadGatewayResponse(res, identifiers);
  }
});

app.use(async (req, res, next) => {
  try {
    const redirect = await resolveLocaleRedirect(req, backendOrigin);
    if (!redirect) {
      next();
      return;
    }
    res.redirect(redirect.status, redirect.location);
  } catch (error) {
    console.error(`Locale redirect failed ${formatErrorForLogSafe(error)}`);
    next();
  }
});

function elapsedMs(start: number): number {
  return Math.round(performance.now() - start);
}

function logApiProxyTiming(timing: {
  correlationId: string;
  clientSessionId: string | null;
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
      `(correlationId=${timing.correlationId} clientSessionId=${timing.clientSessionId ?? 'none'} ` +
      `method=${timing.method} path=${timing.path} ` +
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
  correlationId: string;
  clientSessionId: string | null;
  method: string;
  path: string;
  error: unknown;
}): void {
  try {
    console.error(
      `API proxy failed in ${elapsedMs(context.proxyStartMs)}ms ` +
        `(correlationId=${context.correlationId} ` +
        `clientSessionId=${context.clientSessionId ?? 'none'} method=${context.method} ` +
        `path=${context.path} ` +
        `error=${formatErrorForLogSafe(context.error)})`,
    );
  } catch (logError) {
    console.error(
      `API proxy failed (logging error: ${formatErrorForLogSafe(logError)}) ` +
        `correlationId=${context.correlationId} clientSessionId=${context.clientSessionId ?? 'none'}`,
    );
  }
}

function logSsrRequestStartSafe(context: {
  correlationId: string;
  clientSessionId: string | null;
  method: string;
  path: string;
  host: string | undefined;
}): void {
  try {
    console.info(
      `SSR request started ` +
        `(correlationId=${context.correlationId} ` +
        `clientSessionId=${context.clientSessionId ?? 'none'} method=${context.method} ` +
        `path=${context.path} ` +
        `host=${context.host ?? 'unknown'})`,
    );
  } catch (logError) {
    console.error(`SSR request start log failed ${formatErrorForLogSafe(logError)}`);
  }
}

function logSsrCompletionSafe(context: {
  ssrStartMs: number;
  correlationId: string;
  clientSessionId: string | null;
  method: string;
  path: string;
  status: number;
}): void {
  try {
    console.info(
      `SSR request completed in ${elapsedMs(context.ssrStartMs)}ms ` +
        `(correlationId=${context.correlationId} ` +
        `clientSessionId=${context.clientSessionId ?? 'none'} method=${context.method} ` +
        `path=${context.path} ` +
        `status=${context.status})`,
    );
  } catch (logError) {
    console.error(`SSR request completion log failed ${formatErrorForLogSafe(logError)}`);
  }
}

function logSsrFailureSafe(context: {
  ssrStartMs: number;
  correlationId: string;
  clientSessionId: string | null;
  method: string;
  path: string;
  error: unknown;
}): void {
  try {
    console.error(
      `SSR request failed in ${elapsedMs(context.ssrStartMs)}ms ` +
        `(correlationId=${context.correlationId} ` +
        `clientSessionId=${context.clientSessionId ?? 'none'} method=${context.method} ` +
        `path=${context.path} ` +
        `error=${formatErrorForLogSafe(context.error)})`,
    );
  } catch (logError) {
    console.error(
      `SSR request failed (logging error: ${formatErrorForLogSafe(logError)}) ` +
        `correlationId=${context.correlationId} clientSessionId=${context.clientSessionId ?? 'none'}`,
    );
  }
}

function sendBadGatewayResponse(res: express.Response, identifiers: RequestIdentifiers): void {
  const payload = {
    error: 'Bad Gateway',
    correlationId: identifiers.correlationId,
    ...(identifiers.clientSessionId ? { clientSessionId: identifiers.clientSessionId } : {}),
  };
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
  const identifiers = getRequestIdentifiers(res);
  const requestedLocale = appLocaleFromOriginalUrl(req.originalUrl);
  const localizedDevRewrite = rewriteLocalePrefixForSingleLocaleDev(req);
  const responseLocale = localizedDevRewrite.locale ?? requestedLocale;
  res.once('finish', () => {
    logSsrCompletionSafe({
      ssrStartMs: ssrStart,
      ...identifiers,
      method: req.method,
      path: req.originalUrl,
      status: res.statusCode,
    });
  });
  logSsrRequestStartSafe({
    ...identifiers,
    method: req.method,
    path: req.originalUrl,
    host: req.headers.host,
  });

  angularApp
    .handle(req)
    .then(async (response) => {
      if (!response) {
        next();
        return;
      }
      writeResponseToNodeResponse(await withLocalizedBaseHref(response, responseLocale), res);
    })
    .catch((error: unknown) => {
      logSsrFailureSafe({
        ssrStartMs: ssrStart,
        ...identifiers,
        method: req.method,
        path: req.originalUrl,
        error,
      });
      next(error);
    })
    .finally(localizedDevRewrite.restore);
});

interface LocalizedDevRewrite {
  readonly locale: string | null;
  readonly restore: () => void;
}

function rewriteLocalePrefixForSingleLocaleDev(req: express.Request): LocalizedDevRewrite {
  const originalUrl = req.originalUrl;
  const url = req.url;
  const pathname = new URL(originalUrl || url || '/', 'http://localhost').pathname || '/';
  const locale = appLocaleFromPath(pathname);
  if (
    !locale ||
    (!isNgServeRuntime() && existsSync(join(import.meta.dirname, locale, 'main.server.mjs')))
  ) {
    return { locale: null, restore: () => undefined };
  }

  const strippedPath = stripLocalePrefix(pathname) || '/';
  const suffix = originalUrl.slice(pathname.length);
  req.url = `${strippedPath}${suffix}`;
  req.originalUrl = req.url;
  return {
    locale,
    restore: () => {
      req.url = url;
      req.originalUrl = originalUrl;
    },
  };
}

function isNgServeRuntime(): boolean {
  return process.argv.includes('serve') || process.title.includes('ng serve');
}

function appLocaleFromOriginalUrl(originalUrl: string): string | null {
  const pathname = new URL(originalUrl || '/', 'http://localhost').pathname || '/';
  return appLocaleFromPath(pathname);
}

async function withLocalizedBaseHref(response: Response, locale: string | null): Promise<Response> {
  if (!locale || !response.headers.get('content-type')?.includes('text/html')) {
    return response;
  }

  const html = await response.text();
  const servedDevLocale = appLocaleFromViteClientPath(html);
  const headers = new Headers(response.headers);
  headers.delete('content-length');
  const localizedHtml = rewriteDevAssetUrls(
    html.replace(/<base href="\/"\s*\/?>/, `<base href="/${locale}/">`),
    locale,
    servedDevLocale,
  );
  return new Response(localizedHtml, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

function appLocaleFromViteClientPath(html: string): string | null {
  const match = html.match(/src="\/([a-z]{2})\/@vite\/client"/);
  return match?.[1] ?? null;
}

function rewriteDevAssetUrls(
  html: string,
  requestedLocale: string,
  servedLocale: string | null,
): string {
  if (!servedLocale || servedLocale === requestedLocale) {
    return html;
  }
  return html.replace(
    /\b(src|href)="(?!\/|[a-z][a-z0-9+.-]*:|#)([^"]+)"/gi,
    (_match: string, attr: string, value: string) => `${attr}="/${servedLocale}/${value}"`,
  );
}

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
