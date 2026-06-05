---
id: TASK-BE-199
title: Fix issues found in TASK-BE-198 — @ActiveProfiles and hardcoded WireMock ports
status: ready
type: TASK-BE
target_service: account-service, membership-service, admin-service
---

## Goal

Fix issues found in TASK-BE-198 review:

1. `AccountSignupIntegrationTest` and `SignupRollbackIntegrationTest` are missing
   `@ActiveProfiles("test")`, so the Hikari validation settings in
   `application-test.yml` (mandated by `platform/testing-strategy.md`
   §"MySQL Hikari Validation") are not loaded. This can cause
   `Communications link failure` on CI.

2. `ActivateSubscriptionIntegrationTest` binds WireMock to hardcoded port 18087.
   `AdminIntegrationTest` binds WireMock to hardcoded port 18085 and hard-references
   `"http://localhost:18085"` in its `@DynamicPropertySource`. Both violate
   `testing-strategy.md` ("Never hardcode container host ports") and
   `coding-rules.md` ("Do not hard-code environment-specific values... ports").

## Scope

### account-service

`apps/account-service/src/test/java/com/example/account/integration/`

| File | Fix |
|---|---|
| `AccountSignupIntegrationTest.java` | Add `@ActiveProfiles("test")` |
| `SignupRollbackIntegrationTest.java` | Add `@ActiveProfiles("test")` |

### membership-service

`apps/membership-service/src/test/java/com/example/membership/integration/`

| File | Fix |
|---|---|
| `ActivateSubscriptionIntegrationTest.java` | Replace `WireMockServer(WIREMOCK_PORT)` with `WireMockServer(WireMockConfiguration.options().dynamicPort())`, remove hardcoded `WIREMOCK_PORT` constant, update `@DynamicPropertySource` to use `wireMock.baseUrl()` |

### admin-service

`apps/admin-service/src/test/java/com/example/admin/integration/`

| File | Fix |
|---|---|
| `AdminIntegrationTest.java` | Replace `WireMockServer(18085)` with `WireMockServer(WireMockConfiguration.options().dynamicPort())`, remove hardcoded port references, update `@DynamicPropertySource` to use `wireMock.baseUrl()` |

## Acceptance Criteria

- `AccountSignupIntegrationTest` declares `@ActiveProfiles("test")`
- `SignupRollbackIntegrationTest` declares `@ActiveProfiles("test")`
- `ActivateSubscriptionIntegrationTest` uses dynamic WireMock port; no hardcoded port constant
- `AdminIntegrationTest` uses dynamic WireMock port; no hardcoded port literal; `@DynamicPropertySource` uses `wireMock.baseUrl()`
- `./gradlew :apps:account-service:test` BUILD SUCCESSFUL, failures=0
- `./gradlew :apps:membership-service:test` BUILD SUCCESSFUL, failures=0
- `./gradlew :apps:admin-service:test` BUILD SUCCESSFUL, failures=0

## Related Specs

- `platform/testing-strategy.md` — "Never hardcode container host ports", "MySQL Hikari Validation (Test Profile Only)"
- `platform/coding-rules.md` — "Do not hard-code environment-specific values (URLs, secrets, ports)"

## Related Contracts

없음

## Edge Cases

- `AdminIntegrationTest.configureProperties` uses `@DynamicPropertySource` — WireMock
  must be started before the registry lambda is invoked. WireMock is started in
  `@BeforeAll setupShared()` which is called before `@DynamicPropertySource`, so the
  order is safe as long as `wireMock.baseUrl()` is referenced via a lambda (lazy),
  not called at lambda construction time.
- Removing the hardcoded port in `AdminIntegrationTest` means the existing
  `application-test.yml` fallback URLs (`http://localhost:18085`) will no longer
  match. Confirm that `@DynamicPropertySource` overrides take precedence, or update
  the yml fallback to a placeholder.

## Failure Scenarios

- `@ActiveProfiles` missing → Hikari test config not loaded → possible
  `Communications link failure` on CI when MySQL container is shared across
  multiple Spring contexts
- Hardcoded WireMock port → port already in use → `BindException` on CI agents
  running parallel test forks
