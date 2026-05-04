# Task ID

TASK-MONO-044a

# Title

`libs/java-web` 의 servlet 의존성이 reactive gateway consumer로 누출되는 회귀 fix (3 gateway 부팅 회복)

# Status

ready

# Owner

backend / qa

# Task Tags

- code
- test
- libs

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

[TASK-MONO-044](../review/TASK-MONO-044-main-baseline-ci-regression-cleanup.md) 의 진단 (`knowledge/incidents/2026-05-05-ci-regression.md`) 에서 **4 FAIL Job 중 3개의 단일 root cause** 로 환원된 회귀를 fix.

**도입 commit**: `cf901f4b` (2026-04-30, TASK-MONO-017 PR #93). `libs/java-web/build.gradle` 이 `CommonGlobalExceptionHandler` (servlet `@ControllerAdvice`) 호스팅을 위해 `spring-web` / `spring-webmvc` / `spring-orm` / `jakarta.servlet-api` 를 **`implementation`** 으로 추가 — 이로 인해 `libs:java-web` 을 사용하는 모든 reactive gateway-service (WMS / GAP / fan-platform) 의 classpath 에 servlet API 가 transitive 로 노출. Spring Security autoconfig 가 servlet (`WebSecurityConfiguration`) 과 reactive (`WebFluxSecurityConfiguration`) 를 동시 활성화 → 동일 bean (`conversionServicePostProcessor`) 두 번 등록 시도 → `BeanDefinitionOverrideException` → APPLICATION FAILED TO START.

본 task 가 fix 후:
- Integration (global-account-platform, Testcontainers) Job — gateway-service `integrationTest` 의 `MissingWebServerFactoryBeanException` 해소
- E2E (gateway-master live-pair, Testcontainers) Job — wms-platform gateway 컨테이너 `BeanDefinitionOverrideException` 해소
- E2E (fan-platform v1 live-trio, Testcontainers) Job — fan-platform gateway 컨테이너 동일 예외 해소

Frontend E2E full-stack Job 은 [TASK-MONO-044b](TASK-MONO-044b-traefik-net-ci-overlay-fix.md) 가 별도 fix. 044a + 044b 모두 머지되면 4 Job 회복.

---

# Scope

## In Scope

- 3 reactive gateway-service (`projects/wms-platform/apps/gateway-service`, `projects/global-account-platform/apps/gateway-service`, `projects/fan-platform/apps/gateway-service`) 가 servlet API 를 classpath 에서 보지 않도록 의존성 그래프 재구성.
- 다음 두 전략 중 하나 채택 (PR description 에 결정 근거 기록):
  - **(i) sub-module 분리**: `libs/java-web-servlet/` 신설하여 `CommonGlobalExceptionHandler` + servlet 의존성을 그쪽으로 이전. `libs/java-web` 는 framework-agnostic util만 보유. 6 servlet GAP 서비스 + ecommerce 서비스의 `libs:java-web-servlet` 의존 wiring 갱신.
  - **(ii) `compileOnly` 다운그레이드**: `libs/java-web` 의 servlet 의존성을 `compileOnly` 로 격하 (라이브러리 자체 컴파일에만 필요). servlet consumer는 자기 starter (`spring-boot-starter-web`) 로 servlet API 를 가져옴. 더 작은 PR; 그러나 라이브러리의 의도가 servlet-only 라는 사실을 README/주석으로 명문화 필요.
- 3 reactive gateway 의 application context 부팅 검증:
  - `:projects:global-account-platform:apps:gateway-service:integrationTest` 의 8 통합 테스트 PASS
  - `:projects:wms-platform:apps:gateway-service:e2eTest` 의 `GatewayMasterE2ETest` initializationError 해소
  - `:projects:fan-platform:tests:e2e:e2eTest` 의 3 e2e 테스트 클래스 initializationError 해소
- Servlet 기반 consumer (GAP 6 서비스 + ecommerce 서비스 등) 의 회귀 0 검증.
- `libs/java-web/README.md` (있으면 갱신, 없으면 신설) 에 의도된 사용 패턴 명문화.

## Out of Scope

- Frontend E2E full-stack Job 의 traefik-net 회귀 — TASK-MONO-044b
- nightly main CI run / 회귀 재발 방지 자동화 — 별도 sub-task 후속 (TASK-MONO-044 의 AC #8)
- 기타 4 Job 외 sporadic e2e timeout / flaky — 별도 후속, 4 Job 회복 후 평가
- `libs/java-web` 의 framework-agnostic 분리 광범위 리팩토링 (현재 `CommonGlobalExceptionHandler` 외 servlet 한정 코드 없음 가정 — 분리 시 점진적)

---

# Acceptance Criteria

## 부팅 회복

1. `:projects:global-account-platform:apps:gateway-service:integrationTest` 가 PASS (8 통합 테스트 모두 PASS)
2. `:projects:wms-platform:apps:gateway-service:e2eTest` 의 `GatewayMasterE2ETest` 가 initializationError 없이 부팅, 모든 시나리오 PASS
3. `:projects:fan-platform:tests:e2e:e2eTest` 의 `ArtistAndPostFlowE2ETest` / `MultiTenantIsolationE2ETest` / `VisibilityTierE2ETest` 가 initializationError 없이 부팅, PASS

## 회귀 0

4. GAP 6 servlet 서비스 (`account-service`, `admin-service`, `auth-service`, `community-service`, `membership-service`, `security-service`) `:check` PASS — `CommonGlobalExceptionHandler` 가 정상 동작
5. ecommerce backend 서비스 11개 (auth-service 제외) `:check` PASS

## 진단 + 문서

6. PR description 에 (i) vs (ii) 전략 선택 근거 기록
7. `libs/java-web/` 의 의도된 사용 패턴 (servlet 전용 / framework-agnostic) 이 README 또는 build.gradle 주석으로 명문화

## CI

8. main 의 다음 CI run 에서 Integration (GAP) + E2E (gateway-master) + E2E (fan-platform) 3 Job 이 모두 PASS

---

# Related Specs

- [TASK-MONO-044 진단 보고서](../../knowledge/incidents/2026-05-05-ci-regression.md) § "Root Cause #1"
- [TASK-MONO-017 (libs promotion 도입 commit)](../done/TASK-MONO-017-import-global-account-platform.md) — 회귀 도입 history
- [TASK-MONO-031 (libs audit)](../done/TASK-MONO-031-libs-audit.md) — `libs/java-web` 사용 빈도 매트릭스
- `libs/java-web/build.gradle` — 변경 대상
- `platform/shared-library-policy.md` — 라이브러리 의존성 정책

---

# Related Contracts

- 없음 (라이브러리 내부 의존성 wiring; API/contract 변경 없음)

---

# Target Service / Component

- `libs/java-web/build.gradle` (의존성 재구성)
- (전략 i 채택 시) `libs/java-web-servlet/` 신설 + `CommonGlobalExceptionHandler.java` 이전
- 3 reactive gateway-service `build.gradle`:
  - `projects/wms-platform/apps/gateway-service/build.gradle`
  - `projects/global-account-platform/apps/gateway-service/build.gradle`
  - `projects/fan-platform/apps/gateway-service/build.gradle`
- (전략 i 채택 시) servlet consumer 의 `build.gradle` 갱신:
  - `projects/global-account-platform/apps/{account,admin,auth,community,membership,security}-service/build.gradle`
  - ecommerce 서비스 (CommonGlobalExceptionHandler 사용 여부 확인 후 필요 시)

---

# Implementation Notes

- 우선 `grep -r "CommonGlobalExceptionHandler" projects/ libs/` 로 사용처 카탈로그.
- 전략 (i) 권장 이유: `libs/java-web` 의 `web` 명칭이 framework-agnostic 으로 읽혀야 자연스러우나 현재 servlet 전용 클래스만 호스팅. 분리하면 `libs/java-web-servlet/` 명칭이 의도를 명확히 함.
- 전략 (ii) 권장 이유: 변경 표면 최소. ADR-MONO-002 D3 churn 안정 평가 기간에 부합.
- 두 전략 모두 reactive gateway 의 `build.gradle` 의 `implementation project(':libs:java-web')` 줄을 검토:
  - 전략 (i): 만약 reactive gateway 가 `libs:java-web` 를 정말 사용 안 하면 의존성 자체를 삭제. 사용 중이면 framework-agnostic util 만 분리된 `libs/java-web` 의존을 유지.
  - 전략 (ii): 그대로 두되 transitive servlet 이 `compileOnly` 라 reactive runtime 에 영향 없음을 확인.
- Spring Boot 의 `WebApplicationType` detection: classpath 에 servlet API + WebFlux 가 모두 있으면 SERVLET 으로 falls back 함 (`SpringApplication.deduceFromClasspath()`). 이는 reactive gateway 의 Spring Cloud Gateway autoconfig (`@ConditionalOnWebApplication(type = REACTIVE)`) 와 충돌. 의존성 정리가 fundamental fix.
- 검증 명령:
  ```
  ./gradlew :projects:global-account-platform:apps:gateway-service:integrationTest
  ./gradlew :projects:wms-platform:apps:gateway-service:e2eTest
  ./gradlew :projects:fan-platform:tests:e2e:e2eTest
  ./gradlew :projects:global-account-platform:apps:account-service:check
  ./gradlew :projects:global-account-platform:apps:auth-service:check
  ```
  Docker Desktop / WSL2 활성 필요 (Testcontainers).

---

# Edge Cases

1. **CommonGlobalExceptionHandler 의 reactive consumer 발견**: 현재 가정은 0 — 만약 reactive gateway 중 하나가 이를 implicit 으로 의존했다면 분리/제거 후 부팅 실패 가능. PR 의 첫 단계에서 grep + `:dependencies` task 로 검증.
2. **servlet consumer 가 transitive 로 servlet API 를 못 받음**: 전략 (ii) 채택 시 `compileOnly` 다운그레이드는 servlet consumer 의 own `spring-boot-starter-web` 가 servlet API 를 가져오므로 안전 — 하지만 만약 자기 starter 가 없는 라이브러리 consumer 가 있다면 (이론상) 컴파일 실패. `:dependencies` 로 사전 검증.
3. **fan-platform community-service 의 `JwtConfig` 가 `Rs256JwtVerifier` (libs/java-security) 사용**: 본 task 와 무관 — `libs/java-security` 는 servlet/reactive 양쪽에 안전한 pure JWT 클래스만 호스팅. 단 cross-check 권장.
4. **분리 후 `libs/java-web/build.gradle` 가 빈 라이브러리 됨**: 만약 `CommonGlobalExceptionHandler` 외에 호스팅 클래스가 없으면 `libs/java-web` 자체를 retire 검토. 별도 후속 task 영역.
5. **PR scope 폭발**: 전략 (i) 채택 + servlet consumer 가 8개 이상이면 build.gradle 변경이 대규모. 필요 시 (i) 안에서도 점진적 (libs split 1차 PR + consumer 갱신 2차 PR) 분할 검토.

---

# Failure Scenarios

## A. 전략 (ii) 후 reactive gateway 부팅은 회복되나 servlet consumer 의 일부가 NoClassDefFoundError

`compileOnly` 다운그레이드가 너무 공격적. 해당 consumer 의 build.gradle 에 servlet starter 가 명시되지 않았거나, `libs:java-web` 의 transitive 에만 의존하던 케이스. mitigation: 해당 consumer 의 build.gradle 에 `spring-boot-starter-web` (또는 직접 `spring-web`) 명시 추가.

## B. Reactive gateway 의 부팅이 회복되나 통합 테스트 다른 시나리오에서 회귀

WMS gateway-master e2e 의 250 burst rate-limit 시나리오 등이 application context 회복 후 별도 회귀를 노출할 가능성. 본 task 의 scope 밖이지만 별도 회귀로 분류 → 신규 sub-task (TASK-MONO-044d 등) 발행.

## C. 분리한 `libs/java-web-servlet` 가 다른 reactive consumer 를 누락 시키지 못함

`libs/java-web-servlet` 가 본의 아니게 reactive consumer 의 build.gradle 에 의존성으로 추가되면 회귀 재현. PR review 에서 build.gradle diff 의존성 변경을 일일이 검증 + sample build 명령 PR description 에 첨부.

---

# Test Requirements

- 8 GAP 통합 테스트 (`gateway-service:integrationTest`) PASS
- WMS GatewayMasterE2ETest 의 모든 시나리오 PASS
- 3 fan-platform e2e 테스트 클래스 PASS
- GAP 6 servlet 서비스 + ecommerce 서비스 `:check` 회귀 0
- main 머지 후 다음 CI run 의 Integration / E2E / fan-platform e2e 3 Job 모두 PASS

---

# Definition of Done

- [ ] `libs/java-web` 의존성 재구성 완료 (전략 i 또는 ii)
- [ ] 3 reactive gateway 부팅 회복 검증 (Testcontainers run 결과 PR 첨부)
- [ ] servlet consumer 회귀 0 검증
- [ ] PR description 에 전략 선택 근거 + sample build 명령 결과 기록
- [ ] CI run 의 3 관련 Job PASS
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — 라이브러리 의존성 그래프 재구성은 영향 분석 (consumer 카탈로그 + Spring Boot autoconfig 동작 + servlet/reactive split) 동시 작업 필요.
- **분량 추정**:
  - 전략 (i): 신규 module 1개 + 클래스 1개 이전 + build.gradle 6–10개 갱신 = 중간 PR
  - 전략 (ii): build.gradle 1개 (libs/java-web) + 3 reactive gateway 검증 = 작은 PR
- **dependency**:
  - `선행`: 없음 (TASK-MONO-044 진단 완료 후 즉시 시작 가능)
  - `후속`: TASK-MONO-044b 와 독립. 044a + 044b 모두 머지 후 4 CI Job 회복.
- **Phase 4 evaluation 영향**: ADR-MONO-002 D3 churn 안정 평가의 라이브러리 churn 비용 측면. 본 task 는 churn 자체를 발생시키나, root cause 가 명확하므로 churn 후 안정성 회복 신호.
