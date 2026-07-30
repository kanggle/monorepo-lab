/**
 * `features/operators` hardened HTTP core surface (TASK-PC-FE-110 split).
 *
 * TASK-PC-FE-259 — `callGapOperators` + `OPERATORS_PREFIX` + the
 * `OPERATORS_PROFILE` now live in `shared/api/iam-operators-read.ts`,
 * alongside the `listOperators` READ that `features/dashboards` (the § 2.4.4
 * composed overview) and `features/iam-overview` (the `/iam` landing) also
 * consume. `architecture.md` § Forbidden Dependencies bars a
 * `features/A → features/B` import ("공유 가치는 `shared/` 로 승격"), and a
 * `shared/` module may not import a feature — so the core moved with the read.
 *
 * This module stays the feature-internal import path (`./operators-client`)
 * used by `operators-crud-api` / `operators-self-api` /
 * `operators-assignments-api`, and is still NEVER re-exported through the
 * `operators-api` barrel — the feature's public surface is exactly the prior
 * function set. 0 behavior change: the promoted core is byte-identical (same
 * profile, same `OPERATORS_TIMEOUT_MS`, same `operators_*` log events, same
 * per-endpoint header matrix, same error taxonomy).
 */
export {
  OPERATORS_PREFIX,
  callGapOperators,
} from '@/shared/api/iam-operators-read';
export type { CallOptions } from '@/shared/api/iam-operators-read';
