# Task ID

TASK-MONO-710

# Title

🔴 이커머스 데모 시드에 **주문이 0건**이라 주문·배송·정산 화면 다섯 장이 «빈 목록» 으로만 찍힌다

# Status

review (2026-09-22 UTC)

# Owner

monorepo

# Task Tags

- ecommerce
- demo
- seed
- portfolio

---

# Goal

2026-09-18 데모 창(AMI `af0018aa6`, 인스턴스 `i-07ddb6b41233f2673`)에서 `DEMO_TENANT=ecommerce`
로 콘솔 전량을 찍었다. 카탈로그 쪽은 실데이터가 있는데 **주문 파이프라인 쪽은 전부 비어 있다.**

| 화면 | 관측 (2026-09-18 UTC) |
|---|---|
| `/ecommerce/products` | 🟢 상품 **24건** · 2페이지 |
| `/ecommerce/products/[id]` | 🟢 옵션 4개 · 재고 40/60/50/20 (`TASK-MONO-703` 이 신설한 admin 상세) |
| `/ecommerce/sellers` · `/promotions` · `/users` | 🟢 실데이터 |
| `/ecommerce/orders` | 🔴 «표시할 주문이 없습니다» |
| `/ecommerce/shippings` · `/ecommerce/settlements` | 🔴 촬영 스크립트가 **«빈값»** 으로 분류 |
| `/ecommerce/orders/[id]` | 🔴 **동적 해결 실패** — 목록에 따라갈 항목이 없다 |
| `/ecommerce/settlements/periods/[id]` | 🔴 부모 목록이 404 |

🔴 **이것은 저하가 아니다.** 화면은 200 이고 오류 문구도 마커도 없다 — 데이터가 없는 것이다.
그래서 `TASK-MONO-707` 이 고친 저하 술어는 이 다섯 장을 **정상으로 통과시킨다**(옳다). 큐레이션을
막는 것은 저하가 아니라 **빈 목록**이고, 그 구별이 이 티켓이 서는 자리다.

결과 둘:

1. **포트폴리오**(`TASK-MONO-648` · `667`) — 이커머스의 «주문 생애주기» 를 보여 줄 장이 **한 장도 없다.**
   상품 카탈로그만으로는 「주문→배송→정산」이라는 이 프로젝트의 핵심 서사를 못 싣는다.
2. **동적 경로 표지**(`648` AC-1) — 동적 10개 중 `ecommerce` 테넌트에서 6개 성공 / 4개 실패이고,
   실패 4개 중 **3개가 「주문이 없어서」** 다. 술어를 고쳐도 안 늘어난다.

---

# Scope

## 포함

- 이커머스 데모 시드가 주문을 만들지 않는 이유를 **지목**한다: 시드 스크립트에 없는 것인가,
  있는데 실패하는가, 아니면 주문이 «이벤트로만» 생기는 설계라 시드할 자리가 없는가.
- 고친다면 **어느 층**에서 만드는지 결정: SQL 시드 vs API 호출(주문 생성 → 결제 → 배송 → 정산)
  vs 이벤트 발행. 🔴 주문은 상태 기계라 SQL 로 중간 상태를 박으면 **화면은 채워지고 도메인은
  거짓말을 한다** — 그 함정을 명시적으로 다룬다.
- 최소 목표: 서로 다른 상태의 주문이 **각 상태에 최소 1건씩** 있어서 목록·상세·배송·정산이
  전부 «내용 있는 화면» 이 된다.

## 제외

- 저하 판정 술어(`TASK-MONO-707` 에서 끝났다 — 이 화면들은 저하가 **아니다**).
- 큐레이션 자체(`648` AC-2/AC-4 · `667`).
- wms·scm 쪽 빈 화면(`/scm/inventory` 의 «표시할 스냅샷이 없습니다» 도 같은 모양이지만 기전이
  다르다 — 그쪽은 `TASK-MONO-683` · `675` 계열이다). 🔵 같이 고치려 하지 마라.

---

# Acceptance Criteria

- [x] **AC-0 — 착수 게이트: 전제부터 다시 재라.** 🔴 이 티켓은 «주문이 0건이다» 를 2026-09-18
      **한 창**에서 봤다. 착수 시점에 ⓐ 시드 스크립트에 주문 생성이 **있는가** 를 저장소에서
      먼저 읽고, ⓑ 있다면 그것이 **실패하는지** 성공하고도 안 보이는지(테넌트 경계·상태 필터)
      를 가른다. ⓑ 라면 이 티켓의 제목이 틀린 것이고 그 자리에서 고쳐 적어라.
      → **갈래 ⓐ. 제목은 옳다.** 아래 § AC-0 참조.
