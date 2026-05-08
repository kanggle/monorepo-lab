# Task ID

TASK-MONO-046-7a

# Title

GAP auth-service SAS 7 deferred IT — Cluster A (RT rotation/reuse/revoke 3) + Cluster C (OAuth callback Google/Kakao/Microsoft happy + Microsoft preferredUsername 4) 재활성화 (046-7 11-cycle burn 학습 위에서)

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

[TASK-MONO-046-7](../done/TASK-MONO-046-7-auth-service-sas-deferred-8.md) PR #264 가 11-cycle burn 으로 8 deferred 중 1 회복 (Cluster B userinfo tenant_id) + 7 재 `@Disabled`. 그 과정에서 4 architectural anti-pattern + 6-cycle 임계값 학습이 확정됨. 본 task 는 그 학습 위에서 **7 method root cause 별 분리 fix + 회귀 0** 를 달성한다.

**전제**: 환경 회복 (CI Linux runner 또는 안정적 Docker 환경 — 메모리 `project_testcontainers_docker_desktop_blocker` 회복 후). burn 비용 상한 = 6 cycle (이상 시 design 재평가).

---

# 046-7 학습 요약 (이 task 의 입력)

## 4 architectural anti-pattern

11 cycle 동안 deterministic 하게 반증된 패턴 — fix 시 **건드리면 안 되는** boundary:

### A1. SAS Customizer lifecycle 위반
SAS (Spring Authorization Server) 의 `OAuth2TokenCustomizer<JwtEncodingContext>` / `OAuth2TokenCustomizer<OAuth2TokenClaimsContext>` 는 SAS 라이프사이클의 특정 단계에서만 호출됨. 외부 코드가 customizer 를 참조해 직접 호출하거나 customizer 위에서 transactional boundary 를 시도하면 SAS 가 customize 단계에서 보장하는 read-only context 가 깨짐 (`detachedRotation` 같은 임시 상태 leak).

**규칙**: customizer 안에서 DB write 하지 말 것. customizer 는 claim assemble 만 한다.

### A2. DomainSync vs persistRotation race
RefreshToken rotation 시 두 경로가 같은 row 를 건드림:
- (a) `RefreshTokenRotationService.persistRotation()` — 새 RT row 를 INSERT + 이전 row `revoked=true` UPDATE
- (b) `DomainSyncListener` 의 lazy account-domain sync — account-service 에서 도메인 데이터 fetch 시 옵저버블한 RT 상태를 참조

(b) 가 (a) commit 전에 실행되면 (a) 의 lock 보유한 row 를 (b) 가 다시 lock 시도 → SAS test (특히 PublicClient 시나리오) 에서 sporadic deadlock 또는 wrong-row 회복.

**규칙**: persistRotation 의 TX 안에서 DomainSync 호출하지 말 것. 후처리 hook 으로만.

### A3. 수동 instantiation 의 `@Transactional` 적용 불가
`new SomeService(...)` 로 수동 만든 인스턴스는 Spring proxy 가 아니므로 `@Transactional` annotation 이 무시됨. 11 cycle 의 cycle 4 에서 발견 — fix candidate 로 SecurityFilter 안에서 service 를 수동 인스턴스화한 경로가 있었고 transaction context 가 영속되지 않음.

**규칙**: `@Transactional` 이 필요한 service 는 반드시 `@Component` + DI. 수동 instantiation 은 stateless utility 한정.

### A4. Test order pollution (cycle 6 → 7+ regression)
Cycle 6 에서 Cluster B 1 회복 + 7 disable 의 best state 도달. Cycle 7 부터는 더 많이 enable 시도 시 항상 regression — 즉 **3 cluster (A/B/C) 를 한 cycle 에 같이 enable 하면 test order 의존성으로 후속 IT class 의 컨텍스트가 오염됨** (Spring TestContext `ContextCache` 의 `@DirtiesContext` 누락 또는 SAS 컴포넌트 fresh-instance 보장 부재로 추정).

**규칙**: 한 cycle 에 한 cluster 만 enable. cluster 별 separate `@DirtiesContext(AFTER_CLASS)` 강제.

## 6-cycle 임계값

| Cycle | 진행 | 결과 |
|---|---|---|
| 1-2 | 초기 reproduction + 1차 fix candidate | partial recovery |
| 3-5 | fix iteration | A1/A2/A3 anti-pattern 각각 deterministic 반증 |
| **6** | **best state — Cluster B 회복 + 7 disable + 회귀 0** | **PR #264 base** |
| 7+ | 추가 enable 시도 | 항상 regression — A4 pattern 확정 |

→ 본 task 의 burn 비용 상한 = **6 cycle**. 초과 시 fix 전략 재평가 (별도 ADR 또는 IT 일부를 unit test 로 강등).

---

# Scope

## In Scope

### Phase 1 — Cluster A 단독 cycle (RT rotation / reuse-detection / revoke 3 method)

