# Task ID

TASK-MONO-023e

# Title

community-service JPA repository 통합 테스트 격리 강화

# Status

ready

# Owner

backend / qa

# Task Tags

- code
- test

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[TASK-MONO-023](../in-progress/TASK-MONO-023-main-baseline-integration-cleanup.md) 의 분류 매트릭스에서 식별된 **community-service JPA repository 2 통합 테스트** 의 격리 회귀를 fix:

| 테스트 클래스 | 증상 |
|---|---|
| `community.CommentJpaRepositoryIntegrationTest` | count 2 vs 1 — 다른 테스트의 댓글 행이 누수 |
| `community.ReactionJpaRepositoryIntegrationTest` | "Expecting code to raise throwable" — 이전 테스트의 reaction row 가 unique constraint 를 회피시켜 throwable 미발생 |

이 태스크 완료 후 위 2 테스트가 단일 + 5회 연속 PASS.

community-service 는 frozen demo 라 신규 기능은 추가하지 않지만, 회귀 fix (TASK-BE-021 등 review 시 발견된 fix) 는 수용 가능.

---

# Scope

## In Scope

- 두 테스트의 `@BeforeEach` / `@AfterEach` 검토 — `repo.deleteAll()` 또는 `@DirtiesContext` 적용
- 테스트 fixture 의 unique ID 사용 강화 (이미 [PR #107 의 fix](https://github.com/kanggle/monorepo-lab/pull/107) 에서 prefix-UUID 패턴 도입했으나 잔여 격리 문제)
- `@DataJpaTest` 가 자동 트랜잭션 롤백을 제공하는지 확인 — 일반 `@SpringBootTest` 라면 수동 cleanup 필요
- 5회 연속 PASS 검증

## Out of Scope

- 다른 카테고리 회귀 — TASK-MONO-023a/b/c/d
- community-service 의 신규 기능 (frozen 정책)
- 다른 community 통합 테스트 (PR #107 에서 이미 fix 된 prefix-UUID 패턴은 무관)

---

# Acceptance Criteria

- [ ] `CommentJpaRepositoryIntegrationTest` PASS (단일 + 5회 연속, 임의 순서)
- [ ] `ReactionJpaRepositoryIntegrationTest` PASS (단일 + 5회 연속, 임의 순서)
- [ ] cleanup 패턴이 명시적 (`@AfterEach` 또는 `@DirtiesContext`) — implicit transaction rollback 의존 안 함

---

# Related Specs

- `projects/global-account-platform/specs/services/community-service/data-model.md` § Reaction (계정×포스트 unique)
- `projects/global-account-platform/specs/services/community-service/architecture.md`

---

# Related Contracts

해당 없음 (테스트 격리 작업).

---

# Target Service / Component

- `projects/global-account-platform/apps/community-service/src/test/java/com/example/community/integration/CommentJpaRepositoryIntegrationTest.java`
- `projects/global-account-platform/apps/community-service/src/test/java/com/example/community/integration/ReactionJpaRepositoryIntegrationTest.java`

---

# Implementation Notes

- 두 테스트는 `@SpringBootTest` 일 가능성 높음 (full context). `@DataJpaTest` 로 슬라이스화하면 자동 롤백 + 가벼움.
- `@DataJpaTest` 가 어렵다면 `@AfterEach` 에서 `commentJpaRepository.deleteAll()`, `reactionJpaRepository.deleteAll()` 명시 호출
- ReactionJpaRepository 의 unique constraint (account×post) 위반 테스트는 이전 테스트의 reaction row 가 남아있으면 unique 로 거절되지 않음 → cleanup 필수
- 5회 연속 PASS 명령:
  ```
  for i in {1..5}; do
    ./gradlew :apps:community-service:integrationTest --tests "CommentJpaRepositoryIntegrationTest" --rerun-tasks
  done
  ```

---

# Edge Cases

- 다른 community 테스트의 fixture 가 같은 cleanup 영향 → 격리 강화 후 cascade 영향 분석
- frozen 정책: 격리 fix 는 회귀 fix 로 허용 (FROZEN 예외 — overview.md L3 명시)

---

# Failure Scenarios

- cleanup 추가가 다른 테스트의 setup 데이터를 의도치 않게 삭제 → 영향 범위 축소 (특정 entity 만 deleteAll)

---

# Test Requirements

- 2 통합 테스트 단일 + 5회 연속 PASS
- 임의 실행 순서에서도 PASS (--tests 단일 실행, --tests 두 클래스 동시, 전체 클래스 셋 등)

---

# Definition of Done

- [ ] 2 통합 테스트 PASS (단일 + 5회 연속)
- [ ] cleanup 패턴 명시화
- [ ] frozen 정책 준수 (신규 기능 없음, 회귀 fix 만)
- [ ] Ready for review
