# Task ID

TASK-FAN-BE-041

# Title

ADR-MONO-058 D6 (fan-platform only) — switch community-service's `IamClientCredentialsTokenProvider`
from its local copy to the shared, already-fixed class in `libs/java-security`

# Status

done

# Owner

backend

# Task Tags

- code
- test

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

Close fan-platform's share of `ADR-MONO-058 § D6` (ACCEPTED 2026-07-30).

`§ D6` names 7 copies of `IamClientCredentialsTokenProvider` across 3 projects (iam, ecommerce, fan) as a
**live defect distributed unevenly**, not just duplication: `ecommerce/batch-worker`'s copy carries a
UTF-8-encoding fix for HTTP Basic credentials (RFC 7617) plus explicit connect/read timeouts that several
sibling copies lack, and `fan-platform/community-service`'s copy generalized the hardcoded `scope` into a
constructor parameter — "strictly better", per the ADR, and the shape `TASK-MONO-501` promotes **from**.

Measured against the tree (not the ADR's cross-project paraphrase): fan-platform's copy —
`community-service/src/main/java/com/example/fanplatform/community/infrastructure/membership/IamClientCredentialsTokenProvider.java`
— itself carries the exact defect D6 exists to close:

- `Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes())` — `.getBytes()` with
  **no charset argument** uses the JVM platform-default charset, not explicit UTF-8. This happens to work
  on most JVMs today but is not the RFC 7617-correct, guaranteed-safe form `ecommerce/batch-worker`'s copy
  uses.
- `RestClient.create()` — **no connect or read timeout configured at all**, the exact "hung downstream call
  blocks the calling thread indefinitely" risk `§ D7`'s sibling finding calls out for a different pattern,
  present here too.

So fan-platform's relationship to D6 is two-sided: it contributed the best shape for one dimension (scope
parameterization) while still carrying the defect on the other two dimensions (charset, timeouts) that
`TASK-MONO-501`'s canonical class fixes. This task closes the loop: once `TASK-MONO-501` lands the
canonical, already-fixed class in `libs/java-security`, community-service switches from its local copy to
the shared one, picking up the UTF-8 + timeout fixes while its own scope-parameterization contribution is
preserved (because it was already promoted into the canonical shape).

**Prerequisite (선행): `tasks/ready/TASK-MONO-500-adr058-d4-security-chain-assembly-promotion.md` is
unrelated; the actual prerequisite is
`tasks/ready/TASK-MONO-501-adr058-d6-iam-client-credentials-token-provider-promotion.md`, which is
**ready but NOT YET LANDED** as of this task's filing.** Do not start this task until `TASK-MONO-501`'s
Status reads `done` (verify by reading the file directly — an ADR/task status can go stale).

---

## Measured against the tree — what is actually duplicated inside fan-platform (not the ADR's paraphrase)

Repo-wide grep for `IamClientCredentialsTokenProvider` / `ClientCredentials` / `client_credentials` across
`projects/fan-platform/apps` found **exactly one** in-project copy:

| Service | File |
|---|---|
| `community-service` | `infrastructure/membership/IamClientCredentialsTokenProvider.java` (+ `IamClientCredentialsTokenProviderTest.java`, consumed by `HttpMembershipChecker` and wired via `MembershipCheckerAutoConfig`) |

`artist-service`, `membership-service`, `notification-service`, and `gateway-service` make **no** outbound
OAuth2 client-credentials call — community-service is the only fan-platform service that calls another
fan-platform service's `/internal/**` endpoint (membership-service's, per `TASK-FAN-BE-010`/`029`/`030`).

**This means the task's shape deviates from a literal "N copies converge" pattern**: there is nothing else
inside fan-platform to converge. The work is: delete community-service's local class, adopt the shared one.

---

# Scope

## In Scope

- Delete `community-service/src/main/java/com/example/fanplatform/community/infrastructure/membership/IamClientCredentialsTokenProvider.java`
  and its test `IamClientCredentialsTokenProviderTest.java`.
- Reconfigure `HttpMembershipChecker` and `MembershipCheckerAutoConfig` to construct/consume the shared
  class from `libs/java-security` (module already a declared dependency —
  `community-service/build.gradle` line 57 — no new `build.gradle` entry needed).
- Wire community-service's existing config keys into the shared class's constructor/builder unchanged:
  `iam.internal-client.token-uri` (default `http://iam.local/oauth2/token`), `iam.internal-client.client-id`
  (default `community-service-client`), `iam.internal-client.client-secret` (default `secret`),
  `iam.internal-client.scope` (default `membership.read`). Verify the shared class's constructor shape
  (finalized by `TASK-MONO-501`) actually accepts a parameterized `scope` — if it does not, that is a
  `TASK-MONO-501` defect, stop and report rather than working around it locally.
