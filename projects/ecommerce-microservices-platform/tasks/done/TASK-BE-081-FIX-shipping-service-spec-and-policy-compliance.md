# Task ID

TASK-BE-081-FIX-shipping-service-spec-and-policy-compliance

# Title

배송 서비스 스펙/정책 누락 항목 보완 — architecture.md, 컨트랙트, 에러코드, 이벤트 정책 준수

# Status

<<<<<<<< HEAD:tasks/done/TASK-BE-081-FIX-shipping-service-spec-and-policy-compliance.md
done
========
review
>>>>>>>> worktree-agent-ac4cdd70:tasks/review/TASK-BE-081-FIX-shipping-service-spec-and-policy-compliance.md

# Owner

backend

# Task Tags

- spec
- contract
- event
- code

---

# Goal

TASK-BE-081 리뷰에서 발견된 Critical/Warning 이슈를 수정한다.

구체적으로 다음 항목을 보완한다:
1. `specs/services/shipping-service/architecture.md` 신규 작성 (필수 스펙 누락)
2. `specs/contracts/http/shipping-api.md` 신규 작성 (API 계약 누락)
3. `specs/contracts/events/shipping-events.md` 신규 작성 (이벤트 계약 누락)
4. `specs/platform/error-handling.md`에 shipping 서비스 에러코드 등록
5. `specs/platform/event-driven-policy.md` Services Using Events 테이블에 shipping-service 추가
6. DLQ 토픽 suffix를 구현 코드(`.DLT`)에서 정책(`.dlq`)에 맞게 수정
7. Kafka 재시도 정책을 구현 코드에서 정책(3회/지수 백오프)에 맞게 수정

---

# Scope

## In Scope

- `specs/services/shipping-service/architecture.md` 신규 생성
- `specs/contracts/http/shipping-api.md` 신규 생성 (구현된 API 기반으로 작성)
- `specs/contracts/events/shipping-events.md` 신규 생성 (ShippingStatusChanged 이벤트 계약)
- `specs/platform/error-handling.md` — Shipping 에러코드 섹션 추가
  - `SHIPPING_NOT_FOUND` (404)
  - `INVALID_SHIPPING_REQUEST` (400)
  - `INVALID_STATUS_TRANSITION` (422)
- `specs/platform/event-driven-policy.md` — Services Using Events 테이블에 shipping-service 행 추가
- `apps/shipping-service/src/main/java/com/example/shipping/infrastructure/config/KafkaConsumerConfig.java`
  - DLQ suffix: `.DLT` → `.dlq`
  - 재시도: `FixedBackOff(1000L, 2)` → `ExponentialBackOff(1000L, 2.0)` + max 3회

## Out of Scope

- shipping-service 비즈니스 로직 변경
- 데이터베이스 스키마 변경
- 새 API 추가

---

# Acceptance Criteria

- [x] `specs/services/shipping-service/architecture.md` 파일 존재하며 레이어 구조 명시
- [x] `specs/contracts/http/shipping-api.md` 파일 존재하며 구현된 3개 엔드포인트 계약 포함
- [x] `specs/contracts/events/shipping-events.md` 파일 존재하며 ShippingStatusChanged 이벤트 계약 포함
- [x] `specs/platform/error-handling.md`에 SHIPPING_NOT_FOUND, INVALID_SHIPPING_REQUEST, INVALID_STATUS_TRANSITION 에러코드 등록
- [x] `specs/platform/event-driven-policy.md` Services Using Events 테이블에 `shipping-service | ShippingStatusChanged | order-service, notification-service` 행 추가
- [x] `KafkaConsumerConfig.java` DLQ suffix가 `.dlq`
- [x] `KafkaConsumerConfig.java` 재시도가 지수 백오프 방식, 최대 3회

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

- shipping-api.md 작성 시 구현 코드의 실제 필드명과 일치하도록 역공학 필요
- event-driven-policy.md 수정 시 기존 테이블 형식 유지

---

# Failure Scenarios

- 스펙 파일 형식이 기존 파일과 일관되지 않으면 재작성

---

# Test Requirements

- 스펙/계약 문서는 코드 테스트 불필요 (문서 변경)
- `KafkaConsumerConfig.java` 변경 사항은 기존 테스트가 영향 없는지 확인
