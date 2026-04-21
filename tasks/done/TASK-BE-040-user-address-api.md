# Task ID

TASK-BE-040

# Title

배송 주소 관리 API — 목록 조회, 추가, 수정, 삭제

# Status

review

# Owner

backend

# Task Tags

- code
- api

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

인증된 사용자가 자신의 배송 주소를 관리(조회, 추가, 수정, 삭제)할 수 있는 API를 구현한다.

이 태스크 완료 후: GET /api/users/me/addresses, POST /api/users/me/addresses, PATCH /api/users/me/addresses/{addressId}, DELETE /api/users/me/addresses/{addressId} API가 동작한다.

---

# Scope

## In Scope

- `GET /api/users/me/addresses` — 주소 목록 조회
- `POST /api/users/me/addresses` — 주소 추가
- `PATCH /api/users/me/addresses/{addressId}` — 주소 수정
- `DELETE /api/users/me/addresses/{addressId}` — 주소 삭제
- 기본 주소(isDefault) 지정 로직
- 주소 수 제한 (최대 10개)
- 입력 유효성 검증

## Out of Scope

- 주소 자동완성/검색 (외부 API 연동)
- 관리자용 주소 관리 API
- 주문 시 주소 복사 로직 (order-service 책임)

---

# Acceptance Criteria

- [ ] `GET /api/users/me/addresses` — 사용자의 전체 주소 목록 반환 (200)
- [ ] `POST /api/users/me/addresses` — 주소 생성 (201), id 반환
- [ ] `POST /api/users/me/addresses` — 필수 필드 누락 시 400 VALIDATION_ERROR
- [ ] `POST /api/users/me/addresses` — 10개 초과 시 422 ADDRESS_LIMIT_EXCEEDED
- [ ] `POST /api/users/me/addresses` — isDefault=true 시 기존 기본 주소 해제
- [ ] 첫 번째 주소 추가 시 자동으로 isDefault=true 설정
- [ ] `PATCH /api/users/me/addresses/{addressId}` — 부분 수정 (200)
- [ ] `PATCH /api/users/me/addresses/{addressId}` — 존재하지 않는 주소 시 404
- [ ] `PATCH /api/users/me/addresses/{addressId}` — isDefault=true 변경 시 기존 기본 주소 해제
- [ ] `DELETE /api/users/me/addresses/{addressId}` — 삭제 (204)
- [ ] `DELETE /api/users/me/addresses/{addressId}` — 존재하지 않는 주소 시 404
- [ ] `DELETE /api/users/me/addresses/{addressId}` — 기본 주소 삭제 시 다른 주소 존재하면 422 DEFAULT_ADDRESS_CANNOT_BE_DELETED
- [ ] `DELETE /api/users/me/addresses/{addressId}` — 기본 주소이면서 유일한 주소면 삭제 허용
- [ ] 다른 사용자의 주소에 접근 불가 (X-User-Id 기반 소유권 검증)
- [ ] 응답 형식이 계약과 일치한다
- [ ] Controller 테스트, Service 테스트, 통합 테스트가 통과한다

---

# Related Specs

- `specs/services/user-service/architecture.md`
- `specs/platform/error-handling.md`
- `specs/platform/api-gateway-policy.md`
- `specs/platform/security-rules.md`
- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/architecture/layered.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/http/user-api.md`

---

# Target Service

- `user-service`

---

# Architecture

Follow:

- `specs/services/user-service/architecture.md`

계층 배치:
- Presentation: `AddressController`, Request/Response DTO
- Application: `AddressService` — 주소 CRUD 유스케이스
- Domain: `Address` 엔티티, 주소 수 제한 규칙 (TASK-BE-038에서 생성됨)
- Infrastructure: JPA 레포지토리 구현체

---

# Implementation Notes

### 기본 주소 로직

- 새 주소 추가 시 isDefault=true이면 기존 기본 주소를 false로 변경
- 첫 주소는 자동으로 기본 주소
- 기본 주소 삭제 시: 다른 주소가 있으면 거부, 없으면 허용

### 소유권 검증

- 모든 주소 API에서 X-User-Id와 주소의 userId 일치 여부 확인
- 불일치 시 404 반환 (정보 노출 방지)

### 주소 수 제한

- 도메인 레이어에서 검증 (10개 제한)
- 추가 요청 시 기존 주소 수를 조회하여 판단

---

# Edge Cases

- 첫 주소 생성 시 isDefault=false로 요청 → 자동으로 true 설정
- 기본 주소 수정 시 isDefault=false로 변경 시도 → 다른 주소가 없으면 true 유지
- 10개 주소에서 삭제 후 다시 추가 → 허용 (현재 개수 기준)
- 동시에 2개 주소 추가 요청 (경합) → 하나만 성공하거나 둘 다 성공하되 10개 제한 준수
- 다른 사용자의 addressId로 접근 → 404 (ADDRESS_NOT_FOUND)

---

# Failure Scenarios

- DB 저장 실패 → 500 INTERNAL_ERROR
- 동시 수정으로 인한 OptimisticLockException → 재시도 안내 또는 409
- X-User-Id 헤더 누락 → 401 UNAUTHORIZED

---

# Test Requirements

- 단위 테스트: `AddressServiceTest` — CRUD, 기본 주소 로직, 소유권 검증, 수 제한
- Controller 테스트: `AddressControllerTest` — 요청/응답 매핑, 유효성 검증, 에러 응답
- 통합 테스트: 전체 주소 CRUD 흐름 (Testcontainers)
- 통합 테스트: 기본 주소 변경 시 기존 기본 주소 해제 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
