# Task ID

TASK-BE-665

# Title

스펙의 `AuthAccountServiceDown`(account-service 회로 차단기 open → 로그인 불가)이 배포 규칙에 없고, 그 알림이 물을 차단기 상태 지표도 만들어지지 않는다 — `accountService` 차단기를 Micrometer 에 묶인 레지스트리로 옮기고 알림 + runbook 을 단다

# Status

done

# Owner

backend

# Task Tags

- observability
- auth-service

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.5 (생성자 한 곳 + 알림 1 + runbook 1 + 테스트)
> 기안 2026-11-25 UTC. 출처: `TASK-BE-664` · `TASK-MONO-836` 증거의 «남은 것» → 소유자 «1번 진행».

# Dependency Markers

- 선행: `TASK-BE-664`(done) — auth-service `/actuator/prometheus` 가 열려 이제 긁힌다.

# Goal

2026-11-25 UTC 실측:

- `specs/services/auth-service/observability.md:73` 은 `AuthAccountServiceDown`(조건 «circuit breaker open», critical, «로그인 불가»)을 요구한다. `infra/prometheus/alert-rules.yml` 에 **없다**.
- 알림을 그냥 더해도 울리지 않는다. `AccountServiceClient` 의 차단기는 `ResilienceClientFactory.buildCircuitBreaker("accountService")` = `CircuitBreaker.of(...)` — **어떤 `CircuitBreakerRegistry` 에도 속하지 않는 독립 인스턴스**다. auth-service 런타임 클래스패스에는 `resilience4j-spring-boot3` · `resilience4j-micrometer` 2.2.0 이 있어(실측 `dependencies --configuration runtimeClasspath`) Spring 이 관리하는 레지스트리의 차단기는 `resilience4j_circuitbreaker_state` 로 내보내지지만, 독립 인스턴스는 그 레지스트리가 모른다 → 이 차단기의 상태 지표는 없다(구현에서 대조군 테스트로 확정).
- 저장소의 어떤 알림도 `resilience4j_circuitbreaker_*` 를 묻지 않는다 — 그래서 이 공백이 지금까지 안 드러났다. `TASK-MONO-834` 가드는 `resilience4j_` 를 프레임워크 접두사로 통과시키므로 이것을 못 본다.
- `TASK-BE-664` 가 정했다: account-service 장애(fail-closed `AuthenticationServiceException`)는 `auth_login_total{result="failure"}` 로 세지 않는다 — 세면 크리덴셜 스터핑 알림이 울린다. 그 장애를 맡는 것이 이 알림이다. 지금은 **아무 알림도 맡지 않는다**.

# Scope

## In Scope

1. `AccountServiceClient` 의 운영 생성자(`@Autowired`)가 Spring 의 `CircuitBreakerRegistry` 빈을 받아 `registry.circuitBreaker("accountService", ResilienceClientFactory.standardCircuitBreakerConfig().build())` 로 차단기를 얻는다 — 설정값은 지금과 같다. 테스트 전용 생성자는 그대로(독립 인스턴스).
2. `infra/prometheus/alert-rules.yml` 에 `AuthAccountServiceDown`: `max(resilience4j_circuitbreaker_state{service="auth-service",name="accountService",state=~"open|forced_open"}) == 1`, `for: 1m`, `severity: critical`, `service: auth-service`, `runbook: "knowledge/runbooks/auth-account-service-down.md"`. 식의 label 이름 · 값은 테스트 scrape 에서 본 것으로 확정한다(위는 Resilience4j 2.2 `TaggedCircuitBreakerMetrics` 의 문서상 형태).
3. `knowledge/runbooks/auth-account-service-down.md`(①~⑤ 절, `TASK-MONO-835` 형식) + `README.md` 색인 행.
4. 테스트: ① Spring 의 resilience4j · metrics 자동 구성으로 띄운 컨텍스트에서 `registry.circuitBreaker("accountService", …)` 의 상태가 Prometheus scrape 에 `name="accountService"` 로 나온다 ② **대조군** — 같은 컨텍스트에서 `CircuitBreaker.of("accountService", …)` 로 만든 차단기는 scrape 에 없다(이 티켓 전제의 증명) ③ 차단기를 `transitionToOpenState()` 하면 `state="open"` 줄의 값이 1 이다 ④ `AccountServiceClient` 운영 생성자가 레지스트리에서 차단기를 얻는다(레지스트리에 `accountService` 가 생긴다).

## Out of Scope

- auth-service 의 나머지 독립 차단기 3개(`AdminAssignmentClient` 등) · account-service 1 · ecommerce order-service 1 — 알림이 없다. 같은 방식으로 옮길지는 후속 판단(PR 본문에 목록).
- `ResilienceClientFactory`(`libs/java-common`) 변경 — 레지스트리를 받는 오버로드를 라이브러리에 둘지는 위 후속과 함께.
- 차단기 설정값 변경.

# Acceptance Criteria

