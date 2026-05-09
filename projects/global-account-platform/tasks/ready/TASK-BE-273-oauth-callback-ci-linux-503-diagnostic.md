# Task ID

TASK-BE-273

# Title

OAuth callback IT — CI Linux 503 diagnostic harness + targeted isolation fix (선행=ADR-004)

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

[ADR-004](../../docs/adr/ADR-004-oauth-callback-ci-linux-503-isolation.md) 의 Phase 1 (옵션 A diagnostic harness) → Phase 2 (옵션 B 또는 C, Phase 1 진단 결과에 따라 분기) 를 구현하여 `OAuthLoginIntegrationTest` 의 5 deferred IT method (Google/Kakao/Microsoft happy + Microsoft preferredUsername fallback + Microsoft existing email auto-link) 를 회복한다.

**전제**: ADR-004 status = PROPOSED. PR #264 + 046-7a 13-cycle 학습 — 가설 (CB pollution / JVM-shared state) 모두 falsified. 본 task 는 **진단 우선 → 결과 기반 fix** 의 2-phase 구조.

---

# Scope

## In Scope

### Phase 1 — Diagnostic harness (≤ 2 cycle)

**production code 변경 (log 강화)**:

- `AccountServiceClient.java` 의 3 catch 블록 (`socialSignup` / `getAccountStatus` / `getAccountProfile`) 의 log 에 `e.getCause()` 추가 + stack trace 출력. 예:

  ```java
  } catch (RuntimeException e) {
      log.error("Account service social-signup failed after retries: msg={} cause={} stack={}",
              e.getMessage(), e.getCause(), e);  // pre-existing message preserved
      throw new AccountServiceUnavailableException("Account service is unavailable", e);
  }
  ```

  → root cause 가 `ConnectException` / `UnknownHostException` / `SocketTimeoutException` / 다른 무언가인지 구분 가능.

**test code 추가 (WireMock request 추적)**:

- `OAuthLoginIntegrationTest` 에 WireMock request listener 추가:

  ```java
  @BeforeEach
  void resetStubs() {
      wireMock.resetAll();
      wireMock.addMockServiceRequestListener((request, response) -> {
          log.info("WIREMOCK_REQUEST url={} method={} status={}",
                  request.getUrl(), request.getMethod(), response.getStatus());
      });
      // ... 기존 setup
  }
  ```

  → WireMock 으로 호출이 도달하는지 (network-level reach) + stub 매치 결과 (200/404) 가시화.

**5 IT method `@Disabled` 일시 제거 + CI run**.

**CI 결과 분석**:

| 진단 결과 | Phase 2 분기 |
|---|---|
| `ConnectException: Connection refused` (WireMock 에 도달 못함) | Phase 2 = 옵션 B (별 Gradle source set + CI step) — network 격리 강화 |
| WireMock log 에 incoming request 있음 + stub miss | Phase 2 = 옵션 C (embedded fake controller) — WireMock dynamic-port binding 우회 |
| `UnknownHostException` (localhost 미해석) | Phase 2 = CI runner 의 `/etc/hosts` 점검 + IPv6 우선 disable |
| 모든 환경에서 reproduce 안 됨 (5 method 가 PASS!) | Phase 2 불필요. 단 가능성 매우 낮음 (13-cycle deterministic FAIL 이력) |

진단 결과를 [ADR-004](../../docs/adr/ADR-004-oauth-callback-ci-linux-503-isolation.md) 의 별 ## Phase 1 Findings 섹션에 기록.

### Phase 2 — Targeted fix (≤ 4 cycle, 옵션 B 또는 C)

**옵션 B (Module-level isolation) 채택 시**:

- `auth-service` 의 build.gradle 에 새 source set `oauthLoginIt` 추가.
- `OAuthLoginIntegrationTest.java` 를 새 source set 으로 이동.
- 새 Gradle task `oauthLoginIntegrationTest` (Test 타입) 등록 — 자체 dependencies / classpath / fork config.
- `.github/workflows/ci.yml` 에 `Integration (GAP) — OAuth Login` step 추가 (gap-integration-tests 와 별).

**옵션 C (Embedded fake) 채택 시**:

