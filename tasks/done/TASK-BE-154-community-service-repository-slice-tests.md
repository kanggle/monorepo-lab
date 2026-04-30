# TASK-BE-154 — community-service 리포지토리 슬라이스 테스트 추가

## Goal

`specs/services/community-service/architecture.md`의 Testing Expectations 중
"Repository slice" 레이어가 일부 누락. `PostJpaRepository`/`FeedSubscriptionJpaRepository` 는
이미 통합 테스트로 검증되지만 `CommentJpaRepository`, `ReactionJpaRepository`,
`PostStatusHistoryJpaRepository` 가 0개. 핵심 JPA 쿼리, 복합 PK 제약,
DB 트리거(append-only)를 real MySQL Testcontainer 로 검증한다.

## Scope

**추가 파일 (3개)**

| # | 경로 | 대상 레포지토리 |
|---|------|----------------|
| 1 | `apps/community-service/src/test/.../integration/CommentJpaRepositoryIntegrationTest.java` | `CommentJpaRepository` |
| 2 | `apps/community-service/src/test/.../integration/ReactionJpaRepositoryIntegrationTest.java` | `ReactionJpaRepository` |
| 3 | `apps/community-service/src/test/.../integration/PostStatusHistoryJpaRepositoryIntegrationTest.java` | `PostStatusHistoryJpaRepository` |

**컨벤션**: 기존 `PostRepositoryIntegrationTest` 패턴 — `@SpringBootTest` + `CommunityIntegrationTestBase`
(JVM-shared MySQL + Kafka). UUID 기반 ID 격리.

**변경 없음**: 프로덕션 코드, 계약서, 스펙.

## Acceptance Criteria

### CommentJpaRepositoryIntegrationTest
- [ ] `countByPostIdAndDeletedAtIsNull_includesActiveExcludesDeleted` — soft-delete 댓글 제외 카운트
- [ ] `countsGroupedByPostId_returnsPerPostCounts` — 여러 포스트 그룹별 카운트
- [ ] `countsGroupedByPostId_excludesSoftDeleted` — soft-delete 행 제외 그룹 카운트

### ReactionJpaRepositoryIntegrationTest
- [ ] `findByPostIdAndAccountId_existing_returnsReaction` — 복합 PK 조회
- [ ] `findByPostIdAndAccountId_unknown_returnsEmpty` — 없는 키 → empty
- [ ] `compositeKey_uniqueConstraint_secondInsertFails` — (post_id, account_id) 중복 INSERT 거부
- [ ] `countByPostId_returnsAccurateCount` — 단일 포스트 카운트
- [ ] `countsGroupedByPostId_returnsPerPostCounts` — 그룹별 카운트

### PostStatusHistoryJpaRepositoryIntegrationTest
- [ ] `findByPostIdOrderByOccurredAtAsc_returnsAscOrder` — 시간 오름차순 정렬
- [ ] `appendOnlyTrigger_update_throwsException` — UPDATE 트리거 거부
- [ ] `appendOnlyTrigger_delete_throwsException` — DELETE 트리거 거부

- [ ] `./gradlew :apps:community-service:compileTestJava` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:community-service:test` BUILD SUCCESSFUL (Docker 없으면 SKIP)

## Related Specs

- `specs/services/community-service/architecture.md` — Testing Expectations

## Related Contracts

없음 (테스트 전용)

## Edge Cases

- `Reaction` 복합 PK: `IdClass(ReactionId)` — 중복 INSERT 시 `DataIntegrityViolationException` 래핑 확인
- `post_status_history` 트리거: `JdbcTemplate.update(...)` → MySQL `SIGNAL '45000'` → `DataAccessException`
- 그룹 카운트: 빈 `Collection<String>` 인풋은 본 태스크 범위 밖 (현재 구현이 처리 안 함)

## Failure Scenarios

- Docker 미설치 → `CommunityIntegrationTestBase.isDockerAvailable()` SKIP
- JVM-shared MySQL 사용 시 테스트 간 데이터 누수 — UUID 기반 ID 로 격리
- Reaction 복합 PK INSERT 실패 시 트랜잭션 롤백 필요 — `TransactionTemplate` 명시적 사용
