# Task ID

TASK-MONO-445

# TASK-MONO-445 — the runner spec quoted in testing-strategy.md is wrong, and it is the stated rationale for lane serialisation

- **Type**: TASK-MONO (canon correction — one measured number)
- **Status**: review
- **Scope**: `platform/testing-strategy.md`
- **Analysis model**: Opus 4.8 · **Impl model**: Haiku or Sonnet (one sentence; the measurement is already done)

## Goal

`platform/testing-strategy.md` § *Integration lane serialisation (CI resource contention)* says:

> On a small runner **(2 CPU / 7 GB)** that can exhaust memory and CPU and **sever the
> containers' DB / cache connections mid-run**.

**Both numbers are wrong for this repository.** Measured on an actual `ubuntu-latest` runner during
`TASK-PC-FE-251` (run `29716982256`, 2026-07-20):

```
nproc : 4
Mem   : 15Gi total (15,993 MiB)
```

`kanggle/monorepo-lab` is a **public** repository, and GitHub's standard hosted runner for public
repos is 4-core / 16 GB. The 2-core/7 GB figure is the **private**-repo runner.

## Why this is worth a ticket rather than a silent edit

**The rule it justifies is correct; only the justification is false.** Integration lane
serialisation was earned by evidence — the starvation signatures (`Connection is not available`,
`SQLTransientConnectionException`, …) in specific lanes' own CI history — not by the runner's spec
sheet. So this correction must **not** be read as weakening the rule, and the ticket exists partly
to say that in the same change.

This is the failure mode `platform/refactoring-policy.md` § Rules #6 describes from the other
direction: **the artefact is right, the stated reason is false, and no test asserts on prose.** A
wrong constant in the canonical testing document is exactly what a future author will quote when
sizing a new lane — and sizing against 7 GB when 15 GB is available produces the *opposite* error
from the one this section was written to prevent.

## Scope / Acceptance Criteria

- **AC-0 (re-measure; do not inherit the number above)** — the runner fleet changes. Confirm
  `nproc` and `free` on a current run before editing. If the numbers have moved again, use the new
  ones and say when they were taken. Cheapest source: any workflow step, or re-read run
  `29716982256`'s `PROBE` group while it is still retained.
- **AC-1** — correct the parenthetical to the measured shape, and **date it** (a runner spec is an
  observation about a fleet at a time, not a constant — the defect being fixed is precisely an
  undated constant that went stale).
- **AC-2** — state, in the same paragraph, that the serialisation rule rests on the observed
  starvation signature and **not** on the runner's size, so the corrected (larger) figure is not
  misread as grounds to unserialise a lane. The existing "**Rule — serialise on evidence, never on
  module count**" already carries this; make sure the corrected sentence does not undercut it.
- **AC-3** — grep for other places quoting a runner spec (`2 CPU`, `7 GB`, `2-core`, `16 GB`,
  `ubuntu-latest` sizing comments in `.github/workflows/**` and `tasks/**`) and fix or flag the
  ones that are wrong. **Recount rather than assuming this is the only site** — `TASK-PC-FE-251`
  found two disagreeing claims (this file said 2 CPU / 7 GB; the ticket said 2-core/16 GB) and
  neither was measured.

## Edge Cases / Failure Scenarios

- **Do not "fix" the rule.** If the implementation finds itself editing anything about *when* to
  serialise, the diagnosis has drifted — this ticket changes a factual parenthetical only.
- The larger runner does **not** mean the lanes have headroom to spare: `TASK-PC-FE-251` measured
  the committed federation stack at **24 containers / 6.87 GiB** with **7.1 GiB available**, i.e.
  already using ~45% of the box. If anything the correct number makes the contention story more
  legible, not less.

## Related

- Provenance + measurement: `TASK-PC-FE-251` (AC-1), run `29716982256`.
- `platform/testing-strategy.md` § Integration lane serialisation, § G4 (a threshold calibrated on
  your host is a proposition about your host — the same class of error, one level up: a *spec*
  quoted from the wrong fleet).
- `platform/refactoring-policy.md` § Rules #6 (a false justification attached to a correct artefact
  is invisible to CI).
