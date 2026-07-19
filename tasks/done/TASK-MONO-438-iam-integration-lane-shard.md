# TASK-MONO-438 — shard the iam integration lane across 2 runners (stop at the next bottleneck)

- **Type**: TASK-MONO (CI wall-clock — measurement-driven)
- **Status**: done
- **Scope**: root `.github/workflows/ci.yml` (`iam-integration-tests` caller)
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (must not undo the MONO-393 reliability fix)

## Goal

Apply the TASK-MONO-436 technique to the lane that inherited the critical path. After 436
sharded ecommerce (16m53s → 6m17s measured), `Integration (iam)` at **9m04s** became the
longest job in the run. Same shape, same fix: shard **across runners**, keep `--no-parallel`
**inside** each shard.

## AC-0 — Measurement (done 2026-07-20; re-verify at start, these are a hypothesis)

Source: run `29705436294`, job `88241724977` (the MONO-436 PR's own run). Per-module
durations = interval between consecutive `> Task …:integrationTest` timestamps (valid
because the lane is serial).

| module | s |
|---|---|
| admin-service | 169 |
| auth-service | 144 |
| account-service | 75 |
| security-service | 74 |
| gateway-service | 27 |

**Reconciliation (the check that makes the model usable):** Σ = 489 s, plus the measured
40 s of Gradle configure/compile = 529 s ≈ the job's own `BUILD SUCCESSFUL in 8m 48s`
(1 s rounding). Job wall-clock 544 s includes ~15 s of runner setup beyond Gradle.

## Decision: 2 shards, not 3 — stop at the next bottleneck

LPT over AC-0 gives **A = 243 s (admin + security)** and **B = 246 s (auth + account +
gateway)**. Adding the ~55 s per-shard fixed cost measured in MONO-436 → each shard ≈ 5m01s.

A 3-way split would give ≈3m51s. **It would buy nothing.** The next-longest job is the
now-sharded ecommerce lane at **6m17s**, so the run's critical path is ecommerce-bound as
soon as iam drops below it — which 2 shards already achieves. A third runner would spend
compute for **zero** critical-path gain.

⇒ Shard only as far as the next bottleneck. Re-open this only if ecommerce drops further.

## Scope / Acceptance Criteria

- **AC-1 (shape)**: `iam-integration-tests` becomes a 2-way matrix over `_integration.yml`.
  **Each shard keeps `gradle-args: --no-parallel`.** Removing it is out of scope and
  forbidden here — MONO-393's evidence (Redis connections severed mid-run; the fail-closed
  blacklist then made every refresh 401 and *impersonated a security defect*) is why it
  exists. The block's own caveat — *"Whether five still needs the mitigation is UNMEASURED.
  Keeping it is the conservative default — not a finding"* — stays true and stays quoted.
- **AC-2 (balance)**: A = admin + security (243 s); B = auth + account + gateway (246 s).
- **AC-3 (artifacts)**: `artifact-name` unique per shard (`upload-artifact@v4` rejects
  duplicates within a run). `report-paths` is already a per-module glob — unchanged.
- **AC-4 (verified on CI, not predicted)**: this PR's run shows both shards SUCCESS and the
  lane's measured wall-clock. **Report the observed number.** One run is a sample.
- **AC-5 (no scope creep)**: wms and the remaining serialised lane are untouched; neither is
  near the critical path (wms bundle measured 4m53s).

## Expected effect (projection — replaced by AC-4)

iam lane 9m04s → ≈5m . Run critical path 9m04s → **≈6m17s, set by ecommerce**. Cost: 2
runners instead of 1.

## Related

- **TASK-MONO-436** (the ecommerce precedent + the shard-don't-unserialise reasoning).
- `platform/testing-strategy.md` § Integration lane serialisation; `_integration.yml`
  `gradle-args` doc block.
- TASK-MONO-393 (the Redis-severance evidence), TASK-MONO-398 (why "jobs failing together"
  is an infrastructure signal, not a contention one), TASK-MONO-394 (retired 2 of 7 modules).
- Memory: `env_fail_closed_outage_impersonates_security_defect`,
  `feedback_local_proves_behaviour_not_performance`.

## Edge Cases / Failure Scenarios

- **Do not shard past the next bottleneck.** The gain is bounded by the longest *other* job;
  past that point extra shards are pure cost. Re-measure before adding a third.
- The split is a measurement from one date, not a property — as modules gain ITs it drifts.
- If a shard shows the starvation signature (connections severed while tests execute), the
  answer is a narrower shard, never dropping `--no-parallel`.
- `admin-service` (169 s) alone exceeds a 2-way ideal split; a future module heavier than
  ~245 s would make 2 shards unbalanced and force a re-split.
