# Task ID

TASK-FE-057-fix-001

# Title

TASK-FE-057 리뷰 수정 — SENT 필터링, 알림 유형 표시, window.alert 제거

# Status

done

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-057 리뷰에서 발견된 critical/warning 이슈를 수정한다.

# Scope

## In Scope

- NotificationList에서 SENT 상태 알림만 표시하도록 클라이언트 필터링 추가
- 알림 유형 표시: API에 type 필드가 없으므로 subject를 유형 대체로 표시하는 방식 적용 (또는 channel을 유형으로 표시하는 현재 방식을 AC에 맞게 조정)
- use-update-preferences.ts에서 `window.alert` 제거 → 인라인 에러/성공 피드백으로 변경
- 설정 변경 실패 시 롤백 동작 테스트 추가
- 태스크 파일 Status를 `review`로 수정

## Out of Scope

- API 컨트랙트에 type 필드 추가 (별도 태스크)
- 실시간 푸시 알림

# Acceptance Criteria

- [ ] PENDING/FAILED 상태 알림이 사용자에게 노출되지 않는다
- [ ] 알림 목록에 유형 정보가 표시된다 (subject 기반)
- [ ] `window.alert`가 제거된다
- [ ] 롤백 테스트가 추가된다

# Related Specs

- `specs/contracts/http/notification-api.md`

# Related Contracts

- `specs/contracts/http/notification-api.md`

# Edge Cases

- 모든 알림이 PENDING/FAILED인 경우 빈 목록 표시

# Failure Scenarios

- 설정 변경 네트워크 에러 시 롤백
