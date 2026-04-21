# Task ID

TASK-FE-022

# Title

web-store 배송지 관리 UI 구현 — 목록 조회, 추가, 수정, 삭제

# Status

review

# Owner

frontend

# Task Tags

- code
- api

# Goal

web-store에 배송지 관리 페이지를 구현한다. 로그인한 사용자가 자신의 배송지 목록을 조회하고, 새 배송지를 추가하며, 기존 배송지를 수정/삭제할 수 있다. 기본 배송지 설정 기능을 포함한다.

# Scope

## In Scope

- `/my/addresses` 라우트 및 페이지 구성
- 배송지 목록 조회 UI (GET /api/users/me/addresses)
- 배송지 추가 폼/모달 (POST /api/users/me/addresses)
- 배송지 수정 폼/모달 (PATCH /api/users/me/addresses/{addressId})
- 배송지 삭제 확인 + 처리 (DELETE /api/users/me/addresses/{addressId})
- 기본 배송지 설정 (isDefault 토글)
- `features/user/` 모듈 내 주소 관련 UI, API 구성
- `@repo/api-client`에 address API 함수 추가 (없는 경우)
- `@repo/types`에 Address 관련 타입 추가 (없는 경우)
- 로딩, 에러, 빈 상태 처리

## Out of Scope

- 우편번호 검색 API 연동 (직접 입력만 지원)
- 주문 시 배송지 선택 UI (별도 태스크)
- 지도 기반 주소 입력

# Acceptance Criteria

- [ ] `/my/addresses` 페이지에서 배송지 목록이 표시된다
- [ ] 새 배송지를 추가할 수 있고, 추가 후 목록에 즉시 반영된다
- [ ] 기존 배송지를 수정할 수 있다
- [ ] 배송지를 삭제할 수 있고, 삭제 전 확인 다이얼로그가 표시된다
- [ ] 기본 배송지(isDefault)가 시각적으로 구분되고 변경 가능하다
- [ ] 기본 배송지 삭제 시도 시 에러 메시지가 표시된다 (DEFAULT_ADDRESS_CANNOT_BE_DELETED)
- [ ] 배송지 10개 초과 추가 시 에러 메시지가 표시된다 (ADDRESS_LIMIT_EXCEEDED)
- [ ] 배송지가 없을 때 빈 상태 안내가 표시된다
- [ ] 미인증 사용자 접근 시 로그인 페이지로 리다이렉트된다

# Related Specs

- `specs/services/web-store/architecture.md`
- `specs/platform/architecture.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`

# Related Contracts

- `specs/contracts/http/user-api.md` — GET/POST/PATCH/DELETE /api/users/me/addresses

# Target App

- `apps/web-store`

# Implementation Notes

- FSD 구조: `features/user/ui/AddressList.tsx`, `features/user/ui/AddressForm.tsx`, `features/user/api/addressApi.ts`
- 추가/수정은 모달 또는 인라인 폼 — 기존 프로젝트 UI 패턴을 따를 것
- 삭제 시 ConfirmDialog 패턴 사용
- 서버 상태 관리는 기존 프로젝트 패턴을 따를 것
- 배송지 최대 10개 제한은 백엔드에서 검증하지만 프론트에서도 추가 버튼 비활성화로 UX 개선

# Edge Cases

- 배송지가 0개인 초기 상태
- 배송지가 정확히 10개일 때 추가 버튼 비활성화
- 기본 배송지가 1개뿐일 때 삭제 시도
- 수정 중 다른 탭에서 동일 배송지 삭제
- 기본 배송지 변경 시 기존 기본 배송지 자동 해제

# Failure Scenarios

- GET addresses 401 → 로그인 리다이렉트
- POST address 400 → 필드별 유효성 에러 표시
- POST address 422 (ADDRESS_LIMIT_EXCEEDED) → "배송지는 최대 10개까지 등록 가능합니다" 안내
- DELETE address 404 → "이미 삭제된 배송지입니다" 안내
- DELETE address 422 (DEFAULT_ADDRESS_CANNOT_BE_DELETED) → "기본 배송지는 삭제할 수 없습니다" 안내
- 네트워크 타임아웃 → 재시도 안내

# Test Requirements

- AddressList 컴포넌트 테스트 (목록 렌더링, 빈 상태, 기본 배송지 표시)
- AddressForm 컴포넌트 테스트 (추가/수정 모드, 유효성 검증)
- 배송지 CRUD hook 테스트 (성공, 에러 케이스)
- 삭제 확인 다이얼로그 테스트
- 미인증 리다이렉트 테스트

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
