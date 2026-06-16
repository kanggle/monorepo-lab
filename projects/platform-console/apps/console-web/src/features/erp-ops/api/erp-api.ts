/**
 * Server-side erp `masterdata-service` operations client — public barrel.
 *
 * TASK-PC-FE-098 split the former single ~1.1k-line module into cohesive
 * units while preserving this module path as the stable public surface
 * (every `@/features/erp-ops/api/erp-api` import — 20 route handlers +
 * `erp-state.ts` + tests — keeps working unchanged):
 *
 *   - `erp-client.ts`             — the hardened `callErp` HTTP core +
 *                                   the erp FLAT error envelope parser +
 *                                   the per-domain-credential / resilience /
 *                                   "NO 429 handling" narrative (the
 *                                   `erp-api.test.ts` grep-guard pins this
 *                                   file).
 *   - `erp-masters-api.ts`        — the 5 masters' read + write functions
 *                                   (departments / employees / job-grades /
 *                                   cost-centers / business-partners) +
 *                                   the list/detail query-string helpers.
 *   - `erp-orgview-api.ts`        — read-model employee org-view reads.
 *   - `erp-delegation-facts-api.ts` — read-model delegation-fact reads.
 *
 * Pure structural split — 0 behavior / contract / log-event change.
 */

export * from './erp-masters-api';
export * from './erp-orgview-api';
export * from './erp-delegation-facts-api';
