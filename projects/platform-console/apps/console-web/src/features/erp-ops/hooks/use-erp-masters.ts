'use client';

/**
 * erp-ops masters hooks — public barrel (TASK-PC-FE-107 split).
 *
 * The former single ~755-line module is split into cohesive per-entity hook
 * modules under `./masters/` while preserving THIS module path as the stable
 * surface: the `use-erp-ops` barrel's `export * from './use-erp-masters'`
 * (and every downstream import) keeps working unchanged. Pure structural
 * split — 0 behavior / contract / query-key / endpoint change.
 *
 *   - masters/use-departments        — list + detail + the department WRITE
 *                                       PILOT mutations (create / update /
 *                                       retire / move-parent — FE-046).
 *   - masters/use-employees          — list + detail + create/update/retire
 *                                       (PII; never logged).
 *   - masters/use-job-grades         — list (displayOrder asc) + detail +
 *                                       create/update/retire.
 *   - masters/use-cost-centers       — list + detail + create/update/retire.
 *   - masters/use-business-partners  — list + detail + create/update/retire
 *                                       (confidential paymentTerms).
 *   - masters/use-employee-org-views — read-model org-view read (FE-049,
 *                                       READ-ONLY; no mutation hook — E5).
 *
 * The shared prefix-invalidation helper (`invalidateMaster`) moved to
 * `use-erp-shared.ts` (alongside the masters list/detail query-string
 * builders); the department-only `invalidateDepartments` stays local to
 * `masters/use-departments`.
 */

export * from './masters/use-departments';
export * from './masters/use-employees';
export * from './masters/use-job-grades';
export * from './masters/use-cost-centers';
export * from './masters/use-business-partners';
export * from './masters/use-employee-org-views';
