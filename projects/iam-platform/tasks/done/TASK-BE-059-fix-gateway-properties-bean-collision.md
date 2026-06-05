# Task ID

TASK-BE-059

# Title

gateway-service `GatewayProperties` bean 이름 충돌 해소 — 클래스 리네임

# Status

ready

# Owner

backend

# Task Tags

- code
- refactor

# depends_on

(없음)

---

# Goal

Spring Cloud Gateway의 auto-config가 제공하는 내장 `GatewayProperties` bean과 커스텀 `com.example.gateway.config.GatewayProperties`가 bean 이름(`gatewayProperties`)을 공유하여, 부팅 시 `BeanDefinitionOverrideException`으로 gateway-service가 기동 실패하는 이슈를 해결한다. 커스텀 클래스를 `EdgeGatewayProperties`로 리네임하여 Spring Cloud Gateway 내장 bean과 네임스페이스를 분리한다.

오류 요약 (부팅 시):

```
Invalid bean definition with name 'gatewayProperties' defined in ...GatewayAutoConfiguration:
Cannot register bean definition ... since there is already [Generic bean: class=com.example.gateway.config.GatewayProperties ...] bound.
```

---

# Scope

## In Scope

1. `apps/gateway-service/src/main/java/com/example/gateway/config/GatewayProperties.java` → `EdgeGatewayProperties.java`로 파일·클래스 리네임
2. 참조 5개 업데이트:
   - `config/WebClientConfig.java`
   - `ratelimit/TokenBucketRateLimiter.java`
   - `route/RouteConfig.java`
   - `security/JwksCache.java`
   - `test/ratelimit/TokenBucketRateLimiterTest.java`
3. 인스턴스 생성(`new GatewayProperties()`), inner class 참조(`GatewayProperties.ScopeLimit`, `GatewayProperties.RateLimitProperties`) 전부 새 이름으로 변경
4. `@ConfigurationProperties(prefix = "gateway")` prefix는 **유지** — YAML 호환성 보존 (`application.yml`의 `gateway: jwt: ...` 섹션 변경 불필요)

## Out of Scope

- YAML prefix 변경 (`edge.*` 등) — 호환성 파괴 불필요
- 커스텀 `GatewayProperties`의 필드/로직 변경 — 순수 리네임만
- Spring Cloud Gateway auto-config 비활성화
- Rate limit 정책 변경

---

# Acceptance Criteria

- [ ] `EdgeGatewayProperties.java` 존재, 동일 필드·inner class 보유
- [ ] `GatewayProperties.java` 파일 삭제됨
- [ ] gateway-service 내 모든 참조가 `EdgeGatewayProperties`로 갱신
- [ ] `./gradlew :apps:gateway-service:test` 통과
- [ ] `./gradlew :apps:gateway-service:bootRun`이 정상 기동 (`Tomcat started on port 8080`)
- [ ] `curl http://localhost:8080/actuator/health` → 200 UP
- [ ] YAML의 `gateway.*` prefix 설정이 계속 주입됨 (필드 값 동일성 테스트로 확인)

---

# Related Specs

- `specs/services/gateway-service/architecture.md`
- `platform/coding-rules.md`
- `platform/naming-conventions.md`

---

# Related Contracts

(없음 — 내부 구현 리네임, API/이벤트 변경 없음)

---

# Target Service

- `apps/gateway-service`

---

# Architecture

`specs/services/gateway-service/architecture.md` — rest-api service type. 변경 없음.

---

# Edge Cases

- Bean이 다른 서비스에서 import되는 경우: gateway-service만 해당 클래스 사용 → 타 서비스 영향 없음
- Spring Cloud Gateway가 향후 bean 이름 변경 시 이 리네임이 불필요해질 수 있으나, 현시점 표준 해결법
- `application.yml`의 `gateway.*` 섹션이 Spring Cloud Gateway와 커스텀 양쪽으로 해석되지 않도록 prefix 분리 고려 — 실제로 Spring Cloud Gateway auto-config는 `spring.cloud.gateway.*` prefix 사용하므로 커스텀 `gateway.*`와 겹치지 않음 (충돌은 bean **이름**만의 문제)

---

# Failure Scenarios

- 리네임 후에도 BeanDefinitionOverrideException 지속 → Spring Cloud Gateway 자체 bean 이름이 `gatewayProperties`와 정확히 동일한지 재확인 (현재 클래스명 `EdgeGatewayProperties` → 자동 bean 이름 `edgeGatewayProperties`로 달라지므로 발생 확률 낮음)
- 기존 테스트가 `GatewayProperties` 이름으로 Mock 참조할 때 컴파일 에러 → 테스트에서도 전부 리네임

---

# Test Requirements

순수 리네임이라 새 테스트 불필요. 기존 테스트(`TokenBucketRateLimiterTest`)가 리네임 후 통과하는지 확인.

- `./gradlew :apps:gateway-service:test` 통과
- 수동: `./gradlew :apps:gateway-service:bootRun` → `curl http://localhost:8080/actuator/health` 200

---

# Definition of Done

- [ ] 클래스·참조 리네임 완료
- [ ] gateway-service 단위 테스트 통과
- [ ] 로컬 bootRun 성공 확인
- [ ] Ready for review
