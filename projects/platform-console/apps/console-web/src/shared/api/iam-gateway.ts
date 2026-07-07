import { getServerEnv } from '@/shared/config/env';
import { getOperatorToken, getActiveTenant } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError } from '@/shared/api/errors';

/**
 * Shared server-side **IAM `/api/admin/**` gateway HTTP core** (TASK-PC-FE-208 —
 * dedup of the per-feature IAM operator-token client scaffold). The IAM
 * operator/identity-plane feature clients —
 * `accounts/api/accounts-api.ts` (`callGapAdmin` + `exportAccount`),
 * `audit/api/audit-api.ts` (`queryAudit`),
 * `operators/api/operators-client.ts` (`callGapOperators`),
 * `partnerships/api/partnerships-client.ts` (`callPartnerships`) and
 * `subscriptions/api/subscriptions-client.ts` (`callSubscriptions`) — previously
 * each carried a near-verbatim copy of the SAME hardened call scaffold. This is
 * that scaffold, extracted ONCE; each feature client is now a thin wrapper that
 * supplies an {@link AdminGatewayProfile}.
 *
 * Server-only by construction: imported exclusively from server components and
 * the `runtime = 'nodejs'` route handlers; `getServerEnv()` throws outside the
 * server runtime. The operator token + any PII never reach client JS — client
 * components call the same-origin `/api/{accounts,audit,operators,...}/**` proxy
 * routes, which attach the HttpOnly operator token here server-side.
 *
 * ── INVARIANTS PRESERVED VERBATIM (pinned by
 *    `tests/unit/{accounts,audit,operators,partnerships,subscriptions}-*.test.ts`)
 *
 * - **Trust boundary** (console-integration-contract § 2.1/§ 2.4): the
 *   `/api/admin/**` credential is the EXCHANGED operator token
 *   (`getOperatorToken()`), NEVER the IAM OIDC access token. Absent ⇒
 *   `401 TOKEN_INVALID`, no fetch.
 * - **Tenant**: the active tenant is always sent as `X-Tenant-Id`
 *   (`getActiveTenant()`); absent ⇒ `400 NO_ACTIVE_TENANT` (never an empty
 *   header — no cross-tenant leak).
 * - **Per-endpoint header matrix** (§ 2.4.x — the key correctness risk): the
 *   reason (`X-Operator-Reason`, percent-encoded per TASK-MONO-176) and
 *   `Idempotency-Key` are applied per the caller's supplied fields, gated by the
 *   profile's {@link AdminGatewayProfile.forceMutationHeaders}. `Content-Type`
 *   only when a body is present.
 * - **Resilience** (§ 2.5): AbortController hard timeout; `401` → whole-session
 *   re-login `ApiError`; `403` per {@link AdminGatewayProfile.forbiddenMode};
 *   `503`/timeout/network → the profile's section-degrade error; other `!ok` →
 *   inline `ApiError` (FLAT `{code,message,timestamp}` envelope).
 *
 * Logging: structured, server-side only; the operator token + any PII are NEVER
 * logged. Event names carry the profile prefix (`accounts_*` / `operators_*` /
 * …). (The unified core logs `path` and a uniform `${prefix}_ok` on every leg —
 * a benign superset of what a couple of the pre-dedup clients logged; log event
 * names are not part of the behavior contract.)
 */

export type AdminUnavailableReason = 'timeout' | 'circuit_open' | 'downstream';

/**
 * How a `403` is mapped — the ONE resilience-taxonomy divergence across the IAM
 * clients:
 * - `'auth'` (accounts) — handled together with `401`: `code = errBody.code ??
 *   PERMISSION_DENIED`, message HARD-CODED `'not permitted'` (producer message
 *   ignored), no timestamp; logged as `${prefix}_unauthorized`.
 * - `'dedicated'` (audit) — its own block: `code = errBody.code ??
 *   PERMISSION_DENIED`, message `errBody.message ?? 'not permitted'`, no
 *   timestamp; logged as `${prefix}_forbidden`.
 * - `'generic'` (operators / partnerships / subscriptions) — falls through to
 *   the generic `!ok` path (`code ?? HTTP_403`, `message ?? '<label> (403)'`,
 *   `errBody.timestamp`).
 */
export type ForbiddenMode = 'auth' | 'dedicated' | 'generic';