**범위**: `RefreshTokenRotationIntegrationTest` (또는 동등 위치) 의 3 method —
- `rotation_succeeds_and_revokes_old`
- `reuse_detection_revokes_chain`
- `revoke_invalidates_subsequent_use`

**가설 (1차)**: A2 (DomainSync vs persistRotation race) — `RefreshTokenRotationService.persistRotation()` 안에서 또는 같은 TX 에서 DomainSyncListener 가 호출되어 RT row lock contention.

**Phase 1.1**: `git log --oneline tasks/done/TASK-MONO-046-7-*.md` PR #264 diff 의 `RefreshTokenRotationService` + `DomainSyncListener` 변경 grep — A2 회피 패턴이 어디까지 적용됐는지 확인. PR #264 가 Cluster B 만 회복했으므로 A 영역 fix 는 미적용 가능.

**Phase 1.2**: `RefreshTokenRotationService` 의 `persistRotation()` 메서드에서 DomainSyncListener 호출이 TX 안인지 검증. 안이면 → `TransactionSynchronization.afterCommit()` hook 또는 `ApplicationEventPublisher` event 로 외화 (post-commit listener 패턴).

**Phase 1.3**: 3 method `@Disabled` 제거 + 단일 cycle 실행 (Cluster A 만). 결과:
- 3/3 PASS → ✅ Phase 1 종결
- 부분 PASS / fail → 가설 #1 확정 후 fix iteration. 가설 #2 (RT row lock 자체 acquisition order issue) 후속 검토. cycle 상한 = 3.

### Phase 2 — Cluster C 단독 cycle (OAuth callback Google/Kakao/Microsoft happy + Microsoft preferredUsername 4 method)

**범위**: `OAuthLoginIntegrationTest` (또는 동등) 의 4 method —
- `googleCallback_succeeds`
- `kakaoCallback_succeeds`
- `microsoftCallback_succeeds`
- `microsoftPreferredUsername_picksFromClaim`

**가설 (1차)**: A1 (SAS Customizer lifecycle 위반) — OAuthLogin 의 access-token mint 단계에서 `OAuth2TokenCustomizer` 가 외부 provider claim 을 fetch 하려고 시도. customizer context 는 read-only 라 외부 IO 는 deadlock / context corruption 가능.

**Phase 2.1**: PR #264 diff 의 `OAuth2TokenCustomizer` 구현체 grep — 외부 IO (account-service / WireMock) 호출이 customizer 안에 있는지 확인. 있으면 → claim builder 단계 이전 (e.g., `OAuth2AuthenticationContext` post-login) 으로 이동.

**Phase 2.2**: 4 method 의 WireMock fixture 가 PR #218 (TASK-MONO-044c-1) 의 `@DirtiesContext(AFTER_CLASS)` 패턴을 따르는지 확인. 안 따르면 → 5 IT class 모두 `@DirtiesContext(AFTER_CLASS)` 추가 (account-service URL fixture leak 방지).

**Phase 2.3**: 4 method `@Disabled` 제거 + 단일 cycle 실행 (Cluster C 만). 결과:
- 4/4 PASS → ✅ Phase 2 종결
- 부분 PASS / fail → 가설 #2 (Microsoft preferredUsername 의 claim spec drift — `email` vs `preferred_username` 우선순위) 검토. cycle 상한 = 3.

### Phase 3 — Cluster A + Cluster C combined cycle (회귀 검증)

**Phase 3.1**: Phase 1 + Phase 2 의 모든 enable 을 한 번에 적용 + cycle 1 회 실행.
- A4 pattern 학습대로 sporadic regression 가능 → `@DirtiesContext(AFTER_CLASS)` 의 cluster 간 isolation 효과 검증.
- 7/7 PASS → ✅ Phase 3 종결
- regression → 한 cycle 더 (Phase 3.2): IT class 단위 isolation 추가 (`Isolated` JUnit 5 annotation 또는 cluster 별 별 build target 분리).

**Phase 3.3**: CI `Integration (GAP)` Job 1 회 PASS 확인 (CI Linux runner 가 unblocked 인 가정).

**총 cycle 상한**: Phase 1 (3) + Phase 2 (3) + Phase 3 (2) = **8 cycle 명목 / 6 cycle 보수 가이드**. 6 cycle 초과 시 design 재평가.

## Out of Scope