- [x] **AC-1 — 층을 고른다.** SQL / API / 이벤트 중 하나. 🔴 **고른 이유에 «주문은 상태 기계다»
      가 답으로 들어가야 한다** — 어느 층이 그 불변식을 지키는지가 선택의 축이다.
      → **API.** 아래 § AC-1 참조.
- [x] 🟢 **AC-2 — 시드가 화면을 채운다.** 다음 창에서 `/ecommerce/orders` 가 «표시할 주문이 없습니다»
      가 아니고, `/ecommerce/orders/[id]` 의 **동적 해결이 성공**한다(촬영 매니페스트로 판정 —
      사람 눈이 아니라 스크립트의 `ok`).
      → **이 세션에서 닫을 수 없다.** 판정 도구가 **촬영 매니페스트**인데 데모 창이 없고, 시드는
      **구운 AMI 사본**에서 돌기 때문에 이 변경은 **재굽기(소유자 게이트) 전에는 데모에 도달조차
      하지 않는다**. 그래서 이 티켓은 `in-progress/` 에 남는다 — `review/` 로 옮기지 않았다.
- [x] **AC-3 — 가드.** 🔴 **날짜로 재지 마라.** 시드가 조용히 비는 것을 무는 술어를 놓는다 —
      예: 시드 검증 스크립트가 «주문 ≥ N 건 · 서로 다른 상태 ≥ M» 을 단언한다. **bite**: 시드에서
      주문 생성을 지우면 빨개진다. → 두 층. 아래 § AC-3 참조.
- [x] **AC-4 — 한계를 적는다.** 이 시드로도 **못 만드는** 상태(예: 반품·부분취소)가 있으면
      그 목록을 적는다. 🔴 「전부 된다」로 적지 마라 — 다음 사람이 빈 화면을 보고 결함으로 읽는다.
      → 아래 § AC-4 참조.

---

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/` (주문 상태 기계)
- `infra/demo/seed/` — 데모 시드 진입점
- `tasks/in-progress/TASK-MONO-648-capture-every-page-for-the-application-portfolio.md` § AC-1 동적 경로
- `tasks/in-progress/TASK-MONO-707-the-capture-script-misreads-two-screen-states.md` § 창 실측 (저하 아님의 근거)

# Related Contracts

- 주문 생성/전이 API 계약. 🔴 시드가 API 층을 고르면 그 계약을 **읽고** 쓴다(시드가 계약을 우회하면
  화면은 채워지고 계약 검증은 여전히 공허하다).

---

# Edge Cases

| 경우 | 다룸 |
|---|---|
| 주문이 `ecommerce` 테넌트에만 있어야 하는가 | 🔴 2026-09-18 실측: 상품조차 `demo-corp` 에서는 **목록이 비어** 동적 해결이 0/10 이었다. 시드의 테넌트를 명시해라 |
| 시드가 두 번 돌면 | 주문이 두 배가 되면 안 된다(멱등) |
| 신선 볼륨 vs 기존 볼륨 | 🔴 기존 볼륨에서 이미 시드가 돈 인스턴스에는 안 들어간다 — 그 경우의 동작을 적어라 |
| 결제·배송이 비동기라면 | 시드 직후 화면이 «처리 중» 일 수 있다. AC-2 의 판정을 그 지연에 견디게 써라 |

# Failure Scenarios

1. **SQL 로 완료 상태를 박는다** → 화면은 채워지지만 아웃박스·이벤트 이력이 없어 WMS·정산이
   그 주문을 모른다. 포트폴리오용 스크린샷은 얻고 **도메인 일관성은 잃는다**.
2. **주문을 만들었는데 목록이 여전히 빈다** → 조회가 프로젝션/읽기모델을 보고 있고 그것이 안 찼다.
   🔵 이 저장소가 이미 데인 축이다(`TASK-MONO-675` 의 ref 테이블).
3. **시드가 창에서만 돌고 CI 에서는 안 돈다** → 다음 창에서 또 빈다. AC-3 의 가드가 그것을 문다.

---

---

# 구현 기록 (2026-09-18 UTC)

## AC-0 — 갈래 ⓐ: 시드에 주문 생성이 **없다**

`infra/demo/seed/seed-ecommerce.sh` 304줄 전수. 주문을 만드는 호출은 **한 줄도 없었다.**
그 자리에 있던 것은 *이미 있는 주문을 줍는* 블록이다:

```
  if http GET "$GW/api/orders?size=1"; then
    DELIVER_ORDER_ID="$(… grep -oE '"orderId":"…"' …)"
  fi
  if [ -n "$DELIVER_ORDER_ID" ] && http GET "$GW/api/orders/$DELIVER_ORDER_ID"; then
    … 배송 건 조회 …
  else
    seed_warn "데모 계정의 주문이 없습니다 — 배송 진행과 리뷰 시드를 건너뜁니다"
  fi
