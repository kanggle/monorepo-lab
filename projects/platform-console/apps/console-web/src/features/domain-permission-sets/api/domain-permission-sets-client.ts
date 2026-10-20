import { DomainPermissionSetsUnavailableError } from '@/shared/api/errors';
import {
  callAdminGateway,
  type AdminGatewayProfile,
  type AdminGatewayRequest,
} from '@/shared/api/iam-gateway';

/**
 * Server-side IAM admin-service DOMAIN-PERMISSION-SET client (TASK-PC-FE-338 /
 * ADR-MONO-083 D9) — a thin wrapper over the shared {@link callAdminGateway}
 * core, modelled on `features/operator-groups/api/operator-groups-client.ts`.
 * The `/api/admin/domain-roles` + `/api/admin/domain-permission-sets` surface
 * is gated by `domain_access.manage` (reads too); create carries
 * `X-Operator-Reason` + `Idempotency-Key`, edit/delete carry
 * `X-Operator-Reason` only (`Idempotency-Key` MUST NOT be sent — admin-api.md
 * § 도메인 업무 권한 세트 row 3-5).
 *
 * Auth invariant (§ 2.1): the `/api/admin/**` credential is the EXCHANGED
 * operator token (`getOperatorToken()`), NEVER the IAM OIDC access token.
 * Absent ⇒ `401 TOKEN_INVALID`, no fetch. The active tenant always rides as
 * `X-Tenant-Id` (a `'*'` request would see presets only, per the producer —
 * but the switcher never offers `*`, TASK-PC-FE-353).
 *
 * Resilience (§ 2.5): 401 → `ApiError` (whole-session re-login); 403 →
 * `ApiError` (`forbiddenMode: 'generic'` — inline "not permitted", since even
 * the LIST reads require `domain_access.manage`); 503/timeout →
 * {@link DomainPermissionSetsUnavailableError} (ONLY the 업무 권한 세트 section
 * degrades; the console shell + every other IAM surface stay intact);
 * 400/404/409/422 → `ApiError` (inline actionable — the differentiated
 * read-only / in-use / role-not-assignable / domain-not-subscribed codes are
 * surfaced verbatim).
 */

export const DOMAIN_ROLES_PATH = '/api/admin/domain-roles';
export const DOMAIN_PERMISSION_SETS_PREFIX = '/api/admin/domain-permission-sets';

export interface DomainPermissionSetsCallOptions {
  method: AdminGatewayRequest['method'];
  path: string;
  /** Operator-entered audit reason → `X-Operator-Reason` (every mutation). */
  reason?: string;
  /** `Idempotency-Key` — producer-required on CREATE only; PATCH/DELETE MUST
   *  NOT send it (forwarded only when the caller supplies it). */
  idempotencyKey?: string;
  body?: unknown;
  /** DELETE (204 no content). */
  expectNoContent?: boolean;
}

/**
 * Profile for the shared {@link callAdminGateway} core: the ADR-MONO-083
 * domain-permission-set surface (`DOMAIN_PERMISSION_SETS_TIMEOUT_MS`) that
 * degrades via {@link DomainPermissionSetsUnavailableError} and logs
 * `domain_permission_sets_*` events. `forbiddenMode: 'generic'` (403 → inline
 * `!ok`, same as groups/tenants/org-nodes); `forceMutationHeaders: false` (the
 * caller supplies the required reason + idempotency key per the per-endpoint
 * matrix; the shared core's own reason-blank guard is the fail-safe).
 */
const DOMAIN_PERMISSION_SETS_PROFILE: AdminGatewayProfile = {
  logPrefix: 'domain_permission_sets',
  requestFailedLabel: 'domain permission set request failed',
  resolveTimeoutMs: (env) => env.DOMAIN_PERMISSION_SETS_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new DomainPermissionSetsUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof DomainPermissionSetsUnavailableError,
  messages: {
    degraded: 'IAM domain permission set service unavailable',
    timeout: 'IAM domain permission set call timed out',
    network: 'IAM domain permission set call failed',
  },
  forbiddenMode: 'generic',
  forceMutationHeaders: false,
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callAdminGateway} core with the {@link DOMAIN_PERMISSION_SETS_PROFILE}.
 */
export async function callDomainPermissionSets<T>(
  opts: DomainPermissionSetsCallOptions,
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
    DOMAIN_PERMISSION_SETS_PROFILE,
  );
}
