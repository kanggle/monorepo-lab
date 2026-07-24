# TASK-SCM-BE-047 — 3PL node read-only stock observation (ingestion + staleness)

**Status:** review
**Type:** TASK-SCM-BE
**Depends on / 전제:** [TASK-SCM-BE-046](../done/TASK-SCM-BE-046-third-party-logistics-node-factory.md) **done** (the `THIRD_PARTY_LOGISTICS` node is now constructible + registered) · [ADR-MONO-054](../../../../docs/adr/ADR-MONO-054-third-party-logistics-node-activation.md) **ACCEPTED** §D4 (3PL stock observed **read-only, never operated**).
**후속 / blocks:** none required. BE-048 (honour a 3PL-destined inbound-expected) is **independent** and separately gated — do not couple.

> **Additive, no-Flyway, read-model-only.** The `InventorySnapshot` and `NodeStaleness` models carry **no node-type column** (verified) — they already accept rows for a `THIRD_PARTY_LOGISTICS` node, and the read side (`GET /snapshot`, `/nodes`, `/sku`) already surfaces any node type with no WMS filter. The only missing piece is an **ingestion path** to record observed 3PL stock against an already-registered 3PL node, plus a staleness-row seed. This task adds exactly that.

---

## Why (the gap this closes)

A `THIRD_PARTY_LOGISTICS` node is registrable (BE-046) but **empty** — `inventory-visibility-api.md` says so explicitly ("the registered 3PL node is empty (no stock) … stock observation is TASK-SCM-BE-047"). Stock reaches a WMS node via three `wms.inventory.*` Kafka consumers → `applyInventoryReceived/Adjusted/Transferred` → `applySnapshotDelta`. **A 3PL has no `wms.*` event stream** (its own WMS is authoritative — ADR-050 §D4), so there is no path to record what we *observe* at the 3PL. This task adds the minimal observation ingestion (ADR-054 §D4 "read APIs or periodic snapshots in Phase 2a's minimal form").

## Scope

**In scope:**

1. **Observation ingestion — an operator/internal REST push** (contract-first).
   `POST /api/inventory-visibility/nodes/{nodeId}/observed-stock` (or a clear equivalent under the mutating surface, kept off the read-only `InventoryVisibilityController`). Body = a **full snapshot** of the 3PL node's stock: a list of `{ skuCode, quantity }` (absolute quantities, an observed point-in-time reading). tenant from JWT (fail-closed, same as `POST /nodes`). Contract row precedes code.
2. **Application method** `applyThirdPartyObservedStock(nodeId, tenantId, List<{sku,qty}>, observedAt, ...)`:
   - Resolve the **existing** node by id; require `NodeType.THIRD_PARTY_LOGISTICS` and tenant match. **Absent or wrong-type → reject** (do NOT call `resolveOrCreateNode` — it auto-registers as `WMS_WAREHOUSE`).
   - Write each SKU as an **absolute** quantity via `InventorySnapshot.applyQuantity(...)` (the absolute setter that exists but is currently unused) — a 3PL observation is a full reading, not a delta. Upsert per `(nodeId, sku, tenant)`.
   - **Idempotency/ordering:** carry an observation id or `observedAt` timestamp so a stale/replayed reading does not overwrite a newer one (mirror the `lastEventAt` guard the snapshot already tracks). Reuse the existing dedupe/version machinery where it fits.
   - **Seed/refresh the `NodeStaleness` row** for the node (via the existing `updateStaleness` path or an equivalent) so the node **joins the staleness lifecycle** — a 3PL node that stops being observed goes `STALE`/`UNREACHABLE` exactly like a WMS node (the staleness batch is already node-type-agnostic).
3. **Read side** — no change needed (already surfaces any node type). **Verify** the 3PL node's snapshots appear in `GET /snapshot`, `GET /snapshot?nodeId=`, `GET /sku/{sku}`, and `GET /nodes` — add/adjust a test asserting a 3PL node with observed stock is visible cross-node.
4. **Tests** — unit: the app method writes absolute snapshots for a 3PL node, rejects absent/wrong-type node, seeds staleness, ignores an older `observedAt`. Slice `@WebMvcTest` on the ingestion endpoint (200/accepted; 404 unknown node; 409/422 wrong-type; tenant fail-closed). IT (Testcontainers): observe → snapshot rows present + node visible in cross-node read + staleness row created (CI-only on Windows).

**Out of scope:**

