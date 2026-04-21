# Task ID

TASK-FE-015

# Title

admin-dashboard 기반 구성 및 상품 관리 기능 구현 — 인증 가드, 레이아웃 셸, 공유 컴포넌트, 상품 CRUD

# Status

ready

# Owner

frontend

# Task Tags

- code
- api

# Goal

admin-dashboard 애플리케이션의 기반 인프라(인증 가드, 관리자 레이아웃, 공유 UI 컴포넌트, React Query 설정)를 구성하고, 첫 번째 관리 기능으로 상품 관리(목록 조회, 상세 조회, 등록, 수정, 재고 조정)를 구현한다.

# Scope

## In Scope

- 인증 가드: AuthProvider, AuthGuard 컴포넌트 (미인증 시 로그인 페이지로 리다이렉트)
- 로그인 페이지: admin-dashboard 전용 로그인 폼
- Providers 구성: QueryClientProvider, AuthProvider 래핑
- 관리자 레이아웃 셸: 사이드바 내비게이션, 헤더
- 공유 UI 컴포넌트: DataTable, PageLayout, StatusBadge, LoadingSpinner, ErrorMessage, EmptyState, ConfirmDialog, FilterBar
- 공유 설정: apiClient 인스턴스 생성
- product-management 피처: 상품 목록(DataTable + 필터 + 페이지네이션), 상품 상세, 상품 등록/수정 폼, 재고 조정
- 테스트: 공유 컴포넌트 테스트, 피처 컴포넌트 테스트, 훅 테스트

## Out of Scope

- order-management 피처
- user-management 피처
- dashboard 통계 피처
- E2E 테스트
- 역할 기반 접근 제어 (관리자 역할 검증)
- SSR/SSG (모든 페이지 CSR)

# Acceptance Criteria

- [ ] 미인증 사용자가 admin-dashboard 접근 시 로그인 페이지로 리다이렉트
- [ ] 로그인 성공 시 대시보드 페이지로 이동
- [ ] 사이드바에서 상품 관리 메뉴 클릭 시 상품 목록 페이지 표시
- [ ] 상품 목록에서 페이지네이션, 상태 필터가 동작
- [ ] 상품 등록 폼에서 필수 필드 검증 후 API 호출
- [ ] 상품 수정 시 기존 데이터가 폼에 채워지고 수정 API 호출
- [ ] 재고 조정 기능이 상품 상세 페이지에서 동작
- [ ] 로딩/에러/빈 상태가 모든 페이지에 표시
- [ ] 모든 공유 UI 컴포넌트에 테스트 존재
- [ ] 상품 관리 피처 컴포넌트 및 훅에 테스트 존재

# Related Specs

- `specs/services/admin-dashboard/architecture.md`
- `specs/platform/coding-rules.md`
- `specs/platform/naming-conventions.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature.md`
- `.claude/skills/frontend/api-client.md`
- `.claude/skills/frontend/state-management.md`
- `.claude/skills/frontend/form-handling.md`
- `.claude/skills/frontend/loading-error-handling.md`
- `.claude/skills/frontend/testing-frontend.md`

# Related Contracts

- `specs/contracts/http/product-api.md`
- `specs/contracts/http/auth-api.md`

# Target App

- `apps/admin-dashboard`

# Implementation Notes

- admin-dashboard 아키텍처: Layered by Feature (FSD가 아님)
- 모든 페이지는 `'use client'` — SSR/SSG 불필요
- 인증: web-store의 AuthProvider 패턴 재사용 (JWT + localStorage)
- 상품 관리 API: `@repo/api-client`의 createProductApi 활용 (admin 엔드포인트 포함)
- 폼 관리: 직접 useState로 관리 (react-hook-form 미설치 상태)
- URL 상태 동기화: useSearchParams로 필터/페이지네이션 상태 관리
- 기존 스켈레톤 페이지 교체 필요
- vitest 미설치 — devDependencies에 추가 필요

# Edge Cases

- 빈 상품 목록
- 로딩 중 상태
- 토큰 만료 시 자동 리다이렉트
- 상품 등록 시 필수 필드 누락
- 재고 조정 시 음수 값 입력
- 네트워크 오류 시 에러 메시지 표시

# Failure Scenarios

- API 서버 연결 불가
- 인증 토큰 만료 / 무효
- 상품 등록/수정 API 실패 (validation error)
- 재고 조정 실패 (insufficient stock)
- 페이지네이션 범위 초과

# Test Requirements

- 공유 UI 컴포넌트 테스트 (DataTable, PageLayout, StatusBadge, ConfirmDialog 등)
- product-management 컴포넌트 테스트 (ProductList, ProductDetail, ProductForm)
- product-management 훅 테스트 (useProducts, useProduct, useCreateProduct 등)
- AuthGuard 동작 테스트

# Definition of Done

- [ ] 인증 가드 및 로그인 페이지 구현
- [ ] 관리자 레이아웃 셸 구현
- [ ] 공유 UI 컴포넌트 구현
- [ ] 상품 관리 피처 구현 (목록/상세/등록/수정/재고 조정)
- [ ] API 연동 완료
- [ ] 로딩/에러/빈 상태 처리
- [ ] 테스트 추가 및 통과
- [ ] 리뷰 준비 완료
