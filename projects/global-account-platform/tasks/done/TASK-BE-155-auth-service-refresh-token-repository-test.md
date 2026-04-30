# TASK-BE-155 — auth-service RefreshTokenJpaRepository 슬라이스 테스트 추가

## Goal

`RefreshTokenJpaRepository`는 `@Modifying` Bulk UPDATE 2개 + 활성 JTI 조회 2개 +
rotation chain 관련 쿼리를 보유하지만 전용 슬라이스 테스트가 없음.
real MySQL Testcontainer로 각 쿼리의 정확성을 검증한다.

## Scope

**추가 파일 (1개)**

| # | 경로 |
|---|------|
| 1 | `apps/auth-service/src/test/.../infrastructure/persistence/RefreshTokenJpaRepositoryTest.java` |

**컨벤션**: 기존 `DeviceSessionJpaRepositoryTest` 패턴 —
`@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` + `@Testcontainers` + `@EnabledIf("isDockerAvailable")`.

**변경 없음**: 프로덕션 코드, 계약서, 스펙.

## Acceptance Criteria

- [ ] `findByJti_existing_returnsToken` — jti 로 토큰 조회
- [ ] `findByJti_unknown_returnsEmpty` — 없는 jti → empty
- [ ] `existsByRotatedFrom_existingChain_returnsTrue` — rotation chain 존재 확인
- [ ] `existsByRotatedFrom_noChain_returnsFalse` — chain 없음 → false
- [ ] `findByRotatedFrom_existingChain_returnsToken` — rotation chain 토큰 조회
- [ ] `revokeAllByAccountId_revokesActiveOnly` — @Modifying: 활성 토큰만 revoke, 이미 revoked 는 건드리지 않음
- [ ] `findActiveJtisByAccountId_excludesRevoked` — revoked 행 제외 active JTI 목록
- [ ] `findActiveJtisByDeviceId_excludesRevoked` — device 단위 active JTI 목록
- [ ] `revokeAllByDeviceId_revokesActiveOnly` — @Modifying: device 단위 bulk revoke

- [ ] `./gradlew :apps:auth-service:compileTestJava` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:auth-service:test` BUILD SUCCESSFUL (Docker 없으면 SKIP)

## Related Specs

- `specs/services/auth-service/architecture.md` — Testing Expectations

## Related Contracts

없음 (테스트 전용)

## Edge Cases

- `@Modifying` 쿼리: `clearAutomatically` 기본값(false) — flush 후 L1 캐시 stale 가능성. 검증은 재조회 방식으로
- `revoked=false` 조건: 이미 revoked 된 행은 카운트/결과에서 제외됨을 양쪽 모두 검증
- rotation chain: `rotatedFrom` nullable — null 인 행에 `existsByRotatedFrom` 는 false 반환

## Failure Scenarios

- Docker 미설치 → `@EnabledIf("isDockerAvailable")` SKIP
- `@Modifying` + `@Transactional` 없으면 `InvalidDataAccessApiUsageException` — `@DataJpaTest` 가 자동 제공
