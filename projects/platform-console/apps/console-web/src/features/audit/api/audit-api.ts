import { getActiveTenant } from '@/shared/lib/session';
import { ApiError, AuditUnavailableError } from '@/shared/api/errors';
import {
  callAdminGateway,
  type AdminGatewayProfile,
} from '@/shared/api/iam-gateway';
import {
  AuditPageSchema,
  type AuditPage,
  type AuditQueryParams,
  AUDIT_MAX_PAGE_SIZE,
  AUDIT_DEFAULT_PAGE_SIZE,
} from './types';

/**
 * Server-side IAM admin-service unified audit + security read client — a thin
 * wrapper over the shared {@link callAdminGateway} core (TASK-PC-FE-208 dedup;
 * originally TASK-PC-FE-003 — ADR-MONO-013 Phase 2 slice 2).
 *
 * Auth invariant (console-integration-contract § 2.1/§ 2.4.2 — the #569 trust
 * boundary): the call authenticates with the EXCHANGED operator token
 * (`getOperatorToken()`), NEVER the IAM OIDC access token. Absent ⇒
 * `401 TOKEN_INVALID`, no fetch (enforced by the shared core).
 *
 * Tenant invariant (§ 2.4): the active tenant is sent as `X-Tenant-Id`
 * (`getActiveTenant()`, by the shared core); absent ⇒ `400 NO_ACTIVE_TENANT`.
 * The `tenantId` *query* param — which is what `AuditQueryUseCase` actually
 * scopes by (admin-service does NOT read `X-Tenant-Id` for audit) — **defaults
 * to the active tenant** (TASK-PC-FE-043) so the 감사·보안 view follows the
 * tenant switcher. The producer enforces the dual-read effective-scope gate
 * (403 `TENANT_SCOPE_DENIED`). An explicit `params.tenantId` (SUPER_ADMIN
 * cross-tenant) overrides.
 *
 * READ-ONLY invariant (§ 2.4.2): NO `X-Operator-Reason`, NO `Idempotency-Key`
 * (`forceMutationHeaders: false`, no reason/key supplied). 403 gets its own
 * inline mapping (`forbiddenMode: 'dedicated'` — PERMISSION_DENIED /
 * TENANT_SCOPE_DENIED surface inline, no re-login loop). 503/timeout →
 * {@link AuditUnavailableError} (audit section degrades only).
 */

const AUDIT_PATH = '/api/admin/audit';

/**
 * audit profile for the shared {@link callAdminGateway} core: the IAM audit read
 * surface (`AUDIT_TIMEOUT_MS`) that degrades via {@link AuditUnavailableError}
 * and logs `audit_*` events. `forbiddenMode: 'dedicated'` reproduces audit's own
 * 403 block (code + producer message, no timestamp).
 */
const AUDIT_PROFILE: AdminGatewayProfile = {
  logPrefix: 'audit',
  requestFailedLabel: 'audit request failed',
  resolveTimeoutMs: (env) => env.AUDIT_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new AuditUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof AuditUnavailableError,
  messages: {
    degraded: 'IAM audit service unavailable',
    timeout: 'IAM audit call timed out',
    network: 'IAM audit call failed',
  },
  forbiddenMode: 'dedicated',
  forceMutationHeaders: false,
};

/**
 * Builds the audit query string, applying the client-side guards that
 * pre-empt the producer `422 VALIDATION_ERROR` (task Edge Case / AC):
 *   - `size` is hard-capped to AUDIT_MAX_PAGE_SIZE (≤ 100);
 *   - `from > to` is rejected here (no fetch) with `422 AUDIT_RANGE_INVALID`.
 * Only the explicitly-supplied filters are serialised; `tenantId` is set when
 * present (the caller — `queryAudit` — defaults it to the active tenant, so it
 * is normally present; absent only if no active tenant, which is blocked by the
 * shared core's `400 NO_ACTIVE_TENANT` before fetch).
 */
function buildQuery(params: AuditQueryParams): string {
  const from = params.from?.trim();
  const to = params.to?.trim();
  if (from && to && from > to) {
    // ISO-8601 strings compare lexicographically iff well-formed; the
    // producer is the final authority, this is the cheap client guard.
    throw new ApiError(
      422,
      'AUDIT_RANGE_INVALID',
      'from must not be after to',
    );
  }

  const qs = new URLSearchParams();
  if (params.accountId && params.accountId.trim() !== '') {
    qs.set('accountId', params.accountId.trim());
  }
  if (params.actionCode && params.actionCode.trim() !== '') {
    qs.set('actionCode', params.actionCode.trim());
  }
  if (from) qs.set('from', from);
  if (to) qs.set('to', to);
  if (params.source) qs.set('source', params.source);
  // SUPER_ADMIN explicit cross-tenant ONLY — never fabricated here.
  if (params.tenantId && params.tenantId.trim() !== '') {
    qs.set('tenantId', params.tenantId.trim());
  }
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  const size = Math.min(
    AUDIT_MAX_PAGE_SIZE,
    Math.max(1, params.size ?? AUDIT_DEFAULT_PAGE_SIZE),
  );
  qs.set('size', String(size));
  return qs.toString();
}

/**
 * `GET /api/admin/audit` — the single unified-view read. Resolves the active
 * tenant to default the query `tenantId` scope (TASK-PC-FE-043), applies the
 * client range/size guards (may throw `422` before any fetch), then delegates to
 * the shared {@link callAdminGateway} core (token/tenant/timeout/taxonomy).
 */
export async function queryAudit(
  params: AuditQueryParams = {},
): Promise<AuditPage> {
  // TASK-PC-FE-043: default the audit query SCOPE to the active tenant so the
  // 감사·보안 view follows the tenant switcher (the producer scopes by the
  // `tenantId` query param, NOT `X-Tenant-Id`, and gates it against the
  // operator's dual-read effective scope). An explicit `params.tenantId`
  // (SUPER_ADMIN cross-tenant) overrides. A missing active tenant leaves the
  // param unset; the shared core then blocks with 400 NO_ACTIVE_TENANT (no fetch).
  const tenant = await getActiveTenant();

  // Client guards (from ≤ to, size ≤ 100) — may throw 422 before any fetch.
  const query = buildQuery({ ...params, tenantId: params.tenantId ?? tenant ?? undefined });

  return callAdminGateway(
    { method: 'GET', path: `${AUDIT_PATH}?${query}` },
    (json) => AuditPageSchema.parse(json),
    AUDIT_PROFILE,
  );
}
