# TASK-BE-559 — refactor-spec: wms-platform Tier-2 structural judgment pass

- **Type**: TASK-BE (spec-refactor — structural section ordering / numbering only, NO requirement/contract/decision change)
- **Status**: ready
- **Service**: wms-platform (master-service, outbound-service domain-model)
- **Domain/traits**: wms / [event-driven, transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (per-item intentional-vs-drift judgment)

## Goal

Resolve the **Tier-2 structural** items deferred from TASK-BE-558 (the wms clean-batch refactor).
Each was an "outlier vs siblings" section-ordering / numbering divergence that BE-558 explicitly did
NOT auto-apply because the outlier is often **intentional**. This task judges each item against sibling
parity (read the actual sections, not the scanner claim) and applies **only** the genuinely-clean,
meaning-preserving subset. **5 of 7 candidates were confirmed intentional and rejected** — the point of
the pass is that discipline, not a bulk reorder.

No requirement / contract / schema / status-code / state-transition / business-rule is touched.

## Scope

**Applied — 2 edits / 2 files (meaning-preserving structural):**

| # | File | Fix | Why clean |
|---|---|---|---|
| 1 | `services/master-service/domain-model.md` | Swapped section order: `## Forbidden Patterns (in code)` now precedes `## Reference Data Snapshot (v1 Seed)` | **3/3 siblings** (inbound / outbound / inventory domain-model.md) order Forbidden **before** Reference; master was the sole reversed outlier. Both are leaf sections near the end; headings (hence anchors) unchanged — pure reorder. |
| 2 | `services/outbound-service/domain-model.md` | Scope infra list reconciled to body: merged items 9+10 into `9. ErpOrderWebhookInbox / ErpOrderWebhookDedupe`, renumbered TmsRequestDedupe→10, MasterReadModel→11 | The **Scope list claimed 12 items but the body has 11 headings** — the body deliberately merges the two Erp webhook records under one `## 9.` heading ("Identical pattern…"). Fixed the summary to match the authoritative detail (no body change, no authoring). Now Scope 1–11 = body §1–11. |

## Acceptance Criteria

- [x] 2 edits applied in isolated worktree (`task/be-559-refactor-spec-wms-tier2`); structure re-verified by grep (master: Forbidden@356 before Reference@368; outbound Scope infra 7–11 = body §7–11).
- [x] No heading renamed → no anchor broken (item 1 reorders existing headings; item 2 edits a numbered list, not headings).
- [x] Zero requirement / contract / schema / state-transition / business-rule changes.
- [x] Each of the 7 candidates independently read against siblings before the apply/reject call.

## Related Specs

- `projects/wms-platform/specs/services/{inbound,outbound,inventory,master}-service/{domain-model,database-design,architecture,idempotency}.md`

## Out of Scope — REJECTED after verification (confirmed intentional, NOT drift)

The scanner-flagged Tier-2 items below were each read against siblings and confirmed to be **content-driven / intentional divergence**, not mechanical drift. Forcing sibling-uniformity would corrupt meaning:

- **`inventory-service/database-design.md` — `## 7. Master Read Model` (vs §1 in inbound/outbound).** REJECTED. The `## N.` numbering follows each service's **migration-version order** (`§N (Vk, …)` tags). Master Read Model is **V1 in inbound** (→ §1) but **V4 in inventory** (→ §7). Its position faithfully reflects the actual migration history; renumbering to §1 would misrepresent when the table was introduced. (Also the highest external-ref-blast candidate — correctly avoided.)
- **`master-service/idempotency.md` — unnumbered headings (vs numbered `## 1./2./…` in the other 3).** REJECTED. master's idempotency doc is a **structurally different document** (sections `Scope / Key Contract / Key Scope / Storage / Control Flow / …`), not a renumberable copy of the REST/Webhook/Kafka triad. It even carries an explicit `## Cross-Service Idempotency Key Conventions (Intentional Divergence)` section. Prepending numbers would be a content reorganization, not a mechanical fix.
- **`inventory-service/idempotency.md` — missing `## Cross-References` section.** REJECTED. Present in inbound/outbound but **absent in both inventory AND master** (2-have / 2-don't — no clear convention), and adding it means authoring which cross-refs to list.
- **`inventory-service/domain-model.md` — extra `## State Machines (Cross-reference)` H2.** REJECTED. An **additive navigation** section pointing to `state-machines/`; removing it loses a wayfinding aid and changes nothing structural for the worse.
- **`inventory-service/architecture.md` — standalone `## State Machines` H2 (vs nested `### State Machine` under Concurrency/Saga in siblings).** REJECTED (deferred-judgment). The Reservation lifecycle is a first-class concern for inventory-service; elevating it to an H2 is a defensible authoring choice, not drift. Not reorganized on a thin majority.

## Edge Cases

- Item 1 keeps both master section headings verbatim → `#forbidden-patterns-in-code` and `#reference-data-snapshot-v1-seed` anchors remain valid; only their document order changes.
- Item 2 touches the Scope **list** text only; body headings `## 10./## 11.` are unchanged, so any `#10-…`/`#11-…` body anchors (if any external ref exists) are unaffected.

## Failure Scenarios

- If a downstream doc relied on master's Reference-before-Forbidden order (none does — both are terminal leaf sections), the reorder would surprise it. No such dependency exists.
- If the two Erp webhook records are later split into separate body headings (the inbound convention), Scope item 9 should be re-split to match — noted for whoever makes that call.
