# TASK-MONO-429 — libs/java-gateway: add testFixtures with shared JWT builder + RecordingGatewayFilterChain

- **Type**: TASK-MONO (monorepo-level — shared library change + one project adaptation, atomic)
- **Status**: done
- **Shared path**: `libs/java-gateway` (+ `finance-platform/apps/gateway-service` test adaptation)
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (build wiring + multi-file test refactor)

## Goal

Collapse duplicated gateway filter-test scaffolding by introducing a `testFixtures` source set in
`libs/java-gateway` exposing two shared fixtures, then migrating the copies. **No production code, no
behaviour change** — same tests run.

Consolidations (from the 2026-07-19 test audit):

1. **`RecordingGatewayFilterChain`** — a `GatewayFilterChain` test-double exists as ~6 near-identical
   private copies: 2 in `finance-platform/apps/gateway-service` tests (`GatewayEdgeFilterTest`
   captures the exchange; `RoleAdmissionFilterTest` captures a `called` boolean) and 4 in
   `libs/java-gateway`'s OWN test suite (`IdentityHeaderStripFilterTest`, `JwtHeaderEnrichmentFilterTest`,
   `RequestIdFilterTest`, and the lib's `RoleAdmissionFilterTest`). Provide ONE
   `RecordingGatewayFilterChain` in testFixtures exposing both `wasCalled()` and `capturedExchange()`;
   migrate all 6.
2. **JWT test builder** — `GatewayEdgeFilterTest.jwt(Map)` (RS256, +60s) and
   `TenantClaimValidatorTest.jwt(...)` (same shape, claim-specific signature) and
   `RoleAdmissionFilterTest.jwt(Map)` (alg="none", +300s) are three copies. Provide a
   `jwt(String alg, Duration ttl, Map<String,Object> claims)` (default alg=RS256, ttl=60s) in
   testFixtures; migrate the 3 gateway-service copies, each passing only its deltas. **Keep the alg /
   ttl deltas as parameters** — `RoleAdmissionFilterTest` deliberately uses an unsigned `alg="none"`
   token; do NOT force RS256 on it.

## Scope

- **In scope**: `libs/java-gateway/build.gradle` (add `java-test-fixtures` plugin / testFixtures
  sourceSet — mirror `libs/java-test-support`), new `libs/java-gateway/src/testFixtures/...` fixtures,
  the lib's own 4 filter tests, and `finance-platform/apps/gateway-service` tests
  (`GatewayEdgeFilterTest`, `RoleAdmissionFilterTest`, `TenantClaimValidatorTest`) — add the
  `testFixtures(project(":libs:java-gateway"))` test dependency to gateway-service if not present.
- **Out of scope**: production code; other projects' gateways (this fixture is gateway-lib-generic but
  only finance's gateway-service is migrated here — other services adopt on-demand later);
  `RateLimitKeyTest` @Nested split (cosmetic, separate/optional).

## Acceptance Criteria

- **AC-1**: `libs/java-gateway` has a `testFixtures` sourceSet with `RecordingGatewayFilterChain`
  (exposing `wasCalled()` + `capturedExchange()`) and the parameterized `jwt(...)` builder.
- **AC-2**: all 6 `CapturingChain`/`RecordingGatewayFilterChain` copies (2 gateway-service + 4 lib) are
  replaced by the shared fixture; all 3 gateway-service `jwt()` copies use the shared builder (deltas
  as params — `alg="none"` preserved for `RoleAdmissionFilterTest`).
- **AC-3**: `./gradlew :libs:java-gateway:test :gateway-service:test` GREEN; `@Test` counts unchanged.
- **AC-4**: `./gradlew :libs:java-gateway:build` GREEN (the new sourceSet must not break the lib's own
  build/publish); no production-code change.

## Edge Cases / Failure Scenarios

- The `testFixtures` sourceSet must depend on the SAME `spring-cloud-gateway` / reactor types the
  filters use (`GatewayFilterChain`, `ServerWebExchange`, `Jwt`) — add the compile deps to the
  `testFixturesImplementation` configuration, or the fixture won't compile.
- Do NOT flatten the JWT alg/ttl deltas — `RoleAdmissionFilterTest` needs an unsigned token; a shared
  builder that always signs RS256 would change what that test exercises.
- Confirm `libs/java-test-support` is the pattern to mirror for the `java-test-fixtures` plugin wiring.

## Related

- **TASK-FIN-BE-054/055** (the sibling IT-base consolidations — same "hoist shared test infra" theme,
  but those were per-service; this one is genuinely shared gateway-lib infra, so it lives in the lib).
- `platform/shared-library-policy.md` (the fixture is gateway-generic — no finance-specific content in
  the lib, so it is a legitimate shared-lib fixture, not a HARDSTOP-03 violation).
- Memory: `feedback_repo_knows_what_it_does_not_say` (dedup only genuinely-shared infra), `feedback_recount_population_dont_inherit_scope` (recount the 6 chain copies against the actual files before migrating).
