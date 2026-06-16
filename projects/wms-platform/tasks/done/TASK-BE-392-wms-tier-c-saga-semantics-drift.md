# TASK-BE-392 — wms spec Tier C drift-fix (code-verified saga / state-machine semantics from TASK-BE-385 § Findings)

Status: done

## Goal

Close the **Tier C drift findings** that TASK-BE-385 (wms spec refactor pass) flagged but could not auto-fix under the `/refactor-spec` meaning-preserving constraint, and that TASK-BE-387 explicitly deferred (it closed only Tier A/B value-name drift). Tier C is **config / state-machine semantics** drift: wms specs whose diagrams, terminal-state lists, config defaults, or movement-reason mappings have diverged from the **implemented truth**. Each fix direction was **verified against the actual production code / config before editing** (the discovery agents' assertions alone were not trusted — several BE-385 findings were imprecise, see § Code verification).

This is a doc-only follow-up: `git diff origin/main -- 'projects/wms-platform/apps/**'` MUST be empty.

## Scope

Five Tier C findings (TASK-BE-385 § Findings Tier C), each fixed to match code/config:

1. **Sweeper cap default** — `outbound-service/sagas/outbound-saga.md` §9 Open Questions item 2 says the sweeper attempt cap "starts at 10". The shipped default is **5** (`outbound.saga.sweeper.max-attempts: 5` in `application.yml`; the same doc's §4.2/§4.5 already say 5). The open question is resolved by the implementation. Correct it.
2. **`STUCK_RECOVERY_FAILED` missing from saga diagrams** — the `SagaStatus` enum and `saga-status.md` States table both carry the terminal `STUCK_RECOVERY_FAILED` state, but it is absent from (a) `saga-status.md` ASCII + Mermaid diagrams, (b) `domain-model.md` §6 ASCII, and (c) `domain-model.md` §6 terminal-states sentence (which lists only `RESERVE_FAILED, CANCELLED, COMPLETED`). Add the sweeper-exhaustion transition + terminal node to the diagrams and the terminal list. Also `domain-model.md` "## Open Items" lists already-authored artifacts (saga-status.md, outbound-saga.md, workflows, idempotency.md, external-integrations.md, error-code registrations) as if still open — `architecture.md` § Open Items (Retrospective Backfill Audit) marks every one ✅ done. Mark the stale list resolved.
3. **Non-existent "force-complete" operation** — `saga-status.md` line 35 (`SHIPPED_NOT_NOTIFIED` row) says the saga leaves the state via `:retry-tms-notify` success "or operator force-completes". There is no v1 force-complete; the authoritative Transition Rules table in the same doc lists exactly two `SHIPPED_NOT_NOTIFIED → COMPLETED` triggers (`inventory.confirmed`, or `:retry-tms-notify` success). Remove the phantom operation.
4. **MANUAL release movement reason_code** — `inventory-service/sagas/reservation-saga.md` §2.2 and `state-machines/reservation-status.md` MANUAL row state that a MANUAL reservation release writes a movement with `reason_code = ADJUSTMENT_RECLASSIFY` (saga.md lists it among the release reasons; status.md says "ADJUSTMENT_RECLASSIFY or PICKING_CANCELLED per ops policy"). The implementation (`ReleaseReservationService.reasonCodeFor`) maps `MANUAL -> PICKING_CANCELLED` **unconditionally** (no ops-policy branch); `ADJUSTMENT_RECLASSIFY` is used **only** by the unrelated `StockAdjustment` reclassification flow. Correct both docs to `PICKING_CANCELLED`.
5. **Lot `EXPIRED` terminal label** — `master-service/domain-model.md` §6 lot state-machine ASCII labels the node `EXPIRED (terminal)` while the same diagram draws `EXPIRED → INACTIVE` and the line below already states "`EXPIRED` is terminal for reactivation. Can still be deactivated to hide from listings." The bare "(terminal)" contradicts the outgoing arrow. Align the diagram label to the (already-correct) prose + the `LotStatus`/`Lot` code (`EXPIRED` is terminal-for-reactivation only).

## Related Specs

- `projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md`
- `projects/wms-platform/specs/services/outbound-service/state-machines/saga-status.md`
- `projects/wms-platform/specs/services/outbound-service/domain-model.md`
- `projects/wms-platform/specs/services/inventory-service/sagas/reservation-saga.md`
- `projects/wms-platform/specs/services/inventory-service/state-machines/reservation-status.md`
- `projects/wms-platform/specs/services/master-service/domain-model.md`

## Related Contracts

None changed. No event payload, topic name, REST shape, enum **value**, or Flyway/DDL is altered. The fixes correct prose/diagram/config-default **restatements** to match the canonical implementation; the canonical contracts (`contracts/events/*`, `contracts/http/*`) are untouched.

## Code verification (fix direction is implementation-anchored)

- **(1) cap = 5**: `apps/outbound-service/src/main/resources/application.yml` → `outbound.saga.sweeper.max-attempts: 5` ("cap before STUCK_RECOVERY_FAILED"). `outbound-saga.md` §4.2 already documents "default 5". The architecture.md "Pool size starts at 10" (line 457) is the **TMS bulkhead thread pool** (correct, 10) — NOT touched.
- **(2) STUCK_RECOVERY_FAILED terminal**: `apps/outbound-service/.../domain/model/SagaStatus.java` — terminal set = `{COMPLETED, CANCELLED, RESERVE_FAILED, STUCK_RECOVERY_FAILED}`; `OutboundSaga.markStuckRecoveryFailed(...)`. Swept-from states per `outbound-saga.md` §4.3 = `{REQUESTED, CANCELLATION_REQUESTED, SHIPPED}`. `architecture.md` lists all backfill artifacts ✅ done.
- **(3) no force-complete**: `saga-status.md` Transition Rules row `SHIPPED_NOT_NOTIFIED → COMPLETED` triggers = `InventoryConfirmedConsumer` OR `RetryTmsNotifyUseCase` success. No force-complete use-case exists; `:force-fail` is a v2-planned admin endpoint (→ STUCK, not COMPLETED) per `outbound-saga.md` §6.
- **(4) MANUAL → PICKING_CANCELLED**: `apps/inventory-service/.../application/service/ReleaseReservationService.java` `reasonCodeFor`: `CANCELLED → PICKING_CANCELLED`, `EXPIRED → PICKING_EXPIRED`, `MANUAL → PICKING_CANCELLED`. `ADJUSTMENT_RECLASSIFY` appears only in `StockAdjustment` (reclassify-between-buckets), never the release path.
- **(5) EXPIRED terminal-for-reactivation**: `apps/master-service/.../domain/model/Lot.java` (`deactivate`: `ACTIVE`/`EXPIRED → INACTIVE`; `reactivate`: `INACTIVE → ACTIVE` only — `EXPIRED → reactivate` throws) + `LotStatus.java` javadoc ("EXPIRED is terminal for reactivate; EXPIRED → INACTIVE is permitted"). `domain-model.md` line 304 already states this correctly; only the ASCII label lags.

## Edge Cases

- The string "starts at 10" occurs twice in outbound specs with different meanings: sweeper cap (drift, fix) vs TMS bulkhead pool size (correct, leave). Only the sweeper-cap instance is corrected.
- `SHIPPED_NOT_NOTIFIED` IS already present in both saga diagrams — the BE-385 finding bundled it with `STUCK_RECOVERY_FAILED`, but only the latter is genuinely missing. Verified per-token; no spurious edit to SHIPPED_NOT_NOTIFIED's depiction.

## Failure Scenarios

- **F1 — "fixing" a correct restatement in the wrong direction**: guarded by anchoring every edit to the cited code/config (§ Code verification), not to the discovery agent's claim. Where a finding was imprecise (SHIPPED_NOT_NOTIFIED already present; architecture.md bulkhead "10" correct), the edit is narrowed or skipped.
- **F2 — touching apps/** or a contract value**: guarded by AC-2 (doc-only, `git diff apps/**` empty) — diagrams/prose only, no enum value / topic / DDL change.

## Out of scope (deferred — TASK-BE-385 § Findings Tier D / Tier E)

- **Tier D** identical-recap dedup (inline event/topic tables in `*/architecture.md` → cross-refs) — verify-identity-per-file refactor, low urgency; not semantics drift.
- **Tier E** notification-service `domain-model.md` missing sibling-standard sections + non-numbered idempotency H2s — requires authoring, not drift-fix.

## Acceptance Criteria

- **AC-1 (code-verified)** — every applied change matches the production code / config / domain rule cited in § Code verification. No fix relies on a discovery agent's claim alone.
- **AC-2 (doc-only / meaning-preserving)** — `git diff origin/main -- 'projects/wms-platform/apps/**'` is empty. No Flyway, no enum **value**, no contract payload/topic, no behavior change. All edits under `specs/`.
- **AC-3 (no over-reach)** — the TMS bulkhead "pool size starts at 10" (architecture.md), the v2-planned `:force-fail` admin endpoint, the genuine `ADJUSTMENT_RECLASSIFY` reclassification flow, and the already-present `SHIPPED_NOT_NOTIFIED` depictions are left intact.
- **AC-4 (diagrams ↔ code consistent)** — after the fix, the `STUCK_RECOVERY_FAILED` terminal state and the `EXPIRED` terminal-for-reactivation semantics appear consistently across the prose, the terminal lists, and the ASCII/Mermaid diagrams, matching the enums in code.
- **AC-5 (no new dead refs)** — no cross-reference or anchor is broken by the edits (drift-fix touches inline content, not headings/filenames).
