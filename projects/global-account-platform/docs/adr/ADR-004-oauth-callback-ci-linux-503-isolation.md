# ADR-004: OAuth Callback IT — CI Linux 503 Deeper Isolation Strategy

**Status**: PROPOSED
**Date**: 2026-05-09
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: TASK-MONO-044c-1 (PR #218), TASK-MONO-046-1 (PR #235), TASK-MONO-046-7 (PR #264, 11-cycle burn), TASK-MONO-046-7a (PR #289, cycle 1+2)

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

**환경 의존성**: 같은 5 method 가 local Rancher Desktop dockerd (Windows) + 단일 IT class run 으로는 7/7 PASS (15.3s, TASK-MONO-046-7a cycle 1 evidence). CI Linux + 전체 GAP IT suite (10 class) 에서만 deterministic FAIL.

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
- Linux-specific HTTP / network 행동에 대한 archived 진단 (다른 GAP IT class 에 같은 패턴 재발 시 즉시 참조).
- `AccountServiceClient` 의 log 개선 (root exception + cause chain) 은 production 운영 디버깅에도 도움 — diagnostic harness 의 log 는 production 으로 보존.

### Negative

- 옵션 B 채택 시: build.gradle 의 source set 분리 + CI workflow 변경 = D4 churn freeze 영역 (`.github/`) 이지만 046-7 시리즈 면제 카테고리 적용 가능.
- 옵션 C 채택 시: WireMock 학습 자산 (TASK-BE-145, TASK-MONO-044c-1 등) 부분 폐기.
- 옵션 D fallback 시: portfolio 의 E2E 가드 5 method 영구 약화. README 에 명시 필요.

### Neutral

- 본 ADR 자체는 PROPOSED. Phase 1 결과 후 status 갱신 (ACCEPTED 또는 REJECTED).

---

## References

- TASK-MONO-046-7a (PR #289) — cycle 1+2 evidence (CB reset + forkEvery 1 둘 다 falsified)
- TASK-MONO-046-7 (PR #264) — 11-cycle burn 의 cycle 9-11 503 패턴
- TASK-MONO-044c-1 (PR #218) — `@DirtiesContext(AFTER_CLASS)` + lazy `AccountServiceClient` URL resolution
- ADR-003 (Public-client refresh_token converter) — Cluster A 의 architectural rework, 본 ADR 과 독립
- `OAuthLoginUseCaseTest` (`projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/application/`) — 단위 coverage
- `AccountServiceClient` — log 강화 대상
- Spring Boot reference — `RestClient` + JDK `HttpClient` configuration
- WireMock 3.x docs — `addMockServiceRequestListener`, dynamic-port binding semantics on Linux
