# TASK-MONO-446 — Restore the "as the join key" qualifier that the ADR-MONO-051 D2 promotion dropped

**Status:** ready

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (a one-sentence canonical-text correction, but the wording is load-bearing)

> **Priority: this one first.** The canonical rule currently forbids a published contract that is live in the
> repo today and that ADR-MONO-051 itself cites as the exemplary case. Sibling findings on the same ADR are
> [`TASK-MONO-447`](TASK-MONO-447-adr051-project-count-and-verification-pointer.md) (low severity).

---

## Goal

`ADR-MONO-051` § D2 (`docs/adr/ADR-MONO-051-master-data-stays-federated.md:79`):

> Internal UUID PKs never cross a project boundary **as the join key**.

The rule promoted to platform canon by `TASK-MONO-435` (`platform/service-boundaries.md:87`):

> Internal surrogate keys (UUID PKs) **stay inside the owning project**.

**The qualifier "as the join key" was dropped in the move,** which silently converts a narrow rule into a
broad one. D2 permits a UUID to travel across a boundary as long as nothing joins on it. The promoted text
permits nothing.

The repo relies on exactly what D2 permits. `projects/wms-platform/specs/contracts/events/master-events.md:121`
publishes the warehouse UUID alongside its code:

```json
"warehouse": { "id": "uuid", "warehouseCode": "WH01", … }
```

and ADR-MONO-051 cites this contract as the model case (§1.2). The consumer then persists that UUID as its own
snapshot PK (`WmsSkuSnapshotEntity.of(UUID skuId, String skuCode, …)` —
`projects/ecommerce-microservices-platform/apps/product-service/src/main/java/com/example/product/infrastructure/reconciliation/WmsSkuSnapshotEntity.java:38`),
which is the consumer-side resolution D2 prescribes, not a violation of it.

So read literally, `platform/service-boundaries.md` — a layer-2 source of truth, above every project spec —
now prohibits the contract the ADR was written to bless.

**This is the promotion-loss failure mode, inverted.** The known prior incident lost a *rule* during a
promotion. This one lost a *condition*, so the surviving rule over-reaches. Both come from the same cause: the
promoted text was not diffed against its source.

## Scope

**In scope:**

1. `platform/service-boundaries.md:87` — restore the D2 qualifier so the canonical sentence permits what D2
   permits and forbids what D2 forbids. Match D2's meaning; do not paraphrase it into a third variant.
2. If the surrounding sentences in that bullet were also reworded during promotion, diff the whole bullet
   against ADR § D2 and reconcile — the qualifier may not be the only casualty (AC-1).

**Out of scope:**

- Changing ADR-MONO-051 § D2 itself. The ADR is right; the promotion is what drifted.
- The `all five projects` count error and the § 6 verification pointer → `TASK-MONO-447`.
- Promoting D6, which the ADR declares outstanding — a separate in-flight task
  (`TASK-MONO-437`) already owns it. **Do not touch D6 here**; see § Edge Cases for the coordination note.
- Any change to `master-events.md` or the wms/ecommerce contracts. They are correct as-is; that is the point.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the code wins)** — Do not inherit this ticket's quotes. Re-read
  `ADR-MONO-051:79` and `platform/service-boundaries.md:87` at start of work and confirm the qualifier is
  still missing from the promoted text. If it has been restored already, **STOP and report**.
- **AC-1 (diff, don't spot-fix)** — Diff the **entire** promoted bullet against ADR § D2 clause by clause and
  report every difference found, not just the known one. A promotion that lost one condition may have lost
  others; finding only what this ticket already names would mean the diff was not actually done.
- **AC-2** — The corrected canonical sentence permits the live `wms.master.sku.v1` / warehouse contract shape
  (UUID published alongside the code, consumer resolves by code). State explicitly, in the PR body, why the
  corrected wording admits that contract — a rule that still forbids it has not been fixed.
- **AC-3** — The corrected sentence still forbids what D2 forbids: a consumer joining on the producer's UUID,
  and a producer keeping a per-consumer identifier map. Over-correcting into "UUIDs may cross freely" would
  hollow out the rule.
- **AC-4** — `platform/service-boundaries.md` keeps its existing pointers to `ADR-MONO-051` § D2 and
  `ADR-MONO-050` § 7 D9, and they still resolve.
- **AC-5** — No `projects/**` file is modified. This is a shared-path change only (`CLAUDE.md` shared/project
  boundary).

## Related Specs

- `platform/service-boundaries.md` § Data Boundaries (the canonical home — the file being corrected)
- `docs/adr/ADR-MONO-051-master-data-stays-federated.md` § D2 (the source of truth for the rule's meaning)
- `docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md` § 7 D9 (the single-leg precedent D2 generalises)

## Related Contracts

- `projects/wms-platform/specs/contracts/events/master-events.md` (the live contract the current canonical
  wording forbids — read-only evidence here, must not change)
- `projects/ecommerce-microservices-platform/specs/contracts/events/wms-shipment-subscriptions.md`

## Edge Cases

- **A third wording.** The correction must express D2's meaning, not a fresh formulation. Three variants of one
  rule in two files is worse than the current single wrong one, because then no reader knows which binds.
- **`TASK-MONO-437` is in flight on the same file.** It promotes D6 into the same document. Coordinate: if 437
  lands first, rebase onto it rather than resolving a conflict by picking a side — the two changes touch
  adjacent text and a careless resolution could drop one. If this task lands first, tell 437 so its diff
  includes the corrected sentence.
- **Other promotions from this ADR** — D1 and D3 were identified as already present in canon rather than newly
  promoted. If AC-1's diff shows they were *also* reworded on the way, report it; that widens the finding from
  one sentence to a pattern, and the reviewer should decide scope before it is fixed.

## Failure Scenarios

- **F1 — spot-fixing the known sentence and declaring victory.** The defect is that promotion was not diffed,
  so fixing only the one line already found leaves any sibling losses in place. Guarded by AC-1.
- **F2 — over-correction.** Removing the constraint entirely, or softening it to advice, loses the rule
  ADR-MONO-051 actually decided. Guarded by AC-3.
- **F3 — silent conflict resolution against `TASK-MONO-437`.** Two tasks editing one canonical bullet is
  exactly how a rule disappears. Guarded by the coordination note in § Edge Cases.
