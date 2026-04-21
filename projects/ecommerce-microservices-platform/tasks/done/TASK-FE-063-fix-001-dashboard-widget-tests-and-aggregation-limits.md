# Task ID

TASK-FE-063-fix-001

# Title

TASK-FE-063 리뷰 수정 — 대시보드 위젯 테스트 누락 3종 추가 및 집계 페이지 한계 가드

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

# Goal

Fix issue found in TASK-FE-063. 대시보드 홈 1순위 위젯 구현 리뷰에서 다음 2가지 이슈를 해결한다.

1. 태스크 Test Requirements에 명시된 "각 위젯별 컴포넌트 테스트(로딩/성공/에러/빈 상태)"가 일부 위젯에만 작성됨 — 누락된 3개 위젯의 렌더링 테스트를 추가한다.
2. 집계 위젯이 주문 목록 첫 페이지만 조회해 매출/당일 주문 수치가 과소 집계될 수 있음 — 한계를 명시하고 페이지당 size 상한에서 집계가 잘린 경우 경고/주석을 남긴다.

# Scope

## In Scope

- `PendingOrdersKpi` 렌더링 테스트 추가 (로딩/성공/에러)
- `OutOfStockKpi` 렌더링 테스트 추가 (로딩/성공/에러)
- `RevenueTrendChart` 렌더링 테스트 추가 (로딩/성공/빈 상태/에러)
- `TodayOrdersKpi`와 `RevenueTrendChart` 내부에 집계 범위 한계 주석 추가 (첫 페이지 기반, 주문량 X건 초과 시 미집계 가능성)
- (선택) `totalElements`가 size보다 큰 경우 subValue에 "※ 최근 N건 기준" 주석 텍스트를 추가해 운영자가 한계를 인지하도록 함

## Out of Scope

- 백엔드 집계 전용 API 신규 설계/구현
- 주문 목록 API에 sort/createdAt 범위 파라미터 추가
- 차트 라이브러리 교체 (현재 인라인 SVG 유지)

# Acceptance Criteria

- [ ] `PendingOrdersKpi.test.tsx`: 로딩 시 스켈레톤, 성공 시 `totalElements` 합산 표시, 에러 시 메시지 검증
- [ ] `OutOfStockKpi.test.tsx`: 로딩/성공/에러 검증
- [ ] `RevenueTrendChart.test.tsx`: 로딩/빈 상태("최근 7일 매출 데이터가 없습니다.")/성공(SVG path 렌더링)/에러 검증
- [ ] `TodayOrdersKpi`와 `RevenueTrendChart`의 `PAGE_SIZE` 상수 근처에 집계 한계를 설명하는 주석 1줄 추가
- [ ] `totalElements > PAGE_SIZE` 인 경우 `TodayOrdersKpi`/`RevenueTrendChart`에서 한계 경고 문구를 표시
- [ ] 위젯 단위 테스트 전체(`pnpm test -- widgets/dashboard`) 통과

# Related Specs

> **Before reading Related Specs**: Follow `specs/platform/entrypoint.md` Step 0.

- `specs/platform/testing-strategy.md`
- `specs/services/order-service/api.md`

# Related Skills

- `.claude/skills/INDEX.md` 프론트엔드 테스트 관련 스킬

# Related Contracts

- `specs/contracts/http/orders.*`

# Target App

- `apps/admin-dashboard`

# Implementation Notes

- 기존 테스트 패턴(`today-orders-kpi.test.tsx`, `recent-orders-table.test.tsx`) 그대로 재사용 — `vi.mock('@/features/order-management/api/order-api', …)` + `QueryClientProvider` wrapper.
- `RevenueTrendChart` 테스트에서 SVG path는 `container.querySelector('path')`로 존재 여부 확인.
- 에러 테스트는 mock을 `mockRejectedValueOnce(new Error('fail'))`로 바꾼 뒤 wrapper의 `retry: false` 설정으로 즉시 실패하도록 한다.
- 한계 경고 텍스트 예: `총 주문 N건 중 최근 100건 기준` — 집계 한계 노출.

# Edge Cases

- `totalElements === PAGE_SIZE`: 경계, 경고 미표시
- `totalElements > PAGE_SIZE`: 경고 표시
- `totalElements === 0`: 0건 표시, 경고 없음

# Failure Scenarios

- API 타임아웃: 기존 `ListError` 및 `KpiCard` 에러 분기 동작 확인
- 일부 위젯만 실패: 다른 위젯은 정상 렌더링 유지

# Test Requirements

- `src/__tests__/widgets/dashboard/pending-orders-kpi.test.tsx`
- `src/__tests__/widgets/dashboard/out-of-stock-kpi.test.tsx`
- `src/__tests__/widgets/dashboard/revenue-trend-chart.test.tsx`
- 기존 `today-orders-kpi.test.tsx`에 `totalElements > PAGE_SIZE` 경고 표시 케이스 추가

# Definition of Done

- [ ] 누락된 3개 위젯 테스트 파일 추가
- [ ] 집계 한계 주석 + 경고 문구 구현
- [ ] `pnpm test -- widgets/dashboard` 통과
- [ ] `pnpm exec tsc --noEmit` 시 본 변경 관련 오류 없음
- [ ] Ready for review
