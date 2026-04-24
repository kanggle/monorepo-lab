# Task ID

TASK-FE-060-fix-001

# Title

TASK-FE-060 리뷰 수정 — 라우트 경로 이중 중첩, 미정의 API 엔드포인트, 409 에러 처리

# Status

review

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-060 리뷰에서 발견된 critical/warning 이슈를 수정한다.

# Scope

## In Scope

- 페이지 파일을 `(admin)/notifications/notifications/templates/` → `(admin)/notifications/templates/`로 이동
- 테스트 디렉토리 중복 제거 (`notification-management/notification-management/` → `notification-management/`)
- 테스트 import 경로 수정
- `getTemplate` API: 수정 폼에서 목록 캐시 데이터를 재활용하는 방식으로 변경 (미정의 엔드포인트 호출 제거) 또는 컨트랙트에 엔드포인트 추가
- 409 `TEMPLATE_ALREADY_EXISTS` 에러에 대한 한국어 메시지 처리
- use-template-form.ts 이중 상태 관리 정리
- TemplateList 타입 레이블 키 타입 좁히기

## Out of Scope

- 기능 추가

# Acceptance Criteria

- [ ] `/notifications/templates`, `/notifications/templates/new`, `/notifications/templates/[id]/edit` 라우트가 정상 동작한다
- [ ] 컨트랙트에 없는 API 엔드포인트가 호출되지 않거나, 컨트랙트가 업데이트된다
- [ ] 409 에러 시 "동일한 유형/채널 조합의 템플릿이 이미 존재합니다" 메시지가 표시된다
- [ ] 모든 테스트가 통과한다

# Related Specs

- `specs/contracts/http/notification-api.md`

# Related Contracts

- `specs/contracts/http/notification-api.md`

# Edge Cases

- 동일 유형+채널 중복 생성

# Failure Scenarios

- 409 Conflict 응답
