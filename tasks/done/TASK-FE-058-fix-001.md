# Task ID

TASK-FE-058-fix-001

# Title

TASK-FE-058 리뷰 수정 — 라우트 경로 이중 중첩, 로딩/에러 순서, 날짜 시간대 왜곡

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-058 리뷰에서 발견된 critical/warning 이슈를 수정한다.

# Scope

## In Scope

- 페이지 파일을 `(admin)/promotions/promotions/` → `(admin)/promotions/`로 이동 (경로 중복 제거)
- 테스트 디렉토리를 `promotion-management/promotion-management/` → `promotion-management/`로 이동
- 테스트 import 경로 수정
- PromotionDetail.tsx: isError 체크를 isLoading보다 앞으로 이동
- use-promotion-form.ts: 날짜 ISO 변환 시 시간대 왜곡 방지 (`new Date()` 대신 직접 ISO 문자열 조립)
- 기존 잘못된 디렉토리 삭제

## Out of Scope

- 기능 추가
- minimumOrderAmount 필드 추가

# Acceptance Criteria

- [ ] `/promotions`, `/promotions/new`, `/promotions/[id]`, `/promotions/[id]/edit` 라우트가 정상 동작한다
- [ ] 에러 상태에서 스켈레톤 대신 에러 메시지가 표시된다
- [ ] 날짜가 UTC+9 환경에서도 올바르게 서버에 전달된다
- [ ] 모든 테스트가 통과한다

# Related Specs

- `specs/contracts/http/promotion-api.md`

# Related Contracts

- `specs/contracts/http/promotion-api.md`

# Edge Cases

- UTC+9 환경에서 날짜 경계값

# Failure Scenarios

- 잘못된 라우트 접근
