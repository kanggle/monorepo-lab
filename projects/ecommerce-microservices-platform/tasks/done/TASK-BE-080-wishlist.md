# Task ID

TASK-BE-080

# Title

위시리스트/찜 기능 — 사용자별 관심 상품 목록 관리

# Status

done

# Owner

backend

# Task Tags

- code
- api

---

# Goal

사용자가 관심 있는 상품을 위시리스트에 추가/제거하고 목록을 조회할 수 있는 기능을 구현한다.

---

# Scope

## In Scope

- 위시리스트 추가/제거 API
- 위시리스트 목록 조회 API (페이지네이션)
- 위시리스트 상품 재고/가격 변동 시 알림 연동 포인트
- user-service에 구현 (확장)

## Out of Scope

- 위시리스트 공유 기능
- 위시리스트 기반 추천
- 프론트엔드 UI (별도 FE 태스크)

---

# Acceptance Criteria

- [ ] 위시리스트에 상품 추가 API 동작
- [ ] 위시리스트에서 상품 제거 API 동작
- [ ] 위시리스트 목록 조회 API 동작 (페이지네이션 포함)
- [ ] 동일 상품 중복 추가 방지
- [ ] 삭제된 상품 위시리스트 처리

---

# Related Specs

- `specs/platform/architecture-decision-rule.md`
- `specs/platform/service-boundaries.md`
- `specs/services/user-service/architecture.md`
- `specs/services/user-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- `specs/contracts/http/wishlist-api.md`
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/user-api.md`

---

# Target Service

- `user-service` (확장)

---

# Architecture

- Layered Architecture (follows user-service, see `specs/services/user-service/architecture.md`)

---

# Implementation Notes

- 삭제된 상품은 wishlist에서 productStatus: "DELETED"로 표시 (soft handling)
- 상품 정보는 product-service에서 조회 시 fetch 또는 로컬 캐시

---

# Edge Cases

- 위시리스트에 담긴 상품이 삭제/품절된 경우
- 사용자 탈퇴 시 위시리스트 데이터 정리
- 대량 위시리스트 (상한 필요 여부)

---

# Failure Scenarios

- product-service 장애 시 상품 정보 조회 불가
- 동시 추가/제거 요청

---

# Test Requirements

- unit test
- integration test

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
