# Task ID

TASK-FE-010

# Title

웹스토어 클라이언트 데이터 페칭 React Query 리팩토링

# Status

ready

# Owner

frontend

# Task Tags

- code

# Goal

웹스토어의 클라이언트 사이드 데이터 페칭을 수동 useState/useEffect 패턴에서 React Query(TanStack Query)로 전환하여 캐싱, 자동 재시도, 로딩/에러 상태 관리를 표준화한다. UI 변경 없이 기능만 리팩토링한다.

# Scope

## In Scope

- @tanstack/react-query 설치 및 QueryClientProvider 설정
- feature별 query-keys.ts 생성 (order, user, checkout)
- 주문 feature: useOrders, useOrderDetail, useCancelOrder 훅 React Query 전환
- 사용자 feature: useAddresses, useProfile, useUpdateProfile 훅 React Query 전환
- 결제 확인: useConfirmPayment mutation 전환
- 체크아웃: CheckoutForm 주소 로딩 React Query 전환
- 컴포넌트 업데이트 (OrderHistory, OrderDetailView, AddressManager, ProfileLoader, CheckoutForm, PaymentSuccess)
- barrel export (index.ts) 업데이트

## Out of Scope

- UI/CSS 변경
- 서버 컴포넌트 데이터 페칭 변경 (SSR 페이지)
- Cart Context (클라이언트 로컬 상태 — React Query 대상 아님)
- Auth Context (토큰 기반 인증 — React Query 대상 아님)
- Mock fallback 제거 (별도 태스크)
- API 엔드포인트 변경

# Acceptance Criteria

- [ ] React Query가 설치되고 QueryClientProvider가 providers.tsx에 설정됨
- [ ] 모든 클라이언트 사이드 서버 데이터 페칭이 useQuery/useMutation 사용
- [ ] feature별 query-keys.ts가 존재하고 일관된 키 구조 사용
- [ ] 기존 로딩/에러/빈 상태 동작이 동일하게 유지됨
- [ ] UI가 변경되지 않음
- [ ] 빌드 성공
- [ ] 기존 테스트 통과

# Related Specs

- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`
- `.claude/skills/frontend/state-management.md`
- `.claude/skills/frontend/api-client.md`
- `.claude/skills/frontend/loading-error-handling.md`

# Related Contracts

- N/A (API 변경 없음)

# Target App

- `apps/web-store`

# Implementation Notes

- FSD 아키텍처의 model/ 디렉토리에 훅 배치 (기존 패턴 유지)
- query-keys.ts는 model/ 디렉토리에 생성
- 서버 컴포넌트 페이지 (products, homepage)는 변경하지 않음
- Cart와 Auth는 클라이언트 로컬 상태이므로 Context 유지

# Edge Cases

- React Query 캐시와 기존 동작의 일관성
- 페이지네이션 파라미터 변경 시 쿼리 무효화
- 결제 확인 중복 호출 방지 (기존 ref guard → mutation으로 대체)

# Failure Scenarios

- API 에러 시 React Query의 기본 재시도 동작
- 네트워크 오프라인 시 캐시 데이터 표시

# Test Requirements

- 기존 테스트가 React Query 모킹으로 업데이트되어 통과
- 빌드 성공

# Definition of Done

- [ ] React Query 설치 및 Provider 설정
- [ ] query-keys.ts 생성
- [ ] 훅 리팩토링 완료
- [ ] 컴포넌트 업데이트 완료
- [ ] 빌드 성공
- [ ] 테스트 통과
