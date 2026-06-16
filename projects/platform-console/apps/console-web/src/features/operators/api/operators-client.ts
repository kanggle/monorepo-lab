import { getServerEnv } from '@/shared/config/env';
import { getOperatorToken, getActiveTenant } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, OperatorsUnavailableError } from '@/shared/api/errors';

/**
 * Server-side IAM admin-service operators-management client — hardened HTTP
 * core (TASK-PC-FE-110 split of the former `operators-api.ts` god-file;
 * originally TASK-PC-FE-004 — ADR-MONO-013 Phase 2 slice 3, the MOST
 * privilege-sensitive slice: create/role/status = the
 * operator-privilege-escalation surface).
 *
 * Feature-internal: `callGapOperators` + `OPERATORS_PREFIX` are imported by the
 * sibling operators api modules (`operators-crud-api` / `operators-self-api` /
 * `operators-assignments-api`), NEVER re-exported through the `operators-api`
 * barrel (the public surface stays exactly the prior function set). 0 behavior
 * change.
 *
 * Server-only by construction (same posture as `accounts-api.ts` /
 * `audit-api.ts` / `registry-client.ts`): imported exclusively from server
 * components and the `runtime = 'nodejs'` route handlers; `getServerEnv()`
 * throws outside the server runtime. The operator token, operator emails
 * and passwords never reach client JS — client components call the
 * same-origin `/api/operators/**` proxy routes, which attach the HttpOnly
 * operator token here server-side.
 *
 * Auth invariant (console-integration-contract § 2.1/§ 2.4.3 — the #569
 * trust boundary): every call authenticates with the EXCHANGED operator
 * token (`getOperatorToken()`), NEVER the IAM OIDC access token. An absent
 * operator token ⇒ no usable operator session ⇒ `401 TOKEN_INVALID` (the
 * caller re-logins; the fetch is NOT made — no silent GAP-token fallback).
 *
 * Tenant invariant (§ 2.4 / multi-tenant): the operator's selected active
 * tenant is always sent as `X-Tenant-Id` (`getActiveTenant()`). When none
 * is selected the call is blocked with `400 NO_ACTIVE_TENANT` — never an
 * empty header (no cross-tenant leak). `create` additionally carries a
 * `tenantId` body field; the producer enforces the `*` platform-scope rule.
 *
 * PER-ENDPOINT HEADER MATRIX (§ 2.4.3 — NOT uniform; the key correctness
 * risk; do NOT blanket-apply the FE-002 reason+idempotency pair):
 *   - `GET  /operators`            → no mutation headers (read);
 *   - `POST /operators` (create)   → `X-Operator-Reason` + `Idempotency-Key`;
 *   - `PATCH .../{id}/roles`       → `X-Operator-Reason` ONLY (NO key);
 *   - `PATCH .../{id}/status`      → `X-Operator-Reason` ONLY (NO key);
 *   - `PATCH .../me/password`      → self path, valid token only (no
 *                                    reason / no key per the producer).
 * The reason-bearing mutations fail-safe on an empty reason BEFORE any
 * fetch (the UI's reason-capture gate is the source; this is the fail-safe).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout;
 * 401 → `ApiError` (forced re-login); 403/409/400/404 → `ApiError` (inline
 * actionable, no crash); 503/timeout → `OperatorsUnavailableError`
 * (operators section degrades only — shell intact).
 *
 * Logging: structured, server-side only; the operator token, operator
 * emails AND passwords are NEVER logged (redacted; passwords never logged
 * or echoed at all) — § 2.4.3 / § 2.6 logging invariant.
 */

export const OPERATORS_PREFIX = '/api/admin/operators';

type HttpMethod = 'GET' | 'POST' | 'PATCH' | 'PUT';

export interface CallOptions {
  method: HttpMethod;
  path: string;
  /** Operator-entered audit reason → `X-Operator-Reason` (create/roles/status). */
  reason?: string;
  /** ONLY `create` per the producer matrix. roles/status MUST NOT set this. */
  idempotencyKey?: string;
  /** JSON body (mutations). May contain a plaintext password — NEVER logged. */
  body?: unknown;
  /** Self path (`/me/password`) returns 204 with no body. */
  expectNoContent?: boolean;
}

/**
 * Single hardened call site. Resolves the operator token + active tenant,
 * applies the per-endpoint header matrix + timeout, and maps the producer
 * error envelope to the § 2.5 resilience taxonomy.
 *
 * SECURITY: `opts.body` may carry a plaintext password (create / change-
 * password). It is serialised straight into the request body and is NEVER
 * passed to the logger — only the request id / path / status are logged.
 */
