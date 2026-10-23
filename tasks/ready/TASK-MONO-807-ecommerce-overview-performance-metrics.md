# Task ID

TASK-MONO-807

# Title

`ADR-MONO-084` P2 — E-Commerce 실적 지표: 매출 · 판매 수량 · 주문 수(`CONFIRMED` 이후 · `paidAt` 귀속 · 환불 제외) + `/insights` 순위 기준 교정 + 콘솔 개요 실적 줄

# Status

ready

# Owner

monorepo

# Task Tags

- code
- api
- ecommerce
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (매출 인식 기준 · 집계 쿼리 · 기존 순위 숫자 변경)
> 🔴 **cross-project 원자 PR**: order-service 계약 · 엔드포인트와 콘솔 화면을 한 PR 에 싣는다.

# Dependency Markers

- **선행**: `TASK-MONO-806`(P1 공용 DTO · 경계 · 콘솔 컴포넌트) `main` 머지.
- 형제: `TASK-MONO-808`~`812`.

# Goal

E-Commerce 개요(`/ecommerce`) 맨 위에 이번 주 · 이번 달 **매출 · 판매 수량 · 주문 수**와 직전 대비 · 일별 추이를 보여 준다. 같은 기준으로 기존 상위 5 순위도 고친다(지금은 기간 없는 누적 + `PENDING` 포함 — `OrderJpaRepository.java:160-176`).

# Scope

## In Scope

- 계약 먼저: `order-api.md` 에 `GET /api/admin/orders/metrics?period=week|month` (P1 공용 모양) · 지표 `revenue` · `units` · `orders` 각각의 **포함 상태 · 귀속 시각 · 제외** 한 줄씩(ADR-084 D5).
  - 포함: `CONFIRMED` · `SHIPPED` · `DELIVERED`. 귀속: `paidAt`. 제외: `PENDING` · `CANCELLED` · 환불(`refundedAt` 있음).
  - OD-1 기본값: `BACKORDERED` 는 넣지 않는다. `CONFIRMED` 가 되면 그 주문의 `paidAt` 날짜로 들어간다 → **지난 날의 값이 나중에 바뀔 수 있다**고 계약에 적는다.
  - OD-2 기본값: 할인 후 결제 금액.
- order-service: 집계 쿼리(테넌트 범위 · 판매자 범위 규칙이 있으면 그대로) · 컨트롤러 · `ADMIN` 인가(기존 admin 엔드포인트와 같은 게이트).
- `/insights` 교정: 같은 포함/제외 기준 · `period` 파라미터(기본값은 AC-0 에서 정한다 — 지금 화면이 기간 없이 부른다).
- 콘솔: `features/ecommerce-ops` 개요에 P1 «실적 줄» 붙이기 · 순위 차트에 기간 표시.

## Out of Scope

- 다른 영역(상품 · 배송 · 프로모션 …)의 `/summary` 건수 — 그대로 둔다.
- 정산 · 결제 서비스의 금액 집계.

# Acceptance Criteria

- [ ] **AC-0** — 재측정: `totalPrice` · `discountAmount` · `unitPrice * quantity` 의 실제 의미(할인 전/후 · 배송비 포함 여부)와 셋의 관계 · `paidAt` 이 채워지는 경로(모든 `CONFIRMED` 주문에 있는가) · 환불 시 상태 전이(`refundedAt` 과 `CANCELLED` 의 관계) · 판매자 범위 규칙 · `/insights` 소비자 전수. 결과 → 이 파일 + 계약 문구.
- [ ] **AC-1** — 🔴 대조군(제외 쪽 먼저): `PENDING` · `CANCELLED` · 환불 · `BACKORDERED` 주문을 넣어도 `revenue` · `units` · `orders` 가 안 움직인다. `CONFIRMED` 하나를 넣으면 움직인다.
- [ ] **AC-2** — 귀속 시험: `createdAt` 은 지난주, `paidAt` 은 이번 주인 주문 → 이번 주에 잡힌다. KST 00:00 경계 직전/직후(`Clock` 주입).
- [ ] **AC-3** — 다른 테넌트의 주문은 안 잡힌다.
- [ ] **AC-4** — `/insights` 교정 전후 값을 같은 데이터로 기록한다(줄어든 숫자 = 기준 교정, ADR-084 Consequences).
- [ ] **AC-5** — 콘솔: 실적 줄 렌더 · 칸 실패 시 현재 상태 블록이 선다 · 401 재로그인. 익명 샘플 모드(ADR-MONO-074)가 이 새 호출에 샘플 응답을 준다.
- [ ] **AC-6** — 배포 상태(`main` · AMI · 데모 창 라이브)를 따로 적는다. 라이브가 없으면 ⚪ 와 이유.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D2 · D4 · D5 · OD-1 · OD-2
- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md`
- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md` (샘플 응답)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` § Admin Endpoints (`/summary` · `/insights` · 새 `/metrics`)
- P1 공용 실적 응답 모양

# Edge Cases

- 한 주문에 여러 판매자의 줄 → 주문 수는 1, 매출 · 수량은 줄의 합.
- 부분 환불이 있는가 → AC-0 에서 잰다. 있으면 계약에 처리를 적는다.
- 결제는 지난달, 확정은 이번 달(`BACKORDERED` → `CONFIRMED`) → `paidAt` 기준이라 지난달 값이 늘어난다.

# Failure Scenarios

- `createdAt` 으로 귀속 → 결제 안 된 날에 매출이 잡힌다.
- 콘솔 샘플 모드가 새 호출을 모른다 → 익명 방문자 화면에서 실적 줄이 항상 실패로 보인다.
- `/insights` 숫자가 줄어든 것을 결함으로 되돌린다 → AC-4 기록이 막는다.
