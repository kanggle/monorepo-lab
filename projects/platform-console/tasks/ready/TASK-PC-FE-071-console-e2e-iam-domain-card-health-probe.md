# Task ID

TASK-PC-FE-071

# Title

Make the IAM domain-health card resolve to `data-status='ok'` in the Platform Console E2E stack so `overview-consolidation.spec.ts` "IAM card drills down to IAM detail" passes — the console-bff domain-health probe of the IAM domain currently yields `degraded` in the e2e compose, and the IAM drill-down link is gated on `status==='ok'` (`DomainCard.tsx:242`), so the spec's drill-down assertions (the test's actual purpose) cannot run. Surfaced once TASK-PC-FE-070 fixed the login `globalSetup` and the suite began executing specs again (nightly console-e2e: 0/7 → 6/7; this is the remaining 1/7). Likely root cause: the e2e IAM services run with `KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9999` (non-routable), so `admin-service`'s aggregate `/actuator/health` (the BFF IAM probe target) reports a Kafka component DOWN → non-`UP` → BFF classifies the card `degraded`. Requires running the dockerized e2e stack to confirm the probe response, then a bounded e2e-harness fix.

# Status

ready

# Owner

frontend-engineer + infra (platform-console e2e harness — docker-compose.e2e.yml IAM probe target and/or admin-service actuator health-group config; possibly a console-bff health-classification check). NOT console-web src — the failing assertion is a real product behaviour (drill-down shown only when IAM is healthy), so the fix must make the e2e IAM probe genuinely `ok`, not relax the spec.

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- test
- code
- deploy

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **surfaced by**: [TASK-PC-FE-070](../review/TASK-PC-FE-070-console-e2e-login-landing-route-fix.md) (squash `2cb31f5e2`, #1405) — that fix restored the e2e `globalSetup` OIDC login (which had aborted the whole suite for ~11 days). Once specs ran again, `overview-consolidation.spec.ts:88` failed on `expect(iamCard).toHaveAttribute('data-status','ok')`. This task is the second, previously-masked failure.
- **introduced by**: [TASK-PC-FE-034](../done/TASK-PC-FE-034-consolidate-overview-screens-home-5-domain.md) (#1017, `175b36ae8`, 2026-06-02) — promoted the 5-domain overview to the console home and added `overview-consolidation.spec.ts` with the IAM-card-`ok` + drill-down assertions. The e2e harness (`docker-compose.e2e.yml`) was built (pre-PC-FE-034) so that **only the finance card is guaranteed `ok`** — see its explicit comment: *"Only the finance leg is exercised by the 2 e2e specs … the other domain legs (gap registry / wms / scm / erp) are wired to non-routable hostnames so their adapters [degrade]."* The new IAM-card-`ok` assertion was never reconciled with that harness design (and never ran in nightly because login was simultaneously broken).
- **NON-required-check note**: the `Nightly E2E` workflow's `Platform Console E2E full-stack` job is not a PR-required check, so neither PC-FE-034 nor this gap was caught at PR time. Per the `project_adr023…` lesson, post-merge nightly confirmation must be part of any console routing/overview change's done-definition.

---

# Goal

`overview-consolidation.spec.ts:88` ("IAM card on the home overview drills down to the IAM detail (/dashboards)") asserts:

```ts
const gapCard = page.getByTestId('operator-overview-card-iam');
await expect(gapCard).toHaveAttribute('data-status', 'ok');   // line 93 — FAILS (status='degraded')
const drilldown = page.getByTestId('operator-overview-card-iam-drilldown');
await expect(drilldown).toBeVisible();                         // line 96 — drilldown not rendered when degraded
// … click → /dashboards → "IAM 상세 …" heading + back link
```

The IAM card is `degraded` in the e2e stack, so the assertion fails. Critically, the drill-down link is **gated on health** (`DomainCard.tsx:242`):

```ts
const gapDrilldown = card.domain === 'iam' && card.status === 'ok';
```

so relaxing line 93 does NOT fix the test — when `degraded`, the `operator-overview-card-iam-drilldown` element is never rendered and lines 96-111 fail too. The test fundamentally requires the IAM card to be **genuinely `ok`** (this is correct product behaviour: the drill-down is offered only when IAM is healthy). The fix must therefore make the e2e IAM domain-health probe return `ok`.

How the IAM card status is computed (verified by static read):

- console-web `/dashboards/overview` (5-domain home, PC-FE-034) renders `DomainCard` per domain; `data-status={card.status}` (`ok` | `degraded`), card data from the same-origin `/api/console/dashboards/domain-health` proxy → console-bff.
- console-bff `DomainHealthController` fans out across all 5 domains' **public** `GET /actuator/health` via `AbstractHealthReadAdapter` (`uri("/actuator/health")`, no auth). The IAM leg's base URL is `CONSOLE_BFF_OUTBOUND_IAM_BASE_URL`.
- e2e compose (`projects/platform-console/docker-compose.e2e.yml`): `CONSOLE_BFF_OUTBOUND_IAM_BASE_URL: http://admin-service:8085` (finance → real service; wms/scm/erp → `http://127.0.0.1:9` non-routable = intentionally degraded). `admin-service` listens on 8085 and its OWN compose healthcheck is `curl -fsS http://localhost:8085/actuator/health`, so the endpoint exists and returns 2xx to a liveness probe.

Likely root cause (to confirm at runtime): the e2e IAM services run with `KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9999` (non-routable, from `x-iam-service-env`). `admin-service`'s aggregate `/actuator/health` therefore includes a **Kafka health component DOWN** → overall `status != UP`. The compose healthcheck may still pass if it targets a liveness group that excludes Kafka, but the BFF's `AbstractHealthReadAdapter` reads the **full** `/actuator/health` and classifies non-`UP` as `degraded`. Net: `admin-service` is "alive" for the compose gate but "degraded" to the BFF.

Candidate fixes (pick after runtime confirmation — a SINGLE bounded change, no console-web src behaviour change):

1. **(preferred, if hypothesis holds)** Configure the e2e IAM services' actuator so the readiness/aggregate health the BFF reads excludes the deliberately-dead Kafka component — e.g. `management.health.kafka.enabled=false` (or a health-group) in the e2e profile of `admin-service` (or whichever IAM service the BFF probes), so `/actuator/health` is `UP`.
2. **(alternative)** Point `CONSOLE_BFF_OUTBOUND_IAM_BASE_URL` at an IAM service whose full `/actuator/health` is already `UP` in the e2e stack (e.g. `auth-service:8081`, which has a confirmed-healthy probe and is the most "IAM-representative" — the OIDC AS), if that is acceptable as the IAM domain-health source.
3. **(only if 1/2 are wrong and the harness genuinely cannot make IAM ok)** Re-scope the spec to test the drill-down against a domain that IS guaranteed `ok` in the harness, or make the drill-down test conditional — LAST RESORT, because it weakens the PC-FE-034 acceptance.

# Scope

## In Scope

A single bounded e2e-harness fix to make the IAM domain-health card `ok` in the e2e stack, confirmed by the `overview-consolidation.spec.ts` IAM-card test passing in the nightly `Platform Console E2E` job:

- `projects/platform-console/docker-compose.e2e.yml` (IAM probe target / IAM-service actuator env), and/or
- the e2e profile / actuator health-group config of the IAM service the BFF probes (`admin-service` or `auth-service`, under `iam-platform`), and/or
- (only if the BFF's non-`UP`→`degraded` classification is itself wrong for a partially-degraded-but-serving domain) a console-bff health-classification adjustment — but default assumption is the harness, not the BFF, is at fault.
- Task md + `INDEX.md` lifecycle.

## Out of Scope

- **console-web `src/` behaviour change.** The drill-down-gated-on-health (`DomainCard.tsx:242`) is correct product behaviour; do NOT relax it or the spec's `ok` assertion to paper over a degraded probe.
- **Making wms/scm/erp cards `ok`.** They are intentionally `127.0.0.1:9` (degraded by design); this task is IAM-only.
- **PC-FE-070's login fix** — already merged; this task is strictly the downstream IAM-card-health gap.
- **Promoting the nightly console-e2e job to a PR-required check** — separate CI-gating-policy scope (noted in Failure Scenarios as the systemic gap that let this latency happen).

# Acceptance Criteria

- [ ] **AC-1** Runtime-confirm the root cause: bring up `docker-compose.e2e.yml`, capture `admin-service` (or the BFF's IAM probe target) `GET /actuator/health` body, and the console-bff domain-health composition result for the `iam` card. Record the actual non-`UP` reason (expected: Kafka component DOWN) before fixing.
- [ ] **AC-2** After the fix, the BFF domain-health response for the `iam` card is `status: 'ok'` in the e2e stack (`/api/console/dashboards/domain-health` shows `iam: ok`).
- [ ] **AC-3** `overview-consolidation.spec.ts:88` passes: IAM card `data-status='ok'`, the `operator-overview-card-iam-drilldown` link renders, is keyboard-focusable, navigates to `/dashboards`, and the "IAM 상세 (계정 · 감사 · 운영자)" heading + back link render.
- [ ] **AC-4** The nightly `Platform Console E2E full-stack` job returns `success` (7/7) — verified on the first nightly run after merge (`gh run list --workflow=nightly-e2e.yml`). This is the authoritative check; local `:check` does not exercise the dockerized login/probe.
- [ ] **AC-5** No console-web `src/` behaviour change; the fix is confined to the e2e harness (compose / IAM-service e2e actuator config) — and, if a BFF change is truly required, it is justified against the domain-health contract (`§ 2.4.9.2`), not a test-only hack.
- [ ] **AC-6** The finance card (and the existing PC-FE-016 finance ok-after-save assertion) and the other 6 currently-passing specs stay green — the fix does not regress the rest of the suite.

# Related Specs

- [`specs/services/console-web/architecture.md`](../../specs/services/console-web/architecture.md) — 5-domain overview + E2E testing requirement.
- [`specs/services/console-bff/architecture.md`](../../specs/services/console-bff/architecture.md) — Domain Health Overview composition (5-domain `/actuator/health` fan-out, `AbstractHealthReadAdapter`).
- [TASK-PC-FE-034](../done/TASK-PC-FE-034-consolidate-overview-screens-home-5-domain.md) — the spec + landing this task makes pass.

# Related Contracts

- [`console-integration-contract.md`](../../specs/contracts/console-integration-contract.md) § 2.4.9.2 (Domain Health Overview — per-card `status ∈ {ok, degraded}`, the BFF composes it from each domain's `/actuator/health`). The fix must keep the proxy/composition byte-compatible; only the e2e probe target/health-config changes. If option 3 (BFF classification) is taken, this contract's `degraded` semantics are the reference.

# Edge Cases

- **admin-service alive for compose gate but degraded to BFF**: the compose `service_healthy` may use a liveness group while the BFF reads the full aggregate health — exactly the suspected mismatch. AC-1 must capture BOTH (the compose-gate probe AND the BFF-read probe) to confirm.
- **Kafka deliberately dead in e2e IAM services** (`127.0.0.1:9999`): the fix must NOT make Kafka reachable (out of scope + unnecessary) — it must make the *health the BFF reads* `UP` despite Kafka being intentionally absent (disable/group-exclude the Kafka indicator in the e2e profile).
- **Pointing IAM probe at auth-service (option 2)**: auth-service is the OIDC AS; if its full `/actuator/health` is `UP` in e2e, it is a valid IAM-domain health source — but confirm it does not ALSO carry a dead-Kafka indicator (same trap).
- **Finance card must stay ok**: finance-account-service is the one guaranteed-`ok` leg today (PC-FE-016); the IAM fix must be additive, not a global actuator change that breaks finance's probe.

# Failure Scenarios

- **Relaxing the spec to force green** — explicitly rejected: the drill-down is health-gated (`DomainCard.tsx:242`), so a relaxation would either still fail (drill-down absent) or assert nothing meaningful. The test must pass because IAM is genuinely `ok`. (Out of Scope captures this.)
- **Making Kafka reachable to fix health** — wrong layer: the e2e IAM services are intentionally Kafka-less; spinning up Kafka for them is scope creep and fragility. Fix the health *reporting*, not the dependency.
- **Green locally, red in nightly** — `:check` does not run the dockerized login + BFF probe; only the nightly does. AC-4 mandates confirming the actual nightly job, not a local proxy.
- **Global actuator change regresses finance/other domains** — AC-6 guards this; scope the health-config change to the IAM service(s) / e2e profile only.
- **Systemic: non-PR-gated nightly hid this for 11 days behind the login abort** — recorded, not fixed here; mitigation is treating post-merge nightly confirmation as part of console-overview changes' done-definition.

---

분석=Opus 4.8 / 구현 권장=Opus 4.8 (requires bringing up the dockerized e2e stack to confirm the admin-service `/actuator/health` non-`UP` reason, then a precise actuator-health-group / probe-target change without regressing the finance leg — the runtime confirmation + correct layer choice is the hard part; on this Windows host the e2e stack is heavy, so a dedicated session is recommended). 핵심 검증=머지 후 첫 nightly `Platform Console E2E` job 이 7/7 `success`.

---

## Static Analysis Notes (pre-runtime, 2026-06-13 session — paused before AC-1 Docker confirmation)

Worktree prepared: `mlab-pcfe071` on branch `task/pc-fe-071-iam-card-health-probe` (off origin/main `e40d4529d`). Docker e2e diagnosis deferred to a dedicated session (this host's e2e stack is heavy + OOM-cascade history). **Resume**: `git worktree list` → `cd ../mlab-pcfe071` → run AC-1 below first.

⚠️ **The task's "Kafka component DOWN" hypothesis is very likely WRONG** (verified statically — do NOT chase Kafka):
- admin-service has **zero custom `HealthIndicator`** (grep `HealthIndicator|implements Health` over `admin-service/src/main` = 0).
- **Spring Boot and spring-kafka do NOT auto-register any Kafka health indicator.** So a dead `KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9999` does **not** surface as a DOWN component in admin-service's `/actuator/health`. The Kafka client just logs warnings; health is unaffected.

**Most probable real cause = resilience4j circuitBreakers health indicator:**
- `admin-service/build.gradle`: `io.github.resilience4j:resilience4j-spring-boot3:2.2.0`.
- `admin-service/src/main/resources/application.yml` (base): `management.health.circuitbreakers.enabled: true` (+ a `circuitbreakers` health *group*, but the group does NOT remove the indicator from the main aggregate `/actuator/health`).
- admin-service wraps `@CircuitBreaker(accountService | securityService | authService)` on its outbound clients. If any of those downstream calls fail in the e2e stack → that circuit goes OPEN → `circuitBreakers` indicator DOWN → aggregate `/actuator/health` `status != UP` → BFF (`AbstractHealthReadAdapter` reads full health) classifies `iam` card `degraded`.

**AC-1 (do this FIRST in the dedicated session)** — bring up `projects/platform-console/docker-compose.e2e.yml`, then `curl -s http://localhost:<admin-mapped>/actuator/health` (admin-service container `:8085`; needs `management.endpoint.health.show-details` to see `components`). Identify the actual DOWN component(s) — expect `circuitBreakers`, NOT `kafka`. Also check whether the OPEN circuit is itself a real e2e gap (is account/security/auth-service reachable from admin in the overlay?).

**Fix candidate (re-scoped from the task's option 1)** — if DOWN == `circuitBreakers`: add to `projects/iam-platform/apps/admin-service/src/main/resources/application-e2e.yml` (currently has NO `management:` block — inherits base) a scoped override, e.g. `management.health.circuitbreakers.enabled: false` (admin-service e2e profile ONLY → finance leg untouched = AC-6 safe), OR resolve why the circuit is OPEN (a genuinely-missing downstream). Probe target / profile confirmed: e2e compose `CONSOLE_BFF_OUTBOUND_IAM_BASE_URL=http://admin-service:8085`, admin-service `SPRING_PROFILES_ACTIVE=e2e`. Keep console-web `src/` and the BFF composition byte-unchanged (AC-5).
