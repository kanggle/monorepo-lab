# Task ID

TASK-BE-083

# Title

order-service 이벤트 envelope snake_case 적용 — auth-events, product-events, user-events 일관성 검증

# Status

done

# Owner

backend

# Task Tags

- code
- event
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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

TASK-BE-071에서 payment-service 이벤트 envelope의 snake_case 변환을 완료했으나, order-service의 이벤트(OrderPlacedEvent, OrderCancelledEvent 등)에는 `@JsonProperty` 애노테이션이 없어 camelCase로 직렬화된다. event-driven-policy 스펙에 따라 모든 이벤트 envelope 필드를 snake_case로 통일한다. auth-service, product-service, user-service 이벤트도 동일 이슈가 있으면 함께 수정한다.

---

# Scope

## In Scope

- order-service: OrderPlacedEvent, OrderCancelledEvent 등 모든 이벤트 클래스에 `@JsonProperty` snake_case 적용
- auth-service: 이벤트 envelope 필드 snake_case 확인 및 수정
- product-service: 이벤트 envelope 필드 snake_case 확인 및 수정
- user-service: 이벤트 envelope 필드 snake_case 확인 및 수정
- 각 서비스의 이벤트 소비자(consumer) 측 역직렬화 호환성 확인

## Out of Scope

- payment-service (TASK-BE-071에서 완료)
- 이벤트 payload 필드 변경 (envelope 필드만 대상)
- 새로운 이벤트 타입 추가

---

# Acceptance Criteria

- [ ] order-service 모든 이벤트의 envelope 필드가 snake_case로 직렬화된다 (event_id, event_type, occurred_at)
- [ ] auth-service 모든 이벤트의 envelope 필드가 snake_case로 직렬화된다
- [ ] product-service 모든 이벤트의 envelope 필드가 snake_case로 직렬화된다
- [ ] user-service 모든 이벤트의 envelope 필드가 snake_case로 직렬화된다
- [ ] 각 서비스의 이벤트 소비자가 snake_case envelope을 정상 역직렬화한다
- [ ] 모든 기존 테스트가 통과한다

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/contracts/events/order-events.md`
- `specs/contracts/events/auth-events.md`
- `specs/contracts/events/product-events.md`
- `specs/contracts/events/user-events.md`

# Related Skills

- `.claude/skills/messaging/event-implementation.md`

---

# Related Contracts

- `specs/contracts/events/order-events.md`
- `specs/contracts/events/auth-events.md`
- `specs/contracts/events/product-events.md`
- `specs/contracts/events/user-events.md`

---

# Target Service

- `order-service`
- `auth-service`
- `product-service`
- `user-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`
- `specs/platform/event-driven-policy.md`

---

# Implementation Notes

- TASK-BE-071에서 적용한 payment-service 패턴을 동일하게 따른다: `@JsonProperty("event_id")`, `@JsonProperty("event_type")`, `@JsonProperty("occurred_at")`
- 또는 클래스 레벨 `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` 적용을 검토한다 (일괄 적용 시 payload 필드에 영향이 없는지 확인 필요)
- 이벤트 소비자 측에서 기존 camelCase 메시지와의 하위 호환성이 필요한 경우, `@JsonAlias`를 함께 적용한다

---

# Edge Cases

- 기존 Kafka 토픽에 camelCase 메시지가 남아 있는 경우 → consumer가 양쪽 모두 역직렬화 가능해야 함
- envelope 필드와 payload 필드의 네이밍 전략이 혼재될 수 있음 → envelope만 대상으로 제한
- record 타입 이벤트 클래스의 경우 @JsonProperty 위치가 다름 (생성자 파라미터)

---

# Failure Scenarios

- snake_case 적용 후 consumer가 역직렬화 실패 → @JsonAlias로 하위 호환 보장
- @JsonNaming 일괄 적용 시 payload 필드도 snake_case로 변환되어 consumer 파싱 실패
- 이벤트 직렬화 테스트가 없어 변경 후 문제를 감지하지 못함

---

# Test Requirements

- 각 이벤트 클래스의 직렬화 단위 테스트 (snake_case 필드명 검증)
- 이벤트 소비자 역직렬화 테스트 (snake_case → 도메인 객체)
- 하위 호환성 테스트 (camelCase 메시지도 역직렬화 가능)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
