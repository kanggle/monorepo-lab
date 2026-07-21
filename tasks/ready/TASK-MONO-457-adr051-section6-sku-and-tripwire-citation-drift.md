# TASK-MONO-457 — ADR-MONO-051 §6 has two more rows whose citations no longer resolve (SKU-as-code names a file that does not exist; the tripwire row's citation has drifted)

**Status:** ready

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (both are citation repairs; the tripwire row carries one judgment — whether the citation drifted or the *decision* did)

> Surfaced by [`TASK-MONO-454`](../done/TASK-MONO-454-adr051-section6-rows-that-no-longer-verify.md)'s **AC-1**, which required
> re-running all seven §6 rows rather than inheriting that 447 had verified them. 454 fixed the extraction row and the
> `mdm` row and reported these two as out of scope. **Fourth ticket from the same ADR-MONO-051 audit**, after 446 (canon
> qualifier), 447 (project count + first §6 pointer), and 454 (extraction + mdm rows). The audit chain keeps surfacing §6
> rows because §6's own closing line (`:198`) orders a re-run and each pass measures rather than inherits.

---

## Goal

`ADR-MONO-051` §6 is a table of verification checks closed by an instruction (`:198`): *"Re-run these before citing this ADR
as current."* Two of its rows have a citation that no longer resolves as written. In **both**, the underlying claim is still
**true** — this is citation hygiene, not a decision change — but a reader who follows `:198` hits a broken reference and must
decide for themselves whether the fact drifted, which is exactly the state §6 exists to prevent.

### ① The SKU-as-code row cites a file that does not exist

`:191`:

> \| SKU crosses boundaries as a code, not a UUID \| `skuCode` present in the payloads of `master-events.md`,
> `ecommerce-fulfillment-subscriptions.md`, `replenishment-subscriptions.md`, `scm-procurement-events.md` \|

Measured 2026-07-21: **`ecommerce-fulfillment-subscriptions.md` does not exist** anywhere under
`projects/ecommerce-microservices-platform/specs/contracts/events/`. The fulfillment request event that carries
`lines[].skuCode` is defined in **`fulfillment-events.md`** (`skuCode` present, 3 hits). The row's other three files are
correct (`master-events.md` 1, `replenishment-subscriptions.md` 2, `scm-procurement-events.md` 5). So only the one filename
is wrong; the claim it supports is intact.

This is the same class the 454 extraction row was — a citation that resolves to nothing (or, worse, to the wrong place). §1.2
(`:34`) already refers to the real event by name (`ecommerce.fulfillment.requested.v1`), so the correct anchor is not in doubt.

### ② The tripwire row's citation now describes a consumer

`:195`:

> \| The tripwire has not fired \| `erp.masterdata.businesspartner.changed.v1` subscriber count = 0
> (`erp-masterdata-events.md:48-51`) \|

Measured 2026-07-21: `erp-masterdata-events.md:44-51` now describes **`read-model-service` consuming** the sibling topics
(`department` / `employee` / `jobgrade` / `costcenter`) to project an employee org-view, and states that the **`businesspartner`
topic stays unconsumed in this increment** ("v1 consumers = none remains the historical record"). So the claim — *businesspartner*
subscriber count = 0 — is **still true**, but the cited lines now read, at a glance, as if the contract has a consumer. A reader
verifying "the tripwire has not fired" lands on text about a consumer and cannot tell from the citation alone that the consumer
is for the *sibling* topics, not businesspartner.

**This row carries the one genuine judgment in this ticket** (AC-3): the fix is a citation repair *only if* businesspartner is
still unconsumed. If a businesspartner subscriber has appeared, the §6 claim is **false** and D5 tripwire condition ③ has fired —
which is a decision-reopen, not a citation edit. AC-0 forces that check before any edit.

## Scope

**In scope** — `docs/adr/ADR-MONO-051-master-data-stays-federated.md` only:

1. `:191` — repoint `ecommerce-fulfillment-subscriptions.md` to the file that actually carries the payload
   (`fulfillment-events.md`, confirmed by reading it — AC-2).
2. `:195` — make the citation point at text that actually asserts *businesspartner* has zero subscribers, or add a one-clause
   note distinguishing the sibling-topic consumption from businesspartner. Either outcome is fine; leaving it as-is is not (AC-3).

**Out of scope:**

- `TEMPLATE.md`, `projects/**`, `platform/**` — AC-5. (The event-contract files are the correct side; do **not** rename or move
  a contract to match a stale citation — that is the `TASK-MONO-447` §F3 inversion.)
- The five §6 rows 454 already settled (extraction, mdm) or verified passing (id-map, triplicated, not-joined). AC-1 re-runs all
  seven again rather than inheriting that.
- Re-opening D5 or any decision — *unless* AC-0 finds the tripwire has fired, in which case STOP and escalate (Failure B).

## Acceptance Criteria

