# Task ID

TASK-MONO-023a

# Title

GAP provisioning 통합 테스트 격리·downstream mock 회귀 fix

# Status

review

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

[TASK-MONO-023](../in-progress/TASK-MONO-023-main-baseline-integration-cleanup.md) 의 분류 매트릭스에서 식별된 **provisioning 관련 4 통합 테스트** 의 회귀를 fix 한다:

| 테스트 클래스 | 증상 | 추정 원인 |
|---|---|---|
| `account.AccountRoleProvisioningIntegrationTest` | `Status 201 vs 409` | 테스트 간 동일 이메일 / operatorId 재사용 — unique ID 격리 누락 |
| `account.TenantProvisioningIntegrationTest` | `Status 201 vs 409` | 동일 |
| `account.BulkProvisioningIntegrationTest` | `Status 200 vs 500` | bulk endpoint 의 downstream 호출 실패 (auth-service / security-service mock) |
| `account.SignupAuthServiceDelayIntegrationTest` | `Status 201 vs 503` | auth-service WireMock 503 응답 처리 회귀 |

이 태스크 완료 후:

- 위 4 테스트 클래스가 main CI 의 `Integration (global-account-platform, Testcontainers)` 잡에서 PASS
- 테스트 격리 강화 (각 테스트가 unique 이메일 · operatorId 사용)
- downstream mock 동작이 spec 과 일치 (503 응답 시 idempotent retry, 500 으로 변환되지 않음)

---

# Scope

## In Scope

- 4 테스트 클래스의 fixture 검토 + unique ID 적용 (`UUID.randomUUID().toString()` 등)
- 503 / 500 케이스의 production 코드 검토 — 의도된 동작인지 회귀인지 판정
- 회귀로 확인되면 production 코드 fix (예: `BulkProvisioningService` 의 downstream error handling, `SignupAuthServiceDelay` 의 503 retry)
- 테스트 격리만으로 해결되면 production 코드 변경 없이 fixture 수정으로 끝
- `5회 연속 PASS` 검증 (flaky 가 아닌지 확인)

## Out of Scope

- OAuth2 / OIDC 통합 회귀 — TASK-MONO-023b
- AccountAnonymization / AdminAudit 회귀 — TASK-MONO-023c
- Outbox 관련 회귀 — TASK-MONO-023d
- Community JPA 격리 — TASK-MONO-023e

---

# Acceptance Criteria

- [ ] `AccountRoleProvisioningIntegrationTest` PASS (단일 + 5회 연속)
- [ ] `TenantProvisioningIntegrationTest` PASS (단일 + 5회 연속)
- [ ] `BulkProvisioningIntegrationTest` PASS — 500 응답 원인 production fix 또는 spec 명시
- [ ] `SignupAuthServiceDelayIntegrationTest` PASS — 503 처리 production fix 또는 spec 명시
- [ ] 회귀로 판정된 fix 가 단위 테스트로도 cover 됨

---

# Related Specs

- `projects/global-account-platform/specs/services/account-service/architecture.md`
- `projects/global-account-platform/specs/contracts/http/internal/account-internal-provisioning.md`
- `projects/global-account-platform/specs/features/multi-tenancy.md` § Internal Provisioning API

---

# Related Contracts

- `specs/contracts/http/internal/account-internal-provisioning.md` (POST /internal/tenants/{id}/accounts, :bulk)

---

# Target Service / Component

- `projects/global-account-platform/apps/account-service/src/test/java/com/example/account/integration/`
  - `AccountRoleProvisioningIntegrationTest.java`
  - `TenantProvisioningIntegrationTest.java`
  - `BulkProvisioningIntegrationTest.java`
  - `SignupAuthServiceDelayIntegrationTest.java`
- `projects/global-account-platform/apps/account-service/src/main/java/com/example/account/application/` (회귀 시 production fix)

---

# Implementation Notes

- 각 테스트의 fixture 코드 (이메일 · operatorId · tenantId 생성) 부터 점검
- `@BeforeEach` 가 unique 값을 생성하지 않으면 `UUID.randomUUID()` 도입
- BulkProvisioning 500 의 stack trace 를 분석해 어느 downstream 호출이 실패하는지 파악
- SignupAuthServiceDelay 의 503 mock 은 의도된 흐름 (delay → retry) 일 수 있음. spec 과 비교
- 5회 연속 PASS: `for i in {1..5}; do ./gradlew :apps:account-service:integrationTest --tests "AccountRoleProvisioningIntegrationTest" --rerun-tasks; done`

---

# Edge Cases

- production 코드 fix 가 필요하면 spec 변경 PR 먼저 (CLAUDE.md § Source of Truth Priority)
- 503 처리가 spec 에 명시되지 않으면 spec 보강 PR 분리

---

# Failure Scenarios

- 5회 중 일부만 PASS → 진짜 flaky → @DirtiesContext 또는 명시적 cleanup 도입
- production fix 가 다른 테스트에 영향 → 회귀 영향 분석 + 본 PR 보강

---

# Test Requirements

- 4 통합 테스트 단일 PASS
- 5회 연속 PASS
- production fix 가 있다면 단위 테스트로도 cover

---

# Definition of Done

- [ ] 4 테스트 클래스 PASS (단일 + 5회 연속)
- [ ] production fix (필요 시) + 단위 테스트
- [ ] spec 변경 (필요 시) PR 분리 후 머지
- [ ] Ready for review
