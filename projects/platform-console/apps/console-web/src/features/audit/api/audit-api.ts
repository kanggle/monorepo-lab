/**
 * `features/audit` server-side client surface — the IAM unified audit +
 * security read (TASK-PC-FE-003, § 2.4.2; READ-ONLY — no mutation surface).
 *
 * TASK-PC-FE-259 — the implementation lives in
 * `shared/api/iam-audit-read.ts`. `features/dashboards` (the § 2.4.4 composed
 * operator overview) and `features/iam-overview` (the `/iam` landing snapshot)
 * consume the SAME `queryAudit` read, and `architecture.md` § Forbidden
 * Dependencies bars a `features/A → features/B` import —
 *
 *   > 같은 계층 `features/A → features/B` 상호 참조 금지
 *   > (공유 가치는 `shared/` 로 승격).
 *
 * — so the read was promoted, exactly as `shared/api/rbac-catalog.ts` was for
 * `features/permissions` + `features/permission-sets`.
 *
 * **This module is NOT dead weight — do not "clean it up".** It is the
 * feature's contract-pinned public path: `console-integration-contract.md`
 * § 3.1 rows 9 (audit: query), 10 (security: login-history) and 11 (security:
 * suspicious) bind those parity capabilities to `features/audit` + client
 * export `queryAudit`, and `tests/unit/parity-verification.test.ts` mechanically
 * attests all 16 rows through this path. Removing the re-export would break the
 * ADR-MONO-013 Phase 3 `admin-web`-retirement gate; changing it needs a
 * contract change first.
 */
export { queryAudit } from '@/shared/api/iam-audit-read';
