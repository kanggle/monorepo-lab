import { z } from 'zod';
import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError, RbacUnavailableError } from './errors';
import { callAdminGateway, type AdminGatewayProfile } from './iam-gateway';

/**
 * Shared server-side IAM **RBAC catalog** client (TASK-BE-486 producer /
 * TASK-PC-FE-227 「권한」 + TASK-PC-FE-228 「권한 세트」 consumers).
 *
 * `GET /api/admin/roles` (role → permission-key set) and
 * `GET /api/admin/permissions` (canonical permission-key catalog) are the
 * SAME two read-only endpoints consumed by BOTH features — TASK-PC-FE-228 is
 * an explicit reframe of the role catalog as "permission sets"
 * (`permission_set_id` physically = `admin_roles.id`; the producer
 * deliberately did NOT add a separate `GET /api/admin/permission-sets` view
 * — `admin-api.md`). Per `architecture.md` § Forbidden Dependencies
 * ("features/A → features/B 상호 참조 금지 — 공유 가치는 shared/ 로 승격"), the
 * client lives HERE (not inside either feature) and both
 * `features/permissions` and `features/permission-sets` consume it — the
 * SAME promotion pattern as `shared/api/iam-gateway.ts`'s `callAdminGateway`
 * core, one level more concrete (this file owns the two RBAC-catalog
 * ENDPOINTS + their SSR resilience state, not just the HTTP scaffold).
 *
 * Auth invariant (console-integration-contract § 2.1/§ 2.4.3 — the #569 trust
 * boundary): the EXCHANGED operator token (`getOperatorToken()`), NEVER the
 * IAM OIDC access token — enforced by the shared `callAdminGateway` core.
 * Tenant: the active tenant always rides in `X-Tenant-Id` (the shared core),
 * even though the catalog itself is `scope: "global"` (tenant-independent
 * data) — `admin-api.md` documents no tenant-scope exemption for this
 * surface, so the standard multi-tenant header discipline applies verbatim
 * (no empty header ever leaves; a "select a tenant" gate applies here too).
 *
 * Gate: `operator.manage` (BE-486 decision — no new `role.read`/
 * `permission.read` key; reuses the same permission as the sibling
 * `GET /api/admin/operators/grantable-roles`).
 *
 * READ-ONLY (no mutation surface at all — v1 is seed-only, same constraint
 * as `GET /api/admin/operators/grantable-roles`): NO `X-Operator-Reason`, NO
 * `Idempotency-Key` (`forceMutationHeaders: false`, neither field ever
 * supplied). `forbiddenMode: 'generic'` (403 falls through to the generic
 * `!ok` inline path — mirrors `operators`, NOT the dedicated `audit` 403
 * block). 503/timeout → {@link RbacUnavailableError} (the 「권한」/「권한 세트」
 * sections degrade only — the console shell + every other IAM section stay
 * intact).
 */

const ROLES_PATH = '/api/admin/roles';
const PERMISSIONS_PATH = '/api/admin/permissions';

// --- wire schemas -----------------------------------------------------------

/**
 * One `admin_roles` row + its permission-key set (`admin-api.md` §
 * `GET /api/admin/roles`). `permissions` members are `z.string()` (NOT a
 * closed enum) — an unknown/future permission key must never crash the
 * parse (tolerant forward-compat, the same posture `operators` uses for
 * role members).
 */
export const RoleSchema = z.object({
  id: z.number().int(),
  name: z.string(),
  description: z.string(),
  permissions: z.array(z.string()),
});
export type Role = z.infer<typeof RoleSchema>;

/**
 * `GET /api/admin/roles` envelope. `scope` is parsed as `z.string()` (NOT a
 * closed `z.literal('global')`) — the producer documents it as ALWAYS
 * `"global"` today, but a closed literal would hard-crash the parse on any
 * future producer value instead of letting the UI render it defensively
 * (the task's "scope 오인 방지" requirement is a DISPLAY obligation — reflect
 * whatever the producer sends — not a parse-time assumption).
 */
export const RolesResponseSchema = z.object({
  scope: z.string(),
  roles: z.array(RoleSchema),
});
export type RolesResponse = z.infer<typeof RolesResponseSchema>;

/**
 * `GET /api/admin/permissions` envelope — the canonical permission-key
 * catalog (`rbac.md` § Permission Keys canonical order; includes keys not
 * yet granted to any role — producer Edge Case).
 */
export const PermissionsResponseSchema = z.object({
  scope: z.string(),
  permissions: z.array(z.string()),
});
export type PermissionsResponse = z.infer<typeof PermissionsResponseSchema>;

// --- gateway profile ---------------------------------------------------------

/**
 * rbac-catalog profile for the shared {@link callAdminGateway} core: the IAM
 * RBAC-catalog read surface (`RBAC_TIMEOUT_MS`) that degrades via
 * {@link RbacUnavailableError} and logs `rbac_*` events. `forbiddenMode:
 * 'generic'` (403 → inline `!ok`, mirrors `operators`); no mutation headers
 * are ever applied (read-only).
 */
