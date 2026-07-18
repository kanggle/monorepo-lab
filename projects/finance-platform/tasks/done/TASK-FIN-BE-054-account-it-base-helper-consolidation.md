# TASK-FIN-BE-054 — account-service: consolidate integration-test helpers into AbstractAccountIntegrationTest

- **Type**: TASK-FIN-BE (test refactor — behaviour-preserving)
- **Status**: done
- **Service**: account-service (finance-platform)
- **Domain/traits**: saas / [transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (multi-file behaviour-preserving refactor)

## Goal

Remove copy-paste duplication across the account-service integration tests by hoisting shared
fixtures/helpers into the already-existing `AbstractAccountIntegrationTest` base (which every IT
subclass extends). **No test method bodies, assertions, coverage, or semantics change** — the same
ITs run, they just stop redefining shared scaffolding.

Consolidations (from the 2026-07-19 test audit):

1. **`ActorContext` constants** — `HOLDER` (`new ActorContext("user-1", TENANT_FINANCE, Set.of())`)
   and `OPERATOR` (`new ActorContext("op-1", TENANT_FINANCE, Set.of("OPERATOR"))`) are redefined
   byte-identically in 5 subclasses (AccountLifecycle / AuditAndImmutability / IdempotencyConcurrency
   / AccountOutboxRelay / OwnerRefEncryptionAtRest). Move to `AbstractAccountIntegrationTest` as
   `protected static final`.
2. **KYC-open helper** — the "open account → upgrade KYC to FULL" sequence appears as two near-identical
   private helpers (`openActiveFullKyc` / `active`) plus one inline copy. Hoist ONE
   `protected` helper to the base taking the `AccountApplicationService`. The non-asserted audit-reason
   string literal that currently differs is not load-bearing — pick one.
3. **JWT-signing mechanics** — `CrossTenantHttpIntegrationTest` and `ScopeEnforcementHttpIntegrationTest`
   each reinvent RSA-key-gen + JWS-sign + serialize + `@BeforeAll publishJwks`. Hoist ONLY the signing
   mechanics (one shared `RSA_KEY`, a `protected String token(Consumer<JWTClaimsSet.Builder>)` helper,
   and the JWKS publish) — mirroring how the sibling `AbstractLedgerIntegrationTest` already does it.
   **Keep each subclass's claim-set construction local** — the two files' claim shapes have genuinely
   diverged (Cross-tenant fixes roles/scope; ScopeEnforcement parameterises them). Do NOT merge claims.

## Scope

- **In scope**: `AbstractAccountIntegrationTest` + its IT subclasses under
  `account-service/src/test/java/com/example/finance/account/integration/`.
- **Out of scope**: production code (zero change); the `*UnitTest` naming-convention question
  (finance-wide, separate ticket); the `GlobalExceptionHandler → CommonGlobalExceptionHandler`
  production dedup (separate ticket, design decision); ledger-service (TASK-FIN-BE-055).

## Acceptance Criteria

- **AC-1**: `HOLDER` / `OPERATOR` `ActorContext` constants live once in `AbstractAccountIntegrationTest`;
  no subclass redefines them.
- **AC-2**: the KYC-open sequence exists as one base helper; the 2 private copies + 1 inline copy are gone.
- **AC-3**: RSA key + `token(...)` signing helper + JWKS publish live in the base; the two HTTP+JWT ITs
  use them; each keeps its own claim-set construction (divergent claims NOT merged).
- **AC-4**: `./gradlew :account-service:compileTestJava` is clean; the IT suite is unchanged in count.
- **AC-5**: no production-code change; behaviour/coverage identical (verified by CI's
  `Integration (finance-platform, Testcontainers)` lane, which is authoritative — local Windows
  Testcontainers is flaky and NOT authoritative).

## Edge Cases / Failure Scenarios

- The base already owns the SINGLE `jwk-set-uri` `@DynamicPropertySource` registration (documented in
  its Javadoc — a prior cross-tenant IT failed from a duplicate registration). The hoisted JWKS-publish
  helper must reuse that single registration, NOT add a second.
- Do NOT force a single JWT claim shape — Cross-tenant vs ScopeEnforcement claims are intentionally
  different; only the signing mechanics are shared.

## Related

- **TASK-FIN-BE-055** (the ledger sibling of this consolidation).
- Memory: `feedback_repo_knows_what_it_does_not_say` (dedup only what has NOT diverged — claims stay
  local), `project_console_web_godfile_split_series` (behaviour-preserving refactor discipline).
