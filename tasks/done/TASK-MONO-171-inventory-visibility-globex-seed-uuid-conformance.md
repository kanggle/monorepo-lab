# Task ID

TASK-MONO-171

# Title

Fix the `inventory-visibility-service` **globex demo seed** so the `node_id` / `last_event_id` / snapshot `id` values are valid **UUID strings** — the fed-e2e + console-demo seeds use human-readable ids (`e2e-node-globex-01`, `e2e-event-001`, …) that the producer's read-model reconstruction parses with `UUID.fromString(...)`, throwing `IllegalArgumentException` → an **unlogged 422 VALIDATION_ERROR** on `/snapshot` + `/staleness` for the globex tenant. This is the remaining blocker that keeps the platform-console **SCM 운영** page degraded (surfaced by TASK-MONO-170; SCM-BE-020 fixed only the procurement PO-list leg).

# Status

done

# Owner

integration / devops (root-owned demo + fed-e2e seed fixtures — DATA ONLY; no producer / contract / ADR / application change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy
- test

---

# Dependency Markers

- **surfaced by**: TASK-MONO-170 per-domain ops live demo + TASK-SCM-BE-020 (which fixed the procurement PO-list leg and explicitly scoped this inventory-visibility 422 OUT as a distinct runtime error needing reproduction). With the SCM gateway + console wiring + procurement decimals all working, the SCM 운영 page still degrades because `/api/inventory-visibility/snapshot` (and `/staleness`) return **422** for the globex tenant.
- **root cause (static + data-confirmed, NOT a producer bug)**: the producer always writes node/snapshot/event ids as `UUID` (`InventoryVisibilityApplicationService.resolveOrCreateNode` → `NodeId.of(UUID.randomUUID())`; `SnapshotId.generate()`). On read, the three repository `toDomain` reconstructors parse the persisted strings back with `UUID.fromString(...)`:
  - `NodeStalenessRepositoryImpl.toDomain` — `NodeId.of(UUID.fromString(node_id))` + `UUID.fromString(last_event_id)`
  - `InventorySnapshotRepositoryImpl.toDomain` — `SnapshotId.of(UUID.fromString(id))` + `NodeId.of(UUID.fromString(node_id))` + `UUID.fromString(last_event_id)`
  - `InventoryNodeRepositoryImpl.toDomain` — `NodeId.of(UUID.fromString(id))`
  The globex seed rows carry `id='e2e-node-globex-01'`, `last_event_id='e2e-event-001'`, snapshot `id='e2e-snapshot-globex-01'` — **not UUIDs** → `UUID.fromString` throws `IllegalArgumentException` ("Invalid UUID string: …") → `GlobalExceptionHandler.handleIllegalArgument` maps it to **422 VALIDATION_ERROR and does NOT log it** (only the generic `Exception` handler logs), which is exactly why it was opaque from logs alone.
- **why the "scm" tenant + BFF leg are 200**: the console-bff overview leg's token carries no `tenant_id` (→ `TenantClaimExtractor` default `"scm"`); the `"scm"` tenant has no malformed staleness/snapshot rows, so reconstruction never runs on a bad id. Only the globex assumed-tenant token (active-tenant scoping, ADR-MONO-020) makes the controller query `tenant_id='globex-corp'` → hits the bad rows.
- **why undetected by the federation gate**: the `tenant-switch-rescope` B-side scm leg hits the BFF→producer adapter path (`/api/scm/inventory/visibility`) and only asserts "NOT forbidden" — it never exercises the real `/api/inventory-visibility/snapshot` reconstruction with this seed (the MONO-162 leg-adapter-path gap meta-lesson). The console SCM 운영 page is the first consumer to hit the real read path with the globex tenant.
- **no dependency on**: any producer / console-bff / console-web application change; any contract / ADR change. The fix is the seed data conforming to the producer's UUID write-path invariant.

---

# Goal

Make the globex inventory-visibility seed rows reconstruct cleanly so the SCM 운영 page's inventory snapshot section renders live (no 422). Replace the human-readable `node_id` / `last_event_id` / snapshot `id` literals with valid, referentially-consistent UUID strings in both the fed-e2e fixture (the demo's running source) and the standalone console-demo seed.

# Scope

## In Scope

Root-owned seed fixtures — DATA ONLY:

1. **`tests/federation-hardening-e2e/fixtures/seed-scm-inv.sql`** — replace `e2e-node-globex-01` / `e2e-snapshot-globex-01` / `e2e-event-001` with valid UUIDs, kept referentially consistent across `inventory_nodes.id` ← `inventory_snapshots.node_id` + `node_staleness.node_id` (FK target) and `inventory_snapshots.id` / `last_event_id`.
2. **`scripts/console-demo/seed/07-scm-inventory.sql`** — same fix for the standalone-demo path (`demo-node-globex-01` / `demo-snapshot-globex-01` / `demo-event-001` → valid UUIDs), so the standalone demo cannot reintroduce the bug.
3. **Live re-seed** of the running `federation-hardening-e2e-scm-inv-postgres-1` (DELETE the malformed globex rows + INSERT the corrected ones) so the already-running demo stack serves correctly for the user browser smoke.

