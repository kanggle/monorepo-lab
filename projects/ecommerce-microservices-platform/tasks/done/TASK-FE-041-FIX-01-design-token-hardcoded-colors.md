# Task ID

TASK-FE-041-FIX-01

# Title

web-store UI — 하드코딩된 색상 값을 CSS 변수(디자인 토큰)로 교체

# Status

review

# Owner

frontend

# Task Tags

- code

# Goal

TASK-FE-041 리뷰에서 발견된 하드코딩된 hex 색상 값을 globals.css의 CSS 변수로 교체하여 디자인 토큰 일관성을 확보한다.

# Scope

## 포함

- `apps/web-store/src/shared/ui/Toast.tsx`
  - `STYLE_MAP`의 `backgroundColor`, `borderColor` 등 하드코딩된 hex 색상 → CSS 변수 사용
- `apps/web-store/src/features/order/ui/OrderDetailView.tsx`
  - 버튼 인라인 스타일의 `color: '#fff'` → `color: 'var(--color-white)'` 또는 적절한 CSS 변수 사용

## 제외

- 새로운 디자인 변경 또는 기능 추가
- globals.css 디자인 토큰 구조 변경

# Acceptance Criteria

- [ ] Toast.tsx의 STYLE_MAP에서 하드코딩된 hex 색상이 CSS 변수로 교체됨
- [ ] OrderDetailView.tsx의 `color: '#fff'` 가 CSS 변수로 교체됨
- [ ] 기존 테스트 통과 유지

# Related Specs

- specs/services/web-store/architecture.md
- specs/platform/coding-rules.md

# Related Contracts

- (없음)

# Edge Cases

- globals.css에 white 변수가 없는 경우 적절한 대안 변수 사용 또는 추가

# Failure Scenarios

- CSS 변수 변경 후 Toast 및 OrderDetail 화면에서 시각적 색상이 의도와 다르게 표시되는 경우