- `OAuthFakeAccountService` + `OAuthFakeProvider{Google,Kakao,Microsoft}` 4 `@RestController` 신설 (test profile 한정).
- `OAuthLoginIntegrationTest` 에서 WireMock 제거. `@DynamicPropertySource` 의 `auth.account-service.base-url` 와 `oauth.{google,kakao,microsoft}.token-uri` 등을 in-process Spring server URL 로 교체.
- 5 IT method enable + CI run → PASS 검증.

**옵션 D fallback (Phase 2 4 cycle 안에 PASS 못 시킬 경우)**:

- 5 IT method 영구 `@Disabled("permanent — see ADR-004 Phase 2 D fallback")`.
- `auth-service/README.md` 에 OAuth callback E2E 가드 약화 사실 명시.
- ADR-004 status `REJECTED — option D fallback applied` 갱신.

## Out of Scope

- Cluster A 3 IT (`OAuth2RefreshTokenIntegrationTest` + `OAuth2RevokeIntrospectIntegrationTest`) — TASK-BE-272 / ADR-003
- 다른 GAP IT class 의 503 (현재 없음 — 본 task 의 영역은 OAuth callback 한정)
- production 의 OAuth provider 통합 행동 변경 (`OAuthLoginUseCase` / `MicrosoftOAuthClient` / `GoogleOAuthClient` 도메인 로직 무영향)
- `AccountServiceClient` 의 production behaviour 변경 — log 강화만 keep, retry/CB/timeout 정책 그대로

---

# Acceptance Criteria

- [ ] AC-01 — Phase 1 diagnostic harness 1 cycle 후 root exception (cause + stack trace) 가 CI log 에 명시 surface
- [ ] AC-02 — Phase 1 결과 (network/WireMock/DNS/etc.) 가 ADR-004 의 ## Phase 1 Findings 섹션에 기록
- [ ] AC-03 — Phase 2 채택된 옵션 (B 또는 C) 으로 5 IT method `@Disabled` 제거 + CI Integration (GAP) Job PASS
- [ ] AC-04 — 회귀 0 — Cluster A 3 IT, OAuth2AuthCodePkce 7 IT, OAuth2AuthorizationServer + OAuth2JpaPersistence + OAuth2RevokeIntrospect (enabled) + DeviceSession + AuthIntegration 모두 baseline PASS
- [ ] AC-05 — 옵션 B 채택 시 `.github/workflows/ci.yml` 의 새 step 이 path-filter `gap` 또는 `workflows` 에 의존 — `auth-service` 외 변경에는 trigger 안 됨
- [ ] AC-06 — 옵션 C 채택 시 `WireMock` 의존 제거되지만 다른 IT class (`AuthIntegrationTest`, `OAuth2RefreshTokenIntegrationTest` 등) 의 WireMock 사용은 영향 0
- [ ] AC-07 — 총 cycle ≤ 6 (Phase 1: 2 + Phase 2: 4). 초과 시 ADR-004 § "옵션 D fallback" 적용
- [ ] AC-08 — `AccountServiceClient` log 강화는 production 으로 유지 (operational debugging asset). 회귀 0

---

# Related Specs

