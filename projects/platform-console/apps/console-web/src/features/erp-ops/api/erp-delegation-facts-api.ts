import { clampPageSize } from '@/shared/lib/pagination';
import {
  DelegationFactListResponseSchema,
  type DelegationFactListResponse,
  DelegationFactDetailResponseSchema,
  type DelegationFactDetailResponse,
  type DelegationFactListQueryParams,
  ERP_DEFAULT_PAGE_SIZE,
  ERP_MAX_PAGE_SIZE,
} from './types';
import { callErp } from './erp-client';

// ---------------------------------------------------------------------------
// 8. read-model — delegation facts (TASK-PC-FE-055 — ADR-MONO-013 §D3.1)
//   GET /api/erp/read-model/delegations  (?delegatorId=&delegateId=&status=&activeAt=&page=&size=)
//   GET /api/erp/read-model/delegations/{grantId}
//
//   READ-ONLY (E5) — the read-model holds no domain logic. There is NO
//   mutation function here and no mutation call for this surface. The
//   credential is UNCHANGED (same `getDomainFacingToken()` / IAM OIDC path
//   as all other erp reads; NEVER `getOperatorToken()`). NO `X-Tenant-Id`
//   (erp resolves tenant from JWT claim). NO `X-Operator-Reason` (read-only).
// ---------------------------------------------------------------------------

function delegationListQs(params: DelegationFactListQueryParams): string {
  const qs = new URLSearchParams();
  if (params.delegatorId) qs.set('delegatorId', params.delegatorId);
  if (params.delegateId) qs.set('delegateId', params.delegateId);
  if (params.status) qs.set('status', params.status);
  if (params.activeAt) qs.set('activeAt', params.activeAt);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set(
    'size',
    String(clampPageSize(params.size, ERP_DEFAULT_PAGE_SIZE, ERP_MAX_PAGE_SIZE)),
  );
  return qs.toString();
}

/**
 * Paginated delegation-fact list from `read-model-service`.
 * Org_scope-aware (E6): operator's org_scope constrains the delegator's
 * department subtree; `["*"]`/unset = all (net-zero).
 * READ-ONLY — no write function exists or will exist for this surface (E5).
 */
export async function listDelegationFacts(
  params: DelegationFactListQueryParams = {},
): Promise<DelegationFactListResponse> {
  return callErp(
    {
      path: `/api/erp/read-model/delegations?${delegationListQs(params)}`,
      logPath: '/api/erp/read-model/delegations',
    },
    (json) => DelegationFactListResponseSchema.parse(json),
  );
}

/**
 * Single delegation fact from `read-model-service` by grant id.
 * The latest state only — authoritative grant audit history lives on
 * `approval-service`. READ-ONLY — same credential + `callErp` path as
 * the list.
 */
export async function getDelegationFact(
  grantId: string,
): Promise<DelegationFactDetailResponse> {
  return callErp(
    {
      path: `/api/erp/read-model/delegations/${encodeURIComponent(grantId)}`,
      logPath: '/api/erp/read-model/delegations/{grantId}',
    },
    (json) => DelegationFactDetailResponseSchema.parse(json),
  );
}
