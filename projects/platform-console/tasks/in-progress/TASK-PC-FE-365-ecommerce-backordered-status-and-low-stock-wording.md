# Task ID

TASK-PC-FE-365

# Title

콘솔이 이커머스 주문 상태 `BACKORDERED`(재고부족 이월)를 모른다 — 주문 상태 분포 · 목록 필터에서 빠지고, 운영자가 취소해야 할 그 주문을 찾을 길이 없다 · 곁: WMS «저재고» 주석이 실제 기준과 다르다

# Status

in-progress

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

- [x] **AC-0** — 위 ①~④. ① 이 «거부 → 저하» 면 그것을 운영 결함으로 명시하고 우선 수선.
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

## AC-0 측정 (2026-10-22 UTC)

경로 접두 `A` = `projects/platform-console/apps/console-web/src/features/ecommerce-ops/` · `P` = `projects/ecommerce-microservices-platform/apps/order-service/src/main/java/com/example/order/`.

**① 백오더 응답을 받으면 — 거부 아님, 저하 아님(운영 결함 아님). 단 «보이지 않음 + 취소 불가» 결함은 실재.**
- 읽기 스키마의 `status` 는 `z.string()` 이다 — 목록 행 `A api/order-types.ts:110`(`OrderSummarySchema`) · 상세 `:181`(`OrderDetailSchema`) · 상태 변경 응답 `:195`. `z.enum(ORDER_STATUS_VALUES)` 로 읽는 곳은 없다(헤더 `:18-20` TOLERANCE 불변식). 개요의 상태별 읽기는 `listOrders({status,size:1})` 의 `totalElements` 만 쓴다(`A api/overview-state.ts:202,247`). ⇒ `"status":"BACKORDERED"` 는 통과한다. 새 시험이 목록 · 상세 스키마 파싱을 직접 고정한다.
- 그러나 같은 값이 목록에 들어와도: 배지 톤은 `neutral`(`orderStatusTone` 폴백 `:65` 구판), 개요 «주문 상태» 막대는 `ORDER_STATUS_VALUES` 가 6개뿐이라 백오더 읽기 자체가 없어 **합이 전체보다 작아진다**(`overview-state.ts:202,247`), 상태 필터는 이 값을 못 고른다(`A components/OrdersScreen.tsx:44,115`), 그리고 **상세 화면에서 `allowedTransitions('BACKORDERED')` 가 `[]` → 상태 전이 다이얼로그가 통째로 안 뜬다**(`A components/OrderStatusDialog.tsx:86,93`) — 즉 운영자가 백오더 주문의 상세에 도달해도 취소 버튼이 없었다.

**② `ORDER_STATUS_VALUES` / `OrderStatus` 사용처 전수** — `A api/order-types.ts`(값 · 톤 · 전이) · `A api/overview-state.ts:11,202,247`(분포 버킷) · `A components/OrdersScreen.tsx:9,44`(필터) · `A components/overview-labels.ts:15`(라벨 → `EcommerceOverview.tsx:77` · `EcommerceRecentPanels.tsx:69`) · `A components/OrderStatusBar.tsx`(톤 → 막대) · `A components/OrderStatusDialog.tsx:86`(`allowedTransitions`) · 가이드 `ecommerce-guide/data.ts` `ORDER_STATES`(상태 표 · 흐름) · 샘플 `shared/sample/fixtures/ecommerce.ts` `ECOMMERCE_ORDERS` · 시험 `ecommerce-overview-state` · `ecommerce-orders-state` · `ecommerce-status-tone` · `EcommerceGuideScreen` · `ecommerce-write-buttons`. e2e(`tests/e2e`) 에는 주문 상태 단언이 없다(grep 0건 — 파일 7개 모두 operators/overview/login 계열).

**③ 백오더에서 운영자가 할 수 있는 전이** — 생산자 `P domain/model/OrderStatus.java:13` `isCancellable()` = `PENDING|CONFIRMED|BACKORDERED` ⇒ **취소 가능**. 계약 `ecommerce specs/contracts/events/order-events.md:218` «A BACKORDERED order may be cancelled by an operator». `BACKORDERED → CONFIRMED` 는 재입고 재예약 사가가 한다(`order-events.md:217`, `Order.java:213,226`). 운영자 변경 API `P application/service/AdminOrderStatusService.java:33-41` 는 목표가 `CONFIRMED|CANCELLED` 일 때만 받고 나머지는 400; `Order.confirm`(`Order.java:214`)이 `BACKORDERED` 에서도 통과하므로 **생산자는 기술적으로 운영자의 «백오더 → 확정» 도 받아 준다.** 판단: 콘솔은 **취소만** 낸다 — 계약이 운영자 동작을 취소로만 적고, 수동 확정은 재고 예약 없이 주문을 확정해 이후 재입고 사가와 이중이 되기 때문(생산자 쪽 보강 여부는 이 티켓 밖 · 소유자 판단 사항으로 보고만 한다). `BACKORDERED` 는 어떤 상태의 목표로도 나오지 않는다(시험이 고정).

