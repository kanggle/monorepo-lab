# TASK-PC-FE-178 — WMS 재고 화면 카피에서 "스냅샷" jargon 제거

**Status:** review
**Area:** platform-console / console-web · **Route:** `/wms/inventory` · **Nav:** 변경 없음
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (사용자 카피 4문구 교체, 로직/테스트 무변경)

---

## Goal

WMS 재고 전용 페이지(`/wms/inventory`)의 테이블 카피가 producer 읽기 모델 이름(`InventorySnapshotEntity`)을 그대로 옮긴 **"재고 스냅샷"**을 노출한다. 운영자 관점에서 "스냅샷"은 구현 누수 jargon이다:

- 운영자에겐 "재고 스냅샷" vs "재고"의 행동 정보 차이가 없다(현재 재고를 볼 뿐).
- "스냅샷"이 전하려는 최종 일관성(지연 가능) 뉘앙스는 **이미 lag 배너**가 명시 처리한다("데이터가 약 N초 지연될 수 있습니다 (읽기 모델은 최종 일관성 …)") → 중복.
- 같은 페이지 안에서 표현 불일치: h1 "WMS 재고" · 부제 "재고 **조회**" · 테이블 제목 "재고 **스냅샷**".

WMS 재고 테이블 카피에서 "스냅샷"을 제거하고 **"재고 현황"**으로 통일한다. lag 배너는 유지(최종 일관성 정직성).

## 스윕 결과 (같은 성격 잉여 여부 — 확인 완료)

- **WMS 개요 밴드**(`WmsOverview`/`WmsOpsScreen`): 가시 "스냅샷" **없음**(h2 "운영 개요") → 조치 불필요.
- **SCM**(`ScmSnapshotTable` "재고 가시성 — 스냅샷"): **정당 — 남김**. 크로스노드 *가시성* 뷰이고 바로 아래 REQUIRED S5 freshness 경고(`S5Warning`)가 붙어 "스냅샷"이 staleness 뉘앙스를 실어 나른다.
- **ledger**("마감 스냅샷"), **dashboards/operator-overview**("스냅샷 노드 수" / "최근·이번 스냅샷"): 각 도메인 고유 개념(회계 마감 스냅샷 / 인프라 레지스트리 노드 스냅샷) → 남김.

즉 실제 정리 대상은 **WMS `WmsInventoryTable` 4문구뿐**.

## Scope

**`features/wms-ops/components/WmsInventoryTable.tsx`** — 4개 사용자 카피 교체:
1. h2 제목 `재고 스냅샷` → `재고 현황`
2. 필터 `aria-label="재고 스냅샷 필터"` → `aria-label="재고 현황 필터"`
3. 빈 상태 `표시할 재고 스냅샷이 없습니다.` → `표시할 재고가 없습니다.`
4. sr-only caption `재고 스냅샷` → `재고 현황`
5. (정돈) 코드 주석 `── Inventory snapshot ──` → `── Inventory ──`

**Out of scope:** SCM/ledger/dashboards/operator-overview의 "스냅샷"(위 스윕 결과대로 정당하므로 유지). WMS 개요 밴드(이미 clean). 로직/데이터/testid 변경 없음. lag 배너 유지. producer/contract 무변경.

## Acceptance Criteria
- **AC-1** `/wms/inventory` 테이블 제목이 "재고 현황"으로 표시(더 이상 "스냅샷" 없음). 필터 aria-label·빈 상태·caption도 동일하게 "스냅샷" 제거.
- **AC-2** lag 배너·필터·페이징·상세 패널·testid(`wms-inv-empty`/`wms-inv-table` 등) 회귀 없음.
- **AC-3** SCM 등 타 도메인 "스냅샷" 미변경.
- **AC-4** `pnpm lint` + `tsc --noEmit` + `vitest run` green (WMS 재고 테스트는 testid 기반이라 카피 단언 없음).

## Edge Cases / Failure Scenarios
- 빈 상태/캡션 문구 변경이 테스트를 깨지 않음(WMS 재고 테스트는 `data-testid`로 단언; 문구 미검증 — 사전 grep 확인). 만약 문구 단언이 추가돼 있었다면 vitest RED로 즉시 검출.

## Related
- Producer read model: wms `admin-service` `InventorySnapshotEntity`(이름의 출처; 소비 UI 카피만 조정, 계약 무관).
- 선행: PC-FE-173(재고 전용 페이지 분리), PC-FE-177(개요 순서/저재고).
