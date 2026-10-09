import { TenantsUnavailableError } from '@/shared/api/errors';
import {
  callAdminGateway,
  type AdminGatewayProfile,
  type AdminGatewayRequest,
} from '@/shared/api/iam-gateway';

/**
 * Server-side IAM admin-service client for the tenant ENTRY POLICY
 * (TASK-MONO-771 S5, admin-api.md § Tenant Entry Policy) — a thin wrapper over
 * the shared {@link callAdminGateway} core, like every sibling IAM client.
 *
 * Auth invariant (§ 2.1): the `/api/admin/**` credential is the EXCHANGED
 * operator token, never the IAM OIDC access token. The active tenant always
 * rides as `X-Tenant-Id`; the TARGET tenant is the path `tenantId` (the
 * producer confines by path — a TENANT_ADMIN only for its grant's tenant).
 *
 * Header matrix (§ 2.4.3.3): GET carries no mutation header; PUT carries
 * `X-Operator-Reason` (required — supplied by the caller) and **never** an
 * `Idempotency-Key` (idempotent full-replace PUT).
 *
 * Resilience (§ 2.5): 401 → `ApiError` (whole-session re-login); 403 →
 * `ApiError` (`forbiddenMode: 'generic'` → inline «권한 없음»); 503 / timeout →
 * {@link TenantsUnavailableError} — only THIS control degrades (the tenant
 * detail page and the shell stay intact).
 */

export const ENTRY_POLICY_PATH = (tenantId: string) =>
  `/api/admin/tenants/${encodeURIComponent(tenantId)}/entry-policy`;

const ENTRY_POLICY_PROFILE: AdminGatewayProfile = {
  logPrefix: 'tenant_entry_policy',
  requestFailedLabel: 'tenant entry-policy request failed',
  resolveTimeoutMs: (env) => env.TENANTS_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new TenantsUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof TenantsUnavailableError,
  messages: {
    degraded: 'IAM tenant entry-policy service unavailable',
    timeout: 'IAM tenant entry-policy call timed out',
    network: 'IAM tenant entry-policy call failed',
  },
  forbiddenMode: 'generic',
  forceMutationHeaders: false,
};

export interface EntryPolicyCallOptions {
  method: Extract<AdminGatewayRequest['method'], 'GET' | 'PUT'>;
  path: string;
  /** PUT only — the operator-typed reason → `X-Operator-Reason`. */
  reason?: string;
  body?: unknown;
}

export async function callEntryPolicy<T>(
  opts: EntryPolicyCallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  return callAdminGateway(
    { method: opts.method, path: opts.path, reason: opts.reason, body: opts.body },
    parse,
    ENTRY_POLICY_PROFILE,
  );
}
