# TASK-BE-430 — 주문 생성 멱등화 (중복 주문 방지: FE 키 + BE dedup + 카트비우기 이동)

- **Status**: done
- **Project**: ecommerce-microservices-platform
- **Service**: order-service (BE) + web-store (FE)
- **Type**: bug fix — duplicate orders on checkout re-submit/retry
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus (멀티서비스 + 계약 + saga-adjacent dedup)

## Goal

웹스토어 체크아웃에서 **결제 흐름 주변의 재제출(결제 실패→재시도·뒤로가기·더블클릭·Toss 리다이렉트 왕복)마다 새 주문이 생성**돼 중복/고아 주문이 쌓인다. 실데이터로 확인됨: 같은 user·같은 상품(29000원)에 OrderPlaced 2건(cbfb5cbf=결제없음→CANCELLED, 7ba755b5=결제→SHIPPED) 4분 간격; 32초 간격 PENDING 쌍도 존재. 근본 원인 2가지:
1. `POST /api/orders`(placeOrder)에 **멱등성 없음** — 제출마다 무조건 새 주문.
2. 카트 비우기(`onOrderComplete`)가 **결제 전(주문 생성 직후)에, Toss 전체-리다이렉트와 경쟁(race)**하여 localStorage persist effect 전에 페이지가 떠남 → 카트 잔존 → 재체크아웃 유발. (결제 실패 시에도 카트가 비워져 고아 주문 발생.)

결제 성공/확인 경로는 주문을 만들지 않음(success=orderId로 confirm만). 중복은 전적으로 `CheckoutForm` 제출 반복에서 발생.

## Scope

**In scope** (단일 atomic PR — BE + FE + 계약):

BE (order-service) — 주문 생성 멱등화 (도메인 레벨, Redis 불필요):
1. `db/migration/V10__add_order_idempotency_key.sql` — `orders`에 `idempotency_key VARCHAR(64)` nullable 추가 + **partial unique index** `UNIQUE (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL`(기존 주문=NULL 허용, 동일 user+key 중복 차단).
2. `domain/model/Order.java` — `idempotencyKey` 필드 + `assignIdempotencyKey(String)` (생성 직후 1회 설정; null/blank 무시). **`create()`·`reconstitute()` 시그니처 불변**(ripple 0; 멱등키는 write-only, 재로딩 시 미복원 무해).
3. `infrastructure/persistence/OrderJpaEntity.java` + `OrderJpaMapper.java` — entity `idempotency_key` 컬럼 매핑, `toEntity`에서 `order.getIdempotencyKey()` 영속화(toDomain 복원 불필요).
4. `domain/repository/OrderRepository.java` + `OrderJpaRepository`/`OrderRepositoryImpl` — `Optional<Order> findByUserIdAndIdempotencyKey(String userId, String key)`.
5. `application/service/OrderPlacementService.java` — dedup: key 존재 시 ① `findByUserIdAndIdempotencyKey` → 있으면 기존 orderId 반환(**replay: 생성·이벤트·메트릭 없음**); ② 없으면 create+assignKey+save; save가 `DataIntegrityViolationException`(동시 race) → 재조회로 승자 orderId 반환. OrderPlaced 발행·메트릭은 **실제 생성 시에만**.
6. `application/dto/PlaceOrderCommand.java` — `idempotencyKey` 필드 추가. `presentation/OrderController.java` + `dto/PlaceOrderRequest.toCommand` — `@RequestHeader(value="Idempotency-Key", required=false)` 읽어 전달(**미전송 시 기존 동작=비멱등, 하위호환**).
7. 테스트: `OrderPlacementServiceTest`(replay=동일 orderId·이벤트 미발행, 신규=발행, race=DataIntegrityViolation→승자), `OrderJpaMapperTest`(idempotencyKey 매핑), repo IT(findByUserIdAndIdempotencyKey + unique 위반), `OrderControllerTest`(헤더 전달).

FE (web-store) — 멱등키 발급/전달 + 카트비우기 이동:
8. 체크아웃 멱등키 — 카트 스냅샷 해시 기반 키 발급/재사용(sessionStorage `checkout_idem`: `{cartHash, key}`; 같은 카트=같은 키 재사용, 카트 변경=새 키). 결제 성공(complete 페이지)에서 `removeItem`.
9. `placeOrder`에 `Idempotency-Key` 헤더 주입 — `entities/order/api/order-api.ts` + `@repo/api-client` placeOrder의 per-request 헤더 경로 + **BFF 프록시(`app/api/bff/...`)가 `Idempotency-Key`를 order-service로 forward**.
10. **카트 비우기 이동** — `CheckoutForm.handleSubmit`의 `onOrderComplete()`(결제 전 비우기) 제거 → `checkout/complete` 페이지(결제 confirm 성공 후)에서 비우기. 결제 실패 시 카트 보존(같은 키로 재시도=중복 없음).
11. FE 테스트: 멱등키 발급/재사용(같은 카트→동일 키, 변경→새 키), 결제 성공 시 카트 비우기, CheckoutForm이 결제 전 비우지 않음.

