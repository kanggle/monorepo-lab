# Task ID

TASK-FE-033

# Title

프론트엔드 인라인 스타일 정리 — 스타일 객체 추출 및 CSS Modules 전환

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

admin-dashboard와 web-store의 다수 컴포넌트(ProductForm, CheckoutForm, AddressList, StockAdjustmentForm, products/page.tsx 등)에서 인라인 스타일 객체를 렌더 함수 내에서 직접 정의하고 있다. 매 렌더링마다 새 객체가 생성되어 불필요한 리렌더링을 유발하고, 스타일 재사용과 유지보수가 어렵다. 인라인 스타일을 파일 외부 상수 또는 CSS Modules로 추출한다.

---

# Scope

## In Scope

- admin-dashboard: ProductForm, StockAdjustmentForm의 인라인 스타일을 컴포넌트 외부 상수 객체 또는 CSS Module로 추출
- web-store: CheckoutForm, AddressList, AddressForm, products/page.tsx의 인라인 스타일을 동일하게 추출
- 프로젝트에서 이미 사용 중인 스타일링 방식(CSS Modules 또는 스타일 객체)에 맞춰 통일

## Out of Scope

- 스타일 시스템 전체 교체 (Tailwind CSS, styled-components 등 도입)
- 디자인 변경
- 모든 컴포넌트의 스타일 정리 (주요 6개 컴포넌트만 대상)

---

# Acceptance Criteria

- [ ] 대상 6개 컴포넌트의 렌더 함수 내에 인라인 `style={{...}}` 객체 리터럴이 없다
- [ ] 스타일이 컴포넌트 외부 상수 또는 CSS Module로 추출되었다
- [ ] 기존 UI 외관이 변경되지 않았다 (시각적 회귀 없음)
- [ ] 모든 기존 테스트가 통과한다

---

# Related Specs

- `specs/services/web-store/architecture.md`
- `specs/services/admin-dashboard/architecture.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/frontend/implementation-workflow.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링)

---

# Target App

- `apps/admin-dashboard`
- `apps/web-store`

---

# Implementation Notes

- 방식 1: 컴포넌트 파일 하단 또는 별도 `.styles.ts` 파일에 `const styles = { container: {...}, button: {...} }` 상수 객체 추출
- 방식 2: CSS Modules (`.module.css`) 사용 — Next.js에서 기본 지원
- 프로젝트에서 이미 사용 중인 패턴을 확인하고 그에 맞춘다.
- `useMemo`로 스타일 객체를 감싸는 방식은 피하고, 컴포넌트 외부 상수로 추출하는 것이 더 간결하다.

---

# Edge Cases

- 동적 스타일 (조건부 색상, 크기 등) → 정적 부분만 추출, 동적 부분은 인라인 유지 또는 className 조합
- 반응형 스타일이 인라인으로 정의된 경우 → CSS Module의 미디어 쿼리로 전환
- SSR 환경에서 CSS Module 로딩 순서 문제

---

# Failure Scenarios

- 스타일 추출 시 specificity 변경으로 시각적 회귀
- CSS Module 클래스명 충돌
- 동적 스타일 분리 미스로 조건부 스타일링 깨짐

---

# Test Requirements

- 기존 컴포넌트 테스트 통과 확인
- 시각적 회귀 수동 확인 (스냅샷 테스트 있으면 업데이트)
- 빌드 성공 확인

---

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
