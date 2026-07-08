import { TenantsUnavailableError } from '@/shared/api/errors';
import {
  callAdminGateway,
  type AdminGatewayProfile,
} from '@/shared/api/iam-gateway';

/**
 * Server-side IAM admin-service TENANT-MANAGEMENT client — a thin wrapper over
 * the shared {@link callAdminGateway} core (TASK-PC-FE-226, admin-api.md
 * § "Tenant Lifecycle (TASK-BE-256)"). This is the isolation-boundary CRUD
 * surface (`TenantAdminController`) — SUPER_ADMIN (`tenant_id='*'`) only for
 * ALL four endpoints (list included). Functionally unrelated to
 * `features/tenant` (`TenantSwitcher`, the operator's own active-tenant
 * session selector) — do not conflate.
 *
 * Auth invariant (§ 2.1 trust boundary, mirrored from every other IAM admin
 * client in this codebase): the `/api/admin/**` credential is the EXCHANGED
 * operator token (`getOperatorToken()`), NEVER the IAM OIDC access token.
 * Absent ⇒ `401 TOKEN_INVALID`, no fetch.
 *
 * Tenant header: like every sibling IAM admin client (accounts/operators/
 * subscriptions/partnerships), the shared core always attaches the caller's
 * own active tenant as `X-Tenant-Id` and blocks with `400 NO_ACTIVE_TENANT`
 * when none is selected — even though `TenantAdminController`'s authority
 * check is driven entirely by the JWT `tenant_id='*'` claim (the header is a
 * consistency invariant across every IAM admin surface, not something this
 * particular producer endpoint consumes). A SUPER_ADMIN operator selects `*`
 * (the platform-scope sentinel, offered by the tenant switcher) as their
 * active tenant to satisfy this gate.
 *
 * Header matrix (contract § Tenant Lifecycle): create/update carry
 * `X-Operator-Reason` (the general `/api/admin/*` auth rule — percent-encoded
 * per TASK-MONO-176); `Idempotency-Key` on create is producer-RECOMMENDED
 * (not required) — passed through only when the caller supplies one (the
 * confirm dialog generates it once per confirmed create, mirroring the
 * operators `create` precedent). NO idempotency key on update (partial PATCH
 * is naturally idempotent). Reads (list/get) carry no mutation headers.
 *
 * Resilience (§ 2.5): AbortController hard timeout; 401 → `ApiError`
 * (whole-session re-login); 403 → `ApiError` (`forbiddenMode: 'generic'` —
 * inline "not permitted", the entire screen renders forbidden since even the
 * LIST read requires SUPER_ADMIN); 503/timeout → {@link
 * TenantsUnavailableError} (tenant-management section degrades only; the
 * console shell + every other IAM surface stay intact); 400/404/409 →
 * `ApiError` (inline actionable — 409 `TENANT_ALREADY_EXISTS` / 400
 * `TENANT_ID_RESERVED` / 404 `TENANT_NOT_FOUND`).
 */

export const TENANTS_PREFIX = '/api/admin/tenants';

type HttpMethod = 'GET' | 'POST' | 'PATCH';

export interface TenantsCallOptions {
  method: HttpMethod;
  path: string;
  /** Operator-entered audit reason → `X-Operator-Reason` (create/update). */
  reason?: string;
  /** create-only, producer-recommended (not required) dedupe key. */
  idempotencyKey?: string;
  body?: unknown;
}

/**
 * tenants profile for the shared {@link callAdminGateway} core: the IAM
 * tenant-management surface (`TENANTS_TIMEOUT_MS`) that degrades via
 * {@link TenantsUnavailableError} and logs `tenants_*` events.
 * `forbiddenMode: 'generic'` (403 → inline `!ok`, same as operators/
 * subscriptions/partnerships); reason/key applied per the caller's supplied
 * fields (no forced mutation headers — the caller always supplies the
 * required reason for create/update; the api layer is the fail-safe via
 * `callAdminGateway`'s own reason-blank guard).
 */
const TENANTS_PROFILE: AdminGatewayProfile = {
  logPrefix: 'tenants',
  requestFailedLabel: 'tenant management request failed',
  resolveTimeoutMs: (env) => env.TENANTS_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new TenantsUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof TenantsUnavailableError,
  messages: {
    degraded: 'IAM tenant management service unavailable',
    timeout: 'IAM tenant management call timed out',
    network: 'IAM tenant management call failed',
  },
  forbiddenMode: 'generic',
  forceMutationHeaders: false,
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callAdminGateway} core with the {@link TENANTS_PROFILE}.
 */
export async function callGapTenants<T>(
  opts: TenantsCallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  return callAdminGateway(
    {
      method: opts.method,
      path: opts.path,
      reason: opts.reason,
      idempotencyKey: opts.idempotencyKey,
      body: opts.body,
    },
    parse,
    TENANTS_PROFILE,
  );
}
