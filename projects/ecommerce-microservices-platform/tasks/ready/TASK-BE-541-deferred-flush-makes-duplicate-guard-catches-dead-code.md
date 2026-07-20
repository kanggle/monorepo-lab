# TASK-BE-541 — 중복 방어 `catch (DataIntegrityViolationException)` 이 발화할 수 없다: plain `save()` 는 커밋 시점에 flush 된다

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (트랜잭션 경계·flush 타이밍 판단. 수정 자체는 작지만 어디가 죽었는지 가리는 게 실질)

> `TASK-BE-538` **Edge 3** 수행 중 발견. 저장소가 신뢰하는 중복 방어 관용구가 **여러 곳에서 실행 불가능한 코드**이고, **그 사실을 초록 유닛 테스트가 가리고 있다.**

---

## Goal

이 저장소의 중복 방어 관용구는 이렇게 생겼다:

```java
try {
    repository.save(entity);          // ← plain save
} catch (DataIntegrityViolationException e) {
    throw new SomethingAlreadyExistsException(...);   // → 409
}
```

**이 catch 는 발화할 수 없다.** JPA 의 plain `save()` 는 INSERT 를 영속성 컨텍스트에 큐잉만 하고, 실제 실행은 트랜잭션 **커밋 시점의 flush** 로 미뤄진다. 그 flush 는 `@Transactional` 메서드가 **반환한 뒤** 프록시에서 일어나므로, `try` 블록은 이미 빠져나온 뒤다. 제약 위반은 catch 를 지나쳐 트랜잭션 프록시 밖으로 나간다.

특히 **할당식 ID**(`@Id` 를 애플리케이션이 채우는 엔티티 — `@GeneratedValue(IDENTITY)` 아님)에서는 Hibernate 가 INSERT 를 앞당길 이유가 없어 지연이 확실하다. 이 저장소의 dedupe 엔티티는 대부분 할당식 ID(`eventId`, `deliveryId`, `orderId`)다.

`TASK-BE-535`/`536` 이 만든 신규 경로들은 이걸 알고 **`saveAndFlush`** 를 쓰며 이유를 주석에 남겼다(`ProductCreateRequestRepositoryImpl.java:27-31`, `RefundRequestRepositoryImpl`, `StockAdjustmentRequestRepositoryImpl`, `SettlementPeriodRepositoryImpl`, `CouponIssueRequestRepositoryImpl`). **문제는 그 이전부터 있던 경로들이다.**

### 확인된 죽은 catch

| 위치 | 트랜잭션 | 도달 경로 | 실제 결과 |
|---|---|---|---|
| `order-service` `OrderPlacementService.java:58-68` | `@Transactional` (`:32`), `OrderRepositoryImpl.java:39` 가 plain `save` | HTTP 주문 생성 | 동시 동일 `Idempotency-Key` → 의도한 409 `DUPLICATE_ORDER_REQUEST` 대신 **500** |
| `shipping-service` `JpaWebhookDeliveryStore.java:36-41` | 호출자 트랜잭션에 합류 | HTTP `CarrierWebhookController` | 동시 중복 웹훅 → **500** |
| `shipping-service` `EventDeduplicationChecker.java:31-36` | `@Transactional(propagation = MANDATORY)` (`:19`) — 자기 커밋 없음 | Kafka 컨슈머 | 동시 중복 → 재시도/DLQ |

`JpaWebhookDeliveryStore` 의 클래스 Javadoc(`:15-17`)은 의도를 정확히 적어 두었다 — *"an insert whose PK collision (a concurrent duplicate) is caught and reported as already-seen. **Runs in the caller's transaction** so a failed webhook processing rolls back the registration too."* — **두 번째 문장이 첫 번째 문장이 불가능한 이유다.**

### 별건이지만 같은 뿌리 — 방어가 아예 없는 곳

`product-service` `RegisterProductService.java:137-144` 의 catch 는 `productCreateRequestRepository.insert(...)`(멱등키 청구, `saveAndFlush` 라 살아 있음)**만** 감싼다. 그 뒤 `:146` 의 `productRepository.save(product)` 는 plain 이고 감싸이지도 않았다. 그리고 `:116-121` 은 요청의 variants 를 `optionName` 중복 검사 없이 그대로 만든다. 결과: **`POST /api/admin/products` 에 같은 `optionName` variant 두 개를 담아 보내면 `uq_product_variants_option` 위반이 커밋 시점에 터져 500.** 같은 제약을 쓰는 `POST /{id}/variants` 는 `saveAndFlush` + catch 로 **올바르게 409** 를 낸다(`VariantManagementService.java:53-57`) — **한 제약에 두 개의 쓰기 경로, 하나만 방어됨.**

### 초록 테스트가 이걸 가려 왔다

- `OrderPlacementServiceTest.java:188-190` — `given(orderRepository.save(any())).willThrow(new DataIntegrityViolationException(...))`
- `EventDeduplicationCheckerUnitTest.java:53` — `when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"))`

