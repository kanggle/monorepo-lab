# TASK-MONO-447 — Correct ADR-MONO-051's project count (five → eight) and its § 6 verification pointer

**Status:** done

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Haiku 4.5 (two textual corrections in one ADR file)

> Sibling of [`TASK-MONO-446`](TASK-MONO-446-restore-join-key-qualifier-in-promoted-d2-rule.md), which carries
> the medium-severity finding from the same ADR-MONO-051 audit (2026-07-20) and should land first.
>
> **🟢 SCOPE REDUCED 2026-07-20 (post-filing re-measurement).** This ticket originally warned that
> `TASK-MONO-437` was in flight and might promote the wrong count into canon, doubling the correction
> surface. **That did not happen, and the warning is now withdrawn.** MONO-437 has landed (`tasks/done/`).
> It promoted D6 to **`TEMPLATE.md`** — not `platform/service-boundaries.md`, because D6 binds the
> distribution/extraction strategy rather than service boundaries — and the landed wording at
> [`TEMPLATE.md:81`](../../TEMPLATE.md) reads:
>
> > A proposal that introduces a component **every project must call to resolve identity** must first
> > demonstrate that both of the following survive it…
>
> The literal count was dropped in favour of a count-free form. **Canon is correct; the under-count survives
> only inside the ADR file.** Two consequences: (a) there is no longer any time pressure on this ticket, and
> (b) AC-2's open question ("what wording will not re-rot?") is already answered by precedent — use
> `every project`, matching canon, so ADR and `TEMPLATE.md` say the same thing.

---

## Goal

### ① `all five projects` under-counts by three, inside a binding clause (ADR file only — canon is already correct)

`ADR-MONO-051` § D6 (`docs/adr/ADR-MONO-051-master-data-stays-federated.md:127`):

> Any future proposal that introduces a component **all five projects** must call to resolve identity must
> first demonstrate how `scripts/sync-portfolio.sh` extraction … survive it.

Repeated at § 1.4 and at § 4 A1 (`:145`, "the other five").

Recounted against the repo: `README.md:30` states "**7 domain projects + 1 horizontal console**";
`scripts/sync-portfolio.sh:39-45` configures **7** extractable projects (wms, ecommerce, iam, fan, scm,
finance, erp), plus `projects/platform-console/`. **Eight** `PROJECT.md` files exist.

D6 is not prose — it is a binding rejection criterion. A hub touching six projects would not literally trip a
gate worded "all five", so the scope term matters.

### ② § 6's verification row points at a file that does not contain the sentence it quotes

`ADR-MONO-051` § 6 (`:192`, and § 1.2 at `:34`):

> \| No producer-side id map exists \| "No wms↔ecommerce id map is stored" — `ecommerce-fulfillment-subscriptions.md:41-46` \|

`projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md:41-46` is a JSON payload
block; the string "id map" does not occur in that file. The quoted sentence actually lives in a different file
on a different leg:
`projects/ecommerce-microservices-platform/specs/contracts/events/wms-shipment-subscriptions.md:44` —
"`orderId == orderNo`. No wms↔ecommerce id map is stored."

The underlying claim is **true**; only the pointer is wrong. But § 6 instructs the reader to re-run these
checks (`:198`), and anyone who does will find the check fails and may conclude the decision drifted.

## Scope

**In scope:**

1. `docs/adr/ADR-MONO-051-master-data-stays-federated.md` — correct the project count at every occurrence
   (`:127`, § 1.4, `:145`), using a form that will not rot again as projects are added (see AC-2).
2. Same file — repoint the § 6 verification row (and the § 1.2 reference) to the file and line that actually
   carry the quoted sentence.

**Out of scope:**

- `platform/service-boundaries.md` and the dropped D2 qualifier → `TASK-MONO-446`.
- D6's promotion into canon — **already done** by `TASK-MONO-437` (landed, `tasks/done/`), into `TEMPLATE.md`
  with correct count-free wording. Do not re-promote, and do not "align" `TEMPLATE.md` to the ADR — the
  direction of correction runs the other way here: canon is right, the ADR is stale.
- Re-opening any decision. Both corrections are factual; neither changes what D6 decides, only the scope term
  it names and a citation.