- **BE-048** (honour a 3PL-destined inbound-expected / producer-side routing) — independent, separately scoped; do not touch procurement/demand-planning here.
- **Any real 3PL vendor read-API adapter / polling** — the minimal form is the push endpoint; a periodic vendor-read adapter is a later refinement (ADR-054 §D4 allows either; pick the push for minimality).
- **Mutating the 3PL's stock** — we record an **observation**; we never pick/pack/adjust/transfer at a 3PL (ADR-054 §D4).
- **Delta semantics for 3PL** — 3PL observation is absolute; do not route it through `applySnapshotDelta`.
- **Any Flyway/schema change** — snapshot/staleness tables carry no node-type column; nothing to migrate.
- **Auto-registering the 3PL node on ingestion** — the node must already exist (BE-046); reject if not (no `resolveOrCreateNode`).

## Acceptance Criteria

- [ ] `POST .../nodes/{nodeId}/observed-stock` records absolute snapshots for an existing `THIRD_PARTY_LOGISTICS` node; contract row in the canonical `inventory-visibility-api.md` (+ gateway route) lands **before/with** the code.
- [ ] Ingestion against an **unknown** node → 404; against a **non-3PL** node (e.g. a WMS node id) → 409/422 (never silently mutate a WMS node; never auto-register).
- [ ] Observed SKUs appear in `GET /snapshot` (cross-node), `?nodeId=`, and `GET /sku/{sku}` for the 3PL node.
- [ ] A `NodeStaleness` row is created/refreshed on observation; the node participates in the existing staleness detection (goes STALE when observation stops) — verified.
- [ ] An older `observedAt` (stale/replayed reading) does not overwrite a newer stored quantity.
- [ ] **No Flyway, no delta path, no `resolveOrCreateNode`, no procurement/demand-planning change, no 3PL stock mutation.**
- [ ] Build & Test + scm Integration CI lanes **GREEN** (CI authority for IT).

## Related Specs

- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` — add the observation-ingestion path to the read-model description (still read-only *toward the 3PL*; we ingest our observation).
- `projects/scm-platform/specs/services/inventory-visibility-service/data-model.md` — note 3PL snapshots are absolute observations (no delta), staleness applies.
- `docs/adr/ADR-MONO-054-third-party-logistics-node-activation.md` §D4.

## Related Contracts

- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md` — **canonical**; add the `POST .../observed-stock` section (request/response, 200/404/409|422) **first**.
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` § inventory-visibility-service — add the route row.
- No event contract (the observation is a REST push, not an event — a 3PL has no event stream).

## Edge Cases

- **Absent/wrong-type node** — reject; never `resolveOrCreateNode` (that auto-registers WMS). A WMS node id must not be writable via this endpoint.
- **Stale reading** — an observation with an older `observedAt` than the stored snapshot must not overwrite (last-observation-wins by time, mirroring `lastEventAt`).
- **Empty observation** — a reading of zero stock is valid (SKU dropped to 0), not a no-op; distinguish "0 observed" from "not observed" via the snapshot quantity, and staleness still refreshes (we *did* observe).
- **Staleness seed timing** — a node registered (BE-046) but never observed has **no** `NodeStaleness` row and is absent from the staleness loop; first observation must create it (decide whether registration should also seed it — if so, note it, but keep the write here).
- **Tenant fail-closed** — same OAuth2 RS + tenant rule as the read endpoints and `POST /nodes`; no bespoke gate.

## Failure Scenarios

- **A — Delta leak.** Routing 3PL observation through `applySnapshotDelta` would make repeated observations accumulate instead of set. Use the absolute `applyQuantity`.
- **B — WMS node clobbered.** If the endpoint accepts any nodeId and calls `resolveOrCreateNode`, an operator could mutate a WMS node's snapshot or spawn a phantom WMS node. Resolve-existing-3PL-only, reject otherwise.
- **C — Node silently absent from staleness.** If the observation path doesn't seed `NodeStaleness`, the 3PL node never goes STALE and the "read-only observation with freshness" promise (ADR-054 §D4) is unmet. Seed it.
- **D — Contract after code.** The `inventory-visibility-api.md` row must precede/accompany the endpoint.
- **E — Scope creep into BE-048.** Any procurement/demand-planning/inbound-expected change means the task absorbed BE-048. Stop.

---

**Recommended models** (분석=Opus 4.8 / 구현 권장): additive ingestion endpoint + one app method reusing existing snapshot/staleness machinery + tests, no Flyway, no cross-service coupling → **Sonnet** sufficient. Escalate only if the `observedAt`/version ordering against the existing snapshot version machinery needs a design call.
