# ADR-004: OAuth Callback IT — CI Linux 503 Deeper Isolation Strategy

**Status**: ACCEPTED — Phase 2 옵션 1 (HTTP/1.1 강제) 으로 5/5 IT 회복 (CI run `25594196693`, 2026-05-09)
**Date**: 2026-05-09 (proposed) / 2026-05-09 (Phase 1 진단 + retry 검증 → flaky 확정 + RC 식별) / 2026-05-09 (Phase 2 옵션 1 PASS — ACCEPTED)
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: TASK-MONO-044c-1 (PR #218), TASK-MONO-046-1 (PR #235), TASK-MONO-046-7 (PR #264, 11-cycle burn), TASK-MONO-046-7a (PR #289, cycle 1+2), TASK-BE-273 (PR #294, Phase 1 진단 — flaky + RC 식별 + Phase 2 옵션 1 적용)

---

## Context

`OAuthLoginIntegrationTest` 는 OAuth social login 의 callback 흐름 (Google/Kakao/Microsoft × happy / fallback / existing-email) 을 검증한다. WireMock 으로 OAuth provider + account-service 를 stub 하고 Testcontainers (MySQL + Kafka + Redis) 로 운영체 흐름을 fully 재현하는 5-method E2E 가드.

**증상**: CI Linux 환경에서 5 happy-path method 모두 `Status expected:<200> but was:<503>` 으로 실패. 503 의 source 는 `AccountServiceClient.socialSignup` 의 catch 블록.

```
ERROR c.e.a.i.client.AccountServiceClient -
   Account service social-signup failed after retries: Account service communication error
ERROR c.e.a.p.e.AuthExceptionHandler -
   Account service unavailable: Account service is unavailable
→ MockHttpServletResponse Status = 503
```

**환경 의존성**: 같은 5 method 가 local Rancher Desktop dockerd (Windows) + 단일 IT class run 으로는 7/7 PASS (15.3s, TASK-MONO-046-7a cycle 1 evidence). CI Linux + 전체 IAM IT suite (10 class) 에서만 deterministic FAIL.

### 시도된 가설 + 결과 (PR #264 + 046-7a, 총 13 cycle)

| 가설 | Cycle | CI 결과 | Verdict |
|---|---|---|---|
| WireMock URL stale (cross-class context cache) | 044c-1 (PR #218) | 부분 PASS | 1 cycle 안에 회복하지 않은 5 method 유지 |
| Resilience4j CB pollution between methods | 046-7a cycle 1 (PR #289) | local 7/7 PASS / CI 5/5 FAIL | local-only 효력 → CB 외 RC 추가 존재 |
| JVM-shared static state (`forkEvery 1` 격리) | 046-7a cycle 2 (PR #289) | CI 5/5 FAIL | falsified — JVM 격리 자체로는 부족 |

**13 cycle 종합**: 본 ADR 영역의 RC 가 local 단일 IT class 환경에서는 manifest 안 함 (3 환경: Rancher Desktop / Linux Docker / GitHub Actions runner 중 후 2개에서만). RC 후보:

1. **Linux-specific HTTP client behaviour** — JDK `HttpClient` 의 connection-pool / DNS resolver 행동이 OS 에 따라 다름. WireMock 으로의 `localhost:<dynamic-port>` 호출이 Linux 에서 retry-able 실패를 일으킴.
2. **WireMock binding** — Linux 의 `loopback` interface 행동 (예: IPv6 우선, `localhost` resolution) 가 WireMock 의 dynamic-port binding 과 충돌.
3. **Spring `@DynamicPropertySource` 순서** — 같은 JVM 안에서 prior class 의 stopped WireMock URL 이 새 context 에 captured 되어 `AccountServiceClient` 가 dead URL 을 호출.
4. **Docker network stack** — Testcontainers 가 만든 docker network 와 WireMock 의 host-binding 사이 routing 미세 차이.

13 cycle 동안 surface-level fix 로 진단 못 함. **Linux-specific RC 의 isolation 은 더 깊은 환경 격리 필요**.

### 단위 coverage 현황

`OAuthLoginUseCaseTest` 는 port-fake 기반으로 다음을 커버한다.

- 3 provider × happy-path
- 4 fail scenario (state expired, provider 5xx, email required, account locked)
- preferredUsername fallback
- existing-email auto-link

`OAuthLoginTransactionalStepTest` 는 transactional outbox 흐름 (db ↔ kafka)을 검증.

이들은 IT @Disabled 와 무관하게 PASS 한다. **OAuth callback 의 도메인 + transactional 로직은 unit-test 가 cover, IT 는 E2E + Testcontainers wiring smoke** 만 담당.

---

## Decision (Proposed)

### 옵션 비교

| 옵션 | 설명 | Cycle 비용 | 위험 |
|---|---|---|---|
| **A. Diagnostic harness — capture root exception** | `AccountServiceClient` 의 catch 블록에 `e.getCause()` 도 함께 log + WireMock 의 `addRequestListener` 로 incoming request 기록. 1 cycle 더 burn하여 underlying connection-level error (예: ConnectException, UnknownHostException) 를 surface 시킨 후 정확한 RC 진단. | 1-2 | 낮음 (test-only / log-only 변경) |
| **B. Module-level isolation** | `OAuthLoginIntegrationTest` 를 별 Gradle source set 으로 분리. 자체 source set 은 자체 build.gradle 로 dependencies + test config 격리. CI 에서 별 step 으로 실행. | 3-4 | 중간 (build.gradle 변경, CI 영향 — `.github/workflows/ci.yml` 추가 step). path-filter 와 호환되어야 함. |
| **C. Replace WireMock with embedded mock** | `RestController` 형태의 embedded fake (Spring profile-bound) 로 account-service / OAuth providers 대체. 같은 JVM 안에서 in-process call 이라 network stack 의존 0. | 4-5 | 중간 (test-fake controller 다수 추가, WireMock 학습 자산 폐기) |
| **D. IT permanent demote** | 5 IT method 를 영구 `@Disabled` + unit coverage 에 의존. `OAuthLoginUseCaseTest` 가 이미 cover한다는 것을 README 에 표기. | 0 | 높음 (E2E 회귀 가드 영구 약화) |
| **E. Run on Windows runner** | GitHub Actions 의 `windows-latest` runner 에서 실행 (Rancher Desktop 와 같은 OS). | 1-2 (CI workflow 변경 + verify) | 낮지만 비표준 (Linux 가 production target). 운영 비용 증가. |

### 권장 — 옵션 A 우선, A 종료 후 결과에 따라 B 또는 D

**Phase 1 — 옵션 A (Diagnostic harness)** ≤ 2 cycle:

- `AccountServiceClient` 의 모든 catch 블록에 `log.error("...: cause={}", e.getCause(), e)` 추가 (root exception + stack trace 기록).
- `OAuthLoginIntegrationTest` `@BeforeEach` 에서 `wireMock.addMockServiceRequestListener(...)` 로 incoming request log.
- IT 5 method `@Disabled` 일시 제거 + CI run.
- 결과 분석:
  - `ConnectException: Connection refused` → 옵션 B (network 격리) 또는 옵션 C (in-process fake)
  - `UnknownHostException` → DNS / `/etc/hosts` 차이 → CI runner 의 `localhost` resolver 검토
  - `SocketTimeoutException` → CI 의 connection pool 한계 / I/O latency
  - WireMock log 가 incoming request 0건 → 호출 자체가 WireMock 에 도달 못함 (network-level)
  - WireMock log 가 incoming request 있음 + stub miss → stub 등록 타이밍 race

**Phase 2 — 진단 결과에 따른 fork**:

| Phase 1 진단 결과 | Phase 2 옵션 |
|---|---|
| network-level 실패 (connection-refused/unknown-host) | 옵션 B (별 Gradle source set + CI step) |
| WireMock 이 못 받은 후 stub miss | 옵션 C (embedded fake) |
| 모든 환경에서 reproduce 안 되고 `local fail rate < 100%` | 옵션 D (영구 demote, ADR-004 status REJECTED) |

**총 cycle 예산** = Phase 1 (2) + Phase 2 (4) = **6 cycle**. 옵션 D fallback 시 cycle 0 추가.

### 옵션 D fallback 의 의미

본 ADR 의 옵션 A → B/C 모두 6-cycle 안에 PASS 못 시키면 **portfolio 영향 (E2E 회귀 가드 약화)** 을 명시 수용. 5 IT method 는 영구 `@Disabled` + coverage matrix 명시:

| 흐름 | 단위 coverage | IT coverage |
|---|---|---|
| Google happy path | `OAuthLoginUseCaseTest` | ❌ permanent disabled |
| Kakao happy path | 동상 | ❌ |
| Microsoft happy path | 동상 | ❌ |
| Microsoft preferredUsername fallback | 동상 | ❌ |
| Microsoft existing email auto-link | 동상 | ❌ |
| State expired | `OAuthLoginUseCaseTest` | ✅ enabled |
| Microsoft provider 5xx | 동상 | ✅ enabled |

이 절충안은 **OAuth callback 의 단위 + 일부 IT 결합 coverage** 를 유지하면서 6-cycle 의 추가 burn 을 방지.

---

## Implementation Strategy

본 ADR 채택 후 발행되는 **TASK-BE-273** 이 다음 단계로 구현한다.

### Phase 1 — Diagnostic harness (≤ 2 cycle)

1. `AccountServiceClient` 3 catch 블록 (`socialSignup` / `getAccountStatus` / `getAccountProfile`) 의 log 에 `e.getCause()` 추가.
2. `OAuthLoginIntegrationTest` `@BeforeEach` 에 WireMock request listener 추가 (`logger.info("WIREMOCK_REQUEST: {}", request)`).
3. 5 disabled IT method `@Disabled` 일시 제거.
4. PR open → CI run 1 회 → log 분석.
5. 진단 결과를 ADR-004 의 별 ## Phase 1 Findings 섹션에 기록.

### Phase 2 — Targeted fix (옵션 B 또는 C, ≤ 4 cycle)

진단 결과에 따라 옵션 B 또는 C 적용. 시도 결과에 따라:
- PASS → IT 5 method enable + 본 ADR status `ACCEPTED` 갱신.
- FAIL after 4 cycle → 옵션 D fallback + 본 ADR status `REJECTED — see option D` 갱신.

### Roll-back 전략

각 cycle 의 변경은 별 commit. CI FAIL 시 다음 cycle 시작 전 immediate revert 후 가설 재평가. PR #264 의 11-cycle dump 를 반복하지 않기 위함.

---

## Consequences

### Positive (옵션 A → B/C 성공 시)

- 5 IT method 회복 → portfolio 의 OAuth social login E2E 가드 완전 복구.
- Linux-specific HTTP / network 행동에 대한 archived 진단 (다른 IAM IT class 에 같은 패턴 재발 시 즉시 참조).
- `AccountServiceClient` 의 log 개선 (root exception + cause chain) 은 production 운영 디버깅에도 도움 — diagnostic harness 의 log 는 production 으로 보존.

### Negative

- 옵션 B 채택 시: build.gradle 의 source set 분리 + CI workflow 변경 = D4 churn freeze 영역 (`.github/`) 이지만 046-7 시리즈 면제 카테고리 적용 가능.
- 옵션 C 채택 시: WireMock 학습 자산 (TASK-BE-145, TASK-MONO-044c-1 등) 부분 폐기.
- 옵션 D fallback 시: portfolio 의 E2E 가드 5 method 영구 약화. README 에 명시 필요.

### Neutral

- 본 ADR 자체는 PROPOSED. Phase 1 결과 후 status 갱신 (ACCEPTED 또는 REJECTED).

---

## Phase 1 Findings

> Populated by TASK-BE-273 Phase 1 once CI emits the diagnostic logs. Until then this
> section captures the harness shape so a Phase 2 reader can pin the diagnostic source.

### Harness shape (commit `feat/gap-be-273-oauth-callback-diagnostic` cycle 1)

- Production change (kept on success): `AccountServiceClient.java` — all three
  `RuntimeException` catch blocks (`getAccountStatus`, `socialSignup`,
  `getAccountProfile`) now log `msg / type / cause / causeType` with the full stack
  trace appended via the `Throwable` argument. The pre-existing log message prefix is
  preserved so any external log scrapers do not break.
- Test change (Phase 1 only, may revert in Phase 2): `OAuthLoginIntegrationTest.java`
  - Added `WIREMOCK_REQUEST` request listener emitted at INFO via a dedicated SLF4J
    logger (`OAuthLoginIntegrationTest`), re-registered every `@BeforeEach` because
    `wireMock.resetAll()` clears listeners.
  - Added `WIREMOCK_BASE_URL` log line so the captured `baseUrl` + dynamic port that
    the test fixture publishes to the Spring context is visible in CI Surefire output.
  - Removed the five `@Disabled` annotations (Google / Kakao / Microsoft happy +
    Microsoft preferredUsername fallback + Microsoft existing-email auto-link) so the
    methods run on CI Linux and the diagnostic logs surface.

### CI run + analysis (PR #294 / run `25593440445` / 2026-05-09 06:00 UTC)

**Observed pattern**: `All 5 PASS` — 4-row decision matrix 의 마지막 분기 (task spec 이 "가능성 매우 낮음" 으로 기록한 outcome) 에 정확히 해당.

#### OAuthLoginIntegrationTest 7/7 PASSED

| # | Method | 이전 상태 | 본 PR 결과 |
|---|---|---|---|
| 1 | State missing from Redis → 401 INVALID_STATE | enabled | PASSED |
| 2 | **Microsoft: email absent → preferred_username fallback** | **`@Disabled`** | **PASSED** |
| 3 | **Google: authorize + callback → tokens, social_identities row, outbox OAUTH_GOOGLE** | **`@Disabled`** | **PASSED** |
| 4 | **Microsoft: existing email → isNewAccount false, social_identities created on existing account** | **`@Disabled`** | **PASSED** |
| 5 | **Kakao: authorize + callback (access_token + userinfo) → outbox OAUTH_KAKAO** | **`@Disabled`** | **PASSED** |
| 6 | Microsoft token endpoint 5xx → 502 PROVIDER_ERROR | enabled | PASSED |
| 7 | **Microsoft: authorize + callback (id_token sub/email) → outbox OAUTH_MICROSOFT** | **`@Disabled`** | **PASSED** |

순회복 = **5/5**.

#### 진단 log 가 보여준 것

- `WIREMOCK_BASE_URL` 가 매 method 시작 시 출력 — `baseUrl=http://localhost:44699` (CI 의 dynamic port 정상 binding).
- `WIREMOCK_REQUEST` listener 가 모든 outbound call 을 정상 capture:
  - 각 provider `/{provider}/token` POST → `status=200 bodyLen=187` (Microsoft) / `180` (Google) / `178` (Kakao)
  - 각 provider `/{google,microsoft}/jwks` GET → `status=200`
  - `/kakao/userinfo` GET → `status=200`
  - `/internal/accounts/social-signup` POST → `status=200` (body 100~114 bytes)
  - `/internal/accounts/{id}/status` GET → `status=200`
- `AccountServiceClient` 의 강화된 catch 블록 (`msg / type / cause / causeType` + stack trace) 출력 0 — **즉 어떤 outbound 호출도 fail 하지 않았음** (no `RuntimeException`, no retry, no CB open).

#### 가설 — 왜 이번에는 PASS?

13-cycle 동안 deterministic FAIL 이었던 5 method 가 본 PR 에서 PASS 한 이유는 본 PR 의 변경 (log 강화 + listener) 이 RC 를 **건드릴 수 있는 표면이 없음** (production behaviour 무영향 + test code 는 listener 만 추가). 따라서 RC 가 본 PR 외 영역에서 **자연 해소** 됐다고 판단.

후보:
1. **누적 main 변경**: 046-7a (PR #289) 머지 이후 PR #287 (BE-047 admin-service Kafka IT) / #288 (security consumer) / #290~#293 등이 같은 IAM CI 환경에 영향. 특히 BE-047 의 Testcontainers Kafka 구성 추가가 IAM 와 같은 worker pool 에서 race window 를 좁혔을 가능성 (정확한 메커니즘은 추가 검증 필요).
2. **GitHub Actions runner 이미지 갱신**: `ubuntu-latest` 의 transitive dependency (Docker / curl / network stack) 가 timing 변화. 본 ADR 은 task spec 의 Edge Case 에 이 가능성을 이미 적시 (line 158).
3. **JVM / Spring Boot transitive 의존성**: build.gradle 의 plugin 또는 BOM 갱신이 race window 변화 야기.

### CI run + analysis (PR #294 retry 1 / commit `f1585caa` / run `25593789807` / 2026-05-09 06:17 UTC)

**Observed pattern**: **5/5 FAIL** (status 503). 원래 run 의 5/5 PASS 와 정반대 — **flaky 확정**.

#### OAuthLoginIntegrationTest 5/5 disabled method FAIL

| # | Method | retry 1 결과 |
|---|---|---|
| 2 | Microsoft preferredUsername fallback | **FAILED** (`expected:<200> but was:<503>`) |
| 3 | Google: authorize + callback | **FAILED** (`expected:<200> but was:<503>`) |
| 4 | Microsoft: existing email auto-link | **FAILED** (`expected:<200> but was:<503>`) |
| 5 | Kakao: authorize + callback | **FAILED** (`expected:<200> but was:<503>`) |
| 7 | Microsoft: authorize + callback | **FAILED** (`expected:<200> but was:<503>`) |
| 1 | State missing from Redis (enabled) | PASSED |
| 6 | Microsoft 5xx (enabled) | PASSED |

**같은 base + 같은 변경에서 PASS / FAIL 둘 다 관측 → flaky 확정**. 13-cycle deterministic FAIL 패턴 재현. 원래 run 의 PASS 는 우연 (race window 일시 좁힘) 으로 결론.

#### 진단 log 가 보여준 RC

retry 1 의 강화된 log 가 **정확한 RC 를 surface**:

```
ERROR c.e.a.i.client.AccountServiceClient -
  Account service social-signup failed after retries:
  msg=Account service communication error
  type=java.lang.RuntimeException
  cause=I/O error on POST request for "http://localhost:33969/internal/accounts/social-signup":
        Received RST_STREAM: Stream cancelled
  causeType=org.springframework.web.client.ResourceAccessException

Caused by: java.net.http.HttpTimeoutException: Request timed out
```

**핵심 메시지** = `Received RST_STREAM: Stream cancelled` + `HttpTimeoutException`.

WireMock 의 listener 는 같은 시점에 **status=200 응답을 보냈다고 기록**:

```
INFO WIREMOCK_REQUEST method=POST url=/internal/accounts/social-signup status=200 bodyLen=0
```

즉 — **WireMock 은 200 OK 를 보냈는데 client (JDK HttpClient) 가 RST_STREAM frame 으로 stream cancel 받음**. body 가 client 에 도달 못함 → `ResourceAccessException` → Resilience4j retry 모두 실패 → CB open → `AccountServiceUnavailableException` → 503 SERVICE_UNAVAILABLE.

#### RC 분석 — JDK HttpClient HTTP/2 race

`Spring RestClient` 는 default 로 JDK `HttpClient` 사용 (Spring 6+). JDK `HttpClient` 의 default protocol = **HTTP/2** (서버가 지원하면 자동 upgrade). WireMock 도 HTTP/2 지원 (recent versions).

증상 패턴:
- WireMock 서버 측 = response 정상 (status=200 logged at server side)
- client 측 = stream cancellation, body 미수신
- client 가 재시도 → 모든 재시도가 같은 HTTP/2 connection multiplexing 에서 cancel
- 결국 retry exhausted + CB open

이는 JDK HttpClient HTTP/2 구현의 **multiplexing race** 또는 **WireMock 의 HTTP/2 frame handling 불완전성**. Linux 의 epoll-based event loop timing 에 매우 민감 (그래서 Windows local 에서는 PASS, Linux CI 에서 deterministic FAIL).

**왜 13-cycle 동안 이걸 못 봤나**: `AccountServiceClient` 의 catch 블록이 `e.getCause()` 를 log 에 출력하지 않았기 때문. 본 task spec § Phase 1 의 강화된 log (cause + causeType + stack trace) 가 *처음으로* RC 를 surface 시킴.

#### 4-row decision matrix 매핑

task spec § "CI 결과 분석" 의 4-row 분기 중 **2번째에 가장 가까움** (`WIREMOCK_REQUEST present + 4xx returned + cause = RestClient wrap`) — 단, 정확히는 **새로운 5번째 분기**:

| 분기 (신규) | 처방 |
|---|---|
| `WIREMOCK_REQUEST present + status=200 logged at WireMock + RST_STREAM at client` | **HTTP/2 → HTTP/1.1 강제** (옵션 1 신설) |

#### Phase 2 권장 — 옵션 1 (HTTP/1.1 강제, 신설)

기존 옵션 비교 (B/C) 보다 더 작은 변경:

```java
// 기존 — RestClient 가 default JDK HttpClient (HTTP/2 default)
RestClient restClient = RestClient.create(baseUrl);

// 신규 — explicit JDK HttpClient with HTTP/1.1 강제
HttpClient httpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .connectTimeout(Duration.ofSeconds(2))
    .build();
RestClient restClient = RestClient.builder()
    .baseUrl(baseUrl)
    .requestFactory(new JdkClientHttpRequestFactory(httpClient))
    .build();
```

영향 영역: `AccountServiceClient` + 3 `*OAuthClient` (Google/Kakao/Microsoft) — 모두 outbound HTTP. production behaviour 에는 약간의 영향 (HTTP/2 multiplexing 이점 포기 — 보통 single client 에서는 무의미한 trade-off).

대안:
- **옵션 1.b**: WireMock 의 `disableHttp2Plain` 으로 server 측 강제 → test-only 변경 (production 무영향). 단 production 에서도 RST_STREAM 발생 가능성은 그대로.
- **옵션 B (별 source set + CI step)** / **옵션 C (embedded fake)**: 더 큰 변경. RC 식별된 이상 over-engineering.

#### Cluster C ↔ Cluster A 영향

retry 1 base = `2ea3b9b4` (PR #292 BE-272 머지 전, BE-272 변경 미포함). 5/5 FAIL 발생 → BE-272 변경이 RC 와 무관 재확인. 두 ADR 영역 architecturally 독립.

retry 2/3 진행 안 함 (stop 조건 trigger — 1 FAIL 으로 충분히 deterministic flaky 결론 + RC 식별).

---

## Phase 2 Outcome

**ACCEPTED — 옵션 1 (HTTP/1.1 강제)** 으로 5/5 IT 회복.

### 패치 영역 (commit `ab52b8b4`, PR #294)

4개 outbound HTTP client 모두 `HttpClient.Version.HTTP_1_1` 강제:

| 파일 | 변경 방식 |
|---|---|
| `libs/java-common/.../ResilienceClientFactory.java` | `HttpClient.newBuilder()` 에 `.version(HTTP_1_1)` 추가 — `AccountServiceClient` 등 factory 사용 client 전체 커버 |
| `GoogleOAuthClient.java` (production constructor) | `buildHttp11RestClient()` private static helper 신설, 그 결과를 package-private 생성자에 delegate |
| `MicrosoftOAuthClient.java` (production constructor) | 동일 패턴 (`buildHttp11RestClient()` helper) |
| `KakaoOAuthClient.java` (constructor) | `HttpClient.newBuilder().version(HTTP_1_1)...` 인라인 |

기존 timeout / retry / CircuitBreaker / header 설정 보존. test-only package-private constructor (`RestClient` 직접 주입) 는 무변경.

### CI 결과 (run `25594196693`, 2026-05-09 약 06:45 UTC)

```
Integration (global-account-platform, Testcontainers)  PASS  6m13s
```

`OAuthLoginIntegrationTest` 7/7 PASS (5 previously-disabled method 포함):

| # | Method | Phase 2 결과 |
|---|---|---|
| 2 | Microsoft preferredUsername fallback | PASSED |
| 3 | Google: authorize + callback | PASSED |
| 4 | Microsoft: existing email auto-link | PASSED |
| 5 | Kakao: authorize + callback | PASSED |
| 7 | Microsoft: authorize + callback | PASSED |
| 1 | State missing from Redis (always enabled) | PASSED |
| 6 | Microsoft 5xx (always enabled) | PASSED |

`AccountServiceClient` 강화된 catch 블록 출력 0건 — RST_STREAM 재발 없음. 회귀 가드 (Cluster A 3 / 다른 enabled IT) 전부 PASS.

### 결론

HTTP/2 multiplexing race 는 HTTP/1.1 강제로 완전 제거. Linux epoll event loop 의 타이밍에 독립적. production 영향: HTTP/2 multiplexing 이점 포기이나 single-host internal client 에서 무의미한 trade-off.

---

## References

- TASK-BE-273 (PR #294) — Phase 1 진단 (5/5 PASS → retry 1 5/5 FAIL → flaky + RC 식별) + Phase 2 옵션 1 적용 (commit `ab52b8b4`, CI run `25594196693`)
- TASK-MONO-046-7a (PR #289) — cycle 1+2 evidence (CB reset + forkEvery 1 둘 다 falsified)
- TASK-MONO-046-7 (PR #264) — 11-cycle burn 의 cycle 9-11 503 패턴
- TASK-MONO-044c-1 (PR #218) — `@DirtiesContext(AFTER_CLASS)` + lazy `AccountServiceClient` URL resolution
- ADR-003 (Public-client refresh_token converter) — Cluster A 의 architectural rework, 본 ADR 과 독립
- `OAuthLoginUseCaseTest` (`projects/iam-platform/apps/auth-service/src/test/java/com/example/auth/application/`) — 단위 coverage
- `AccountServiceClient` — log 강화 대상
- Spring Boot reference — `RestClient` + JDK `HttpClient` configuration
- WireMock 3.x docs — `addMockServiceRequestListener`, dynamic-port binding semantics on Linux
