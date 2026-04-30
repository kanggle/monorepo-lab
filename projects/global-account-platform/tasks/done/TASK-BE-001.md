# Task ID

TASK-BE-001

# Title

OperatorAdminUseCase CQRS 분리 — 커맨드/쿼리 유스케이스 분리

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`admin-service`의 `OperatorAdminUseCase`(513 LOC)가 커맨드(create, patchRole, patchStatus)와 쿼리(getCurrent, listOperators)를 모두 담당하고 있다.
account-service에서 이미 채택한 CQRS 패턴(SignupUseCase vs. AccountSearchQueryService)에 맞게 분리하여 단일 책임 원칙을 따르게 한다.

완료 후 `OperatorAdminUseCase`는 제거되고 아래 클래스로 대체된다:
- `CreateOperatorUseCase` — 운영자 생성
- `PatchOperatorRoleUseCase` — 역할 변경
- `PatchOperatorStatusUseCase` — 상태 변경
- `OperatorQueryService` — 현재 운영자 조회, 목록 조회

---

# Scope

## In Scope

- `OperatorAdminUseCase` 분리 및 삭제
- 분리된 4개 클래스 생성 (application 레이어 유지)
- 이를 주입받는 Controller 수정
- 단위 테스트 작성/이전

## Out of Scope

- API 계약(HTTP 엔드포인트) 변경 없음
- DB 스키마 변경 없음
- 다른 서비스 영향 없음

---

# Acceptance Criteria

- [ ] `OperatorAdminUseCase` 클래스가 삭제된다
- [ ] `CreateOperatorUseCase`, `PatchOperatorRoleUseCase`, `PatchOperatorStatusUseCase`, `OperatorQueryService` 4개 클래스가 생성된다
- [ ] Controller가 분리된 클래스를 올바르게 주입받는다
- [ ] 기존 동작이 변경되지 않는다 (행위 동등성)
- [ ] 각 클래스에 단위 테스트가 존재한다
- [ ] 빌드 및 테스트 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/overview.md`
- `platform/service-types/rest-api.md`

# Related Skills

- `.claude/skills/backend/architecture/layered/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — API 계약 변경 없음

---

# Target Service

- `admin-service`

---

# Architecture

Follow:

- `specs/services/admin-service/architecture.md`
- Thin Layered (Command Gateway): presentation / application / infrastructure

---

# Implementation Notes

- `OperatorAdminUseCase`의 메서드를 목적별로 분리. 공유 의존성(repository, event publisher 등)은 각 클래스에 개별 주입.
- `OperatorQueryService`는 `@Transactional(readOnly = true)` 적용.
- 커맨드 유스케이스는 `@Transactional` 유지.
- 기존 테스트 로직을 해당 클래스 테스트로 이전하고 불필요한 중복 제거.

---

# Edge Cases

- 운영자가 자기 자신의 상태를 변경하려는 경우 — 기존 검증 로직 유지
- 마지막 SUPER_ADMIN 삭제 방지 로직 — `PatchOperatorStatusUseCase`로 이전
- 동시 수정 시 OptimisticLockException — 기존 처리 유지

---

# Failure Scenarios

- Controller에서 주입 누락 시 `NoSuchBeanDefinitionException` → Spring 컨텍스트 로드 테스트로 감지
- 트랜잭션 경계 실수로 쿼리 유스케이스에 write 발생 → `readOnly = true` 위반 시 예외

---

# Test Requirements

- 각 커맨드 유스케이스 단위 테스트 (mockito)
- `OperatorQueryService` 단위 테스트
- Controller 슬라이스 테스트 (`@WebMvcTest`) — 기존 유지

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
