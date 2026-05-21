import { getServerEnv } from '@/shared/config/env';
import { getOperatorToken, getActiveTenant } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, OperatorsUnavailableError } from '@/shared/api/errors';
import {
  OperatorPageSchema,
  OperatorSummarySchema,
  type OperatorPage,
  type OperatorListParams,
  CreateOperatorResultSchema,
  type CreateOperatorResult,
  type CreateOperatorInput,
  EditRolesResultSchema,
  type EditRolesResult,
  ChangeStatusResultSchema,
  type ChangeStatusResult,
  type ChangePasswordInput,
  type OperatorStatus,
  type UpdateProfileInput,
} from './types';

/**
 * Server-side GAP admin-service operators-management client
 * (TASK-PC-FE-004 — ADR-MONO-013 Phase 2 slice 3, the MOST
 * privilege-sensitive slice: create/role/status = the
 * operator-privilege-escalation surface).
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
 * token (`getOperatorToken()`), NEVER the GAP OIDC access token. An absent
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

const OPERATORS_PREFIX = '/api/admin/operators';

type HttpMethod = 'GET' | 'POST' | 'PATCH';

interface CallOptions {
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
async function callGapOperators<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Trust boundary: the /api/admin/** credential is the EXCHANGED operator
  // token — never the GAP OIDC access token. Absent ⇒ 401, no fetch.
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
    headers['X-Operator-Reason'] = reason;
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
    const res = await fetch(`${env.GAP_ADMIN_API_BASE}${opts.path}`, {
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
        'GAP operators service unavailable',
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
        'GAP operators call timed out',
      );
    }
    logger.error('operators_error', { requestId, path: opts.path });
    throw new OperatorsUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'GAP operators call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

// ---------------------------------------------------------------------------
// 1. list — GET /api/admin/operators (status filter + pagination; READ)
//    No mutation headers (per the matrix).
// ---------------------------------------------------------------------------

export async function listOperators(
  params: OperatorListParams = {},
): Promise<OperatorPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(Math.min(100, Math.max(1, params.size ?? 20))));
  return callGapOperators(
    { method: 'GET', path: `${OPERATORS_PREFIX}?${qs.toString()}` },
    (json) => OperatorPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 2. create — POST /api/admin/operators
//    HEADERS: X-Operator-Reason + Idempotency-Key (BOTH required, per the
//    producer). The password is in the body — server-side only, NEVER
//    logged/echoed.
// ---------------------------------------------------------------------------

export async function createOperator(
  input: CreateOperatorInput,
  reason: string,
  idempotencyKey: string,
): Promise<CreateOperatorResult> {
  return callGapOperators(
    {
      method: 'POST',
      path: OPERATORS_PREFIX,
      reason,
      idempotencyKey,
      body: {
        email: input.email,
        displayName: input.displayName,
        password: input.password,
        roles: input.roles,
        tenantId: input.tenantId,
      },
    },
    (json) => CreateOperatorResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 3. edit-roles — PATCH /api/admin/operators/{operatorId}/roles
//    HEADERS: X-Operator-Reason ONLY. The producer does NOT list
//    Idempotency-Key — sending it is a contract deviation; full-replace
//    PATCH is idempotent producer-side. `[]` allowed = remove all roles.
// ---------------------------------------------------------------------------

export async function editOperatorRoles(
  operatorId: string,
  roles: string[],
  reason: string,
): Promise<EditRolesResult> {
  return callGapOperators(
    {
      method: 'PATCH',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/roles`,
      reason,
      // NO idempotencyKey — per the producer header matrix (§ 2.4.3).
      body: { roles },
    },
    (json) => EditRolesResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 4. change-status — PATCH /api/admin/operators/{operatorId}/status
//    HEADERS: X-Operator-Reason ONLY (NO Idempotency-Key — idempotent PATCH).
// ---------------------------------------------------------------------------

export async function changeOperatorStatus(
  operatorId: string,
  status: OperatorStatus,
  reason: string,
): Promise<ChangeStatusResult> {
  return callGapOperators(
    {
      method: 'PATCH',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/status`,
      reason,
      // NO idempotencyKey — per the producer header matrix (§ 2.4.3).
      body: { status },
    },
    (json) => ChangeStatusResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 5. change-password — PATCH /api/admin/operators/me/password
//    SELF only (there is no admin-set-other-password endpoint). Valid
//    operator token only (no operator.manage, no X-Operator-Reason, no
//    Idempotency-Key per the producer). 204 No Content on success. The
//    current/new passwords are server-side only — NEVER logged/echoed.
// ---------------------------------------------------------------------------

export async function changeOwnPassword(
  input: ChangePasswordInput,
): Promise<void> {
  await callGapOperators(
    {
      method: 'PATCH',
      path: `${OPERATORS_PREFIX}/me/password`,
      // NO reason, NO idempotencyKey — self auth flow per the producer.
      body: {
        currentPassword: input.currentPassword,
        newPassword: input.newPassword,
      },
      expectNoContent: true,
    },
    () => undefined,
  );
}

// ---------------------------------------------------------------------------
// 6. update-profile — PATCH /api/admin/operators/me/profile (TASK-BE-306 /
//    TASK-PC-FE-016). SELF only (no admin-set-other-profile). Valid operator
//    token only — no X-Operator-Reason, no Idempotency-Key per the producer.
//    204 No Content on success. The value is the operator's chosen
//    finance-platform account UUID (opaque to GAP — TASK-BE-304 § Decision
//    authority); `null` clears the column. Body shape mirrors the read shape
//    on console-registry-api `operatorContext.defaultAccountId` verbatim.
// ---------------------------------------------------------------------------

export async function updateOwnProfile(
  input: UpdateProfileInput,
): Promise<void> {
  await callGapOperators(
    {
      method: 'PATCH',
      path: `${OPERATORS_PREFIX}/me/profile`,
      // NO reason, NO idempotencyKey — self auth flow per the producer.
      body: {
        operatorContext: { defaultAccountId: input.defaultAccountId },
      },
      expectNoContent: true,
    },
    () => undefined,
  );
}

// ---------------------------------------------------------------------------
// 7. admin-set-profile — PATCH /api/admin/operators/{operatorId}/profile
//    (TASK-BE-307 producer / TASK-PC-FE-017 consumer). Admin-on-behalf-of:
//    SUPER_ADMIN sets ANOTHER operator's `operatorContext.defaultAccountId`
//    with `operator.manage` permission + explicit `X-Operator-Reason`. Self
//    via this path → producer `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`
//    (the UI gates the per-row button when the row is self — UX layer; this
//    api fn forwards whatever the producer returns).
//
//    HEADERS: `X-Operator-Reason` required (producer rejects empty); NO
//    `Idempotency-Key` per the producer matrix (mirror rows 13 + 14
//    `{id}/roles` + `{id}/status` non-uniformity — full-replace PATCH on the
//    column is idempotent; sending the key is a header-matrix-drift defect).
//    204 No Content on success. The value is the target operator's chosen
//    default finance-platform account UUID (opaque to GAP — TASK-BE-304
//    § Decision authority); `null` clears the column.
// ---------------------------------------------------------------------------

export async function setOperatorProfile(
  operatorId: string,
  defaultAccountId: string | null,
  reason: string,
): Promise<void> {
  await callGapOperators(
    {
      method: 'PATCH',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/profile`,
      reason,
      // NO idempotencyKey — per the producer header matrix (§ 2.4.3 row 7,
      // mirror /roles + /status). Header-matrix-drift defense.
      body: {
        operatorContext: { defaultAccountId },
      },
      expectNoContent: true,
    },
    () => undefined,
  );
}

// ---------------------------------------------------------------------------
// 8. getSelfOperatorIdOrNull — GET /api/admin/me
//    TASK-PC-FE-020 — server-side resolve the caller's own operatorId for the
//    `OperatorsScreen` self-row UX gate (PC-FE-017 honest gap (b) closure).
//    Fail-graceful: returns `null` on every observed failure mode (401, 403,
//    503/timeout/network, schema parse, unexpected). Producer
//    `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH` is the authoritative
//    gate; this is the UX layer that hides the disallowed button.
//
//    Read-only / no mutation headers; uses the same hardened `callGapOperators`
//    site (operator token + active tenant + structured error logging).
// ---------------------------------------------------------------------------

export async function getSelfOperatorIdOrNull(): Promise<string | null> {
  try {
    const me = await callGapOperators(
      { method: 'GET', path: '/api/admin/me' },
      (json) => OperatorSummarySchema.parse(json),
    );
    return me.operatorId;
  } catch {
    // Every failure mode (ApiError 401/403/etc, OperatorsUnavailableError,
    // network, schema parse, unexpected) → null. The page renders with the
    // gate inactive; the next mutation surfaces the real error (e.g. list
    // call's 401 → redirect-to-login). The producer 400 on self-via-admin
    // remains the authoritative fail-safe — never a security regression.
    return null;
  }
}
