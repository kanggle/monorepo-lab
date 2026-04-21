# Task ID

TASK-BE-081-FIX2-missing-specs-and-kafka-config

# Title

배송 서비스 누락 스펙 파일 생성 및 KafkaConsumerConfig 정책 준수 수정

# Status

ready

# Owner

backend

# Task Tags

- spec
- contract
- code

---

# Goal

TASK-BE-081-FIX 리뷰에서 미완료로 확인된 항목을 수정한다.

에러코드 등록과 event-driven-policy 테이블 추가는 완료되었으나, 아래 5개 항목이 미완료 상태다:

1. `specs/services/shipping-service/architecture.md` 신규 생성 (파일 미존재)
2. `specs/contracts/http/shipping-api.md` 신규 생성 (파일 미존재)
3. `specs/contracts/events/shipping-events.md` 신규 생성 (파일 미존재)
4. `KafkaConsumerConfig.java` DLQ suffix `.DLT` → `.dlq` 수정 (정책 불일치)
5. `KafkaConsumerConfig.java` 재시도 `FixedBackOff(1000L, 2)` → `ExponentialBackOff` + max 3회 수정

---

# Scope

## In Scope

- `specs/services/shipping-service/architecture.md` 신규 생성
  - 기존 서비스들(order-service, payment-service)의 architecture.md 형식 준수
  - 레이어 구조: interfaces(rest), application, domain, infrastructure
  - 아키텍처 스타일: DDD-style (shipping-service는 도메인 규칙 포함)
- `specs/contracts/http/shipping-api.md` 신규 생성
  - 구현된 3개 엔드포인트 계약 포함:
    - `GET /api/shippings/orders/{orderId}` (X-User-Id 헤더)
    - `PUT /api/shippings/{shippingId}/status` (X-User-Role: ADMIN 헤더)
    - `GET /api/shippings` (X-User-Role: ADMIN 헤더, 관리자 목록 조회)
  - 기존 order-api.md, payment-api.md 형식 준수
- `specs/contracts/events/shipping-events.md` 신규 생성
  - ShippingStatusChanged 이벤트 계약 포함
  - payload 필드: shippingId, orderId, userId, previousStatus, newStatus, trackingNumber, carrier, changedAt
  - 기존 order-events.md 형식 준수
- `apps/shipping-service/src/main/java/com/example/shipping/infrastructure/config/KafkaConsumerConfig.java`
  - DLQ suffix: `record.topic() + ".DLT"` → `record.topic() + ".dlq"`
  - 재시도: `FixedBackOff(1000L, 2)` → `ExponentialBackOffWithMaxRetries` 또는 동등한 지수 백오프 (max 3회, 기본 1s)
  - `import org.springframework.util.backoff.FixedBackOff` → ExponentialBackOff 관련 import로 교체

## Out of Scope

- shipping-service 비즈니스 로직 변경
- 데이터베이스 스키마 변경
- 새 API 추가
- 에러코드 추가 (이미 완료)
- event-driven-policy.md 수정 (이미 완료)

---

# Acceptance Criteria

- [ ] `specs/services/shipping-service/architecture.md` 파일 존재하며 레이어 구조 명시
- [ ] `specs/contracts/http/shipping-api.md` 파일 존재하며 구현된 3개 엔드포인트 계약 포함
  - `GET /api/shippings/orders/{orderId}` (X-User-Id 헤더, ShippingResponse 형식)
  - `PUT /api/shippings/{shippingId}/status` (X-User-Role: ADMIN, UpdateShippingStatusResponse 형식)
  - `GET /api/shippings` (X-User-Role: ADMIN, ShippingListResponse 형식)
- [ ] `specs/contracts/events/shipping-events.md` 파일 존재하며 ShippingStatusChanged 이벤트 계약 포함
- [ ] `KafkaConsumerConfig.java` DLQ suffix가 `.dlq` (`.DLT` 아님)
- [ ] `KafkaConsumerConfig.java` 재시도가 지수 백오프 방식, 최대 3회

---

# Related Specs

- `specs/platform/architecture-decision-rule.md`
- `specs/platform/event-driven-policy.md`
- `specs/platform/error-handling.md`
- `specs/services/shipping-service/architecture.md` (신규 생성 대상)

---

# Related Contracts

- `specs/contracts/http/shipping-api.md` (신규 생성 대상)
- `specs/contracts/events/shipping-events.md` (신규 생성 대상)
- `specs/contracts/events/order-events.md`

---

# Edge Cases

- shipping-api.md 작성 시 구현 코드의 실제 필드명과 반드시 일치해야 함
  - ShippingResponse: shippingId, orderId, status, trackingNumber, carrier, statusHistory[], createdAt, updatedAt
  - UpdateShippingStatusResponse: shippingId, status, updatedAt
  - ShippingListResponse: content[], page, size, totalElements
  - ShippingListItem: shippingId, orderId, status, trackingNumber, carrier, createdAt, updatedAt
- ExponentialBackOff 사용 시 Spring Kafka의 `ExponentialBackOffWithMaxRetries` 또는 `ExponentialBackOff`에 maxElapsedTime 설정 방식 중 하나 사용
- KafkaConsumerConfig 변경 후 기존 테스트에 영향 없는지 확인

---

# Failure Scenarios

- 스펙 파일 형식이 기존 파일과 일관되지 않으면 재작성
- ExponentialBackOff max retries 설정이 3회를 초과하면 정책 위반

---

# Test Requirements

- 스펙/계약 문서는 코드 테스트 불필요 (문서 변경)
- `KafkaConsumerConfig.java` 변경 사항은 기존 테스트(`ShippingIntegrationTest`, `OrderConfirmedEventConsumerTest`)가 영향 없는지 확인