const RBAC_PROFILE: AdminGatewayProfile = {
  logPrefix: 'rbac',
  requestFailedLabel: 'rbac catalog request failed',
  resolveTimeoutMs: (env) => env.RBAC_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new RbacUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof RbacUnavailableError,
  messages: {
    degraded: 'IAM 권한 카탈로그 서비스를 일시적으로 사용할 수 없습니다.',
    timeout: 'IAM 권한 카탈로그 호출이 시간 초과되었습니다.',
    network: 'IAM 권한 카탈로그 호출이 실패했습니다.',
  },
  forbiddenMode: 'generic',
  forceMutationHeaders: false,
};

// --- reads -------------------------------------------------------------------

/** `GET /api/admin/roles` — role catalog + each role's permission-key set. */
export async function getRoleCatalog(): Promise<RolesResponse> {
  return callAdminGateway(
    { method: 'GET', path: ROLES_PATH },
    (json) => RolesResponseSchema.parse(json),
    RBAC_PROFILE,
  );
}

/** `GET /api/admin/permissions` — canonical permission-key catalog. */
export async function getPermissionCatalog(): Promise<PermissionsResponse> {
  return callAdminGateway(
    { method: 'GET', path: PERMISSIONS_PATH },
    (json) => PermissionsResponseSchema.parse(json),
    RBAC_PROFILE,
  );
}

// --- SSR resilience state ----------------------------------------------------

/**
 * Server-side RBAC-catalog state shared by BOTH `(console)/permissions` and
 * `(console)/permission-sets` (TASK-PC-FE-227/228). Mirrors
 * `features/operators/api/operators-state.ts` `getOperatorsListState()` /
 * `features/audit/api/audit-state.ts` `getAuditListState()`:
 *   - 401 → `redirect('/login')` (no partial authed state).
 *   - `NO_ACTIVE_TENANT` → a distinct "select a tenant" UI state (the
 *     catalog itself is global data, but the shared gateway core still
 *     requires an active-tenant header — no empty `X-Tenant-Id` ever
 *     leaves).
 *   - 403 `PERMISSION_DENIED` (lacks `operator.manage`) → an inline "not
 *     permitted" state, never a crash / re-login loop.
 *   - 503 / timeout / network → DEGRADED (the 「권한」/「권한 세트」 section
 *     degrades; the console shell + every other IAM section stay intact).
 *
 * Fetches BOTH endpoints (roles + permission catalog) concurrently — a
 * single read-only page load, no polling.
 */
export interface RbacCatalogState {
  roles: Role[] | null;
  permissions: string[] | null;
  /** Producer `scope` value (both endpoints document `"global"`) — echoed
   *  verbatim, not assumed, so the UI never mislabels a future tenant-scoped
   *  producer value as global (task Implementation Notes). `null` only when
   *  the data itself is `null` (gated/degraded states). */
  scope: string | null;
  degraded: boolean;
  /** True when no tenant is selected — render the "select a tenant" gate. */
  noTenant: boolean;
  /** Set when the producer returned 403 — inline "not permitted", no crash,
   *  no re-login loop. Carries the producer code for an actionable copy. */
  permissionError: { code: string; message: string } | null;
}

export async function getRbacCatalogState(): Promise<RbacCatalogState> {
  // Pre-flight tenant gate (no empty header ever leaves).
  const tenant = await getActiveTenant();
  if (!tenant) {
    return {
      roles: null,
      permissions: null,
      scope: null,
      degraded: false,
      noTenant: true,
      permissionError: null,
    };
  }

  try {
    const [rolesResp, permissionsResp] = await Promise.all([
      getRoleCatalog(),
      getPermissionCatalog(),
    ]);
    return {
      roles: rolesResp.roles,
      permissions: permissionsResp.permissions,
      scope: rolesResp.scope,
      degraded: false,
      noTenant: false,
      permissionError: null,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      // No partial authed state → clean re-login.
      redirect('/login');
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return {
        roles: null,
        permissions: null,
        scope: null,
        degraded: false,
        noTenant: true,
        permissionError: null,
      };
    }
    if (err instanceof ApiError && err.status === 403) {
      // 403 PERMISSION_DENIED (lacks operator.manage) → inline "not
      // permitted" (no crash, no re-login loop).
      return {
        roles: null,
        permissions: null,
        scope: null,
        degraded: false,
        noTenant: false,
        permissionError: { code: err.code, message: err.message },
      };
    }
    if (err instanceof RbacUnavailableError) {
      // Degrade ONLY this section — shell intact.
      return {
        roles: null,
        permissions: null,
        scope: null,
        degraded: true,
        noTenant: false,
        permissionError: null,
      };
    }
    // A genuine unexpected producer error → degrade rather than crash.
    return {
      roles: null,
      permissions: null,
      scope: null,
      degraded: true,
      noTenant: false,
      permissionError: null,
    };
  }
}
