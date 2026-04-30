import { ApiError } from './errors';

/**
 * Fetch wrapper used by every component / hook.
 *
 * Rules enforced here:
 * - Always sends cookies (`credentials: 'include'`) — access/refresh tokens are HttpOnly
 *   cookies set by the Next.js Route Handlers (`/api/auth/*`). JS never reads tokens.
 * - On 401, attempts a single refresh via `/api/auth/refresh` and retries.
 * - On refresh failure, redirects to `/login?redirect=<current>`.
 * - Idempotency-Key + X-Operator-Reason are caller-provided via `writeCommand` helpers.
 */

export interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  idempotencyKey?: string;
  operatorReason?: string;
  /** Skip automatic 401→refresh retry. Used by /api/auth/refresh itself. */
  skipAuthRetry?: boolean;
  /** Override base URL (defaults to same-origin relative). */
  baseUrl?: string;
}

function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

function resolveUrl(path: string, baseUrl?: string): string {
  if (/^https?:\/\//i.test(path)) return path;
  if (baseUrl) return `${baseUrl.replace(/\/$/, '')}${path.startsWith('/') ? path : `/${path}`}`;
  return path; // same-origin (Next route handler or relative)
}

async function parseErrorBody(res: Response): Promise<ApiError> {
  let code = 'UNKNOWN';
  let message = res.statusText || 'Request failed';
  let timestamp: string | undefined;
  let extra: Record<string, unknown> = {};
  try {
    const data = (await res.clone().json()) as Record<string, unknown>;
    code = (data.code as string) ?? code;
    message = (data.message as string) ?? message;
    timestamp = data.timestamp as string | undefined;
    const { code: _, message: _m, timestamp: _t, ...rest } = data;
    extra = rest;
  } catch {
    // ignore parse error — keep defaults
  }
  return new ApiError(res.status, code, message, timestamp, extra);
}

async function doFetch(path: string, opts: ApiRequestOptions): Promise<Response> {
  const headers = new Headers(opts.headers);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  if (opts.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (opts.idempotencyKey) headers.set('Idempotency-Key', opts.idempotencyKey);
  if (opts.operatorReason) headers.set('X-Operator-Reason', opts.operatorReason);

  return fetch(resolveUrl(path, opts.baseUrl), {
    ...opts,
    headers,
    credentials: 'include',
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  });
}

let inflightRefresh: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  if (inflightRefresh) return inflightRefresh;
  inflightRefresh = (async () => {
    try {
      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        credentials: 'include',
      });
      return res.ok;
    } catch {
      return false;
    } finally {
      // Allow subsequent refreshes later
      setTimeout(() => {
        inflightRefresh = null;
      }, 0);
    }
  })();
  return inflightRefresh;
}

function redirectToLogin() {
  if (!isBrowser()) return;
  const current = window.location.pathname + window.location.search;
  window.location.assign(`/login?redirect=${encodeURIComponent(current)}`);
}

export async function apiFetch<T = unknown>(path: string, opts: ApiRequestOptions = {}): Promise<T> {
  let res = await doFetch(path, opts);

  if (res.status === 401 && !opts.skipAuthRetry && isBrowser()) {
    const refreshed = await refreshSession();
    if (refreshed) {
      res = await doFetch(path, opts);
    } else {
      redirectToLogin();
      throw new ApiError(401, 'TOKEN_INVALID', 'Session expired');
    }
  }

  if (!res.ok) throw await parseErrorBody(res);

  if (res.status === 204) return undefined as T;
  const ct = res.headers.get('Content-Type') ?? '';
  if (ct.includes('application/json')) return (await res.json()) as T;
  return (await res.text()) as unknown as T;
}

export const apiClient = {
  get: <T = unknown>(path: string, opts: ApiRequestOptions = {}) =>
    apiFetch<T>(path, { ...opts, method: 'GET' }),
  post: <T = unknown>(path: string, body?: unknown, opts: ApiRequestOptions = {}) =>
    apiFetch<T>(path, { ...opts, method: 'POST', body }),
  patch: <T = unknown>(path: string, body?: unknown, opts: ApiRequestOptions = {}) =>
    apiFetch<T>(path, { ...opts, method: 'PATCH', body }),
  put: <T = unknown>(path: string, body?: unknown, opts: ApiRequestOptions = {}) =>
    apiFetch<T>(path, { ...opts, method: 'PUT', body }),
  del: <T = unknown>(path: string, opts: ApiRequestOptions = {}) =>
    apiFetch<T>(path, { ...opts, method: 'DELETE' }),
};