- **AC-0 (gate — re-measure; the file wins).** Do not inherit this ticket's filenames or line numbers. (a) Confirm
  `ecommerce-fulfillment-subscriptions.md` still does not exist and `skuCode` is still in `fulfillment-events.md`. (b) Re-read
  `erp-masterdata-events.md` around the cited lines and confirm **businesspartner is still unconsumed** — grep the repo for a
  subscriber to `erp.masterdata.businesspartner.changed.v1`. **If either row has already been fixed, STOP and report. If
  businesspartner now HAS a subscriber, STOP — that is D5 firing, not a citation defect (Failure B).**
- **AC-1 (re-run all seven).** Re-run every §6 row and report each; a row 454 fixed or passed is a hypothesis, not a source.
- **AC-2 (the destination must exist).** Before editing `:191`, **read `fulfillment-events.md`** and confirm `skuCode` is in a
  payload there. Before editing `:195`, read the exact lines you cite and confirm they assert the businesspartner-specific count.
- **AC-3 (the tripwire row must end in a state a reader can act on).** State in the PR whether you (a) repointed to lines that
  assert businesspartner = 0 subscribers, or (b) kept the citation and added a note distinguishing sibling consumption. A silent
  leave-as-is fails this AC — the row's whole purpose is to be re-run.
- **AC-4.** `docs/adr/INDEX.md`'s ADR-MONO-051 row still matches the file header and `scripts/check-adr-index-drift.sh` passes.
  Status and Date must not change (this is not a decision change).
- **AC-5.** No `projects/**`, no `platform/**`, no `TEMPLATE.md` modification.

## Related Specs

- `docs/adr/ADR-MONO-051-master-data-stays-federated.md` §1.2, §6, D5 (the file being corrected)
- `projects/ecommerce-microservices-platform/specs/contracts/events/fulfillment-events.md` (the real SKU-payload destination; read-only here)
- `projects/erp-platform/specs/contracts/events/erp-masterdata-events.md` (the tripwire citation's target; read-only here)
- `tasks/done/TASK-MONO-454-adr051-section6-rows-that-no-longer-verify.md` (the AC-1 re-run that surfaced both items)

## Related Contracts

- None. Both event contracts are the correct side and are read-only here.

## Edge Cases

- **The tripwire row is not merely a citation.** If AC-0 finds a businesspartner subscriber, the claim "the tripwire has not
  fired" is false and D5 condition ③ has fired. That is a decision-reopen (a new supplier-service leg per D5), not a doc fix —
  escalate rather than "correcting" the row to hide it. This is the one place where a wrong-looking citation could be masking a
  real state change.
- **`read-model-service` consuming the siblings is legitimate and unrelated.** Do not read it as a tripwire hit — the tripwire is
  businesspartner-specific by D5's exact wording. The whole point of the ② fix is to stop the citation from implying otherwise.
- **The SKU row lists four files; only one is wrong.** Do not "helpfully" rewrite the other three — they resolve. Change the one
  broken filename and nothing else (the 447/454 discipline: fix citations, not descriptions).

## Failure Scenarios

- **F1 — repointing `:191` to another non-existent or wrong file.** Editing a citation without opening the destination is how the
  454 defect and this one were created. Guarded by AC-2.
- **F2 — the tripwire has actually fired and the row is "fixed" as hygiene.** A businesspartner subscriber makes the claim false;
  editing the citation to look tidy would bury a decision-reopen trigger. Guarded by AC-0 and § Edge Cases.
- **F3 — treating either row as cosmetic and skipping it.** A verification table whose closing line orders a re-run trains readers
  to skip it once a row always fails to resolve. Guarded by AC-3.
- **F4 — inheriting 454's measurements instead of re-running.** 454 measured on 2026-07-21; the erp contract file is exactly the
  kind that changes when a consumer is wired. Guarded by AC-0/AC-1.

## Test Requirements

- Doc-only: `scripts/check-adr-index-drift.sh` GREEN, ADR file diff confined to `:191` and the `:195` row, Status/Date unchanged,
  no `projects/**` / `platform/**` / `TEMPLATE.md` in the diff.

## Definition of Done

- [ ] AC-0 re-measure (both rows; businesspartner-subscriber grep)
- [ ] `:191` repointed to the real payload file, destination read (AC-2)
- [ ] `:195` ends in a re-runnable state; option (a)/(b) recorded (AC-3)
- [ ] `check-adr-index-drift.sh` GREEN; Status/Date unchanged (AC-4)
- [ ] ADR file only (AC-5)

## Notes

- **This is where the ADR-051 §6 audit chain should end** — after this, all seven rows resolve. If a fifth ticket is ever needed,
  the real fix is structural (a check that §6's citations resolve, like the `check-adr-index-drift.sh` predicate but for in-row
  file/section references), not another row-by-row pass. That is a different, larger ticket and should be counted (how many ADRs
  carry in-row citations at all) before it is opened — `TASK-MONO-328`'s "no signal, no start" rule applies.
- **dependency**: none blocking. Source = `TASK-MONO-454` AC-1 re-run.
