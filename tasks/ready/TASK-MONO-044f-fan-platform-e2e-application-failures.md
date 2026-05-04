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

- 4 failing e2e 테스트의 stack trace + HTTP response body 분석 (테스트 보고서 + 컨테이너 로그):
  - `ArtistAndPostFlowE2ETest > admin registers + publishes artist; ...`
  - `VisibilityTierE2ETest > PUBLIC post -> any authenticated tenant member sees 200`
  - `VisibilityTierE2ETest > PREMIUM post -> v1 always-pass + WARN log captured`
  - `VisibilityTierE2ETest > MEMBERS_ONLY post -> v1 stub allows access`
- 다음 가설 단계별 검증:
  1. **(a) 044a libs/java-web 분리 누락 dependent**: fan-platform 의 servlet service 들 (`artist-service`/`post-service`/`membership-service` 등) `build.gradle` 이 `libs:java-web-servlet` 의존을 가지고 있는지 확인. 누락 시 `CommonGlobalExceptionHandler` 미로드 / @ControllerAdvice 미동작 → exception 시 잘못된 status. **Most likely root cause**.
  2. **(b) gateway route rewrite 회귀**: `/api/v1/artist/artists -> /api/artists` 경로 재작성이 새 reactive 부팅 후에도 정상 동작하는지 검증. `gateway-public-routes.md` spec 과 일치 확인.
  3. **(c) 권한/역할 검증 로직**: `admin-tier role` 인식 또는 `tenant_id` claim 처리 회귀.
- root cause 별 fix:
  - (a) 누락 service 들에 `libs:java-web-servlet` 의존 추가 (한 commit 으로 cross-service 수정)
  - (b) gateway route 정의 또는 strip prefix 회귀 fix
  - (c) downstream service 의 SecurityConfig 또는 role mapping fix

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

- **첫 단계**: 4 테스트의 HTTP response body / status code 를 fan-platform e2e 보고서 (`projects/fan-platform/tests/e2e/build/reports/tests/e2eTest/index.html`) 에서 확인. CI artifacts 다운로드 또는 로컬 e2e 재현.
- **(a) 검증 명령**: `grep -r "libs:java-web" projects/fan-platform/apps/*/build.gradle` 로 의존 분포 확인. 누락이 있으면 일관 적용 + `libs:java-web-servlet` 으로 갱신.
- **(b) 검증**: gateway-service 컨테이너 로그에서 `/api/v1/artist/artists` 요청의 라우팅 확인. `application.yml` 의 `RewritePath` 필터 동작 확인.
- **(c) 검증**: downstream service 의 SecurityConfig 가 admin-tier role 을 인식하는지 trace.
- 가설 (a) 가 가장 likely — 044a 머지 시 `libs/java-web-servlet` 으로 의존 마이그레이션이 어느 한 service 에서 누락되었을 수 있음.

---

# Edge Cases

1. **4 실패가 단일 root cause**: ideal. (a) 가능성 가장 높음.
2. **WMS / GAP / scm 도 동일 누락**: 본 task 에서 cross-project 수정 적용. PR description 에 명시.
3. **route rewrite 가 reactive 부팅 차이로 회귀**: gateway 의 application.yml SpEL 또는 RewritePath 필터 검증 필요.
4. **admin-tier role 자체 미정의**: spec 회귀 → spec/contract 갱신 필요.

---

# Failure Scenarios

## A. 단일 root cause = 가설 (a)

`libs:java-web-servlet` 의존 누락 fix. cross-service 정합성 일관 적용.

## B. 복수 root cause

cause 별 commit 분할, 또는 sub-task 분할 (044f-1, 044f-2).

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

- **Recommended impl model**: **Sonnet** — 가설 (a) 가 단일 root cause 면 cross-service `build.gradle` 일관성 fix (단순). 복수 cause 또는 (b)(c) 인 경우 Opus 로 escalate.
- **분량 추정**: (a) 단일이면 1-3 build.gradle 변경 (작은 PR). 복수 cause 면 medium PR.
- **dependency**:
  - `선행`: TASK-MONO-044a + 044d (이미 머지됨)
  - `후속`: 없음
- **CI gating**: 본 PR 자체 영향 = `E2E (fan-platform v1 live-trio)` Job FAIL → SUCCESS. 다른 Job 영향 0.
