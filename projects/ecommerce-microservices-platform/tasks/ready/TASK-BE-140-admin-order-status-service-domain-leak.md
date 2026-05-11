# Task ID

TASK-BE-140

# Title

order-service `AdminOrderController` 의 domain 모델 직접 import 제거 — `AdminOrderStatusService.changeStatus()` 가 raw `Order` 대신 application DTO 반환

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`presentation/AdminOrderController` 가 `com.example.order.domain.model.Order` 와 `com.example.order.domain.model.OrderStatus` 를 직접 import 하는 layer drift 를 해소한다.

[`specs/services/order-service/architecture.md`](../../specs/services/order-service/architecture.md) § Forbidden Dependencies 의 "controllers must not bypass application services" + § Boundary Rules ("application layer orchestrates use-cases and transaction boundaries") 위반 1건을 closure.

원인: `AdminOrderStatusService.changeStatus(String orderId, OrderStatus targetStatus)` 가 raw `Order` aggregate 를 반환 → controller 가 `Order.getOrderId()` + `Order.getStatus().name()` 으로 응답 조립. 이 의존이 controller→domain import 를 강제한다.

해결: service signature 가 String 을 받고 새 application DTO (`AdminOrderStatusChangeResult`) 를 반환하도록 변경. controller 는 도메인 타입을 전혀 보지 않게 된다.

본 task 는 ecommerce/order-service sweep dry-run (2026-05-11) 의 finding A1 single-PR closure 이며, B/C 카테고리 polish 는 본 task scope 외 (별도 평가에서 DEFER 결정 — 메모리 `project_refactor_sweep_status.md` ecommerce/order-service 섹션 참조).

---

# Scope

## In Scope

- 새 application DTO record 추가: `application/dto/AdminOrderStatusChangeResult` (`String orderId`, `String status`)
- `AdminOrderStatusService.changeStatus(String orderId, OrderStatus targetStatus)` signature 변경 → `changeStatus(String orderId, String targetStatusRaw)` + DTO 반환. enum 파싱 + `InvalidOrderStatusException` 던지기를 service 내부로 이동
- `AdminOrderController.changeStatus` endpoint 가 `request.status()` 를 String 그대로 service 에 위임하고 DTO → `AdminOrderStatusChangeResponse` 매핑
- `AdminOrderController` 의 line 7 (`import com.example.order.domain.model.Order`) + line 8 (`import com.example.order.domain.model.OrderStatus`) 제거
- 기존 unit/controller-slice 테스트 signature drift 수정 (`AdminOrderStatusServiceTest`, `AdminOrderControllerTest` 추정)
- 새 path 에 대한 service 단위 테스트 1건 (invalid raw status → `InvalidOrderStatusException`) 추가

## Out of Scope

- `AdminOrderController.getOrders` (line 41) 의 `orderQueryService.getAllOrders(OrderControllerUtils.parseStatus(status), pageQuery)` chain. `parseStatus` 는 presentation/OrderControllerUtils 안에 머무름. controller 자체에 enum 변수가 노출되지 않으므로 import 도 발생하지 않음.
- `OrderControllerUtils` 위치 이동 (presentation → application). 별도 평가.
- B 카테고리 simplification (Payment\*Service 중복, `*Summary::from firstItemName` 중복, consumer guard 중복, `OrderRepositoryImpl.save vs saveAll` branch). dry-run report 참조.
- C 카테고리 cleanup (Korean exception message, unused param). 다음 feature PR 에 묻혀 처리.
- 다른 controller 의 동일 패턴 검사 (BuyerOrderController 등). 별도 평가.
- 기존 HTTP contract 변경 (`AdminOrderStatusChangeResponse` 응답 JSON 동일 유지 — `orderId` + `status` 필드).

---

# Acceptance Criteria