- Auditing other ADRs for the same count error. If the audit is wanted, it is its own task with its own
  population count — do not sample opportunistically from here.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the code wins)** — Do not inherit this ticket's numbers. **Recount the projects
  yourself** at start of work: enumerate `projects/*/PROJECT.md`, cross-check against
  `scripts/sync-portfolio.sh` and `README.md`. If the true count is not eight, use what you measure and say so
  — this ticket's "eight" is a hypothesis like any other. Also re-verify that `ecommerce-fulfillment-subscriptions.md`
  still lacks the quoted sentence (sanity-check the pattern against `wms-shipment-subscriptions.md:44`, where
  it is known to exist, so an empty result is proven to mean absence).
- **AC-1** — Every occurrence of the under-count is corrected. Grep the whole ADR for `five`/`5` used as a
  project count; a partial fix leaves the clause self-contradictory.
- **AC-2** — The corrected wording does not re-rot on the next project, **and it matches the wording already
  promoted to canon**: `TEMPLATE.md:81` binds this rule as "a component **every project must call to resolve
  identity**". Use that form in the ADR too, so the two say the same thing. Do not swap five→eight (it rots on
  project nine), and do not invent a third variant — one rule in two files with three wordings is worse than
  the single stale number this ticket exists to remove.
- **AC-3** — The § 6 row and § 1.2 reference cite a file:line whose content genuinely contains the quoted
  sentence. Verify by reading the cited line after editing, not by trusting the edit.
- **AC-4** — Re-run § 6's checks as § 6 instructs and confirm every row now resolves. Report any *other* row
  that fails — the audit checked this one closely and the rest only in passing.
- **AC-5** — `docs/adr/INDEX.md`'s ADR-MONO-051 row still matches the file's header, and
  `scripts/check-adr-index-drift.sh` still passes. (Neither correction should touch Status or Date; confirm
  they did not.)
- **AC-6** — No `projects/**` and no `platform/**` file is modified.

## Related Specs

- `docs/adr/ADR-MONO-051-master-data-stays-federated.md` (the file being corrected)
- `README.md` § project inventory, `scripts/sync-portfolio.sh` (the count's real source)
- `projects/ecommerce-microservices-platform/specs/contracts/events/wms-shipment-subscriptions.md` (where the
  quoted sentence actually lives)

## Related Contracts

- None. No API or event contract changes.

## Edge Cases

- **~~`TASK-MONO-437` may promote the wrong count~~ — WITHDRAWN, it did not.** Recorded here rather than
  deleted, because the reasoning is worth keeping: the risk was real when this ticket was filed, and the
  reason it did not materialise is instructive. 437 re-measured before starting, found that the anchor
  section ADR-051 named (`TEMPLATE.md § Discovery → Distribution`) **does not exist**, chose a different
  destination on that evidence, and rewrote the clause count-free as `every project`. Compare
  `TASK-MONO-446`: the D2 promotion landed the same week, skipped the source diff, and lost a qualifier.
  **Same ADR, same window, opposite outcomes — the variable was whether the promoter re-read the source.**
  Promotion-loss is not a property of promoting; it is a property of promoting without diffing.
- **"Projects" may not mean the same thing in D6 as in `README.md`** — `platform-console` is a horizontal
  console, not a domain project, so a defensible reading is 7 domain projects + 1. Whichever reading is taken,
  make it explicit in the corrected sentence rather than leaving a bare number to be re-litigated.
- **The § 6 pointer may have been right when written** — files move. Do not editorialise about the original
  author's error; just repoint it.

## Failure Scenarios

- **F1 — swapping five→eight and re-rotting on project nine.** Guarded by AC-2.
- **F2 — fixing the pointer to another wrong line.** Editing a citation without reading the destination is how
  the defect arose. Guarded by AC-3's read-after-edit.
- **F3 — "aligning" `TEMPLATE.md` to the ADR.** Canon already carries the correct count-free wording; the ADR
  is the stale side. An implementer who assumes the ADR is authoritative because it is the ADR would propagate
  the error into canon — the exact inversion this ticket now exists to prevent. Guarded by § Scope's
  out-of-scope entry and AC-2.
- **F4 — inheriting "eight" from this ticket without recounting.** The audit that produced this ticket found
  three separate count/citation errors in one ADR; a ticket derived from it is not more trustworthy than its
  source. Guarded by AC-0.
