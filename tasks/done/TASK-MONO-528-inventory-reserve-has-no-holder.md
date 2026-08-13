# Task ID

TASK-MONO-528

# Title

`INVENTORY_RESERVE` 를 쥔 자격증명이 없다 — 출고 사가의 예약 단계가 막혀 있고, `ADR-MONO-061` 이 그것을 이제 **부여 가능**하게만 만들어 두었다

# Status

done

# Owner

monorepo

# Task Tags

- iam
- security
- demo

---

# 배경

`TASK-MONO-514` 가 발굴하고 **의도적으로 묶지 않은** 두 번째 표면이다.

wms 출고 사가의 예약 단계는 `INVENTORY_RESERVE` 를 요구하는데, 그 권한을 쥔 주체가 없다.
운영자에게 주는 것은 **계약 위반**이다 — `inventory-service-api.md` 가 명시한다:

> `INVENTORY_RESERVE` is a **machine-to-machine** scope. **Human users do not hold it.**

즉 답은 운영자 엔타이틀먼트 확대가 아니라 **워크로드 자격증명**이고, `TASK-MONO-514` §③ 이
*"별도 결정이다. 이 티켓에서 묶지 않는다"* 로 남겼다.

## 무엇이 바뀌어서 지금 이 티켓이 생겼나

`ADR-MONO-061`(ACCEPTED 2026-08-13, **C**)이 워크로드 토큰에 `roles` 를 실을 수 있게 만들었고,
`TASK-MONO-514` 가 `WorkloadRoleCatalog` 로 그 배선을 깔았다. **능력은 생겼고 부여는 안 했다** —
ADR 의 fail-closed 기본값(열거되지 않은 클라이언트는 아무 role 도 받지 않는다)이 그대로 유지된다.

🔴 **"이제 가능하다" 는 "이제 옳다" 가 아니다.** 이 티켓이 답해야 할 것은 배선이 아니라 결정이다.

---

# Goal

wms 출고 사가의 예약 단계가 **실제로 통과**한다 — 또는 그것이 v1 범위 밖이라는 것이 결정으로
기록되고, 데모/워크스루가 그 전제 위에서 정확하게 서술된다.

---

# Scope

## In Scope

- `INVENTORY_RESERVE` 를 어느 워크로드 클라이언트가, **어느 scope 로** 받는지 결정
- 예약 단계를 실제로 호출하는 주체가 무엇인지 실측(서비스 간 호출인가, 사가 오케스트레이터인가,
  아니면 **아무도 부르지 않는가**)
- `docs/guides/interview-demo-walkthrough.md` § 6 의 *"WMS 출고는 주문까지만 심는다"* 행 갱신

## Out of Scope

- 운영자 엔타이틀먼트 확대 — **계약이 금지한다**(위 인용)
- `ADR-MONO-061` 의 재해석. C 는 확정이고, 이 티켓은 그 안에서 부여를 정한다

---

# Acceptance Criteria

- [x] **AC-0 (전제 실측)** — ✅ **완료 2026-08-13. 🔴🔴 이 티켓의 전제가 거짓이었다.**
      **예약 단계는 막혀 있지 않다 — 주문을 넣는 순간 자동으로 통과한다.** `ReceiveOrderService`
      가 주문 생성과 **같은 TX** 로 `outbound.picking.requested` 를 발행하고(사가 step 1),
      inventory 의 `PickingRequestedConsumer` 가 받아 `ReserveStockService` 를 부른다.
      **Kafka 컨슈머에는 JWT 가 없으므로 role 검사 자체가 없다.** `INVENTORY_RESERVE` 를 요구하는
      것은 `ReservationController` 3개(**manual** REST)뿐이고 **저장소 전체 호출자 0건**이다.
      **라이브 증거** — 손대지 않은 직전 데모 DB(`wms_postgres-data` 볼륨):
      `outbound.order.received` 09:42:03.860 → `outbound.picking.requested` 09:42:03.885 →
      `reservation` **RESERVED** 09:42:05.464 · `inventory_movement` `PICKING` 2건 ·
      `outbound_saga` = `RESERVED`. `PickingFlowIntegrationTest` 가 같은 경로를 CI 에서 고정.
      🔴 **계측기를 두 번 고쳤다** — 둘 다 거짓 부재를 낼 뻔했다:
      ① *"outbound 에 HTTP 클라이언트가 없다 ⇒ 예약 호출자 없다"* → **틀린 술어**. wms 는
      **아무도** HTTP 로 서비스 간 통신을 하지 않는다(전부 이벤트) ⇒ 아무것도 증명 못 한다.
      ② 테스트 검색 0건 → **글롭 문제**. 다시 재니 5개 존재.
      🔵 **배송 절반은 그대로다**: 시드가 멈추는 진짜 지점은 피킹 확정(`picking_confirmation`
      **0건** — 시드가 안 부른다)이고 배송은 도달 불가 TMS 스텁이다.
