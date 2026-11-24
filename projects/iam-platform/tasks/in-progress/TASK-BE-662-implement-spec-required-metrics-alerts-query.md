# Task ID

TASK-BE-662

# Title

스펙이 «필수» 로 정했고 배포된 알림 규칙이 조회하는데 코드가 등록하지 않는 metric 4개를 구현한다 — `gateway_jwt_validation_total` · `admin_command_total` · `account_outbox_lag_seconds` · `security_auto_lock_issued_total`

# Status

in-progress

# Owner

backend

# Task Tags

- observability
- code

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (`admin_command_total` 의 outcome 분류 · JWT 결과 분류가 설계 판단)
> 기안 2026-11-24 UTC. 출처: `TASK-MONO-833` 작업 중 발견 → 소유자 결정 «코드에 구현 (권장)»(스펙·알림은 그대로, 코드를 스펙에 맞춘다).

# Dependency Markers

- 후속: `TASK-MONO-834`(알림 규칙 ↔ 코드 metric 가드). 834 의 가드는 이 티켓이 머지되기 전에는 iam 에서 빨강이다 — **이 티켓이 먼저**.

# Goal

`projects/iam-platform/infra/prometheus/alert-rules.yml` 의 알림 다섯 개가 코드에 없는 metric 을 조회한다 → **영원히 울리지 않는다**(2026-11-24 UTC 실측: 네 이름 모두 iam 코드 · `libs/` 어디에도 문자열로 없음):

| 알림 | 조회 metric | 스펙 정의 |
|---|---|---|
| `GatewayJwtFailureSpike` | `gateway_jwt_validation_total{result="failure"}` | `gateway-service/observability.md:15` — counter, `result` (valid/expired/invalid_signature/missing) |
| `AdminActionSpike` · `AdminCommandFailureHigh` | `admin_command_total` (· `{outcome="failure"}`) | `admin-service/observability.md:13` — counter, `action_code`, `outcome` (success/failure/denied). 같은 파일 :33 은 파트너십 전이도 이걸로 센다고 적는다 |
| `AccountOutboxLagHigh` | `account_outbox_lag_seconds` | `account-service/observability.md:20` — gauge, 미발행 outbox 최대 나이 |
| `SecurityAutoLockSpike` | `security_auto_lock_issued_total` | `security-service/observability.md:20` — counter, `result` (success/failure) |

스펙이 source of truth 이므로 코드를 스펙에 맞춘다.

# Scope

## In Scope

1. **gateway-service** — `gateway_jwt_validation_total{result}` 를 JWT 를 검사하는 경로(`filter/JwtAuthenticationFilter` · `security/TokenValidator`)에서, **JWT 검사가 일어난 요청마다 정확히 한 번**:
   - `valid` — 검증 통과 · `expired` — `exp` 만료 · `missing` — 보호 경로인데 토큰 없음 · `invalid_signature` — **그 밖의 모든 검증 실패**(형식 · kid 없음/모름 · 서명 · issuer 등). 🔴 스펙의 값 목록이 넷뿐이므로 «만료가 아닌 실패 전부» 를 여기에 묶는다 — `gateway-service/observability.md` 의 그 행에 이 정의를 **한 줄** 명시(스펙을 먼저 고친다).
   - 공개 경로(토큰 검사 없음)는 세지 않는다.
   - ⚠️ 알림식은 `result="failure"` 를 조회한다 — 스펙 값에 `failure` 는 없다. 알림식 정정(`result!="valid"`)은 **`TASK-MONO-834`** 몫(알림 규칙 파일). 이 티켓은 스펙의 값을 따른다.
