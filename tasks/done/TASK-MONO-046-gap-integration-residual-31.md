# Task ID

TASK-MONO-046

# Title

GAP integration job 잔재 31건 fix (auth-service 12 + security-service 19, 044c-1 미완 영역)

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

[TASK-MONO-044c-1](../done/TASK-MONO-044c-1-...md) 머지 후 main commit `3a058d90` 에서 PR #222 의 commit 메시지가 "main CI 4 회귀 청소 시리즈 완전 종결" 을 선언했으나, 실제로는 `Integration (global-account-platform, Testcontainers)` Job 이 여전히 FAILURE.

CI run `25371958651` (main HEAD) + `25372328277` (PR #223 spec) 에서 동일 잔재 31건 확인:

- **auth-service**: 60 tests, 12 failed
  - OAuth2AuthCodePkceIntegrationTest > userinfo: valid access_token → 200 with sub + email + tenant_id
  - OAuth2RefreshTokenIntegrationTest > 6 tests (authCode flow / rotation / tenant_id claim / reuse detection / unknown token / cross-tenant)
  - OAuth2RevokeIntrospectIntegrationTest > authCode flow: refresh_token → revoke → introspect → active=false
  - + 4 more (truncated)

- **security-service**: 20 tests, **19 failed**
  - CrossTenantVelocityIntegrationTest
  - DetectionE2EIntegrationTest
  - + 17 more (이전 044 시리즈에서 한 번도 다루지 않은 영역 — 새 발견)

본 task 가 fix 후 `Integration (GAP)` Job FAILURE → SUCCESS, 본격 main green 회복.

---

# Scope

## In Scope

### auth-service 12건

대부분 OAuth2 RefreshToken / RevokeIntrospect / AuthCodePkce userinfo 영역. 044c 의 BCrypt re-pin + 044c-1 의 RC#1/2/3 fix 가 일부만 해소했고 나머지가 잔재. 가설:

- (a) 단일 root cause — 예: refresh_token store 의 tenant_id 주입 path 회귀, 또는 SAS 1.4.1 + JpaRegisteredClientRepository 통합 시 token rotation 시점 race
- (b) 복수 cause — RefreshToken / RevokeIntrospect / AuthCodePkce userinfo 가 각각 다른 결함

### security-service 19건

`CrossTenantVelocityIntegrationTest` + `DetectionE2EIntegrationTest` 가 19/20 fail. 단일 root cause 가능성 높음:

- (a) security-service Spring context 부팅 시 의존성 누락 (libs/java-web-servlet 분리 후 의존 갱신 누락? — TASK-MONO-044f 와 동일 패턴 가능)
- (b) Velocity rule / Detection 의 tenant scoping 회귀
- (c) Outbox / Kafka producer 회귀

### 진단 + fix

- 위 두 서비스 별 stack trace 수집 (CI artifact 또는 로컬 reproduce)
- root cause 분류 + fix 전략 PR description 기록
- 단일 root cause 면 1 commit, 복수면 cause 별 commit
- production 코드 회귀가 root cause 면 production fix 도 본 task 범위 안

## Out of Scope

- gateway-service IT (이전 회귀 fix 됨, 1/34 잔재는 별 task)
- community-service IT (044c 로 fix 됨)
- account-service IT (044c-1 로 fix 됨)
- 다른 baseline regression (예: nightly fullstack 첫 run 결과로 발견되는 새 회귀)
- TASK-MONO-045 의 path-filter / nightly cron 효과 측정 — 별 task (045 done 후 평균 CI 시간)

---

# Acceptance Criteria

## 부팅 + 통과

1. `:projects:global-account-platform:apps:auth-service:integrationTest` PASS (60/60)
2. `:projects:global-account-platform:apps:security-service:integrationTest` PASS (20/20)
3. main CI 의 `Integration (GAP)` Job FAILURE → SUCCESS

## 진단 + 분류

4. PR description 에 root cause 분류 + fix 전략 기록 (auth 12 + security 19 → 단일 / 복수 cause 결정 + 근거)
5. security-service 19건 의 Spring context 부팅 단계 검증 — libs/java-web-servlet 의존성 누락 가능성 우선 점검

## 회귀 0

6. 044c / 044c-1 / 044f 시리즈에서 fix 한 다른 영역 회귀 0
7. 회귀 보고서 `knowledge/incidents/2026-05-05-ci-regression.md` 에 본 task 후속 결과 단락 추가

---

# Related Specs

- [TASK-MONO-044 진단 보고서](../../knowledge/incidents/2026-05-05-ci-regression.md)
- [TASK-MONO-044c (BCrypt re-pin)](../done/TASK-MONO-044c-...md)
- [TASK-MONO-044c-1 (GAP RC#1/2/3 잔존 17건)](../done/TASK-MONO-044c-1-...md) — 직접 선행
- [TASK-MONO-044a (libs/java-web split)](../done/TASK-MONO-044a-libs-java-web-servlet-leak-fix.md) — security-service 가설 (a) 의 가능 원인
- `projects/global-account-platform/specs/services/auth-service/`
- `projects/global-account-platform/specs/services/security-service/`
- `projects/global-account-platform/specs/contracts/http/auth-api.md`

---

# Related Contracts

- `auth-api.md` § OAuth2 Refresh Token / Revoke / Introspect / UserInfo
- security-api.md (있으면)

---

# Target Service / Component

- `projects/global-account-platform/apps/auth-service/src/main/java/...` (production code if regression)
- `projects/global-account-platform/apps/auth-service/src/test/java/...integration` (test fixture if test-only)
- `projects/global-account-platform/apps/security-service/src/main/java/...`
- `projects/global-account-platform/apps/security-service/src/test/java/...integration`
- `projects/global-account-platform/apps/security-service/build.gradle` (가설 (a) 시 의존성 추가)

---

# Implementation Notes

- **첫 단계**: `:projects:global-account-platform:apps:security-service:integrationTest` 로컬 실행 → stack trace 수집. 19/20 fail 이라 cluster 의 시작 단계 결함 (context init / autowire / Flyway) 가능성 큼. `initializationError` 패턴이면 044f 와 동일 — `libs:java-web-servlet` 의존성 누락 의심.
- 두 번째: auth-service 12건 stack trace. RefreshToken 6건이 가장 많아서 거기부터.
- 가설 (a) cross-check: `grep -rn "libs:java-web" projects/global-account-platform/apps/*/build.gradle` 로 `libs:java-web` (parent now-empty) vs `libs:java-web-servlet` 분포 확인. 누락 service 발견 시 일관 적용.
- 검증 명령:
  ```
  ./gradlew :projects:global-account-platform:apps:auth-service:integrationTest
  ./gradlew :projects:global-account-platform:apps:security-service:integrationTest
  ./gradlew :projects:global-account-platform:apps:security-service:check  # 회귀 0 검증
  ```

---

# Edge Cases

1. **security-service 가 단일 root cause** (Spring context 부팅 단계): 1 build.gradle 또는 1 Flyway 변경. 작은 PR.
2. **auth-service 12건이 단일 root cause**: 1 production code 또는 1 SAS config 변경.
3. **두 서비스가 무관한 root cause**: cause 별 commit 분할.
4. **production 코드 회귀**: PR scope 가 커지면 sub-task 분할 (046-1, 046-2) 검토.

---

# Failure Scenarios

## A. security-service 19건 = libs/java-web-servlet 누락 (가설 (a))

`build.gradle` 에 `libs:java-web-servlet` 의존성 추가 1줄. 044a 의 cross-project 정합성 패치 누락 cleanup. 작은 PR.

## B. auth-service 12건이 SAS 1.4.1 + JpaRegisteredClientRepository tracing 영역

044c 의 deferred 영역. Docker 환경에서만 재현되므로 로컬 트레이싱 어려움. 임시: skip 또는 `@Disabled` 후 별 task 로 분리 (046-1).

## C. CI sporadic flakiness (Testcontainers 자원 한계)

deterministic fail 만 fix 후 sporadic 은 별 task 로 이관 (TASK-MONO-044 § AC #8 영역).

---

# Test Requirements

- 두 서비스 integrationTest 모두 PASS (로컬 + CI)
- main CI `Integration (GAP)` Job 다음 run SUCCESS 검증
- 회귀 보고서 단락 갱신

---

# Definition of Done

- [ ] auth-service 12건 + security-service 19건 stack trace 수집
- [ ] root cause 분류 + fix 전략 PR description 기록
- [ ] cause 별 fix commit
- [ ] 두 서비스 integrationTest 로컬 PASS
- [ ] main CI `Integration (GAP)` Job SUCCESS 검증
- [ ] 회귀 보고서 단락 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — auth-service OAuth2 RefreshToken 영역 + security-service Velocity/Detection 도메인 동시 분석. security-service 단일 root cause 면 사후 Sonnet downgrade.
- **분량 추정**: 가설 (a) 단일이면 작은 PR (build.gradle 수정 + test fixture 정리). 복수 cause 면 medium PR.
- **dependency**:
  - `선행`: TASK-MONO-044a + 044c + 044c-1 (모두 머지됨)
  - `후속`: 본 task 머지 시 main `Integration (GAP)` Job 처음으로 SUCCESS — main green 회복 milestone
- **CI gating**: 본 PR 자체 영향 = `Integration (GAP)` Job FAIL → SUCCESS. 다른 Job 영향 0.
- **시기**: 045 머지 후 path-filter 활성화로 GAP 변경 PR 만 GAP IT 활성됨. 본 task 의 PR 자체는 path-filter 룰로 GAP IT job 활성 보장.
