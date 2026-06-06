# Task ID

TASK-BE-040-fix2-missing-unit-tests

# Title

admin-service — AdminRefreshTokenService / AdminLogoutService 단위 테스트 + clearAutomatically 회귀 테스트 추가

# Status

ready

# Owner

backend

# Task Tags

- test

# depends_on

- TASK-BE-040-fix-port-boundary-and-signed-operator-id

---

# Goal

TASK-BE-040-fix 리뷰에서 발견된 Warning 2건을 해소한다:

1. `AdminRefreshTokenService`, `AdminLogoutService`에 대한 **port stub 기반 단위 테스트** 부재 — 태스크 AC "단위 테스트: port stub으로 서비스 테스트 가능함을 증빙" 미충족.
2. `@Modifying(clearAutomatically = true)` 적용에 대한 **회귀 테스트** 부재 — 태스크 AC "`@Modifying clearAutomatically` 회귀 — 동일 tx에서 revoke 후 read 일관성" 미충족.

---

# Scope

## In Scope

### Test 1 — AdminRefreshTokenService 단위 테스트
- `AdminRefreshTokenServiceTest` 신설 (`application/` 계층, Mockito)
- `AdminRefreshTokenPort` / `OperatorLookupPort` / `AdminRefreshTokenIssuer` / `JwtVerifier` mock 사용
- 커버 시나리오:
  - 정상 rotation → `RefreshResult.operatorId` 포함 확인
  - jti 미등록 → `InvalidRefreshTokenException`
  - operator sub 불일치 → `InvalidRefreshTokenException`
  - 재사용 탐지 → `revokeAllForOperator` 호출 + `RefreshTokenReuseDetectedException(operatorId)` throw 확인

### Test 2 — AdminLogoutService 단위 테스트
- `AdminLogoutServiceTest` 신설 (`application/` 계층, Mockito)
- 커버 시나리오:
  - 유효한 refresh JWT + operator 일치 → `tokenPort.revoke(LOGOUT)` 호출 확인
  - refresh JWT null → revoke 미호출
  - operator 불일치 → revoke 미호출

### Test 3 — clearAutomatically 회귀 테스트
- `AdminRefreshTokenJpaAdapterTest` 또는 기존 통합 테스트에서:
  - 동일 트랜잭션 내 `revokeAllForOperator` 이후 `findByJti` 조회 시 stale 엔티티 반환하지 않음을 검증
  - 테스트 전략: `@DataJpaTest` + 인메모리 H2 또는 Testcontainers MySQL

## Out of Scope

- 다른 서비스의 테스트 추가
- 기존 통과 중인 테스트 수정

---

# Acceptance Criteria

- [ ] `AdminRefreshTokenServiceTest` 존재, 4개 시나리오 커버
- [ ] `AdminLogoutServiceTest` 존재, 3개 시나리오 커버
- [ ] `revokeAllForOperator` 이후 동일 tx 내 조회에서 revoked row 반환 검증 테스트 존재
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md` (Testing Expectations)
- `platform/testing-strategy.md`

# Related Contracts

- 없음 (테스트만)

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- JwtVerifier mock: `JwtVerificationException` throw 시나리오 포함
- `clearAutomatically` 테스트: H2 방언에서 JPQL UPDATE 동작 차이 없음 확인

---

# Failure Scenarios

- Testcontainers 사용 시 CI OOM → `@DataJpaTest` + H2 우선 사용

---

# Test Requirements

- 단위: Mockito stub
- JPA 슬라이스: `@DataJpaTest`

---

# Definition of Done

- [ ] 구현 + 테스트 완료
- [ ] Ready for review
