# Task ID

TASK-BE-664

# Title

auth · account 의 `/actuator/prometheus` 가 열려 있지 않아 두 서비스의 metric 이 한 번도 수집되지 않았다 + auth 스펙 필수 metric 2개 미구현(`auth_login_total` · `auth_token_reuse_detected_total`)

# Status

ready

# Owner

backend

# Task Tags

- observability
- code
- fix

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (로그인 경로가 여럿 — 어디서 세는지가 설계 판단)
> 기안 2026-11-24 UTC. 출처: `TASK-MONO-835`(runbook) 작성 중 발견 → 원칙은 `TASK-BE-662` 의 소유자 결정 «코드를 스펙에 맞춘다» 와 같다.

# Dependency Markers

- 후속: `TASK-MONO-835` — 그 티켓이 `check-alert-rule-metrics.sh` 의 오탐(아래 Goal ③)을 좁히면 이 티켓 전에는 iam 에서 빨강이다 → **이 티켓이 먼저**.

# Goal

2026-11-24 UTC 실측:

1. **수집이 안 된다.** `projects/iam-platform/apps/{auth,account}-service/src/main/resources/application.yml` 의 `management:` 에는 `health.mail` 뿐이고 `endpoints.web.exposure.include` 가 없다 → Spring Boot 기본값(`health` 만) → `/actuator/prometheus` 404. Prometheus(`infra/prometheus/prometheus.yml` 의 `auth-service` :8081 · `account-service` :8082 잡)는 이 두 타깃을 긁지 못하고, 그래서 **`TASK-BE-662` · `TASK-BE-663` 이 고친 `account_outbox_lag_seconds` · `auth_outbox_lag_seconds` 도 Prometheus 에 들어가지 않는다**(`ServiceDown` 은 두 타깃에서 늘 울린다). 형제 셋(gateway · security · admin)은 `include: health,info,prometheus[,…]` 를 갖는다. 레지스트리(`micrometer-registry-prometheus`)는 `libs/java-observability` 를 통해 런타임 클래스패스에 **있다**(auth `runtimeClasspath` 로 확인) — 빠진 것은 노출 한 줄.
2. **auth 스펙 필수 metric 2개가 없다** — `auth-service/observability.md:13` `auth_login_total` counter `result` (success/failure/rate_limited/account_locked) · :17 `auth_token_reuse_detected_total` counter. 알림 `AuthLoginFailureSpike`(`rate(auth_login_total{result="failure"}[5m]) > 20`) · `AuthRefreshTokenReuseDetected`(`increase(auth_token_reuse_detected_total[5m]) > 0`)가 영원히 no-data.
3. (참고 — 이 티켓 범위 밖) `TASK-MONO-834` 가드가 ② 를 놓친 이유: 토픽 이름 `"auth.token.reuse.detected"` 가 `.`→`_` 뒤 `auth_token_reuse_detected` 가 되고, 알림 이름에서 `_total` 을 떼면 같아져 «내보냄» 으로 읽혔다. `TASK-MONO-835` 가 좁힌다.

# Scope

## In Scope

1. auth-service · account-service `application.yml` `management.endpoints.web.exposure.include: health,info,prometheus,metrics`(gateway · security 와 같은 값). 기존 `management.health.mail.enabled: false` 는 유지.
2. `auth_login_total{result}` — 스펙의 네 값으로, **로그인 시도가 결과를 낸 곳에서 시도당 정확히 한 번**. 구현자가 auth 의 로그인 경로(`application/LoginUseCase` · `OAuthLoginUseCase` · 브라우저/SAS 로그인 등)를 읽고, 스펙이 말하는 «로그인 시도» 가 어느 경로들인지 정한 뒤 **그 판단과 근거를 증거 절에 적는다**. 기준: `rate_limited` = 로그인 rate-limit 으로 거절 · `account_locked` = 잠긴 계정이라 거절 · `failure` = 그 밖의 인증 실패(자격 불일치 등) · `success` = 토큰/세션 발급. 한 요청이 두 경로를 지나 두 번 세지지 않게.
3. `auth_token_reuse_detected_total` — refresh token 재사용을 **탐지한 순간마다 한 번**(지금 `auth.token.reuse.detected` 이벤트를 발행하는 지점 — `RefreshTokenUseCase` · `SasRefreshTokenAuthenticationProvider` 의 재사용 분기). 이벤트 발행 실패와 무관하게 탐지는 센다.
4. 단위 테스트(`SimpleMeterRegistry`) 분기별 + 노출 설정을 확인하는 테스트 하나(가능하면 기존 slice/actuator 테스트 패턴을 따른다 — 없으면 `application.yml` 을 읽어 단언하는 단위 테스트도 된다).

## Out of Scope

- 알림 규칙 변경 · 다른 서비스 · 스펙 표의 나머지 미구현 metric.

# Acceptance Criteria

- [ ] AC-1: 두 서비스의 노출 설정에 `prometheus` 가 들어 있다.
- [ ] AC-2: 두 metric 이 스펙의 이름 · 태그(값 4종)로 등록되고, 시도/탐지당 한 번이다. 어느 경로에서 세는지와 그 근거가 증거 절에 있다.
- [ ] AC-3: 분기별 단위 테스트 · auth-service · account-service `test` rc=0.
- [ ] AC-4: PR CI 의 iam Build & Test · Integration (iam A/B) 가 **실행되어** success.

# Related Specs

- `projects/iam-platform/specs/services/auth-service/observability.md` :13 · :17 · :70~74
- `projects/iam-platform/infra/prometheus/prometheus.yml`

# Related Contracts

- 없음(HTTP · 이벤트 계약 불변).

# Edge Cases

- 같은 로그인 요청이 rate-limit 필터와 use case 를 모두 지남 → 거절된 쪽에서 한 번만.
- 노출을 켜도 `SecurityConfig` 가 `/actuator/**` 를 permitAll 하고 있음(auth :173 · account :164) — 추가 보안 변경 없음. 🔴 `metrics` 엔드포인트 노출이 내부 정보를 드러낸다는 판단이 있으면 형제와 같은 값으로 맞추는 것이 기준이다(형제가 이미 노출).

# Failure Scenarios

- 노출만 켜고 레지스트리가 없다고 오해 → 실측상 있다(Goal ①). 다시 의존성을 추가하지 말 것.
- metric 을 이벤트 리스너에서 세면 이벤트 발행 실패 시 탐지가 사라진다 → 탐지 지점에서 센다.

# Test Requirements

- 단위 테스트 · 두 서비스 `test` · CI.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