- [ADR-004 — OAuth Callback CI Linux 503 Deeper Isolation](../../docs/adr/ADR-004-oauth-callback-ci-linux-503-isolation.md)
- `tasks/done/TASK-MONO-046-7a-auth-service-sas-7-deferred.md` (root) — cycle 1+2 evidence
- `tasks/done/TASK-MONO-046-7-auth-service-sas-deferred-8.md` (root) — 11-cycle burn cycle 9-11 503 패턴
- `tasks/done/TASK-MONO-044c-1-gap-auth-oauth-pkce-circuitbreaker-residue.md` (root) — `@DirtiesContext(AFTER_CLASS)` 패턴 + lazy `AccountServiceClient` URL resolution
- `projects/global-account-platform/specs/services/auth-service/architecture.md` — OAuth social login 디자인

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/auth-api.md` — `POST /api/auth/oauth/{authorize,callback}` API
- account-service `POST /internal/accounts/social-signup` + `GET /internal/accounts/{id}/status` 내부 API — 본 task 영향 0 (production behaviour 변경 안 함)

---

# Edge Cases

- **Phase 1 진단 결과가 "WireMock 에 incoming request 있음 + stub matched + 200 returned"** (즉 stub 은 정상 작동인데 다른 곳에서 503): retry 카운터 / CB 상태 / Spring exception handling chain 추가 진단 필요. ADR-004 의 옵션 A 를 한 cycle 더 burn (총 3) 하여 origin 의 정확한 위치 파악.
- **옵션 B 채택 시 build.gradle source set 분리가 다른 GAP service (account-service / admin-service / community-service / membership-service) 의 build 에 영향**: 본 task 변경은 `auth-service/build.gradle` 만. 다른 service 영향 0 가설 검증 — 1 cycle 안에 다른 service IT 도 baseline PASS 확인.
- **옵션 C 채택 시 embedded fake 가 production OAuth provider behaviour 와 다른 시나리오 누락**: 단위 test (`MicrosoftOAuthClientTest` 등) + WireMock 기반 다른 IT 가 그 영역 cover. embedded fake 는 happy-path + 1 fail 만 제공 (provider 5xx).
- **CI 의 GitHub Actions runner 변경** (예: `ubuntu-latest` 가 24.04 → 다른 release 로 갱신): network stack 행동이 변할 수 있어 본 task 의 fix 가 약해질 가능성. ADR-004 의 ## References 에 GitHub Actions runner version 명시 + 변경 시 회귀 검증 권고.

# Failure Scenarios

- **Phase 1 진단 자체가 1-2 cycle 안에 deterministic 한 root exception 을 surface 못 함** (예: log 가 race-y 하게 capture 되거나 cause 가 nested 5+ 단계): test JVM 의 logback config 강화 (모든 IT class 에 `level=DEBUG` for `c.e.a.i.client`) → 1 cycle 추가. ADR-004 의 cycle 예산 6 → 7 로 1 cycle 증가 검토 (별 ADR 갱신).
- **옵션 B 채택했는데 CI workflow 변경 검증 cycle 에서 path-filter 깨짐** (예: GAP 외 변경에도 새 step trigger): `.github/workflows/ci.yml` revert + 옵션 C 로 전환. workflow 변경은 D4 churn freeze 영역이지만 046 시리즈 면제 카테고리 자연 확장.
- **6 cycle 초과해도 미해결**: ADR-004 § "옵션 D fallback" 적용 + 5 IT method 영구 `@Disabled`. `auth-service/README.md` + portfolio README 에 OAuth callback E2E 가드 약화 명시. Cluster A 3 IT 는 TASK-BE-272 로 별 회복 가능 (서로 독립).
- **Phase 1 의 log 강화 변경이 production 회귀 발생** (e.g. log volume 폭증으로 log aggregator 부하): immediate revert + log level 을 DEBUG 로 강등 (ERROR 로 항상 출력하지 말고 DEBUG 에서만). 본 task 의 production code 변경 영역은 log 만이라 회귀 가능성 낮음.

---

# Notes

- **모델 권장**: 분석=Opus 4.7 / 구현=Opus 4.7 — Linux-specific HTTP/network 진단 + 옵션 B/C 분기 = complex investigation. Sonnet 으로 burn 시 가설 검증 부족 위험.
- **D4 churn freeze 영향**: 옵션 B 채택 시 `.github/workflows/ci.yml` 변경 = freeze 영역. 046 시리즈 자연 확장 (regression fix path) 으로 처리 — TASK-MONO-046-7/8 면제 적용. 옵션 C 채택 시 freeze 영향 0.
- **연관 메모리**: `project_046_7_11_cycle_burn` (ADR-004 의 base context), `project_testcontainers_docker_desktop_blocker` (env 차이 학습 — 본 task 가 그 차이를 isolation).
- **회귀 가드 우선**: 본 task 는 진단 우선 task. 첫 cycle 의 diagnostic log 가 Phase 2 의 의사결정 base. 짚이 안되는 가설로 cycle 더 태우면 PR #264 11-cycle 패턴 재발 — 그 회피가 spec 의 핵심.
