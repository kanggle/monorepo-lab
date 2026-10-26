# Task ID

TASK-FAN-BE-053

# Title

팔로잉 피드의 `commentCount` · `reactionCount` 가 최대 5분 낡는다 — Redis 페이지 캐시가 수치까지 얼려 두고 쓰기가 무효화하지 않는다

# Status

review

# Owner

fan-platform

# Task Tags

- backend
- community
- cache
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (캐시 계약 · 읽기 시점 오버레이 · 실패 경로 설계; 형제인 `TASK-FAN-BE-046` 의 권한 오버레이 선례를 따름)
> Drafted 2026-10-25 UTC. 출처: 35차 데모 창. 방향은 소유자 승인(«결함으로 보고 고친다»; 페이지 캐시는 두고 읽기 시점에 라이브 수치를 덧입힌다). 사실은 origin/main `1ccccdb88` 에서 읽었다.

---

# 배경

## 라이브 증거 (35차 창, 2026-10-25 09:23Z)

- 같은 순간에 글 상세 `GET /api/v1/community/posts/{id}` 는 `reactionCount 1` (반응 PUT 직후), 피드 `GET /api/v1/community/feed` 는 같은 글이 `reactionCount 0`.
- Redis 키 `feed:v3:fan-platform:<account>:0:10` 의 `ttl 297`(초) — 항목이 남은 수명 동안 낡은 수치를 계속 서빙. 검증 후 반응은 DELETE 204 로 되돌렸다.

## 현재 코드 상태 (2026-10-25 UTC 실측)

- `application/GetFeedUseCase.java:74-89` — 캐시 hit 이면 `FeedItemSnapshot` 페이지를 그대로 쓰고 `applyEntitlement` 만 얹는다. miss 일 때만 `queryAndBuild`(`:92-111`)가 `commentRepository.countsByPostIds`(`:97`) · `reactionRepository.countsByPostIds`(`:98`)로 수치를 채워 **스냅샷에 구워 넣는다**(`:108-109`).
- `infrastructure/cache/FeedCacheRepository.java:36-37,62` — 무효화는 TTL 뿐(`TTL_MINUTES = 5`). 반응·댓글 쓰기 경로는 피드 캐시를 지우지 않는다. 키 = `feed:<ver>:<tenant>:<account>:<page>:<size>` (`:24`).
- `GetFeedUseCase` 는 이미 **같은 종류의 문제를 한 번 풀었다**: `TASK-FAN-BE-046` 이 «권한 판정은 캐시하지 않고 읽을 때 매번 계산» 으로 `applyEntitlement` 를 hit/miss 양쪽 끝에 둔다(`:88-89`, 클래스 javadoc `:33-47`). 이 티켓은 **수치**를 같은 자리에 같은 방식으로 얹는다.
- 형제: `GetMyPostsUseCase.java:49-56` 은 캐시 없이 매 요청 `countsByPostIds` 를 부른다(수치가 낡지 않음). 상세(`GET /posts/{id}`)도 캐시 없음 — `architecture.md` § Read Path.
- 계약: `projects/fan-platform/specs/contracts/http/community-api.md` § Feed(`:288-325`)는 `commentCount`/`reactionCount` 를 예시 JSON 에만 두고 **신선도에 대한 문장이 없다**(grep: `fresh|stale|cache|TTL` 매치 없음). `specs/services/community-service/architecture.md:253-255`(§ Read Path)가 «TTL-only … 새 글·팔로우는 최대 5분 후 보임» 만 말하고 **수치는 언급하지 않는다**. 즉 수치 낡음은 현재 **계약에 약속도 부인도 없는 공백** 이다.

---

# Goal

팔로잉 피드가 돌려주는 `commentCount` · `reactionCount` 가 캐시 TTL 과 무관하게 읽는 시점의 실제 값이다. 새 글·팔로우 변화의 5분 지연(페이지 구성)은 그대로 둔다.