- [ ] `apps/order-service/src/main/java/com/example/order/presentation/AdminOrderController.java` 에 `com.example.order.domain.**` import 라인 0개 (grep 검증).
- [ ] `AdminOrderStatusService.changeStatus(...)` 반환 타입이 `application/dto/AdminOrderStatusChangeResult` (record).
- [ ] invalid `status` 본문 (지원되지 않는 enum 값) 시 기존 동일 `InvalidOrderStatusException` 발생 + HTTP 400 매핑 유지 (presentation/ExceptionHandler 변경 없음).
- [ ] HTTP 응답 JSON shape (`{"orderId": "...", "status": "..."}`) 변경 없음 — 기존 contract 무 변경.
- [ ] 기존 `AdminOrderControllerTest` + `AdminOrderStatusServiceTest` 가 새 signature 로 PASS (assertion 데이터 동일).
- [ ] `AdminOrderStatusServiceTest` 에 raw status 가 잘못된 enum 일 때 `InvalidOrderStatusException` 던지는 case 1건 신규 추가.
- [ ] `./gradlew :order-service:test` PASS, `./gradlew :order-service:check` 회귀 없음.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- [`specs/services/order-service/architecture.md`](../../specs/services/order-service/architecture.md) — Forbidden Dependencies + Boundary Rules
- [`platform/service-types/rest-api.md`](../../../../platform/service-types/rest-api.md)
- `rules/common.md`
- `rules/domains/ecommerce.md` (있으면)
- `rules/traits/transactional.md` (있으면)

# Related Skills

- `.claude/skills/backend/implement-task` (구현)
- `.claude/skills/backend/refactoring` (참고 — 본 task 는 sweep 가 아닌 targeted spec-drift fix)

---

# Related Contracts

- HTTP 응답 shape 변경 없음 — 기존 `AdminOrderStatusChangeResponse` 그대로 유지. `specs/contracts/http/` 변경 없음.

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- [`specs/services/order-service/architecture.md`](../../specs/services/order-service/architecture.md) — DDD 4-layer (presentation / application / domain / infrastructure), 본 task 는 presentation→application 경계 정상화

---

# Implementation Notes

- enum 파싱 책임 이동만 한다 — `OrderStatus.valueOf` + `IllegalArgumentException → InvalidOrderStatusException` 매핑은 `OrderControllerUtils.parseStatus()` 와 동일한 로직을 `AdminOrderStatusService` 내부 private helper 로 복제하거나, `parseStatus` 를 application 패키지로 이동 후 양쪽이 쓰게 한다. 둘 중 단순한 길로 — service 내부 복제 권장 (out-of-scope 인 getOrders 흐름 그대로 보존, 본 task 의 cross-cutting 변경 최소화).
- 새 `AdminOrderStatusChangeResult` 는 record. lombok 사용 안 함.
- `InvalidOrderStatusException` 의 패키지 (`presentation/exception/`) 는 그대로 둔다 — getOrders 의 `parseStatus` 흐름이 여전히 사용. cross-cutting 영향 0.
- HTTP layer (controller → presentation/dto/AdminOrderStatusChangeResponse) 매핑은 controller 안 1-liner 로 유지.

---

# Edge Cases

- `request.status()` 가 `null` 또는 빈 문자열 — 기존 `OrderControllerUtils.parseStatus()` 는 null 반환 후 `InvalidOrderStatusException` 던짐. 새 service 흐름도 동일하게 던져야 한다 (controller line 63-65 의 null-check 와 동일).
- 지원되지 않는 enum 값 (`"FOO"`) — `OrderStatus.valueOf` 가 `IllegalArgumentException` 던짐, 이를 `InvalidOrderStatusException` 으로 wrapping.
- 지원되지 않는 transition (`PENDING → DELIVERED` 등) — service 의 switch default case 가 `IllegalArgumentException("Unsupported target status: …")` 던지는 기존 동작 그대로 유지.
- 동시 status 변경 — 본 task scope 외, 기존 동작 그대로.

---

# Failure Scenarios

- invalid raw status 본문 → `InvalidOrderStatusException` → HTTP 400 매핑 유지.
- order not found → 기존 `OrderNotFoundException` 동작 그대로.
- DB 예외 → 기존 transactional 동작 그대로.

---

# Test Requirements

- unit: `AdminOrderStatusServiceTest` 가 새 signature 로 PASS, invalid raw status case 1 신규.
- controller-slice: `AdminOrderControllerTest` 가 새 service signature 로 PASS, 응답 JSON shape 동일.
- IT: 신규 추가 없음. Testcontainers npipe blocker (메모리 `project_testcontainers_docker_desktop_blocker`) 영향 없음.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing (`./gradlew :order-service:check`)
- [ ] Controller 에서 `com.example.order.domain.**` import 0 (grep)
- [ ] HTTP contract 무 변경 (응답 JSON shape 동일)
- [ ] Specs 변경 없음 (architecture.md 의 § Forbidden Dependencies 위반 해소만)
- [ ] Ready for review