- [x] **AC-1 (부여 결정)** — ✅ **어느 클라이언트에도 부여하지 않는다.** `WorkloadRoleCatalog`
      기본값 **무변경**(`ADR-MONO-061` § 구속력 3 그대로). 부여하면 **호출자 0인 표면**이 열리고,
      그것이 이 티켓 자신의 Failure Scenario 다. 결정과 사유를 카탈로그에 **주석으로 기록**했다 —
      빈 상태가 "아무도 안 봤다" 가 아니라 "재고 결정했다" 로 읽히도록.
      🔵 scope 신설도 하지 않는다(부여할 대상이 없으므로).
- [x] **AC-2 (도달 가능성)** — ✅ **예약 단계는 실제로 통과한다** — 위 라이브 증거가 그것이다.
      🔴 다만 이 티켓이 상상한 메커니즘(워크로드 토큰)이 **아니라** 이벤트 경로로 통과한다.
      "토큰 클레임만 확인" 함정은 애초에 발생할 수 없었다 — 이 경로엔 토큰이 없다.
- [x] **AC-3 (음성 대조)** — ✅ manual REST 표면의 role 게이트는 그대로 살아 있고
      `ReservationControllerSliceTest` 가 role 유/무를 이미 대조한다.
      🔴 **티켓이 지정한 형태(같은 클라이언트·좁은 scope)는 성립하지 않는다** — 부여를 하지
      않았으므로 대조할 자격증명 자체가 없다. 빠뜨린 게 아니라 **부여 결정이 없어진 결과**다.
- [x] **AC-4 (사람 평면 불변)** — ✅ `OperatorRoleDerivation` **무변경** + 단언 신설:
      `OperatorRoleDerivationTest#noDomain_derivesInventoryReserve` 가 **알려진 8개 도메인 키
      전부**의 union 에 `INVENTORY_RESERVE` 가 없음을 강제한다(계약이 역할을 구속하지 특정
      도메인 arm 을 구속하지 않으므로 wms 하나가 아니라 전수로 걸었다). 비공허성 가드
      `isNotEmpty()` 포함. 🔵 **bite 확인**: 단언 대상을 실재하는 역할(`INVENTORY_READ`)로
      바꾸니 그 테스트만 **FAILED**(rc=1) → 되돌려 9/9 GREEN.
- [x] **AC-5 (워크스루 정합)** — ✅ § 6 행을 실측대로 정정(원인 진술이 틀렸다는 것까지 명시).
      🔴 티켓 Failure Scenario 를 지켜 **✅ 가 아니라 🔵** 로 뒀다 — 배송 절반이 남아 있다.
      `check-walkthrough-ledger-drift.sh` **rc=0**.

---

# Related Specs

- `projects/wms-platform/specs/contracts/inventory-service-api.md` (§ roles — 인용의 출처)
- [`ADR-MONO-061`](../../docs/adr/ADR-MONO-061-workload-token-authorization-plane.md) (ACCEPTED — C)
- `projects/iam-platform/apps/auth-service/.../WorkloadRoleCatalog.java`
- `tasks/done/TASK-MONO-514-wms-master-writes-need-a-role-nobody-can-get.md` (§③ · § 구현 ⑦)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § Gateway Enforcement Rules (머신 인가 축)

