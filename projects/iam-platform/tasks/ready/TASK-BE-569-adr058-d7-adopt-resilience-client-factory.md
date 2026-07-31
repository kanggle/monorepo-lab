# Task ID

TASK-BE-569

# Title

ADR-MONO-058 D7 — iam-platform adopts `ResilienceClientFactory.buildRestClient` for outbound HTTP client construction (`admin-service` ×5, `account-service` ×1)

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`ADR-MONO-058` (ACCEPTED 2026-07-30) § 2 D7 lists iam-platform (with console-bff, ecommerce) among the
`ResilienceClientFactory` non-adopters, and frames the risk as "several non-adopters have **zero read timeout** —
live production-risk gap". **That specific framing does not hold for iam-platform** — confirmed by reading every
outbound HTTP client in iam-platform's 5 services (2026-07-31): all 9 classes already carry explicit connect + read
timeouts. What *is* true, and is this task's actual scope: 6 of those 9 classes hand-roll, byte-for-byte, the exact
`HttpClient`+`JdkClientHttpRequestFactory`+HTTP/1.1-pin+`RestClient.builder()` construction sequence that
`libs/java-common.ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs)` already provides
— confirmed by reading the factory's own source
(`libs/java-common/src/main/java/com/example/common/resilience/ResilienceClientFactory.java`), which pins HTTP/1.1
for the identical reason (WireMock H2C `RST_STREAM` races, cited `TASK-BE-273`/`ADR-004`) each iam class's own
comment gives independently. This is pure, confirmed duplication of already-centralized code — a straightforward
adoption, not a defect closure.

**Inventory (2026-07-31 direct read) of every outbound HTTP client in iam-platform:**