- [x] AC-1: 테스트 ①~④ 통과 · ② 는 독립 인스턴스의 지표가 **없음** 을 단언한다.
- [x] AC-2: 알림 식의 label 이 테스트 scrape 에서 본 실제 이름과 같다(PR 본문에 scrape 줄 인용). `promtool check rules` SUCCESS.
- [x] AC-3: `check-alert-rule-metrics.sh` rc=0(runbook 링크 포함) · auth-service `test` 통과.
- [x] AC-4: 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) · PR CI 실패 0.

# Related Specs

- `specs/services/auth-service/observability.md` § Alerts
- `specs/services/auth-service/architecture.md`(account-service 호출 · fail-closed)

# Related Contracts

- 없음.

# Edge Cases

- `forced_open` 도 로그인 불가이므로 같은 알림에 넣는다. `half_open` 은 회복 시도 중이라 넣지 않는다.
- 레지스트리의 기본 설정(`resilience4j.circuitbreaker.configs.default`)이 yml 에 있다면 `circuitBreaker(name, config)` 의 명시 config 가 이긴다 — 설정 불변을 테스트 ④ 에서 config 값으로 확인.

# Failure Scenarios

- 알림 label 을 문서에서 옮겨 적어 실제 scrape 와 다르다 → 영원히 no-data. AC-2 가 scrape 인용을 요구한다.
- 레지스트리 빈이 없는 테스트 컨텍스트(슬라이스 테스트)에서 `AccountServiceClient` 생성이 깨진다 → auth-service 전체 `test` 로 확인.

# Test Requirements

- 위 ①~④ · auth-service `test` · promtool · 가드 · CI.

# Implementation Evidence (2026-11-25 UTC)

- **변경**: `AccountServiceClient` 운영 생성자가 `CircuitBreakerRegistry` 를 받아 `registry.circuitBreaker("accountService", ResilienceClientFactory.standardCircuitBreakerConfig().build())` — 설정값 그대로. 테스트 전용 생성자는 독립 인스턴스 그대로(기존 클라이언트 테스트 5클래스 60건 PASS). 이름은 상수 `CIRCUIT_BREAKER_NAME`. `alert-rules.yml` 에 `AuthAccountServiceDown` · runbook `auth-account-service-down.md` + README 행. auth-service `testImplementation 'io.micrometer:micrometer-registry-prometheus'`(실제 scrape 용 — 런타임엔 이미 `libs/java-observability` 로 있음).
- **AC-1** — `AccountServiceCircuitBreakerMetricsTest` 5/5(Boot metrics + Prometheus + resilience4j-spring-boot3 자체 자동 구성, 실제 scrape 문자열): ① 운영 생성자의 차단기 → `state="closed"` 1.0 ② **대조군 — `CircuitBreaker.of("accountService")` 를 open 시켜도 scrape 에 `name="accountService"` 가 없다**(이 티켓 전제의 증명) ③ `transitionToOpenState()` → `state="open"` 1.0 ④ 설정값(실패율 · 창 · 최소 호출 · half-open 호출 · open 대기) 동일 ⑤(추가) 차단기 open 이 auth-service health 를 DOWN 으로 만들지 않는다 — (a) 기본값에선 resilience4j health indicator 자체가 없다(메타데이터 `management.health.circuitbreakers.enabled` default false, auth yml 미설정) (b) 강제로 켜도 `accountService` 는 indicator 에 없고 DOWN 아님. **bite**: 운영 생성자를 `CircuitBreaker.of` 로 되돌리면 ①④ 실패 → 복원.
- **AC-2** — 테스트 scrape 에서 본 줄: `resilience4j_circuitbreaker_state{name="accountService",state="open"} 1.0`(state 값 = closed · disabled · forced_open · half_open · metrics_only · open). 운영에선 `service="auth-service"` 가 `prometheus.yml` 정적 label 과 라이브러리 공통 태그로 붙는다. 식 = `max(resilience4j_circuitbreaker_state{service="auth-service",name="accountService",state=~"open|forced_open"}) == 1`, `for: 1m`(차단기가 10초마다 half_open 을 오가므로 `max` + 1분). `promtool check rules` SUCCESS 15 rules — 이미지 삭제.
- **AC-3** — `check-alert-rule-metrics.sh` rc=0(«55 metric reference(s) … 20 runbook link(s)»). auth-service 전체 `test` rc=0 — **1344 tests, 실패 0**.
- **AC-4** — 스테이지 뒤 `check-*.sh` 43: 41 rc=0, 비0 둘 = 환경 전제(erp 래칫 · prerendered verdict). `.mjs` 6/6. PR CI(#4685): 실패 0 · SUCCESS 22 · SKIPPED 51 — `Integration (iam A/B …)` · `E2E smoke (iam docker-compose)` · `Alert rule metrics …` **실행되어** SUCCESS.
- 🔵 남은 것(티켓 없음): auth-service 의 나머지 독립 차단기 3(`adminAssignment` · `adminOperatorFacet` · `adminOperatorInvitation`) · account-service `authService` · ecommerce order-service 1 — 알림이 없어 지표가 없어도 지금은 아무것도 안 깨진다.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
