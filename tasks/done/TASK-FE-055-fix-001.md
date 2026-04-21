# Task ID

TASK-FE-055-fix-001

# Title

TASK-FE-055 리뷰 수정 — 운송장 표시 방어 처리, retry 정책 개선

# Status

review

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-055 리뷰에서 발견된 warning 이슈를 수정한다.

# Scope

## In Scope

- ShippingTracker.tsx: 운송장/택배사 표시 조건을 `showTrackingInfo`만으로 분리, 각 필드 개별 렌더링
- use-shipping-tracking.ts: `retry: false` → 에러 코드 기반 조건부 retry로 변경 (SHIPPING_NOT_FOUND, ACCESS_DENIED만 retry 차단)
- 취소된 주문 배송 정보 조회 테스트 추가

## Out of Scope

- 기능 추가

# Acceptance Criteria

- [ ] 운송장/택배사 중 하나만 있어도 표시된다
- [ ] 404/403 에러는 재시도하지 않고, 네트워크 에러/5xx는 재시도한다
- [ ] 기존 테스트가 통과한다

# Related Specs

- `specs/contracts/http/shipping-api.md`

# Related Contracts

- `specs/contracts/http/shipping-api.md`

# Edge Cases

- 운송장만 있고 택배사 없는 경우

# Failure Scenarios

- 네트워크 타임아웃 시 재시도
