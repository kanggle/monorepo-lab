/**
 * `features/erp-ops` public API (Layered-by-Feature — app/ imports
 * only this barrel, never feature internals; architecture.md §
 * Allowed Dependencies). erp operations section, TASK-PC-FE-010 —
 * the FOURTH NON-GAP federated domain and the FIRST
 * internal-system-primary confirmation (ADR-MONO-013 Phase 6).
 * READ + DEPARTMENT WRITE PILOT (TASK-PC-FE-046 — § 2.4.8 *Department
 * write binding (PILOT)*): the department master is writable
 * (create / update / retire / move-parent); the other four masters
 * stay read-only.
 *
 * Auth (console-integration-contract § 2.4.8 — REUSE of the § 2.4.5
 * per-domain credential rule, NOT re-derived): this feature's
 * server client uses the **GAP OIDC access token**
 * (`getAccessToken()`), NEVER the GAP exchanged operator token
 * (`getOperatorToken()`) — the #569 invariant is GAP-domain-scoped.
 * Same outcome as wms / scm / finance.
 *
 * E2/E3 honesty (§ 2.4.8): every master detail surfaces
 * `effectivePeriod` (active vs retired both rendered, retired NOT
 * hidden) and the `<AsOfPicker>` controls the `?asOf=` URL param
 * that threads through every list / detail query (the producer
 * returns the state-at-that-instant, NEVER current state on a
 * past asOf). E1 reference integrity surfacing — broken / retired
 * cross-references render a `<RetiredReferenceBadge>`, never
 * silently sanitized.
 *
 * Honest erp constraint (§ 2.4.8 — INVERSE of FE-009 finance): erp
 * v1 has BOTH list AND detail GETs for every master (10
 * endpoints) and `?asOf=` on every read — the section is
 * **list-driven with effective-dating first-class**; do NOT
 * force-fit the finance account-id-driven shape.
 */
export { ErpOpsScreen } from './components/ErpOpsScreen';
export { AsOfPicker } from './components/AsOfPicker';
export { EffectivePeriodBadge } from './components/EffectivePeriodBadge';
export { RetiredReferenceBadge } from './components/RetiredReferenceBadge';
export { DepartmentList } from './components/DepartmentList';
export { DepartmentDetail } from './components/DepartmentDetail';
// TASK-PC-FE-046 — department write PILOT.
export { DepartmentWriteDialog } from './components/DepartmentWriteDialog';
export type {
  DeptWriteMode,
  DeptWriteRequest,
} from './components/DepartmentWriteDialog';
// TASK-PC-FE-048 — generic master write dialog (the other four masters).
export {
  MasterWriteDialog,
  useMasterWrite,
} from './components/MasterWriteDialog';
export type {
  MasterFieldDef,
  MasterFieldKind,
  MasterWriteConfig,
  MasterWriteController,
  MasterWriteRequest,
  MasterOption,
} from './components/MasterWriteDialog';
export {
  EMPLOYEE_WRITE_CONFIG,
  JOB_GRADE_WRITE_CONFIG,
  COST_CENTER_WRITE_CONFIG,
  BUSINESS_PARTNER_WRITE_CONFIG,
} from './components/master-write-configs';
export { EmployeeList } from './components/EmployeeList';
export { EmployeeDetail } from './components/EmployeeDetail';
export { JobGradeList } from './components/JobGradeList';
export { JobGradeDetail } from './components/JobGradeDetail';
export { CostCenterList } from './components/CostCenterList';
export { CostCenterDetail } from './components/CostCenterDetail';
export { BusinessPartnerList } from './components/BusinessPartnerList';
export { BusinessPartnerDetail } from './components/BusinessPartnerDetail';
export { getErpSectionState } from './api/erp-state';
export type { ErpSectionState } from './api/erp-state';
export {
  ERP_KEY,
  normaliseAsOf,
  departmentsListKey,
  departmentDetailKey,
  employeesListKey,
  employeeDetailKey,
  jobGradesListKey,
  jobGradeDetailKey,
  costCentersListKey,
  costCenterDetailKey,
  businessPartnersListKey,
  businessPartnerDetailKey,
} from './api/erp-state';
export { useAsOf } from './hooks/use-erp-ops';
// TASK-PC-FE-046 — department write PILOT mutation hooks (the ONLY erp
// mutation hooks; the other four masters have none).
export {
  useCreateDepartment,
  useUpdateDepartment,
  useRetireDepartment,
  useMoveDepartmentParent,
} from './hooks/use-erp-ops';
// TASK-PC-FE-048 — the other four masters' write mutation hooks.
export {
  useCreateEmployee,
  useUpdateEmployee,
  useRetireEmployee,
  useCreateJobGrade,
  useUpdateJobGrade,
  useRetireJobGrade,
  useCreateCostCenter,
  useUpdateCostCenter,
  useRetireCostCenter,
  useCreateBusinessPartner,
  useUpdateBusinessPartner,
  useRetireBusinessPartner,
} from './hooks/use-erp-ops';
export type {
  Department,
  DepartmentListResponse,
  DepartmentDetailResponse,
  Employee,
  EmployeeListResponse,
  EmployeeDetailResponse,
  JobGrade,
  JobGradeListResponse,
  JobGradeDetailResponse,
  CostCenter,
  CostCenterListResponse,
  CostCenterDetailResponse,
  BusinessPartner,
  BusinessPartnerListResponse,
  BusinessPartnerDetailResponse,
  EffectivePeriod,
  Audit,
  ErpMeta,
  ErpListQueryParams,
  ErpDetailQueryParams,
  CreateDepartmentInput,
  UpdateDepartmentInput,
  RetireDepartmentInput,
  MoveDepartmentParentInput,
  CreateEmployeeInput,
  UpdateEmployeeInput,
  CreateJobGradeInput,
  UpdateJobGradeInput,
  CreateCostCenterInput,
  UpdateCostCenterInput,
  CreateBusinessPartnerInput,
  UpdateBusinessPartnerInput,
} from './api/types';
export {
  isRetired,
  labelForUnknownEnum,
  KNOWN_MASTER_STATUSES,
  KNOWN_EMPLOYMENT_STATUSES,
  KNOWN_PARTNER_TYPES,
} from './api/types';