**④ «저재고» 설명 전수** — 콘솔 화면의 저재고 표시(재고 표 배지 · «저재고만» 필터 · 개요 저재고 수)는 전부 서버 `lowStockFlag` 를 그대로 받는다(콘솔이 임계값을 계산·읽지 않음: `wms-ops/api/types.ts:70`, `wms-ops/api/overview-state.ts:169`, `wms-ops/components/WmsInventoryDataTable.tsx:116`). 설명 문구:
  - 사용자 문구 `wms-guide/data.ts:165-168`(`LOW_STOCK_MECHANISMS[0]`): «고정값 · 가용 10개 이하» — **현재 사실과 일치**(BE-644 가 인용한 `InventoryProjectionService.java:55,341`). 그대로 둔다.
  - 거짓 주석 2곳: `wms-ops/api/overview-state.ts:62-63` · `:164-165` «below their reorder point(재주문점 아래)» → 실제는 `availableQty <= 10` 상수, SKU 별 재주문점 아님. `wms-ops/components/WmsOperationsScreen.tsx:31-33` «the two settings the 개요 저재고/재고 배지 derive from» → `inventory.low_stock.default_threshold_qty` 는 **알림**에만 쓰이고 배지는 설정을 따르지 않는다. 둘 다 주석만 바로잡았다.
  - 숫자 10 의 처리(BE-644 와 맞춤): BE-644 의 (가) «설정을 따르게 고침» 이 추천안이므로 이 숫자는 **그 티켓이 (가) 로 끝나는 순간 거짓**이 된다. 사용자 문구에서 10 을 빼 «기준값 이하» 로만 쓰면 지금도 맞고 (가) 뒤에도 맞지만, 가이드의 두 번째 칸(알림은 설정 가능)과 대비되는 핵심이 «고정 vs 설정» 이라 숫자 없는 문장으로는 대비가 사라진다. ⇒ **사용자 문구는 현 사실 그대로 두고**, `wms-guide/data.ts` 의 해당 상수 위에 «BE-644 (가) 결정 시 같은 PR 에서 고칠 것» 경고 주석을 달았다. 결정은 소유자 몫(아래 «새 문구» 끝의 질문).

## 새 문구 (소유자 승인 대기)

| 파일:줄 | 문구 | 언제 보이나 |
|---|---|---|
| `ecommerce-ops/components/overview-labels.ts:17` | **재고부족 이월** (신규 라벨) | 이커머스 개요 «주문 상태» 목록 · 최근 주문 패널의 상태 칸에서 `BACKORDERED` 일 때 |
| `ecommerce-ops/components/OrdersScreen.tsx:115` (옵션 값) | `BACKORDERED` (신규 옵션 · 기존 옵션과 같은 원문 표기 · 새 글자 없음) | 주문 목록 «상태» 드롭다운 |
| `ecommerce-guide/data.ts:167-172` 신규 행 | 상태 `BACKORDERED` · 라벨 **재고부족 이월** · 운영자 변경 «가능» · 종료 «진행» · 의미 «결제는 끝났지만 재고가 모자라 확정을 미뤄 둔 상태입니다. 다시 입고되면 먼저 들어온 주문부터 자동으로 확정됩니다. 운영자는 취소만 할 수 있습니다.» | 이커머스 가이드 › 흐름 › 주문 상태 표 · 상태 흐름 칩 (`대기 → 재고부족 이월 → 확정 → …`) |
| `ecommerce-guide/components/EcommerceGuideScreen.tsx:401-402` | 변경 전: «정상 경로는 대기 → 확정 → 배송중 → 배송완료 이며, 예외 종료로 취소 · 복구실패가 있습니다.» → 후 : «정상 경로는 대기 → 확정 → 배송중 → 배송완료 이며, 재고가 모자라면 **재고부족 이월** 로 잠시 멈추고, 예외 종료로 취소 · 복구실패가 있습니다.» | 가이드 › 흐름 › 주문 소개 문단 |
| `ecommerce-guide/data.ts:215` (`ORDER_LIFECYCLE_NOTE.body` 끝) | 변경 전: «이 상태는 취소할 수 있지만 콘솔 화면의 상태 목록에는 나타나지 않고, 조회로만 확인할 수 있습니다.» → 후: «이 상태의 주문은 주문 화면의 상태 필터와 개요의 주문 상태 막대에서 확인할 수 있고, 운영자는 취소할 수 있습니다.» | 가이드 › 흐름 › 주문 아래 안내 카드 |
| `ecommerce-guide/data.ts:636` (용어집 `backorder`) | 변경 전: «… 콘솔 상태 목록에는 없고 조회로만 보입니다.» → 후: «… 주문 화면의 상태 필터에서 찾을 수 있습니다.» | 가이드 › 용어집 |
| 바뀌지 않음 | 상태 변경 다이얼로그의 «취소» 버튼 · 확인창 문구(`OrderStatusDialog.tsx:45-48,171-177`) — 백오더 상세에서 기존 «취소» 가 그대로 뜬다 · `wms-guide/data.ts:166-167` «고정값 · 가용 10개 이하» | — |

