# Task ID

TASK-FE-059-fix-001

# Title

TASK-FE-059 리뷰 수정 — SHIPPING_STATUS_OPTIONS 누락, 테스트 디렉토리 중복, 상태 레이블 개선

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-059 리뷰에서 발견된 critical/warning 이슈를 수정한다.

# Scope

## In Scope

- shared/lib/status-options.ts에 `SHIPPING_STATUS_OPTIONS`와 `VALID_SHIPPING_STATUSES` 상수 추가
- 테스트 디렉토리 `shipping-management/shipping-management/` → `shipping-management/`로 이동
- StatusBadge.tsx의 IN_TRANSIT 레이블 `'배송중(운송)'` → `'운송중'`으로 변경 (SHIPPED '배송중'과 구분)
- ConfirmDialog 메시지 문구 자연스럽게 수정

## Out of Scope

- 기능 추가

# Acceptance Criteria

- [ ] ShippingList가 런타임 에러 없이 정상 렌더링된다
- [ ] 테스트 디렉토리 구조가 컨벤션과 일치한다
- [ ] 상태 레이블이 자연스러운 한국어로 표시된다
- [ ] 모든 테스트가 통과한다

# Related Specs

- `specs/contracts/http/shipping-api.md`

# Related Contracts

- `specs/contracts/http/shipping-api.md`

# Edge Cases

- 없음

# Failure Scenarios

- status-options 임포트 실패
