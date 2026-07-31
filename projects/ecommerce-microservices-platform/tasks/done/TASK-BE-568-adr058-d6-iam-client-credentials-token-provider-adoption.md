# Task ID

TASK-BE-568

# Title

ADR-MONO-058 D6 — adopt the promoted canonical `IamClientCredentialsTokenProvider` in `product-service` (closes the UTF-8/timeout defect)

# Status

done

# Owner

backend

# Task Tags

- code
- bugfix
- security

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

**Prerequisite (blocking): `tasks/ready/TASK-MONO-501-adr058-d6-iam-client-credentials-token-provider-promotion.md`
must land first.** That root task promotes a canonical, already-fixed
`IamClientCredentialsTokenProvider`-equivalent class to `libs/java-security`. This task
cannot start until `TASK-MONO-501` is `done` and the canonical class exists on
`libs/java-security`'s classpath — verify this directly (read the task's Status field
and confirm the class is present in `libs/java-security`) before beginning, not by
assuming it has landed because time has passed.

`ADR-MONO-058` § 2 D6 found 7 copies of `IamClientCredentialsTokenProvider` across 3
projects, already diverged in a way that matters: `ecommerce/batch-worker`'s copy
(`apps/batch-worker/src/main/java/com/example/batch/infrastructure/client/IamClientCredentialsTokenProvider.java`)
carries two fixes —

1. **UTF-8 Basic-auth encoding** (RFC 7617): `Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8))`
2. **Explicit connect/read timeouts**: built via `RestClients.timed(Duration.ofSeconds(5), Duration.ofSeconds(5))`

— while `ecommerce/product-service`'s copy
(`apps/product-service/src/main/java/com/example/product/infrastructure/client/AccountServiceSellerProvisioner.java`'s
inner `IamClientCredentialsTokenProvider`) still has **both** defects: it encodes the
Basic-auth header with the platform-default charset
(`.getBytes()`, no explicit `Charset` argument) and builds its `RestClient` with
`RestClient.create()` — **zero timeout of any kind** on the token-acquisition call.
A hung IAM token endpoint would block `product-service`'s `synchronized currentBearer()`
indefinitely, and on any non-UTF-8-default JVM/locale the Basic-auth header would be
silently wrong.

After this task, `product-service` uses the shared canonical class from
`libs/java-security` instead of its own copy, closing both defects at once instead of
patching them in place.

---

# Scope

## In Scope

- Remove `product-service`'s local `IamClientCredentialsTokenProvider` class
  (currently at `apps/product-service/src/main/java/com/example/product/infrastructure/client/IamClientCredentialsTokenProvider.java`).
- Wire `AccountServiceSellerProvisioner` (the sole consumer — constructor-injects
  `IamClientCredentialsTokenProvider tokenProvider`) to the promoted
  `libs/java-security` class instead, passing product-service's existing config keys
  (`iam.internal-client.token-uri`, `iam.internal-client.client-id` default
  `product-service-client`, `iam.internal-client.client-secret`) into whatever
  constructor/builder shape `TASK-MONO-501` lands (verify the actual shape once that
  task is done — do not assume it matches product-service's current constructor
  signature).
- Add explicit connect/read timeout configuration for product-service's token
  acquisition (currently absent — see Goal). Use the same 5s/5s default
  `batch-worker` uses unless product-service's own IAM latency profile calls for a
  different value (no evidence found requiring a different value; default to parity
  with `batch-worker` unless the promoted class forces a different config shape).
- Add `libs/java-security` as a `build.gradle` dependency to `product-service` if not
  already present (verify — `product-service` almost certainly already depends on
  `libs/java-security` for other JWT/tenant-claim machinery; confirm rather than
  assume).
