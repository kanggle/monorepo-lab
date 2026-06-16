/**
 * Server-side IAM admin-service operators-management client — public barrel
 * (TASK-PC-FE-110 split).
 *
 * The former single ~581-line module is split into the hardened HTTP core +
 * cohesive per-concern function modules while preserving THIS module path as
 * the stable public surface: every `@/features/operators/api/operators-api`
 * import (8 route handlers + `operators-state.ts` + `dashboards/overview-api` +
 * the page + tests) keeps working unchanged. Pure structural split — 0
 * behavior / contract / header-matrix / log-event change.
 *
 *   - operators-client.ts          — the hardened `callGapOperators` HTTP core
 *                                     + per-endpoint header matrix + operator-
 *                                     token / active-tenant / timeout /
 *                                     resilience taxonomy (feature-internal —
 *                                     NOT re-exported here, so the public
 *                                     surface stays exactly the prior fn set).
 *   - operators-crud-api.ts        — admin operator management: list / create /
 *                                     edit-roles / change-status / admin-set-profile.
 *   - operators-self-api.ts        — self-service `/me/*`: change own password /
 *                                     update own profile / self-id resolve.
 *   - operators-assignments-api.ts — org-scope: list-assignments / set-org-scope.
 *
 * The privilege-sensitive create/role/status surface, the per-endpoint header
 * matrix, and the password/email/token redaction invariants are UNCHANGED —
 * they now live in `operators-client.ts` (matrix) + `operators-crud-api.ts`
 * (the mutations). Tests assert behavior through this barrel.
 */

export * from './operators-crud-api';
export * from './operators-self-api';
export * from './operators-assignments-api';
