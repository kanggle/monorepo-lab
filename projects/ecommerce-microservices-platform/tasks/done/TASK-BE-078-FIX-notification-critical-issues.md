# Task ID

TASK-BE-078-FIX-notification-critical-issues

# Title

알림 서비스 — 리뷰 이슈 수정 (DLT 네이밍 / event_id 고유 제약 / 관리자 권한 검사 / readOnly 트랜잭션 / shipping 계약 파일)

# Status

done

# Owner

backend

# Task Tags

- fix
- security
- event
- api

---

# Goal

TASK-BE-078 리뷰에서 발견된 Critical 및 Warning 이슈를 수정한다.

수정 대상:
1. DLT 토픽 네이밍이 스펙(`{original-topic}.dlq`)과 불일치 (`.DLT` → `.dlq`)
2. `notifications.event_id` 컬럼에 UNIQUE 제약 누락으로 인한 중복 발송 경쟁 조건
3. 어드민 템플릿 엔드포인트에 역할 검증 없음
4. `PreferenceService.getPreference()`에서 `readOnly = true` 트랜잭션 내 저장 호출
5. `specs/contracts/events/shipping-events.md` 계약 파일 누락

---

# Scope

## In Scope

- `apps/notification-service/src/main/java/com/example/notification/config/KafkaConsumerConfig.java`
  - DLT 토픽 접미사 `.DLT` → `.dlq` 변경
- `apps/notification-service/src/main/resources/db/migration/` 하위에 새 마이그레이션 추가
  - `event_id` UNIQUE 제약 추가 (V4)
- `apps/notification-service/src/main/java/com/example/notification/adapter/in/rest/TemplateController.java`
  - `X-User-Role` 헤더 검증 추가 (관리자 역할 확인)
- `apps/notification-service/src/main/java/com/example/notification/adapter/in/rest/GlobalExceptionHandler.java`
  - 관리자 역할 오류 처리 추가 (필요 시)
- `apps/notification-service/src/main/java/com/example/notification/application/service/PreferenceService.java`
  - `getPreference()` 메서드의 `@Transactional` 분리 (기본값 저장 경우 readOnly 제외)
- `specs/contracts/events/shipping-events.md`
  - `ShippingStatusChanged` 이벤트 계약 파일 신규 작성

## Out of Scope

- 실제 재시도 스케줄러 구현 (별도 태스크 범위)
- SMS/푸시 채널 구현

---

# Acceptance Criteria

- [ ] DLT 토픽 이름이 `{original-topic}.dlq` 형식으로 생성된다
- [ ] `notifications` 테이블의 `event_id` 컬럼에 UNIQUE 제약이 적용된다
- [ ] `POST /api/notifications/templates`와 `PUT /api/notifications/templates/{templateId}`에 관리자 역할 검증이 동작한다
  - 비관리자 요청 시 403 ACCESS_DENIED 반환
- [ ] `PreferenceService.getPreference()`가 기본 설정을 저장할 때 올바른 트랜잭션 컨텍스트에서 동작한다
- [ ] `specs/contracts/events/shipping-events.md`가 존재하고 `ShippingStatusChanged` 이벤트 스키마를 정의한다
- [ ] 수정된 항목에 대한 테스트가 추가되거나 갱신된다

---

# Related Specs

- `specs/platform/event-driven-policy.md` — DLQ 토픽 네이밍, 재시도 정책
- `specs/platform/architecture-decision-rule.md`
- `specs/services/notification-service/architecture.md`
- `specs/platform/error-handling.md`

# Related Contracts

- `specs/contracts/http/notification-api.md` — 어드민 엔드포인트 권한 규칙
- `specs/contracts/events/shipping-events.md` — 신규 작성 대상

---

# Edge Cases

- `event_id`가 NULL인 기존 레코드가 있을 경우 UNIQUE 제약 마이그레이션 충돌 가능 → NULL 허용 + NOT NULL 값에만 UNIQUE 적용 (부분 인덱스 사용 검토)
- `X-User-Role` 헤더가 없는 요청 → 403 반환
- 기본 설정 저장 중 동시 요청이 들어올 경우 → 중복 INSERT 방지 (UPSERT 또는 예외 처리)

---

# Failure Scenarios

- 마이그레이션 실패 시 서비스 기동 불가 → 롤백 스크립트 준비
- 역할 헤더 검증 실패 시 500 오류 발생 → 예외 핸들러에서 처리

---

# Review Findings Summary

## Critical (반드시 수정)

- [KafkaConsumerConfig.java:24] DLT 토픽 접미사 `.DLT` 사용 — 스펙은 `.dlq` 요구
- [V1__create_notifications_table.sql] `event_id` 컬럼에 UNIQUE 제약 없음 — 동시 중복 이벤트 경쟁 조건 발생 가능
- [TemplateController.java] 관리자 전용 엔드포인트(GET/POST/PUT templates)에 역할 검증 없음 — 계약의 admin-only 규칙 위반
- [specs/contracts/events/shipping-events.md 없음] `ShippingStatusChangedEventConsumer` 구현이 계약 없이 작성됨 — 계약 우선 원칙 위반

## Warning (수정 권장)

- [PreferenceService.java:17] `@Transactional(readOnly = true)` 메서드 내에서 `preferenceRepository.save()` 호출 — JPA 더티 체킹 비활성화 상태에서 저장이 누락될 수 있음

## Suggestion (개선 검토)

- [NotificationSendService.java:100] `retryFailedNotifications()` 메서드 본문이 비어 있고 주석만 존재 — 별도 태스크로 실제 재시도 스케줄러 구현 권장