/** An IAM admin gateway request (relative to `IAM_ADMIN_API_BASE`). */
export interface AdminGatewayRequest {
  method: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  path: string;
  /** Operator-entered audit reason → `X-Operator-Reason` (percent-encoded). */
  reason?: string;
  /** `Idempotency-Key` — only the callers the producer matrix allows supply it. */
  idempotencyKey?: string;
  /** Typed mutation body; `undefined` for reads (⇒ no `Content-Type`). */
  body?: unknown;
  /** `204`-returning mutations (e.g. participant-remove / self-password). */
  expectNoContent?: boolean;
  /** Per-call override of {@link AdminGatewayProfile.requestFailedLabel} — the
   *  `!ok` fallback message (accounts `export` uses `'export failed'`). */
  failLabel?: string;
}

/**
 * Per-feature behaviour the shared core is parameterised by — the ONLY thing
 * that differs between the IAM clients.
 */
export interface AdminGatewayProfile {
  /** Log-event prefix: `accounts` | `audit` | `operators` | `partnerships` | `subscriptions`. */
  logPrefix: string;
  /** `!ok` fallback message base → `'<label> (<status>)'` (e.g. `'accounts request failed'`). */
  requestFailedLabel: string;
  /** Resolves the per-feature timeout from the server env (accessed inside the
   *  server-only core, so the feature clients never touch `getServerEnv()`). */
  resolveTimeoutMs: (env: ReturnType<typeof getServerEnv>) => number;
  /** Build the section-degrade error (`503`/timeout/network) — the feature's
   *  `*UnavailableError` variant so ONLY that console section degrades. */
  makeUnavailable: (
    reason: AdminUnavailableReason,
    code: string,
    message: string,
  ) => Error;
  /** `instanceof` guard for the profile's degrade error (catch re-throw). */
  isUnavailable: (err: unknown) => boolean;
  /** Degrade / timeout / network `message` strings (preserved per feature). */
  messages: { degraded: string; timeout: string; network: string };
  /** 403 mapping — see {@link ForbiddenMode}. */
  forbiddenMode: ForbiddenMode;
  /** `true` (accounts): a non-GET REQUIRES a non-empty reason + idempotency key.
   *  `false` (others): reason/key are validated only when the caller supplies
   *  them (the per-endpoint matrix is expressed by which fields the caller passes). */
  forceMutationHeaders: boolean;
}

/**
 * The single hardened IAM admin gateway call site (extracted from the five
 * feature clients). Resolves the exchanged operator token + active tenant,
 * applies the per-endpoint header matrix + timeout, and maps the producer FLAT
 * error envelope to the § 2.5 resilience taxonomy. Returns `parse(json)` (or
 * `undefined` for a 204 / `expectNoContent`).
 */