| Service | Class | Construction | Timeouts | Resilience |
|---|---|---|---|---|
| auth-service | `AccountServiceClient` | `ResilienceClientFactory.buildRestClient` | 3000/5000ms | `ResilienceClientFactory.buildCircuitBreaker`/`buildRetry` — **already fully adopted** |
| auth-service | `AdminAssignmentClient` | `ResilienceClientFactory.buildRestClient` | 3000/5000ms | same — **already fully adopted** |
| account-service | `AuthServiceClient` | hand-rolled `JdkClientHttpRequestFactory` (HTTP/1.1 pin, explicit) | 3000/15000ms | `ResilienceClientFactory.buildCircuitBreaker`/`buildRetry` (partial — CB/retry adopted, RestClient construction not) |
| admin-service | `AccountServiceClient` | hand-rolled `JdkClientHttpRequestFactory` | 3000/10000ms | `@Retry`/`@CircuitBreaker` annotations (declarative, not the factory) |
| admin-service | `AuthServiceClient` | hand-rolled `JdkClientHttpRequestFactory` | 3000/10000ms | `@Retry`/`@CircuitBreaker` annotations |
| admin-service | `SecurityServiceClient` | hand-rolled `JdkClientHttpRequestFactory` | 3000/10000ms | `@Retry`/`@CircuitBreaker` annotations |
| admin-service | `AccountServiceTenantClient` | hand-rolled `JdkClientHttpRequestFactory` | 3000/10000ms | `@Retry`/`@CircuitBreaker` annotations |
| admin-service | `AccountServiceOrgNodeClient` | hand-rolled `JdkClientHttpRequestFactory` | 3000/**3000**ms (deliberately short — permission-check path, documented in its own javadoc) | `@CircuitBreaker` only, **no `@Retry`** (deliberate — see its javadoc) |
| security-service | `AccountServiceClient` | raw `java.net.http.HttpClient`, manual retry+jitter loop | connect from config, per-request `HttpRequest.timeout()` | fully custom, no Resilience4j at all |

---

# Scope

## In Scope

- Swap the hand-rolled `HttpClient.newBuilder().version(HTTP_1_1)...` + `JdkClientHttpRequestFactory` + `RestClient.builder()`
  block in **6 classes** for a single call to `ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs)`:
  - `admin-service`: `AccountServiceClient`, `AuthServiceClient`, `SecurityServiceClient`,
    `AccountServiceTenantClient`, `AccountServiceOrgNodeClient`
  - `account-service`: `AuthServiceClient`
- Preserve every class's **existing, already-configured timeout values verbatim** — in particular
  `AccountServiceOrgNodeClient`'s deliberately short 3000/3000ms (its own javadoc explains why: a slow
  account-service must time the permission check out CLOSED, not sit on the 10s sibling default). Do not normalize
  timeout values across classes as a side effect of this refactor.
- Preserve each class's existing resilience-decision layer unchanged — the `@Retry`/`@CircuitBreaker`
  annotation-based approach in the 5 admin-service classes, and the existing
  `ResilienceClientFactory.buildCircuitBreaker`/`buildRetry` calls already present in account-service's
  `AuthServiceClient`. This task touches **only** the `RestClient`/`HttpClient` construction mechanism.

## Out of Scope

- `auth-service`'s `AccountServiceClient`/`AdminAssignmentClient` — already fully adopted, no change.
- `security-service`'s `AccountServiceClient` — architecturally a different pattern (raw `java.net.http.HttpClient`
  with a manual retry+jitter loop, zero Resilience4j usage). Migrating it to `ResilienceClientFactory` would replace
  hand-tuned jitter/backoff with the factory's exponential-random backoff and *add* a circuit breaker where none
  exists today — a materially larger behavior change than the other 6 classes' pure construction-mechanism swap.
  Flag as a candidate follow-up task if the team wants full fleet-wide consistency later; do not fold it into this
  task's "mechanical, no-behavior-change" scope.
- Any change to the `@Retry name=`/`@CircuitBreaker name=` values or the Resilience4j config keys backing them in
  `application.yml` (e.g. `resilience4j.circuitbreaker.instances.accountService.*`).
- D6 (`IamClientCredentialsTokenProvider` promotion/adoption) — filed separately as
  `tasks/ready/TASK-BE-568-adr058-d6-adopt-iam-client-credentials-token-provider.md`. Several of the same 6 classes
  are touched by both tasks (they consume `IamClientCredentialsTokenProvider` as a constructor parameter) — keep the
  two changes in separate PRs/commits so either can be reviewed and reverted independently; if implemented in the
  same worktree, sequence D6 before D7 or vice versa deliberately rather than interleaving edits to the same
  constructor in one hunk.
- EventDedupePort adoption (the other named-but-separate half of the ADR's D7) — not present as an un-adopted gap in
  iam-platform per this task's own investigation scope (not re-audited here; if a future sweep finds a gap, file it
  separately).

---

# Acceptance Criteria

- [ ] **AC-0 (re-verify gate).** At pickup time, re-read each of the 6 target classes and confirm they still
      hand-roll `JdkClientHttpRequestFactory` construction with the timeout values in the inventory table above. If
      any class has already been touched (adopted, refactored, or timeout values changed) since this task was filed,
      re-scope against the current code rather than this table.
- [ ] **AC-1.** All 6 classes' constructors replace their hand-rolled `HttpClient`/`JdkClientHttpRequestFactory`/
      `RestClient.builder()` block with `ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs)`.
- [ ] **AC-2.** Each class's effective connect/read timeout values are byte-identical before and after (verified by
      a test or explicit before/after property-value comparison in the PR body) — `AccountServiceOrgNodeClient`'s
      3000/3000ms in particular must not silently become 3000/10000ms.
- [ ] **AC-3.** No `@Retry`/`@CircuitBreaker` annotation, config name, or `ResilienceClientFactory.buildCircuitBreaker`/
      `buildRetry` call is touched — diff confined to the `RestClient`/`HttpClient` construction lines and the now-unused
      imports (`java.net.http.HttpClient`, `org.springframework.http.client.JdkClientHttpRequestFactory`, `java.time.Duration`
      where no longer needed).
- [ ] **AC-4.** HTTP/1.1 pinning behavior is preserved (inherent in `ResilienceClientFactory.buildRestClient` — verify
      by reading its source, not by re-implementing a separate pin).
- [ ] **AC-5.** Existing tests for all 6 classes (unit + any WireMock-backed slice/integration tests) pass unchanged —
      no test assertion updated, since this is a pure construction-mechanism refactor with no intended behavior change.
- [ ] **AC-6.** `./gradlew :projects:iam-platform:apps:admin-service:test :projects:iam-platform:apps:account-service:test`
      GREEN at baseline counts (measured at pickup time).
- [ ] **AC-7.** `security-service`'s `AccountServiceClient` is untouched — confirmed via `git diff --stat`.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus `rules/domains/saas.md` and `rules/traits/{transactional,regulated,audit-heavy,integration-heavy,multi-tenant}.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D7, § 6 item 2
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` — the split-origin tracking task
- `libs/java-common/src/main/java/com/example/common/resilience/ResilienceClientFactory.java` — the target factory
  (already exists, no promotion task needed for D7 per the ADR's own § 2 D7: "no new code, close the adoption gap")
- `platform/shared-library-policy.md`
- `projects/iam-platform/specs/services/{admin-service,account-service}/architecture.md`

---

# Related Contracts

- None — outbound HTTP client construction only, no wire-format or event contract change. The internal HTTP contracts
  these 6 clients call (`specs/contracts/http/internal/admin-to-account.md`, `admin-to-auth.md`,
  `security-to-account.md` and equivalents) are unaffected.

---

# Target Service

- `admin-service`, `account-service` (iam-platform)

---

# Architecture

Follow:

- `projects/iam-platform/specs/services/admin-service/architecture.md`
- `projects/iam-platform/specs/services/account-service/architecture.md`

---

# Implementation Notes

- `ResilienceClientFactory.buildRestClient` signature: `buildRestClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs)`
  (and a `Duration`-typed overload) — a direct drop-in for every one of the 6 classes' existing
  `@Value("...:3000")`/`@Value("...:10000")`-style constructor parameters; no property renaming needed.
- The factory's HTTP/1.1 pin exists for the identical documented reason each iam class's own comment already gives
  (WireMock H2C `RST_STREAM` race under Linux epoll, `TASK-BE-273`/`ADR-004`) — this is not a coincidence, it is the
  same fix independently re-derived and re-implemented 6 times; adopting the factory removes the duplication without
  changing the reasoning.
- `AccountServiceOrgNodeClient`'s constructor already takes its read timeout from a *different* property key
  (`admin.org-node.read-timeout-ms`, default 3000) than its 4 admin-service siblings
  (`admin.downstream.read-timeout-ms`, default 10000) — preserve this distinct property key; do not consolidate it
  into the shared `admin.downstream.*` key as part of this refactor (that would be a separate, riskier behavior
  change requiring its own review of the fail-fast rationale in that class's javadoc).

---

# Edge Cases

- Some of the 6 classes have a test-only secondary constructor (mirroring the pattern seen in `auth-service`'s
  already-adopted `AccountServiceClient`/`AdminAssignmentClient`, which pin a `RestClient` directly for
  `ContextCache`-shared-context test scenarios) — if any of the 6 target classes has an equivalent, ensure that
  constructor's direct `ResilienceClientFactory.buildRestClient` call (or pinned `RestClient`) is updated
  consistently with the primary constructor, not left on the old hand-rolled path.
- `AccountServiceOrgNodeClient` is the one class among the 6 that intentionally has no `@Retry` — verify the refactor
  doesn't add one by copy-paste from a sibling class.

---

# Failure Scenarios

- **Timeout normalization regression.** Copy-pasting one class's `ResilienceClientFactory.buildRestClient(...)` call
  into another without carrying its own specific timeout values would either widen `AccountServiceOrgNodeClient`'s
  deliberate fail-fast window (an authorization-latency correctness issue on the permission-check path, per its own
  javadoc) or shrink another client's timeout below what its downstream call actually needs. AC-2 exists specifically
  to catch this.
- **Resilience-layer scope creep.** Converting the 5 admin-service classes' `@Retry`/`@CircuitBreaker` annotations to
  `ResilienceClientFactory.buildCircuitBreaker`/`buildRetry()` "while touching the file anyway" is explicitly out of
  scope — that is a separate, larger behavioral-equivalence question (annotation-based AOP vs. programmatic
  decoration have different failure-visibility and testing characteristics) that deserves its own task and review if
  ever pursued.
- **Silently sweeping in `security-service`.** Its `AccountServiceClient` looks similar at a glance but is a
  fundamentally different implementation (raw `HttpClient`, manual retry/jitter, no circuit breaker) — treating it as
  "just another hand-rolled RestClient" and mechanically converting it would add circuit-breaker behavior that
  doesn't exist today, which is a behavior change this task's AC set (built around "no intended behavior change")
  cannot honestly claim to have verified as safe. AC-7 exists to catch an accidental touch.

---

# Test Requirements

- Existing unit/WireMock-backed tests for all 6 target classes must pass unchanged — no new test file required for a
  pure construction-mechanism refactor. If any of the 6 classes currently lacks a construction-level smoke test
  (e.g. "the client actually enforces its configured read timeout"), consider adding one, but this is not required
  to satisfy AC-5/AC-6.

---

# Definition of Done

- [ ] 6 classes' `RestClient` construction switched to `ResilienceClientFactory.buildRestClient`
- [ ] Timeout values verified byte-identical per class (AC-2)
- [ ] No resilience-annotation/config changes (AC-3)
- [ ] `security-service` untouched (AC-7)
- [ ] `admin-service`/`account-service` test suites GREEN at baseline counts
- [ ] Task moved to `done`, referencing `TASK-MONO-495`

---

# Provenance

Filed while splitting `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` into per-project adoption
tasks for iam-platform, per that task's own Acceptance Criteria. The ADR's § 2 D7 framing ("several non-adopters have
zero read timeout") was checked directly against iam-platform's code (2026-07-31, see the inventory table in Goal)
and found **not to apply here** — every iam outbound client already has explicit timeouts. This task's scope was
narrowed accordingly to the actually-confirmed duplication (hand-rolled `RestClient` construction boilerplate that
`ResilienceClientFactory.buildRestClient` already centralizes), not the ADR's original framing of the risk.

See `tasks/ready/TASK-BE-568-adr058-d6-adopt-iam-client-credentials-token-provider.md`'s own Provenance section for
the D1 (not applicable) and D2 (already adopted) findings from the same investigation.

분석=Sonnet 5 / 구현 권장=Sonnet 5 (mechanical construction-layer refactor across 6 classes in 2 services, no
resilience-behavior change).
