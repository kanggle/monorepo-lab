import { ApiError, messageForCode, MFA_REQUIRED_CODE } from './errors';
import { buildStepUpRedirectFor } from '@/shared/lib/login-redirect';
import { isSampleErrorCode, SAMPLE_READ_ONLY } from '@/shared/sample/codes';
import { publishSampleRefusal } from '@/shared/lib/sample-refusal';
import {
  OPERATOR_CHECK_UNAVAILABLE,
  OPERATOR_CHECK_UNAVAILABLE_CODE,
} from '@/shared/lib/iam-token-refusal';

/**
 * The ONLY backend entry point for client components (architecture.md
 * § Forbidden Dependencies: no direct `fetch()` in components).
 *
 * Client components never talk to IAM / domain gateways directly — they call
 * same-origin Next.js route handlers (`/api/...`) which attach the HttpOnly
 * cookie operator token server-side. JS never reads a token.
 *
 * - Always sends cookies (`credentials: 'include'`).
 * - On 401, attempts a single refresh via `/api/auth/refresh` then retries.
 * - On refresh failure, redirects to `/login?redirect=<current>`.
 * - On refresh `403 MFA_REQUIRED` (TASK-MONO-771), navigates to
 *   `/api/auth/step-up?redirect=<current>` instead.
 * - On refresh `503 OPERATOR_CHECK_UNAVAILABLE` (TASK-MONO-772 S4, § 2.6.3),
 *   navigates to `/login?error=operator_check_unavailable&redirect=<current>`
 *   — the reason is shown; the BFF kept every cookie.
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
  let details: unknown;
  try {
    const data = (await res.clone().json()) as Record<string, unknown>;
    code = (data.code as string) ?? code;
    message = (data.message as string) ?? message;
    timestamp = data.timestamp as string | undefined;
    // TASK-PC-FE-318 — the route handlers pass the producer's `details` through.
    details = data.details;
  } catch {
    /* keep defaults */
  }
  // ADR-MONO-074 R1ⓐ / A9 — a sample refusal's copy comes from the ONE
  // code → copy mapping, never from the wire message (the route handlers pass
  // the cores' overwritten `'not permitted'` through). Rewriting it HERE, at the
  // single client entry point, means every renderer that prints `err.message`
  // shows the copy too — and renderers that look the code up already do.
  if (isSampleErrorCode(code)) {
    message = messageForCode(code);
    // Renderers that print a fixed string never read the error at all; the
    // `(console)` shell hears this signal and says it once for them.
    if (code === SAMPLE_READ_ONLY) publishSampleRefusal(code);
  }
  return new ApiError(res.status, code, message, timestamp, details);
}

async function doFetch(path: string, opts: ApiRequestOptions): Promise<Response> {
  const headers = new Headers(opts.headers);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  if (opts.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  // DEMO-URL-EXEMPT: same-origin — 이 앱 자신의 라우트 핸들러다(백엔드가 아니다).
  //   브라우저 경로는 상대경로, 서버 경로는 호출자가 건넨 `selfOrigin()`.
  return fetch(path, {
    ...opts,
    headers,
    credentials: 'include',
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  });
}

/**
 * `ok` — refreshed; `mfa_required` — the operator re-exchange answered
 * `403 MFA_REQUIRED` (TASK-MONO-771, contract § 2.6.1); `operator_check_unavailable`
 * — IAM could not ask whether the personal account is an operator (TASK-MONO-772,
 * § 2.6.3); `failed` — anything else.
 */
type RefreshResult = 'ok' | 'mfa_required' | 'operator_check_unavailable' | 'failed';

let inflightRefresh: Promise<RefreshResult> | null = null;

async function refreshSession(): Promise<RefreshResult> {
  if (inflightRefresh) return inflightRefresh;
  inflightRefresh = (async (): Promise<RefreshResult> => {
    try {
      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        credentials: 'include',
      });
      if (res.ok) return 'ok';
      if (res.status === 403) {
        const body = (await res.json().catch(() => ({}))) as { code?: unknown };
        if (body.code === MFA_REQUIRED_CODE) return 'mfa_required';
      }
      if (res.status === 503) {
        const body = (await res.json().catch(() => ({}))) as { code?: unknown };
        if (body.code === OPERATOR_CHECK_UNAVAILABLE_CODE) return 'operator_check_unavailable';
      }
      return 'failed';
    } catch {
      return 'failed';
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

/** TASK-MONO-772 S4 (§ 2.6.3) — the reason travels with the bounce. */
function redirectToLoginWithOperatorCheckUnavailable() {
  if (!isBrowser()) return;
  const current = window.location.pathname + window.location.search;
  window.location.assign(
    `/login?error=${OPERATOR_CHECK_UNAVAILABLE}&redirect=${encodeURIComponent(current)}`,
  );
}

/** TASK-MONO-771 (§ 2.6.1) — the browser client's step-up navigation. */
function redirectToStepUp() {
  if (!isBrowser()) return;
  const current = window.location.pathname + window.location.search;
  window.location.assign(buildStepUpRedirectFor(current));
}

export async function apiFetch<T = unknown>(
  path: string,
  opts: ApiRequestOptions = {},
): Promise<T> {
  let res = await doFetch(path, opts);

  if (res.status === 401 && !opts.skipAuthRetry && isBrowser()) {
    const refreshed = await refreshSession();
    if (refreshed === 'ok') {
      res = await doFetch(path, opts);
    } else if (refreshed === 'mfa_required') {
      // Never `/login` and never `/onboarding` — the operator needs a second
      // factor, not a new identity (contract § 2.6.1).
      redirectToStepUp();
      throw new ApiError(403, MFA_REQUIRED_CODE, 'Second factor required');
    } else if (refreshed === 'operator_check_unavailable') {
      redirectToLoginWithOperatorCheckUnavailable();
      throw new ApiError(503, OPERATOR_CHECK_UNAVAILABLE_CODE, 'Operator check unavailable');
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
  // PUT — used by the org-scope set proxy (TASK-PC-FE-050), whose route
  // handler is PUT-only (full-replace of the assignment's org_scope).
  put: <T = unknown>(path: string, body?: unknown, opts: ApiRequestOptions = {}) =>
    apiFetch<T>(path, { ...opts, method: 'PUT', body }),
  // PATCH — partial-update mutations (ecommerce product/variant update + stock
  // adjust, TASK-PC-FE-081, whose route handlers are PATCH).
  patch: <T = unknown>(path: string, body?: unknown, opts: ApiRequestOptions = {}) =>
    apiFetch<T>(path, { ...opts, method: 'PATCH', body }),
  // DELETE — resource removal (ecommerce product/variant delete,
  // TASK-PC-FE-081). The route handler returns 204 → `undefined`.
  delete: <T = unknown>(path: string, opts: ApiRequestOptions = {}) =>
    apiFetch<T>(path, { ...opts, method: 'DELETE' }),
};
