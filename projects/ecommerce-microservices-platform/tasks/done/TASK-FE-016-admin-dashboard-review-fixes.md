# Task ID

TASK-FE-016

# Title

TASK-FE-015 admin-dashboard 리뷰 수정 — 버그 수정, 타입 안전성, 누락 테스트 추가

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-015 리뷰에서 발견된 버그 3건을 수정하고, 테스트 커버리지가 부족한 컴포넌트·훅에 테스트를 추가한다.

# Scope

## In Scope

### 버그 수정
- use-products.ts: `name` 필터가 API에 전달되지 않는 버그 수정
- ProductForm.tsx, StockAdjustmentForm.tsx, login/page.tsx: `err as ApiErrorResponse` unsafe 타입 캐스트를 타입 가드로 교체
- ProductForm.tsx: 가격 검증 `price >= 0` → `price > 0` 수정

### 누락 테스트 추가
- ProductDetail 컴포넌트 테스트
- StockAdjustmentForm 컴포넌트 테스트
- useUpdateProduct 훅 테스트
- useAdjustStock 훅 테스트
- FilterBar 컴포넌트 테스트
- Sidebar 컴포넌트 테스트

## Out of Scope

- 접근성 개선 (포커스 트랩, ARIA 등)
- 검색 디바운싱
- 역할 기반 접근 제어
- 구조적 로깅 추가

# Acceptance Criteria

- [ ] `name` 필터가 `getProducts` API 호출 시 전달됨
- [ ] 모든 catch 블록에서 안전한 에러 타입 처리 적용
- [ ] 가격 0원 등록이 불가능 (price > 0)
- [ ] ProductDetail, StockAdjustmentForm 컴포넌트 테스트 존재
- [ ] useUpdateProduct, useAdjustStock 훅 테스트 존재
- [ ] FilterBar, Sidebar 컴포넌트 테스트 존재
- [ ] 기존 테스트 포함 전체 테스트 통과
- [ ] 빌드 성공

# Related Specs

- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/http/product-api.md`

# Edge Cases

- API 에러가 ApiErrorResponse 형식이 아닌 경우 (네트워크 에러 등)
- price가 정확히 0인 경우 폼 제출 불가
- name 필터가 빈 문자열인 경우

# Failure Scenarios

- 알 수 없는 에러 타입이 throw된 경우 기본 메시지 표시
- 검색 필터 적용 후 API 실패 시 에러 상태 표시
