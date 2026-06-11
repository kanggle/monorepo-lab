# Task ID

TASK-MONO-219

# Title

Author **ADR-MONO-027 PROPOSED** — wms → scm stock-replenishment loop (low-stock alert → reorder *suggestion*). Records the new cross-project coupling direction (wms→scm) that activates scm's v2-deferred `demand-planning-service`, plus the breakdown of follow-on scm tasks. Doc-only; ACCEPTED + implementation are separate user-gated tasks (sibling ADR-019/020/021 staged-child pattern).

# Status

ready

# Owner

architecture

# Task Tags

- docs
- adr

---

# Dependency Markers

- **triggered by**: user request 2026-06-11 — "재고 부족이 자동으로 보충 발주를 트리거하게 만들고 싶다".
- **sits alongside**: [ADR-MONO-022](../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) — the *forward* fulfillment loop. ADR-027 is the *replenishment* counter-loop; they share no order identity. No amend.
- **live precedent reused**: scm `inventory-visibility-service` ← wms inventory events (cross-project subscription pattern).
- **후속 (this task authors them, user-gated)**: TASK-MONO-220 (ACCEPTED transition) + scm BE-022/023 (spec) + BE-024/025 + INT-002 (impl, in scm backlog until their specs merge).

# Goal

Publish ADR-MONO-027 PROPOSED so the replenishment loop is implemented against a recorded decision (suggestion-only, demand-planning-owned mapping, scm-owned reorder policy) rather than an implicit one — with ACCEPTED and execution gated as separate user-explicit-intent tasks. Also place the follow-on scm task breakdown so the design is reviewable as a whole.

# Scope

## In Scope

- `docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md` (NEW, Status PROPOSED) — D1 Kafka subscribe to existing `wms.inventory.alert.v1` / D2 suggestion-only (operator-gated) / D3 demand-planning-owned `sku_supplier_map` / D4 scm-owned reorder policy distinct from wms threshold / D5 intra-scm DRAFT-PO materialization / D6 dedup (event + open-suggestion guard) / D7 demand-planning 3-facet boundary / D8 standalone degradation + §3 implementation plan.
- Author the follow-on scm task files (BE-022/023 in scm `ready/`; BE-024/025/INT-002 in scm `backlog/`) so the full breakdown is visible. (These are spec/impl placeholders; they do not run until ACCEPTED.)
- This `tasks/INDEX.md` (root) ready-list registration.
- Doc-only. NO schema/code change.

## Out of Scope

- ADR ACCEPTED transition → TASK-MONO-220 (separate, user-gated).
- Any `apps/` code, migration, or contract edit → Phase 1 scm impl tasks.
- wms producer change — none needed (alert already published); only a 1-line cross-project-consumer note in wms `inventory-events.md`, owned by TASK-SCM-BE-022.

# Acceptance Criteria

- **AC-1** ADR-MONO-027 exists with Status PROPOSED and D1–D8 CHOSEN-PROPOSED + §3 task plan.
- **AC-2** The decision driver names the user's intent and the three AskUserQuestion selections (suggestion-only / demand-planning-owned table / scope-through-federation-E2E).
- **AC-3** §1.3 explicitly distinguishes this loop from the ADR-022 backorder path (replenishment ≠ fulfilling the out-of-stock order).
- **AC-4** D2-b (auto-submit) recorded as rejected with the financial-commitment reason.
- **AC-5** Doc-only diff (no `apps/`, no migrations). Follow-on task files are markdown only.

# Related Specs

- [ADR-MONO-022](../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) (forward loop, ACL pattern, §D8 degradation precedent)
- [`platform/service-boundaries.md`](../../platform/service-boundaries.md) § Asynchronous cross-project events (the permitting rule)
- [`projects/scm-platform/PROJECT.md`](../../projects/scm-platform/PROJECT.md) § Service Map (`demand-planning-service` v2 entry being activated)

# Related Contracts

- [`projects/wms-platform/specs/contracts/events/inventory-events.md`](../../projects/wms-platform/specs/contracts/events/inventory-events.md) §7 `inventory.low-stock-detected` (the consumed signal — schema authoritative on the wms side; unchanged here).

# Edge Cases

- ADR numbering: origin/main latest is ADR-MONO-026 → this is 027. Verify no concurrent session claimed 027 before merge (parallel iam session active).
- The ADR must not push any pricing/promotion/payment concept into wms or any demand concept into procurement's PO lifecycle — boundaries stay clean (D7).

# Failure Scenarios

- If the ADR is authored AND `apps/` code is implemented in the same task → violates the decision-precedes-implementation rule. This task is doc-only; ACCEPTED (MONO-220) gates code.
- If a follow-on impl task is placed in `ready/` before its spec exists → violates scm INDEX backlog→ready move rule. BE-024/025/INT-002 stay in `backlog/` until BE-022/023 specs merge.

# Notes

- 분석=Opus 4.8 / 구현 권장=Opus (ADR authoring = 복잡 cross-domain 결합 결정). doc-only.
- Sibling staged-ADR precedent: MONO-164 (ADR-021 PROPOSED) → MONO-165 (ACCEPTED) → impl. Same shape here.
