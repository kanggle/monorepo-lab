# Task ID

TASK-FE-039

# Title

프론트엔드 접근성(a11y) 개선 — form label 연결, aria-label 추가, Next.js Image 전환

# Status

done

# Owner

frontend

# Task Tags

- code
- test

# Goal

admin-dashboard와 web-store의 접근성 문제를 수정한다.

현재 문제:
1. form label이 `htmlFor`로 input에 연결되지 않아 스크린 리더가 관계를 인식하지 못함
2. 버튼, 액션 요소에 `aria-label`이 누락됨
3. web-store에서 `<img>` 태그를 직접 사용하여 Next.js Image 최적화 미적용
4. 주문 상세 페이지에서 전화번호가 마스킹 없이 노출됨

# Scope

## In Scope

- VariantEditor, StockAdjustmentForm 등 form 컴포넌트의 label `htmlFor` 추가
- PageLayout, DataTable 등 인터랙티브 요소의 `aria-label` 추가
- web-store ProductImage 컴포넌트의 Next.js `Image` 전환
- web-store 주문 상세 전화번호 마스킹 처리
- 테스트 추가

## Out of Scope

- WAI-ARIA 전체 감사
- 키보드 네비게이션 전면 개선
- 색상 대비 검사

# Acceptance Criteria

- [ ] 모든 form input이 label과 `htmlFor`/`id`로 연결되어 있다
- [ ] 인터랙티브 요소(버튼, 링크)에 `aria-label`이 존재한다
- [ ] web-store의 상품 이미지가 Next.js `Image` 컴포넌트를 사용한다
- [ ] 주문 상세 전화번호가 중간 자리 마스킹 처리된다 (예: 010-****-1234)
- [ ] 기존 테스트가 통과한다

# Related Specs

- `specs/services/admin-dashboard/architecture.md`
- `specs/services/web-store/architecture.md`

# Related Skills

- `.claude/skills/frontend/react-patterns.md`

# Related Contracts

없음

# Target App

- `apps/admin-dashboard`
- `apps/web-store`

# Implementation Notes

- label-input 연결: 각 input에 고유 `id` 부여, label에 `htmlFor` 추가
- Next.js Image: `import Image from 'next/image'` 후 width/height 또는 fill 속성 설정
- 전화번호 마스킹: `maskPhone("010-1234-5678")` → `"010-****-5678"` 유틸 함수 구현
- LoadingSpinner에 `role="status"` 및 `aria-label="로딩 중"` 추가

# Edge Cases

- 이미지 로드 실패 시 fallback 처리 (onError)
- 전화번호 형식이 표준이 아닌 경우 마스킹
- label 없는 검색 input (placeholder만 있는 경우)

# Failure Scenarios

- Next.js Image 설정 오류 시 빌드 실패 → width/height 필수
- 이미지 도메인 미등록 시 next.config.ts 수정 필요

# Test Requirements

- label-input 연결 확인 컴포넌트 테스트
- aria-label 존재 확인 테스트
- 전화번호 마스킹 유틸 단위 테스트
- Next.js Image 렌더링 확인 테스트

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