# Scope

## In Scope

- **방향(소유자 승인): 페이지 캐시는 유지하고, 읽는 시점에 라이브 수치를 덧입힌다** — `applyEntitlement` 와 같은 자리(캐시 hit/miss 이후, 응답 직전)에서 스냅샷 페이지의 `postId` 목록으로 `commentRepository.countsByPostIds` · `reactionRepository.countsByPostIds` 를 호출해 `FeedItemView` 의 수치를 대체한다.
- **쓰기 시 무효화(evict-on-write)를 채택하지 않는 이유:** 캐시 키가 `계정 × 페이지 × 크기` 라서 한 글에 반응이 하나 달리면 그 글을 피드에 가진 **모든 팔로워의 모든 페이지 키** 를 지워야 한다. 키로 역조회할 색인이 없고(팔로워 수 × 페이지 수), 이벤트 기반 DEL 은 이벤트가 안 오면 조용히 낡은 채 남는다(`GetFeedUseCase` 클래스 javadoc 이 같은 이유로 권한에 대해 기각한 논거). 오버레이는 이벤트 도착에 의존하지 않는다.
- **스냅샷 모양 결정:** 오버레이 후 스냅샷의 `commentCount`/`reactionCount` 는 죽은 필드가 된다. (a) 필드는 두고 오버레이가 덮어쓴다(키 버전 유지, 구현 최소) 또는 (b) 스냅샷에서 제거하고 `KEY_VERSION` 을 `v4` 로 올린다(죽은 데이터를 캐시에 안 둠; `FeedCacheRepository` 의 버전 규칙 `:28-34,52-60` — 모양이 바뀌면 올린다). 결정을 구현 보고에 근거와 함께 남긴다.
- **계약·아키텍처 먼저(같은 PR, 첫 커밋):** `community-api.md` § Feed 에 «`commentCount`/`reactionCount` 는 읽는 시점의 값이다; 페이지 구성(어떤 글이 보이는가)은 최대 5분 낡을 수 있다» 를 명시한다. `architecture.md` § Read Path(`:253-255`)의 «Bulk aggregate counts … 2 grouped queries per page» 줄을 «캐시 hit 경로에서도 수행» 으로 갱신하고 «hit 이면 DB 를 안 건드린다» 류 서술(`FeedCacheRepository` 클래스 javadoc `:26`, 메트릭 설명 `community_feed_cache_hits_total` = «zero DB round-trips») 을 정정한다.

## Out of Scope

- 페이지 구성(새 글·팔로우)의 신선도 — TTL 5분 유지.
- 상세·`mine` 경로(이미 라이브).
- 프런트 변경 (`TASK-FAN-FE-033` 은 반응 취소만).
- 집계 테이블/카운터 캐시 도입.

# Acceptance Criteria