2. **admin-service** — `admin_command_total{action_code,outcome}` 를 `application/AdminActionAuditor` 한 곳에서: SUCCESS/FAILURE 로 끝나는 기록(`recordCompletion` · `record` · `recordWithPermission`) → `success`/`failure`, DENIED 기록(`recordDenied` 와 `record*Denied` · `recordRoleGrantForbidden` 계열) → `denied`. `IN_PROGRESS`(`recordStart`) · 로그인 감사(`recordLogin`)는 명령 완료가 아니므로 세지 않는다. `action_code` = `ActionCode` 이름(null → `UNKNOWN`, 기존 `admin.audit.*` 카운터와 같은 규칙). 🔴 감사 쓰기가 실패해 예외가 나는 경로에서 «셌는데 기록은 없음» 이 되지 않게 — 기록이 성공한 뒤에 센다.
3. **account-service** — `account_outbox_lag_seconds` gauge: 미발행 outbox 행 중 가장 오래된 것의 나이(초), 없으면 0. **auth-service 의 `infrastructure/messaging/OutboxLagMetric` 를 그대로 본뜬다**(같은 의미 · 같은 실패 처리 — 형제가 이미 진단한 수정이 있으면 그대로 따른다, CLAUDE.md § Required Workflow 10).
4. **security-service** — `security_auto_lock_issued_total{result}` 를 `infrastructure/client/AccountServiceClient.lock()` 의 **최종 결과마다 한 번**: `SUCCESS` · `ALREADY_LOCKED` → `success`, `FAILURE`(재시도 소진 · 409 외 4xx) · `INVALID_TRANSITION`(409) → `failure`. 재시도 시도마다 세지 않는다(그건 `security_auto_lock_retries_total`).
5. 각 metric 에 단위 테스트(`SimpleMeterRegistry` 로 값 · 태그 단언) — 분기별 하나 이상.

## Out of Scope

- 알림 규칙 파일 수정(→ `TASK-MONO-834`).
- 스펙에는 있지만 알림이 조회하지 않는 나머지 미구현 metric(security-service 스펙 표 11개 중 9개 등) — 범위가 다르다. `TASK-MONO-834` 의 증거 절에 «남은 것» 으로 기록.
- 기존 metric 이름 · 태그 변경.

# Acceptance Criteria

- [ ] AC-0: 착수 직전 — 네 이름이 여전히 iam 코드 · `libs/` 에 없는가(있으면 그 metric 은 범위에서 빼고 기록).
- [ ] AC-1: 네 metric 이 스펙의 이름 · 유형 · 태그(값 포함)로 등록된다. `gateway-service/observability.md` 에 `invalid_signature` 정의 한 줄.
- [ ] AC-2: 분기별 단위 테스트 — gateway 4값 · admin 3값 · account lag(행 없음 0 / 있음 > 0) · security 2값(+ 409 → failure, 재시도는 한 번만 셈). 해당 서비스 `test` 태스크 초록.
- [ ] AC-3: 해당 4 서비스의 기존 테스트(unit · slice)가 초록. Testcontainers 통합은 CI 에서.
- [ ] AC-4: PR CI 의 iam Build & Test · Integration (iam A/B) 가 **실행되어** success.

# Related Specs

- `projects/iam-platform/specs/services/{gateway,admin,account,security}-service/observability.md`
- `platform/observability.md`

# Related Contracts

- 없음(HTTP · 이벤트 계약 불변).

# Edge Cases

- gateway: 토큰이 있지만 `Bearer` 접두가 아님 → `invalid_signature`(형식 실패). 공개 경로에 토큰이 붙어 와도 검사하지 않으면 세지 않는다.
- admin: DENIED 감사 쓰기가 best-effort 로 실패(`admin.audit.*_deny_failure` 증가)해도 거부 자체는 일어났다 → `denied` 는 센다(거부 응답이 나갔으므로). SUCCESS/FAILURE 는 fail-closed 경로라 기록 실패 = 요청 실패 → 세지 않는다.
- account: outbox 테이블 조회가 실패하면 auth 의 형제와 같은 값(NaN 또는 마지막 값)을 따른다.

# Failure Scenarios

- 한 요청이 두 번 세짐(필터 + 검증기 양쪽에서) → 알림 임계값이 반으로 깎인 효과. AC-2 의 «정확히 한 번» 단언.
- `action_code` 태그 카디널리티 — `ActionCode` 는 enum 이라 유한. 문자열 자유값이 들어가지 않게.

# Test Requirements

- 단위 테스트(`SimpleMeterRegistry`) · 서비스별 `test` · CI.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
