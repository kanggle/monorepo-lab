# TASK-PC-FE-197 — WMS 개요·재고·출고 god-file 컴포넌트 분할 (wms-ops)

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving god-file split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (frontend-engineer 디스패치 — testid/markup byte-보존)

---

## Goal

SCM(PC-FE-190)과 동일한 접근으로, WMS 콘솔 `wms-ops` 피처의 god-file 컴포넌트를 콘솔 god-file split 시리즈(PC-FE-098~153) 휴리스틱대로 프레젠테이션 조각으로 분할한다. **behavior-preserving** — 마크업·testid·props·데이터 흐름·훅·렌더 출력 전부 불변. 기존 테스트가 계약(무수정 통과).

대상 god-file(components/):
- `WmsOverview.tsx`(~344) · `WmsInventoryTable.tsx`(~333) · `WmsInventoryScreen.tsx`(~288) · `WmsShipmentsTable.tsx`(~208) · `WmsOpsScreen.tsx`(~173)

## Scope

각 god-file에서 응집된 **프레젠테이션** 조각을 같은 `components/` 디렉터리의 신규 sibling 파일로 추출; 원본은 orchestration(state·effect·action 핸들러·list-state 분기)을 유지하는 얇은 컨테이너로 축소. 모든 `data-testid`/`aria-*`/className/요소 순서/key/조건 렌더/텍스트 verbatim 보존, export 심볼·시그니처 불변.

**Out of scope:** `api/`·`hooks/`·proxy 라우트·producer·contract·테스트 무변경. 컴포넌트(+`index.ts` barrel re-export 경로)만.

## Acceptance Criteria
- **AC-1** 대상 5개 god-file이 의미 있게 축소되고, 추출 조각이 원본 렌더 출력을 byte-동일하게 재현.
- **AC-2** 모든 testid(인덱스 템플릿 포함: `wms-inv-row-${i}`·`wms-inv-detail-${i}`·`wms-inv-low-${i}`·`wms-ship-row-${i}`·`wms-ship-carrier-${i}`·`wms-${area.key}-count-*`·`wms-alert-status-${bucket.key}`)·aria·요소 순서 보존.
- **AC-3** `index.ts` 공개 API 불변(`WmsRecentShipments`/`WmsRecentAdjustments` 동일 심볼·시그니처, 내부 import 경로만 변경).
- **AC-4** `tsc --noEmit` 0 + `next lint` 0 + `vitest`(wms 전 스위트) green, 회귀 0. 신규 테스트 불필요(기존 테스트=behavior-preservation 계약).

## Edge Cases / Failure Scenarios
- 공유 read-model-lag 배너를 `WmsLagHint`(testid-parameterized)로 3개 화면(개요·재고·출고)에서 공유 추출 — 빈 메시지 시 `null` 반환으로 원본 `{msg && <div/>}` DOM 재현.
- 재고 상세 degrade 분기(`detailDegraded || !detail.data`)를 `degraded={..} data={..}` props로 넘겨 조각 내부에서 `degraded || !data` 재현 — 동작 동일.
- 출고 intro `<p>`를 filters 조각으로 이동 시 flatten 순서 `h2 → p → form` 보존.
- `WmsOpsScreen`은 거의 전부 orchestration(alerts 쿼리·ack 변이·idempotency-key 생명주기) → lag 배너만 프레젠테이션 추출 가능(휴리스틱 부합).

## Related
- 미러: TASK-PC-FE-190 (scm 컴포넌트 분할).
- 선행: TASK-PC-FE-192 (wms gateway client dedup, 파일 disjoint — components vs api).
- 후속: wms-outbound-ops 분할 · E-Commerce 화면 분할.
- 기존 테스트(계약): `tests/unit/{WmsOpsScreen,WmsInventoryScreen,WmsShipmentsScreen,wms-overview,WmsRecentShipments,WmsRecentAdjustments,wms-nav}.test.tsx`.
