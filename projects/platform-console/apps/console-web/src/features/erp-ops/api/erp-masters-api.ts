/**
 * erp masters api — public barrel (TASK-PC-FE-108 split).
 *
 * The former single ~628-line module is split into cohesive per-entity api
 * modules under `./masters/` while preserving THIS module path as the stable
 * surface: the `erp-api` barrel's `export * from './erp-masters-api'` (and
 * every downstream import — 20 route handlers + `erp-state.ts` + tests) keeps
 * working unchanged. Pure structural split — 0 behavior / contract /
 * log-event / endpoint change.
 *
 *   - masters/masters-qs           — the shared server-side list/detail
 *                                     query-string builders + the `compact`
 *                                     body cleaner (feature-internal).
 *   - masters/departments-api      — list + detail + the department WRITE
 *                                     PILOT mutations (FE-046).
 *   - masters/employees-api        — list + detail + create/update/retire.
 *   - masters/job-grades-api       — list (displayOrder asc) + detail + CUD.
 *   - masters/cost-centers-api     — list + detail + create/update/retire.
 *   - masters/business-partners-api— list + detail + CUD (confidential).
 *
 * Mirrors the TASK-PC-FE-107 `hooks/masters/` per-entity hook split — the two
 * together complete the erp-ops "masters" vertical (types still pending).
 */

export * from './masters/departments-api';
export * from './masters/employees-api';
export * from './masters/job-grades-api';
export * from './masters/cost-centers-api';
export * from './masters/business-partners-api';
