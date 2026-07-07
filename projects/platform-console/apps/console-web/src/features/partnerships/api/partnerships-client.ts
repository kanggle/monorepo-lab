import { PartnershipsUnavailableError } from '@/shared/api/errors';
import {
  callAdminGateway,
  type AdminGatewayProfile,
} from '@/shared/api/iam-gateway';

/**
 * Server-side IAM admin-service cross-org partnership client — a thin wrapper
 * over the shared {@link callAdminGateway} core (TASK-PC-FE-208 dedup;
 * originally TASK-PC-FE-187 / ADR-MONO-045 §3.4, admin-api.md § Partnership
 * Management, BE-476/477/478).
 *
 * Auth invariant (§ 2.1 trust boundary): the `/api/admin/**` credential is the
 * EXCHANGED operator token (`getOperatorToken()`), NEVER the IAM OIDC access
 * token. Absent ⇒ `401 TOKEN_INVALID`, no fetch. A tenant owner
 * (`TENANT_ADMIN`) holds this operator token with `partnership.manage`. The
 * actor's active tenant is always sent as `X-Tenant-Id` (`getActiveTenant()`);
 * absent ⇒ `400 NO_ACTIVE_TENANT`. The producer's D2 TenantScopeGuard confines
 * every op to the acting-side (host/partner) tenant.
 *
 * PER-ENDPOINT HEADER MATRIX (NOT uniform — the key correctness risk; expressed
 * by which fields each caller passes, applied by the shared core):
 *   - `GET  /partnerships`               → no mutation headers (read);
 *   - `POST /partnerships` (invite)      → `X-Operator-Reason` + `Idempotency-Key`;
 *   - `POST {id}:accept|:suspend|:reactivate|:terminate`
 *                                        → `X-Operator-Reason` ONLY (NO key);
 *   - `POST {id}/participants/{opId}`    → `X-Operator-Reason` ONLY (optional body);
 *   - `DELETE {id}/participants/{opId}`  → `X-Operator-Reason` ONLY, 204 no content.
 *
 * Resilience (§ 2.5): 401 → `ApiError` (re-login); 403/404/409/400/422 →
 * `ApiError` (inline actionable — `forbiddenMode: 'generic'`); 503/timeout →
 * {@link PartnershipsUnavailableError} (partnership surface degrades only).
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

/**
 * partnerships profile for the shared {@link callAdminGateway} core: the IAM
 * partnership surface (`PARTNERSHIPS_TIMEOUT_MS`) that degrades via
 * {@link PartnershipsUnavailableError} and logs `partnerships_*` events.
 * `forbiddenMode: 'generic'` (403 → inline `!ok`); reason/key applied per the
 * caller's supplied fields (the per-endpoint matrix).
 */
const PARTNERSHIPS_PROFILE: AdminGatewayProfile = {
  logPrefix: 'partnerships',
  requestFailedLabel: 'partnership request failed',
  resolveTimeoutMs: (env) => env.PARTNERSHIPS_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new PartnershipsUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof PartnershipsUnavailableError,
  messages: {
    degraded: 'IAM partnership service unavailable',
    timeout: 'IAM partnership call timed out',
    network: 'IAM partnership call failed',
  },
  forbiddenMode: 'generic',
  forceMutationHeaders: false,
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callAdminGateway} core with the {@link PARTNERSHIPS_PROFILE}. Passes the
 * method + per-endpoint reason/idempotency/body/expectNoContent through.
 */
export async function callPartnerships<T>(
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
    PARTNERSHIPS_PROFILE,
  );
}
