# TASK-FE-044: Admin Dashboard UI/디자인 개선

## Goal

admin-dashboard의 인라인 스타일 일관성을 확보하고, 디자인 토큰을 도입하여 유지보수성과 시각적 품질을 높인다.

## Scope

- `apps/admin-dashboard/src/` 내 공통 UI 컴포넌트 및 피처 컴포넌트

### In Scope

1. 디자인 토큰 상수 파일 생성 (`shared/constants/design.ts`) — 색상, 여백, 타이포그래피, border-radius, shadow
2. 입력 필드 패딩 표준화 (8px 12px 통일)
3. 버튼 스타일 표준화 — LoginForm 버튼 패딩을 공통 패턴(8px 16px)으로 통일
4. 에러 색상 표준화 — ListError의 `red` → `#dc2626`
5. 폼 필드 여백 표준화 (marginBottom 16px 통일)
6. 버튼/입력 필드에 focus 스타일 추가 (outline)
7. DataTable 행 hover 스타일 추가
8. 기존 공통 UI 컴포넌트에서 디자인 토큰 적용 (하드코딩 hex → 상수 참조)

### Out of Scope

- CSS 프레임워크 도입
- 다크모드/테마 시스템
- 대시보드 통계 페이지 구현
- Sidebar 아이콘 추가
- 스토리북 구축

## Acceptance Criteria

- [ ] `shared/constants/design.ts`가 존재하고 색상/여백/타이포그래피 토큰을 export한다
- [ ] 모든 입력 필드의 패딩이 8px 12px로 통일되어 있다
- [ ] 모든 표준 버튼의 패딩이 8px 16px로 통일되어 있다
- [ ] ListError의 에러 색상이 `#dc2626`이다
- [ ] LoginForm의 필드 여백이 다른 폼과 일관된다
- [ ] DataTable 행에 hover 배경색이 적용된다
- [ ] 공통 UI 컴포넌트(Sidebar, PageLayout, DataTable, FilterBar, Section, DescriptionList, ConfirmDialog, Toast, StatusBadge)가 디자인 토큰을 참조한다
- [ ] 기존 테스트가 통과한다

## Related Specs

- `specs/services/admin-dashboard/architecture.md`

## Related Contracts

- 없음 (프론트엔드 UI 개선)

## Edge Cases

- 디자인 토큰 변경 시 기존 스타일이 깨지지 않아야 한다
- 버튼 focus outline이 브라우저 기본 스타일과 충돌하지 않아야 한다

## Failure Scenarios

- 데스크탑/모바일 모두에서 기존 레이아웃이 유지되어야 한다
- 색상 변경 후 텍스트 가독성이 저하되지 않아야 한다
