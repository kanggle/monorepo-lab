# TASK-FE-046: Admin 상품 상세 페이지 UI 다듬기

## Goal

admin-dashboard 상품 상세 페이지의 시각적 완성도를 높이고, 정보 배치와 인터랙션을 개선한다.

## Scope

- `apps/admin-dashboard/src/features/product-management/components/ProductDetail.tsx`
- `apps/admin-dashboard/src/shared/ui/Section.tsx`

### In Scope

1. Section 컴포넌트에 카드 스타일 적용 (배경, border, padding, border-radius)
2. 기본 정보 섹션에 StatusBadge를 상태 드롭다운 옆에 함께 표시하여 현재 상태를 시각적으로 명확히
3. 가격 표시에 통화 강조 스타일 적용 (큰 폰트, 볼드)
4. 옵션/재고 테이블 헤더에 배경색 적용, 재고 수량에 조건부 색상 (재고 부족 시 빨간색)
5. 설명 텍스트가 비어있을 때 빈 상태 표시 ("-")
6. 목록으로 돌아가기 링크 추가

### Out of Scope

- 상품 삭제 기능
- 상품 이미지 표시
- 새로운 API 호출

## Acceptance Criteria

- [ ] Section 컴포넌트가 카드 스타일(흰 배경, border, padding, border-radius)을 가진다
- [ ] 상태 드롭다운 옆에 StatusBadge가 현재 상태를 시각적으로 표시한다
- [ ] 가격이 강조된 스타일(큰 폰트, 볼드)로 표시된다
- [ ] 옵션 테이블 헤더에 배경색이 적용된다
- [ ] 재고가 5개 이하인 옵션은 빨간 텍스트로 표시된다
- [ ] 설명이 빈 문자열이면 "-"로 표시된다
- [ ] 상품 목록으로 돌아가는 링크가 존재한다
- [ ] 기존 테스트가 통과한다

## Related Specs

- `specs/services/admin-dashboard/architecture.md`

## Related Contracts

- 없음 (프론트엔드 UI 개선)

## Edge Cases

- Section 카드 스타일 변경 시 다른 페이지(주문, 사용자 상세)의 레이아웃이 깨지지 않아야 한다
- 재고가 0인 경우와 5 이하인 경우를 구분하여 표시

## Failure Scenarios

- 데스크탑/모바일 모두에서 카드 레이아웃이 정상 표시되어야 한다