```

ⓑ(있는데 실패/안 보임)가 아니므로 **티켓 제목은 옳다.**

### 🔴🔴 아무도 안 적은 따름정리 — 지워진 것은 한 화면이 아니라 세 갈래다

`SHIP_ID` 와 `REVIEW_PRODUCT_ID` 는 **저 `if` 안에서만** 세팅된다. 주문이 0건이면

| 블록 | 위치 | 실제로 일어난 일 |
|---|---|---|
| 배송 진행 (PREPARING→…→DELIVERED) | 2절(운영자) `if [ -n "${SHIP_ID:-}" ]` | `else` 가지의 `seed_log` 한 줄만 찍고 **통째로 건너뜀** |
| 리뷰 | 3절(소비자) `[ -n "$REVIEW_PRODUCT_ID" ]` | 조건이 거짓이라 **elif 도 안 타고 조용히 지나감** |

즉 **배송 시드와 리뷰 시드가 매 창 실행되지 않았다.** 스크립트 헤더가 «시드는 배송을 실제로
진행시켜 리뷰 자격을 만든다» 라고 적어 두었는데, 그 문장은 **한 번도 참인 적이 없었다**(주문이
없었으므로). 요약 줄은 `실패 0` 이었다.

🔴 그리고 그 `seed_warn` 자체가 결함의 일부다. `lib.sh` 헤더가 시드의 성질을 이렇게 적는다 —
*"실패하면 데모가 비므로 **아무도 무시할 수 없다**"*. 데모가 그 데이터 없이는 존재할 수 없는데
경고로 지나가면, 그것은 정확히 이 저장소가 반복해서 당한 «초록인데 화면은 비었다» 다.
그래서 이 수정의 일부는 **그 경고를 단언으로 바꾸는 것**이다(AC-3 ①).

## AC-1 — 층 = **API**

세 줄로:

1. **주문은 상태 기계다.** `orders` 에 중간/종단 상태를 INSERT 하면 아웃박스에 행이 없고,
   `OrderPlaced`·`OrderConfirmed`·`PaymentCompleted` 가 하나도 발행되지 않는다 ⇒ 배송 건은
   **생기지 않고**(`shipping-api.md` Notes: 배송 건은 `OrderConfirmed` 소비로 생성), 정산 적립도
   **안 쌓이며**(`settlement-subscriptions.md`: 적립은 `PaymentCompleted` 소비), wms 는 그 주문을
   모른다. 화면은 차고 도메인은 거짓말한다 — 티켓의 Failure Scenario 1 그대로다.
2. **`lib.sh` 가 이미 그 정책을 코드로 강제한다**: *"넣을 수 있는 것은 실제 API 로 넣는다"*,
   `dbexec` 는 `--why` 없이는 실행되지 않으며, 가드 (y) 가 `lib.sh` 밖의 raw `psql`/`mysql` 을
   막는다. 주문에는 막힌 엔드포인트가 없으므로 `--why` 에 적을 사유가 존재하지 않는다
   ⇒ **주문에 `dbexec` 를 쓰지 않는다.**
3. **같은 논증이 이미 이 파일에 있다.** 헤더가 리뷰에 대해 *"시드가 그 규칙을 우회하면(리뷰 행
   직접 INSERT) 데모는 존재할 수 없는 상태를 보여준다"* 라고 적어 뒀다. 주문은 그 논증의 한 층
   아래일 뿐이고, 한 파일 안에서 층마다 다른 답을 쓸 이유가 없다.

🔵 이벤트 층(Kafka 직접 발행)도 기각했다: 아웃박스를 건너뛰므로 `order_outbox` 이력이 비고,
«주문 행은 없는데 이벤트만 있는» 더 나쁜 불일치를 만든다.

## 만들 수 있는 상태와 그 경로 (전부 계약서 대조)

| 상태 | 슬롯 | 경로 |
|---|---|---|
| `CANCELLED` | 1 | `POST /api/orders` → `POST /api/orders/{id}/cancel` (소유자) |
| `PENDING` | 2 | `POST /api/orders` 만. **결제하지 않는다** |
| `CONFIRMED` | 3 | 위 + `POST /api/payments` → `POST /api/payments/confirm` → 재고 예약 사가가 확정 |
| `SHIPPED` | 4 | 위 + 운영자 `PUT /api/shippings/{id}/status` 를 **SHIPPED 에서 멈춘다** |
| `DELIVERED` | 5 | 위 + IN_TRANSIT → DELIVERED (기존 진행 블록. 리뷰 자격도 여기서 생긴다) |

🔴 `POST /api/admin/orders/{id}/status` 는 **쓰지 않는다**. ① `SHIPPED`/`DELIVERED` 는 거기서
거절된다(400 — 주문은 배송의 되돌아오는 다리로만 그 둘에 간다, ADR-MONO-022 §D7). ② `CONFIRMED`
는 받지만 `AdminOrderStatusService` 에 **이벤트 발행기가 주입되어 있지 않다**(소스 확인) — 행만
바뀌고 배송 건도 정산 적립도 안 생긴다. 그래서 시드는 주문 상태가 아니라 **배송**을 움직인다.

🔵 기존 배송 진행 블록은 **함수(`ship_progress`)로 바꿔 두 번 부른다** — 사본을 만들지 않았다.
🔵 조회(구매자 전용 `GET /api/shippings/orders/{id}`)와 전이(운영자 `PUT`)의 **신원이 다른 것**은
실측으로 기록된 제약이라 그대로 뒀다.

## AC-3 — 가드 두 층 (날짜 아님)

### ① 진짜 게이트 — 시드 안의 런타임 사후조건 (`seed-ecommerce.sh` § 5)

방금 만든 것을 **운영자 토큰으로 다시 읽어서** 잰다. 하한 미달은 전부 **`seed_fail`** 이다.

- `GET /api/admin/orders?size=50` → 주문 **≥ 5** · 서로 다른 상태 **≥ 4**
- `GET /api/shippings?size=1` → 배송 건 **≥ 2**

🔴 읽는 쪽이 **운영자 평면**인 것이 술어의 일부다. 구매자 평면으로 재면 «만들어졌다» 만 증명하고
«콘솔이 본다» 는 증명하지 못한다 — TASK-BE-576 이 정확히 그 틈이었다.
🔴 상태 하한이 5 가 아니라 **4** 인 이유: `PENDING` 은 설계상 한시적이다(아래 § AC-4 4번).
5 를 요구하면 **성공이 고장난다**.

### ② 오프라인 파수꾼 — `EcommerceOrderDemoSeedTest` (order-service)

스택 없이 CI 에서 빨개지는 쪽. **시드 파일 자신을 파싱**한다(상수 재기술 금지 —
`FanArtistDemoSeedTest` 선례: *"재기술한 테스트는 재기술을 검증한다"*). 6칸:

1. `POST "$GW/api/orders"` 가 존재하고, `Idempotency-Key` 가 **고정 리터럴**이다.
2. 시드의 `dbexec` 대상이 `ecommerce-user-postgres` **뿐**이다(주문 SQL 직삽입 금지).
3. `"$GW/api/admin/orders/` 호출이 없다 + `AdminOrderStatusService` 에 `OrderEventPublisher` 가
   없다(=그 엔드포인트를 쓰면 안 되는 **이유**를 소스에서 다시 확인한다. 그 서비스가 나중에
   발행기를 갖게 되면 이 칸이 빨개지는데, 그건 «핀이 낡았다» 는 옳은 신호다).
