import { OperatorsUnavailableError } from '@/shared/api/errors';
import {
  callAdminGateway,
  type AdminGatewayProfile,
} from '@/shared/api/iam-gateway';

/**
 * Server-side IAM admin-service operators-management client — a thin wrapper
 * over the shared {@link callAdminGateway} core (TASK-PC-FE-208 dedup;
 * originally TASK-PC-FE-004/110 — ADR-MONO-013 Phase 2 slice 3, the MOST
 * privilege-sensitive slice: create/role/status = the
 * operator-privilege-escalation surface).
 *
 * Feature-internal: `callGapOperators` + `OPERATORS_PREFIX` are imported by the
 * sibling operators api modules (`operators-crud-api` / `operators-self-api` /
 * `operators-assignments-api`), NEVER re-exported through the `operators-api`
 * barrel (the public surface stays exactly the prior function set). 0 behavior
 * change.
 *
 * Auth invariant (console-integration-contract § 2.1/§ 2.4.3 — the #569 trust
 * boundary): every call authenticates with the EXCHANGED operator token
 * (`getOperatorToken()`), NEVER the IAM OIDC access token — the EXACT INVERSE of
 * the wms client (which requires the IAM OIDC token directly). Absent ⇒
 * `401 TOKEN_INVALID`, no fetch. The active tenant rides in `X-Tenant-Id`
 * (`getActiveTenant()`); absent ⇒ `400 NO_ACTIVE_TENANT`. `create` additionally
 * carries a `tenantId` body field; the producer enforces the `*` platform-scope.
 *
 * PER-ENDPOINT HEADER MATRIX (§ 2.4.3 — NOT uniform; the key correctness risk;
 * expressed by which fields each caller passes, applied by the shared core):
 *   - `GET  /operators`            → no mutation headers (read);
 *   - `POST /operators` (create)   → `X-Operator-Reason` + `Idempotency-Key`;
 *   - `PATCH .../{id}/roles`       → `X-Operator-Reason` ONLY (NO key);
 *   - `PATCH .../{id}/status`      → `X-Operator-Reason` ONLY (NO key);
 *   - `PATCH .../me/password`      → self path, valid token only (no reason / no
 *                                    key; `expectNoContent` — 204).
 * The reason-bearing mutations fail-safe on an empty reason BEFORE any fetch.
 *
 * Resilience (§ 2.5): AbortController hard timeout; 401 → `ApiError` (forced
 * re-login); 403/409/400/404 → `ApiError` (inline actionable — `forbiddenMode:
 * 'generic'`); 503/timeout → {@link OperatorsUnavailableError} (operators
 * section degrades only). SECURITY: `body` may carry a plaintext password
 * (create / change-password) — it is serialised into the request and is NEVER
 * logged (only the request id / path / status are).
 */

export const OPERATORS_PREFIX = '/api/admin/operators';

type HttpMethod = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

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
 * operators profile for the shared {@link callAdminGateway} core: the IAM
 * operators surface (`OPERATORS_TIMEOUT_MS`) that degrades via
 * {@link OperatorsUnavailableError} and logs `operators_*` events.
 * `forbiddenMode: 'generic'` (403 → inline `!ok`); reason/key applied per the
 * caller's supplied fields (the per-endpoint matrix).
 */
const OPERATORS_PROFILE: AdminGatewayProfile = {
  logPrefix: 'operators',
  requestFailedLabel: 'operators request failed',
  resolveTimeoutMs: (env) => env.OPERATORS_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new OperatorsUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof OperatorsUnavailableError,
  messages: {
    degraded: 'IAM operators service unavailable',
    timeout: 'IAM operators call timed out',
    network: 'IAM operators call failed',
  },
  forbiddenMode: 'generic',
  forceMutationHeaders: false,
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callAdminGateway} core with the {@link OPERATORS_PROFILE}. Passes the
 * method + per-endpoint reason/idempotency/body/expectNoContent through.
 */
export async function callGapOperators<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  return callAdminGateway(
    {
      method: opts.method,
      path: opts.path,
      reason: opts.reason,
      idempotencyKey: opts.idempotencyKey,
      body: opts.body,
      expectNoContent: opts.expectNoContent,
    },
    parse,
    OPERATORS_PROFILE,
  );
}