- Verify, by test, that the request community-service's adopted instance sends is **byte-equivalent** in
  meaning to what the deleted local copy sent: `grant_type=client_credentials&scope=membership.read`
  (URL-encoded), `Authorization: Basic <base64(clientId:clientSecret)>` — this is the exact request shape
  `TASK-FAN-BE-030` fixed after a live 403 regression; do not regress it.
- Positively verify the two defects are now actually closed for community-service's real outbound call —
  not merely that `libs/java-security`'s own unit tests (required by `TASK-MONO-501`) pass in isolation:
  - A community-service-level test asserting the Basic-auth header bytes are UTF-8-encoded for
    community's actual `client-id`/`client-secret` config values.
  - A community-service-level test or explicit constructor-parameter assertion that connect/read timeouts
    are configured (not silently absent) — pick a value; document why (matches
    `platform/testing-strategy.md`/service defaults if any exist, otherwise a documented, sane default).
- Preserve `HttpMembershipChecker`'s fail-closed contract exactly: token-acquisition failure → caught →
  deny (`false`), no silent fallback to allow. This is a Ownership-Rule / security-policy boundary that
  must stay in community-service regardless of which class acquires the token.
- One PR touching community-service only (shared `libs/java-security` is `TASK-MONO-501`'s scope, already
  landed by the time this task starts — this task does not touch the lib).

## Out of Scope

- `TASK-MONO-501` itself (the promotion) — must be `done` before this task starts.
- `iam-platform` and `ecommerce-microservices-platform`'s own D6 adoption — separate future tasks, filed
  in their own projects' `tasks/ready/` (`ADR-MONO-058 § 6` forbids a cross-project mega-PR).
- Any change to `HttpMembershipChecker`'s fail-closed decision logic, `MembershipCheckerAutoConfig`'s
  `@ConditionalOnMissingBean` bean-ordering mechanism (`TASK-FAN-BE-010`'s deliberate stub-always-loses-tie
  design), or the `AlwaysAllowMembershipChecker` stub.
- Any change to IAM-side (`community-service-client`'s registered scope, V0009 seed data).
- `ADR-MONO-058 § D1` (already done, `TASK-FAN-BE-040`) — `HttpMembershipChecker`'s own inbound auth is a
  different converter (`WorkloadIdentityAuthoritiesConverter`, membership-service side); this task only
  touches the **outbound** token-acquisition class.

---

# Acceptance Criteria

- [x] `TASK-MONO-501`'s Status confirmed `done` before this task's implementation starts (verify by reading
      the file, not by inference). — Read `tasks/done/TASK-MONO-501-...md` directly: `# Status` → `done`.
- [x] Community-service's local `IamClientCredentialsTokenProvider.java` and its test are deleted; repo-wide
      grep for `class IamClientCredentialsTokenProvider` under `projects/fan-platform/apps` → **0 hits**.
      — Both files deleted; `Grep "class IamClientCredentialsTokenProvider"` under
      `projects/fan-platform/apps` → 0 hits (confirmed post-change).
- [x] `HttpMembershipChecker` / `MembershipCheckerAutoConfig` construct and consume the shared
      `libs/java-security` class instead, with community-service's four config keys threaded through
      unchanged (same property names, same defaults). — `MembershipCheckerAutoConfig` gained a
      `@Bean IamClientCredentialsTokenProvider iamClientCredentialsTokenProvider(...)` factory method
      (shared class is a plain POJO, no `@Component`) threading `iam.internal-client.{token-uri,client-id,
      client-secret,scope}` through unchanged (same property names/defaults) plus two new keys for the
      class's now-required timeout params (`connect-timeout-ms`/`read-timeout-ms`, default 2000/3000ms,
      matching the sibling `community.membership-service.*` timeout convention already in the same file).
      `HttpMembershipChecker` itself is untouched (still consumes `RestClient` + `tokenProvider.currentBearer()`).
- [x] A test proves the Basic-auth header community-service's adopted instance sends is UTF-8-encoded (not
      merely "the shared class has its own UTF-8 test" — that proves the class, not this call site's use of
      it). — `CommunityIamTokenAcquisitionTest.sendsUtf8BasicAuthAndScope` asserts the header against
      `Base64.getEncoder().encodeToString("community-service-client:secret".getBytes(StandardCharsets.UTF_8))`
      for community's real config values, via MockWebServer.
