# Task ID

TASK-BE-661

# Title

inventory-service dev seed has no WH01 warehouse snapshot — the SKU-APPLE-001 low-stock alert leaves with `warehouseCode` null, so scm's replenishment suggestion has no warehouse code and its PO would emit no wms inbound-expected

# Status

done (2026-10-28 UTC — 4-dim verified · impl PR #4568 squash d5e2928b1 · 36차 창)

# Owner

backend

# Task Tags

- code
- bug
- wms
- demo-seed

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (one idempotent seed row + one parity cell; no production code)
> Drafted 2026-10-26 UTC. Owner said «진행» to «①(scm 제안 warehouseId 발견)을 굽기 전에 처리» on 2026-10-26 UTC. Facts below read on origin/main `23af942f3`. 🔴 Bake target — the seed is a Flyway repeatable in the inventory-service jar, so the demo sees it only after the next AMI bake.

# Dependency Markers

- 출처: 35th demo window (`TASK-MONO-672` § «35th window — results», `TASK-SCM-BE-062` § CORRECTION 35차). The scm suggestion raised from the SKU-APPLE-001 low-stock alert came back with `warehouseId` = the wms **location** id and **no `warehouseCode` key**.
- 관련(설계, 결함 아님): `warehouseId` holding the location id is deliberate — ADR-MONO-050 D9 (`ReorderSuggestion.java:15-32,50`: dedup key, «never emitted downstream for a WMS_WAREHOUSE destination»). The defect is only the **missing `warehouseCode`**, the field that does flow to the PO.
- 관련: `TASK-MONO-768` (added the WH-MAIN snapshot rows to this same seed for the ecommerce path).

# Goal

On the demo stack, a low-stock alert for SKU-APPLE-001 (warehouse WH01) carries `warehouseCode = "WH01"`, so the scm suggestion carries `warehouseCode` and a PO materialised from it can address wms by code — the only end-to-end scm replenishment chain the demo has.

# Scope

## In Scope

1. `projects/wms-platform/apps/inventory-service/src/main/resources/db/seed/R__seed_dev_masterref.sql`: add the WH01 `warehouse_snapshot` row — id `01910000-0000-7000-8000-000000000001`, code `WH01`, status `ACTIVE` — idempotent (`ON CONFLICT (id) DO NOTHING`, same shape as the WH-MAIN row at `:101-110`). Values must equal master-service `R__01_seed_dev_warehouse.sql` and the sibling snapshots (outbound `R__seed_dev_masterref.sql:157-158`, inbound `:27-31`, admin `:73-74`).
2. A parity cell (in `EcommerceSeedParityTest` or a sibling seed test in inventory-service) that fails today: **every `warehouse_id` referenced by inventory's seeded `location_snapshot` rows has a `warehouse_snapshot` row in the same file**, and its code equals master's. Choose the closure predicate, not a WH01-literal check, so the next location added under a new warehouse is caught too.
3. Update the seed's header comment (`:17-21`, «one Location, one SKU, one Lot») to say the warehouse row is now seeded and why (`LowStockDetectionService` / `ReceiveStockService` stamp `warehouseCode` from this table).

## Out of Scope

- The scm field naming (`warehouseId` = location id) — ADR-MONO-050 D9 decision.
- Production code in `LowStockDetectionService` (null-code best-effort behaviour stays — it is correct for a genuine startup race).
- Backfilling already-raised suggestions on a live volume (the demo volume is fresh per bake).
- admin-service's alert projection showing `warehouseId: null` — a separate read-model question; record if seen, do not fix here.

# Acceptance Criteria

- [x] **AC-0** — re-read on the starting commit: inventory seed has no WH01 `warehouse_snapshot` row; the SKU-APPLE location `…1001` names warehouse `…0001`; `LowStockDetectionService.java:94-95` and `ReceiveStockService` read `warehouseCode` from `warehouse_snapshot`; scm contract `demand-planning-api.md:96-101` says `warehouseCode` is present only when the raise path learned a code. Record any line that differs.
- [x] **AC-1 (수정 전 빨강)** — the parity cell from Scope 2 is red on today's seed (show the failing assertion) and green after the row is added.
- [x] **AC-2** — the row's id and code equal master-service and the three sibling snapshots (asserted by the cell, not by inspection).
- [x] **AC-3** — `./gradlew :projects:wms-platform:apps:inventory-service:test` (exact path from `settings.gradle`) rc=0; existing `EcommerceSeedParityTest` cells unchanged and green.
- [x] **AC-4 (라이브, 굽기 뒤 창)** — fresh volume: drop SKU-APPLE-001 below 10 (`TASK-MONO-672` § 35th preparation step 3) → the scm suggestion carries `"warehouseCode":"WH01"`. ⚪ with the reason if the window cannot run step 3. Undo per that section.

# Related Specs

- `projects/wms-platform/specs/services/inventory-service/architecture.md` (master read-model snapshots)
- ADR-MONO-050 D9 (cross-service identifiers are codes)

# Related Contracts

- `projects/wms-platform/specs/contracts/events/inventory-events.md` — `inventory.low-stock-detected` / `wms.inventory.alert.v1` payload (`warehouseCode` nullable, best-effort)
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md` § suggestions (`warehouseCode` present-when)

No contract change.

# Edge Cases

- The repeatable re-runs over a live volume on checksum change — the row must be `ON CONFLICT DO NOTHING` (a master event may already have written the row with a newer `master_version`; do not overwrite it).
- A location row pointing at a warehouse id absent from master entirely — the parity cell should report it as a seed error, not silently pass.

# Failure Scenarios

1. The row is added with a code differing from master (`WH-01`, `wh01`) — scm addresses the PO by a code wms cannot resolve (`findWarehouseByCode` miss). AC-2 pins equality.
2. The check pins only `WH01` by literal — the next warehouse added to the seed repeats this defect. Scope 2 asks for the closure predicate.
3. Fixed in the seed but the live AC is read before a bake — the old jar still lacks the row and the window reports «not fixed». AC-4 is explicitly after the bake.

# Implementation (2026-10-26 UTC)

- AC-0 re-read on `d9cc1ef24`: inventory seed had no WH01 `warehouse_snapshot` row (only WH-MAIN); location `...1001` names warehouse `...0001`; no line differed from the task text.
- Change: `inventory-service/src/main/resources/db/seed/R__seed_dev_masterref.sql` — added `warehouse_snapshot ('01910000-0000-7000-8000-000000000001', 'WH01', 'ACTIVE', '2026-04-18T00:00:00Z', 0) ON CONFLICT (id) DO NOTHING` + header comment. Test: new cell `everyLocationWarehouseHasSnapshotWithMastersCode` in `EcommerceSeedParityTest` (closure predicate: every `warehouse_id` of inventory's seeded `location_snapshot` rows has a `warehouse_snapshot` row in the same file, code equal to master `warehouses`, and outbound / inbound / admin hold the same id->code). No production Java change.
- Red before fix (cell run on the seed without the row, gradle rc=1): `location WH01-A-01-01-01 names warehouse 01910000-0000-7000-8000-000000000001 (master code WH01) but inventory's seed has no warehouse_snapshot row for it — Expecting actual: {"01910000-0000-7000-8000-000000000002"="WH-MAIN"} to contain key: "01910000-0000-7000-8000-000000000001"`.
- Green after: `./gradlew :projects:wms-platform:apps:inventory-service:test --tests '*EcommerceSeedParityTest*'` rc=0; full `./gradlew :projects:wms-platform:apps:inventory-service:test` rc=0.
- AC-4 (live, after bake) remains open.


---

## CORRECTION — 36차 창 라이브 결과 (2026-10-28 UTC)

- **AC-4 ✅.** 36차 창: 굽기 `0777e56ea` → `ami-0bb9778ca4a73e75c`, 02:54:31Z 개설 · 04:46Z `/stop`. `SKU-APPLE-001` 재고 `71d55bcf…` available 85 → 조정 −80 → 5 (201, 03:11:17Z). 약 4 초 안에 scm 제안 `c873edf0…` 이 `SUGGESTED` · source `ALERT` 로 생겼고 `"warehouseCode":"WH01"` · `reorderPointAtTrigger` 10 · `triggerAvailableQty` 5 · `suggestedQty` 50 을 실었다. inventory-service 로그 `inventory.low-stock-detected emitted … available=5 threshold=10`.
- 제안의 `warehouseId` 는 여전히 LOCATION id `01910000-…-000000001001` 이다 (35차 곁 발견, 그대로).
- 콘솔 `/scm/replenishment` 「창고」 열이 WH01 을 보인다 (소유자).

## Close (2026-10-28 UTC)

4-dim 검증 통과 — (a) impl PR #4568 `state=MERGED` · (b) squash `d5e2928b1` 가 `origin/main` 에 있음 · (c) 머지 시점 필수 체크 실패 0 · (d) `# Acceptance Criteria` 의 모든 AC 를 그 AC 의 동사에 맞춰 닫음.

- AC-4 36차 창 라이브 ✅
