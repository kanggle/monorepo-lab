import { GroupsUnavailableError } from '@/shared/api/errors';
import {
  callAdminGateway,
  type AdminGatewayProfile,
  type AdminGatewayRequest,
} from '@/shared/api/iam-gateway';

/**
 * Server-side IAM admin-service OPERATOR-GROUP client (TASK-PC-FE-250 /
 * ADR-MONO-046) — a thin wrapper over the shared {@link callAdminGateway} core,
 * modelled on `features/org-hierarchy/api/org-nodes-client.ts`. The
 * `/api/admin/groups` surface (group CRUD + `/{id}/members` + `/{id}/grants`)
 * is gated by `group.manage` (reads too); every mutation carries an
 * `X-Operator-Reason` header, and the create/add-member/add-grants mutations
 * additionally carry an `Idempotency-Key` (both attached by the shared core
 * from the caller's supplied `reason` / `idempotencyKey`).
 *
 * Auth invariant (§ 2.1): the `/api/admin/**` credential is the EXCHANGED
 * operator token (`getOperatorToken()`), NEVER the IAM OIDC access token.
 * Absent ⇒ `401 TOKEN_INVALID`, no fetch. The active tenant always rides as
 * `X-Tenant-Id` (a SUPER_ADMIN selects `*`), consistent with every sibling IAM
 * admin client.
 *
 * Resilience (§ 2.5): 401 → `ApiError` (whole-session re-login); 403 →
 * `ApiError` (`forbiddenMode: 'generic'` — inline "not permitted", since even
 * the LIST read requires `group.manage`); 503/timeout → {@link
 * GroupsUnavailableError} (the 운영자 그룹 section degrades only; the console
 * shell + every other IAM surface stay intact); 400/404/409/422 → `ApiError`
 * (inline actionable — the differentiated name-conflict / tenant-mismatch /
 * no-escalation / role-forbidden codes are surfaced verbatim).
 */

export const GROUPS_PREFIX = '/api/admin/groups';

export interface GroupsCallOptions {
  method: AdminGatewayRequest['method'];
  path: string;
  /** Operator-entered audit reason → `X-Operator-Reason` (all mutations). */
  reason?: string;
  /** `Idempotency-Key` — required by the producer on create / add-member /
   *  add-grants; forwarded only when the caller supplies it. */
  idempotencyKey?: string;
  body?: unknown;
  /** `204`-returning mutations (delete group / remove member / revoke grant). */
  expectNoContent?: boolean;
}

/**
 * groups profile for the shared {@link callAdminGateway} core: the ADR-MONO-046
 * operator-group surface (`GROUPS_TIMEOUT_MS`) that degrades via
 * {@link GroupsUnavailableError} and logs `groups_*` events.
 * `forbiddenMode: 'generic'` (403 → inline `!ok`, same as tenants/org-nodes);
 * `forceMutationHeaders: false` (the caller supplies the required reason +
 * idempotency key per the per-endpoint matrix; the shared core's own
 * reason-blank guard is the fail-safe).
 */
const GROUPS_PROFILE: AdminGatewayProfile = {
  logPrefix: 'groups',
  requestFailedLabel: 'operator-group request failed',
  resolveTimeoutMs: (env) => env.GROUPS_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new GroupsUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof GroupsUnavailableError,
  messages: {
    degraded: 'IAM operator-group service unavailable',
    timeout: 'IAM operator-group call timed out',
    network: 'IAM operator-group call failed',
  },
  forbiddenMode: 'generic',
  forceMutationHeaders: false,
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callAdminGateway} core with the {@link GROUPS_PROFILE}.
 */
export async function callGapGroups<T>(
  opts: GroupsCallOptions,
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
    GROUPS_PROFILE,
  );
}
