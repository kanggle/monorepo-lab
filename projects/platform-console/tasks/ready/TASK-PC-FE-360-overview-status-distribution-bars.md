# Task ID

TASK-PC-FE-360

# Title

그래프 2차 묶음 ② — 이커머스 개요에 배송 상태 분포 · ERP 개요에 결재 상태 분포 막대 (상태별 총 건수 호출, 개요 화면에만)

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (이커머스 주문 상태 막대 — `TASK-PC-FE-357` `OrderStatusBar` — 와 같은 모양)
> 기안 2026-10-21 UTC — 콘솔 그래프 조사의 2차 묶음. 소유자 결정 2026-10-22 KST: **각 도메인 개요 화면에만**(목록 화면은 그대로 — 필터를 바꿀 때마다 호출이 늘지 않게).

# Dependency Markers

- 선행 없음. 같은 2차 묶음: `TASK-PC-FE-359`(독립). 선례: `TASK-PC-FE-357`(주문 상태 막대 · `shared/ui/InlineBar.tsx`).

# Goal

| # | 화면 | 더하는 것 | 데이터 (새 호출) |
|---|---|---|---|
| 1 | 이커머스 개요 `/ecommerce` | 배송 상태 4단계(`PREPARING` → `SHIPPED` → `IN_TRANSIT` → `DELIVERED`) 누적 막대 + 상태별 건수 | 상태마다 `listShippings({status, size:1}).totalElements` — **4 호출**. 주문 상태(`overview-state.ts:188-192`)와 같은 방식 |
| 2 | ERP 개요 `/erp` | 결재 상태 6종(`DRAFT` · `SUBMITTED` · `IN_REVIEW` · `APPROVED` · `REJECTED` · `WITHDRAWN`) 누적 막대 + 건수 | 상태마다 결재 목록 `status` 필터 `size:1` 의 `meta.totalElements` — **6 호출**. 지금 «결재 대기» 타일이 이미 하나를 부른다(재사용 가능하면 5) |

# Scope

## In Scope

- **AC-0 측정**: ① 배송 목록 API 의 `status` 필터 · `totalElements`(전체 모집단인가) ② 결재 목록 API 의 `status` 필터 · 범위(운영자의 데이터 범위 — 부서 서브트리 — 가 건수에 걸리는가: 걸리면 «내가 볼 수 있는 결재» 의 분포다, 그렇게 밝힌다) ③ 두 개요의 셀 저하 규칙(`cell()` · 403 · 401) ④ 샘플 고정값에 상태별 값이 있는가.
- 구현: 상태별 셀 — 하나라도 저하 · 권한 없음이면 막대 없음(357 `buildOrderStatusSegments` 와 같은 규칙: 일부만 그린 분포는 전체처럼 읽힌다) · 전부 0 → 빈 상태 문구 · 숫자 글자 · 색 견본 · 기존 testid 유지. 공용 `InlineBar` 재사용.
- 시험: 비율 · 하나 저하 · 전부 0 · 403 · 401(세션 전체 재로그인 — 형제와 같게) · 호출 수(4 · 6 — 그 이상 안 나간다).

## Out of Scope

- 목록 화면(`/ecommerce/shippings` · `/erp/approval`) 위의 막대(소유자 결정).
- 감사 로그 그래프(보류).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~④ 를 file:line 과 함께.
- [ ] **AC-1** — 구현 + **사용자에게 보이는 새 문구 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [ ] **AC-2** — 시험 위 목록 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` · `node scripts/check-client-graph-server-only.mjs` · `check-client-graph-backend-origins.mjs`(로컬, 스테이지 뒤) rc=0. SSR 이 바뀌므로 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` — 이커머스 개요 · ERP 개요 절(AC-0 에서 이름 확인 · 읽기 추가를 적는다)

# Edge Cases

- 결재 데이터 범위가 운영자마다 다르면 같은 회사에서도 분포가 다르게 보인다 — 정상, 밝힘 문구로.
- 알 수 없는 새 상태가 생기면 — 고정 목록만 센다(합계가 전체와 다를 수 있음을 시험 · 주석으로).

# Failure Scenarios

1. 상태 하나의 실패가 개요 전체를 저하시킨다 → 셀 독립 시험.
2. 호출이 상태 수보다 많이 나간다(재시도 · 중복) → 호출 수 단언.
