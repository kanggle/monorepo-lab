# TASK-SCM-BE-046 — activate the `THIRD_PARTY_LOGISTICS` node: factory + explicit registration (no Flyway)

**Status:** review
**Type:** TASK-SCM-BE
**Depends on / 전제:** [ADR-MONO-054](../../../../docs/adr/ADR-MONO-054-third-party-logistics-node-activation.md) **ACCEPTED** §D2/§D4 (the Phase-2a Surface-A activation this task starts) · [ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4 (a 3PL warehouse is operated by the 3PL's own WMS — observed, never operated).
**후속 / blocks:** [TASK-SCM-BE-047] (3PL read-only observation — node staleness + snapshot ingestion) and [TASK-SCM-BE-048] (honour a 3PL-destined inbound-expected) both need this node to be constructible. **Surface B is NOT unblocked by this task** — the outbound 3PL fulfillment path stays deferred on ADR-054 §D5's named missing owner.

> **Small, contained, no-Flyway slice.** The `THIRD_PARTY_LOGISTICS` enum value, its JPA mapping, and the `ck_inventory_nodes_type` CHECK **already exist** (ADR-054 §1.2). This task adds only the **missing constructor path** — a `registerThirdPartyLogistics(...)` factory — plus an **explicit registration** trigger (a 3PL relationship is an onboarding fact, not an event side-effect). Read-only observation of the node's stock is the *next* task (BE-047); this one just makes the node exist.

---

## Why (the gap this closes)

`THIRD_PARTY_LOGISTICS` is declared at every layer except the one that constructs it (ADR-054 §1.2):

| Layer | State |
|---|---|
| `NodeType.THIRD_PARTY_LOGISTICS` | present |
| `InventoryNodeJpaEntity.NodeTypeJpa` | present |
| DB `ck_inventory_nodes_type` CHECK | includes it |
| **`InventoryNode` factory** | **only `autoRegisterWmsWarehouse(...)` — no 3PL path** |

A wms warehouse node is auto-registered on first `wms.inventory.*` mutation event. A 3PL node has **no such event stream** — it is born from an onboarding decision — so it must be **explicitly registered**, not auto-created. This task adds that path.

## Scope

**In scope:**

1. **Spec first (separate commit/PR per the scm PR Separation Rule)** — update
   `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md`
   and `data-model.md` to document: (a) `THIRD_PARTY_LOGISTICS` nodes are
   **explicitly registered** (not auto-registered), and (b) they are **observed
   read-only, never operated** (ADR-054 §D4 / ADR-050 §D4). The node type is
   already in the data model; this documents its *activation* and its read-only stance.
2. **Domain factory** — add `InventoryNode.registerThirdPartyLogistics(...)` sibling
   to `autoRegisterWmsWarehouse`, producing a `NodeType.THIRD_PARTY_LOGISTICS` node in
   `NodeStatus.ACTIVE`. It takes the tenant, an external 3PL node id, and a display
   name (a 3PL node **is** named at registration, unlike an auto-registered warehouse
   whose name starts empty). `warehouseCode` is **not** applicable (that is a wms
   business code) — leave it null.
3. **Registration use case + trigger** — an explicit `RegisterThirdPartyLogisticsNode`
   application use case that persists the node via the existing
   `InventoryNodePersistencePort`, **idempotent** on `(tenant_id, node_external_id)`
   (the existing `uq_inventory_nodes_tenant_external` unique constraint — a repeat
   registration is a no-op, not a duplicate). Expose it as a small **operator/internal
   endpoint** (contract-first — see Related Contracts) **or** a seed, per the
   implementer's read of the existing registration surface; if an endpoint is added,
   the contract row precedes the code.
4. **Tests** — unit: the factory produces a `THIRD_PARTY_LOGISTICS`/`ACTIVE` node with
   the given external id + name and null `warehouseCode`. Application/slice: registration
   persists once and is idempotent on a repeat (no second row, no exception). If an
   endpoint is added, a `@WebMvcTest` slice + tenant fail-closed (reuse the existing
   `InventoryVisibilityController` security config — no new auth surface).

**Out of scope:**

- **3PL stock observation** (node staleness, snapshot ingestion, read APIs) → **TASK-SCM-BE-047**. This task makes the node exist; it does **not** feed it any inventory.
- **Honouring a 3PL-destined inbound-expected** (the scm producer-side routing so a 3PL PO's expectation goes to the 3PL path, not the wms consumer) → **TASK-SCM-BE-048**. The wms whitelist gate stays untouched by this task.
- **Any Flyway/schema change** — the enum value + CHECK already accept `THIRD_PARTY_LOGISTICS` (ADR-054 §1.2). If this task needs a migration, stop and re-scope.
- **Any outbound 3PL fulfillment / `ThirdPartyFulfillmentPort` / FulfillmentRouter change** — Surface B, deferred (ADR-054 §D5/§D7).
- **Mutating 3PL stock** — a 3PL node is observed read-only, never picked/packed/adjusted/transferred by us (ADR-054 §D4).

## Acceptance Criteria

- [ ] `inventory-visibility-service` spec (architecture.md + data-model.md) documents `THIRD_PARTY_LOGISTICS` as **explicitly registered** and **read-only-observed**, landed **before/separate from** the impl (specs win; PR Separation Rule).
- [ ] `InventoryNode.registerThirdPartyLogistics(...)` exists and produces a `THIRD_PARTY_LOGISTICS` / `ACTIVE` node (unit-tested), `warehouseCode` null.
- [ ] Registration persists the node and is **idempotent** on `(tenant_id, node_external_id)` — a repeat registration is a no-op (verified against `uq_inventory_nodes_tenant_external`), not a duplicate row or a 500.
- [ ] **No Flyway/schema change, no new error code beyond what registration idempotency needs, no new dependency.**
- [ ] **wms whitelist gate untouched**, no 3PL stock ingestion, no outbound/FulfillmentRouter change (those are BE-047 / BE-048 / Surface B).
- [ ] Build & Test + scm Integration CI lanes **GREEN** (CI is authority for IT).

## Related Specs

- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` — node registration model (auto vs explicit); add the 3PL read-only-observation stance.
- `projects/scm-platform/specs/services/inventory-visibility-service/data-model.md` — `inventory_nodes.node_type` domain (already lists `THIRD_PARTY_LOGISTICS`); document its activation.
- `docs/adr/ADR-MONO-054-third-party-logistics-node-activation.md` §D2 (factory), §D4 (read-only observation), §1.2 (what already exists — no Flyway).
- `docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md` §D4 (a 3PL is operated by its own WMS — the read-only rationale).

## Related Contracts

- **If** registration is exposed as an operator/internal endpoint: `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` § `inventory-visibility-service` (or the internal-routes contract that service uses) — the new route row **precedes** the code (contract-first). **If** registration is seed/config-only: no contract change (record the choice in the PR).
- No event contract: a 3PL node is **not** born from an event (unlike wms warehouse nodes), so there is no subscription to add.

## Edge Cases

- **Idempotent registration.** `uq_inventory_nodes_tenant_external` already prevents a duplicate `(tenant_id, node_external_id)`. A repeat registration must be a **no-op returning the existing node**, not a unique-violation 500. Handle it in the use case (find-or-register), don't let the constraint surface as an error.
- **`warehouseCode` is wms-only.** A 3PL node has no wms business warehouse code — leave it null; do **not** repurpose the ADR-050 D9 field.
- **Name is required at registration** (unlike auto-registered warehouse nodes, which start with an empty name enriched later) — a 3PL relationship is known by name when onboarded.
- **Status starts `ACTIVE`.** A newly onboarded 3PL is active; `SUSPENDED`/`DECOMMISSIONED` transitions are existing node-status machinery, not new to this task.
- **No auto-registration path.** Do **not** wire 3PL node birth into any `wms.inventory.*` consumer — there is no such event, and coupling it there would be wrong (ADR-054 §D2).

## Failure Scenarios

- **A — Surface B leaks in.** Any `ThirdPartyFulfillmentPort`, FulfillmentRouter 3PL-arm activation, or outbound fulfillment code means the task absorbed the deferred Surface B (ADR-054 §D5). Stop and split — this task is node-existence only.
- **B — A Flyway migration appears.** The enum + CHECK already accept the value (ADR-054 §1.2). A migration signals a misread of the schema; stop and verify against `V1__init.sql:20`.
- **C — Stock observation bundled in.** Feeding the 3PL node any inventory (snapshots, staleness, read APIs) is BE-047. This task registers an **empty** node.
- **D — The wms gate is widened.** "Honouring" a 3PL destination is **routing away from wms** (BE-048), not teaching the wms `CreateScmInboundExpectationService` gate to accept `THIRD_PARTY_LOGISTICS`. If this diff touches that gate, it is the wrong task (ADR-054 §D3/§A1).
- **E — Registration made non-idempotent.** A create-only endpoint that 500s on re-registration is a fragile onboarding surface; the AC requires find-or-register.

---

**Recommended models** (분석=Opus 4.8 / 구현 권장): a domain factory + an idempotent registration use case + spec update, no Flyway, no cross-service coupling — well-bounded → **Sonnet** sufficient. Escalate to Opus only if the existing registration surface (auto-register path) turns out to entangle the explicit path in a way that needs a design call.
