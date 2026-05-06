import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { randomUUID } from 'node:crypto';
import { join } from 'node:path';
import { createAuthRouter } from './server/auth/auth.controller';
import { resolveValidSession } from './server/auth/auth-session-resolver';
import { readAuthConfig } from './server/auth/auth-session';
import { formatErrorForLogSafe, registerCompactProcessErrorLogging } from './server/server-log';
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

app.use('/auth', createAuthRouter(authConfig));

const requestIdHeader = 'x-request-id';

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
