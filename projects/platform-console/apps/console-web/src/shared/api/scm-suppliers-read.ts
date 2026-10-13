import { clampPageSize } from '@/shared/lib/pagination';
import { ScmUnavailableError } from '@/shared/api/errors';
import { callScmGateway, type ScmGatewayProfile } from '@/shared/api/scm-gateway';
import {
  SupplierPageSchema,
  type SupplierPage,
  type SupplierQueryParams,
  SCM_SUPPLIER_DEFAULT_PAGE_SIZE,
  SCM_SUPPLIER_MAX_PAGE_SIZE,
} from './scm-suppliers-types';

/**
 * Server-side scm procurement **supplier master** list read (TASK-PC-FE-337).
 * `GET /api/v1/procurement/suppliers`.
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * Two feature sections consume the same supplier list — `features/scm-ops`
 * (the PO filter's `supplierId`, an id) and `features/scm-config`
 * (`SupplierMapForm`'s `supplierId`, a CODE). `features/A → features/B`
 * import is forbidden (architecture.md § Forbidden Dependencies); this read
 * is the `shared/` promotion (the same remedy `shared/api/iam-*-read.ts` /
 * `shared/api/rbac-catalog.ts` already applied).
 *
 * Built directly on the shared {@link callScmGateway} core (`shared/api/
 * scm-gateway.ts`) — NOT on `features/scm-ops/api/scm-client.ts` — because
 * `shared/` may not import `features/*` either (the same Forbidden-
 * Dependencies rule, the other direction). The scm-ops / scm-config / scm-
 * replenishment feature clients are themselves thin adapters over this same
 * shared core; this module is simply a fourth, cross-feature adapter over
 * it, so the credential / tenant / error-envelope / resilience narrative is
 * identical to every other scm client without re-deriving it:
 *
 * `logPrefix: 'scm'` — REUSES `scm-client.ts`'s own surface (procurement
 * supplier master is part of the SAME procurement-service already answering
 * PO reads under that surface) rather than minting a new `scm_suppliers`
 * one. `tests/unit/sample-coverage-ledger.test.ts` discovers every distinct
 * `logPrefix` literal and demands a `SURFACE_COVERAGE` ledger row (+ a
 * sample fixture) for each — reusing `'scm'` means this read rides the
 * EXISTING `flat:scm` row/fixture (`shared/sample/fixtures/scm.ts`'s
 * `suppliersFixture`, dispatched by the same `scmFixture()` that already
 * answers PO/nodes/snapshot/sku/staleness) instead of requiring a whole new
 * ledger surface for a read that is, semantically, the same producer family.
 *
 *   - **Credential**: the domain-facing IAM OIDC access token
 *     (`getDomainFacingToken()`), reused from § 2.4.5 — NEVER the exchanged
 *     IAM operator token.
 *   - **Tenant**: NO `X-Tenant-Id` — scm resolves the tenant from the JWT
 *     `tenant_id ∈ {scm,*}` claim producer-side.
 *   - **Error envelope**: scm's FLAT shape `{ code, message, details?,
 *     timestamp }` (parsed by the shared core; distinct from wms's nested
 *     shape).
 *   - **Resilience**: `401` → `ApiError` (whole-session re-login);
 *     `403`/`404`/`400`/`422` → `ApiError` (inline, no crash); `429` → ONE
 *     bounded `Retry-After` backoff then `ScmRateLimitedError` (no storm);
 *     `503`/timeout/network → {@link ScmUnavailableError} (ONLY the calling
 *     section degrades — the console shell + every other section stay
 *     intact).
 *   - **Read-only**: a pure GET — no `Idempotency-Key`, no
 *     `X-Operator-Reason`, no body, no supplier write (`POST /suppliers`
 *     registration is out of console scope here).
 *
 * Server-only by construction (reaches `shared/lib/session.ts` →
 * `next/headers`); imported exclusively from the `/api/scm/procurement/
 * suppliers` route handler. Client components never import this module —
 * they consume the same-origin proxy through each feature's own hook,
 * parsing with the client-safe `scm-suppliers-types.ts` (the split this
 * file's own module-doc warns about: mixing the two breaks `next build`
 * with "You're importing a component that needs next/headers").
 */

const SUPPLIERS_PROFILE: ScmGatewayProfile = {
  logPrefix: 'scm',
  requestFailedLabel: 'scm suppliers request failed',
  makeUnavailable: (reason, code, message) =>
    new ScmUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof ScmUnavailableError,
  messages: {
    degraded: 'scm gateway unavailable',
    timeout: 'scm gateway call timed out',
    network: 'scm gateway call failed',
  },
};

/**
 * `GET /api/v1/procurement/suppliers` — list suppliers (paginated,
 * tenant-scoped). procurement-api.md § GET suppliers: the envelope is
 * `{ data: { content, page, size, totalElements, totalPages }, meta }`,
 * the SAME shape as `GET /api/v1/procurement/po`.
 */
export async function listSuppliers(
  params: SupplierQueryParams = {},
): Promise<SupplierPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set(
    'size',
    String(
      clampPageSize(
        params.size,
        SCM_SUPPLIER_DEFAULT_PAGE_SIZE,
        SCM_SUPPLIER_MAX_PAGE_SIZE,
      ),
    ),
  );
  const { raw } = await callScmGateway(
    { path: `/api/v1/procurement/suppliers?${qs.toString()}` },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return SupplierPageSchema.parse(env.data);
    },
    SUPPLIERS_PROFILE,
  );
  return raw;
}
