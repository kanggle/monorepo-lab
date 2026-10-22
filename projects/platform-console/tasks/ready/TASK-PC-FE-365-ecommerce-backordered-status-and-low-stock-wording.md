# Task ID

TASK-PC-FE-365

# Title

콘솔이 이커머스 주문 상태 `BACKORDERED`(재고부족 이월)를 모른다 — 주문 상태 분포 · 목록 필터에서 빠지고, 운영자가 취소해야 할 그 주문을 찾을 길이 없다 · 곁: WMS «저재고» 주석이 실제 기준과 다르다

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (콘솔 상태 목록 · 라벨 · 톤 · 주석 — 서버 변경 없음)
> 기안 2026-10-22 UTC — `TASK-PC-FE-364` 의 업무 지표 측정에서 나온 곁 발견(소유자 «진행», 2026-10-22 KST).

# Dependency Markers

- 선행 없음. 관련: `TASK-PC-FE-357`(주문 상태 막대) · `TASK-PC-FE-364`(통합 개요 출고 대기 주문).
- 짝: `projects/wms-platform/tasks/ready/TASK-BE-644-*.md`(저재고 기준이 설정을 무시 — 서버). 이 티켓의 저재고 항목은 **주석 · 문구만**.

# Goal

1. **`BACKORDERED`**: 주문 서비스는 결제 뒤 재고 예약이 실패하면 `PENDING → BACKORDERED` 로 옮긴다(`order-service/.../OrderBackorderService.java` · `OrderReservationFailedConsumer.java`). 운영자는 그 주문을 **취소**할 수 있다(`OperatorOrderCancellationService.java` 머리말 — «most often a BACKORDERED one»). 그런데 콘솔의 `ORDER_STATUS_VALUES`(`features/ecommerce-ops/api/order-types.ts:37-44`)에 이 값이 없다 ⇒
   - 이커머스 개요 «주문 상태» 막대(`TASK-PC-FE-357`)의 합이 전체 주문보다 작다(그 주문들이 조용히 빠진다).
   - 주문 목록 상태 필터로 고를 수 없다 — 운영자가 취소할 주문을 찾을 길이 없다.
   - 가이드(`features/ecommerce-guide/data.ts:145-149`)는 «운영자-선택 상태로 노출하지 않는다» 고 적었다 — 상태 **변경** 선택지에서 빼는 것은 맞지만, 분포 · 필터 · 배지에서까지 빠질 이유는 아니다.
2. **WMS 저재고 주석**: 콘솔 주석 · 문구가 저재고를 «재주문점 아래» 로 설명하는 곳이 있다(AC-0 에서 전수). 실제 admin 읽기 모델의 `lowStockFlag` 는 `availableQty ≤ 10` 고정(`wms admin-service InventoryProjectionService.java:55,341`) — 서버 수선은 `TASK-BE-644`, 이 티켓은 **콘솔의 설명을 사실대로**.

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 콘솔이 `BACKORDERED` 주문을 받으면 어떻게 되나 — 목록 · 상세 · 개요 응답의 zod 가 `z.enum(ORDER_STATUS_VALUES)` 로 **거부**해 화면이 저하되는가(그렇다면 지금 운영 결함), 아니면 통과하는가 ② `ORDER_STATUS_VALUES` 를 쓰는 곳 전수(상태 분포 · 필터 · 배지 톤 · 상태 변경 다이얼로그 · 가이드 · 샘플 · 시험) ③ 백오더 주문에서 운영자가 할 수 있는 전이(생산자 `allowedTransitions` 대응 — 취소만?) ④ 콘솔의 «저재고» 설명 문구 · 주석 전수.
- 구현: `BACKORDERED` 를 상태 목록 · 라벨(예 «재고부족 이월») · 톤에 더한다 · 상태 **변경** 다이얼로그의 «바꿀 수 있는 상태» 에는 넣지 않는다(생산자 전이 규칙대로 — 백오더 → 취소만 허용이면 그것만) · 주문 상태 막대 · 필터에 나타난다 · 샘플에 백오더 1건(가능하면).
- 통합 개요 «출고 대기 주문»(`CONFIRMED`) 은 그대로 — 백오더는 결제 뒤 재고 문제로 다른 줄이다(필요하면 다음 결정).
- 저재고: 주석 · 사용자 문구를 «가용 수량이 기준값(현재 10) 이하» 처럼 사실대로. 숫자를 문구에 박을지는 AC-0 이 BE-644 와 맞춰 정한다(서버가 설정을 따르게 되면 숫자를 박으면 안 된다).
- 시험: `BACKORDERED` 응답 파싱 · 분포 합 = 전체 · 필터 · 상태 변경 다이얼로그 선택지 · 기존 스위트.

## Out of Scope

- 서버 변경(주문 상태 기계 · WMS 저재고 계산 — `TASK-BE-644`).
- `STUCK_RECOVERY_FAILED`(기록되는 곳이 없어 늘 0 — `Order.java:341` 호출자 0) 를 목록에서 뺄지 — 따로 결정.

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~④. ① 이 «거부 → 저하» 면 그것을 운영 결함으로 명시하고 우선 수선.
- [ ] **AC-1** — 구현 + **사용자에게 보이는 새 문구(상태 라벨 · 가이드 설명 · 저재고 설명) 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [ ] **AC-2** — 시험 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`, 마지막 전체 실행은 최종 커밋에서) · `tsc --noEmit` · `next lint` · Client graph 가드 둘 · 큐 가드 셋(스테이지 뒤) rc=0. SSR 이 바뀌면 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` (상태 목록 — 읽기만)
- `projects/platform-console/specs/contracts/console-integration-contract.md` 이커머스 주문 절

# Edge Cases

- 백오더 주문이 0 건이면 막대에 그 조각이 없을 뿐 — 정상.
- 생산자가 백오더에서 허용하는 전이가 콘솔의 가정과 다르면 생산자를 따른다.

# Failure Scenarios

1. 상태 변경 다이얼로그에 `BACKORDERED` 를 «바꿀 수 있는 값» 으로 넣어 운영자가 고르면 생산자 400 → AC-0 ③.
2. 저재고 문구에 «10» 을 박았는데 BE-644 가 설정을 따르게 되어 거짓이 된다 → AC-0 ④ 에서 BE-644 와 맞춤.
