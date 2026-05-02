# Task ID

TASK-MONO-023

# Title

main branch 의 baseline integration / E2E 회귀 청소

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

PR #107 / #108 / #109 머지 직후 (2026-05-02) 발견된 사실:

- main 브랜치 자체에 GAP integration / E2E (gateway-master) / Frontend E2E full-stack 잡이 **장기간 회귀 상태로 누적**되어 있다.
- 단순 doc-only PR (#108, #109) 도 같은 잡들이 FAIL 처리됨 — 즉 PR 의 변경과 무관한 main baseline 회귀.
- 그동안 사용자가 로컬에서 해당 잡들을 실행하지 않았거나 GitHub Actions 알림을 끄고 있어 누적 발견 안 됨.

이 태스크 완료 후:

- main 의 모든 CI 잡 (`Build & Test`, `Frontend (3종)`, `Package boot (2종)`, `Integration (master-service)`, `Integration (GAP)`, `E2E (gateway-master)`, `Frontend E2E full-stack`) 이 안정적으로 PASS
- 신규 PR 가 변경 무관 잡 실패에 시달리지 않음 (이전 처럼 false-positive 로 인한 grinding 비용 없음)
- 회귀 재발 방지를 위한 알림 / 게이팅 정책 명시

---

# Scope

## In Scope

### 1. main 의 실제 실패 잡 진단 + 분류

- 가장 최근 main commit 의 CI 결과 분석 (현재는 squash-merge 직후 c1040e20 / d4ca24a0 / 512cbbd4)
- 실패 테스트들을 다음 카테고리로 분류:
  - **(a) 진짜 회귀** (코드 또는 테스트 fix 필요)
  - **(b) flaky test** (테스트 격리 또는 timing — re-run 으로 회피 가능)
  - **(c) infra 의존 실패** (Docker / 네트워크 / runner 환경 의존)
  - **(d) 의도적으로 깨진 채로 유지된 placeholder** (있다면 명시)

### 2. 카테고리별 fix

- **(a) 진짜 회귀**: 본 태스크 안에서 fix 또는 sub-task 로 분할 (개수에 따라)
  - 예시 추정 영역 (PR #107 grinding 중 관찰):
    - `AccountAnonymizationSchedulerIntegrationTest` actorType=user vs system 단언 불일치
    - `AdminAuditTenantScopeIntegrationTest` 크로스 테넌트 deny audit row 누락
    - 다양한 `Status 200 vs 401/500/503` (OIDC/auth 통합 회귀)
    - `BadSqlGrammar outbox_events` 마이그레이션 missing
    - `DataIntegrityViolationException Data too long` 잔여 (community-service 외)
- **(b) flaky test**: 테스트 격리 강화 (각 테스트 unique ID, `@DirtiesContext`, etc.) 또는 의도된 retry 정책
- **(c) infra**: `docker pull`, `:e2e` image build 등 CI step 보강 (TASK-MONO-015 패턴 참고)
- **(d) placeholder**: `@Disabled` 명시 + 사유 javadoc + 복원 조건 task 발행

### 3. CI 게이팅 정책

- 머지 차단 잡: `Build & Test`, `Frontend (lint/build/unit)`, `Package boot` (필수 그린)
- 머지 차단 권장 잡: `Integration (master/GAP)`, `E2E`, `Frontend E2E full-stack` (조직 정책 결정)
- branch protection 룰 추가 또는 GitHub Actions required checks 갱신
- 필요 시 `--admin` 머지 가이드 문서화 (사용자가 의도해서 우회할 때만 사용)

### 4. 알림 / 모니터링

- `.github/workflows/ci.yml` 의 main push 트리거에 Slack / 이메일 / 디스코드 webhook 추가 (선택)
- 또는 GitHub email 알림 활성화 가이드를 README dev-onboarding 에 명시

## Out of Scope

- TASK-MONO-022 (Traefik 마이그레이션) 의 영향에 따른 e2e CI 재구성 — 그 태스크 내부에서 처리
- 새 e2e 시나리오 추가 — 기존 잡 stabilize 만
- 운영 (production) 환경 모니터링 — 본 태스크는 CI 환경만
- master-service 의 1개 flaky test (`Status 201 expected vs 409`) — 이미 카테고리 (b) 로 별도 처리 가능

---

# Acceptance Criteria

- [ ] main 최신 commit 에 대해 모든 CI 잡이 SUCCESS (또는 의도적 SKIP) 인 상태
- [ ] 실패 카테고리별 처리 결과 문서화 (해당 PR 의 description 또는 본 task body 의 Implementation Notes 에 추가)
- [ ] (a) 진짜 회귀 → 모두 fix 됨 (개수 많을 시 sub-task 로 분할 후 추적)
- [ ] (b) flaky → 격리 또는 retry 정책 적용
- [ ] (c) infra → CI step 수정으로 안정화
- [ ] (d) placeholder → `@Disabled` + 사유 명시
- [ ] CI 게이팅 정책 결정 + 적용 (branch protection 또는 README 명시)
- [ ] (선택) 알림 채널 설정 가이드 추가

---

# Related Specs

- 본 태스크가 영향 주는 스펙 없음 (테스트·CI 청소 작업).
- 회귀 fix 가 spec 변경을 요구하면 (예: AccountAnonymization actorType 의도가 spec 과 다른 경우) 해당 spec 함께 수정.

---

# Related Contracts

해당 없음. (테스트 stabilization 작업)

---

# Target Service / Component

- `projects/global-account-platform/apps/account-service` (AccountAnonymization 회귀 가능성)
- `projects/global-account-platform/apps/admin-service` (AdminAuditTenantScope 회귀 가능성)
- `projects/global-account-platform/apps/community-service` (잔여 truncation / OIDC integration)
- `projects/global-account-platform/apps/auth-service` (OIDC / Status code 회귀)
- `projects/global-account-platform/apps/membership-service` (관련 회귀 시)
- `projects/global-account-platform/apps/security-service` (관련 회귀 시)
- `.github/workflows/ci.yml`

---

# Implementation Notes

## 2026-05-02 분류 작업 결과

### 데이터 소스

- main 의 가장 최근 CI run = `25257943718` (PR #110 머지 직후, post-PR-#107 GAP OIDC 머지 상태)
- 다운로드: `gh run download 25257943718 -n gap-integration-test-reports`
- 18 개 GAP integration 테스트 클래스가 FAILURE
- E2E (gateway-master) / Frontend E2E full-stack 잡은 별도 분류 (TASK-MONO-022 의 Phase 2 마이그레이션 후 재평가 — `frontend-e2e` 잡이 PORT_PREFIX 가정에 의존)

### 실패 매트릭스 (18 클래스)

| # | 테스트 클래스 | 에러 시그니처 | 카테고리 | sub-task |
|---|---|---|---|---|
| 1 | account.AccountAnonymizationSchedulerIntegrationTest | actorType=user vs system 단언 불일치 | (a) regression | 023c |
| 2 | account.AccountEventPublisherIntegrationTest | No EntityManager (트랜잭션 누락) | (a) regression | 023c |
| 3 | account.AccountRoleProvisioningIntegrationTest | 201 vs 409 (이메일 충돌 — 테스트 격리) | (b) flaky | 023a |
| 4 | account.BulkProvisioningIntegrationTest | 200 vs 500 (downstream/internal failure) | (a) regression | 023a |
| 5 | account.SignupAuthServiceDelayIntegrationTest | 201 vs 503 (auth-service mock unhealthy) | (a) regression | 023a |
| 6 | account.TenantProvisioningIntegrationTest | 201 vs 409 (테스트 격리) | (b) flaky | 023a |
| 7 | admin.AdminAuditTenantScopeIntegrationTest | expected:1 was:0 (cross-tenant deny audit row 누락) | (a) regression — 미구현 | 023c |
| 8 | admin.TenantAdminIntegrationTest | BadSqlGrammar `outbox_events` 테이블 없음 | (a) regression — Flyway 누락 | 023d |
| 9 | auth.AuthIntegrationTest | 429 vs 200 (rate limit 미동작) | (a) regression OR (b) | 023b |
| 10 | auth.OAuth2AuthCodePkceIntegrationTest | 400 vs REDIRECTION | (a) regression — OIDC 회귀 | 023b |
| 11 | auth.OAuth2AuthorizationServerIntegrationTest | 200 vs 401 | (a) regression — OIDC 회귀 | 023b |
| 12 | auth.OAuth2JpaPersistenceIntegrationTest | 200 vs 401 | (a) regression — OIDC 회귀 | 023b |
| 13 | auth.OAuth2RefreshTokenIntegrationTest | 400 vs REDIRECTION | (a) regression — OIDC 회귀 | 023b |
| 14 | auth.OAuth2RevokeIntrospectIntegrationTest | 200 vs 401 | (a) regression — OIDC 회귀 | 023b |
| 15 | auth.OAuthLoginIntegrationTest | 200 vs 503 (소셜 OAuth 모킹 503) | (a) regression | 023b |
| 16 | auth.OutboxRelayIntegrationTest | "actual not to be empty" (outbox 미발행) | (a) regression | 023d |
| 17 | community.CommentJpaRepositoryIntegrationTest | count 2 vs 1 (테스트 격리) | (b) flaky | 023e |
| 18 | community.ReactionJpaRepositoryIntegrationTest | "Expecting code to raise throwable" (테스트 격리) | (b) flaky | 023e |

### sub-task 그룹핑

5 sub-task 로 분할 (root cause 기준):

- **TASK-MONO-023a — provisioning test 분류·격리** (4 클래스: #3 #4 #5 #6)
  - 201 vs 409: 이메일/operatorId unique 격리 부족 → unique ID 사용
  - 200 vs 500: bulk provisioning 의 downstream 실패 — 진짜 회귀 또는 mock 보강
  - 201 vs 503: auth-service WireMock 503 응답 처리

- **TASK-MONO-023b — OAuth2 / OIDC 회귀 family** (7 클래스: #9 ~ #15)
  - PR #107 의 SAS 도입 (TASK-BE-251) 이후 일부 OIDC 흐름 회귀
  - 200 vs 401, 400 vs REDIRECTION, 503 (downstream) 다양
  - 공통 root cause 가능: `oauth_clients` seed 누락 또는 issuer 불일치

- **TASK-MONO-023c — Audit / Anonymization 회귀** (3 클래스: #1 #2 #7)
  - AccountAnonymization 의 actorType 분기 회귀 (system 배치 vs user)
  - AccountEventPublisher 의 트랜잭션 컨텍스트 누락
  - AdminAuditTenantScope 의 cross-tenant deny audit row INSERT 미구현 (TASK-BE-262 의 spec 과 어긋남)

- **TASK-MONO-023d — Outbox 관련 실패** (2 클래스: #8 #16)
  - TenantAdmin: `outbox_events` 테이블이 통합 테스트 환경에 없음 (Flyway 마이그레이션 누락)
  - OutboxRelay: outbox 행이 비어있음 (publisher 호출 누락 또는 transaction commit 시점)

- **TASK-MONO-023e — Community JPA 격리** (2 클래스: #17 #18)
  - CommentJpa / ReactionJpa: 테스트 간 데이터 누수, count 불일치, exception 미발생
  - `@DataJpaTest` 또는 명시적 `repo.deleteAll()` 보강

### 카테고리 통계

| 카테고리 | 개수 | 처리 방향 |
|---|---|---|
| (a) 진짜 회귀 | 12 | sub-task 로 fix (023a/b/c/d) |
| (b) flaky / 격리 | 6 | sub-task 로 격리 강화 (023a/e) |
| (c) infra | 0 | (없음 — Testcontainers 환경 안정) |
| (d) placeholder | 0 | (없음) |

### 정책 결정

#### CI 게이팅

- **머지 차단 (필수 그린)**: `Build & Test`, `Frontend (lint/build/unit/E2E smoke)`, `Package boot` — 본 태스크 범위 외 모두 그린
- **머지 차단 권장 (배포 전 그린)**: `Integration (master/GAP)`, `E2E`, `Frontend E2E full-stack` — 본 sub-task 들 완료 후 활성화 권장
- **branch protection**: 본 태스크에서는 룰 변경하지 않음 (필수 잡 그린 상태이므로 추가 게이팅 불필요). sub-task 5건 모두 머지 후 별도 작업으로 활성화 결정.

#### 알림

- 추가 알림 채널 (Slack/이메일) 설정은 본 태스크 범위 밖. 별도 follow-up 으로 분리.
- 사용자가 GitHub email 알림 활성화 여부는 사용자 자율.

### TASK-MONO-022 와의 순서 의존성

- TASK-MONO-022 (Phase 1 Traefik infra) — **이미 머지됨** (PR #111). 본 태스크의 sub-task 들은 Traefik infra 와 독립.
- TASK-MONO-024 (Phase 2 마이그레이션) — `frontend-e2e` 잡이 PORT_PREFIX 가정에 의존. 본 태스크 sub-task 들 완료 후 Phase 2 진행 시 e2e 잡 수정 동시 처리.

### 본 태스크의 산출물

- **이 분류 매트릭스** (위) + 정책 결정
- **5개 sub-task 파일**: `tasks/ready/TASK-MONO-023a~e-*.md`
- **TASK-MONO-023 자체는 review → done** 으로 이동 (분류·정책 작업 완료)
- 각 sub-task 가 후속 PR 로 진행

---

# Edge Cases

- 어떤 fail 이 (a) vs (b) 인지 모호한 경우 → 5회 연속 PASS 시 (b) 로 분류, 그 외 (a) 로 분류
- TASK-MONO-022 마이그레이션이 일부 e2e 잡을 obsolete 시키면 → 해당 jobs 는 본 태스크 범위에서 제외 (TASK-MONO-022 가 책임)
- 회귀 fix 가 spec 변경을 요구할 때 → spec 변경 PR 먼저, 후속 fix PR 별도 (CLAUDE.md § Source of Truth Priority)
- branch protection 룰 추가 시 사용자가 직접 GitHub UI 에서 수동 적용 필요 → CLAUDE.md/README 안내만 가능

---

# Failure Scenarios

- **분류 시 (a) 가 너무 많아 본 태스크 안에 못 담음** → 모두 sub-task 발행 후 본 태스크는 "분류·정책" 만 done 처리
- **flaky 가 영구 flaky (재현 불가)** → 일단 retry 정책 적용 + `@Disabled` 처리 + 별도 추적 task
- **CI 환경 자체 결함 (GitHub runner Docker 28 등)** → workaround 적용 + GitHub 공식 fix 대기

---

# Test Requirements

- 본 태스크 자체는 코드 변경 없이 분류 + 정책 수립이 메인 산출물
- sub-task 들에서 각각의 회귀에 대한 단위/통합 테스트 추가 (해당 sub-task 책임)

---

# Definition of Done

- [ ] main 최신 commit 의 CI 잡 결과 분석 완료 + 카테고리 분류 문서화
- [ ] 카테고리별 처리 (fix 또는 sub-task 분할) 완료
- [ ] CI 게이팅 정책 결정 + 적용 (branch protection 또는 명시적 README)
- [ ] (선택) 알림 채널 가이드 추가
- [ ] 모든 sub-task 가 발행되어 추적 가능
- [ ] Ready for review