4. `order_wait_status` 인자가 전부 실제 `OrderStatus` 상수다(셸 ↔ 자바 enum 대조).
5. 🔴 `ship_progress` 두 호출의 **최종 목표가 서로 다르다**(`{SHIPPED, DELIVERED}`).
   **이 칸은 런타임 게이트가 못 잡는 것을 잡는다**: 둘 다 DELIVERED 로 «단순화» 하면 시드 직후엔
   여전히 4종(CANCELLED+PENDING+CONFIRMED+DELIVERED)이라 ①이 통과하고, ~35분 뒤 PENDING 이
   자동취소된 다음에야 빨개진다. 두 층이 **서로 다른 것을 잰다**는 근거다.
6. 하한들이 시드가 실제로 다루는 슬롯 수로 **도달 가능**하고, 세 하한 가지가 전부 `seed_fail`
   이며 `seed_warn` 이 아니다.

모든 칸에 **비공허성 단언**을 붙였다(파싱 0건은 «없음» 이 아니라 추출식이 깨진 것).

🔴 배선: `order-service/build.gradle` 의 `test` 에 `inputs.file(… seed-ecommerce.sh)` 를 선언하고
`ci.yml` 의 `ecommerce` 필터에 **정확한 파일 경로 1행**을 더했다. 둘 중 하나만 하면
`Outside-module Gradle input filter coverage` 잡이 이름을 대며 빨개진다(그 잡을 먼저 읽고 넣었다).

