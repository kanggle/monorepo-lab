/**
 * `features/audit` public type surface — the IAM unified audit + security
 * read shapes (§ 2.4.2, READ-ONLY).
 *
 * TASK-PC-FE-259 — the shapes themselves live in
 * `shared/api/iam-audit-types.ts`: the audit read they parse is consumed by
 * three features (`audit`, `dashboards`, `iam-overview`), and
 * `architecture.md` § Forbidden Dependencies bars a `features/A →
 * features/B` import ("공유 가치는 `shared/` 로 승격"). Re-exported here so
 * every existing `../api/types` import inside `features/audit` (the filter
 * bar, the discriminated table, the state loader, the hooks) is unchanged.
 *
 * The union stays tolerant: an unrecognised `source` parses to a
 * `GenericAuditRow` and NEVER throws (console-integration-contract § 2.4.2
 * "discriminated rendering tolerance").
 */
export * from '@/shared/api/iam-audit-types';
