# Task ID

TASK-FE-027

# Title

web-store 메모리 누수 수정 및 에러 핸들링 개선 — setTimeout cleanup, localStorage 에러 처리

# Status

review

# Owner

frontend

# Task Tags

- code
- test

# Goal

web-store의 AddToCartButton에서 setTimeout cleanup 미처리로 인한 메모리 누수를 수정하고, CartContext의 localStorage 에러 핸들링을 개선한다.

현재 상태:
1. AddToCartButton: `setTimeout(() => setAdded(false), 1500)` 에서 컴포넌트 언마운트 시 cleanup 미수행
2. CartContext: localStorage 접근 시 빈 catch 블록으로 에러가 무시됨
3. API 클라이언트 토큰 리프레시 중 isRefreshing 플래그가 stuck 될 수 있는 timeout 미설정

# Scope

## In Scope

- AddToCartButton의 setTimeout에 useRef/useEffect cleanup 적용
- CartContext의 localStorage 에러 시 사용자에게 알림 (console.warn + fallback)
- api-client의 토큰 리프레시에 timeout 추가 (10초)

## Out of Scope

- httpOnly 쿠키로 토큰 저장 방식 전환
- Service Worker 기반 캐싱
- CSP(Content Security Policy) 헤더 설정

# Acceptance Criteria

- [ ] AddToCartButton 언마운트 시 setTimeout이 정리된다
- [ ] CartContext의 localStorage 접근 실패 시 console.warn 로그가 남고 빈 카트로 fallback 된다
- [ ] api-client 토큰 리프레시에 10초 timeout이 적용되며, timeout 시 isRefreshing이 false로 복원된다
- [ ] 기존 테스트가 통과한다

# Related Specs

- `specs/services/web-store/architecture.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`

# Related Contracts

_(없음 — UI 내부 코드)_

# Target App

- `apps/web-store`
- `packages/api-client`

# Implementation Notes

- AddToCartButton: `useEffect` 내에서 `setTimeout` 호출 후 cleanup 함수에서 `clearTimeout` 반환
- CartContext: `try { JSON.parse(localStorage.getItem(...)) } catch (e) { console.warn('Cart load failed', e); return []; }`
- api-client: `AbortController` 또는 `Promise.race([refreshCall, timeoutPromise])` 사용

# Edge Cases

- AddToCartButton 빠른 연속 클릭 시 여러 timeout이 설정되는 경우
- localStorage quota 초과
- 토큰 리프레시 중 네트워크 변경 (온라인↔오프라인)

# Failure Scenarios

- localStorage 완전 비활성화 환경 (private browsing 일부 브라우저)
- 리프레시 토큰 만료 + timeout 동시 발생
- JSON 파싱 에러 (손상된 localStorage 데이터)

# Test Requirements

- AddToCartButton 언마운트 시 state update 없음 테스트
- CartContext localStorage 에러 시 fallback 테스트
- api-client 리프레시 timeout 테스트

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
