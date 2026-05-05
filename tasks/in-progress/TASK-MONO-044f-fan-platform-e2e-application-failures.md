# Task ID

TASK-MONO-044f

# Title

fan-platform v1 e2e 의 application-layer assertion 실패 4건 fix (gateway 부팅 회복 후 노출)

# Status

ready

# Owner

backend / qa

# Task Tags

- test
- code

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

[TASK-MONO-044d](../done/TASK-MONO-044d-fan-platform-keyresolver-ambiguity.md) 가 `RateLimitConfig` 의 `KeyResolver` 모호성을 해소하면서 fan-platform-gateway-service 가 정상 부팅. 그 결과 e2e 테스트 7건이 모두 `initializationError` 없이 *실행* 되었으나 4건이 application-layer assertion 으로 fail. 044d spec § Failure Scenario C 에 명시적으로 follow-up 분리된 영역.

CI run `25342813498` (PR #205, 044d) 의 `E2E (fan-platform v1 live-trio, Testcontainers)` Job 결과 (3 PASS / 4 FAIL):

```
ArtistAndPostFlowE2ETest > admin registers + publishes artist; fan follows, posts, reacts; feed contains the fan's post FAILED
  org.opentest4j.AssertionFailedError: [POST /api/v1/artist/artists (-> /api/artists) returns 201 for admin-tier role]
VisibilityTierE2ETest > PUBLIC post -> any authenticated tenant member sees 200 FAILED
VisibilityTierE2ETest > PREMIUM post -> v1 always-pass + WARN log captured in container stdout FAILED
VisibilityTierE2ETest > MEMBERS_ONLY post -> v1 stub allows access (follow-up: bean-swap test profile) FAILED

7 tests completed, 4 failed
```

가설: 044a 의 `libs/java-web` → `libs/java-web-servlet` 분리 이후 fan-platform 의 servlet 기반 service (artist-service / post-service / membership-service 등) 가 `libs:java-web-servlet` 의존을 얻어야 함에도 누락되었거나, route rewrite 또는 권한 검증 로직이 회귀했을 가능성. ArtistAndPostFlowE2ETest 의 첫 실패는 admin role 의 `POST /api/v1/artist/artists` 가 201 가 아닌 다른 status 반환 — gateway 통과 / downstream service 응답 문제 양쪽 모두 가능.

본 task 가 fix 후 `E2E (fan-platform v1 live-trio)` Job FAILURE → SUCCESS, 044d AC #3 사후 충족.

---

# Scope

## In Scope

- 4 failing e2e 테스트의 stack trace + HTTP response body 분석:
  - `ArtistAndPostFlowE2ETest > admin registers + publishes artist; ...` — actual=**404** (TASK-MONO-044c-1 후속 분석 시점에서 확인)
  - `VisibilityTierE2ETest > PUBLIC post -> any authenticated tenant member sees 200` — actual=**500**
  - `VisibilityTierE2ETest > PREMIUM post -> v1 always-pass + WARN log captured` — actual=**500**
  - `VisibilityTierE2ETest > MEMBERS_ONLY post -> v1 stub allows access` — actual=**500**

> 2026-05-05 분석 갱신 — 본 task 의 가설 (a)/(b)/(c) 모두 부정확으로 판명. 실제 root cause 는 아래와 같이 RC#1 + RC#2 로 split:

### RC#1 (1 fail) — e2e fixture vs gateway route + contract spec **path drift**

`ArtistAndPostFlowE2ETest` 의 첫 fail 은 `POST /api/v1/artist/artists` → 404. 실제 contract 와 gateway route 의 일관된 path 는 `/api/v1/artists/**`:

| 파일 | path |
|---|---|
| `tests/e2e/.../E2ETestFixtures.java#pathArtistRegister()` | `/api/v1/artist/artists` ❌ (drift) |
| `apps/gateway-service/.../application.yml` (id=artist-service-artists) | `Path=/api/v1/artists/**` ✅ |
| `specs/contracts/http/artist-api.md` § Public ↔ Internal table | `/api/v1/artists/**` ✅ |

→ **fix**: `E2ETestFixtures.pathArtistRegister()` 와 `pathArtistStatus(...)` 의 base 를 `/api/v1/artists` 로 정정.

본래 PR #131 (TASK-FAN-INT-001 v1 e2e 도입) 에서 fixture 가 잘못 작성되었으나 servlet leak 부팅 차단으로 노출되지 않았음 — 044d KeyResolver fix 로 부팅 회복 후 path drift 가 처음으로 가시화. 단순 1-line fixture fix.

### RC#2 (3 fail) — VisibilityTier 의 `POST /api/community/posts` 가 community-service 에서 **500**

VisibilityTier 3 테스트 모두 첫 단계 `pathCommunityPosts()` (`/api/v1/community/posts`) 의 createPost 가 500. gateway route `/api/v1/community/(?<segment>.*) → /api/community/${segment}` 는 정의되어 있음. PostStatusMachine 자체에는 회귀 없음 (DRAFT→PUBLISHED for AUTHOR 가 정의됨; 또한 createPost 흐름에서는 호출되지 않을 수 있음).

→ **분석 단계**:
1. `tests/e2e/build/reports/tests/e2eTest/index.html` (CI artifact) 또는 로컬 재현으로 community-service container stderr 확보.
2. PostController#createPost → RegisterPostUseCase → DB INSERT → outbox emit 흐름 중 500 발생 지점 식별.
3. 가설 후보:
   - (a) DB schema 회귀 (V0001/V0002 fan-platform community Flyway seed 와 entity mapping mismatch)
   - (b) outbox emit 시 NPE / serialization 실패
   - (c) ActorContextResolver 가 JWT claim 누락으로 NPE
   - (d) PostType=`FAN_POST` 인 enum 매핑 회귀

**fix**: cause 식별 후 cause 별 commit. RC#1 과 RC#2 는 서로 무관하므로 commit 분리.

## Out of Scope

- 새 e2e 시나리오 추가 (현 7 → 새 8+) — fix 만 집중
- gateway-service 자체 (044d 로 부팅 회복 입증)
- nightly CI 회귀 자동화 (TASK-MONO-044 § AC #8 별도 후속)
- fan-platform v2 의 placeholder service (notification / admin / search / media) 영향 0

---

# Acceptance Criteria

## 부팅 + 통과

1. 7/7 fan-platform e2e 테스트 PASS (현재 3/7)
2. main CI 의 `E2E (fan-platform v1 live-trio, Testcontainers)` Job FAILURE → SUCCESS

## 진단 + 분류

3. PR description 에 root cause 분류 (a/b/c) + fix 전략 기록
4. 단일 root cause 면 1 commit, 복수면 cause 별 commit

## 회귀 0

5. fan-platform 의 다른 영역 (slice tests / unit tests / 다른 e2e 가 있다면) 회귀 0
6. 다른 reactive gateway 사용 프로젝트 (WMS / GAP / scm) 영향 0 (044a 로 분리된 java-web-servlet 의존이 일관 적용되었는지)
7. 회귀 보고서 `knowledge/incidents/2026-05-05-ci-regression.md` 에 fan-platform 후속 결과 단락 추가

---

# Related Specs

- [TASK-MONO-044 진단 보고서](../../knowledge/incidents/2026-05-05-ci-regression.md) § Job 3
- [TASK-MONO-044a (servlet leak fix)](../done/TASK-MONO-044a-libs-java-web-servlet-leak-fix.md) — 가설 (a) 의 직접 선행
- [TASK-MONO-044d (KeyResolver fix)](../done/TASK-MONO-044d-fan-platform-keyresolver-ambiguity.md) — 본 task 가 노출시킨 결함의 직접 선행
- `projects/fan-platform/specs/services/gateway-service/` — gateway-public-routes
- `projects/fan-platform/specs/services/artist-service/`
- `projects/fan-platform/specs/services/post-service/` (또는 동등)

---

# Related Contracts

- `projects/fan-platform/specs/contracts/http/...api.md` (관련 endpoint 들)

---

# Target Service / Component

- `projects/fan-platform/apps/<servlet-service>/build.gradle` (가설 (a) 시 다수 service)
- `projects/fan-platform/apps/gateway-service/src/main/resources/application*.yml` (가설 (b) 시)
- `projects/fan-platform/apps/<servlet-service>/src/main/java/.../config/SecurityConfig.java` (가설 (c) 시)

---

# Implementation Notes

- **RC#1 fix**: `tests/e2e/.../E2ETestFixtures.java` 의 `pathArtistRegister()` / `pathArtistStatus(...)` 를 `/api/v1/artist/artists...` → `/api/v1/artists...` 로 정정. 1 commit, 1-2 줄 변경.
- **RC#2 분석 첫 단계**: 로컬에서 fan-platform e2e suite 실행 후 `tests/e2e/build/reports/tests/e2eTest/index.html` 의 `VisibilityTierE2ETest > PUBLIC ...` 의 stack trace + community-service 컨테이너 stdout (`docker logs <community-service-container>`) 으로 500 origin 확인.
- 부수 검토: `git log --since=2026-05-01 --oneline -- projects/fan-platform/apps/community-service/src/main` 으로 회귀 도입 commit 후보 추적.
- 가설 (a) `libs:java-web-servlet` 의존 누락은 부정 — fan-platform community/artist build.gradle 은 PR #131 v1 bootstrap 시점 이후 변경 없음을 git log 로 확인 가능 (단순 검증).

---

# Edge Cases

1. **RC#1 단독 fix 시 RC#2 자동 해소 가능성** — 매우 낮음. RC#1 은 artist endpoint 의 path drift, RC#2 는 community POST 의 application 500 이라 서로 다른 서비스 + 다른 endpoint. 분리 fix.
2. **RC#2 가 V0011 OAuth seed 회귀 (TASK-MONO-044c BCrypt re-pin) 와 cascade**: 매우 낮음. fan-platform e2e 는 자체 RSA JWKS 로 직접 서명 (`JwtTestHelper`), GAP SAS 발행 토큰을 사용하지 않음. 무관.
3. **route rewrite 가 reactive 부팅 차이로 회귀**: 부정 — `/api/v1/community/...` route 는 `RewritePath` 정의됨 + RC#1 은 단순 fixture path drift.
4. **RC#2 community 500 의 cause 가 V0001 schema vs entity drift**: 가능. fan-platform v1 community Flyway seed 와 PostJpaEntity / PostJpaRepository 의 컬럼 매핑 검증 필요.

---

# Failure Scenarios

## A. RC#1 + RC#2 가 본 task 가 가정한 바와 같이 분리 (확정)

- RC#1: `E2ETestFixtures` path drift fix — 1 commit
- RC#2: community POST 500 의 cause 식별 후 cause 별 commit

## B. RC#2 가 복수 cause 로 더 분해

`044f-1` (RC#1 fixture fix) + `044f-2` (RC#2 community 500) 로 sub-task 분할 검토. 또는 RC#2 의 cause 별 sub-task 추가 분할.

## C. fix 후에도 sporadic 실패

CI runner 자원 / Testcontainers 환경 한계 영역. TASK-MONO-044 § AC #8 후속.

---

# Test Requirements

- `:projects:fan-platform:tests:e2e:e2eTest` 7/7 PASS (로컬 + CI)
- fan-platform 의 모든 servlet service `:check` PASS
- main CI `E2E (fan-platform v1 live-trio)` Job SUCCESS 검증
- 회귀 보고서 단락 갱신

---

# Definition of Done

- [ ] 4 실패 stack trace + HTTP response 수집
- [ ] root cause 분류 (a/b/c) + fix 전략 PR description 기록
- [ ] cause 별 fix commit
- [ ] 7/7 e2e 로컬 PASS
- [ ] main CI Job SUCCESS 검증
- [ ] 회귀 보고서 단락 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — RC#2 community POST 500 이 application/도메인 레이어 회귀 분석 필요. RC#1 은 단순 1-line fixture fix 라 별도로 빠르게 처리 가능. RC#2 cause 가 자명한 단일 (예: schema drift 1 컬럼) 으로 판명 시 사후 Sonnet downgrade 가능.
- **분량 추정**: RC#1 small (1 commit, 2 줄). RC#2 medium 예상 (community-service 의 POST 흐름 분석 + cause 별 fix).
- **dependency**:
  - `선행`: TASK-MONO-044a + 044d (이미 머지됨)
  - `후속`: 없음
- **CI gating**: 본 PR 자체 영향 = `E2E (fan-platform v1 live-trio)` Job FAIL → SUCCESS. 다른 Job 영향 0.
