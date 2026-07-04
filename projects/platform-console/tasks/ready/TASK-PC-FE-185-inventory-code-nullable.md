# TASK-PC-FE-185 — WMS 재고 스키마: locationCode/skuCode nullable 허용 (degrade 회귀 수정)

**Status:** ready
**Area:** platform-console / console-web · **File:** `features/wms-ops/api/types.ts` (`InventoryRowSchema`)
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (Zod 2필드 `.nullable()` + 회귀 테스트, 단순)

---

## Goal

`/wms/inventory`(재고 현황)가 HTTP 200 을 받고도 **응답 파싱 단계에서 throw** 하여 섹션 전체가 degrade("wms 재고 정보를 일시적으로 불러올 수 없습니다") 되는 회귀를 수정한다.

**근본 원인**: `InventoryRowSchema` 의 `locationCode`/`skuCode` 가 `z.string().optional()` 로 선언되어 **JSON `null` 을 거부**한다(`optional` 은 `undefined`/생략만 허용). admin-service 읽기모델(`admin_inventory_snapshot`)은 대응 마스터 ref(`admin_{location,sku}_ref`)가 아직 투영되지 않은 재고 행의 코드를 **NULL 로 남긴다**(예: 갓 입고·예약된 SKU 가 마스터 이벤트 도달 전). 그 행이 페이지에 포함되면 `InventoryPageSchema.parse` 가 throw → `getWmsInventoryState` 가 이를 잡아 degrade 로 처리 → 200 인데도 화면은 장애.

형제 필드(`lotId`·`lotNo`)와 shipment 의 모든 코드 필드는 이미 `.nullable().optional()` 이다 — 이 두 필드만 `.nullable()` 이 빠진 잠복 버그였다. 콘솔 테이블은 이미 `r.locationCode ?? r.locationId`(SKU 도 동일)로 null 을 id 로 폴백 렌더하므로, **스키마만 null 을 통과시키면** 정상 동작한다(렌더 변경 불필요).

## Scope

**`features/wms-ops/api/types.ts`** — `InventoryRowSchema`:
- `locationCode: z.string().optional()` → `z.string().nullable().optional()`
- `skuCode: z.string().optional()` → `z.string().nullable().optional()`
- 이유 주석 추가(마스터 ref 미투영 시 null; `code ?? id` 폴백; degrade 회귀 방지).

**`tests/unit/wms-api.test.ts`** — tolerant-parsing 블록에 회귀 테스트 1건: `locationCode/skuCode = null` 인 inventory 페이지가 throw 없이 파싱(수정 전이면 RED).

**Out of scope:** 렌더/컴포넌트 변경 없음(이미 nullish 폴백). admin-service producer·읽기모델·contract 무변경(널 코드는 정당한 상태). 다른 도메인 스키마 무변경.

## Acceptance Criteria
- **AC-1** `locationCode`/`skuCode = null` 을 포함한 `/dashboard/inventory` 200 응답이 `InventoryPageSchema` 를 통과한다(throw 없음) → 재고 섹션이 degrade 하지 않고 정상 렌더(코드 없는 행은 id 로 폴백 표시).
- **AC-2** 코드가 채워진 기존 행은 회귀 없이 코드 그대로 표시.
- **AC-3** `pnpm lint` + `tsc --noEmit` + `vitest run` green. 신규 회귀 테스트가 수정 전 RED / 수정 후 GREEN.

## Edge Cases / Failure Scenarios
- `lowStockOnly=true` 쿼리는 저재고 행만 반환하므로 null-코드 비저재고 행을 안 만나 우연히 통과했었다(부분 degrade의 지문 — 일반 쿼리만 실패). 수정 후 두 경로 모두 통과.
- 빈 문자열 `''` 코드는 수정 전에도 통과했으므로(유효 string) 영향 없음 — 문제는 오직 JSON `null`.
- 향후 admin 이 코드를 채워 재투영해도 non-null 이 되어 무해(수정은 null 도 허용할 뿐).

## Related
- 노출 경위: TASK-PC-FE-183(WMS 가이드) 배포 후 fed-e2e 데모에서 admin-service 를 ecommerce-kafka 로 재연결(택배/출고 읽기모델 투영)하자, 마스터 ref 없는 fulfillment SKU 재고 행이 null 코드로 투영되며 이 잠복 버그가 라이브로 드러남.
- 소비 화면: `features/wms-ops/components/WmsInventoryTable.tsx`(`code ?? id` 폴백 — 이미 null-tolerant).
- 읽기모델 SoT: admin-service `InventorySnapshotEntity` / `InventoryProjectionService`(마스터 ref 미투영 시 code NULL).
