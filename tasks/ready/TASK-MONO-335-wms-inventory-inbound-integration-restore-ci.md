# TASK-MONO-335 — Restore + CI-wire the two dormant WMS integration suites (inventory-service, inbound-service)

- **Status**: ready
- **Level**: monorepo (shared `.github/workflows/`) + wms-platform adaptation (atomic)
- **Type**: CI reliability / dormant-test restoration
- **Analysis model**: Opus 4.8 / **구현 권장**: Opus (multi-module dormant restore + flake-sensitive lane design)

## Goal

Two WMS integration suites are **silently dormant** — they compile (they sit in
the `test` sourceSet, so `:check` compiles them) but run in **no CI lane**, and
they cannot even load their Spring context:

| Service | `@Tag("integration")` IT | Context-load blocker | CI wiring |
|---|---|---|---|
| `inventory-service` | 7 (`AdjustmentTransfer`, `FlywayMigration`, `MasterLocationConsumer`, `MasterLocationDltRouting`, `PutawayCompletedConsumer`, `PickingFlow`, `AdminSettingsConsumer`) | `SecurityConfig` has no `@ConditionalOnWebApplication` | none |
| `inbound-service` | 1 (`PutawayLifecycle`) | same | none |

Both services' `SecurityConfig` declares `@Bean SecurityFilterChain
securityFilterChain(HttpSecurity http, …)`. Their IT base runs
`@SpringBootTest(webEnvironment = NONE)`, under which no servlet web context —
and therefore no `HttpSecurity` bean — exists. The filter-chain bean's
dependency is unsatisfiable → the context fails to load → **every IT in both
services errors before its first assertion**. Because neither
`integrationTest` task is invoked by any CI lane, this rot was never surfaced
(and it let TASK-BE-459's `!standalone` consumer-wiring bug ship without an
integration signal).

This is the **exact** symptom outbound-service already fixed in TASK-BE-334,
where `SecurityConfig` was annotated `@ConditionalOnWebApplication(type =
SERVLET)` (see the in-code comment there). This task applies the established
pattern to the two remaining services and then wires their `integrationTest`
into CI so the suites stay honest.

## Root cause

- `HttpSecurity` is auto-configured only inside a servlet web application
  context (`HttpSecurityConfiguration` is `@ConditionalOnWebApplication(SERVLET)`).
- `inventory`/`inbound` `SecurityConfig` are plain `@Configuration` (no web
  condition), so under `webEnvironment=NONE` Spring still tries to build
  `securityFilterChain(HttpSecurity …)` and fails with an unsatisfied
  dependency.
- The four **wired** WMS integration modules avoid this: `outbound` gates its
  `SecurityConfig` with `@ConditionalOnWebApplication` (BE-334); `master` runs
  `webEnvironment=RANDOM_PORT` (real servlet context, so `HttpSecurity` exists);
  `notification`/`admin` have no servlet `SecurityFilterChain` bean in the IT
  context. `inbound`/`inventory` fall through the gap.

## Scope

1. **`projects/wms-platform/apps/inventory-service/.../config/SecurityConfig.java`**
   — add `@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)`
   at class level (+ import + a one-line rationale comment mirroring BE-334).
2. **`projects/wms-platform/apps/inbound-service/.../config/SecurityConfig.java`**
   — same.
3. **`.github/workflows/ci.yml`** — add a **new, separate** integration job for
   the two modules (do **not** append them to the existing 4-module WMS lane).
   MONO-331's flake is *intra-runner* container contention; a distinct job on
   its own runner is contention-isolated. The new job runs `--no-parallel`
   internally (two Testcontainers stacks serialized), same gating as the WMS
   lane (`push` / `libs` / `workflows` / `wms`), reuses `_integration.yml`.
- **Out of scope**: touching the passing 4-module WMS lane; per-stack container
  footprint reduction (MONO-331 follow-up territory); any behavioural change to
  production security (the condition is `true` in the real servlet app, so
  runtime security is unchanged).

## Acceptance Criteria

- Both `SecurityConfig` classes are annotated `@ConditionalOnWebApplication(SERVLET)`;
  production behaviour unchanged (servlet app ⇒ condition true ⇒ filter chain active).
- The new CI job runs `inventory-service:integrationTest` + `inbound-service:integrationTest`
  and is **green across the PR's CI runs** (authoritative signal — local Windows
  Testcontainers is not reliable per project policy).
- Existing WMS integration lane and all other integration callers are untouched.
- `changes.outputs.wms` already covers both services (no new project) — no
  path-filter change required; confirm no `dorny/paths-filter` edit is needed.
- YAML valid; reusable-workflow contract unchanged.

## Related Specs / Contracts

- n/a (CI infrastructure + test-context wiring). Precedent: TASK-BE-334
  (`@ConditionalOnWebApplication` on outbound `SecurityConfig`), TASK-MONO-331
  (`--no-parallel` WMS lane), TASK-MONO-326 (`_integration.yml` lineage).

## Edge Cases

- A `@WebMvcTest` slice test in either service imports `SecurityConfig`
  explicitly → `@WebMvcTest` builds a servlet context, so the condition is
  `true` and the slice still gets the real security wiring (no regression).
- inventory IT base sets a dummy `jwt.jwk-set-uri=http://localhost:0/...`; under
  `NONE` with the config skipped, the resource-server autoconfig (also
  `@ConditionalOnWebApplication`) never builds a `JwtDecoder`, so the dummy URI
  stays inert (no JWKS fetch attempt).

## Failure Scenarios

- **Other-rot**: once the context loads, a dormant IT may fail for an
  independent reason (schema/assertion drift accumulated while unwired). If so,
  fix within this PR when small; if `inbound`'s single IT carries heavy
  unrelated rot, descope `inbound` from the CI job (keep its `SecurityConfig`
  fix) and file a follow-up so the PR lands green — never merge CI-red.
- New job resource-contends despite separate runner → it already runs
  `--no-parallel`; escalate to footprint reduction (MONO-331 follow-up).
- 30-min timeout too tight for the 2-stack serial run → raise; worst case ≈
  2 × single-module wall-clock.
