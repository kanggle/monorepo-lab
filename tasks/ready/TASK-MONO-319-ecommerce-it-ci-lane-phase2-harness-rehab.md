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

# Scoping CORRECTION (2026-06-30, Phase 2 diagnosis) — premise above was wrong

Phase 1 (TC bump #2055) is **DONE** (merged, CI-green; order/payment IT lanes pass under
1.21.3). The first-container Docker-detection root cause was then captured (`--info` on a
single product-service IT, surfacing the real cause under the cached `IllegalStateException`):

```
NpipeSocketClientProviderStrategy: failed with exception
  MalformedChunkCodingException: Bad chunk header
→ Could not find a valid Docker environment
```

**This is a FLAKY host-transport issue, NOT an ecommerce-IT-harness defect.** The scoping
finding #2 above ("NOT a flake (consistent)", "ecommerce-IT-harness-specific") is **wrong** —
it generalised from only 2 runs. With more runs the failure is plainly intermittent:

- product-service `:integrationTest` (clean dedicated task, identical config): **run #1 PASS /
  run #2 FAIL** back-to-back (~1 pass per 3 runs). The Npipe `MalformedChunkCodingException`
  bites intermittently.
- `wms master-service:integrationTest` passed once and also shares the exact same root Docker
  convention — its "green" was the same flaky draw, not a harness difference.
- Disproven along the way: the `~/.testcontainers.properties` npipe strategy pin (removing it
  did not help — Npipe is the only Windows strategy TC auto-selects anyway); and a JaCoCo-agent
  hypothesis (disabling it did not help).

**Corrected verdict**: there is **no ecommerce-specific harness bug to rehab**. The real
deliverable is simply **add a dedicated `integrationTest` task per service + wire it into the
`ecommerce-integration-tests` CI job** (MONO-307 pattern). **CI Linux (unix socket) is the
authority** — it has no npipe and no `MalformedChunkCoding` flake (the existing order/payment
lanes prove ecommerce ITs run reliably there). Local Windows IT is flaky-but-passes-on-retry;
it is NOT a reliable gate (this also corrects project memory
`project_testcontainers_docker_desktop_blocker`'s "local is IT-verification authority" for the
npipe path).

**AC-1 reframed**: product-service ITs run GREEN **on CI**; locally they are flaky (host npipe),
green on retry — do not treat a single local failure as a regression.

**Phase 2 done in this PR**: product-service dedicated `integrationTest` task added + wired into
the `ecommerce-integration-tests` CI job (order + payment + product). AC-3 (remaining 9 services)
= per-service follow-ups: each just needs the same `integrationTest` task block + a CI-job entry
(no harness rehab — the ITs already compile and the harness is CI-viable).

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
