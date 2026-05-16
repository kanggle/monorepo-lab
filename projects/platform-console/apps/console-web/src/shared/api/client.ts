import { ApiError } from './errors';

/**
 * The ONLY backend entry point for client components (architecture.md
 * § Forbidden Dependencies: no direct `fetch()` in components).
 *
 * Client components never talk to GAP / domain gateways directly — they call
 * same-origin Next.js route handlers (`/api/...`) which attach the HttpOnly
 * cookie operator token server-side. JS never reads a token.
 *
 * - Always sends cookies (`credentials: 'include'`).
 * - On 401, attempts a single refresh via `/api/auth/refresh` then retries.
 * - On refresh failure, redirects to `/login?redirect=<current>`.
 */

export interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  skipAuthRetry?: boolean;
}

function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

async function parseError(res: Response): Promise<ApiError> {
  let code = 'UNKNOWN';
  let message = res.statusText || 'Request failed';
  let timestamp: string | undefined;
  try {
    const data = (await res.clone().json()) as Record<string, unknown>;
    code = (data.code as string) ?? code;
    message = (data.message as string) ?? message;
    timestamp = data.timestamp as string | undefined;
  } catch {
    /* keep defaults */
  }
  return new ApiError(res.status, code, message, timestamp);
}

async function doFetch(path: string, opts: ApiRequestOptions): Promise<Response> {
  const headers = new Headers(opts.headers);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  if (opts.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  return fetch(path, {
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

export async function apiFetch<T = unknown>(
  path: string,
  opts: ApiRequestOptions = {},
): Promise<T> {
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

  if (!res.ok) throw await parseError(res);
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
};