### bite

`order_place` 의 `http POST "$GW/api/orders"` 를 지우고 ②를 돌렸다 → **빨강**(아래 § 실행 기록).
①은 **이 세션에서 돌릴 수 없다** — 기동한 스택이 필요하고 데모 창이 없다. 술어가 무는 자리는
소스로만 확인했다(하한 미달 → `seed_fail` → `seed_summary` 가 rc 1).

## AC-4 — 이 시드가 **못 만드는** 것

🔴 「전부 된다」가 아니다.

1. **반품 / 부분취소.** `PaymentRefunded` 를 만들지 않는다 ⇒ `commission_accrual` 의 `REVERSAL`
   행과 `promotion_cost` 의 `REVERSAL` 행이 **없다**. 붙이려면 `GET /api/payments/orders/{orderId}`
   로 `paymentId` 를 얻어 `POST /api/payments/{paymentId}/refund`(Idempotency-Key 필수).
2. **`BACKORDERED`.** 재고 부족일 때만 생긴다. 카탈로그 시드 재고가 넉넉해 수량 1 주문으로는
   나오지 않고, 일부러 초과 주문하면 그 주문이 CONFIRMED 로 못 가 배송·정산 사슬이 끊긴다.
3. **`STUCK_RECOVERY_FAILED`.** 도메인에 남아 있지만 더 이상 주 종단이 아니다(TASK-BE-435 가
   `CANCELLED(PAYMENT_TIMEOUT)` 으로 바꿨다). 시드가 밟을 경로가 없다.
4. 🔴 **`PENDING` 은 한시적이다 — 약 35분.** `OrderStuckDetector` 가 `PENDING AND payment_id IS
   NULL` 을 유예 **1800초** 뒤부터 **60초**마다 쓸고, **5회째**에 `CANCELLED(PAYMENT_TIMEOUT)` 으로
   자동 취소한다. ⇒ **촬영은 시드 직후에.** 그 뒤에는 상태가 4종이 되고 CANCELLED 가 2건이 된다.
   (이것이 ①의 하한을 4로 잡은 이유다.)
5. **정산 — 적립은 자동, 지급은 아니다.** 적립(`commission_accrual`)은 `PaymentCompleted` 소비로
   자동으로 쌓이므로 `/ecommerce/settlements` 의 적립 표는 결제만으로 찬다. **지급(`seller_payout`)
   은 기간 **마감**이라는 운영자 행위가 있어야 생긴다** — 그래서 시드가 기간을 열고·마감하고·
   지급을 실행한다(§ 4). 남는 한계 넷:
   - (a) 지급은 **모의**다(합성 참조번호, 실제 송금 없음 — 계약서가 명시).
   - (b) 기간 창이 고정 리터럴 `[2026-03-01, 2030-01-01)` 이라 **2030 이후에는 다시 빈다**.
     (현재시각 기준으로 열면 2회차 실행이 기간을 또 연다 — 기존 기간 시드가 같은 이유로
     고정 리터럴이다.) 🔴 앞 기간 `[2026-01-01, 2026-02-01)` 과 **겹치지 않게** 골랐다:
     계약서가 «겹치는 두 창을 둘 다 마감하면 교집합의 적립이 두 번 지급된다» 를 **방어하지 않는
     의도된 잔여 위험**으로 적어 두었기 때문이다.
   - (c) 🔴 적립은 **`default` 셀러에 붙는다.** V8/V19 카탈로그 상품의 `seller_id` 가 전부
     `default` 이고, 주문 라인의 `sellerId` 는 그 값을 그대로 싣기 때문이다. 시드가 수수료율 5%를
     설정하는 `demo-seller` 는 상품이 없으므로 **잔액 0** 이고, `default` 의 적립은 **플랫폼 기본
     수수료율**로 계산된다. ⇒ 「셀러」 탭의 `demo-seller` 와 「정산」 탭의 적립은 **서로 다른
     셀러를 말한다**. 붙이려면 카탈로그 상품 일부의 소유 셀러를 옮겨야 하고, 그건 이 티켓의
     범위가 아니다(제품 결정).
   - (d) **기존 볼륨**: 이 변경 전에 시드가 돈 인스턴스에는 `2026-01` 기간만 있고 그 창은 적립을
     담지 않는다. 새 기간은 `api_create_unless` 로 추가되지만, 그 인스턴스에서 이미 CLOSED 인
     기간은 다시 마감되지 않는다(마감 후 쌓인 적립은 지급에 안 들어간다).