**둘 다 실제 리포지터리가 결코 하지 않는 일을 모킹한다.** 진짜 `save()` 는 던지지 않는다 — flush 가 던진다. 테스트는 **불가능한 전제 위에서** catch 가 동작함을 단언하고 초록이 된다. 대조군: `VariantManagementServiceTest.java:127` 은 `saveAndFlush` 를 스텁하므로 전제가 실재한다.

---

## Scope

### In Scope

1. **AC-0 전수 훑기** — 중복 방어 catch 관용구를 쓰는 **모든** 지점을 열거하고 각각 살았는지 죽었는지 판정.
2. 죽은 곳을 살린다: `saveAndFlush` / 명시적 `flush` / `REQUIRES_NEW` 중 그 지점에 맞는 것.
3. `RegisterProductService` 의 방어 부재 수정.
4. 불가능한 전제에 기대는 테스트를 실재 전제로 교체.

### Out of Scope

- 선체크 범위 ≠ 제약 범위 문제는 `TASK-BE-540`.
- `uq_notifications_event_id` 모양 문제는 `TASK-BE-539`.
- `DataIntegrityViolationException` → 409 전역 핸들러 배선은 `TASK-BE-542`. **둘은 상호보완**: 542 는 "500 이 안 나게" 하는 백스톱이고, 이 task 는 "**의도한 도메인 코드가** 나게" 하는 수정이다. 542 만 하면 `DUPLICATE_ORDER_REQUEST` 대신 일반 `DATA_INTEGRITY_VIOLATION` 이 나간다.

---

## Acceptance Criteria

- **AC-0 (gate — 전수 훑기)** — `catch (DataIntegrityViolationException` 을 ecommerce `apps/**/src/main` 전체에서 열거하고, 각 지점마다 **감싸는 저장 호출이 실제로 flush 를 강제하는지** 판정해 표로 남긴다. 최소한 아래는 모두 검사 대상이다(본문의 3건 외):
  `order-service EventDeduplicationChecker` · `product-service ReservationEventDedupe` · `product-service WmsReconciliationDedupe` · `settlement-service ProcessedEventStoreImpl`.
  **판정 기준은 `save` 라는 이름이 아니라 flush 타이밍이다** — `REQUIRES_NEW` 로 내부 트랜잭션이 호출자의 try 안에서 커밋되면 plain `save` 라도 살아 있다(`PaymentRefundStrandedRecorder` 가 그 예). **저장 호출을 리포지터리 impl 까지 따라가 실제 `jpaRepository` 호출을 확인할 것.**
- **AC-1** — 죽은 것으로 판정된 지점이 전부 살아난다. HTTP 도달 지점은 **의도한 도메인 에러 코드**(409)를 내고 500 이 아니다.
- **AC-2 (guard — 수정 전 RED)** — 각 수정 지점마다 **동시 중복을 실제로 일으키는** 테스트를 수정 전 코드에 대고 먼저 돌려 RED 를 확인한다. **모킹으로 `save()` 가 던지게 만드는 방식 금지** — 그게 이 결함을 가려 온 바로 그 수법이다. 실제 제약을 때리는 IT 여야 한다.
- **AC-3** — `POST /api/admin/products` 에 동일 `optionName` variant 2개를 담은 요청이 **409**(`DUPLICATE_VARIANT_OPTION`)를 낸다. 수정 전 500 확인 선행.
- **AC-4** — `OrderPlacementServiceTest.java:188-190` 과 `EventDeduplicationCheckerUnitTest.java:53` 의 불가능 전제를 제거한다. 유닛 레벨에서 실재 전제를 만들 수 없으면 **그 단언은 IT 로 옮기고 유닛 테스트에서는 삭제**한다 — 거짓 확신을 주는 초록 테스트는 없느니만 못하다.
- **AC-5** — AC-0 표에서 **살아 있다고 판정한 지점**도 근거(어떤 메커니즘으로 flush 가 try 안에서 일어나는가)를 한 줄씩 적는다. "살아 있음" 도 주장이다.

---

## Related Specs

- `platform/testing-strategy.md` § A test that bypasses the enforcement layer — AC-2/AC-4 의 근거
- `specs/services/order-service/architecture.md` · `specs/services/shipping-service/architecture.md` · `specs/services/product-service/architecture.md`
- `tasks/done/TASK-BE-535-money-path-duplicate-request-guards.md` · `tasks/done/TASK-BE-536-inventory-coupon-path-duplicate-request-guards.md` — `saveAndFlush` 를 쓴 이유가 주석으로 남아 있는 참조 구현
- `tasks/ready/TASK-BE-538-adr-002-d3-wording-adjudication.md` § Edge 3 — 출처

## Related Contracts

