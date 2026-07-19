# TASK-MONO-440 — drop the `needs: build-and-test` gate so downstream lanes start immediately

- **Type**: TASK-MONO (CI wall-clock — measurement-driven, policy change)
- **Status**: done
- **Scope**: root `.github/workflows/ci.yml` (17 `needs:` lists)
- **Analysis model**: Opus 4.8 · **Impl model**: Opus

## Goal

Remove the last deterministic serial segment on the critical path. MONO-436/438 sharded the
two slowest lanes; what remains in front of every code PR is a **2m20s gate** that produces
nothing any downstream job consumes.

## AC-0 — Measurement (done 2026-07-20; re-verify at start)

**The gate is on the critical path, and queueing is not.** Timeline of run `29706299169`:

```
created         22:31:26
changes         22:31:28 → 22:31:38   (10s)
build-and-test  22:31:41 → 22:34:01   (2m20s)   <- serial gate
ecommerce A     22:34:04 → 22:41:20   (7m16s)   <- starts 3s after the gate
run end         22:41:20                총 9m54s
```

12s + 2m20s + 7m16s = 9m54s exactly. Runner queue delay was **3 s**, so the 2m20s is the
gate itself, not scheduling.

**The gate is a pure ordering edge, not a data dependency:**
- `build-and-test`'s only artifact is `test-reports`, uploaded `if: failure()` (diagnostic).
- `ci.yml` contains **zero** `download-artifact` steps — nothing consumes it.
- The job declares no `outputs:`.

**The failure it guards against is not the one that occurs here.** Across the last 100
`ci.yml` runs: `build-and-test` failed **0** times; all 3 CI failures were integration
lanes (scm inventory+inbound IT; wms master+notification+outbound IT ×2).

## Honest statement of uncertainty

0 failures in ~20 executions is **not** proof of rarity — by the rule of three the 95% upper
bound on the failure rate is still ~15%. This is a judgement under uncertainty, and it is
recorded as such. What tips it:

1. **Nothing is lost from the signal.** `build-and-test` still runs, still reports, still
   fails the run. Only the "don't spend compute on a red PR" property goes away, and that
   loss is bounded at ~9 integration jobs on the affected PR.
2. **The repo has already chosen this trade twice** — MONO-436 (1→3 runners) and MONO-438
   (1→2 runners) both bought wall-clock with compute. This buys more, more cheaply.
3. **It is the best of the three.** The shard gains fight the max of noisy variables and
   were partly buried in ±59 s of run-to-run spread; removing a fixed serial segment is
   deterministic.

## Scope / Acceptance Criteria

- **AC-1**: `build-and-test` is removed from all 17 `needs:` lists.
  `needs: [changes, build-and-test]` → `needs: changes`;
  `needs: [changes, build-and-test, boot-jars]` → `needs: [changes, boot-jars]`.
  Real data dependencies (`boot-jars`, `fan-platform-boot-jars`, `scm-platform-boot-jars` —
  they publish jars the e2e jobs download) are **kept**.
- **AC-2**: `build-and-test` itself is otherwise unchanged — same `if:`, same tasks, same
  reports. It becomes a peer job rather than a gate.
- **AC-3 (the real risk — measure it)**: the gain is only real if the runner pool absorbs
  the wider simultaneous burst. Evidence it should: in run `29706299169` at least a dozen
  jobs started within 3 s of each other once the gate lifted. But if concurrency is capped,
  **the wait simply moves into the queue and the gain is zero**. This PR's own run is the
  test: compare `createdAt → updatedAt` against the ~9m54s baseline **and** check that
  downstream jobs' `startedAt` is now within seconds of `changes` completing.
- **AC-4**: report the observed number, not the projection. One run is a sample — the
  measured spread on this repo's runs is ±3m42s end-to-end, which is **larger than the
  2m20s being removed**, so a single run cannot confirm the gain by itself. Compare start
  offsets (deterministic) as the primary evidence, wall-clock as secondary.

## Expected effect (projection — replaced by AC-3/AC-4)

Every code-touching run loses a 2m20s serial prefix: ~9m54s → ~7m30s on a run like the
baseline. Cost: on a PR whose unit tests fail, ~9 integration jobs run that would previously
have been skipped.

## Related

- **TASK-MONO-436** (ecommerce shard), **TASK-MONO-438** (iam shard, 2-not-3 reasoning).
- Memory: `feedback_local_proves_behaviour_not_performance` (one sample is not a constant —
  this series has now been burned by that twice).

## Edge Cases / Failure Scenarios

- **Queueing eats the gain** (AC-3). If measured start offsets show jobs waiting, revert:
  the change is one mechanical edit and carries no data risk.
- A compile error now surfaces in every lane at once instead of once in the gate — noisier
  logs on a broken PR, but not a lost signal (and arguably a faster one, since each lane
  reports its own compile failure).
- Do **not** also remove `needs: changes` — that one carries the path-filter outputs the
  `if:` conditions read, and is a genuine data dependency.