- [x] **AC-0 (오버레이 지점과 질의 비용 확인)** — 착수 시점 코드로 확인해 기록한다: (1) 오버레이 지점 = `GetFeedUseCase.execute` 의 4단계 앞(`:88`) 혹은 `applyEntitlement` 안 — 어느 쪽이든 hit/miss 양 경로를 지난다; (2) 비용 = 페이지당 `countsByPostIds` **2회**(댓글·반응 각 1, 그룹 쿼리 — `CommentRepositoryImpl.java:39-47`, `ReactionRepositoryImpl.java:41-49`), 대상은 `size ≤ 50`(`MAX_SIZE`) 건의 `postId` `IN` 목록, 빈 페이지면 질의 0회(`isEmpty()` 단락); (3) hit 경로가 «DB 0회» 에서 «DB 2회» 로 바뀐다는 사실을 명시하고 이 변화가 수용 가능한지(feed 호출 빈도 · 커넥션 풀) 판단을 적는다. 또한 `countsByPostIds` 대상 인덱스(`post_id`)를 `EXPLAIN` 또는 스키마로 확인한다.
- [x] **AC-1 (계약 먼저)** — `community-api.md` § Feed 가 수치 신선도(읽는 시점) 와 페이지 구성 신선도(최대 5분) 를 구분해 명시하고, `architecture.md` § Read Path 와 `FeedCacheRepository` javadoc/메트릭 설명이 더 이상 «hit = DB 0회» 라 말하지 않는다. 이 문서 변경이 코드 변경보다 **앞선 커밋**이다.
- [x] **AC-2 (신선, 수정 전 빨강)** — 단위 테스트(`GetFeedUseCaseTest` 계열, 캐시 hit 을 구성): 스냅샷에 구워진 수치(예: `reactionCount 0`)와 저장소가 돌려주는 라이브 수치(예: `1`)가 다를 때 응답이 **라이브 값**이다. **오버레이를 되돌리면 빨강** 임을 mutation/revert 로 보이고 기록한다. `GetFeedUseCaseEntitlementFreshnessTest` 와 같은 구성을 재사용한다(형제 grep: `projects/fan-platform` 의 `Freshness` 테스트).
- [x] **AC-3 (miss 경로 대조군)** — 캐시 miss 경로도 응답 수치가 라이브 값이다(이중 호출이 아니라 miss 시 `queryAndBuild` 의 집계를 재사용하거나 한 번만 부른다 — 호출 횟수를 `verify(times)` 로 고정: 페이지당 댓글 1 · 반응 1).
- [x] **AC-4 (비용 경계)** — 빈 페이지에서 `countsByPostIds` 가 호출되지 않거나 빈 맵을 돌려주며 예외 없음. 한 페이지에서 각 저장소가 정확히 1회 호출됨을 단언.
- [x] **AC-5 (실패 경로 결정)** — 집계 질의가 실패하면(DB 오류) 피드 전체를 500 으로 만들지, 스냅샷의 구워진 수치로 폴백할지 **결정하고 테스트로 고정한다**. 기본 권장: 수치는 부가 정보이므로 폴백(낡은 수치 + 경고 로그 + 카운터), 단 페이지 자체 질의 실패는 기존대로. 선택과 근거를 구현 보고에 적는다.
- [ ] **AC-6 (통합)** — Testcontainers 통합 테스트(`FeedQueryIntegrationTest` 계열): 피드를 한 번 조회해 캐시를 채운 뒤 반응/댓글을 추가하고 **캐시 TTL 안에서** 다시 조회하면 새 수치가 보인다. 키 버전을 바꾸면 쓰는 `FeedCacheRepository.KEY_VERSION` 상수로 키를 구성(손으로 타이핑한 키 금지 — 기존 규칙 `:51-55`).
- [ ] **AC-7 (회귀)** — community-service 전체 단위·통합 초록. 형제 모듈이 수치를 가진 다른 응답이 있는지 grep(피드 외 캐시 사용처) 해 같은 결함이 없는지 확인하고 결과를 기록.
- [ ] **AC-8 (라이브, 다음 데모 창 — 굽기 필요)** — 이 변경은 community-service 이므로 **다음 AMI 굽기 후 창**에서 측정한다: 반응 PUT 직후 `/feed` 의 `reactionCount` 가 상세와 같은 값이고, Redis 키 `ttl` 이 아직 남아 있는데도(예: `redis-cli TTL feed:<ver>:...` > 0) 그렇다. 반응은 DELETE 204 로 되돌린다. 못 재면 ⚪ + 사유.

# Related Specs

