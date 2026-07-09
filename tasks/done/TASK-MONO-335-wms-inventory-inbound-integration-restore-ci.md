# TASK-MONO-335 — Restore + CI-wire the dormant inventory-service integration suite (inbound-service split to TASK-BE-489)

- **Status**: done
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

## Delivered — Other-rot outcome (2026-07-09)

Wiring the suites into CI surfaced the anticipated other-rot in waves; all but
one were test-only and fixed in this PR:

1. Context load: `@ConditionalOnWebApplication(SERVLET)` on inventory + inbound
   `SecurityConfig` (per plan).
2. `Instant`→`OffsetDateTime` for `TIMESTAMPTZ` binds (pgjdbc type-inference) —
   AdjustmentTransfer / FlywayMigration / PickingFlow.
3. `DELETE`→`TRUNCATE` for append-only teardown (inventory `inventory_movement`
   V5 W2; inbound `putaway_confirmation` + `inbound_outbox` V7 W2).
4. Transfer test: seeded `location_snapshot` (source+target, `STORAGE`) so
   `TransferStockService.resolveSameWarehouse` resolves the warehouse (realistic
   master-data-present precondition).
**inbound descoped to TASK-BE-489.** inbound was intended to ship in this PR
too, but restoring it surfaced multi-layer rot beyond the SecurityConfig fix:
(1) context load also needs `OutboxAutoConfiguration` excluded (`ProcessedEventJpaEntity`
→ missing `processed_events` table); (2) even with both context fixes, the
golden-path IT times out awaiting the emitted lifecycle events (a functional
event-emission issue — the one-line exclusion likely also needs inventory's
companion outbox-publish beans). Per this task's own failure-scenario plan
("if inbound carries heavy unrelated rot, descope it and file a follow-up so the
PR lands green"), **all inbound changes were reverted** and the CI job wires
**inventory only**. inbound's full restore + wiring is TASK-BE-489.

So this PR delivers: inventory-service IT fully restored (7 classes, 20 tests,
`redeliveryIsDeduped` `@Disabled` → BE-488) + wired to a new
`wms-inventory-integration-tests` CI job, all green.

One failure was **not** test-only: `PutawayCompletedConsumerIntegrationTest`
re-applied a redelivered event (`expected 50 but was 100`, deterministic across
runs). Root cause: **real production dedupe bug** — `EventDedupeRepositoryImpl`
uses `repository.save()` on an assigned-`@Id` entity, which Spring Data treats as
`merge()`/upsert, so duplicate `eventId`s silently UPDATE instead of colliding
on the PK → the event re-applies. Per user decision (2026-07-09), that test is
`@Disabled` here (the deterministic test body is kept) and the production bug +
sibling-service audit (`outbound`/`notification` share the pattern, covered only
by mock unit tests) is split to **TASK-BE-488** (wms-platform). CI is wired and
guards the remaining 7 restored ITs + the suite going forward.

A separate latent robustness gap discovered en route — `resolveSameWarehouse`'s
documented "fall back to source row warehouse_id" is not implemented (unreachable
on the normal snapshot-present path) — is noted in BE-488's Out-of-Scope for
future consideration.
