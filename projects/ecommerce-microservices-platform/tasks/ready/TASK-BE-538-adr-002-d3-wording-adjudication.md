# TASK-BE-538 — ADR-002 Decision-3 의 문자적 요구를 만족하는 구현이 하나도 없다: 문구를 판정한다

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (ADR 문구 판정 + 20 엔드포인트 분류 재검. 구현이 아니라 판단이 실질이다)

> `TASK-BE-535`(PR #2747) 와 `TASK-BE-536`(PR #2754) 이 **양쪽 다** 명시적으로 범위 밖에 남기며 *"ADR 소유자의 결정이지 이 task 의 것이 아니다"* 라고 적은 항목. 두 티켓이 근거 데이터를 이미 남겨 뒀으므로 지금이 판정 적기다.

---

## ⚠️ ACCEPT 게이트 경계 (먼저 읽어라)

**이 task 는 ADR-002 를 개정하지 않는다.** `ADR-002` 는 `Accepted` 상태이고, ACCEPTED ADR 의 Decision 절을 고치는 것은 [`platform/architecture-decision-rule.md`](../../../../platform/architecture-decision-rule.md) § The ACCEPTED Gate 대상이다 — **맨 "진행" 으로는 열리지 않으며 사용자의 정확형 의사표시가 필요**하다.

이 task 의 산출물은 **판정과 근거**다: 문구가 틀렸는지 / 코드가 틀렸는지 / 둘 다 옳고 표현만 좁은지, 그리고 고친다면 **정확히 어떤 문장으로** 고칠지의 제안. 실제 개정은 그 제안이 승인된 뒤 별 task 다.

---

## Goal

[`ADR-002`](../../docs/adr/ADR-002-saga-over-distributed-transaction.md) Decision-3 은 이렇게 적혀 있다:

> **Idempotency Key**: 금전·재고 변경 API는 클라이언트 측 키를 받아 **중복 요청을 결정적으로 거부**.

2026-07-20 인구조사가 이 문장을 코드에 대고 측정했다. **범위 안 20 엔드포인트 중 이 문장을 문자 그대로 만족한 것은 0개였다.** 분포는 `0 ENFORCED / 1 OPTIONAL / 13 NATURAL-KEY / 6 NONE`.

그런데 **13 개(NATURAL-KEY)는 실제로 안전하다** — unique 제약이나 상태기계가 중복을 막는다. 즉:

- 이 ADR 은 **코드가 실제로 하는 안전 확보 방식(자연키·상태기계)을 인정하지 않는다.**
- 그 결과 **옳은 코드가 문자 그대로는 영구 위반 상태**로 남는다.
- 그리고 이 상태를 **어떤 검사도 잡지 못한다** — 잡았다면 13 건이 계속 RED 였을 것이다.

`TASK-BE-535`/`536` 이 NONE 6 건을 닫아 **문자적 만족이 0 → 6 으로 올라갔다.** 그러나 **13 건은 여전히 문자 그대로는 위반이면서 옳다.** 숫자가 방금 바뀌었으므로 지금이 문구를 현실에 맞출 시점이다.

### 이것이 왜 `TASK-MONO-444` 와 같은 클래스인가

`MONO-444` 는 `platform/error-handling.md` 가 **코드에 없는 동작을 선언**하는 건이다. 이 task 는 ADR 이 **코드가 실제로 쓰는 안전 수단을 인정하지 않는** 건이다. 방향은 다르지만 성질은 같다 — **권위 문서와 진실이 어긋나 있고, 자동 검사는 그 축을 보지 않는다.** 둘을 같이 읽으면 이 저장소의 "선언 ↔ 진실" 결함이 두 방향 모두에서 나타난다는 게 보인다.

---

## Scope

### In Scope

1. **AC-0 인구조사 재측정** (아래 AC-0). 20/0/1/13/6 은 **출처가 아니라 가설**이다.
2. 세 갈래 중 판정 + 근거 기록:
   - **(A) 문구가 좁다** — 자연키·상태기계도 "결정적 거부" 의 정당한 구현임을 인정하도록 D3 를 다시 쓴다. *현재로선 가장 유력해 보이나, AC-0 재측정이 뒤집을 수 있다.*
   - **(B) 문구가 옳고 코드가 미달** — 13 건 전부에 클라이언트 키를 붙인다. **BE-535/536 이 둘 다 "순수 비용" 이라며 명시적으로 거부한 방향**이므로, 고르려면 그 판단이 왜 틀렸는지를 적어야 한다.
   - **(C) 범위 정의가 문제** — "금전·재고 변경 API" 의 경계가 모호해서 20 이라는 모집단 자체가 흔들린다. 그렇다면 고칠 것은 요구가 아니라 **적용 범위 문장**이다.
3. 판정이 (A)/(C) 면 **제안 문장을 확정 형태로 작성**한다(개정 task 가 그대로 쓸 수 있게).
4. 개정을 수행할 후속 task 의 형태를 적는다(ACCEPT 게이트 절차 포함).

### Out of Scope

- **ADR-002 본문 수정 0** — § ACCEPT 게이트 경계 참조. 이 task 는 제안까지다.
- **코드 변경 0** — 어떤 엔드포인트도 건드리지 않는다. (B) 로 판정돼도 구현은 별 task.
- **`platform/error-handling.md` 0** — 그건 `TASK-MONO-444` 다.
- **`libs/java-web-servlet` `IdempotencyKeyFilter` 채택 재논의 0** — 비채택은 `TASK-BE-430` 에 기록된 별도 결정이다.

---

## Acceptance Criteria

- **AC-0 (gate — 인구조사 재측정)** — `0 ENFORCED / 1 OPTIONAL / 13 NATURAL-KEY / 6 NONE` 과 "범위 안 20 개" 를 **직접 다시 센다.** BE-535/536 이 6 건을 바꿨으므로 **분포는 이미 달라져 있어야 한다** — 달라지지 않았다면 내 측정이 틀린 것이다. 엔드포인트 목록도 재열거한다(모집단을 물려받지 않는다). **탐지식은 known-positive 로 자기검증한 뒤 쓴다** (예: `order-service` 는 멱등 관련 hit 가 많아야 한다 — 0 이 나오면 탐지식이 깨진 것).
- **AC-1** — (A)/(B)/(C) 판정 + 근거. 특히 (B) 를 고른다면 BE-535/536 의 "NATURAL-KEY 13 건에 키를 붙이는 것은 순수 비용" 판단을 **명시적으로 반박**해야 한다.
- **AC-2** — (A)/(C) 판정 시 **제안 문장이 확정 형태**로 존재한다. "이런 취지로 고치자" 가 아니라 그대로 붙여넣을 수 있는 문장이어야 한다.
- **AC-3** — 제안 문장이 **현재 코드베이스를 정확히 기술**하는지 20 개 전부에 대고 대조한다. 새 문구가 또 일부를 영구 위반으로 남기면 같은 결함을 반복하는 것이다.
- **AC-4** — 후속 개정 task 의 형태 기록(ACCEPT 게이트 절차 명시).
- **AC-5** — 문서만. `apps/**` diff 0, ADR 본문 diff 0.

---

## Related Specs

- [`ADR-002`](../../docs/adr/ADR-002-saga-over-distributed-transaction.md) § Decision 3 — 판정 대상
- [`platform/architecture-decision-rule.md`](../../../../platform/architecture-decision-rule.md) § The ACCEPTED Gate — 왜 이 task 가 개정까지 가지 않는가
- `tasks/done/TASK-BE-535-money-path-duplicate-request-guards.md` — 인구조사 출처 + money 2 건, "13 건에 키를 붙이지 마라" 판단
- `tasks/done/TASK-BE-536-inventory-coupon-path-duplicate-request-guards.md` — stock/coupon 4 건, 혼합 메커니즘이 옳다는 실증
- `../../../../tasks/ready/TASK-MONO-444-error-registry-describes-overlap-rejection-that-code-permits.md` — 같은 "선언 ↔ 진실" 클래스의 반대 방향 사례

---

## Related Contracts

- 없음 — 판정 task 다. 계약·스키마·코드 변경 0.

---

## Edge Cases

1. **🔴 인구조사 숫자를 물려받는 것** — 20/13/6 은 내가 만든 숫자고 **가설이다.** BE-535/536 이 6 건을 바꿨으니 재측정하면 달라야 정상이다. 같게 나오면 측정이 잘못된 것이다.
2. **"문서를 코드에 맞춘다" 의 함정** — 문구를 코드에 맞춰 느슨하게 고치면 *어떤 코드도 위반이 아닌* 무력한 문장이 될 수 있다. **새 문구도 무언가를 거부할 수 있어야 한다** — 그러지 못하면 규칙이 아니라 서술이다.
3. **NATURAL-KEY 13 건이 정말 안전한지** — "unique 제약이 있다" 와 "중복 요청이 실제로 거부된다" 는 다르다. `BE-536` 에서 **제약은 있는데 plain `save()` 라 커밋 시점에야 터져 500 으로 새는** 사례가 실제로 나왔다(`uq_product_variants_option`). **13 건 중 같은 지문이 더 있는지 표본이라도 확인하라** — 있으면 그것이 이 task 의 가장 큰 수확이다.
4. **(C) 가 과소평가되기 쉽다** — "금전·재고 변경 API" 라는 범위 문장이 모호하면 다음 인구조사도 다른 20 을 셀 것이다. 요구를 고치는 것보다 범위를 고치는 게 답일 수 있다.

---

## Edge 3 수행 결과 (2026-07-20 — 선행 완료)

Edge 3(*"unique 제약이 있다"* ≠ *"중복이 실제로 거부된다"*)를 **표본이 아니라 전수**로 수행했다. ecommerce `apps/**` 의 유니크 제약 전부를 열거하고 각 쓰기 경로를 추적했다. **이 task 의 가장 큰 수확이 맞았고, 남은 (A)/(B)/(C) 판정에 직접 영향을 준다.**

### 결정적 발견 — 중복 거부의 종착지가 대부분 500 이다

`@ExceptionHandler(DataIntegrityViolationException.class)` 보유 현황(형제 대조):

- **보유**: wms `outbound` · scm `procurement` · finance `account` · fan-platform 4개 · ecommerce `auth`·`user`
- **미보유**: ecommerce 나머지 **9개 서비스** — 잡히지 않은 제약 위반은 각 서비스 자기 `@ExceptionHandler(Exception.class)` 로 낙하해 **500 `INTERNAL_ERROR`**

이 서비스들의 `ConstraintViolationException` 핸들러는 **전부 `jakarta.validation`**(빈 검증)이라 DB 제약과 무관하다. 이름 유사성이 "이미 처리됨" 처럼 보이게 한다.

### 분류 결과

| 제약 | 서비스 | 판정 |
|---|---|---|
| `uq_product_variants_option` (`POST /{id}/variants`) | product | **SAFE** — `saveAndFlush`+catch → 409 |
| `uq_product_variants_option` (`POST /admin/products`) | product | **LEAKS-500** — 같은 제약의 두 번째 쓰기 경로, plain `save`, 미방어 |
| `uq_orders_idempotency` | order | **RACE-ONLY→500** — catch 가 plain `save` 를 감싸 **죽은 코드** |
| `uq_seller_commission_rate_tenant_seller` | settlement | **RACE-ONLY→500** |
| `uq_seller_payout_period_seller` | settlement | **RACE-ONLY→500** |
| `uq_reviews_user_product_active` | review | **RACE-ONLY→500** + 선체크/제약 범위 불일치 |
| `uq_template_tenant_type_channel` | notification | **RACE-ONLY→500** (선체크 컬럼=제약 컬럼, 가장 정합) |
| `uq_push_subscriptions_tenant_endpoint` | notification | **제약에 도달 불가** — 조회가 제약보다 넓어 INSERT 분기에 안 감 |
| `uq_notifications_event_id` | notification | **제약이 쓰기 모양과 어긋남** — 정상 최초 전송이 위반 → DLQ |
| `uq_stranded_refund_open_payment` | payment | **SAFE** — `REQUIRES_NEW` 라 flush 가 호출자 try 안에서 일어남 |
| `uq_wishlist_items_user_product` | user | **SAFE** — 선체크 409 + DIVE 핸들러가 경합까지 커버 |
| `uq_shippings_order_id` / `uq_stock_reservations_order_id` / `uq_user_profiles_user_id` | shipping·product·user | HTTP 미도달(컨슈머 전용), 컨슈머 내 경합은 재시도/DLQ |
| `uq_product_variants_sku` | product | **NOT-REACHABLE** — `sku` 를 쓰는 코드 없음 |

### Edge 3 가 (A)/(B)/(C) 판정에 주는 것

**"13 건은 실제로 안전하다" 는 전제가 무너졌다.** 안전한 것은 소수이고, 다수는 **순차 요청에 대해서만** 안전하며 동시 요청에서는 500 이다. 즉 D3 의 *"중복 요청을 결정적으로 거부"* 를 **자연키·상태기계도 만족한다고 인정하는 (A) 안**을 그대로 쓰면, **실제로는 500 을 내는 경로들까지 규정 준수로 인정**하게 된다.

⇒ **(A) 를 고르더라도 "자연키·상태기계"만으로는 부족하고, "동시 요청에서도 4xx 로 거부된다" 는 조건이 문장에 들어가야 한다.** 그래야 Edge 2("아무것도 거부하지 못하는 문장") 를 피한다. 이것이 Edge 3 의 판정 기여다.

### 파생 티켓 (이 task 는 여전히 코드 변경 0)

Edge 3 의 수확은 **전부 별 task 로 분리**했다 — 이 task 는 판정 문서로 남는다:

- `TASK-BE-539` — `uq_notifications_event_id` 모양 오류 → 정상 이벤트 DLQ 유실
- `TASK-BE-540` — 선체크 범위 ≠ 제약 범위 2건 (review · notification push)
- `TASK-BE-541` — 지연 flush 로 죽은 중복 방어 catch + 이를 가려 온 불가능 픽스처 테스트
- `TASK-BE-542` — 레지스트리가 선언한 `DATA_INTEGRITY_VIOLATION` 409 를 ecommerce 9개 서비스가 미구현

**미검증으로 남긴 것**: review 사례의 교차 테넌트 도달성(같은 `(user_id, product_id)` 가 두 테넌트에 존재 가능한지)은 재지 않았다 — `TASK-BE-540` AC-0 이 가른다. 도달 불가면 그 건은 "500 결함" 이 아니라 "정합성 결함" 으로 강등된다.

---

## Failure Scenarios

- **F1 — 이 task 가 조용히 ADR 을 고친다.** ACCEPT 게이트 위반. 제안까지가 범위다.
- **F2 — 재측정 없이 20/13/6 을 그대로 인용.** 이 저장소가 반복해 대가를 치른 실패(모집단 물려받기). BE-535/536 이 방금 분포를 바꿨으므로 특히 위험하다.
- **F3 — 아무것도 거부하지 않는 문구를 제안.** Edge 2 참조. 통과하기 쉬운 문장은 규칙이 아니다.
- **F4 — Edge 3 을 건너뛴다.** 13 건을 "이미 안전" 으로 분류만 하고 실제 거동을 안 보면, BE-536 이 발견한 것과 같은 *제약은 있는데 500 으로 새는* 사례를 놓친다.

---

## Test Requirements

- 문서 task 이므로 자동 테스트 없음.
- 대신 **재측정 산출물**: 20(±) 엔드포인트 전체 목록 + 각각의 분류 + 분류 근거(파일:라인). 표로 남긴다.
- Edge 3 표본 검증 결과(NATURAL-KEY 중 최소 3 건의 실제 중복 거동).

---

## Definition of Done

- [ ] AC-0 인구조사 전수 재측정 (탐지식 known-positive 자기검증 선행)
- [ ] (A)/(B)/(C) 판정 + 근거
- [ ] (A)/(C) 시 제안 문장 확정 형태
- [ ] 제안 문장을 20 개 전부에 대조 (AC-3)
- [x] **NATURAL-KEY 표본 실거동 확인 (Edge 3) — 2026-07-20 선행 수행 완료. 아래 § Edge 3 수행 결과 참조.**
- [ ] 후속 개정 task 형태 기록 (ACCEPT 게이트 절차 포함)
- [ ] 문서만 — 코드·ADR 본문 diff 0

---

## Notes

- **분량**: medium. 파일은 거의 안 바뀌지만 20 엔드포인트 재측정이 실질이다.
- **dependency**: `선행` = `TASK-BE-535`(done) · `TASK-BE-536`(done) — 둘이 분포를 바꿨고 근거 데이터를 남겼다. `후속` = ADR-002 개정 task(ACCEPT 게이트 통과 후).
- **형제**: `TASK-MONO-444` — 같은 "선언 ↔ 진실" 클래스, 반대 방향(문서가 코드에 없는 것을 약속). 둘을 같이 읽으면 축이 보인다.
- **이 task 가 방어하는 실패 모드**: **어떤 구현도 만족하지 못하는 요구는 규칙이 아니라 소음이다.** 문자 그대로 읽으면 13 개의 옳은 엔드포인트가 영구 위반이고, 그래서 아무도 그 문장을 문자 그대로 읽지 않으며, 그 결과 **진짜 위반(NONE 6 건)이 2026-07-20 까지 발견되지 않았다.** 지켜지지 않는 규칙은 지켜지는지 아무도 확인하지 않는 규칙이 된다.
