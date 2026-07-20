# TASK-BE-543 — `payments.order_id`: 선체크는 테넌트 스코프인데 제약은 전역이다 (`user_id` 축은 `TASK-BE-540` 이 이미 판정했다)

**Status:** review

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (측정 1건 + 판정. 원래 3 사이트였으나 2건이 이미 답이 나와 축소됨)

> **2026-07-20 축소.** 이 티켓은 원래 *"신원 키가 전역인가 테넌트별인가"* 를 세 사이트에 대해 묻는 형태로 냈다. `TASK-BE-540`(PR #2780, 머지 `9112d5ff8`)이 그중 **`user_id` 축을 이미 판정**해 두 사이트가 닫혔다. 남은 것은 `order_id` 축 하나다.

---

## 이미 판정된 것 (재측정 금지)

`TASK-BE-540` AC-0(머지 `9112d5ff8`, close `eaed87080`)이 `user_id` 축을 확정했다. 근거는 두 곳에 있다:

1. **`tasks/INDEX.md` 의 BE-540 done 행** — *"AC-0 이 두 사례를 갈랐다: **A 도달가능**(수정), **B 도달불가**(정합성으로 강등)"*. 판정 자체는 여기가 가장 명시적이다.
2. **테스트 Javadoc** `apps/review-service/src/test/java/com/example/review/ReviewDataIntegrityBackstopIntegrationTest.java:47-48` — 그 판정의 *이유*:

> *"the cross-tenant state was also an **impossible production input** (product ids are per-tenant UUIDs and one user id resolves to one tenant — TASK-BE-540 AC-0)"*

**🔴 `tasks/done/TASK-BE-540-...md` 본문에는 결과 섹션이 없다** — close chore 가 결과를 INDEX 행에만 적었다. 즉 이 판정은 **티켓을 열어서는 찾을 수 없다.** 착수 시 그 티켓 본문만 읽고 *"AC-0 미수행"* 으로 오독하지 말 것.

⇒ **두 사이트가 닫혔다:**

| 사이트 | 판정 | 근거 |
|---|---|---|
| review `reviews` (BE-540 사례 B) | **도달 불가** — 같은 `(user_id, product_id)` 가 두 테넌트에 존재할 수 없다 | 위 Javadoc. 게다가 BE-540 이 `V7` 로 인덱스를 `(tenant_id, user_id, product_id)` 로 재정의해 **불일치 자체가 사라졌다** |
| user-service `wishlist_items` | **도달 불가로 추정** — 선체크·제약이 같은 `user_id` 축이므로 위 판정이 그대로 적용된다 | 동일 |

**🔴 `wishlist_items` 는 "추정" 이다.** `user_id` 축이 닫혔으므로 교차 테넌트 충돌은 불가하지만, **`uq_wishlist_items_user_product` 가 `tenant_id` 를 포함하지 않는 정합성 결함 자체는 남아 있다**(`V4__add_tenant_id.sql:6` 이 *"unique constraints are unchanged"* 라고 명시적으로 그 상태를 남겼다). BE-540 § Edge 1 이 말한 대로 **도달 불가가 결함 없음은 아니다** — 우선순위만 낮다. AC-2 로 처리한다.

### 🔴 판정이 자바 주석 한 곳에만 산다

`TASK-BE-540` 은 현재 `tasks/review/` 에 있고 **결과 섹션이 없다**(close chore 미실시). 세 티켓의 범위를 결정한 AC-0 판정이 **테스트 Javadoc 두 줄에만** 기록돼 있다. 그 close chore 가 판정을 티켓에 옮기면 **그 기록이 이 티켓의 인용보다 우선한다**(AC-0 참조).

---

## Goal — 남은 한 사이트

| | 범위 |
|---|---|
| 제약 `payments.order_id` UNIQUE (`V1:3`) | **전역** |
| 선체크 `PaymentRepositoryImpl.findByOrderIdAndTenantId` (`:46`) | **테넌트 스코프** |

선체크가 제약보다 **좁다.** 같은 `order_id` 가 두 테넌트에 존재할 수 있다면, 두 번째 테넌트의 결제 시도는 선체크를 결정적으로 통과한 뒤 전역 제약을 위반한다. `TASK-BE-542` 가 payment-service 에 백스톱을 배선했으므로 결과는 **500 이 아니라 409 `DATA_INTEGRITY_VIOLATION`** 인데, **그건 올바른 응답이 아닐 수 있다** — 남의 테넌트 주문을 참조한 것이라면 404/403 이 맞다.

`ShippingRepositoryImpl:70` 이 *"orderId 는 전역 유일"* 이라고 적지만, **그건 주석 속 주장이고 BE-540 형제 훑기가 이미 같은 이유로 채택을 거부했다.**

---

## Scope

### In Scope

1. **AC-0** — `order_id` 발급이 전역 유일인지 배선에서 확정.
2. **AC-1** — 판정에 따라 payment 선체크/제약 정렬 또는 정합성 기록.
3. **AC-2** — `wishlist_items` 정합성 결함을 별 티켓으로 분리하거나 여기서 함께 처리(판단).

### Out of Scope

- review 사이트 — BE-540 이 `V7` 로 해소 완료.
- `user_id` 축 재측정 — 위 § 이미 판정된 것.
- 무근거 선체크 2건(`sellers.account_id`, `commission_accrual`) — BE-540 AC-4 목록의 별 축. **백스톱 자체가 없는 더 무거운 클래스**이고 별 티켓 대상이다.

---

## Acceptance Criteria

- **AC-0 (gate)** — `order_id` 발급 규칙을 **배선에서** 확정한다. `OrderJpaEntity` 는 할당식 `@Id` 이므로 생성 지점을 따라가 전역 유일성이 **강제되는지** 볼 것. **산문 주장 금지**(`ShippingRepositoryImpl:70`). **선행 확인**: `TASK-BE-540` close chore 가 머지됐다면 그 티켓의 AC-0 결과 기록을 먼저 읽고, 이 티켓의 인용과 다르면 **그쪽이 이긴다.**
- **AC-1** — 도달 가능이면 선체크와 제약의 범위를 맞추고, **409 가 옳은 응답인지도 판정**한다(교차 테넌트 주문 참조라면 404/403 이 맞다). 도달 불가면 정합성 결함으로 기록하고 제약을 `V*__add_tenant_id` 이후 상태와 정합하게 만들지 판단한다.
- **AC-2** — `wishlist_items` 의 `tenant_id` 미포함 유니크 제약을 처리한다(수정 또는 별 티켓 분리 + 근거).
- **AC-3** — 판정이 payment 백스톱 도달성을 바꾸면 `TASK-MONO-450`(함대 표준) 입력으로 기록한다.

---

## Related Specs

- `apps/payment-service/src/main/resources/db/migration/V1__*.sql:3` — 대상 제약
- `apps/payment-service/.../PaymentRepositoryImpl.java:46` — 대상 선체크
- `apps/user-service/src/main/resources/db/migration/V4__add_tenant_id.sql:6` — `wishlist_items` 를 그 상태로 남긴 지점
- `apps/review-service/src/test/.../ReviewDataIntegrityBackstopIntegrationTest.java:47-48` — **`user_id` 축 판정의 현재 유일한 기록**
- `tasks/review/TASK-BE-540-...md` — 출처. close chore 후 결과 섹션이 생기면 그쪽이 정경

## Related Contracts

- `specs/contracts/http/payment-api.md` — AC-1 이 상태 코드를 바꾸면 선행 갱신.

---

## Edge Cases

1. **🔴 409 가 정답이라고 가정하지 말 것** — `TASK-BE-542` 백스톱이 이 경로를 409 로 만들지만, 원인이 "남의 테넌트 주문 참조" 라면 **409 는 존재를 누설**한다(M3 는 교차 테넌트 존재를 숨기라고 요구한다). 백스톱이 있다는 사실이 그 응답이 옳다는 뜻은 아니다.
2. **도달 불가여도 결함은 남는다** — BE-540 § Edge 1.
3. **판정 출처가 옮겨 다닌다** — 지금은 테스트 Javadoc, 곧 BE-540 done 티켓. 착수 시 **최신 위치를 먼저 확인**할 것.

---

## 수행 결과 (2026-07-20)

### 선행 상태 정정 — `TASK-BE-540` close chore 는 머지됐다

이 티켓 본문(§ Notes, § Related Specs, § 판정이 자바 주석 한 곳에만 산다)은 `TASK-BE-540` 을 *"`tasks/review/` 에 있고 결과 섹션이 없다 / close chore 대기"* 로 적었다. **그 close chore 는 `eaed87080` 로 머지됐고 BE-540 은 `tasks/done/` 에 있다.** AC-0·F3 이 정한 우선순위대로 **BE-540 done 기록이 이 티켓의 인용보다 우선**한다.

대조 결과 **`user_id` 축에 대해 두 기록은 모순되지 않는다** — BE-540 은 사례 B 를 도달 불가로 판정했고 이 티켓의 인용(테스트 Javadoc)도 같은 내용이다. F1(이미 잰 것 재측정)도 F3(검증 없이 인용 채택)도 발생하지 않았다.

### AC-0 — `order_id` 는 전역 유일이다 (배선 확인, 산문 아님)

F2 가 금지한 산문 근거(`ShippingRepositoryImpl:70`)를 **채택하지 않고** 생성 지점을 따라갔다:

- `Order.java:83` — `order.orderId = UUID.randomUUID().toString()`. **유일한 생성 지점**이고 테넌트 분할 스킴이 없다.
- `Order.java:118`(`reconstitute`)은 `orderId` 를 **인자로 받는 재수화**이므로 생성 지점이 아니다.
- `OrderJpaEntity:21` 은 할당식 `@Id`(`@GeneratedValue` 없음) — DB 가 값을 만들지 않는다.

⇒ `payments.order_id` 의 **전역 UNIQUE(`V1:3`)는 옳다.** 틀린 것은 선체크의 범위다.

### AC-1 — 선체크 정렬 + **409 는 정답이 아니다**

Edge 1 이 옳았다. `TASK-BE-542` 백스톱이 이 경로를 409 로 만들지만, 원인이 *"남의 테넌트 주문 참조"* 라면 409 는 **교차 테넌트 존재를 확인해 주는 응답**이다(M3 는 은닉을 요구). 채택 = **404 `PAYMENT_NOT_FOUND`** — 이 엔드포인트가 *"그런 결제 없음"* 에 이미 쓰는 코드라 교차 테넌트 건과 진짜 부재가 **호출자에게 구별되지 않는다.**

배선은 두 층이다:

1. **순차** — 전역 선체크 `existsByOrderIdAcrossTenants` 가 insert **전에** 404 로 거부. 쓰기에 도달하지 않으므로 전역 제약을 건드리지 않는다.
2. **동시** — 🔴 **선체크만으로는 가드가 아니다.** 두 요청이 모두 선체크를 통과한 뒤 한쪽이 먼저 커밋하면 패자는 전역 제약에 걸려 **409 를 받는다 — 404 로 숨기려던 존재가 그대로 샌다.** 진짜 중재자는 제약이므로 생성 경로가 위반을 **직접 같은 404 로 번역**한다.

**`saveAndFlush` 가 필수인 이유**: `PaymentJpaEntity:17-19` 는 할당식 `@Id` 라 plain `save()` 의 INSERT 가 커밋까지 지연된다 — catch 와 컨트롤러를 지나친 뒤 터지므로 **catch 가 죽은 코드가 된다**(`TASK-BE-541` 이 카탈로그화한 실패 모드). 포트에 `saveAndFlush` 를 신설해 동기 INSERT 로 잡는다.

**부수 변경 1건**: `incrementPaymentCreated()` 를 저장 **성공 이후**로 옮겼다. 새 거부 경로가 생겼으므로 그대로 두면 거부된 요청이 "생성됨" 으로 계수된다.

### AC-2 — `wishlist_items` 는 **별 티켓으로 분리** → `TASK-BE-546`

여기서 함께 고치지 않은 근거: (1) **도달 불가**(BE-540 `user_id` 축 판정)라 런타임 사고가 없고 정합성 결함이라 우선순위가 낮다 · (2) 이 PR 을 **payment 범위로 유지**해야 리뷰 가능하다 · (3) `user-service` 마이그레이션을 **다른 task 가 동시에 `user-service` 를 편집 중**일 때 얹으면 직렬화 규율을 깬다.

### AC-3 — `TASK-MONO-450` 입력

이 판정은 payment 백스톱의 **도달성을 줄인다.** BE-542 가 배선한 DIVE→409 경로 중 `payments.order_id` 건은 이제 **서비스 계층이 먼저 404 로 번역**하므로 백스톱에 도달하지 않는다. `TASK-MONO-450`(함대 표준 판정)이 *"백스톱이 실제로 무는 경로가 몇 개인가"* 를 다루므로 **payment 의 결정적 경로가 하나 줄었다**는 사실을 입력으로 기록한다.

### 검증한 것과 하지 않은 것

- **로컬 실측**: `payment-service` 단위 **212 tests / 0 failures / 0 errors / 0 skipped**(`--rerun-tasks`, test-report XML 대조). 신규 2건이 XML 에 **실제 실행 기록으로** 존재함을 개별 확인했다 — 레인 초록만으로는 내 테스트가 돌았다는 증거가 못 된다.
- **가드가 무는가**: 선체크를 지우면 `never()).saveAndFlush` 단언이 깨지고, catch 를 지우면 DIVE 가 그대로 올라와 `PaymentNotFoundException` 단언이 깨진다. 두 테스트 모두 물 기회가 있다.
- **🔴 하지 않은 것**: **실제 DB 경합으로 증명하지 않았다.** 동시 케이스는 `saveAndFlush` 가 DIVE 를 던지도록 스텁한 **단위** 테스트이고, 이는 *"제약이 실제로 그 시점에 그 예외를 낸다"* 를 증명하지 않는다 — 그 전제는 `TASK-BE-542` 가 CI Linux 에서 확인한 23505 전달 사실에 기대고 있다. 로컬 Windows Testcontainers 는 FLAKY 라 판정 자격이 없다. **CI Linux 가 권위.**

---

## Failure Scenarios

- **F1 — `user_id` 축을 다시 잰다.** 이미 답이 있다. 모집단 물려받기의 반대 얼굴 — **이미 측정된 것을 재측정하는 낭비**다. 단, F3 주의.
- **F2 — 산문을 증거로 채택.** `ShippingRepositoryImpl:70`.
- **F3 — 인용을 검증 없이 믿는다.** 이 티켓의 `user_id` 축 인용도 **테스트 Javadoc 두 줄**이 출처다. BE-540 close chore 기록과 어긋나면 그쪽이 이긴다(AC-0).

---

## Test Requirements

- 판정이 도달 가능이면 회귀 IT(수정 전 RED 확인 포함). 로컬 Testcontainers FLAKY — **CI Linux 가 권위.**

---

## Definition of Done

- [x] AC-0 `order_id` 전역 유일성 배선 확인 (BE-540 close chore 기록 선행 확인 — 머지됨 `eaed87080`, 인용과 모순 없음)
- [x] AC-1 payment 정렬 + **409 기각 → 404 채택**, 동시 경로까지 배선(`saveAndFlush` + DIVE 번역)
- [x] AC-2 `wishlist_items` 분리 → `TASK-BE-546` (근거 기록)
- [x] AC-3 MONO-450 입력 기록 — payment 백스톱 결정적 경로 1개 감소

---

## Notes

- **분량**: small. 원래 medium 이었으나 2/3 이 이미 답이 나와 축소됐다.
- **dependency**: `선행` = `TASK-BE-540`(impl 머지 `9112d5ff8`; **close chore 도 머지됨 `eaed87080` — `tasks/done/` 에 있다**). `하류` = `TASK-MONO-450` · `TASK-BE-546`(AC-2 분리분).
- **축소 경위**: 이 티켓은 `TASK-BE-542` 의 backstop IT 가 **불가능한 픽스처**였음을 내가 뒤늦게 인정하며(PR #2781 철회) 세 사이트 판정용으로 냈다. 그런데 `TASK-BE-540` 세션이 **같은 문제를 먼저, 더 정확하게** 풀었다 — 그쪽은 픽스처의 인위성을 스스로 찾았고(구매 게이트를 목킹으로 치워야만 성립했다는 점까지), 트리거를 **실제 경합 창**으로 바꿔 23505 전달 증명을 보존했으며, AC-0 으로 `user_id` 축을 확정했다. **내가 세 사이트라고 적은 것 중 둘은 이미 답이 있었다.**
- **이 task 가 방어하는 실패 모드**: **한 번 잰 것을 다시 재지 않는 것과, 남이 잰 것을 검증 없이 믿는 것은 다른 실패다.** 이 티켓은 전자를 피하려고 축소됐지만 F3 로 후자를 막는다 — 인용의 출처가 **자바 주석 두 줄**이라는 사실을 숨기지 않는 이유다. [[feedback_recount_population_dont_inherit_scope]] [[env_test_fixture_impossible_input_proves_nothing]] [[feedback_repo_knows_what_it_does_not_say]]