6. **쿠폰이 적용된 주문.** 시드는 쿠폰을 발급하지만 주문에 **적용하지 않는다**(`couponId` 미전달).
   ⇒ `promotion_cost` 원장과 `discountAmount > 0` 인 주문이 없다.
7. **다중 셀러 주문.** 한 주문은 라인 1개다. 「한 주문이 여러 셀러에 걸친다」는 계약의 성질은
   데모에 안 보인다.
8. **상품 이미지(MinIO).** 기존 한계 그대로 — 스크립트 끝의 주석이 계속 그 자리에 남아 있다.

## 멱등 / 기존 볼륨

- 주문은 `Idempotency-Key: demo-seed-order-<슬롯>` **고정 키**라 두 번 돌아도 **두 배가 되지 않는다**
  (같은 키+같은 사용자의 재요청은 원래 주문을 돌려준다 — `order-api.md`). 그래서 주문에는
  `api_create_unless` 식 탐지 프로브를 만들지 않았다: **계약이 직접 답한다.**
- 결제 생성은 멱등(201, 부작용 없음), 재승인은 409 로 거절 ⇒ 둘 다 «존재» 로 센다.
- 취소 재실행은 422 `ORDER_CANNOT_BE_CANCELLED` ⇒ «이미 취소됨» 으로 센다.
- 기간 마감 재실행은 409 `PERIOD_ALREADY_CLOSED`, 지급 실행은 `(periodId, sellerId)` 멱등.
- 🔴 **기존 볼륨에 이미 있던 주문은 그대로 남는다.** 사후조건의 하한은 `≥` 라서 문제되지 않지만,
  그 인스턴스의 목록에는 시드가 만들지 않은 주문이 섞인다.

## 실행 기록 (rc · 2026-09-18 UTC)

| 항목 | rc | 비고 |
|---|---|---|
| `bash -n infra/demo/seed/seed-ecommerce.sh` | 0 | |
| `shellcheck` | — | **이 호스트에 없다**(`command -v shellcheck` 실패). 못 돌린 것을 «통과» 로 적지 않는다 |
| `gradlew :…:order-service:test --tests EcommerceOrderDemoSeedTest` | 0 | **7칸 실행**(XML `tests="7" failures="0"`) — rc 만 보면 0칸 실행도 0 이다 |
| `gradlew :…:order-service:test` (전체) | 0 | |
| `scripts/check-index-queue-drift.sh` | 0 | INDEX 9개 · 양방향 일치 |
| `scripts/check-task-id-collision.sh` | 0 | active 15건 · 중복 0 |
| `scripts/check-walkthrough-ledger-drift.sh` | 0 | |
| `scripts/check-seed-catalogue-parity.sh` | 0 | 번들 24 · postgres 24 · h2 24 |
| `scripts/check-outside-module-input-filter-coverage.sh` | 0 | **2 → 3건**(새 선언을 실제로 봤다는 증거) |
| `scripts/check-required-check-names.sh` | 0 | ci.yml 은 **필터 1행만** 바꿨다 — 잡 이름은 한 글자도 안 건드렸다 |
| `scripts/check-demo-resolver-copies.sh` | 0 | |
| `scripts/check-dev-seed-migration-band.sh` | 0 | |
| `scripts/check-ci-baseline-reachable.sh` | 0 | |
| `scripts/check-vercel-build-triggers.sh` | 0 | |
| `scripts/check-ls-files-guard-count.sh` | 0 | 22/59 — **`scripts/` 에 추가·삭제가 없으므로 전수 스윕 규칙은 해당 없음**(`git status` 로 확인) |
| `scripts/check-flyway-version-collision.sh` | 0 | |
| `scripts/check-stage-inherits-its-contexts.mjs` | 0 | |
| `scripts/check-prerendered-demo-verdict.sh` | **1** | 🔵 **내 변경과 무관한 환경 전제 미충족**: `DEMO_API_BASE` 가 비어 있어 가드가 스스로 «공허한 통과» 를 거부한다. 빌드된 web-store + 그 env 가 있어야 돌고, CI 는 그 env 를 건다 |

### bite (AC-3)

사본을 `scratchpad` 에 떠 놓고 실트리를 변형했다 — 🔴 `git checkout --` 로 되돌리지 않았다
(그 하네스가 작업분을 지운 전례가 있다).