- [x] A test or explicit assertion proves connect/read timeouts are configured for community-service's
      instance (no default that reproduces "no timeout at all"). — Two-pronged: (1)
      `CommunityIamTokenAcquisitionTest.zeroTimeoutRejected` proves `Duration.ZERO` for either timeout is
      rejected by construction; (2) `MembershipCheckerAutoConfigTest.zeroConnectTimeoutFailsContextRefresh`
      proves community's `iam.internal-client.connect-timeout-ms` property actually flows into the
      constructor (overriding it to `0` fails context refresh with the shared class's own
      `IllegalArgumentException`) — not silently dropped/hardcoded by the wiring.
- [x] `scope=membership.read` still flows through the token request unchanged — verified by a test, not
      assumed (regression target: the exact defect `TASK-FAN-BE-030` fixed). —
      `CommunityIamTokenAcquisitionTest.sendsUtf8BasicAuthAndScope` asserts the request body equals
      `grant_type=client_credentials&scope=membership.read` byte-for-byte.
- [x] `HttpMembershipChecker`'s fail-closed behavior (token-acquisition failure → deny) is verified
      unmodified — existing test(s) covering this pass without change to their assertions. —
      `HttpMembershipCheckerTest` (6 tests) untouched, all pass.
- [x] No `build.gradle` in fan-platform gains a new dependency (community-service already declares
      `libs:java-security`). — `community-service/build.gradle` unmodified (line 57 dependency was already
      present).
- [x] Test-count parity recorded (before/after, per `community-service`); no test lost. — See table below;
      138 → 140 (+2, all additive timeout-wiring assertions), 0 failures/errors/skipped both sides.
- [x] `./gradlew :community-service:check` GREEN; CI `Integration (fan-platform, Testcontainers)` lane
      GREEN is authoritative (local Windows Docker is not —
      `project_testcontainers_docker_desktop_blocker`). — Local `:community-service:check` GREEN
      (BUILD SUCCESSFUL, 140/140 tests, 0 failures); CI lane authoritative once PR opens.

## Before / After Test-Count Table (`community-service`, whole module)

| | tests | skipped | failures | errors |
|---|---|---|---|---|
| Before (baseline, `git stash` re-run) | 138 | 0 | 0 | 0 |
| After | 140 | 0 | 0 | 0 |

