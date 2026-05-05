# Task ID

TASK-MONO-044f-2

# Title

fan-platform e2e 잔존 1건 fix — feed 가 actor 자신의 FAN_POST 를 포함하지 않음

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

[TASK-MONO-044f](../done/TASK-MONO-044f-fan-platform-e2e-application-failures.md) 의 RC#1a (e2e fixture path drift) + RC#1b (gateway RewritePath collection-form) + RC#2 (Hibernate JSONB bind) 5 PR 시리즈로 **4건 fail → 1건 fail** 로 회복. 잔존 1건은 044f 의 RC 와 무관한 별도 application-layer 회귀이며, layered cascade 의 마지막 layer 에서 노출.

CI run `25368457777` (PR #214 머지 후 main run) 의 잔존 fail:

```
ArtistAndPostFlowE2ETest > admin registers + publishes artist; fan follows, posts, reacts; feed contains the fan's post FAILED
    at app//com.example.fanplatform.e2e.scenario.ArtistAndPostFlowE2ETest.fullArtistAndPostFlow(ArtistAndPostFlowE2ETest.java:306)

7 tests completed, 1 failed
```

`ArtistAndPostFlowE2ETest.java:306`:

```java
assertThat(foundFanPost)
        .as("feed must include the FAN_POST the fan just published — saw %d entries",
                contentArr.size())
        .isTrue();
```

즉 step 4 (fan publishes a `FAN_POST`/`PUBLIC`) 가 RC#2 fix 후 정상 201 + outbox publish, step 5 (reaction) 정상, step 6 (`GET /api/community/feed`) 의 응답에 fan 자신이 publish 한 FAN_POST 가 포함되지 않음.

JavaDoc 인용 (test 의 의도):

> Fan calls `GET /api/community/feed` — feed includes the fan's own post (v1 feed surfaces the actor's own posts plus their followed artists' posts).

즉 v1 feed contract 가 "actor 자신의 posts + 팔로우한 artists 의 posts" 를 surface 한다고 명시. 실제 동작은 **자신의 posts 가 누락**. spec drift 또는 application 회귀.

본 task 가 fix 후 `E2E (fan-platform v1 live-trio)` Job 7/7 PASS, main CI 의 fan-platform job FAILURE → SUCCESS.

---

# Scope

## In Scope

- 가설 검증:
  1. **(a) FeedQueryUseCase 가 actor 자신의 posts 를 포함 안 함** — feed query 가 `WHERE author_account_id IN (followed_artist_ids)` 만 filter, `OR author_account_id = :actor` 누락. **most likely**.
  2. **(b) Redis feed cache 가 publish 직후 invalidation 미동작** — actor 자신의 publish 가 cache update 안 함. eventual consistency 영역.
  3. **(c) test 가 잘못 (spec과 실제 contract 불일치)** — v1 feed 가 actor 자신의 posts 를 surface 하지 않는 게 정확하면 spec 정정 + test 갱신.
- 가설별 확인 명령:
  - (a) `grep -rn "FeedQuery\|feed.*query\|GetFeedUseCase" projects/fan-platform/apps/community-service/src/main`
  - (b) `grep -rn "FeedCache\|feed.*cache\|@Cacheable.*feed" projects/fan-platform/apps/community-service/src/main`
  - (c) `projects/fan-platform/specs/contracts/http/community-api.md` § Feed
- root cause 별 fix:
  - (a) feed query SQL 또는 application service 에 `OR author_account_id = :actor` 추가
  - (b) Redis cache key 패턴 + invalidation 시점 검토
  - (c) test 또는 spec 정정 — 어느 쪽이 source of truth 인지 contract 로 결정
- 회귀 가드 추가: FeedQueryIntegrationTest 또는 동등 slice test 에 actor self-publish → feed contains it case 추가

## Out of Scope

- 새 e2e 시나리오 추가 (현 7 → 새 8+)
- v2 의 feed re-design (개인화, multi-source, etc.)
- TASK-MONO-044 의 다른 회귀 (044c-1, 044e)

---

# Acceptance Criteria

## 부팅 + 통과

1. `:projects:fan-platform:tests:e2e:e2eTest` PASS (7/7, 현 6/7 → 7/7)
2. main CI `E2E (fan-platform v1 live-trio, Testcontainers)` Job FAILURE → SUCCESS
3. fan-platform 의 다른 영역 (slice/unit tests) 회귀 0

## 진단 + 분류

4. PR description 에 root cause 분류 (a/b/c) + 채택 사유 명시
5. 채택이 (c) (test 잘못) 일 경우 contract spec 갱신 PR 분리 가능성 검토

## 회귀 0

6. FeedQueryIntegrationTest 또는 등가 slice test 에 actor self-publish + feed contains 가드 추가
7. 회귀 보고서 `knowledge/incidents/2026-05-05-ci-regression.md` § fan-platform 단락에 단락 추가

---

# Related Specs

- [TASK-MONO-044f (선행, 5 PR + close 시리즈로 종결)](../done/TASK-MONO-044f-fan-platform-e2e-application-failures.md)
- [TASK-MONO-044 진단 보고서](../../knowledge/incidents/2026-05-05-ci-regression.md) § Job 3
- `projects/fan-platform/specs/services/community-service/architecture.md` § Feed query
- `projects/fan-platform/specs/contracts/http/community-api.md` § Feed (GET /api/community/feed)
- `projects/fan-platform/specs/integration/v1-e2e-scenarios.md` § Scenario 1 step 6

---

# Related Contracts

- `community-api.md` § GET /api/community/feed — actor 자신의 posts + 팔로우한 artists 의 posts 명시 여부 확인

---

# Target Service / Component

- `projects/fan-platform/apps/community-service/src/main/java/.../application/FeedQueryUseCase.java` (또는 동등)
- `projects/fan-platform/apps/community-service/src/main/java/.../infrastructure/jpa/PostJpaRepository.java` (feed query method)
- `projects/fan-platform/apps/community-service/src/main/java/.../infrastructure/cache/FeedCache.java` (있을 경우)
- `projects/fan-platform/tests/e2e/.../ArtistAndPostFlowE2ETest.java` (test 가 잘못이면)
- `projects/fan-platform/specs/contracts/http/community-api.md` (spec drift 시)

---

# Implementation Notes

- **첫 단계**: `git log --since=2026-04-25 --oneline projects/fan-platform/apps/community-service/src/main/java/com/example/fanplatform/community/application/Feed* projects/fan-platform/apps/community-service/src/main/java/com/example/fanplatform/community/infrastructure/jpa/PostJpaRepository.java` 로 feed 영역 변경 추적.
- **(a) 검증**: FeedQueryUseCase 또는 동등의 SQL/JPQL 을 직접 읽고 `author_account_id` filter 가 `actor` 포함하는지 확인. 또는 PostJpaRepository 의 `findFeedFor(...)` query method.
- **(b) 검증**: `@Cacheable` 또는 RedisTemplate 사용 위치 검색. publish 후 evict 또는 update 동작 확인. v1 일정상 가능성은 낮음 (cache 도입 안 됐을 가능성).
- **(c) 검증**: contract spec 의 feed 정의 vs e2e test 의 assertion 메시지 vs production code 의 실제 SQL 비교. spec 이 옳다면 production fix, test 가 옳다면 spec 정정.

---

# Edge Cases

1. **actor 가 어떠한 artist 도 follow 안 한 경우** (test 시나리오의 fan): feed 가 자신의 posts 만 노출. v1 spec 이 이 case 를 명시 안 했다면 spec 보강.
2. **author 가 ARTIST_POST 를 publish (artist role)**: artist 자신의 ARTIST_POST 도 feed 노출 여부 — 본 task 범위 밖이지만 spec 일관성 차원 검토.
3. **PUBLIC vs MEMBERS_ONLY/PREMIUM visibility**: visibility 가 feed query 에 어떻게 반영되는지. test 가 PUBLIC 만 사용하니 본 task 에서는 PUBLIC 만 다룸.
4. **post_status PUBLISHED 만 surface**: DRAFT/HIDDEN/DELETED 가 feed 에 안 나옴 — 일반 contract 검증.

---

# Failure Scenarios

## A. 가설 (a) 가 root cause — production query 누락

feed query SQL/JPQL 에 `OR author_account_id = :actor` 추가. 1 production fix + 1 회귀 가드 test.

## B. 가설 (c) 가 root cause — test 잘못 / spec drift

contract spec 정정 + test 갱신. PR description 에 spec drift 명시. 가장 작은 PR.

## C. 가설 (b) 가 root cause — Redis cache invalidation

cache key 패턴 + evict 시점 fix. medium PR.

## D. fan-platform v1 의 의도된 design 으로 actor self-post 가 feed 에 안 나오는 게 맞다면

test (`ArtistAndPostFlowE2ETest`) 의 step 6 검증을 약하게 (skip self-post check, follow-only check) 변경. spec 명문화. 가장 작은 PR.

---

# Test Requirements

- e2e suite 7/7 PASS
- FeedQueryIntegrationTest 또는 등가 slice test 에 actor self-publish 회귀 가드 추가
- main CI `E2E (fan-platform v1 live-trio)` Job 의 다음 run SUCCESS 확인

---

# Definition of Done

- [ ] root cause 분류 (a/b/c/D) + 채택 사유 PR description 기록
- [ ] cause 별 fix commit
- [ ] e2e 7/7 로컬 PASS
- [ ] main CI Job SUCCESS 검증
- [ ] 회귀 가드 test 추가 (또는 spec drift 정정)
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** — feed query 1 method 또는 spec/test 1 줄 정정 가능성. (a) 가 단일 root cause 면 작은 fix. 복수 cause 또는 cache 침범 시 Opus escalate.
- **분량 추정**: small (1-2 file). spec drift case 면 single-line.
- **dependency**:
  - `선행`: TASK-MONO-044f (5 PR + close 시리즈로 종결됨)
  - `후속`: 없음
- **CI gating**: 본 PR 자체 영향 = `E2E (fan-platform v1 live-trio)` Job FAIL → SUCCESS. 다른 Job 영향 0.
