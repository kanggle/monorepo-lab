# Task ID

TASK-MONO-044d

# Title

fan-platform gateway-service 의 `RateLimitConfig` `KeyResolver` bean 모호성 fix (servlet leak 해소 후 노출)

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

[TASK-MONO-044a](../done/TASK-MONO-044a-libs-java-web-servlet-leak-fix.md) 가 `libs/java-web` servlet leak 을 해소하면서 fan-platform-gateway-service 가 reactive 모드로 정상 부팅을 시도. 그러나 부팅 시 Spring Cloud Gateway 의 `requestRateLimiterGatewayFilterFactory` 가 `KeyResolver` bean 두 개 사이에서 ambiguous 로 부팅 fail.

CI run `25338898652` (PR #201, 044a) 의 `E2E (fan-platform v1 live-trio, Testcontainers)` Job 컨테이너 로그:

```
APPLICATION FAILED TO START
Description:
  Parameter 1 of method requestRateLimiterGatewayFilterFactory in
  org.springframework.cloud.gateway.config.GatewayAutoConfiguration
  required a single bean, but 2 were found:
    - clientIpKeyResolver: defined by method 'clientIpKeyResolver' in class path resource [com/example/fanplatform/gateway/config/RateLimitConfig.class]
    - accountKeyResolver:  defined by method 'accountKeyResolver'  in class path resource [com/example/fanplatform/gateway/config/RateLimitConfig.class]
Action:
  Consider marking one of the beans as @Primary, updating the consumer to accept multiple beans, or using @Qualifier to identify the bean that should be consumed
```

이 회귀는 servlet leak 으로 부팅이 차단되어 있던 동안 묻혀 있던 잠재 결함. 044a 머지 후 처음 가시화.

본 task 가 fix 후 `E2E (fan-platform v1 live-trio)` Job FAILURE → SUCCESS.

---

# Scope

## In Scope

- `projects/fan-platform/apps/gateway-service/src/main/java/com/example/fanplatform/gateway/config/RateLimitConfig.java` 의 `KeyResolver` bean 정의 정리.
- 다음 세 옵션 중 하나 채택 (PR description 에 결정 근거 기록):
  - **(i) `@Primary` 명시**: 두 bean 중 의도된 default 에 `@Primary` 부착. 가장 작은 변경.
  - **(ii) `defaultKeyResolver` 명시**: Spring Cloud Gateway 가 인식하는 `defaultKeyResolver` 라는 이름의 bean 만 등록하고, 나머지는 route-별 `key-resolver` 참조 가능하게 명시 이름. 의도 명확성 가장 높음.
  - **(iii) 한 bean 으로 통합**: 두 resolver 의 사용처를 점검해 실제 필요 1개만 남기고 다른 하나 제거.
- `application.yml` / `application-e2e.yml` 의 rate-limit 설정에서 어느 resolver 를 가리키는지 (또는 default 의존인지) 확인 + 정합성 보장.
- `projects/fan-platform/tests/e2e/` 의 3 테스트 클래스 (`ArtistAndPostFlowE2ETest`, `MultiTenantIsolationE2ETest`, `VisibilityTierE2ETest`) initializationError 해소 검증.

## Out of Scope

- rate-limit 정책 자체의 설계 변경 (resolver 선택은 유지, 모호성만 해소)
- `clientIpKeyResolver` / `accountKeyResolver` 의 비즈니스 로직 변경
- 044a 영향이 아닌 다른 fan-platform 회귀 (현 시점 기타 회귀 미관측)

---

# Acceptance Criteria

## 부팅 회복

1. fan-platform-gateway-service 컨테이너가 `BeanCreationException`/ambiguity 없이 부팅
2. `:projects:fan-platform:tests:e2e:e2eTest` 의 3 e2e 테스트 클래스 (`ArtistAndPostFlowE2ETest`, `MultiTenantIsolationE2ETest`, `VisibilityTierE2ETest`) 가 initializationError 없이 시작
3. main CI 의 `E2E (fan-platform v1 live-trio, Testcontainers)` Job FAILURE → SUCCESS

## 회귀 0

4. fan-platform 의 다른 e2e 시나리오 (rate-limit 시나리오 포함) 회귀 0
5. fan-platform-gateway-service `:check` PASS

## 진단 + 문서

6. 결정 근거 (옵션 i / ii / iii) PR description 에 기록
7. `RateLimitConfig.java` 변경 부분에 의도 주석 (왜 두 resolver 가 공존하는지 또는 통합 사유)
8. 회귀 보고서 `knowledge/incidents/2026-05-05-ci-regression.md` 에 fan-platform 후속 결과 단락 추가

---

# Related Specs

- [TASK-MONO-044 진단 보고서](../../knowledge/incidents/2026-05-05-ci-regression.md) § Job 3
- [TASK-MONO-044a (servlet leak fix)](../done/TASK-MONO-044a-libs-java-web-servlet-leak-fix.md) — 직접 선행
- `projects/fan-platform/specs/services/gateway-service/` (rate-limit 설계)
- Spring Cloud Gateway `RequestRateLimiterGatewayFilterFactory` 문서

---

# Related Contracts

- 없음 (gateway-service 내부 config)

---

# Target Service / Component

- `projects/fan-platform/apps/gateway-service/src/main/java/com/example/fanplatform/gateway/config/RateLimitConfig.java`
- `projects/fan-platform/apps/gateway-service/src/main/resources/application*.yml` (필요 시)

---

# Implementation Notes

- 옵션 (ii) 가 권장: `defaultKeyResolver` 라는 명시 이름을 단 1개 bean 만 등록 → Spring Cloud Gateway autoconfig 가 자동 인식. 나머지 resolver 는 별도 이름 (`accountKeyResolver`) 으로 두고 application.yml 의 route-별 `key-resolver: "#{@accountKeyResolver}"` SpEL 로 명시 참조.
- 옵션 (i) 은 빠른 fix 이지만 어느 resolver 가 default 인지 의도 모호 — 코드 리뷰에서 혼동 위험.
- 옵션 (iii) 은 두 resolver 의 사용처를 점검해야 하며 만약 둘 다 사용 중이면 부적합.
- 검증 명령:
  ```
  ./gradlew :projects:fan-platform:apps:gateway-service:check
  ./gradlew :projects:fan-platform:apps:gateway-service:bootRun --args='--spring.profiles.active=e2e'  # smoke
  ./gradlew :projects:fan-platform:tests:e2e:e2eTest
  ```
- `git blame` 으로 `RateLimitConfig` 의 두 bean 도입 history 확인 권장 — 의도 추적.

---

# Edge Cases

1. **`accountKeyResolver` 가 multi-tenant rate limiting 의 핵심**: 이미 application.yml 의 routes 가 어느 resolver 를 가리키는지 검증. 양쪽 모두 사용 중이면 옵션 (ii) 만 유효.
2. **다른 service (fan-platform 외) 의 RateLimitConfig 패턴**: 동일 패턴이 다른 reactive gateway (WMS / GAP / scm) 에도 있는지 grep 으로 확인. 있으면 cross-project fix 검토.
3. **테스트 환경에서만 실패하고 dev 환경에서는 무관**: dev 환경 application.yml 의 rate-limit 설정 차이로 한쪽만 활성일 가능성. e2e profile 도 동일 fix 적용 여부 검증.

---

# Failure Scenarios

## A. 옵션 (ii) 적용 후 application.yml 의 SpEL 참조가 working 안 함

reactive gateway 의 `key-resolver: "#{@beanName}"` 문법이 deprecated / changed 가능성 — 옵션 (i) 또는 (iii) 으로 fallback.

## B. 두 resolver 가 모두 production 에서 사용 중

옵션 (iii) 폐기. (i) 또는 (ii) 만 가능.

## C. 044a 머지 후 더 깊은 reactive boot 회귀 노출

본 task 범위 밖. 별도 follow-up 발행.

---

# Test Requirements

- fan-platform-gateway-service `:check` PASS
- `:projects:fan-platform:tests:e2e:e2eTest` PASS
- main CI `E2E (fan-platform v1 live-trio)` Job SUCCESS 검증
- 회귀 보고서 단락 갱신

---

# Definition of Done

- [ ] 옵션 (i)(ii)(iii) 중 선택 + 적용
- [ ] `RateLimitConfig.java` 변경 + 의도 주석
- [ ] application*.yml 정합성 확인 (필요 시 SpEL 참조 갱신)
- [ ] 3 e2e 테스트 로컬 PASS
- [ ] main CI `E2E (fan-platform v1 live-trio)` Job SUCCESS 검증
- [ ] 회귀 보고서 단락 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** — Spring Cloud Gateway `KeyResolver` ambiguity 는 표준 패턴 fix. application.yml SpEL 참조 검증만 주의.
- **분량 추정**: 1 java 파일 + (필요 시) 1-2 yml 파일. 작은 PR.
- **dependency**:
  - `선행`: TASK-MONO-044a (이미 머지됨)
  - `후속`: 없음
- **CI gating**: 본 PR 자체 영향 = `E2E (fan-platform v1 live-trio)` Job FAIL → SUCCESS. 다른 Job 영향 0.