| 변형 | 오프라인 파수꾼 rc | 무는 칸 |
|---|---|---|
| 대조군 (무변형, `--rerun` 강제) | **0** | 7칸 전부 통과 · 태스크가 **실제로 실행**됨(FROM-CACHE 아님) |
| ① `order_place` 의 `http POST "$GW/api/orders"` 제거 (= **주문 생성 삭제**) | **1** | `the seed places orders through POST /api/orders with a fixed Idempotency-Key` |
| ② `ship_progress` 두 호출의 최종 목표를 **둘 다 DELIVERED** 로 | **1** | `the two shipments stop at different states…` |

🔵 ①과 ② 사이에 파수꾼이 **캐시를 타지 않고 다시 돌았다**는 것 자체가 `inputs.file` 배선의
증거다 — 시드만 바꿔도 `:test` 가 무효화된다(그 선언이 없으면 UP-TO-DATE 로 초록이 된다).

⚪ **런타임 게이트(①층)의 bite 는 못 돌렸다.** 기동한 스택이 필요하고 이 세션에 데모 창이 없다.
소스로만 확인했다: 하한 미달 → `seed_fail` → `SEED_FAILURES>0` → `seed_summary` 가 rc 1.
🔴 이것을 «통과» 로 적지 않는다 — 측정하지 않은 것이다.

---

# 분석 / 구현 권장

(분석=Opus 5 / 구현 권장=Opus — 주문 상태 기계와 이벤트 경계를 건드리므로 단순 시드 추가가 아니다)


---

# 🟢 AC-2 판정 — 데모 창에서 닫았다 (2026-09-22 UTC · 분석=Opus 5)

## 🔴 먼저: AC-2 는 «기다리면 되는 칸» 이 아니었다 — **시드가 고장나 있었다**

재굽기로 신선 볼륨을 산 뒤 부팅 시드를 읽으니 주문 생성이 **다섯 번 다 실패**했다:

```
[seed:ecommerce] ✗ 주문 시드: 상품 b0000000-…-0002 에서 variantId/price 를 추출하지 못했습니다
  (b0000000-…0003 · …0004 · …0005 · …0006 도 같은 줄)
[seed:ecommerce] SHIPPED 에서 멈출 배송 건이 없습니다 — 주문 하나가 SHIPPED 상태에 앉지 못합니다
[seed:ecommerce] 진행할 배송 건이 없습니다 — 리뷰 자격(DELIVERED)을 만들지 않습니다
```

🔵 **시드의 자기 보고는 옳았다** — `seed_fail` 이 다섯 번 울렸고 요약도 실패로 셌다. 이 티켓이
AC-3 에서 만든 «조용히 비는 것을 무는» 층이 실제로 물었다. 무너진 것은 **추출식**이다.

## 원인 — «내가 부르는 이름» 으로 «남의 코퍼스» 를 grep 했다

| | 값 |
|---|---|
| 상품 상세가 주는 것 (`GET /api/products/{id}`, 실측) | `"variants":[{"id":"c0000000-…-0005","optionName":"28","stock":40,"additionalPrice":0}, …]` |
| 시드가 찾던 것 (`seed-ecommerce.sh:115`) | `grep -oE '"variantId":"[0-9a-f-]{36}"'` |

🔴 **`variantId` 라는 키는 상품 응답에 없다.** 그것은 **주문** 평면의 이름이고
(`PlaceOrderCommand.OrderItemCommand.variantId`), 상품 평면에서 같은 개념은 `variants[].id` 다.
두 이름이 **일부러 다른데** 한쪽 이름으로 다른 쪽을 읽었다.

## 고침 + bite

```bash
vid="$(printf '%s' "$body" | sed -n 's/.*"variants":\[//p' | grep -oE '"id":"[0-9a-f-]{36}"' | head -1 | cut -d'"' -f4)"
```

- **bite**: `EcommerceOrderDemoSeedTest#theVariantIdIsReadFromTheProductResponseShape`
  — 옛 줄로 되돌리면 **rc=1**, 그 칸만 빨강(실측). 고친 줄에서 **BUILD SUCCESSFUL**.
- 🔵 그 칸은 모듈 **안**의 `PlaceOrderCommand.java` 도 읽어 «주문 평면은 진짜로 `variantId` 를
  쓴다» 를 먼저 세운다 — 그래야 「이름이 다르다」가 단언이 되지 추측이 안 된다.

## 🟢 고친 시드를 창에서 돌린 결과