계약:
12. `specs/contracts/http/order-api.md` — `POST /api/orders`에 `Idempotency-Key` 헤더(optional, 권장; 동일 키 재요청 시 동일 orderId 반환, 멱등) 문서화.

**Out of scope**:
- 버려진 PENDING 주문 타임아웃 자동 취소(별도 task 후보 — 멱등화와 독립).
- 공유 `IdempotencyKeyFilter`(libs/java-web-servlet) 채택 — order-service는 Redis 미보유, 응답캐시 필터보다 도메인 unique 제약이 주문 애그리거트에 더 robust(lib posture도 domain-layer unique를 backstop으로 명시). 의도적 도메인-레벨 선택.
- `/api/admin/orders` 등 다른 엔드포인트 멱등화.

## Acceptance Criteria

- **AC-1 — replay 동일 orderId.** 같은 `Idempotency-Key`+같은 user 로 `POST /api/orders` 재요청 시 **새 주문·OrderPlaced 발행 없이** 최초 orderId 를 반환(201). DB orders row 1건 유지.
- **AC-2 — 신규 키=신규 주문.** 다른 키(또는 카트 변경으로 바뀐 키)는 정상적으로 새 주문 생성.
- **AC-3 — 하위호환.** `Idempotency-Key` 미전송 시 기존 동작(매 요청 새 주문) 유지 — 컬럼 NULL, partial unique 미적용.
- **AC-4 — race 안전.** 동시 동일 키 요청 시 partial unique index 가 중복 row 를 차단(둘 중 하나만 생성); 패배 요청은 승자 orderId 반환 또는 명확한 충돌(중복 주문 0).
- **AC-5 — 카트 race 제거.** 카트 비우기가 **결제 성공 시점**으로 이동 → 결제 전 race 소멸, 결제 실패 시 카트 보존.
- **AC-6 — FE 키 안정성.** 같은 카트의 재제출(결제 실패→재시도)은 **동일 키 재사용**(sessionStorage) → BE dedup → 동일 주문; 카트 변경 시 새 키; 결제 완주 후 키 리셋.
- **AC-7 — 헤더 전파.** 브라우저→BFF→order-service 까지 `Idempotency-Key` 가 전달됨(BFF forward 확인).
- **AC-8 — 게이트.** order-service `:test` GREEN(신규/수정 테스트). web-store `pnpm lint`+`tsc` GREEN; vitest 는 로컬 Node24×vitest4 미기동 → CI(Node20) 권위. Testcontainers IT 로컬 비활성 → CI 권위.

## Related Specs
- `specs/use-cases/cart-and-order.md` UC-1 — 주문 생성(PENDING)→결제→CONFIRMED saga. 멱등화는 UC-1 재시도 안전성 보강.
- `projects/ecommerce-microservices-platform/PROJECT.md` — `transactional` trait("Saga + idempotency 필수").
- `docs/adr/ADR-MONO-038` — 공유 idempotency 필터(채택 안 함 근거=Scope Out).

## Related Contracts
- `specs/contracts/http/order-api.md` — `POST /api/orders`(Idempotency-Key 헤더 추가).
- `specs/contracts/events/order-events.md` — OrderPlaced(replay 시 **미발행**; 계약 payload 불변).

## Edge Cases
- 멱등키 있으나 기존 주문이 CANCELLED 상태 — replay 가 CANCELLED 주문 orderId 반환(같은 작업의 결과). FE 는 보통 새 카트=새 키라 발생 드묾; 발생 시 사용자는 새 체크아웃(새 키)로 진행.
- 카트 변경 후 같은 세션 재체크아웃 — cartHash 변경 → 새 키 → 새 주문(정확). 기존 미완 주문은 고아(out-of-scope 타임아웃이 정리 대상).
- BFF 가 `Idempotency-Key` 를 strip → dedup 무력화 → forward 화이트리스트에 명시 추가(AC-7 테스트로 가드).
- DataIntegrityViolation 후 동일 @Transactional 내 재조회 불가(poisoned tx) → 재조회는 신규 읽기 경로/예외 매핑으로; 동시-race 는 드물고 unique 제약이 중복 0 을 보장(핵심 가드).

## Failure Scenarios
- replay 분기에서 이벤트를 또 발행하면 중복 결제 레코드 → AC-1 테스트(이벤트 미발행 단언)로 가드.
- 카트 비우기를 결제 전/후 양쪽에서 하면 이중 — complete 페이지로만 이동(AC-5), CheckoutForm 에서 제거 확인.
- partial unique index 누락 시 race 중복 잔존 → repo IT(unique 위반)로 가드.
- 멱등키 미전파(BFF strip)면 멱등화가 조용히 무력 → AC-7 + 배포 후 라이브 중복-재현 검증.
