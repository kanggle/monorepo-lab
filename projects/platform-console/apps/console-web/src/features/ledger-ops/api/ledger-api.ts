/**
 * Server-side finance `ledger-service` operations client — public barrel.
 *
 * TASK-PC-FE-102 split the former single ~740-line module into cohesive units
 * while preserving this module path as the stable public surface (every
 * `@/features/ledger-ops/api/ledger-api` import — 12 route handlers +
 * `ledger-state.ts` + tests — keeps working unchanged):
 *
 *   - `ledger-client.ts`            — the hardened `callLedger` HTTP core +
 *                                     the finance FLAT error-envelope parser +
 *                                     the `pageParams` helper + the
 *                                     per-domain-credential / resilience /
 *                                     "NO 429 handling" narrative.
 *   - `ledger-reads-api.ts`         — the nine pure GET reads (trial balance /
 *                                     periods list+detail / journal entry /
 *                                     account balance+entries / statement /
 *                                     FX position lots / FX rates).
 *   - `ledger-reconciliation-api.ts`— the reconciliation discrepancy reads +
 *                                     the ledger's FIRST and ONLY mutation
 *                                     (`resolveDiscrepancy`).
 *
 * Pure structural split — 0 behavior / contract / log-event change.
 */

export * from './ledger-reads-api';
export * from './ledger-reconciliation-api';
export * from './ledger-fx-rates-api';