- 046-7 의 Cluster B (userinfo tenant_id 1) — PR #264 에서 회복 완료
- 046-8 / 046-8a 영역 (security-service consumer pipeline) — 별 task
- production behaviour 변경 (예: 새 OAuth provider 추가, claim spec 변경) — 본 task 는 fix-only
- IT 일부의 unit test 강등 — 6 cycle 초과 시 별 ADR 로 분리
- account-service / WireMock fixture 외부 의존 fix (PR #218 패턴 답습 한도 외)

---

# Acceptance Criteria

- [ ] AC-01 — Cluster A 3 method `@Disabled` 제거 + 단독 cycle PASS
- [ ] AC-02 — Cluster C 4 method `@Disabled` 제거 + 단독 cycle PASS
- [ ] AC-03 — Cluster A + C combined cycle 7/7 PASS
- [ ] AC-04 — CI `Integration (GAP)` Job 1 회 PASS (60/60 PASS / 0 FAIL / 0 skipped, 단 RC#3 sporadic 1 disabled 유지)
- [ ] AC-05 — 4 architectural anti-pattern 중 어느 하나도 fix 과정에서 위반하지 않음 (A1: customizer 에 IO 추가 X / A2: persistRotation TX 안 DomainSync 호출 X / A3: 수동 instantiation 의 `@Transactional` 의존 X / A4: cluster 간 `@DirtiesContext` 누락 X)
- [ ] AC-06 — 총 burn ≤ 6 cycle (8 cycle 명목 한도 도달 시 design 재평가 ADR 작성 후 보고)
- [ ] AC-07 — production code 변경 시 회귀 0 (auth-service 60/60 PASS 유지)
- [ ] AC-08 — D4 churn freeze 면제 카테고리 (regression fix path) 만 변경 — `libs/`/`platform/`/`rules/`/`.claude/` shared 영역 churn 0

---

# Related Specs

- `tasks/done/TASK-MONO-046-7-auth-service-sas-deferred-8.md` — 11-cycle burn 결과 + 4 anti-pattern 학습
- `tasks/done/TASK-MONO-044c-1-gap-auth-oauth-pkce-circuitbreaker-residue.md` — `@DirtiesContext(AFTER_CLASS)` 패턴 (PR #218)
- `projects/global-account-platform/specs/services/auth-service/architecture.md` — SAS 통합 + RT rotation 디자인
- `projects/global-account-platform/specs/services/auth-service/idempotency.md` — RT 멱등성

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/auth-api.md` — RT rotation / OAuth callback API
- `projects/global-account-platform/specs/contracts/http/userinfo-api.md` — userinfo (Cluster B 회복 base)

---

# Edge Cases

- **Phase 1 가설 #1 (A2 race) 가 deterministic 반증되면**: 가설 #2 (RT row lock acquisition order 문제 — id ascending 보장 누락) 로 전환. PR #208 (TASK-MONO-044c) 의 9 deterministic fix 패턴 참조.
- **Phase 2 가설 #1 (A1 lifecycle 위반) 가 반증되면**: 가설 #2 (Microsoft `preferred_username` vs `email` claim 우선순위 spec drift) 로 전환. OAuth provider 의 actual JWT 샘플 capture 후 contract 확정.
- **A4 pattern 회피 못 하면 Phase 3 에서 IT 분할**: cluster 별 별 Gradle test target 분리 (`auth-service:integrationTestClusterA` 등). build.gradle 변경이 D4 churn freeze 영역 (root build files 면제 외)인지 검증 필요 — `apps/global-account-platform/auth-service/build.gradle` 은 project-internal 이므로 freeze 외.
- **CI Linux runner 도 reproduce 안 됨**: env blocker 가 Docker Desktop 한정이 아닌 더 깊은 issue. 별 incident report + design 재평가 ADR.

# Failure Scenarios

- **6 cycle 초과해도 미해결**: design 재평가 ADR 작성 후 옵션 — (a) IT 7 method 를 unit test 로 강등 (port fakes), (b) integration test 분할 (cluster 별 separate build target), (c) accept-as-known-issue + nightly task 화 (PR #218 RC#3 패턴). 사용자 결정 입력.
- **Production code 변경 시 회귀 발생**: auth-service 60/60 회귀 가드 깨짐 → 즉시 revert + cycle 재시작. 회귀 깨진 root cause 진단 우선.
- **A2 fix 가 다른 lazy DomainSync 경로를 깬다면**: DomainSyncListener 호출처 audit 후 후처리 hook 패턴 일괄 적용. 별 sub-task 분리 가능.
- **CI Linux runner 가 reproduce 못 하지만 local 만 reproduce**: Rancher Desktop env blocker 영향 미해결 — 본 task scope 외. TASK-MONO-046-8 의 env blocker 와 같은 카테고리 (`project_testcontainers_docker_desktop_blocker` 메모리 추적 중).

---

# Notes

- **모델 권장**: 분석=Opus 4.7 / 구현=Opus 4.7 — SAS lifecycle / RT rotation / OAuth callback 의 3 영역 cross-cut + 4 anti-pattern 회피 + 6-cycle 임계값 관리 = complex domain work. Sonnet 으로 burn 시 anti-pattern 위반 위험.
- **D4 churn freeze 면제 근거**: regression fix path 카테고리 자연 확장. `apps/global-account-platform/auth-service/**` (project-internal) 변경만 예상 — shared 영역 (`libs/`/`platform/`/`rules/`/`.claude/`) 변경 0 가 AC-08.
- **연관 메모리**: `project_046_7_11_cycle_burn` (anti-pattern + 6-cycle 임계값), `project_046_series_close` (046 시리즈 종결 패턴), `project_testcontainers_docker_desktop_blocker` (env blocker), `project_ci_path_filter_045` (CI Linux runner = only reproduction path).