소유자 확인이 필요한 두 가지: (1) 라벨 «재고부족 이월» — 가이드가 이미 쓰던 표현을 그대로 옮겼다. (2) WMS 가이드의 «10» 을 BE-644 결정 전에 둘지(지금 사실 · (가) 시 같은 PR 에서 수정) 아니면 숫자를 빼고 «고정 기준» 으로만 쓸지.

## 구현 (2026-10-22 UTC · Sonnet)

- **변경 파일** (모두 `projects/platform-console/apps/console-web/`): `src/features/ecommerce-ops/api/order-types.ts`(값 · 톤 `warning` · 전이 `BACKORDERED:['CANCELLED']` · 헤더 주석) · `components/overview-labels.ts`(라벨) · `components/OrderStatusDialog.tsx`(주석) · `src/features/ecommerce-guide/data.ts` · `components/EcommerceGuideScreen.tsx` · `src/features/wms-ops/api/overview-state.ts` · `components/WmsOperationsScreen.tsx` · `src/features/wms-guide/data.ts`(주석만) · `src/shared/sample/fixtures/ecommerce.ts`(`order-sample-0006` BACKORDERED 1건 · `ORDERS_SUMMARY` month/total 6 · 순위 계산에서 BACKORDERED 제외 — 확정 전이라 정산 · 배송 행도 없음). 필터 · 막대 · 상태 목록은 `ORDER_STATUS_VALUES` 에서 파생되므로 값 추가만으로 따라온다. 기존 testid 변경 없음. 순서는 `PENDING, BACKORDERED, CONFIRMED, …`(재입고 시 PENDING 다음 단계).
- **시험**: 신규 `tests/unit/ecommerce-orders-backordered.test.tsx`(필터 옵션 · 라벨 · 막대 합 · 샘플 1건) · 갱신/추가 `ecommerce-orders-state`(전이 · 목록 · 상세 스키마 파싱) · `ecommerce-status-tone` · `ecommerce-overview-state`(버킷 + 읽기 호출) · `EcommerceGuideScreen`(가이드 = enum, 가능 칸 = `allowedTransitions`) · `ecommerce-write-buttons`(백오더 상세는 취소 버튼만).
- **결과**: `vitest run --maxWorkers=4 --minWorkers=2` 426 파일 · 5227 시험 통과(rc=0, 최종 코드) · `tsc --noEmit` rc=0 · `next lint` rc=0(기존 경고 1건, 무관) · 가드 결과는 커밋 메시지/보고에 기재. 샘플 정합 시험(`sample-overview-cards-match-lists` 등)은 수정 없이 통과.
- **SSR vs 클라이언트**: 상태 목록 · 라벨 · 톤은 서버 컴포넌트(개요 `EcommerceOverview` · `OrderStatusBar` 의 분포 읽기 — 읽기 호출이 6 → 7개) · 클라이언트(`OrdersScreen` 필터 · `OrderStatusDialog`) 양쪽에 쓰인다. **SSR 이 바뀌므로(개요 7번째 상태 읽기) 머지 전 브랜치 nightly 콘솔 잡 필요**(AC-2). 막대에서 대기 · 백오더가 같은 `warning` 색이라 인접 조각이 붙어 보인다 — 숫자는 `<dl>` 에 따로 있고 막대는 장식이라 두었다(구분이 필요하면 후속 결정).
- **남은 것**: AC-1(소유자 문구 승인) · AC-2(nightly) · AC-3(배포 · 샘플 화면 확인). 기록용: 생산자는 운영자의 «BACKORDERED → CONFIRMED» 도 기술적으로 수락(AC-0 ③).
