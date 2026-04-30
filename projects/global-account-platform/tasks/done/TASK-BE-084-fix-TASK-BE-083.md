---
id: TASK-BE-084
title: "fix(TASK-BE-083): GET /me 404→401 수정 + N+1 TOTP 쿼리 벌크 처리 + AbstractIntegrationTest 확장"
status: ready
area: backend
service: admin-service
---

## Goal

TASK-BE-083에서 발견된 세 가지 이슈를 수정한다.

1. `GET /api/admin/me` — operator row 미존재 시 `404 OPERATOR_NOT_FOUND` 대신 `401 TOKEN_INVALID` 반환 (스펙 준수)
2. `listOperators()` 내 `bulkLoadEnrolledTotpIds()` — 페이지당 N번 쿼리 대신 IN-bulk 단일 쿼리로 교체 (N+1 제거)
3. `OperatorAdminIntegrationTest` — `AbstractIntegrationTest`를 상속하지 않고 독립 컨테이너를 선언하여 platform/testing-strategy.md 위반; 플랫폼 표준에 맞게 수정

## Background

- TASK-BE-083 구현 리뷰(2026-04-25)에서 발견된 이슈.
- `specs/contracts/http/admin-api.md §GET /api/admin/me` Errors 표는 `401 TOKEN_INVALID`만 정의하며 404는 정의되지 않음.
- `platform/testing-strategy.md` Testcontainers Conventions 절은 "Infrastructure containers that every integration test needs (MySQL, Kafka) must extend `libs/java-test-support/.../AbstractIntegrationTest`"를 명시.
- TOTP 벌크 로드 미구현으로 페이지 크기 N = 조회 횟수 N.

## Scope

1. **`GET /me` 응답 코드 수정**
   - `OperatorAdminUseCase.getCurrentOperator()`: operator row 미존재 시 `OperatorNotFoundException` 대신 `OperatorUnauthorizedException`(이미 존재) throw
   - `AdminExceptionHandler`는 이미 `OperatorUnauthorizedException` → `401 TOKEN_INVALID`를 처리하므로 별도 변경 불필요
   - `OperatorAdminControllerTest.me_returns_current_operator*` 테스트 케이스를 새 예외 기대로 업데이트

2. **N+1 TOTP 쿼리 제거**
   - `AdminOperatorTotpJpaRepository`에 `findByIdIn(Collection<Long> ids)` 메서드 추가
   - `OperatorAdminUseCase.bulkLoadEnrolledTotpIds()`: 루프 제거, IN 쿼리 단일 호출로 교체

3. **통합 테스트 플랫폼 표준 준수**
   - `OperatorAdminIntegrationTest`: `AbstractIntegrationTest` 상속 추가
   - MySQL/Kafka 컨테이너 선언을 클래스에서 제거 (부모에서 상속)
   - Redis 컨테이너는 `OperatorAdminIntegrationTest`가 직접 선언 유지 (서비스별 컨테이너는 서브클래스에서 선언)
   - `withStartupTimeout(Duration.ofMinutes(3))` 추가 (기존 redis 컨테이너에 누락)

## Acceptance Criteria

- [ ] `GET /api/admin/me` — operator row 없을 때 `401 TOKEN_INVALID` 반환
- [ ] `listOperators()` — TOTP 상태 조회가 페이지당 단일 IN 쿼리 1건으로 처리됨
- [ ] `OperatorAdminIntegrationTest` — `AbstractIntegrationTest` 상속, MySQL/Kafka 컨테이너 중복 선언 없음
- [ ] 기존 `OperatorAdminControllerTest`, `OperatorAdminUseCaseTest`, `OperatorAdminIntegrationTest` 전체 통과
- [ ] 전체 admin-service 테스트 통과

## Related Specs

- `specs/contracts/http/admin-api.md` — GET /api/admin/me 에러 정의 (401만)
- `platform/testing-strategy.md` — Testcontainers AbstractIntegrationTest 요건

## Related Contracts

- `specs/contracts/http/admin-api.md`

## Edge Cases

- TOTP row가 하나도 없을 경우: `findByIdIn()`이 빈 리스트 반환 → 정상 처리
- 페이지가 비어 있을 경우: bulkLoad 함수 early-return 유지

## Failure Scenarios

- `findByIdIn()` 쿼리 실패 → 트랜잭션 롤백, 500 반환 (fail-closed)
- AbstractIntegrationTest 상속 누락 → CI 빌드 실패 (컴파일 오류)
