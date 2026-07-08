/**
 * `features/erp-ops` public API (Layered-by-Feature — app/ imports
 * only this barrel, never feature internals; architecture.md §
 * Allowed Dependencies). erp operations section, TASK-PC-FE-010 —
 * the FOURTH NON-IAM federated domain and the FIRST
 * internal-system-primary confirmation (ADR-MONO-013 Phase 6).
 * READ + DEPARTMENT WRITE PILOT (TASK-PC-FE-046 — § 2.4.8 *Department
 * write binding (PILOT)*): the department master is writable
 * (create / update / retire / move-parent); the other four masters
 * stay read-only.
 *
 * Auth (console-integration-contract § 2.4.8 — REUSE of the § 2.4.5
 * per-domain credential rule, NOT re-derived): this feature's
 * server client uses the **IAM OIDC access token**
 * (`getAccessToken()`), NEVER the IAM exchanged operator token
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
// TASK-PC-FE-076 — the four drill-in section screens (replacing the single
// `ErpOpsScreen`): 마스터 / 통합 조회 / 결재함 / 위임.
export { ErpMastersScreen } from './components/ErpMastersScreen';
export type { ErpMastersScreenProps } from './components/ErpMastersScreen';
// TASK-PC-FE-232 — erp domain overview snapshot (`/erp`). Promotes +
// expands the former TASK-PC-FE-161 masters-embedded overview.
export { ErpOverviewScreen } from './components/ErpOverviewScreen';
export type { ErpOverviewScreenProps } from './components/ErpOverviewScreen';
export { getErpOverviewState } from './api/overview-state';
export type {
  ErpOverviewState,
  ErpAreaCount,
  CellStatus as ErpOverviewCellStatus,
} from './api/overview-state';
export { ErpOrgViewScreen } from './components/ErpOrgViewScreen';
export type { ErpOrgViewScreenProps } from './components/ErpOrgViewScreen';
export { ErpApprovalScreen } from './components/ErpApprovalScreen';
export type { ErpApprovalScreenProps } from './components/ErpApprovalScreen';
export { ErpDelegationScreen } from './components/ErpDelegationScreen';
export type { ErpDelegationScreenProps } from './components/ErpDelegationScreen';
// TASK-PC-FE-076 — shared route eligibility pre-flight + notice.
export { resolveErpEligibility } from './api/erp-eligibility';
export type { ErpEligibility } from './api/erp-eligibility';
export { ErpSectionNotice } from './components/ErpSectionNotice';
export type { ErpNoticeKind } from './components/ErpSectionNotice';
export { EmployeeOrgViewCard } from './components/EmployeeOrgViewCard';
// TASK-PC-FE-055 — delegation facts read-only card (위임 현황).
export { DelegationFactCard } from './components/DelegationFactCard';
// TASK-PC-FE-051 — approval workflow (결재함).
export {
  ApprovalScreen,
  ApprovalDetail,
  ApprovalCreateDialog,
} from './components/ApprovalScreen';
export { approvalErrorMessage } from './components/approval-error';
// TASK-PC-FE-054 — delegation grant management (위임 관리).
export { DelegationScreen } from './components/DelegationScreen';
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
// TASK-PC-FE-076 — the four per-route state loaders (replacing the single
// `getErpSectionState`); each fetches ONLY its route's slice.
export {
  getErpMastersState,
  getErpOrgViewState,
  getErpApprovalState,
  getErpDelegationState,
} from './api/erp-state';
export type {
  ErpMastersState,
  ErpOrgViewState,
  ErpApprovalState,
  ErpDelegationState,
} from './api/erp-state';
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
  employeeOrgViewsListKey,
  employeeOrgViewDetailKey,
  delegationFactsListKey,
  delegationFactDetailKey,
} from './api/erp-state';
export { useAsOf, useEmployeeOrgViews } from './hooks/use-erp-ops';
// TASK-PC-FE-055 — delegation fact read-model hooks.
export {
  useDelegationFacts,
  useDelegationFact,
} from './hooks/use-erp-ops';
// TASK-PC-FE-051 — approval workflow query + mutation hooks.
export {
  useApprovalRequests,
  useApprovalInbox,
  useApprovalRequest,
  useCreateApproval,
  useSubmitApproval,
  useApproveApproval,
  useRejectApproval,
  useWithdrawApproval,
} from './hooks/use-erp-ops';
export type {
  CreateApprovalArgs,
  ApprovalTransitionArgs,
} from './hooks/use-erp-ops';
// TASK-PC-FE-054 — delegation grant management hooks.
export {
  useDelegations,
  useCreateDelegation,
  useRevokeDelegation,
} from './hooks/use-erp-ops';
export type {
  CreateDelegationArgs,
  RevokeDelegationArgs,
} from './hooks/use-erp-ops';
// TASK-PC-FE-054 — delegation types + helpers.
export type {
  DelegationGrant,
  DelegationListResponse,
  DelegationListMeta,
  CreateDelegationInput,
} from './api/delegation-types';
export {
  DelegationGrantSchema,
  DelegationListResponseSchema,
  isActiveGrant,
} from './api/delegation-types';
export {
  DELEGATION_PREFIX,
  delegationListKey,
} from './api/erp-keys';
// TASK-PC-FE-051 — approval types + helpers.
export type {
  ApprovalRequest,
  ApprovalSummary,
  ApprovalHistoryEntry,
  ApprovalStage,
  ApprovalListResponse,
  ApprovalDetailResponse,
  ApprovalListQueryParams,
  ApprovalInboxQueryParams,
  ApprovalStatus,
  ApprovalSubjectType,
  ApprovalTransition,
  CreateApprovalInput,
} from './api/approval-types';
export {
  ApprovalRequestSchema,
  ApprovalSummarySchema,
  ApprovalHistoryEntrySchema,
  ApprovalStageSchema,
  ApprovalListResponseSchema,
  ApprovalDetailResponseSchema,
  APPROVAL_STATUSES,
  APPROVAL_SUBJECT_TYPES,
  APPROVAL_TRANSITIONS,
  TERMINAL_APPROVAL_STATUSES,
  allowedTransitionsFor,
  isTerminalApprovalStatus,
  transitionRequiresReason,
} from './api/approval-types';
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
  // TASK-PC-FE-049 — read-model org-view types.
  EmployeeOrgView,
  EmployeeOrgViewListResponse,
  EmployeeOrgViewDetailResponse,
  DepartmentRef,
  CostCenterRef,
  JobGradeRef,
  DepartmentPathNode,
  ReadModelMeta,
  OrgViewListQueryParams,
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
  // TASK-PC-FE-055 — read-model delegation-fact types.
  DelegationFact,
  DelegationFactListResponse,
  DelegationFactDetailResponse,
  DelegationFactListQueryParams,
} from './api/types';
export {
  isRetired,
  labelForUnknownEnum,
  KNOWN_MASTER_STATUSES,
  KNOWN_EMPLOYMENT_STATUSES,
  KNOWN_PARTNER_TYPES,
  // TASK-PC-FE-049 — read-model org-view schemas.
  EmployeeOrgViewSchema,
  EmployeeOrgViewListResponseSchema,
  EmployeeOrgViewDetailResponseSchema,
  ReadModelMetaSchema,
  // TASK-PC-FE-055 — read-model delegation-fact schemas.
  DelegationFactSchema,
  DelegationFactListResponseSchema,
  DelegationFactDetailResponseSchema,
} from './api/types';
