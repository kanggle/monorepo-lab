# Task ID

TASK-BE-078-FIX2-preference-transaction-and-shipping-contract

# Title

알림 서비스 — FIX 리뷰 잔여 이슈 수정 (PreferenceService 트랜잭션 / shipping-events 계약 / 관리자 역할 테스트)

# Status

ready

# Owner

backend

# Task Tags

- fix
- event
- api
- test

---

# Goal

TASK-BE-078-FIX 리뷰에서 발견된 미수정 Critical 및 Warning 이슈를 수정한다.

수정 대상:
1. `PreferenceService.getPreference()`가 `@Transactional(readOnly = true)` 컨텍스트 내에서 `preferenceRepository.save()` 호출 — readOnly 트랜잭션에서는 JPA 더티 체킹이 비활성화되어 저장이 누락될 수 있음
2. `specs/contracts/events/shipping-events.md` 파일 미존재 — `ShippingStatusChangedEventConsumer` 구현이 계약 없이 존재하며 `event-driven-policy.md`의 계약 우선 원칙 위반
3. `TemplateControllerTest`에서 관리자 역할 헤더가 없을 때 403 반환 테스트 누락

---

# Scope

## In Scope

- `apps/notification-service/src/main/java/com/example/notification/application/service/PreferenceService.java`
  - `getPreference()` 메서드에 `@Transactional` 어노테이션 추가 (readOnly = false로 분리)
  - 클래스 레벨은 `@Transactional(readOnly = true)` 유지, 메서드 레벨에서 오버라이드
- `specs/contracts/events/shipping-events.md`
  - `ShippingStatusChanged` 이벤트 스키마 정의 신규 작성
  - `ShippingStatusChangedEvent.java`의 필드 구조(`shippingId`, `orderId`, `userId`, `previousStatus`, `newStatus`, `trackingNumber`, `carrier`, `changedAt`)를 반영
- `apps/notification-service/src/test/java/com/example/notification/adapter/in/rest/TemplateControllerTest.java`
  - 관리자 역할 헤더(`X-User-Role`) 없는 요청 시 403 ACCESS_DENIED 반환 테스트 추가
  - `ADMIN` 이외의 역할 헤더로 요청 시 403 반환 테스트 추가

## Out of Scope

- KafkaConsumerConfig, V4 마이그레이션, TemplateController 본체 수정 (이미 수정됨)

---

# Acceptance Criteria

- [ ] `PreferenceService.getPreference()`가 기본 설정 저장 시 readOnly가 아닌 트랜잭션 컨텍스트에서 실행된다
- [ ] `specs/contracts/events/shipping-events.md`가 존재하고 `ShippingStatusChanged` 이벤트 스키마를 정의한다
  - 이벤트 봉투 필드(`event_id`, `event_type`, `occurred_at`, `source`, `payload`) 포함
  - payload 필드: `shippingId`, `orderId`, `userId`, `previousStatus`, `newStatus`, `trackingNumber`, `carrier`, `changedAt`
- [ ] `TemplateControllerTest`에서 X-User-Role 헤더 없는 요청 시 403 반환 테스트가 존재한다
- [ ] `TemplateControllerTest`에서 비관리자 역할(예: USER) 헤더로 요청 시 403 반환 테스트가 존재한다

---

# Related Specs

- `specs/platform/event-driven-policy.md` — 계약 우선 원칙, DLQ 정책
- `specs/services/notification-service/architecture.md`

# Related Contracts

- `specs/contracts/events/shipping-events.md` — 신규 작성 대상
- `specs/contracts/http/notification-api.md` — 어드민 엔드포인트 권한 규칙

---

# Edge Cases

- `getPreference()` 동시 호출 시 기본 설정 중복 INSERT 발생 가능 → DB UNIQUE 제약 또는 예외 처리로 방어
- `shipping-events.md` 작성 시 `ShippingStatusChangedEvent.java` 기존 필드와 불일치 없도록 검증

---

# Failure Scenarios

- `getPreference()`에 `@Transactional` 추가 누락 시 readOnly 컨텍스트에서 save 호출로 인한 데이터 손실
- 계약 파일 없이 구현이 변경될 경우 소비자와의 계약 불일치 발생

---

# Review Findings Summary

## Critical (반드시 수정)

- [PreferenceService.java:17-22] 클래스 레벨 `@Transactional(readOnly = true)` 내에서 `getPreference()` 메서드가 `@Transactional` 오버라이드 없이 `preferenceRepository.save()` 호출 — 기본 설정 저장이 누락될 수 있음
- [specs/contracts/events/shipping-events.md 없음] `ShippingStatusChangedEventConsumer`가 존재하지 않는 계약을 기반으로 구현됨 — `event-driven-policy.md` 계약 우선 원칙 위반

## Warning (수정 권장)

- [TemplateControllerTest.java] `X-User-Role` 헤더 없는 요청 및 비관리자 역할 요청에 대한 403 테스트 누락 — 관리자 역할 검증 로직이 테스트되지 않음
