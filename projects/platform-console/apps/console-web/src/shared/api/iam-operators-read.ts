import { getActiveTenant } from '@/shared/lib/session';
import { clampPageSize } from '@/shared/lib/pagination';
import { OperatorsUnavailableError } from './errors';
import { callAdminGateway, type AdminGatewayProfile } from './iam-gateway';
import {
  OperatorPageSchema,
  type OperatorPage,
  type OperatorListParams,
} from './iam-operators-types';

/**
 * Shared server-side IAM `admin-service` **operators READ** client —
 * `GET /api/admin/operators` (list) + its wire shapes, plus the hardened
 * {@link callGapOperators} core every operators call rides
 * (TASK-PC-FE-004/110 producer binding / TASK-PC-FE-259 promotion).
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * `listOperators` has **three** consuming features: `features/operators`
 * (the `/operators` management screen), `features/dashboards` (the § 2.4.4
 * composed operator overview — its third leg) and `features/iam-overview`
 * (the `/iam` landing snapshot). The latter two previously imported it
 * straight out of `@/features/operators/api/operators-api`, which
 * `architecture.md` § Forbidden Dependencies bars —
 *
 *   > 같은 계층 `features/A → features/B` 상호 참조 금지
 *   > (공유 가치는 `shared/` 로 승격).
 *
 * Same promotion pattern as `shared/api/rbac-catalog.ts`, whose header cites
 * the same rule for the same reason.
 *
 * ── ONLY THE READ MOVED (deliberate) ──
 * The MOST privilege-sensitive surface in the console — create / edit-roles /
 * change-status / password / profile / org-scope assignments, i.e. the
 * operator-privilege-escalation surface — has a single consuming feature and
 * stays in `features/operators/api/*`, where
 * `console-integration-contract.md` § 3.1 rows 12–15, 17–18 place it. Those
 * modules import {@link callGapOperators} + {@link OPERATORS_PREFIX} from here
 * so every operators call still goes through ONE hardened call site, and
 * `features/operators/api/operators-crud-api.ts` re-exports `listOperators`
 * so § 2.4.4 leg 3 (`features/operators` `listOperators`) stays literally true
 * and the 16-row parity attestation is unchanged.
 *
 * ── SERVER-ONLY (the wire shapes are NOT here) ──
 * This module reaches `shared/lib/session.ts` → `next/headers`, so it must
 * never be pulled into a client bundle. The pure-zod list shapes live in
 * `shared/api/iam-operators-types.ts`, which is what
 * `features/operators/api/types.ts` (imported by client components) re-exports.
 *
 * ── BEHAVIOUR (moved verbatim from `features/operators/api/*`) ──
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

// ---------------------------------------------------------------------------
// list — GET /api/admin/operators (status filter + pagination; READ)
//    No mutation headers (per the matrix). § 2.4.4 leg 3.
// ---------------------------------------------------------------------------

export async function listOperators(
  params: OperatorListParams = {},
): Promise<OperatorPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  // TASK-MONO-175: scope the operator list to the ACTIVE tenant so 운영자 관리
  // follows the tenant switcher (the producer scopes by the `tenantId` query
  // param — home ∪ assignment — and gates it against the caller's effective
  // scope; mirror of the audit `tenantId` pattern). The same active tenant is
  // also sent as `X-Tenant-Id` by `callGapOperators`; when none is selected
  // that call blocks with NO_ACTIVE_TENANT before any fetch.
  const tenant = await getActiveTenant();
  if (tenant) qs.set('tenantId', tenant);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampPageSize(params.size, 20, 100)));
  return callGapOperators(
    { method: 'GET', path: `${OPERATORS_PREFIX}?${qs.toString()}` },
    (json) => OperatorPageSchema.parse(json),
  );
}