```
[seed:ecommerce] 주문 1: e8ba8934… (슬림핏 데님 청바지 · 59000원)   … 주문 5 까지
[seed:ecommerce] 주문 1 취소 (CANCELLED) · 주문 2 PENDING 유지 · 결제 승인 3건 → CONFIRMED
[seed:ecommerce] 배송 … → SHIPPED · 배송 … → SHIPPED → IN_TRANSIT → DELIVERED
[seed:ecommerce] 생성  리뷰(프리미엄 견과류 선물세트) · 정산 적립 3 건 → 마감 → 지급
[seed:ecommerce] 사후조건 — 운영자 평면 주문 5 건 · 서로 다른 상태 5 종
                 (CANCELLED CONFIRMED DELIVERED PENDING SHIPPED)
[seed:ecommerce] 사후조건 — 배송 건 3 건
```

⇒ **AC-3 의 런타임 층(①)이 이 창에서 실제로 초록으로 발화했다.** 위 § 의 ⚪(*"런타임 게이트의
bite 는 못 돌렸다"*)가 «돌렸다» 로 바뀐다 — 다만 그것은 **양성 발화**이고 음성(bite) 은 여전히 ⚪.

## 🟢 AC-2 의 술어 — 촬영 매니페스트로 판정했다

`node scripts/capture-portfolio.mjs --app console` (`DEMO_TENANT=ecommerce`):

```
✔ /ecommerce/orders            (textLen 726, empty=false)
✔ /ecommerce/orders/[id] → /ecommerce/orders/9bce4a2b-109c-46ca-bcd2-f186257fd83d
✔ /ecommerce/products/[id] · /ecommerce/products/[id]/edit
✔ /ecommerce/promotions/[id] · /ecommerce/sellers/[id] · /ecommerce/users/[id]
ecommerce 21장 중 빈 장 0
```

🔵 **동적 해결이 성공했다는 것이 곧 «목록에 따라갈 행이 있다»** 이다 — 사람 눈이 아니라
스크립트의 `ok` 가 판정했다(AC-2 가 요구한 그대로). 스토어 쪽도 함께 열렸다:
`store /my/orders/[id] → /my/orders/9bce4a2b-…` (21/22 촬영, 실패 1).

## 🔴🔴 AC-3 정정 — 그 사후조건은 «콘솔이 본다» 를 **증명하지 않는다**

AC-3 의 사후조건은 주석에 *"읽는 쪽이 운영자 평면(`/api/admin/orders`)인 것이 중요하다 … 콘솔이
읽는 바로 그 엔드포인트로 재야 화면이 찬다는 뜻이 된다"* 라고 적는다. **엔드포인트는 맞는데
테넌트가 다르다.** 시드는 `OP_TOKEN="$(operator_token ecommerce)"`(`:413`)로 **`ecommerce`**
테넌트를 assume 하고, 촬영·기본 콘솔 세션은 **`demo-corp`** 다. 대조군 실측(같은 순간·같은 URL):

| 테넌트 | `/api/admin/orders` | products | users | sellers |
|---|---|---|---|---|
| `ecommerce` | **5** | **24** | **1** | **2** |
| `demo-corp` | **0** | **0** | **0** | **0** |

🔴 **이 축 자체는 새 발견이 아니다** — `TASK-MONO-648` 이 이미 *"`/ecommerce/*` 3장은 테넌트 `ecommerce` 로 재촬영한 뒤에만 승인 목록으로 확정한다 … `demo-corp` 로 찍으면 그 매니페스트는 **빈 표 세 장을 승인된 것으로** 기록한다"* 라고 적어 두었다. 새로운 것은 **수치 대조군**과, **이 티켓의 사후조건이 같은 함정에 빠져 있다**는 연결이다(648 의 노트는 촬영에 대한 것이었다).

⇒ 첫 촬영(테넌트 `demo-corp`)에서 ecommerce 14장 중 **9장이 빈 목록**이었고, 그것은 콘솔 결함도
게이트웨이 결함도 아니다. 🔴 **그래서 이 사후조건은 그 화면에 대해 공허하다** — 시드가 쓴 테넌트를
그대로 다시 읽으니 언제나 참이다. 🔵 이 어긋남은 이 티켓이 고칠 범위가 아니라 **선택**이다
(시드를 `demo-corp` 로 옮길 것인가 · 콘솔의 ecommerce 화면이 `ecommerce` 테넌트를 기본으로 볼
것인가 · 촬영이 도메인별 테넌트를 고를 것인가) ⇒ **별도 티켓으로 기안한다**(AC-2 규율:
결함/결정은 여기서 처리하지 않는다).

## ⚪ 여전히 안 잰 것

- **AC-3 런타임 층의 bite**(주문 생성을 지우면 빨개지는가) — 양성 발화만 봤다.
- `/ecommerce/notifications/templates/[id]/edit` — 목록이 비어 동적 해결 실패(템플릿 시드는 있으나
  상세 링크가 없다). 이 티켓의 다섯 화면 밖이다.
