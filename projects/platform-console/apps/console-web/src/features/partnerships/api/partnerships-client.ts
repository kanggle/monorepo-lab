import { getServerEnv } from '@/shared/config/env';
import { getOperatorToken, getActiveTenant } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, PartnershipsUnavailableError } from '@/shared/api/errors';

/**
 * Server-side IAM admin-service cross-org partnership client — hardened HTTP
 * core (TASK-PC-FE-187 / ADR-MONO-045 §3.4, admin-api.md § Partnership
 * Management, BE-476/477/478). Mirrors the operators-client
 * `callGapOperators` core (feature-isolated own copy — architecture forbids a
 * cross-feature import of the operators core).
 *
 * Auth invariant (§ 2.1 trust boundary): the `/api/admin/**` credential is the
 * EXCHANGED operator token (`getOperatorToken()`), NEVER the IAM OIDC access
 * token. Absent ⇒ `401 TOKEN_INVALID`, no fetch. A tenant owner
 * (`TENANT_ADMIN`) holds this operator token with `partnership.manage`.
 *
 * Tenant invariant (D2): the actor's active tenant is always sent as
 * `X-Tenant-Id` (`getActiveTenant()`); absent ⇒ `400 NO_ACTIVE_TENANT` (never
 * an empty header — no cross-tenant leak). The producer's D2 TenantScopeGuard
 * confines every op to the acting-side (host/partner) tenant.
 *
 * PER-ENDPOINT HEADER MATRIX (NOT uniform — the key correctness risk; mirror
 * of the operators non-uniform matrix, do NOT blanket-apply reason+key):
 *   - `GET  /partnerships`               → no mutation headers (read);
 *   - `POST /partnerships` (invite)      → `X-Operator-Reason` + `Idempotency-Key`;
 *   - `POST {id}:accept|:suspend|:reactivate|:terminate`
 *                                        → `X-Operator-Reason` ONLY (NO key);
 *   - `POST {id}/participants/{opId}`    → `X-Operator-Reason` ONLY (optional body);
 *   - `DELETE {id}/participants/{opId}`  → `X-Operator-Reason` ONLY, 204 no content.
 * The reason-bearing mutations fail-safe on an empty reason BEFORE any fetch
 * (the UI's reason-capture gate is the source; this is the fail-safe).
 *
 * Resilience (§ 2.5): AbortController hard timeout; 401 → `ApiError`
 * (re-login); 403/404/409/400/422 → `ApiError` (inline actionable — e.g. the
 * 409 ALREADY_EXISTS / 422 SCOPE_INVALID drive inline copy); 503/timeout →
 * `PartnershipsUnavailableError` (partnership surface degrades only). The
 * operator token is never logged.
 */

export const PARTNERSHIPS_PREFIX = '/api/admin/partnerships';

type HttpMethod = 'GET' | 'POST' | 'DELETE';

export interface CallOptions {
  method: HttpMethod;
  path: string;
  /** Operator-entered audit reason → `X-Operator-Reason` (every mutation). */
  reason?: string;
  /** ONLY `invite` per the producer matrix. accept/suspend/reactivate/
   *  terminate/participant MUST NOT set this. */
  idempotencyKey?: string;
  /** JSON body (invite / participant-add). */
  body?: unknown;
  /** participant-remove (DELETE) returns 204 with no body. */
  expectNoContent?: boolean;
}

export async function callPartnerships<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Trust boundary: the /api/admin/** credential is the EXCHANGED operator
  // token — never the IAM OIDC access token. Absent ⇒ 401, no fetch.
  const token = await getOperatorToken();
  if (!token) {
    logger.warn('partnerships_no_operator_session', {
      requestId,
      path: opts.path,
    });
    throw new ApiError(401, 'TOKEN_INVALID', 'No operator session');
  }

  // Multi-tenant (D2): always send the active tenant; block (no empty header)
  // when none is selected — never a cross-tenant / unscoped call.
  const tenant = await getActiveTenant();
  if (!tenant) {
    logger.warn('partnerships_no_active_tenant', {
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

  // PER-ENDPOINT HEADER MATRIX. The reason MUST be non-empty for the
  // reason-bearing mutations — the UI's reason-capture gate is the source;
  // this is the fail-safe (no fetch on an empty reason).
  if (opts.reason !== undefined) {
    const reason = opts.reason.trim();
    if (reason === '') {
      logger.warn('partnerships_mutation_no_reason', {
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
  // ONLY caller that supplies it is `invite` (the matrix). accept/suspend/
  // reactivate/terminate/participant never pass it, so it is never attached
  // there (a test pins the ABSENCE on accept).
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
    env.PARTNERSHIPS_TIMEOUT_MS,
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
      logger.warn('partnerships_unauthorized', {
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
      logger.warn('partnerships_degraded', {
        requestId,
        status: 503,
        code,
        path: opts.path,
      });
      // Partnership surface degrades only — shell stays intact (§ 2.5).
      throw new PartnershipsUnavailableError(
        code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        code,
        'IAM partnership service unavailable',
      );
    }

    if (!res.ok) {
      // 403 PERMISSION_DENIED / 403 PARTNERSHIP_SCOPE_DENIED,
      // 400 REASON_REQUIRED / VALIDATION_ERROR,
      // 422 PARTNERSHIP_SCOPE_INVALID / PARTICIPANT_NOT_OWN_OPERATOR /
      //     PARTICIPANT_SCOPE_EXCEEDS_DELEGATION,
      // 404 PARTNERSHIP_NOT_FOUND / OPERATOR_NOT_FOUND / PARTICIPANT_NOT_FOUND,
      // 409 PARTNERSHIP_ALREADY_EXISTS / PARTNERSHIP_TRANSITION_INVALID
      // → inline actionable error (no crash, no re-login loop).
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
        timestamp?: string;
      };
      logger.warn('partnerships_request_error', {
        requestId,
        status: res.status,
        code: errBody.code,
        path: opts.path,
      });
      throw new ApiError(
        res.status,
        errBody.code ?? `HTTP_${res.status}`,
        errBody.message ?? `partnership request failed (${res.status})`,
        errBody.timestamp,
      );
    }

    logger.info('partnerships_ok', {
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
      err instanceof PartnershipsUnavailableError
    ) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('partnerships_timeout', {
        requestId,
        timeoutMs: env.PARTNERSHIPS_TIMEOUT_MS,
        path: opts.path,
      });
      throw new PartnershipsUnavailableError(
        'timeout',
        'TIMEOUT',
        'IAM partnership call timed out',
      );
    }
    logger.error('partnerships_error', { requestId, path: opts.path });
    throw new PartnershipsUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'IAM partnership call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}
