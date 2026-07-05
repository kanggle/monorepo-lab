# TASK-PC-FE-198 — WMS 출고 운영 god-file 컴포넌트 분할 (wms-outbound-ops)

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving god-file split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (frontend-engineer 디스패치 — testid/markup byte-보존)

---

## Goal

PC-FE-197(wms-ops)에 이어 WMS 콘솔 `wms-outbound-ops` 피처의 god-file 컴포넌트를 콘솔 god-file split 시리즈(PC-FE-098~153) 휴리스틱대로 프레젠테이션 조각으로 분할. **behavior-preserving** — 마크업·testid·props·데이터 흐름·훅·렌더 출력 전부 불변. 기존 테스트가 계약(무수정 통과).

대상 god-file(components/):
- `OutboundOpsScreen.tsx`(~385) · `OutboundOrderDrill.tsx`(~266) · `OutboundCancelDialog.tsx`(~205) · `OutboundOrdersTable.tsx`(~196)

## Scope

각 god-file에서 응집된 **프레젠테이션** 조각을 같은 `components/` 디렉터리의 신규 sibling 파일로 추출; 원본은 orchestration(state·effect·action 핸들러·idempotency-key/reason-capture 생명주기·list-state 분기)을 유지하는 얇은 컨테이너로 축소. 모든 `data-testid`/`aria-*`/className/요소 순서/key/조건 렌더/텍스트 verbatim 보존, export 심볼·시그니처 불변.

**Out of scope:** `api/`·`hooks/`·proxy 라우트·producer·contract·테스트 무변경. 컴포넌트(+`index.ts` barrel — 이번엔 미변경, `OutboundOpsScreen`/`OutboundActionDialog`만 노출 유지)만.

## Acceptance Criteria
- **AC-1** 대상 4개 god-file이 의미 있게 축소되고, 추출 조각이 원본 렌더 출력을 byte-동일하게 재현.
- **AC-2** 모든 testid(인덱스 템플릿 포함: `outbound-row-${i}`·`outbound-row-status-${i}`·`outbound-drill-${i}`·`outbound-line-${i}`·`outbound-cancel-*`·`outbound-action-*`)·aria·요소 순서 보존.
- **AC-3** `index.ts` 공개 API 불변(`OutboundOpsScreen`+`OutboundActionDialog`만 export).
- **AC-4** `tsc --noEmit` 0 + `next lint` 0 + `vitest`(outbound 전 스위트) green, 회귀 0. 신규 테스트 불필요.

## Edge Cases / Failure Scenarios
- **Cancel dialog focus-trap 보존**: reason state·open-reset+auto-focus effect·Escape/focus-trap 키핸들러·`dialogRef` 프레임을 컨테이너에 유지, presentational body(`OutboundCancelDialogBody`)만 추출 — body가 같은 `role="dialog"` div 하위에 렌더되므로 `querySelectorAll` focus-trap이 textarea+버튼을 그대로 탐색, `reasonRef` 관통.
- **`aria-labelledby="wms-outbound-heading"` 크로스-컴포넌트 해소**: `<h1 id="wms-outbound-heading">`를 `OutboundOpsHeader`로 이동하되 참조 `<section aria-labelledby>`는 컨테이너 잔류 → DOM에서 id 해소 유지.
- **OutboundOpsScreen은 거의 전부 orchestration**(pick/pack/ship/cancel/retry 5개 변이 생명주기 + idempotency-key + reason capture) → 헤더만 추출 가능(PC-FE-197 WmsOpsScreen과 동형 휴리스틱).
- **페이지네이션**: prev-disabled=요청 `query.page`, pageinfo/next-disabled=반환 `ordersData.page` 분리 보존.

## Related
- 미러: TASK-PC-FE-197 (wms-ops 컴포넌트 분할).
- 선행: TASK-PC-FE-192 (wms gateway client dedup).
- 후속: E-Commerce 화면 분할.
- 기존 테스트(계약): `tests/unit/{OutboundOpsScreen,outbound-proxy,outbound-nav}.test.tsx` + outbound state/api/envelope.
