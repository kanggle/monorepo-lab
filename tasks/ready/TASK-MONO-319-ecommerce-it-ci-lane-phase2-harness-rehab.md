# Task ID

TASK-MONO-319

# Title

Ecommerce IT CI lane Phase 2 — rehab the per-service integration-test harness (starting with product-service) and add CI lanes for the 10 services MONO-307 deferred

# Status

ready

# Owner

backend

# Task Tags

- ci
- test

---

# Goal

TASK-MONO-307 Phase 1 added a CI `:integrationTest` lane for **order-service +
payment-service** only. The other **10 ecommerce services** carry ~47 `@Tag("integration")`
Testcontainers ITs that are compiled but **never executed in any CI lane** (Docker-free
`:check` excludes the `integration` tag). The 2026-06-30 backlog sweep flagged this as
the most substantial untracked gap — Phase 1's first green run surfaced money-safety
bugs (BE-439/440/443), so the unguarded services (product-service: cache eviction,
multi-tenant isolation, seller-provisioning ITs; etc.) are a real correctness-coverage
hole.

Services with no CI IT lane: `product-service`, `user-service`, `promotion-service`,
`shipping-service`, `settlement-service`, `search-service`, `review-service`,
`batch-worker`, `notification-service`, `gateway-service`.
(`auth-service` is decommissioned — excluded.)

# Scope

## In Scope

- **Rehab the ecommerce IT harness per service** so each runs green, then add its CI
  lane (mirror the MONO-307 order/payment pattern: a dedicated `integrationTest` task
  + the `ecommerce-integration-tests` CI job entry).
- Pilot with **product-service** (12 IT classes), then extend.

## Out of Scope

- order-service / payment-service (already lane'd by MONO-307).
- The web-store logout e2e (TASK-MONO-318).

---

# Acceptance Criteria

- [ ] **AC-1** — product-service `:integrationTest` (or `test -PrunIntegration`) runs GREEN
  (12 IT classes), locally and on CI.
- [ ] **AC-2** — A dedicated `integrationTest` task added for product-service (MONO-307
  pattern, not wired into `check`), and a CI lane entry added.
- [ ] **AC-3** — Remaining services rehab'd + lane'd (may be split into per-service
  follow-ups).

---

# Scoping findings (2026-06-30)

A scoping pass was run (the backlog sweep → user-approved "scope step 1"):

1. **ecommerce is still on `testcontainers-bom:1.20.4`** (all 13 services) — the repo-wide
   bump to `1.21.3` that unblocked wms/iam/scm/finance/erp/fan local IT (see project
   memory `project_testcontainers_docker_desktop_blocker`, 2026-06-30 RESOLVED) was
   **not applied to ecommerce**. Bumping ecommerce to `1.21.3` cleared the local
   `MalformedChunkCodingException` (the 324 non-IT product-service tests then passed),
   so **the TC bump is a prerequisite + a low-risk standalone alignment**.
2. **After the bump, `product-service:test -PrunIntegration` still fails — 2/2 consecutive
   runs, all 12 IT classes `initializationError` with Testcontainers
   `"Previous attempts to find a Docker environment failed. Will not retry."`** This is
   NOT a flake (consistent), and NOT the host's intermittent Docker-probe skip (it errors,
   not skips). wms `master-service:integrationTest` runs green locally under the **same**
   root Docker convention (`DOCKER_API_VERSION=1.45` forced in root `build.gradle`
   `tasks.withType(Test)`), so the failure is **ecommerce-IT-harness-specific** — the
   harness was never made CI-viable (MONO-307 only rehab'd order/payment: stripped the
   stale npipe override, pinned `@SpringBootConfiguration classes=`, fixed schema-drift,
   moved to a dedicated `integrationTest` task).

**Verdict**: this is **uncertain-depth harness rehab**, not a quick lane-add. Estimated
**M~L**. The exact first-container Docker-detection root cause (under the cached
`IllegalStateException`) needs capturing from the first IT class's stdout — that is the
first rehab step.

**Recommended first steps**: (1) bump ecommerce TC → `1.21.3` (13 files; verify
order/payment IT stay green on CI). (2) Capture the first-container Docker-detection
cause for product-service. (3) Apply the MONO-307-style rehab (dedicated `integrationTest`
task + config fixes) until product-service IT is green locally. (4) Add the CI lane.
(5) Extend to the remaining 9 services.

---

# Related Specs

- `tasks/done/TASK-MONO-307-ecommerce-integration-ci-lane.md` (Phase 1 pattern + phase boundary)
- `platform/testing-strategy.md`

# Related Contracts

- 없음 (CI/test-harness only).

---

# Definition of Done

- [ ] AC-1…AC-3 satisfied (AC-3 may be tracked as per-service follow-ups)
- [ ] Ready for review