## Out of Scope

- **Producer robustness** — the unlogged 422-on-corrupt-data is a real diagnostic smell (corrupt-id reconstruction is arguably a 500-class server fault, not a 422 client error), but the `IllegalArgumentException → 422` mapping is a cross-cutting handler shared by the SCM services and ALSO serves legitimate client 422s; changing it is a separate, riskier concern. Documented as an observation, not changed here. The producer correctly rejects malformed data — the data is the bug.
- **The federation-gate leg-path gap** (the e2e scm leg hitting the BFF adapter path instead of the real `/snapshot`) — a separate hardening task (would have caught this); noted as follow-up.
- WMS alerts read-model seeding (separate documented follow-up).
- Any producer / contract / ADR / console change.

# Acceptance Criteria

- [x] **AC-1** `seed-scm-inv.sql` + `07-scm-inventory.sql` use valid UUID strings for every `id` / `node_id` / `last_event_id`, referentially consistent (snapshot.node_id == staleness.node_id == node.id within each file). (`e2e00000-…` / `de300000-…` namespaces.)
- [x] **AC-2** No non-UUID id literal remains in either seed (grep clean — `**/*.sql` scan returned 0).
- [x] **AC-3** Diff confined to the two seed `.sql` files (+ task lifecycle). No producer / application / contract / ADR change.
- [x] **AC-4** (live, data-layer) Running `federation-hardening-e2e-scm-inv-postgres-1` globex rows replaced (DELETE malformed + INSERT corrected); `SELECT` confirms valid-UUID `id`/`node_id`/`last_event_id`, referentially consistent. Defensive scan: 0 non-UUID rows across all tenants.
- [x] **AC-5** Full CI GREEN on PR #1039 (19 checks pass + 1 skip; the transient `scm-platform v1 cross-service smoke` Docker-Hub `eclipse-temurin:21-jre-alpine` registry i/o timeout cleared on re-run — unrelated to the data change). No spec/assertion references the old literals (they existed ONLY in the two seeds).
- [ ] **AC-6** (live, user browser) globex-corp active tenant → console **SCM 운영** inventory snapshot section renders live (no `scm_error` on `/api/inventory-visibility/snapshot`; combined with SCM-BE-020 the SCM 운영 page renders fully). — pending user browser smoke.

# Related Specs

- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md` — the `/snapshot` + `/staleness` response shapes; node ids are UUIDs. **Unchanged.**
- `projects/scm-platform/apps/inventory-visibility-service/.../adapter/outbound/persistence/adapter/*RepositoryImpl.java` — the `UUID.fromString` reconstruction the seed must satisfy.
- `tests/federation-hardening-e2e/fixtures/seed-scm-inv.sql` — the fed-e2e source the running demo loads.

# Related Contracts

- `inventory-visibility-api.md` — byte-unchanged; the seed is brought into conformance with the producer's UUID id invariant.

# Edge Cases

- FK consistency: `inventory_snapshots.node_id` references `inventory_nodes.id` — both must change to the same UUID.
- `ON CONFLICT DO NOTHING` idempotency preserved; the live re-seed DELETEs the old (different-id) rows first, otherwise the stale malformed rows would remain alongside the new ones.
- The two seed files keep distinct UUID namespaces (`e2e…` vs the demo set) — they load into different DBs, no collision.

# Failure Scenarios

- Changing only one of the three id columns → FK break or partial reconstruction failure. Mitigation: AC-1 requires referential consistency across all three tables.
- A spec asserting the old literal id → gate break. Mitigation: AC-5 + the pre-change grep (confirmed the literals appear ONLY in the two seed files).

# Test Requirements

- AC-1…AC-4 verified in-session (grep + live `SELECT` against the running container).
- AC-5 = federation gate green on the PR.
- AC-6 = user browser smoke (the runbook is `console-demo:up` already up).

# Definition of Done

- [ ] Both seeds use valid referentially-consistent UUIDs.
- [ ] Live `scm-inv-postgres` re-seeded + `SELECT` confirms.
- [ ] Diff confined to the two `.sql` files; producer/contract/ADR untouched.
- [ ] Federation gate green; PR merged with 3-dim verification.
- [ ] Task md + `tasks/INDEX.md` updated.
- [ ] AC-6 (browser) confirmed by the user.

---

분석=Opus 4.8 / 구현=Opus(직접, 정적 체인 + 라이브 데이터 확정). 같은 MONO-170 데모가 노출한 3번째이자 마지막 producer↔consumer drift — 단, 여기서는 producer 가 아니라 **시드 데이터가 producer 의 UUID write-path 불변식을 위반**한 케이스. green-wash 금지: 데이터-레이어까지 라이브 재시드 검증, 브라우저 200 은 사용자 AC.
