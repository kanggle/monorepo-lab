import { OrgNodesUnavailableError } from '@/shared/api/errors';
import {
  callAdminGateway,
  type AdminGatewayProfile,
  type AdminGatewayRequest,
} from '@/shared/api/iam-gateway';

/**
 * Server-side IAM admin-service ORG-NODE client (TASK-PC-FE-237 / ADR-047) —
 * a thin wrapper over the shared {@link callAdminGateway} core, modelled on
 * `features/tenants/api/tenants-client.ts`. The `/api/admin/org-nodes` surface
 * (org-node CRUD + `/{id}/ceiling` + `/{id}/tenants` + `/{id}/admins`) is
 * gated by `org.manage`; every mutation carries an `X-Operator-Reason` header
 * (attached by the shared core from the caller's `reason`).
 *
 * Auth invariant (§ 2.1): the `/api/admin/**` credential is the EXCHANGED
 * operator token (`getOperatorToken()`), NEVER the IAM OIDC access token.
 * Absent ⇒ `401 TOKEN_INVALID`, no fetch. The active tenant always rides as
 * `X-Tenant-Id` (a SUPER_ADMIN selects `*`), consistent with every sibling IAM
 * admin client.
 *
 * Resilience (§ 2.5): 401 → `ApiError` (whole-session re-login); 403 →
 * `ApiError` (`forbiddenMode: 'generic'` — inline "not permitted", since even
 * the LIST read requires `org.manage`); 503/timeout → {@link
 * OrgNodesUnavailableError} (the org-hierarchy section degrades only; the
 * console shell + the tenant switcher's flat fallback stay intact);
 * 400/404/422 → `ApiError` (inline actionable — the differentiated 422 cycle /
 * depth / ceiling-not-subset / not-empty codes are surfaced verbatim).
 */

export const ORG_NODES_PREFIX = '/api/admin/org-nodes';

export interface OrgNodesCallOptions {
  method: AdminGatewayRequest['method'];
  path: string;
  /** Operator-entered audit reason → `X-Operator-Reason` (all mutations). */
  reason?: string;
  body?: unknown;
  /** `204`-returning mutations (delete node / revoke admin). */
  expectNoContent?: boolean;
}

/**
 * org-nodes profile for the shared {@link callAdminGateway} core: the ADR-047
 * hierarchy surface (`ORG_NODES_TIMEOUT_MS`) that degrades via
 * {@link OrgNodesUnavailableError} and logs `org_nodes_*` events.
 * `forbiddenMode: 'generic'` (403 → inline `!ok`, same as tenants/operators);
 * `forceMutationHeaders: false` (the caller supplies the required reason for
 * each mutation; the shared core's own reason-blank guard is the fail-safe).
 */
const ORG_NODES_PROFILE: AdminGatewayProfile = {
  logPrefix: 'org_nodes',
  requestFailedLabel: 'org-node request failed',
  resolveTimeoutMs: (env) => env.ORG_NODES_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new OrgNodesUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof OrgNodesUnavailableError,
  messages: {
    degraded: 'IAM org-node hierarchy service unavailable',
    timeout: 'IAM org-node hierarchy call timed out',
    network: 'IAM org-node hierarchy call failed',
  },
  forbiddenMode: 'generic',
  forceMutationHeaders: false,
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callAdminGateway} core with the {@link ORG_NODES_PROFILE}.
 */
export async function callOrgNodes<T>(
  opts: OrgNodesCallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  return callAdminGateway(
    {
      method: opts.method,
      path: opts.path,
      reason: opts.reason,
      body: opts.body,
      expectNoContent: opts.expectNoContent,
    },
    parse,
    ORG_NODES_PROFILE,
  );
}