export async function callAdminGateway<T>(
  req: AdminGatewayRequest,
  parse: (json: unknown) => T,
  profile: AdminGatewayProfile,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();
  const { logPrefix } = profile;

  // Trust boundary: the /api/admin/** credential is the EXCHANGED operator
  // token — never the IAM OIDC access token. Absent ⇒ 401, no fetch.
  const token = await getOperatorToken();
  if (!token) {
    logger.warn(`${logPrefix}_no_operator_session`, { requestId, path: req.path });
    throw new ApiError(401, 'TOKEN_INVALID', 'No operator session');
  }

  // Multi-tenant: always send the selected tenant; block (no empty header)
  // when none is selected — never a cross-tenant / unscoped call.
  const tenant = await getActiveTenant();
  if (!tenant) {
    logger.warn(`${logPrefix}_no_active_tenant`, { requestId, path: req.path });
    throw new ApiError(400, 'NO_ACTIVE_TENANT', 'No active tenant selected');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Tenant-Id': tenant,
    'X-Request-Id': requestId,
  };

  // Per-endpoint header matrix. `forceMutationHeaders` (accounts) makes a
  // non-GET REQUIRE reason + key; otherwise reason/key are validated only when
  // the caller supplies them (TASK-MONO-176: percent-encode the reason so a
  // non-Latin-1 value does not make `fetch()` throw on the ByteString header).
  if (profile.forceMutationHeaders && req.method !== 'GET') {
    const reason = req.reason?.trim() ?? '';
    if (reason === '') {
      logger.warn(`${logPrefix}_mutation_no_reason`, { requestId, path: req.path });
      throw new ApiError(
        400,
        'REASON_REQUIRED',
        'An operator reason is required for this action',
      );
    }
    if (!req.idempotencyKey) {
      throw new ApiError(
        400,
        'VALIDATION_ERROR',
        'An idempotency key is required for this action',
      );
    }
    headers['X-Operator-Reason'] = encodeURIComponent(reason);
    headers['Idempotency-Key'] = req.idempotencyKey;
  } else {
    if (req.reason !== undefined) {
      const reason = req.reason.trim();
      if (reason === '') {
        logger.warn(`${logPrefix}_mutation_no_reason`, { requestId, path: req.path });
        throw new ApiError(
          400,
          'REASON_REQUIRED',
          'An operator reason is required for this action',
        );
      }
      headers['X-Operator-Reason'] = encodeURIComponent(reason);
    }
    if (req.idempotencyKey !== undefined) {
      if (req.idempotencyKey.trim() === '') {
        throw new ApiError(
          400,
          'VALIDATION_ERROR',
          'An idempotency key is required for this action',
        );
      }
      headers['Idempotency-Key'] = req.idempotencyKey;
    }
  }

  if (req.body !== undefined) headers['Content-Type'] = 'application/json';

  const timeoutMs = profile.resolveTimeoutMs(env);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const res = await fetch(`${env.IAM_ADMIN_API_BASE}${req.path}`, {
      method: req.method,
      headers,
      body: req.body === undefined ? undefined : JSON.stringify(req.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const errBody = (await res.json().catch(() => ({}))) as { code?: string };
      logger.warn(`${logPrefix}_unauthorized`, {
        requestId,
        status: 401,
        code: errBody.code,
        path: req.path,
      });
      // No partial authed state — caller forces a clean re-login.
      throw new ApiError(401, errBody.code ?? 'TOKEN_INVALID', 'session expired');
    }

    // 403 — the ONE taxonomy divergence, mapped by `profile.forbiddenMode`.
    if (res.status === 403 && profile.forbiddenMode === 'auth') {
      // accounts: handled with 401 — code only, message hard-coded, no timestamp.
      const errBody = (await res.json().catch(() => ({}))) as { code?: string };
      logger.warn(`${logPrefix}_unauthorized`, {
        requestId,
        status: 403,
        code: errBody.code,
        path: req.path,
      });
      throw new ApiError(403, errBody.code ?? 'PERMISSION_DENIED', 'not permitted');
    }
    if (res.status === 403 && profile.forbiddenMode === 'dedicated') {
      // audit: its own block — code + producer message, no timestamp.
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
      };
      const code = errBody.code ?? 'PERMISSION_DENIED';
      logger.warn(`${logPrefix}_forbidden`, {
        requestId,
        status: 403,
        code,
        path: req.path,
      });
      throw new ApiError(403, code, errBody.message ?? 'not permitted');
    }
    // 'generic' → 403 falls through to the `!ok` path below.

    if (res.status === 503) {
      const errBody = (await res.json().catch(() => ({}))) as { code?: string };
      const code = errBody.code ?? 'DOWNSTREAM_ERROR';
      logger.warn(`${logPrefix}_degraded`, {
        requestId,
        status: 503,
        code,
        path: req.path,
      });
      // ONLY this feature's section degrades — shell + other sections intact.
      throw profile.makeUnavailable(
        code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        code,
        profile.messages.degraded,
      );
    }

    if (!res.ok) {
      // 400/403(generic)/404/409/422 → inline actionable (no crash, no re-login).
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
        timestamp?: string;
      };
      logger.warn(`${logPrefix}_request_error`, {
        requestId,
        status: res.status,
        code: errBody.code,
        path: req.path,
      });
      const label = req.failLabel ?? profile.requestFailedLabel;
      throw new ApiError(
        res.status,
        errBody.code ?? `HTTP_${res.status}`,
        errBody.message ?? `${label} (${res.status})`,
        errBody.timestamp,
      );
    }

    logger.info(`${logPrefix}_ok`, {
      requestId,
      status: res.status,
      path: req.path,
    });
    if (req.expectNoContent || res.status === 204) {
      return undefined as T;
    }
    return parse(await res.json());
  } catch (err) {
    if (err instanceof ApiError || profile.isUnavailable(err)) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn(`${logPrefix}_timeout`, { requestId, timeoutMs, path: req.path });
      throw profile.makeUnavailable('timeout', 'TIMEOUT', profile.messages.timeout);
    }
    logger.error(`${logPrefix}_error`, { requestId, path: req.path });
    throw profile.makeUnavailable(
      'downstream',
      'NETWORK_ERROR',
      profile.messages.network,
    );
  } finally {
    clearTimeout(timer);
  }
}
