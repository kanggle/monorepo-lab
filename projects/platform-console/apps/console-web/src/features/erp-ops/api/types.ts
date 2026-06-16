/**
 * Feature-local types for the erp `masterdata-service` console binding —
 * public barrel (TASK-PC-FE-109 split).
 *
 * The former single ~757-line module is split into cohesive per-area type
 * modules under `./types/` while preserving THIS module path as the stable
 * surface: every `@/features/erp-ops/api/types` import (the api modules, the
 * hooks, the route-handler body validators, the components + tests) keeps
 * working unchanged. Pure structural split — 0 schema / behavior change.
 *
 *   - types/common              — E2 EffectivePeriod, honest tolerant enum
 *                                 vocabularies, Audit, ErpMeta / ReadModelMeta,
 *                                 ErpList/DetailQueryParams + page bounds,
 *                                 labelForUnknownEnum / isRetired, ErpRetireBody.
 *   - types/department          — Department read + WRITE PILOT (FE-046).
 *   - types/employee            — Employee read + create/update (PII name).
 *   - types/job-grade           — JobGrade read + create/update.
 *   - types/cost-center         — CostCenter read + create/update.
 *   - types/business-partner    — BusinessPartner read + create/update
 *                                 (confidential paymentTerms).
 *   - types/employee-org-view   — read-model EmployeeOrgView + refs (FE-049).
 *   - types/delegation-fact     — read-model DelegationFact (FE-055).
 *
 * Completes the TASK-PC-FE-107 (hooks/masters) + TASK-PC-FE-108 (api/masters)
 * erp-ops "masters" vertical on the types layer.
 *
 * Authoritative producer contract (do NOT redefine — consume read-only):
 *   `erp-platform/specs/contracts/http/masterdata-api.md` (5 masters × list+detail)
 *   `erp-platform/specs/contracts/http/read-model-api.md` (EmployeeOrgView, DelegationFact)
 * Consumer obligation: `console-integration-contract.md` § 2.4.8.
 */

export * from './types/common';
export * from './types/department';
export * from './types/employee';
export * from './types/job-grade';
export * from './types/cost-center';
export * from './types/business-partner';
export * from './types/employee-org-view';
export * from './types/delegation-fact';
