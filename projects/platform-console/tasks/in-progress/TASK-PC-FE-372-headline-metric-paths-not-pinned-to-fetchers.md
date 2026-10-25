# Task ID

TASK-PC-FE-372

# Title

The home headline composition restates each domain's metrics path, and the comment says a unit test pins it to the feature fetchers — no test does

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- test
- console

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (export four path constants, use them, one contrast test)
> Drafted 2026-10-25 UTC. Owner said «진행» to the recommendation on 2026-10-25 UTC. Facts read on origin/main `53a9227fb`.

# Dependency Markers

- 출처: `TASK-PC-FE-368` (done) — 2026-10-24 handoff «홈 합성이 도메인 지표 경로를 다시 적었다 … fetcher 와의 대조 시험이 없다».

# Goal

If a domain's «실적» fetcher and the home headline leg ever disagree on the metrics path, a unit test goes red — instead of the home card silently showing «불러올 수 없음» while the domain screen works.

# Scope

## In Scope

- **AC-0 facts (measured while drafting):**
  - `shared/composition/operator-overview-headline.ts:59-75` builds six URLs by hand. Its comment (`:31-35`) says the paths are «restated here and pinned against them by a unit test» — `shared/` may not import `features/` (architecture.md § Forbidden Dependencies).
  - The test that claims it, `tests/unit/operator-overview-headline-composition.test.ts`, routes requests by substring (`:73-78`, `url.includes('/po/metrics')` …) and never reads a feature fetcher. A fetcher path change leaves it green.
  - Fetchers: iam `features/iam-overview/api/iam-metrics-api.ts:48` (`IAM_METRICS_PATH`, exported) · finance `features/finance-overview/api/finance-metrics-api.ts:41` (`FINANCE_METRICS_PATH`, exported) · wms `features/wms-ops/api/wms-metrics-api.ts:36` (inline `/dashboard/metrics`) · scm `features/scm-ops/api/po-metrics-api.ts:38` (inline `/api/v1/procurement/po/metrics`) · erp `features/erp-ops/api/approval-metrics-api.ts:29-30` (inline `/api/erp/approval/metrics`) · ecommerce `features/ecommerce-ops/api/orders-api.ts:133` (inline `/orders/metrics`).
- **Fix:** export a path constant from the four inline fetchers (as iam and finance already do) and use it there; add a unit test (tests may import both layers) that, for each domain, asserts the headline leg URL's path equals `<base path> + <the fetcher's constant>` and that the leg uses the same base env var as the fetcher. Correct the composition comment to name the test.

## Out of Scope

- Making `shared/` import the constants (forbidden direction — the test is the pin).

# Acceptance Criteria

- [ ] **AC-1** — four new exported path constants; each fetcher builds its URL from its constant (no behaviour change — the existing fetcher tests stay green).
- [ ] **AC-2** — the contrast test covers all six domains; changing one fetcher constant turns it red (shown by mutation).
- [ ] **AC-3** — console `vitest run --maxWorkers=2 --minWorkers=1` · `tsc --noEmit` · lint green.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Forbidden Dependencies
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1

# Related Contracts

- None changed.

# Edge Cases

- wms and ecommerce bases already end in a path (`…/api/v1/admin`, `…/api/admin`) — compare the full path (base path + constant), not just the constant.

# Failure Scenarios

1. The test compares two copies written in the test itself (a third copy) instead of importing the fetchers' constants — it would stay green on a fetcher change. AC-2's mutation proves it reads the fetcher.
