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

**Phase 2 done (#2055 bump + #2056 lane, both MERGED to main)**: product-service dedicated
`integrationTest` task added + wired into the `ecommerce-integration-tests` CI job
(order + payment + product), GREEN. The first CI run surfaced + fixed two real harness gaps the
local npipe flake had masked: (a) Redis `RedisConnectionFailureException` on 4 cache-path ITs →
`spring.cache.type=none` in their `@DynamicPropertySource`; (b) an unstubbed `@MockitoBean`
`SellerAccountProvisioner` NPE → `@BeforeEach` stub `ProvisioningResult.failed()`.

---

# Resume playbook (next session) — AC-3 nine-service rollout

State at handoff: main @ `aa2a002ee`, all Phase-1/2 PRs merged, MONO-319 stays in `ready/`.
Lane has order + payment + product. **AC-3 = add the remaining 9 services.**

**Per-service IT inventory (2026-06-30 scan; verify before each):**

| service | IT classes | dedicated `integrationTest` task? | Redis/@Cacheable? |
|---|---|---|---|
| gateway-service | 2 | **YES already** (build.gradle:52) — only needs the CI-job entry | no |
| user-service | 7 | no — add task | no |
| promotion-service | 6 | no — add task | no |
| batch-worker | 6 (3 @Tag) | no — add task | no |
| shipping-service | 4 | no — add task | no |
| settlement-service | 4 | no — add task | no |
| review-service | 3 | no — add task | no |
| search-service | 2 (4 @Tag) | no — add task | no |
| notification-service | 2 | no — add task | no |

**None of the 9 use Redis/@Cacheable** → the product-service Redis gap is unlikely to recur; watch
instead for Kafka / WireMock-stub / unstubbed-`@MockitoBean` NPE gaps (search-service likely needs
an Elasticsearch/OpenSearch container — check its ITs first).

**Steps (mechanical, repeat per service):**
1. **Add the dedicated task** to each service's `build.gradle` (except gateway, which has it). Copy
   the exact block from `product-service/build.gradle` (the `tasks.register('integrationTest', Test)`
   stanza with `includeTags 'integration'`, 5m timeout, NOT in `check`). It auto-inherits the root
   `tasks.withType(Test)` Docker env.
2. **Wire into CI** — `.github/workflows/ci.yml`, job `ecommerce-integration-tests` (~line 1300):
   add each `:projects:ecommerce-microservices-platform:apps:<svc>:integrationTest` to the gradle
   run step, and add its `build/reports/tests/integrationTest/` + `build/test-results/integrationTest/`
   to the failure-upload `path:` list.
3. **Push → first CI run is the gate** (local is the npipe flake, NOT authority — see correction
   above). For any RED: `gh run download <run-id> -n ecommerce-integration-test-reports -D <dir>`,
   parse the per-class JUnit XML (PowerShell `[xml]` → testsuite tests/failures/errors + testcase
   failure messages) — the gh job log is a 153 KB single-line classpath-noise blob and swallows the
   server-side 500 stack, so the **XML artifact is the authority**. Fix per-service gaps the same
   way (cache off / mock stub / missing container) mirroring a passing sibling IT in that service.
4. **Job-time budget**: the lane grows 12→21 services × (Postgres + embedded Kafka). The job is
   `timeout-minutes: 30`. If it approaches the cap, either split into 3+3+3 batches across separate
   jobs, or raise the timeout. Watch the wall-clock as services are added.
5. **CI path-filter**: editing each service's `build.gradle` (under `projects/ecommerce-…`) trips the
   `ecommerce` change-filter, so the job runs on the PR automatically — no path-filter edit needed.

Recommended cadence: batch 3 services per PR (e.g. user+promotion+gateway first), so a RED is easy
to triage and the job-time growth is observable. `(분석=Opus 4.8 / 구현 권장=Opus — 첫 CI run의 갭
진단은 판단 필요; 레인 배선 자체는 Sonnet 가능)`

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
