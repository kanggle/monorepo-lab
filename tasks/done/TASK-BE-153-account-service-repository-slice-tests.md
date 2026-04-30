# TASK-BE-153 — account-service 리포지토리 슬라이스 테스트 추가

## Goal

`specs/services/account-service/architecture.md`의 Testing Expectations 중
"Repository slice" 레이어가 현재 0개(`@DataJpaTest` 파일 없음). 핵심 JPA 쿼리와
DB 트리거(append-only 보장)를 real MySQL Testcontainer로 검증한다.

## Scope

**추가 파일 (2개)**

| # | 경로 | 대상 레포지토리 |
|---|------|----------------|
| 1 | `apps/account-service/src/test/…/infrastructure/persistence/AccountJpaRepositoryTest.java` | `AccountJpaRepository` |
| 2 | `apps/account-service/src/test/…/infrastructure/persistence/AccountStatusHistoryJpaRepositoryTest.java` | `AccountStatusHistoryJpaRepository` |

**변경 없음**: 프로덕션 코드, 계약서, 스펙.

## Acceptance Criteria

### AccountJpaRepositoryTest
- [ ] `findByEmail_existing_returnsAccount` — 이메일로 계정 조회 성공
- [ ] `existsByEmail_existingEmail_returnsTrue` — 이메일 존재 확인
- [ ] `existsByEmail_unknownEmail_returnsFalse` — 없는 이메일 → false
- [ ] `findActiveDormantCandidates_pastThreshold_returnsActiveAccounts` — lastLoginSucceededAt < threshold 인 ACTIVE 계정 반환
- [ ] `findActiveDormantCandidates_recentLogin_excludes` — 최근 로그인 계정 제외
- [ ] `findActiveDormantCandidates_nullLastLogin_usesCreatedAt` — null lastLoginSucceededAt → createdAt 사용 (COALESCE 검증)
- [ ] `findAnonymizationCandidates_deletedPastGrace_unmaskedProfile_returnsEligible` — grace 기간 경과 + maskedAt=null → 반환
- [ ] `findAnonymizationCandidates_recentlyDeleted_excludes` — grace 기간 미경과 → 제외

### AccountStatusHistoryJpaRepositoryTest
- [ ] `findByAccountIdOrderByOccurredAtDesc_multipleEntries_returnsDescOrder` — 최신 순 정렬 검증
- [ ] `findTopByAccountIdOrderByOccurredAtDesc_returnsLatestEntry` — 가장 최신 단일 항목
- [ ] `appendOnlyTrigger_update_throwsException` — DB 트리거 UPDATE 거부 검증 (A3)
- [ ] `appendOnlyTrigger_delete_throwsException` — DB 트리거 DELETE 거부 검증 (A3)

- [ ] `./gradlew :apps:account-service:compileTestJava` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:account-service:test` BUILD SUCCESSFUL (Docker 없으면 SKIP)

## Related Specs

- `specs/services/account-service/architecture.md` — Testing Expectations, 필수 시나리오

## Related Contracts

없음 (테스트 전용)

## Edge Cases

- `findActiveDormantCandidates`: COALESCE(lastLoginSucceededAt, createdAt) — null 값을 JDBC로 직접 세팅해야 검증 가능
- `findAnonymizationCandidates`: LEFT JOIN profiles — 프로파일 없는 계정도 포함 대상 (OR p.accountId IS NULL 조건)
- Trigger 테스트: `JdbcTemplate.update()` 시 MySQL SIGNAL → `DataAccessException` wrapping 확인

## Failure Scenarios

- Docker 미설치 → `@EnabledIf("isDockerAvailable")` SKIP
- JDBC INSERT 타임스탬프 정밀도 — `DATETIME(6)` 컬럼이므로 마이크로초 단위 저장 가능; `Instant` 비교 시 truncation 주의
