/**
 * Feature-local types for the erp `masterdata-service` console binding —
 * public barrel (TASK-PC-FE-109 split).
 *
 * The former single ~757-line module is split into cohesive per-area type
 * modules under `./` while preserving `@/features/erp-ops/api/types` as the
 * stable surface: every import of that path (the api modules, the hooks, the
 * route-handler body validators, the components + tests) keeps working
 * unchanged. Pure structural split — 0 schema / behavior change.
 *
 *   - common              — E2 EffectivePeriod, honest tolerant enum
 *                           vocabularies, Audit, ErpMeta / ReadModelMeta,
 *                           ErpList/DetailQueryParams + page bounds,
 *                           labelForUnknownEnum / isRetired, ErpRetireBody.
 *   - department          — Department read + WRITE PILOT (FE-046).
 *   - employee            — Employee read + create/update (PII name).
 *   - job-grade           — JobGrade read + create/update.
 *   - cost-center         — CostCenter read + create/update.
 *   - business-partner    — BusinessPartner read + create/update
 *                           (confidential paymentTerms).
 *   - employee-org-view   — read-model EmployeeOrgView + refs (FE-049).
 *   - delegation-fact     — read-model DelegationFact (FE-055).
 *
 * Completes the TASK-PC-FE-107 (hooks/masters) + TASK-PC-FE-108 (api/masters)
 * erp-ops "masters" vertical on the types layer.
 *
 * Authoritative producer contract (do NOT redefine — consume read-only):
 *   `erp-platform/specs/contracts/http/masterdata-api.md` (5 masters × list+detail)
 *   `erp-platform/specs/contracts/http/read-model-api.md` (EmployeeOrgView, DelegationFact)
 * Consumer obligation: `console-integration-contract.md` § 2.4.8.
 */

export * from './common';
export * from './department';
export * from './employee';
export * from './job-grade';
export * from './cost-center';
export * from './business-partner';
export * from './employee-org-view';
export * from './delegation-fact';