export async function callGapOperators<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Trust boundary: the /api/admin/** credential is the EXCHANGED operator
  // token — never the IAM OIDC access token. Absent ⇒ 401, no fetch.
  const token = await getOperatorToken();
  if (!token) {
    logger.warn('operators_no_operator_session', {
      requestId,
      path: opts.path,
    });
    throw new ApiError(401, 'TOKEN_INVALID', 'No operator session');
  }

  // Multi-tenant: always send the selected tenant; block (no empty header)
  // when none is selected — never a cross-tenant / unscoped call.
  const tenant = await getActiveTenant();
  if (!tenant) {
    logger.warn('operators_no_active_tenant', {
      requestId,
      path: opts.path,
    });
    throw new ApiError(400, 'NO_ACTIVE_TENANT', 'No active tenant selected');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Tenant-Id': tenant,
    'X-Request-Id': requestId,
  };

  // PER-ENDPOINT HEADER MATRIX (§ 2.4.3). The reason MUST be non-empty for
  // the reason-bearing mutations — the UI's reason-capture gate is the
  // source; this is the fail-safe (no fetch on an empty reason).
  if (opts.reason !== undefined) {
    const reason = opts.reason.trim();
    if (reason === '') {
      logger.warn('operators_mutation_no_reason', {
        requestId,
        path: opts.path,
      });
      throw new ApiError(
        400,
        'REASON_REQUIRED',
        'An operator reason is required for this action',
      );
    }
    // TASK-MONO-176: HTTP header values are ByteStrings (ISO-8859-1); a
    // non-Latin-1 reason (e.g. Korean) makes `fetch()` throw before sending.
    // Percent-encode so the wire header is ASCII; the producer
    // (OperatorReasonDecodingFilter) decodes it back to the original UTF-8.
    headers['X-Operator-Reason'] = encodeURIComponent(reason);
  }
  // Idempotency-Key is ONLY ever set when the caller supplies one — and the
  // ONLY caller that supplies it is `create` (the matrix). roles/status/
  // password never pass it, so it is never attached there (a test pins the
  // ABSENCE on roles/status).
  if (opts.idempotencyKey !== undefined) {
    if (opts.idempotencyKey.trim() === '') {
      throw new ApiError(
        400,
        'VALIDATION_ERROR',
        'An idempotency key is required for this action',
      );
    }
    headers['Idempotency-Key'] = opts.idempotencyKey;
  }

  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    env.OPERATORS_TIMEOUT_MS,
  );

  try {
    const res = await fetch(`${env.IAM_ADMIN_API_BASE}${opts.path}`, {
      method: opts.method,
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
      };
      logger.warn('operators_unauthorized', {
        requestId,
        status: 401,
        code: errBody.code,
        path: opts.path,
      });
      // No partial authed state — caller forces a clean re-login.
      throw new ApiError(
        401,
        errBody.code ?? 'TOKEN_INVALID',
        'session expired',
      );
    }

    if (res.status === 503) {
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
      };
      const code = errBody.code ?? 'DOWNSTREAM_ERROR';
      logger.warn('operators_degraded', {
        requestId,
        status: 503,
        code,
        path: opts.path,
      });
      // Operators section degrades only — shell stays intact (§ 2.5).
      throw new OperatorsUnavailableError(
        code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        code,
        'IAM operators service unavailable',
      );
    }

    if (!res.ok) {
      // 403 PERMISSION_DENIED (not SUPER_ADMIN / lacks operator.manage),
      // 403 TENANT_SCOPE_DENIED, 409 OPERATOR_EMAIL_CONFLICT,
      // 400 ROLE_NOT_FOUND/VALIDATION_ERROR/STATE_TRANSITION_INVALID/
      //     SELF_SUSPEND_FORBIDDEN/CURRENT_PASSWORD_MISMATCH/
      //     PASSWORD_POLICY_VIOLATION, 404 OPERATOR_NOT_FOUND
      // → inline actionable error (no crash, no re-login loop).
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
        timestamp?: string;
      };
      logger.warn('operators_request_error', {
        requestId,
        status: res.status,
        code: errBody.code,
        path: opts.path,
      });
      throw new ApiError(
        res.status,
        errBody.code ?? `HTTP_${res.status}`,
        errBody.message ?? `operators request failed (${res.status})`,
        errBody.timestamp,
      );
    }

    logger.info('operators_ok', {
      requestId,
      status: res.status,
      path: opts.path,
    });

    if (opts.expectNoContent || res.status === 204) {
      return undefined as T;
    }
    return parse(await res.json());
  } catch (err) {
    if (
      err instanceof ApiError ||
      err instanceof OperatorsUnavailableError
    ) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('operators_timeout', {
        requestId,
        timeoutMs: env.OPERATORS_TIMEOUT_MS,
        path: opts.path,
      });
      throw new OperatorsUnavailableError(
        'timeout',
        'TIMEOUT',
        'IAM operators call timed out',
      );
    }
    logger.error('operators_error', { requestId, path: opts.path });
    throw new OperatorsUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'IAM operators call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}
