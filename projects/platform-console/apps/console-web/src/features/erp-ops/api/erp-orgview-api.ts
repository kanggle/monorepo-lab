import { clampPageSize } from '@/shared/lib/pagination';
import {
  EmployeeOrgViewListResponseSchema,
  type EmployeeOrgViewListResponse,
  EmployeeOrgViewDetailResponseSchema,
  type EmployeeOrgViewDetailResponse,
  type ErpDetailQueryParams,
  type OrgViewListQueryParams,
  ERP_DEFAULT_PAGE_SIZE,
  ERP_MAX_PAGE_SIZE,
} from './types';
import { callErp } from './erp-client';

// ---------------------------------------------------------------------------
// 7. read-model — employee org-view (TASK-PC-FE-049 — ADR-MONO-016 § D3)
//   GET /api/erp/read-model/employees  (?asOf=&page=&size=&departmentId=&status=)
//   GET /api/erp/read-model/employees/{id} (?asOf=)
//
//   READ-ONLY (E5) — the read-model holds no domain logic. There is NO
//   mutation function here and no mutation call for this surface. The
//   credential is UNCHANGED (same `getDomainFacingToken()` / IAM OIDC path
//   as the masterdata reads; NEVER `getOperatorToken()`). `?asOf` (E3)
//   threads through verbatim via the same `callErp` helper.
// ---------------------------------------------------------------------------

function orgViewListQs(params: OrgViewListQueryParams): string {
  const qs = new URLSearchParams();
  if (params.asOf) qs.set('asOf', params.asOf);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set(
    'size',
    String(clampPageSize(params.size, ERP_DEFAULT_PAGE_SIZE, ERP_MAX_PAGE_SIZE)),
  );
  if (params.departmentId) qs.set('departmentId', params.departmentId);
  if (params.status) qs.set('status', params.status);
  return qs.toString();
}

/**
 * Paginated employee org-view list from `read-model-service`.
 * Produces the eventually-consistent projection (employee +
 * resolved department hierarchy + cost center + job grade).
 * READ-ONLY — no write function exists or will exist for this
 * surface (E5 — the read-model re-emits nothing).
 */
export async function listEmployeeOrgViews(
  params: OrgViewListQueryParams = {},
): Promise<EmployeeOrgViewListResponse> {
  return callErp(
    {
      path: `/api/erp/read-model/employees?${orgViewListQs(params)}`,
      logPath: '/api/erp/read-model/employees',
    },
    (json) => EmployeeOrgViewListResponseSchema.parse(json),
  );
}

/**
 * Single employee org-view from `read-model-service` by aggregate id.
 * The `meta.unresolved` array (when present) names the references
 * that have not yet been projected — the consumer MUST surface a
 * "동기화 중" badge for those fields and MUST NOT fabricate values.
 * READ-ONLY — same credential + `callErp` path as the list.
 */
export async function getEmployeeOrgView(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<EmployeeOrgViewDetailResponse> {
  const qs = params.asOf ? `?asOf=${encodeURIComponent(params.asOf)}` : '';
  return callErp(
    {
      path: `/api/erp/read-model/employees/${encodeURIComponent(id)}${qs}`,
      logPath: '/api/erp/read-model/employees/{id}',
    },
    (json) => EmployeeOrgViewDetailResponseSchema.parse(json),
  );
}
