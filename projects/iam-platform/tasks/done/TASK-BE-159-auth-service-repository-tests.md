---
id: TASK-BE-159
title: auth-service CredentialJpaRepository / SocialIdentityJpaRepository 슬라이스 테스트
status: ready
priority: medium
assignee: ""
---

## Goal

auth-service 내 미커버 JPA 리포지토리 2개에 대한 쿼리 슬라이스 테스트를 작성하여
`findByAccountId`, `findByEmail`, `findByProviderAndProviderUserId` 등의 파생 쿼리가
실제 MySQL 스키마에서 올바르게 동작함을 검증한다.

## Scope

대상 리포지토리:
- `CredentialJpaRepository` — findByAccountId, findByEmail
- `SocialIdentityJpaRepository` — findByProviderAndProviderUserId, findByAccountId

## Acceptance Criteria

- [ ] 2개 클래스 각각 `@DataJpaTest + @Testcontainers + @EnabledIf("isDockerAvailable")` 패턴 준수
- [ ] `withDatabaseName("auth_db")` + Flyway 마이그레이션으로 실제 스키마 적용
- [ ] `findByEmail` 검증: nullable email 컬럼(V0006 추가) 있음/없음 케이스
- [ ] `findByProviderAndProviderUserId` 복합 조건 조회 검증
- [ ] `findByAccountId` 단건 및 복수 SocialIdentity 반환 검증
- [ ] 테스트 메서드 네이밍: `{scenario}_{condition}_{expectedResult}`
- [ ] `@DisplayName` 한국어 비즈니스 설명
- [ ] `compileTestJava` 성공

## Related Specs

- `specs/services/auth-service/architecture.md`

## Related Contracts

없음 (DB 내부 쿼리 검증)

## Edge Cases

- `findByEmail` — email이 null인 credential 행 (V0006 이전 데이터)
- `findByAccountId` (SocialIdentity) — 동일 accountId에 복수 provider 연결 케이스
- `findByProviderAndProviderUserId` — provider는 같지만 providerUserId가 다른 경우

## Failure Scenarios

- Docker 미사용 환경: `@EnabledIf("isDockerAvailable")` 로 자동 스킵
- `credentials.account_id` UNIQUE 제약 — 동일 accountId로 중복 삽입 시 실패 → 테스트마다 UUID 사용