# Edge Cases

- **부르는 코드가 없을 수 있다.** 그 경우 이 티켓의 답은 부여가 아니라 *"예약 단계는 v1 에서 도달
  경로가 없다"* 를 기록하는 것이고, 그것도 산출물이다(조용히 부여만 하고 닫으면 아무도 쓰지 않는
  권한이 늘어난다).
- wms 는 데이터에 테넌트가 거의 없다 — `tenant_id` 컬럼을 가진 테이블이 5개 DB 통틀어
  `outbound_db.outbound_order` 하나뿐이다(`TASK-MONO-514` Edge Case 실측). 권한 범위를 테넌트
  격리로 좁힐 수 없다는 뜻이다.

# Failure Scenarios

- **role 만 부여하고 닫는다** — 부르는 코드가 없으면 아무것도 열리지 않고, 권한만 넓어진다.
- **AC-2 없이 토큰 클레임만 확인한다** — `TASK-MONO-514` 가 정확히 그 함정을 문서화했다.
- **워크스루 행을 ✅ 로 바꾸면서 "배송" 절반을 빠뜨린다** — 그 행은 예약과 배송 **둘 다**를
  서술한다. 한쪽만 풀렸으면 🔵 이지 ✅ 가 아니다.

# Definition of Done

- [x] AC-0 실측 기록 (부르는 코드 전수) — **호출자 0건 + 사가는 이벤트로 예약**(라이브 증거)
- [x] 결정 + 배선 — **부여 0**. 배선 변경 없음, 대신 **틀린 사유를 적고 있던 4곳 정정**
- [x] AC-2/AC-3 실측 증거 — 예약 통과는 라이브 DB, 음성 대조는 기존 슬라이스 테스트
- [x] 워크스루 § 6 행 갱신 + 가드 rc=0
- [x] Ready for review

---

## 착수 기록 (2026-08-13, UTC)

**이 티켓은 부여를 하러 왔다가 정정을 하고 간다.** AC-0 이 요구한 *"부여할 대상이 실재하는지부터
재라"* 가 정확히 그 일을 했다 — 전제가 거짓이었고, 부여했다면 **호출자 0인 표면에 권한만
넓히는** 결과였다.

🔴 **왜 전제가 거짓이 됐나 — 이름이 같은 두 경로.** `INVENTORY_RESERVE` 라는 이름이 붙은
표면은 하나인데, "예약" 이라는 **행위**로 가는 길은 둘이다:

| 경로 | 인가 | 호출자 |
|---|---|---|
| `POST /api/v1/inventory/reservations` (manual REST) | `hasRole('INVENTORY_RESERVE')` | **0건** |
| `outbound.picking.requested` → `PickingRequestedConsumer` → `ReserveStockService` (사가) | **없음**(컨슈머엔 JWT 가 없다) | 주문 생성마다 |

시드 작성자가 본 403 은 **윗 줄**이고, 데모가 실제로 쓰는 건 **아랫 줄**이다. 그 403 이 찍히던
순간에도 사가는 예약에 성공하고 있었다. **한 사건으로 읽은 것**이 오독의 전부다.

🔴 **틀린 사유가 4곳에 복제돼 있었다** — 하나가 다른 하나를 인용하며 굳었다:
`inventory-service-api.md` § Authorization + §4 머리 · inventory `SecurityConfig` javadoc ·
`seed-wms.sh` 헤더 · 워크스루 § 6 행. **전부 *"`outbound-service` 가 service-account JWT 로
이 표면을 호출한다"* 를 전제하는데, `outbound-service` 는 HTTP 클라이언트를 하나도 갖고 있지
않다.** 네 곳 다 실측으로 정정했다.

🔵 **남긴 것**: manual REST 표면은 **지우지 않는다**(운영자 out-of-band/복구 경로이고, role
게이트는 슬라이스 테스트가 지킨다). 호출자가 생기는 날 그때 가리킬 대상을 놓고 부여를 결정하면
된다 — 지금 부여하면 그 결정을 **호출자 없이** 내리는 것이다.

분석·구현=Opus 5.
