# Task ID

TASK-FE-060

# Title

admin-dashboard 알림 템플릿 관리 — 알림 템플릿 목록/생성/수정

# Status

review

# Owner

frontend

# Task Tags

- code
- api
- test

# Goal

관리자가 알림 템플릿을 조회/생성/수정하여 시스템 알림의 내용과 형식을 관리할 수 있다.

# Scope

## In Scope

- 알림 템플릿 목록 (GET `/api/notifications/templates`) — 페이지네이션
- 알림 템플릿 생성 (POST `/api/notifications/templates`)
- 알림 템플릿 수정 (PUT `/api/notifications/templates/{templateId}`)
- 라우트: `/notifications/templates`, `/notifications/templates/new`, `/notifications/templates/[id]/edit`

## Out of Scope

- 알림 발송 이력 조회
- 알림 수동 발송
- 템플릿 삭제
- 템플릿 미리보기 렌더링

# Acceptance Criteria

- [ ] 알림 템플릿 목록이 페이지네이션으로 표시된다
- [ ] 각 템플릿에 유형(ORDER_PLACED 등), 채널, 제목이 표시된다
- [ ] 템플릿 생성 폼에서 유형, 채널, 제목, 본문을 입력할 수 있다
- [ ] 본문에 `{{variable}}` 형식의 플레이스홀더 사용이 안내된다
- [ ] 템플릿 수정이 가능하다
- [ ] 폼 validation이 동작한다 (필수값)
- [ ] 로딩/에러/빈 상태가 처리된다
- [ ] 컴포넌트 테스트와 페이지 테스트가 작성된다

# Related Specs

- `specs/services/notification-service/overview.md`
- `specs/services/notification-service/architecture.md`
- `specs/services/admin-dashboard/overview.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/frontend/implementation-workflow.md`
- `.claude/skills/frontend/architecture/layered-by-feature.md`
- `.claude/skills/frontend/api-client.md`
- `.claude/skills/frontend/state-management.md`
- `.claude/skills/frontend/form-handling.md`
- `.claude/skills/frontend/loading-error-handling.md`
- `.claude/skills/frontend/testing-frontend.md`

# Related Contracts

- `specs/contracts/http/notification-api.md`

# Target App

- `apps/admin-dashboard`

# Implementation Notes

- layered-by-feature 아키텍처에 따라 `features/notification-management/` 디렉토리 구성
- 템플릿 유형: ORDER_PLACED, PAYMENT_COMPLETED, SHIPPING_STATUS_CHANGED, WELCOME
- 채널: EMAIL, SMS, PUSH
- 본문에 `{{orderNumber}}`, `{{userName}}` 등 변수 플레이스홀더 지원
- 사이드바 네비게이션에 "알림 관리" 메뉴 추가
- 기존 product-management 폼 패턴 참고

# Edge Cases

- 템플릿이 없는 경우 (빈 상태)
- 동일 유형+채널 조합의 템플릿 중복 생성 시도
- 긴 본문 입력
- 잘못된 플레이스홀더 변수명 입력

# Failure Scenarios

- API 호출 실패
- 템플릿 생성/수정 시 validation 실패
- 중복 템플릿 생성 (409)
- 권한 없는 접근 (403)
- 타임아웃

# Test Requirements

- TemplateList 페이지 테스트 (목록, 페이지네이션)
- TemplateForm 컴포넌트 테스트 (생성/수정 폼, validation)
- 에러/로딩/빈 상태 테스트

# Definition of Done

- [ ] UI 구현 완료
- [ ] API 연동 완료
- [ ] 로딩/에러/빈 상태 처리
- [ ] 테스트 추가
- [ ] 테스트 통과
- [ ] 리뷰 준비 완료