- Update/add a unit test on product-service's side confirming the wired provider now
  produces a UTF-8-encoded Basic-auth header (regression guard local to this service,
  in addition to the canonical class's own UTF-8 unit test landed by `TASK-MONO-501`).

## Out of Scope

- `ecommerce/batch-worker` — already has the fixed shape; adopting the shared class
  there is optional cleanup (removes its now-duplicated `RestClients.timed` helper
  usage for this one call site) but is **not required** to close any defect, since
  batch-worker's copy is already correct. If picked up, treat as a follow-on, not
  part of this task's Acceptance Criteria.
- `iam-platform`'s and `fan-platform`'s copies — separate projects, separate
  per-project adoption tasks (per `ADR-MONO-058 § 6` item 1: "7 per-service adoption
  tasks (or fewer if some are bundled per-project)" — this task is ecommerce's slice
  only).
- `TASK-MONO-501` itself — the promotion work belongs to that task, not this one; if
  it is not yet `done`, this task remains blocked (see Goal).
- Any change to `AccountServiceSellerProvisioner`'s business logic (seller
  provisioning, lock, deactivate flows) beyond the token-provider wiring swap.

---

# Acceptance Criteria

- [x] `TASK-MONO-501` confirmed `done` before implementation starts (verify by
      reading the task file's Status, not by assumption). — Confirmed: file lives at
      `tasks/done/TASK-MONO-501-...md` with DoD box "Canonical class landed in
      `libs/java-security`" checked; class read directly at
      `libs/java-security/src/main/java/com/example/security/oauth2/client/IamClientCredentialsTokenProvider.java`.
- [x] `product-service`'s local `IamClientCredentialsTokenProvider` class is deleted;
      `AccountServiceSellerProvisioner` is wired to the `libs/java-security` canonical
      class. — Local file removed; `AccountServiceSellerProvisioner` now imports
      `com.example.security.oauth2.client.IamClientCredentialsTokenProvider`; new
      `IamTokenProviderConfig` (`infrastructure/config`) supplies the bean via
      `@Bean` factory method (the shared class has no `@Component`, per its own
      framework-neutral-POJO contract).
- [x] product-service's outbound IAM token calls now use UTF-8 Basic-auth encoding
      (verified by a unit test asserting the byte-level encoding, mirroring
      `batch-worker`'s existing fix / `TASK-MONO-501`'s canonical-class test). —
      `IamTokenProviderConfigTest#basicAuthHeaderIsUtf8Encoded` asserts the actual
      WireMock-observed `Authorization` header equals the UTF-8-encoded value and
      differs from the ISO-8859-1 one, using non-ASCII client id/secret.
- [x] product-service's outbound IAM token calls now have explicit connect/read
      timeouts (verified — no more `RestClient.create()`/unbounded call). — New
      `iam.internal-client.connect-timeout-ms`/`read-timeout-ms` config keys (default
      5000/5000, parity with `batch-worker`); `IamTokenProviderConfigTest#readTimeoutIsHonored`
      proves a 300ms read timeout fails fast against a 5s-delayed stub.
- [x] `AccountServiceSellerProvisioner`'s existing seller-provisioning tests remain
      GREEN (fail-soft try/catch behavior, D3 stance per its own javadoc, must be
      unaffected). — `AccountServiceSellerProvisionerTest` unchanged apart from the
      import, still mocks the token provider; all 12 tests GREEN.
- [x] `./gradlew :projects:ecommerce-microservices-platform:apps:product-service:test`
      GREEN. — `BUILD SUCCESSFUL`, full product-service suite (incl. the 3 new
      `IamTokenProviderConfigTest` cases) 0 failures.
- [x] No behavior change to product-service's `internal-client.client-id` default
      (`product-service-client`) or any other existing config key's default value,
      unless the promoted class's shape requires a key rename — if so, update
      `application.yml` and any deployment config consistently, and note the rename
      explicitly in this task's implementation. — No rename: `iam.internal-client.token-uri`/
      `client-id`(default unchanged `product-service-client`)/`client-secret` and
      `iam.account-service.base-url`/`iam.downstream.*`/`iam.seller.role` all preserved
      byte-for-byte. Only *new* keys added (`iam.internal-client.connect-timeout-ms`/
      `read-timeout-ms`, absent before this task since the local copy had no timeout at all).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read
> `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and
> `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a
> Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2
  D6, § 6 item 1
- `tasks/ready/TASK-MONO-501-adr058-d6-iam-client-credentials-token-provider-promotion.md`
  (**hard prerequisite** — the canonical class this task adopts)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (origin)
- `specs/services/product-service/architecture.md` § seller provisioning / ADR-MONO-042

---

# Related Contracts

- None — this is an outbound OAuth2 client-credentials call to IAM's internal token
  endpoint, not an inbound API this project publishes. No wire-format change to any
  published contract.

---

# Target Service

- `product-service`

---

# Architecture

Follow:

- `specs/services/product-service/architecture.md`

---

# Implementation Notes

- The consumer is a single class: `AccountServiceSellerProvisioner`
  (`apps/product-service/src/main/java/com/example/product/infrastructure/client/AccountServiceSellerProvisioner.java`).
  Its javadoc already documents the ADR-005/ADR-MONO-042 lineage of this token
  provider — update that javadoc's "MIRRORS admin-service `AccountServiceClient`"
  framing once this class is no longer a local mirror but a direct consumer of the
  shared library class.
- `batch-worker`'s already-fixed copy
  (`apps/batch-worker/src/main/java/com/example/batch/infrastructure/client/IamClientCredentialsTokenProvider.java`)
  is the concrete before/after reference for what "fixed" looks like — its `W-1`/`W-2`
  code comments name the exact two defects this task closes for product-service.
- product-service's `AccountServiceSellerProvisioner` constructor already receives
  `connect-timeout-ms`/`read-timeout-ms` config values (`iam.downstream.connect-timeout-ms`,
  `iam.downstream.read-timeout-ms`) for its *own* outbound `RestClient` to
  account-service — note these are a **different** HTTP call (the actual
  provisioning call) from the token-acquisition call this task fixes; do not conflate
  the two config namespaces when wiring the new timeout config for the token
  provider itself.

---

# Edge Cases

- If `TASK-MONO-501`'s landed canonical class has a materially different constructor
  shape than expected (e.g. requires a `Clock` or a different scope-parameterization
  convention per that task's Scope, which generalizes `scope` into a constructor
  parameter following `fan-platform/community-service`'s shape) — product-service's
  IAM token endpoint may not need a `scope` parameter at all (grep shows no `scope`
  usage in product-service's current token request body, just
  `grant_type=client_credentials`). Confirm whether the promoted class makes `scope`
  optional/nullable before assuming a mandatory parameter breaks product-service's
  call.
- If product-service's IAM token endpoint has stricter latency requirements than
  batch-worker's 5s/5s default (unlikely — both call the same IAM `/oauth2/token`
  endpoint), adjust the timeout values accordingly rather than blindly copying
  batch-worker's constants.

---

# Failure Scenarios

- Adopting the shared class but keeping product-service's own un-fixed encoding via a
  local override or wrapper would defeat the purpose of this task — verify the actual
  Basic-auth bytes sent, not just that the class compiles against the new type.
- If `TASK-MONO-501` has NOT actually landed and this task proceeds anyway (assuming
  the canonical class exists), the build will fail to resolve
  `libs/java-security`'s new class — treat any such failure as confirmation to stop
  and re-check the prerequisite, not as a reason to fall back to re-implementing a
  local fixed copy (which would recreate the exact "N separate defect-fix tasks"
  outcome the ADR's D6 promotion exists to avoid).

---

# Test Requirements

- Unit test: `AccountServiceSellerProvisioner`'s token-provider wiring produces a
  UTF-8-encoded Basic-auth header (byte-level assertion, not just "request succeeds").
- Unit test or integration test confirming a timeout is honored (either by asserting
  the configured `RestClient`'s request factory carries the expected timeout values,
  mirroring `TASK-MONO-501`'s own timeout-propagation test, or a Testcontainers-style
  hung-endpoint test if the existing test suite already has that pattern available).
- Existing `AccountServiceSellerProvisioner` fail-soft behavior tests (provision/lock/
  deactivate swallow-on-failure) remain GREEN, unchanged.

---

# Definition of Done

- [x] `TASK-MONO-501` confirmed done before starting
- [x] product-service's local `IamClientCredentialsTokenProvider` removed
- [x] `AccountServiceSellerProvisioner` wired to the shared `libs/java-security` class
- [x] UTF-8 encoding + timeout defects closed and verified by tests
- [x] `./gradlew :projects:ecommerce-microservices-platform:apps:product-service:test` GREEN
- [x] Ready for review
