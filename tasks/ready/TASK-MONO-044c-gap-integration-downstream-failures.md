# Task ID

TASK-MONO-044c

# Title

GAP integration job downstream 결함 33건 fix (servlet leak 해소 후 노출)

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

[TASK-MONO-044a](../done/TASK-MONO-044a-libs-java-web-servlet-leak-fix.md) 가 `libs/java-web` servlet leak 을 해소한 결과 GAP gateway-service `integrationTest` 의 부팅이 회복되었으나, 동일 Job (`Integration (global-account-platform, Testcontainers)`) 안의 *다른* 서비스 integrationTest 33건이 실제 test 실패로 가시화. servlet leak 으로 인한 부팅 차단 상태에서는 gateway-service 가 가장 먼저 fail 했고 후속 서비스 테스트가 실행 자체를 못 하면서 실패가 묻혀 있었음.

CI run `25338898652` (PR #201, 044a) 에서 노출된 분포:
- `community-service:integrationTest` — 51 tests, 3 failed
- `account-service:integrationTest` — 45 tests, 2 failed
- `auth-service:integrationTest` — 60 tests, 28 failed (auth-service 가 압도적)

본 task 가 fix 후 `Integration (GAP)` Job 의 0 failure → SUCCESS.

---

# Scope

## In Scope

3 service 의 33 failing tests 를 조사 + fix. Service 별 진단 우선:

### community-service (3건)

- `CommentJpaRepository 통합 테스트 > countsGroupedByPostId — soft-delete 행은 그룹 카운트에서 제외`
- `CommentJpaRepository 통합 테스트 > countByPostIdAndDeletedAtIsNull — 활성 댓글만 카운트, soft-delete 제외`
- `ReactionJpaRepository 통합 테스트 > 복합 PK — (post_id, account_id) 중복 INSERT → DataIntegrityViolationException`

→ JPA repository 레이어. 공통 가설: soft-delete 컬럼 / 복합 PK 제약 조건 schema drift.

### account-service (2건)

- `AccountRoleProvisioning integration — TASK-BE-255 > account 행 삭제 시 account_roles 가 ON DELETE CASCADE 로 함께 사라진다`
- `BulkProvisioning 통합 테스트 — TASK-BE-257 > 5건 정상 생성 → 200 + created=5 + outbox 5건 + audit 1건`

→ provisioning 도메인. 가설: outbox/audit 테스트 픽스처 또는 cascade 제약 회귀.

### auth-service (28건)

- `AuthIntegrationTest > TASK-BE-063: duplicate credential create returns 409` (1건)
- `OAuth2AuthCodePkceIntegrationTest` (5건 — authorize/token/userinfo/PKCE 검증)
- `OAuth2AuthorizationServerIntegrationTest` (1건 — client_credentials + tenant_id claim)
- `OAuth2JpaPersistenceIntegrationTest` (4건 — Flyway V0008 system seed scopes / JPA-backed client / oauth2_authorization 테이블 / revoke→introspect lifecycle)
- `OAuth2RefreshTokenIntegrationTest` (6건 — refresh_token 발행 + 회전 + 재사용 감지 + 타 tenant rejection)
- `OAuth2RevokeIntrospectIntegrationTest` (6건 — revoke + introspect E2E)
- `OAuthLoginIntegrationTest` (5건 — Google/Microsoft/Kakao social login 콜백 + outbox)

→ OAuth2/SAS 영역에 대규모 회귀. 가설: 단일 root cause 가능성 매우 높음 (예: Spring Authorization Server config drift, Flyway seed 누락, 또는 fixture/clock 회귀).

## Out of Scope

- 33건이 단일 root cause 가 아니면 본 task 안에서 sub-task 분할 가능 (구현 단계 결정)
- gateway-service `integrationTest` (044a 로 이미 PASS — 본 task 는 다른 서비스만)
- nightly CI 회귀 방지 자동화 (TASK-MONO-044 의 AC #8 별도 후속)
- production 도메인 로직 변경 (테스트 회귀 fix 가 production 코드 회귀를 우회한 것으로 판명되면 stop & report)

---

# Acceptance Criteria

## 부팅 + 통과

1. `:projects:global-account-platform:apps:community-service:integrationTest` PASS (51/51)
2. `:projects:global-account-platform:apps:account-service:integrationTest` PASS (45/45)
3. `:projects:global-account-platform:apps:auth-service:integrationTest` PASS (60/60)
4. main CI 의 `Integration (global-account-platform, Testcontainers)` Job FAILURE → SUCCESS

## 진단 + 분류

5. PR description 에 33건 의 root cause 분류표 (단일/복수) 와 fix 전략 기록
6. 단일 root cause 라면 1 commit 으로, 복수 cause 라면 cause 별 commit 분할

## 회귀 0

7. 044a/044b 머지 이후 새로 노출된 다른 서비스 회귀 없음
8. 진단 보고서 `knowledge/incidents/2026-05-05-ci-regression.md` 에 GAP downstream 부분 후속 결과 기록 (1 단락 추가)

---

# Related Specs

- [TASK-MONO-044 진단 보고서](../../knowledge/incidents/2026-05-05-ci-regression.md) § Job 1
- [TASK-MONO-044a (servlet leak fix)](../done/TASK-MONO-044a-libs-java-web-servlet-leak-fix.md) — 본 task 가 노출시킨 결함의 직접 선행
- `projects/global-account-platform/specs/services/community-service/`
- `projects/global-account-platform/specs/services/account-service/`
- `projects/global-account-platform/specs/services/auth-service/`
- `projects/global-account-platform/specs/contracts/http/auth-api.md` (OAuth2 + introspect/revoke)
- `projects/global-account-platform/db/V0008__*.sql` (system seed scopes — 의심 1)

---

# Related Contracts

- `auth-api.md` § OAuth2 Clients / Scopes / Endpoints
- `account-api.md` § Provisioning + Bulk Provisioning

---

# Target Service / Component

- `projects/global-account-platform/apps/community-service/src/test/...integrationTest`
- `projects/global-account-platform/apps/account-service/src/test/...integrationTest`
- `projects/global-account-platform/apps/auth-service/src/test/...integrationTest`
- (root cause 가 production 코드면 해당 main src 도 변경)

---

# Implementation Notes

- **첫 단계**: auth-service 28건의 stack trace 를 모두 수집 → 단일 vs 복수 root cause 판정.
- 단일 root cause 가능성: (a) Spring Authorization Server config / scope seed 갱신 누락, (b) Testcontainers 환경 변수 / Flyway profile 회귀, (c) tenant_id 주입 path 회귀 — TASK-BE-258/259 후속 영역.
- 33건 동시 실패가 단일 origin commit 으로 추적 가능한지 `git log --since=2026-04-30 --name-only -- projects/global-account-platform/apps/auth-service` 로 확인 권장.
- account-service 2건은 TASK-BE-255/257 명시적 referenced — `git log --grep="TASK-BE-255\|TASK-BE-257"` 로 도입 PR 추적.
- community-service 3건은 모두 JPA repository soft-delete / 복합 PK — DB schema 또는 entity mapping 변경 검토.

---

# Edge Cases

1. **단일 root cause 가 production 코드 회귀**: production fix 가 본 task 범위 안. PR description 에 명시.
2. **복수 root cause 가 서로 무관**: cause 별 sub-task 분할 (044c-1/c-2/c-3) 또는 cause 별 commit 단위로 분할.
3. **테스트 자체가 outdated 인 경우**: spec/contract 가 변경되었는데 테스트가 따라가지 않은 case — spec 갱신 또는 테스트 동기화. 어느 쪽인지 PR description 에 명시.
4. **Flyway V0008 seed 누락**: 044a 머지 후 첫 노출이지만 도입 commit 은 044a 보다 훨씬 이전 — `git log --all --oneline -- projects/global-account-platform/db/V0008*` 로 추적.

---

# Failure Scenarios

## A. 33건이 모두 단일 root cause

이상적. 1 commit fix + 1 회귀 보고서 단락.

## B. 복수 cause + 일부는 production 코드 회귀

production fix 가 함께 들어감. PR scope 가 커지면 sub-task 분할 검토.

## C. 일부 테스트가 환경 의존 (CI runner 자원 / Docker version) 으로 sporadic

TASK-MONO-044 § AC #8 (nightly 회귀 방지) 영역. 본 task 에서는 deterministic 부분만 fix 하고 sporadic 은 별도 후속 이관.

---

# Test Requirements

- 3 서비스 integrationTest PASS (각 서비스 단독 실행 + 전체 batch 실행 모두)
- root cause 분류 + fix 전략을 PR description 에 기록
- main CI `Integration (GAP)` Job 의 다음 run SUCCESS 확인
- 회귀 보고서 `knowledge/incidents/2026-05-05-ci-regression.md` § GAP 단락 갱신

---

# Definition of Done

- [ ] 33건 stack trace 수집 + root cause 분류
- [ ] cause 별 fix commit
- [ ] 3 서비스 integrationTest 로컬 PASS
- [ ] main CI `Integration (GAP)` Job SUCCESS 검증
- [ ] 회귀 보고서 단락 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — 33건의 root cause 분류 + Spring Authorization Server / OAuth2 영역 도메인 분석 + 잠재적 production 코드 회귀 판정 동시 수행. cause 가 단일이면 사후 Sonnet 으로 다운그레이드 가능.
- **분량 추정**: 단일 root cause 면 작은 PR (1 config 또는 1 seed). 복수면 sub-task 분할 후 medium PR 들.
- **dependency**:
  - `선행`: TASK-MONO-044a (이미 머지됨, 본 task 가 노출시킨 회귀의 직접 선행)
  - `후속`: 없음 (단, sporadic 회귀 발견 시 TASK-MONO-044 § AC #8 별도 task)
- **CI gating**: 본 PR 자체는 `Integration (GAP)` Job 만 FAIL → SUCCESS 로 회복하는 것이 검증. 다른 Job 영향 0.
