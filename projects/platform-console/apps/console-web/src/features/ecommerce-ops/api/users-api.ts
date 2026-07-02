import { getServerEnv } from '@/shared/config/env';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  callEcommerce,
  type EcommerceCallLabel,
} from './ecommerce-client';
import {
  UserListSchema,
  type UserList,
  UserDetailSchema,
  type UserDetail,
  UserAreaSummarySchema,
  type UserAreaSummary,
  type UserListParams,
  USER_DEFAULT_PAGE_SIZE,
  USER_MAX_PAGE_SIZE,
} from './user-types';

/**
 * Server-side ecommerce `user-service` READ-ONLY operations client
 * (TASK-PC-FE-084 — the users facet of ADR-MONO-031 Phase 2b). Drives the
 * in-console user list and detail screens.
 *
 * Server-only by construction (same posture as `orders-api.ts`): imported
 * exclusively from server components and the `runtime = 'nodejs'` route
 * handlers; `getServerEnv()` throws outside the server runtime. The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/ecommerce/users/**` proxy routes, which attach the HttpOnly
 * credential here server-side.
 *
 * ── THE AUTH MODEL (same as orders-api.ts — § 2.4.10) ─────────────────────
 *
 * Per ADR-MONO-017 D2.A this surface is console-web → ecommerce gateway
 * DIRECT (no console-bff write leg). The ecommerce gateway requires
 * `account_type=OPERATOR` on the IAM OIDC token (BE-367). Therefore this
 * client uses `getDomainFacingToken()` (the assumed tenant-scoped IAM OIDC
 * token when the operator switched to a customer, else the base access token
 * — net-zero; ADR-MONO-020 D4) and NEVER `getOperatorToken()`. A test pins
 * that `getOperatorToken` is never called.
 *
 * Tenant invariant (§ 2.4.10): ecommerce resolves the tenant from the JWT
 * `tenant_id ∈ {ecommerce,*}` claim — the console does NOT send `X-Tenant-Id`.
 *
 * READ-ONLY discipline: this surface has NO mutations. There are NO POST/PATCH/
 * DELETE endpoints, NO Idempotency-Key, NO state machine transitions.
 *
 * Error envelope (§ 2.4.10 / § 2.5): ecommerce uses the FLAT shape
 * `{ code, message, timestamp }`. `parseUserError()` reads the flat shape and
 * tolerates an absent / non-JSON body without crashing.
 *
 * Resilience (§ 2.5):
 *   - `401` → `ApiError(401)` (whole-session re-login).
 *   - `403` → `ApiError(403)` (inline "not available to your role").
 *   - `404` USER_PROFILE_NOT_FOUND → `ApiError(404)` (inline not-found).
 *   - `503`/timeout/network → `EcommerceUnavailableError` (section degrades).
 */

/** Per-slice observability + message label for the (read-only) user surface. */
const USER_LABEL: EcommerceCallLabel = {
  event: 'user',
  errorNoun: 'user',
  unavailableLabel: 'user-service',
  timedOutLabel: 'user-service',
  failedLabel: 'user-service',
};

const clampSize = (size?: number): number =>
  clampPageSize(size, USER_DEFAULT_PAGE_SIZE, USER_MAX_PAGE_SIZE);

// ===========================================================================
// READS
// ===========================================================================

/** GET /admin/users/summary — period-based counts (TASK-PC-FE-160).
 *  Returns { today, week, month, total } for the tenant. */
export function getUsersSummary(): Promise<UserAreaSummary> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: '/users/summary',
    },
    (j) => UserAreaSummarySchema.parse(j),
    USER_LABEL,
  );
}

/** GET /admin/users?status&email&page&size (paginated user summaries). */
export function listUsers(params: UserListParams = {}): Promise<UserList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.email) qs.set('email', params.email);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/users?${qs.toString()}`,
    },
    (j) => UserListSchema.parse(j),
    USER_LABEL,
  );
}

/** GET /admin/users/{userId} (user detail). */
export function getUser(userId: string): Promise<UserDetail> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/users/${encodeURIComponent(userId)}`,
    },
    (j) => UserDetailSchema.parse(j),
    USER_LABEL,
  );
}
