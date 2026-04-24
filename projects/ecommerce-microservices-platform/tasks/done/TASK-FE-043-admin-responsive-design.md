# TASK-FE-043: Admin Dashboard 반응형 디자인 대응

## Goal

admin-dashboard를 모바일/태블릿 환경에서도 사용할 수 있도록 반응형 레이아웃을 적용한다.

## Scope

- `apps/admin-dashboard/src/` 내 레이아웃 및 공통 UI 컴포넌트

### In Scope

1. Sidebar 반응형 처리 (모바일에서 햄버거 메뉴로 토글)
2. DataTable 가로 스크롤 오버플로우 처리
3. FilterBar flex-wrap 및 검색 필드 고정 너비 제거
4. PageLayout 헤더 액션 영역 반응형 처리
5. DescriptionList 모바일 레이아웃 조정
6. 폼 컴포넌트(ProductForm, StockAdjustmentForm) maxWidth 모바일 대응

### Out of Scope

- CSS 프레임워크(Tailwind 등) 도입
- 전역 CSS 파일 도입 (인라인 스타일 유지)
- 페이지 기능 변경

## Acceptance Criteria

- [ ] 모바일(375px)에서 Sidebar가 숨겨지고 햄버거 버튼으로 토글 가능하다
- [ ] 태블릿(768px) 이상에서 Sidebar가 항상 표시된다
- [ ] DataTable이 모바일에서 가로 스크롤이 가능하다
- [ ] FilterBar가 모바일에서 줄바꿈되어 표시된다
- [ ] PageLayout 헤더의 제목과 액션이 모바일에서 세로로 배치된다
- [ ] DescriptionList가 모바일에서 라벨/값이 세로로 배치된다
- [ ] 폼 컴포넌트가 모바일 화면 너비를 벗어나지 않는다
- [ ] 기존 테스트가 통과한다

## Related Specs

- `specs/services/admin-dashboard/architecture.md`

## Related Contracts

- 없음 (프론트엔드 UI 개선)

## Edge Cases

- Sidebar 토글 시 본문 콘텐츠가 밀리지 않아야 한다 (오버레이 방식)
- 매우 긴 텍스트(상품명, 이메일 등)가 레이아웃을 깨뜨리지 않아야 한다
- DataTable 열이 많을 때(5개 이상) 가로 스크롤이 자연스러워야 한다

## Failure Scenarios

- 데스크탑 해상도에서 기존 레이아웃이 변경되지 않아야 한다
- Sidebar 토글 상태가 페이지 이동 시 유지되어야 한다
