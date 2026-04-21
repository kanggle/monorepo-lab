# TASK-FE-044-FIX-01: Admin Dashboard 버튼/입력 필드 focus 스타일 미적용 수정

## Goal

TASK-FE-044 리뷰에서 발견된 이슈 수정: 버튼과 입력 필드에 focus 스타일(outline)이 전혀 적용되어 있지 않다.
`shared/constants/design.ts`의 `inputStyle`, `primaryButton`, `secondaryButton`, `dangerButton`, `smallButton` 모두 `:focus` 관련 속성이 없으며, 전역 CSS도 존재하지 않는다.
브라우저마다 기본 outline을 제거하거나 무시하는 경우 접근성(키보드 네비게이션) 기준이 충족되지 않는다.

## Scope

- `apps/admin-dashboard/src/shared/constants/design.ts` — `inputStyle`, `primaryButton`, `secondaryButton`, `dangerButton`, `smallButton`에 `:focus-visible` 기준 outline 주석 또는 전역 CSS 추가
- `apps/admin-dashboard/src/app/` 또는 동등한 위치에 `globals.css` 또는 equivalent 전역 스타일 파일을 통해 인라인 style prop으로는 표현할 수 없는 `:focus-visible` 의사 클래스 스타일 적용

### In Scope

1. 버튼(button)과 입력 필드(input, textarea, select)에 `:focus-visible` outline 스타일 추가
2. 브라우저 기본 outline reset 후 커스텀 outline(`2px solid #2563eb`, `outline-offset: 2px`) 적용
3. 기존 테스트가 통과하는지 확인

### Out of Scope

- 다크모드/테마
- 컴포넌트 전면 리팩터링

## Acceptance Criteria

- [ ] 버튼, input, textarea, select 요소에 `:focus-visible` 시 파란색(blue600) outline이 표시된다
- [ ] 브라우저 기본 outline이 `outline: none` 등으로 제거된 경우에도 커스텀 outline이 대체 적용된다
- [ ] 기존 테스트가 모두 통과한다

## Related Specs

- `specs/services/admin-dashboard/architecture.md`

## Related Contracts

- 없음 (프론트엔드 UI 개선)

## Edge Cases

- 마우스 클릭 시에는 `:focus-visible`이 활성화되지 않아야 한다 (브라우저 표준 동작)
- 버튼 `disabled` 상태에서는 focus outline이 표시되지 않아야 한다

## Failure Scenarios

- focus outline 추가 후 기존 레이아웃이 깨지지 않아야 한다
- outline-offset으로 인한 레이아웃 시프트가 없어야 한다
