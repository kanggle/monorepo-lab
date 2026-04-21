# Task ID

TASK-FE-057

# Title

web-store 알림 — 알림 목록/상세 조회 및 알림 설정 관리

# Status

backlog

# Owner

frontend

# Task Tags

- code
- api
- test

# Goal

고객이 마이페이지에서 수신한 알림 목록을 확인하고, 알림 상세를 조회하며, 알림 수신 채널(이메일/SMS/푸시) 설정을 관리할 수 있다.

# Scope

## In Scope

- 알림 목록 조회 (GET `/api/notifications/me`) — 페이지네이션
- 알림 상세 조회 (GET `/api/notifications/me/{notificationId}`)
- 알림 설정 조회 (GET `/api/notifications/me/preferences`)
- 알림 설정 변경 (PUT `/api/notifications/me/preferences`)
- 마이페이지 라우트 `/my/notifications`, `/my/notifications/settings` 추가

## Out of Scope

- 실시간 푸시 알림 (WebSocket/SSE)
- 알림 삭제
- 알림 읽음 처리
- 관리자 알림 관리 (admin-dashboard에서 별도 처리)

# Acceptance Criteria

- [ ] 마이페이지 `/my/notifications`에서 알림 목록이 시간순으로 표시된다
- [ ] 알림 클릭 시 상세 내용이 표시된다
- [ ] 알림에 유형(주문, 결제, 배송 등), 제목, 발송 시간이 표시된다
- [ ] `/my/notifications/settings`에서 채널별 수신 설정을 토글할 수 있다
- [ ] 설정 변경 시 즉시 저장되고 피드백이 표시된다
- [ ] 로딩/에러/빈 상태가 처리된다
- [ ] 컴포넌트 테스트와 페이지 테스트가 작성된다

# Related Specs

- `specs/services/notification-service/overview.md`
- `specs/services/notification-service/architecture.md`
- `specs/services/web-store/overview.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/frontend/implementation-workflow.md`
- `.claude/skills/frontend/architecture/feature-sliced-design.md`
- `.claude/skills/frontend/api-client.md`
- `.claude/skills/frontend/state-management.md`
- `.claude/skills/frontend/form-handling.md`
- `.claude/skills/frontend/loading-error-handling.md`
- `.claude/skills/frontend/testing-frontend.md`

# Related Contracts

- `specs/contracts/http/notification-api.md`

# Target App

- `apps/web-store`

# Implementation Notes

- feature-sliced-design에 따라 `features/notification/` 디렉토리 구성
- 알림 설정: emailEnabled, smsEnabled, pushEnabled (boolean 토글)
- 알림 채널: EMAIL, SMS, PUSH
- 알림 상태: PENDING, SENT, FAILED — 사용자에게는 SENT만 표시
- 마이페이지 네비게이션에 "알림" 메뉴 추가

# Edge Cases

- 알림이 없는 경우 (빈 상태)
- 알림 설정이 아직 없는 경우 (기본값 처리)
- FAILED 상태 알림 표시 여부
- 대량 알림 페이지네이션

# Failure Scenarios

- API 호출 실패
- 설정 변경 시 네트워크 에러 (롤백 처리)
- 권한 없는 접근 (401)
- 타임아웃

# Test Requirements

- NotificationList 컴포넌트 테스트 (목록 렌더링)
- NotificationDetail 컴포넌트 테스트
- NotificationSettings 컴포넌트 테스트 (토글 동작, 저장)
- 페이지 테스트
- 에러/로딩/빈 상태 테스트

# Definition of Done

- [ ] UI 구현 완료
- [ ] API 연동 완료
- [ ] 로딩/에러/빈 상태 처리
- [ ] 테스트 추가
- [ ] 테스트 통과
- [ ] 리뷰 준비 완료