- `specs/contracts/http/order-api.md` — 동시 중복 키의 결과가 계약에 명시돼 있다면 현재 문서가 **실제 거동(500)이 아니라 의도(409)를 적고 있을 가능성**이 높다. 코드를 의도에 맞추므로 계약 변경은 없을 것으로 보이나 **대조는 필수**.
- 캐리어 웹훅 계약 — 동시 중복 전달의 응답 코드 확인.

---

## Edge Cases

1. **🔴 `saveAndFlush` 로 바꾸면 다른 게 깨질 수 있다** — flush 를 앞당기면 같은 트랜잭션의 이후 작업이 보는 상태가 달라지고, 배치 INSERT 최적화가 사라진다. **성능이 아니라 정합성 관점에서** 각 지점의 이후 로직을 확인할 것.
2. **`MANDATORY` 전파는 `saveAndFlush` 만으로 부족할 수 있다** — `EventDeduplicationChecker` 는 호출자 트랜잭션에 합류한다. flush 를 강제하면 catch 는 살아나지만, **호출자 트랜잭션은 이미 오염된 상태**일 수 있다(Hibernate 세션은 제약 위반 후 신뢰 불가). 이 지점은 `REQUIRES_NEW` 가 옳은 답일 수 있다 — `PaymentRefundStrandedRecorder` 선례 참조.
3. **한 제약에 쓰기 경로가 둘 이상** — `uq_product_variants_option` 이 그랬다(`POST /{id}/variants` 는 방어됨, `POST /admin/products` 는 아님). AC-0 훑기는 **catch 지점이 아니라 제약 기준으로도** 한 번 봐야 이 형태를 놓치지 않는다.
4. **로컬 Windows 에서 동시성 IT 는 신뢰 불가** — Testcontainers FLAKY. **CI Linux 가 권위.** 로컬 초록을 근거로 삼지 말 것.

---

## Failure Scenarios

- **F1 — `save` → `saveAndFlush` 기계적 치환.** 트랜잭션 전파와 이후 로직을 안 보고 바꾸면 Edge 1/2 를 밟는다. **지점마다 판단이 필요하다.**
- **F2 — AC-2 를 모킹으로 만족시킨다.** 이 결함이 존재해 온 유일한 이유가 그것이다. 같은 방식으로 "고쳤다" 고 하면 아무것도 바뀌지 않는다.
- **F3 — `TASK-BE-542` 만 하고 이 티켓을 닫는다.** 500 은 사라지지만 모든 중복이 일반 `DATA_INTEGRITY_VIOLATION` 으로 뭉개진다 — 클라이언트가 "주문 중복" 과 "FK 위반" 을 구분하지 못한다.
- **F4 — AC-0 을 본문의 3건으로 끝낸다.** 본문 목록은 **표본이지 모집단이 아니다.** 관용구가 6곳 이상에 있다는 신호가 이미 있다.

---

## Test Requirements

- **IT (Testcontainers, CI Linux 권위)**: 각 수정 지점의 실제 동시 중복 재현. 수정 전 RED → 수정 후 GREEN.
- **유닛**: 불가능 전제 제거(AC-4). 남는 유닛 테스트는 실재하는 전제만 스텁한다.
- 동시성 IT 는 flaky 하기 쉬우므로 **배리어 기반**으로 쓰고, 실패 시 재실행이 아니라 원인을 본다.

---

## Definition of Done

- [ ] AC-0 전수 훑기 표 (죽음/삶 + 근거, 제약 기준 교차 확인 포함)
- [ ] 죽은 catch 전부 수정 (지점별로 적합한 메커니즘 선택)
- [ ] `RegisterProductService` 중복 `optionName` 방어 (AC-3)
- [ ] 각 수정마다 수정 전 RED 확인 (AC-2, 모킹 금지)
- [ ] 불가능 전제 테스트 2건 이상 제거/이관 (AC-4)
- [ ] CI Linux GREEN
- [ ] 계약 문서 대조

---

## Notes

- **분량**: medium~large. 지점 수가 많고 지점마다 트랜잭션 판단이 다르다.
- **dependency**: `선행` = 없음. 다만 **`TASK-BE-542` 를 먼저 하면 그 사이 500 이 409 로 덮여** 이 티켓의 수정 전 RED 확인이 어려워진다 — **이 task 를 먼저 하거나, 542 이후라면 RED 기준을 "잘못된 에러 코드" 로 바꿔 잡을 것.**
- `형제` = `TASK-BE-539` · `TASK-BE-540` · `TASK-BE-542` (모두 `TASK-BE-538` Edge 3 산물).
- **이 task 가 방어하는 실패 모드**: **테스트가 초록인 이유가 코드가 옳아서가 아니라 전제가 불가능해서일 수 있다.** 이 관용구는 저장소 전반이 신뢰해 온 것이고, 신뢰의 근거는 초록 테스트였으며, 그 테스트는 실제 리포지터리가 결코 하지 않는 일을 모킹하고 있었다. [[env_test_fixture_impossible_input_proves_nothing]] [[feedback_guard_predicate_wrong_verify_the_artifact]] [[project_guard_reachability_not_just_bite]]