- `projects/fan-platform/specs/services/community-service/architecture.md` § Read Path · § Membership Tiering
- `projects/fan-platform/tasks/done/` — `TASK-FAN-BE-046` (권한 오버레이 선례; 파일명은 착수 시 Glob 으로 확인)

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` § Feed — **신선도 문장 추가(변경 대상).**

# Edge Cases

- 캐시에는 있으나 그 사이 삭제된 글 — 집계 맵에 항목이 없어 `getOrDefault(…, 0L)` 이 0 을 준다(페이지 구성의 낡음은 이 티켓 범위 밖이지만 수치가 음수/예외가 되지 않음을 확인).
- 페이지 크기 50(상한) — `IN` 목록 50 개 질의가 허용 범위임(AC-0 에서 확인).
- Redis 불가 — 기존대로 miss 처럼 Postgres 로 폴백; 그때 수치는 `queryAndBuild` 와 오버레이 중 한 번만 계산된다(AC-3).
- 잠긴(locked) 항목 — 수치는 잠금과 무관하게 기존대로 노출(변경 없음; 계약 `:323` 은 title/body/mediaRefs 만 가린다).

# Failure Scenarios

1. 오버레이를 `applyEntitlement` 뒤나 miss 경로에만 둔다 — hit 경로(문제의 경로)가 여전히 낡는다(AC-2 가 hit 을 구성하는 이유).
2. miss 경로가 집계를 두 번 한다(`queryAndBuild` + 오버레이) — 페이지당 4질의로 불필요한 비용(AC-3·AC-4).
3. 집계 실패가 피드 전체 500 이 된다 — 부가 수치 때문에 핵심 목록이 죽는다(AC-5).
4. evict-on-write 로 선회한다 — 계정×페이지 키 때문에 팬아웃 삭제가 필요하고 이벤트 유실 시 조용히 낡는다(Scope 의 기각 이유).
5. 계약·아키텍처 문서를 안 고친다 — «hit = DB 0회» 서술이 코드와 갈라져 다음 사람이 그 가정 위에 짓는다(AC-1).

---

# Implementation Record (2026-10-25 UTC)

## AC-0 — 오버레이 지점과 질의 비용 (착수 시점 코드로 확인)

1. **지점** — `GetFeedUseCase.execute` 의 캐시 hit 분기, 권한 게이트(`render`, 구 `applyEntitlement`) 바로 앞. miss 경로는 `queryAndBuild`(이제 `FeedReader.buildPage`)가 같은 요청에서 읽은 수치를 그대로 쓰므로 오버레이를 다시 하지 않는다 — 두 경로 모두 응답 수치는 그 요청에서 읽은 값이다.
2. **비용** — 페이지당 grouped 집계 2회(`CommentJpaRepository.countsGroupedByPostId`, `ReactionJpaRepository.countsGroupedByPostId`), `IN` 목록 ≤ 50(`MAX_SIZE`). 빈 캐시 페이지는 0회(`GetFeedUseCase` 에서 `isEmpty()` 단락 — 저장소 단락에 기대지 않음). 인덱스: `V1__init.sql` 의 `idx_comments_tenant_post_created (tenant_id, post_id, created_at)` · `idx_reactions_tenant_post (tenant_id, post_id)` (+ reactions PK `(post_id, reactor_account_id)`); 이후 마이그레이션에 DROP 없음(grep). `EXPLAIN` 은 이 호스트에 Docker 가 없어 실행하지 못했다 — 스키마로만 확인.
3. **hit 경로 DB 0회 → 2회** — 수용 가능하다고 판단: 질의는 인덱스로 좁혀지는 `IN (≤50)` GROUP BY 두 개이고, `/posts/mine`(`GetMyPostsUseCase`)이 이미 매 요청 같은 두 질의를 캐시 없이 돈다. 커넥션 풀(`hikari.maximum-pool-size: 10`)에는 오히려 유리해졌다: 이전에는 `execute` 의 `@Transactional(readOnly = true)` 가 캐시 조회 **전에** 열려 hit 에서도 커넥션을 잡은 채 membership-service HTTP 호출까지 기다렸다(read-only 트랜잭션은 Spring 의 Hibernate dialect 가 begin 시점에 커넥션을 확보한다 — 코드 경로 추론, 계측 아님). 이제 커넥션은 집계 두 질의 동안만 잡힌다.

## 결정

- **스냅샷 모양 = (a) 필드 유지, `KEY_VERSION` 은 `v3` 그대로.** 근거: AC-5 의 기본 권장(폴백 = 스냅샷의 구워진 수치)을 택하면 그 필드가 폴백 값이므로 지울 수 없다 — (b) 를 고르면 폴백할 값이 없어진다. 모양이 안 바뀌었으니 버전 규칙(`FeedCacheRepository` «모양이 바뀌면 올린다»)도 올리라고 하지 않고, 배포 전 `v3` 항목은 그대로 역직렬화되어 첫 읽기에서 수치가 덮인다. `FeedItemSnapshot`·`FeedCacheRepository` javadoc 에 기록.
- **AC-5 = 티켓 기본 권장 그대로: hit 에서 집계 실패 → 200 + 스냅샷 수치 + WARN 로그 + `community_feed_live_counts_fallback_total`.** 흡수하는 것은 DB 실패(`DataAccessException`, `TransactionException`)뿐 — 그 밖의 예외는 버그로 보고 전파. 두 집계 중 하나만 실패해도 둘 다 스냅샷 값(반쪽 라이브 상태를 만들지 않음). miss 경로는 «페이지 자체 질의 실패는 기존대로» 에 따라 전파(폴백할 낡은 값이 없고, 캐시에도 안 씀).
- **트랜잭션 경계를 옮겼다 (티켓이 예상하지 못한 부분).** 폴백이 런타임에 실제로 동작하려면 실패한 질의가 바깥 트랜잭션 안에 있으면 안 된다 — Hibernate 가 실패 시 현재 트랜잭션을 rollback-only 로 표시하므로, `execute` 가 `@Transactional` 이면 잡은 예외가 커밋에서 `UnexpectedRollbackException`(500)으로 돌아온다. 목(mock) 단위 테스트로는 보이지 않는 결함이다. 그래서 DB 읽기를 새 빈 `FeedReader`(`buildPage` · `liveCounts`, 각각 `@Transactional(readOnly = true)`)로 옮기고 `execute` 는 비트랜잭션 오케스트레이터로 둔다 — `transaction-handling` 스킬의 «self-invocation 은 별도 빈으로», «외부 호출은 트랜잭션 밖» 과 일치. miss 경로는 이전과 같이 페이지+집계가 한 트랜잭션.
- **evict-on-write 는 채택하지 않음** — Scope 의 기각 이유 그대로(계정×페이지 키 팬아웃, 이벤트 유실 시 조용히 낡음).

## 테스트 / 수정 전 빨강

- 신규 `GetFeedUseCaseCountFreshnessTest` 12셀(hit 라이브 수치 ↑·↓, 삭제된 글 0, 잠긴 글도 라이브 수치, 저장소별 1회·페이지 전체 id, 빈 페이지 0회, miss 1회씩, 폴백 2종, 비DB 예외 전파, miss 실패 전파, `@Transactional` 경계 핀). 기존 `GetFeedUseCaseEntitlementFreshnessTest` 의 «hit = DB 0회» 셀은 «hit 는 페이지 질의를 안 한다»(`verifyNoInteractions(postRepository)`)로 고쳤다.
- 변이 ① hit 경로 오버레이 제거(`render(cached.get(), actor)`) → 8 failed, 대표 `🔴 AC-2` = `expected: 1L but was: 0L`(35차 창 증상 그대로). ② 폴백 catch 제거 → AC-5 2셀 failed(`DataAccessResourceFailureException: connection refused`, `CannotCreateTransactionException: pool exhausted`). ③ miss 경로에도 오버레이 → AC-3 failed(`TooManyActualInvocations: commentRepository.countsByPostIds`). 원복 후 `:projects:fan-platform:apps:community-service:test --rerun-tasks` rc=0, 41 클래스 238 tests, 실패 0.
- `FeedQueryIntegrationTest` 에 2셀 추가: (a) 캐시 채움 → 반응 PUT + 댓글 → `getExpire(key) > 0` 확인 → 재조회 시 수치 1/1, 제목은 DB 변경 전 값(=캐시에서 서빙됨 판별자); (b) 실제 SQL 오류(`ALTER TABLE reactions RENAME`)로 hit 의 집계를 깨도 200 + 스냅샷 수치 — 트랜잭션 경계를 런타임에서 재는 셀. **이 호스트에 Docker 가 없어 두 셀 모두 로컬 미실행** → AC-6 · AC-7 은 CI `integrationTest` 결과로 닫는다. (b) 의 수정 전 빨강(= `execute` 에 `@Transactional(readOnly = true)` 를 되돌리면 500)은 CI 변이로만 보일 수 있다.

## AC-7 형제 grep

fan-platform 의 Redis 사용처는 `FeedCacheRepository`(community)와 `ArtistDirectoryCacheAdapter`(artist) 둘뿐(`StringRedisTemplate|RedisTemplate` grep). artist 디렉터리 캐시의 `ArtistView` 에는 수치 필드가 없다 — 같은 결함 없음. 상세(`GET /posts/{id}`)·`/posts/mine` 은 캐시 없음.

---

## CORRECTION — AC-6 · AC-7 닫힘 (2026-10-26 UTC, CI 로그 실측)

- **AC-6 ✅** — impl PR #4563(head `c22c8c9d2`, squash `b3a113aa6`) 의 run `40185901815` job `121146537505` «Integration (fan-platform, Testcontainers)» = SUCCESS. 잡 초록만으로 닫지 않고 **로그에서 셀 이름을 읽었다**(태그 없는 Testcontainers 는 «돌았다» 가 추론뿐인 함정): `:projects:fan-platform:apps:community-service:integrationTest` 실행, `FeedQueryIntegrationTest > 🔴 053 AC-6: 캐시를 채운 뒤 반응·댓글을 더하면 TTL 안에서도 다음 조회에 새 수치가 보인다 PASSED` · `FeedQueryIntegrationTest > 053 AC-5: 히트에서 집계 질의가 실제 DB 오류로 실패해도 200 + 캐시 적재 시점 수치 (트랜잭션 경계 실측) PASSED`, 기존 4셀도 PASSED, 요약 표 `community-service:integrationTest | 59 | 0 | 0 | 0`, `BUILD SUCCESSFUL in 2m 51s`. ⚪ (b) 셀의 **수정 전 빨강**(`execute` 에 `@Transactional(readOnly = true)` 복원 → 500)은 CI 변이를 돌리지 않아 **측정하지 않았다** — 이 셀이 트랜잭션 경계를 무는지는 단위 테스트의 `@Transactional` 경계 핀(위 «테스트» 절)이 대신 지킨다.
- **AC-7 ✅** — 통합: 위 59건 실패 0. 단위: 같은 run 의 job `121146537301` «Build & Test (JDK 21, Linux)» = SUCCESS, 로그에 `> Task :projects:fan-platform:apps:community-service:test` 가 접미사 없이(= `UP-TO-DATE`/`FROM-CACHE` 아님, 실제 실행) 찍히고 빌드 성공 — 이 잡은 테스트별 이벤트를 출력하지 않아 **셀 단위는 로그로 못 본다**; 셀 수는 구현 기록의 로컬 실행(`--rerun-tasks` rc=0, 41 클래스 238 tests, 실패 0)이다. 형제 grep 은 위 절.
- **AC-8 — 열린 채 유지**(다음 AMI 굽기 후 창). ⇒ review 유지.
- (원문 체크박스는 동결 규칙상 바꾸지 않았다 — 이 절이 AC-6 · AC-7 의 닫힘 기록이다.)
