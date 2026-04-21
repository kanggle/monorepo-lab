# Task ID

TASK-FE-063

# Title

admin-dashboard 홈 화면 1순위 위젯 구현 — 주문/매출/재고 KPI 및 최근 주문

# Status

review

# Owner

frontend

# Task Tags

- code
- api
- test

# Goal

어드민 사용자가 `/dashboard` 진입 시 운영 현황을 즉시 파악할 수 있도록, 현재 플레이스홀더 상태의 대시보드 홈에 기존 백엔드 API만 활용한 고가치 위젯 5종을 추가한다.

# Scope

## In Scope

- 오늘 주문 수 / 매출액 KPI 카드 (`GET /api/orders` 기반, 당일 필터 + 합산)
- 재고 부족 상품 수 KPI 카드 (`GET /api/products` 기반, 임계치 이하 count)
- 처리 대기 주문 KPI 카드 (`GET /api/orders?status=PENDING,PAID` count)
- 최근 7일 매출 추이 라인 차트 (프론트 집계)
- 최근 주문 5건 테이블 (주문번호/회원/금액/상태)
- KPI 카드 공통 컴포넌트 (`KpiCard`) 신규 작성
- 각 위젯 단위 테스트 추가

## Out of Scope

- 배송 지연, 알림 발송 실패, 쿠폰 사용률, 카테고리별 판매량 등 2·3순위 위젯
- 백엔드 집계 전용 API (`/api/admin/dashboard/metrics`) 신규 개발
- 실시간 갱신 (폴링/SSE/WebSocket)
- 재고 부족 임계치 설정 UI (상수로 고정)

# Acceptance Criteria

- [ ] `/dashboard` 접속 시 5개 위젯이 모두 렌더링된다
- [ ] 각 KPI 카드가 기존 API 호출 결과를 기반으로 올바른 수치를 표시한다
- [ ] 주문 데이터가 없을 때 각 위젯이 0 또는 "데이터 없음" 빈 상태를 표시한다
- [ ] API 에러 발생 시 위젯별 에러 메시지가 표시되고 다른 위젯은 정상 렌더링된다
- [ ] 각 위젯의 로딩 상태 (스켈레톤 등)가 표시된다
- [ ] 차트는 최근 7일의 일자별 매출 합계를 올바르게 보여준다
- [ ] 최근 주문 테이블은 최신순 5건을 표시하고 주문 상세로 이동 가능하다

# Related Specs

> **Before reading Related Specs**: Follow `specs/platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `specs/rules/common.md` plus any `specs/rules/domains/<domain>.md` and `specs/rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/platform/entrypoint.md`
- `specs/services/order-service/api.md`
- `specs/services/product-service/api.md`
- `specs/features/` (주문/상품 관련)

# Related Skills

- `.claude/skills/INDEX.md`에서 프론트엔드 위젯/FSD 관련 스킬 참조

# Related Contracts

- `specs/contracts/http/orders.*`
- `specs/contracts/http/products.*`

# Target App

- `apps/admin-dashboard`

# Implementation Notes

- FSD 레이어 구성:
  - `src/widgets/dashboard/` 아래 각 위젯 컴포넌트 배치
  - 기존 `src/entities/order/api/*`, `src/entities/product/api/*` 재사용 — 파라미터 미지원 시 훅을 확장하지 말고 위젯 내부에서 필터링/집계
- 차트 라이브러리: admin-dashboard에 이미 도입된 라이브러리 확인 후 재사용. 없으면 recharts 추가 (package.json 업데이트 별도 커밋).
- KPI 카드는 `src/shared/ui`의 Card 컴포넌트 활용
- 재고 임계치 상수: `LOW_STOCK_THRESHOLD = 10` 위젯 파일 내 정의
- 매출 집계 시 주문 상태가 취소(CANCELLED)인 건은 제외
- 시간대: KST 기준 당일/최근 7일 (UTC 경계 이슈 주의)

# Edge Cases

- 당일 주문이 0건인 경우: "0" 표시
- 재고 부족 상품이 없는 경우: "0" 표시
- 최근 주문이 0건인 경우: "최근 주문이 없습니다" 빈 상태
- 7일 중 일부 날짜에 매출 없음: 해당 일자 0원으로 라인 이어짐
- 주문 목록 API가 페이지네이션으로 전체가 오지 않는 경우: 최근 주문 5건은 size=5 요청, 7일 매출은 필요한 범위만 조회

# Failure Scenarios

- API 500 에러: 위젯 내부에 재시도 버튼과 에러 메시지 표시, 다른 위젯 영향 없음
- 인증 만료(401): 기존 인터셉터에 의해 로그인 페이지로 리다이렉트
- 느린 응답: 위젯별 스켈레톤 로딩 표시
- 네트워크 오프라인: React Query 기본 재시도 정책 적용

# Test Requirements

- `KpiCard` 공통 컴포넌트 단위 테스트 (제목/수치/증감 렌더링)
- 각 위젯별 컴포넌트 테스트 (로딩/성공/에러/빈 상태)
- `RevenueTrendChart` 집계 로직 단위 테스트 (일자별 groupBy, 취소 주문 제외)
- 테스트 파일 경로: `apps/admin-dashboard/src/__tests__/widgets/dashboard/*.test.tsx`

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing (`pnpm --filter admin-dashboard test`)
- [ ] Typecheck passing (`pnpm --filter admin-dashboard typecheck`)
- [ ] 브라우저에서 `/dashboard` 수동 확인 완료
- [ ] Ready for review
