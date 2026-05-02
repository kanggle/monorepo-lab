# Task ID

TASK-BE-269

# Title

Fix TASK-BE-253 — restore connect/read timeouts on community-service OAuth2 WebClients

# Status

ready

# Owner

backend

# Task Tags

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

Fix issue found in TASK-BE-253 review.

`OAuth2WebClientConfig` in community-service reads `connect-timeout-ms` /
`read-timeout-ms` properties from configuration but never applies them to the
constructed `WebClient` beans. The pre-existing `RestClient`-based clients
(`AccountExistenceClient`, `AccountProfileClient`, `MembershipAccessClient`)
honored 2s connect / 3s read timeouts; after TASK-BE-253 those values are
silently ignored, so outbound calls now use Reactor Netty's defaults
(no read timeout, default connect timeout). This contradicts the task's own
Edge Cases ("GAP 가용성 저하" / token endpoint failure containment) and the
Caller Constraint declared in
`specs/contracts/http/internal/community-to-membership.md` line 62
("타임아웃: 연결 2s / 응답 3s").

Restore the timeout enforcement on all OAuth2-pre-bound `WebClient` instances
without changing any other production behavior.

---

# Scope

## In Scope

- `apps/community-service/src/main/java/com/example/community/infrastructure/config/OAuth2WebClientConfig.java`:
  - Apply `connect-timeout-ms` and `read-timeout-ms` to the `accountServiceWebClient` and `membershipServiceWebClient` beans (e.g. via `HttpClient` from `reactor.netty` + `WebClient.Builder.clientConnector(new ReactorClientHttpConnector(httpClient))`, or `JdkClientHttpConnector`).
  - Remove the `@SuppressWarnings("unused")` `ms()` helper or actually use it.
- Add a unit test asserting the configured timeouts propagate to the connector. Re-using `WireMockServer` with a deliberate slow response (e.g. `withFixedDelay(read-timeout-ms + 500)`) is acceptable.
- Smoke regression: `OAuth2WebClientConfigUnitTest` continues to pass.

## Out of Scope

- membership-service outbound (still uses `X-Internal-Token` per TASK-BE-253 Out of Scope; will migrate later).
- Any new feature work — community-service is FROZEN.
- Resolving the duplicate `JwtTimestampValidator` registration in `OAuth2ResourceServerConfig` (separate Suggestion; not behavior-affecting).

---

# Acceptance Criteria

- [ ] `connect-timeout-ms` and `read-timeout-ms` from `application.yml` are observably applied to both `accountServiceWebClient` and `membershipServiceWebClient` (verified by a test that fails when the connector default is used).
- [ ] No regression in existing tests: `:projects:global-account-platform:apps:community-service:check` PASS.
- [ ] No regression in `:projects:global-account-platform:apps:community-service:integrationTest`.
- [ ] `OAuth2WebClientConfigUnitTest` continues to verify `Authorization: Bearer` attachment.
- [ ] No changes to membership-service.
- [ ] No changes to `account-service`, `admin-service`, or `GlobalExceptionHandler.java` (user WIP protection).

---

# Related Specs

- `projects/global-account-platform/specs/contracts/http/internal/community-to-account.md`
- `projects/global-account-platform/specs/contracts/http/internal/community-to-membership.md` § Caller Constraints (timeout 2s/3s)
- `projects/global-account-platform/specs/services/community-service/architecture.md`

# Related Skills

- `.claude/skills/backend/` (WebClient timeout configuration)

---

# Related Contracts

- No contract change. Behavior must match the timeout values already declared in the contracts.

---

# Target Service

- `community-service`

---

# Architecture

- `infrastructure/config/`: keep timeout wiring co-located with the existing OAuth2 client configuration.

---

# Implementation Notes

- The `spring-boot-starter-webflux` dependency is already present (added by TASK-BE-253), so `reactor.netty.http.client.HttpClient` + `ReactorClientHttpConnector` is available with no new dependency.
- Alternative: `JdkClientHttpConnector` with `java.net.http.HttpClient` keeps the implementation closer to the legacy `RestClient`-based code, but Reactor Netty matches the WebFlux stack already pulled in.
- `OAuth2AuthorizedClientManager` does not need its own timeout configuration in this task — token endpoint timeouts are tracked separately by Spring Security defaults; revisit if circuit-breaker behavior on the token endpoint becomes a concern.
- Keep `community.account-service.connect-timeout-ms` and `community.account-service.read-timeout-ms` in `application.yml` (do not rename / remove); only wire them through.

---

# Edge Cases

- Timeout shorter than current SAS token endpoint cold-start latency → token endpoint call should still finish (it does not flow through these `WebClient` beans).
- `WireMockServer` with simulated delay must trigger `WebClientRequestException` / `WebClientResponseException` of timeout type.

---

# Failure Scenarios

- Reverting to Reactor Netty defaults silently — would re-introduce this bug. Test must assert against an actual failure mode.

---

# Test Requirements

- Unit test (no Spring context, like the existing `OAuth2WebClientConfigUnitTest`):
  - Assert: a request to a stub that delays > read-timeout fails with a timeout-shaped exception.
  - Assert: the existing token-attachment expectation continues to hold.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] No contract changes required
- [ ] Specs unchanged
- [ ] Ready for review

---

# References

- Original task: `tasks/done/TASK-BE-253-community-membership-oidc-integration.md`
- Implementation commit: `6159fd90`
- Pre-TASK-BE-253 timeout pattern: `git show 6159fd90^:projects/global-account-platform/apps/community-service/src/main/java/com/example/community/infrastructure/client/AccountExistenceClient.java`
