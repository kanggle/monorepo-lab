# TASK-MONO-436 — shard the ecommerce integration lane across runners (keep serialisation inside each shard)

- **Type**: TASK-MONO (CI wall-clock — measurement-driven)
- **Status**: done
- **Scope**: root `.github/workflows/ci.yml` (`ecommerce-integration-tests` caller)
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (must not undo the MONO-398 reliability fix)

## Goal

Cut the critical path of a code PR without re-introducing the flake the repo already paid
to fix. The ecommerce integration lane is **the** bottleneck: 16m53s against a next-longest
job of 4m. It is slow **by design** (`--no-parallel`, TASK-MONO-403/398) because 12
Testcontainers stacks booting at once on one runner starved Hikari/Postgres.

The fix is not to re-parallelise **inside** a runner — that is exactly what caused the
starvation. It is to **shard the lane across runners**, with each shard still
`--no-parallel` internally. Parallelism across runners is not the parallelism that broke.

## AC-0 — Measurement (done 2026-07-20; re-verify at start, the numbers are a hypothesis)

Source: run `29640065069`, job `88069194561` (2026-07-18, SUCCESS). Per-module durations
derived from the interval between consecutive `> Task …:integrationTest` log timestamps
(valid because the lane is serial).

| module | s | | module | s |
|---|---|---|---|---|
| order-service | 188 | | product-service | 71 |
| promotion-service | 170 | | search-service | 64 |
| shipping-service | 103 | | review-service | 43 |
| user-service | 85 | | notification-service | 30 |
| payment-service | 75 | | batch-worker | 27 |
| settlement-service | 72 | | gateway-service | 22 |

**Arithmetic check (this is why the model is trustworthy):** Σ = 950 s = 15m50s; plus the
measured 63 s of Gradle configure/compile before the first IT = **16m53s**, which matches
the job's own `BUILD SUCCESSFUL in 16m 53s` exactly. A per-module model that did not
reconcile with the build's own total would not be usable for balancing.

## Scope / Acceptance Criteria

- **AC-1 (shape)**: the `ecommerce-integration-tests` caller becomes a 3-way matrix over the
  reusable `_integration.yml`. **Each shard keeps `gradle-args: --no-parallel`.** Removing
  it — from any shard, for any reason, including "this shard only has 4 modules" — is out of
  scope and forbidden here: `_integration.yml` states the rule as *"Module count is a
  hypothesis, not evidence"*, and MONO-398 audited exactly that mistake.
- **AC-2 (balance)**: shards assigned by longest-processing-time greedy over AC-0 →
  **A=317 s, B=309 s, C=324 s**. Each shard's IT time ≈ 5m20s.
- **AC-3 (artifacts)**: `artifact-name` must be unique per shard — `upload-artifact@v4`
  errors on duplicate names within a run. `report-paths` collapses to a per-module glob
  (`apps/*/build/{reports/tests,test-results}/integrationTest/`) instead of 24 literal
  lines; a fresh runner per shard means no cross-shard leakage.
- **AC-4 (verified on CI, not predicted)**: the PR's own run shows all three shards SUCCESS
  and the lane's wall-clock measured from the run. **Report the observed number, not the
  projection.** A single run is one sample, not a constant — state it as such.
- **AC-5 (no scope creep)**: the other three serialised lanes (iam, wms, ecommerce-adjacent)
  are NOT touched. Each has its own evidence history; sharding them is a separate decision.

## Expected effect (projection — must be replaced by AC-4's measurement)

~16m53s → ~5m20s of tests + per-shard fixed cost (checkout + JDK + Gradle configure,
≈2–3 min) ≈ **8 min**, i.e. roughly a 9-minute cut on the critical path. Cost: 3 runners
instead of 1 (~24 runner-minutes vs 17) — **more total compute for less wall-clock**, which
is the intended trade and should be stated plainly rather than sold as a free win.

## Secondary benefit

The current block warns: *"A thirteenth module narrows that margin and nothing here will
warn you"* (12 modules, 30-min timeout, 47% margin). Sharding removes that cliff — each
shard carries its own 30-min budget, so the next module added lands in a shard at ~5 min,
not against a lane at ~17 min.

## Related

- `platform/testing-strategy.md` § Integration lane serialisation (the rule + its cost).
- `.github/workflows/_integration.yml` (`gradle-args` doc block — the "not free" warning).
- TASK-MONO-398 (audit that found four unmitigated lanes with zero evidence — *"zero is a
  result"*), TASK-MONO-403 (serialisation), TASK-MONO-326 (the reusable lane).
- Memory: `env_wms_notification_seed_cluster_ci_flake`, `feedback_local_proves_behaviour_not_performance`.

## Edge Cases / Failure Scenarios

- **A matrix job that calls a reusable workflow must still surface distinguishable check
  names.** If all three render as the same name the merge-verification discipline
  (`statusCheckRollup` by state) still works, but a human cannot tell which shard failed —
  put the shard key in the job `name:`.
- **Re-balancing decays.** The AC-0 numbers are one run on one runner class; as modules gain
  ITs the split drifts. This is acceptable (any shard has ~25 min of headroom) but the split
  must be documented as *measured on a date*, not as a property.
- If a shard shows the starvation signature (connections severed while tests execute), the
  answer is a **narrower shard**, not removing `--no-parallel`.