Delta: `IamClientCredentialsTokenProviderTest` (3 tests) deleted, replaced by
`CommunityIamTokenAcquisitionTest` (3 tests, adapted to the shared class's 6-arg constructor); `MembershipCheckerAutoConfigTest` gained 2 new tests (default-config bean construction + timeout-wiring proof). Net +2, no test lost.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D6, § 6 item 1
  (ACCEPTED 2026-07-30)
- `tasks/ready/TASK-MONO-501-adr058-d6-iam-client-credentials-token-provider-promotion.md` — **prerequisite,
  must be `done` before this task starts.** Read its Scope to know the exact shape (constructor/builder
  parameters) the promoted class exposes.
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (the tracking task this splits from)
- `platform/shared-library-policy.md` § Decision Rule, § Ownership Rule
- `platform/security-rules.md` § A verified token proves authentication, not authorization
- `projects/fan-platform/tasks/done/TASK-FAN-BE-010-community-http-membership-checker.md` — origin of this
  class (admin-service ADR-005 pattern), the `@ConditionalOnMissingBean` bean-ordering design, and the
  fail-closed contract this task must preserve
- `projects/fan-platform/tasks/done/TASK-FAN-BE-029-membership-workload-identity-positive-discriminator.md`
  and `…/TASK-FAN-BE-030-community-requests-membership-scope.md` — the live 403 regression and its fix
  (`scope=membership.read` must be present in the token request) that this adoption must not reopen
- `projects/fan-platform/tasks/done/TASK-FAN-BE-038-adr058-d2-error-envelope-shared-handler-adoption.md`,
  `…/TASK-FAN-BE-039-adr058-d5-public-paths-shared-value-type.md`,
  `…/TASK-FAN-BE-040-adr058-d1-actor-jwt-claim-cluster.md` — prior art for this project's
  `ADR-MONO-058` adoption-task governance shape (before/after test-count table, guard mutation-check,
  explicit statement of observable-behaviour deltas)
- `projects/fan-platform/specs/services/community-service/architecture.md`
- `projects/fan-platform/specs/services/membership-service/architecture.md` § the internal access-check
  endpoint this token authenticates against

---

# Related Contracts

None — this is an outbound OAuth2 client-credentials call community-service makes to IAM; no wire-format or
event contract this repo controls documents its internal shape. The one indirectly-related contract,
`projects/fan-platform/specs/contracts/http/membership-api.md`'s internal access-check section, describes
the **receiving** endpoint's shape (unaffected by this task) — read-only context.

---

# Target Service

- `community-service` (fan-platform)
- Consumes `libs/java-security` (already landed by `TASK-MONO-501`; not modified by this task)

---

# Architecture

Follow `community-service/architecture.md`. `IamClientCredentialsTokenProvider`'s call site
(`HttpMembershipChecker`) and its wiring (`MembershipCheckerAutoConfig`) stay in their current package
(`infrastructure.membership`) — only the token-provider class's origin changes (local → shared).

---

# Implementation Notes

- Read `TASK-MONO-501`'s actual landed shape first (constructor signature, builder API) before writing any
  adoption code — do not assume the interface described in `TASK-MONO-501`'s own ready-state task file is
  exactly what shipped; the promotion task explicitly reserves the right to reconcile against divergence
  found during its own implementation.
- The existing `IamClientCredentialsTokenProviderTest.java` (MockWebServer-based, per
  `TASK-FAN-BE-010`'s Verification notes) is a good template for the new call-site-level test — reuse its
  MockWebServer fixture rather than re-inventing one, adapting assertions to the shared class's actual
  API surface.
- `community-service`'s `@Value` defaults (`http://iam.local/oauth2/token`, `community-service-client`,
  `secret`, `membership.read`) must survive unchanged — they are the actual pre-seeded IAM `V0009` values
  this service's local demo/CI environment depends on.

---

# Edge Cases

- **Timeout value choice.** No connect/read timeout currently exists to preserve — this task is free to
  choose a value, but must not choose "unbounded"/absent, which is the exact defect being closed. If
  `TASK-MONO-501`'s shared class exposes a required (non-optional) timeout parameter, use it; if it exposes
  an optional one with a safe non-zero default, verify that default is actually non-zero rather than
  assuming.
- **Token caching semantics.** The deleted local copy cached the token with a 60-second refresh skew
  (`REFRESH_SKEW`). If the shared class's caching semantics differ, that is an observable behaviour change
  (more/fewer token requests under load) — note it explicitly in the PR body rather than silently absorbing
  it.
- **`scope` URL-encoding.** The deleted copy explicitly `URLEncoder.encode`s the scope value into the
  request body. Verify the shared class does the same (a scope value is unlikely to need encoding today,
  but silently dropping the encoding step would be a latent defect for a future scope value with special
  characters).

---

# Failure Scenarios

- **Reintroducing the platform-default-charset Basic-auth defect by not actually wiring the shared class's
  fixed encoding path.** If the adoption compiles and passes existing tests but the shared class's own
  UTF-8 fix is bypassed (e.g. because community-service pre-encodes the header itself and passes it as an
  opaque string), the defect is not actually closed — verify via the explicit UTF-8-encoding test required
  above, not by "it compiles."
- **Regressing the `scope=membership.read` fix (`TASK-FAN-BE-030`).** That was a live production 403 caused
  by a missing scope in the token request. If the shared class's constructor makes `scope` optional and
  community-service's wiring omits it, this task silently reopens that exact incident.
- **Weakening the fail-closed contract.** `HttpMembershipChecker` must still deny access on any
  token-acquisition failure. Swapping to a class whose exceptions propagate differently (e.g. a checked
  exception the call site doesn't catch, causing a 500 instead of a fail-closed deny) would be a behaviour
  regression on the authorization path.

---

# Test Requirements

- Call-site-level test(s) in `community-service` (reusing/adapting `IamClientCredentialsTokenProviderTest`'s
  MockWebServer fixture): token acquisition happy path against the shared class, UTF-8 Basic-auth header
  byte assertion for community's real config values, `scope=membership.read` present and URL-encoded in the
  request body, timeout configuration present.
- `HttpMembershipCheckerTest` (existing) passes unmodified — proof the fail-closed contract survives.
- Before/after test-count table for `community-service`, 0 failures/errors/skipped both sides.
- `./gradlew :community-service:check` GREEN. CI `Integration (fan-platform, Testcontainers)` GREEN
  authoritative.

---

# Definition of Done

- [x] `TASK-MONO-501` confirmed `done` before starting
- [x] Local `IamClientCredentialsTokenProvider` deleted; shared `libs/java-security` class adopted
- [x] UTF-8 encoding + timeout configuration verified in effect for community-service's real call, by test
- [x] `scope=membership.read` regression-tested
- [x] Fail-closed contract verified unmodified
- [x] Test-count parity recorded; `:community-service:check` GREEN locally (140/140, 0 failures) — CI
      Integration lane authoritative once PR opens
- [x] Ready for review
