/**
 * `features/tenants` public API (Layered-by-Feature — app/ imports only this
 * barrel, never feature internals; architecture.md § Allowed Dependencies).
 *
 * IAM tenant-management CRUD surface (TASK-PC-FE-226 — the isolation-boundary
 * resource itself, SUPER_ADMIN only). Functionally UNRELATED to
 * `features/tenant` (`TenantSwitcher` — the operator's own active-tenant
 * session selector); do not conflate.
 */
export { TenantsScreen } from './components/TenantsScreen';
export type { TenantsScreenProps } from './components/TenantsScreen';
export { TenantDetail } from './components/TenantDetail';
export type { TenantDetailProps } from './components/TenantDetail';

export { getTenantsListState, getTenantDetailState } from './api/tenants-state';
export type { TenantsListState, TenantDetailState } from './api/tenants-state';

export type {
  Tenant,
  TenantPage,
  TenantType,
  TenantStatus,
  TenantPatchStatus,
  TenantListParams,
  CreateTenantInput,
  UpdateTenantInput,
  TenantOrgNodeOption,
  FirstAdminSummary,
} from './api/types';
export {
  TENANT_TYPES,
  TENANT_STATUSES,
  TENANT_PATCH_STATUSES,
  FIRST_ADMIN_EMAIL_REGEX,
  FIRST_ADMIN_EMAIL_MAX,
  FIRST_ADMIN_DISPLAY_NAME_MAX,
} from './api/types';
