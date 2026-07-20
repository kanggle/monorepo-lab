# TASK-MONO-447 — Correct ADR-MONO-051's project count (five → eight) and its § 6 verification pointer

**Status:** ready

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Haiku 4.5 (two textual corrections in one ADR file)

> Sibling of [`TASK-MONO-446`](TASK-MONO-446-restore-join-key-qualifier-in-promoted-d2-rule.md), which carries
> the medium-severity finding from the same ADR-MONO-051 audit (2026-07-20) and should land first.
> **Coordination:** finding ① below directly affects `TASK-MONO-437`, in flight — see § Edge Cases.

---

## Goal

### ① `all five projects` under-counts by three, inside a binding clause

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
- D6's promotion into canon → `TASK-MONO-437`, in flight.
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
- **AC-2** — The corrected wording does not re-rot on the next project. Prefer a form that binds without a
  literal count (e.g. "every project in the portfolio") over swapping five→eight, and say in the PR body which
  form was chosen and why. If a literal count is kept, it must be sourced to a file that would be updated when
  a project is added.
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

- **`TASK-MONO-437` is promoting D6 into `platform/service-boundaries.md` right now.** If it promotes the
  clause carrying "all five projects", the wrong count lands in canon and this correction then has to be made
  twice, in two files. **Coordinate before starting**: either correct the ADR first so 437 promotes correct
  text, or tell 437 to use the corrected scope term. This is the same promotion-loss pattern `TASK-MONO-446`
  exists to repair — catching it before promotion is free; after is another ticket.
- **"Projects" may not mean the same thing in D6 as in `README.md`** — `platform-console` is a horizontal
  console, not a domain project, so a defensible reading is 7 domain projects + 1. Whichever reading is taken,
  make it explicit in the corrected sentence rather than leaving a bare number to be re-litigated.
- **The § 6 pointer may have been right when written** — files move. Do not editorialise about the original
  author's error; just repoint it.

## Failure Scenarios

- **F1 — swapping five→eight and re-rotting on project nine.** Guarded by AC-2.
- **F2 — fixing the pointer to another wrong line.** Editing a citation without reading the destination is how
  the defect arose. Guarded by AC-3's read-after-edit.
- **F3 — landing after `TASK-MONO-437` promotes the wrong count**, doubling the correction surface. Guarded by
  the coordination requirement in § Edge Cases.
- **F4 — inheriting "eight" from this ticket without recounting.** The audit that produced this ticket found
  three separate count/citation errors in one ADR; a ticket derived from it is not more trustworthy than its
  source. Guarded by AC-0.
