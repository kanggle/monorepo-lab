# Task ID

TASK-BE-078

# Title

알림 서비스 — 이메일/SMS/푸시 알림 발송 기능

# Status

done

# Owner

backend

# Task Tags

- code
- api
- event

---

# Goal

notification-service를 신규 구축하여 이벤트 기반으로 사용자에게 알림을 발송한다.

주문 확인, 결제 완료, 배송 상태 변경 등 주요 이벤트 발생 시 적절한 채널(이메일, SMS, 푸시)로 알림을 전달한다.

---

# Scope

## In Scope

- notification-service 신규 서비스 부트스트랩
- 알림 템플릿 관리
- 이벤트 소비를 통한 알림 트리거 (OrderPlaced, PaymentCompleted 등)
- 이메일 발송 (SMTP 또는 외부 API)
- 알림 이력 조회 API
- 알림 설정 (사용자별 수신 채널 설정)

## Out of Scope

- 실시간 웹소켓 알림
- 마케팅 대량 발송
- 프론트엔드 UI (별도 FE 태스크)

---

# Acceptance Criteria

- [ ] 이벤트 소비를 통한 알림 자동 발송
- [ ] 최소 이메일 채널 지원
- [ ] 알림 템플릿 관리 기능
- [ ] 알림 이력 조회 API 동작
- [ ] 사용자별 알림 설정 관리
- [ ] 발송 실패 시 재시도 처리

---

# Related Specs

- `specs/platform/architecture-decision-rule.md`
- `specs/platform/service-boundaries.md`
- `specs/platform/event-driven-policy.md`
- `specs/services/notification-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- `specs/contracts/http/notification-api.md`
- `specs/contracts/events/order-events.md`
- `specs/contracts/events/payment-events.md`
- `specs/contracts/events/user-events.md`

---

# Target Service

- `notification-service` (신규)

---

# Architecture

- Hexagonal Architecture (see `specs/services/notification-service/architecture.md`)

---

# Implementation Notes

- 외부 메일 서비스는 outbound port 인터페이스 뒤에 추상화 (어댑터 패턴)
- 이벤트 소비를 통한 알림 트리거, HTTP API는 이력 조회/설정 관리용

---

# Edge Cases

- 동일 이벤트 중복 수신 시 알림 중복 발송 방지
- 사용자가 알림 수신 거부한 채널로 발송 시도
- 알림 템플릿에 필요한 데이터 누락

---

# Failure Scenarios

- 외부 메일 서비스 장애
- 이벤트 소비 실패
- 템플릿 렌더링 오류

---

# Test Requirements

- unit test
- integration test
- 외부 서비스 모킹 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
