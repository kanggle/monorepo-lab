# Task ID

TASK-BE-599

# Status

ready — 🔴 **AC-0 은 소유자 결정**(무엇을 폼 로그인 경로에 올릴지). 결정 전에는 AC-1(계약 문서 정정)만 착수 가능.

# Title

브라우저 폼 로그인이 `auth.login.*` 을 하나도 내지 않는다 — security-service 의 VELOCITY · DEVICE_CHANGE · GEO_ANOMALY 가 입력을 못 받는다

# Owner

iam-platform

# Task Tags

- auth-service
- security-service
- event
- contract-drift

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 이벤트 발행 지점·실패 시 계정 식별·rate-limit 동작 변경이 걸린 설계. AC-1(문서 정정)만은 Sonnet 으로 충분.

---

# Goal

`TASK-BE-398` 이 JSON `POST /api/auth/login` 을 걷어낸 뒤, 사람이 비밀번호로 로그인하는 **유일한** 경로는 SAS 폼 로그인
(`CredentialAuthenticationProvider`)이다. 그 경로는 로그인 이벤트를 **하나도** 내지 않는다. 그래서 security-service 의
로그인 기반 탐지 규칙 셋이 데모든 운영이든 **입력을 받을 길이 없다**. 계약 문서는 그 이벤트들을 살아 있는 것으로 적고 있다.

이 티켓은 ① 계약 문서를 **지금 사실대로** 고치고, ② 소유자가 정한 범위만큼 폼 로그인 경로에 로그인 이벤트를 되살린다.

## 실측 (2026-09-25 UTC · 저장소만 · 창 없음)

| 사실 | 근거 |
|---|---|
| 폼 로그인 provider 는 이벤트·rate-limit·디바이스 세션을 **의도적으로** 안 한다 | `auth-service/…/infrastructure/security/CredentialAuthenticationProvider.java:29-47` 클래스 주석 — *"does **not** reuse the full `LoginUseCase` … publishes login-attempt/success/failure events … out of scope for the v1 form-login surface"* · 성공/실패 모두 `AuthEventPublisher` 호출 0 |
| 그 공백은 «별 task» 로 미뤄졌고 그 task 는 없다 | `tasks/done/TASK-BE-309-…:94` *"추후 enhancement 로 publish 가능 (별 task)"* · `:177` Out of Scope. 🔴 iam `tasks/**` 전 큐에서 `auth\.login`·`LoginSucceeded`·`CredentialAuthenticationProvider`·`AuthenticationSuccessHandler`·`VELOCITY`·`DEVICE_CHANGE`·`GEO` 검색 → 활성 큐 0건(전부 `done/` 의 옛 기록) — «이 검색으로는 못 찾음» |
| 발행기는 멀쩡히 살아 있지만 **부르는 곳이 없다** | `OutboxAuthEventPublisher` 의 `publishLoginAttempted/Failed/Succeeded` · `publishAuthSessionCreated` 를 부르는 유일한 클래스가 `LoginUseCase` 인데, `LoginUseCase` 를 주입하는 곳이 `src/main` 에 **0** 이다(클래스 정의와 주석 참조만) |
| 셋 다 로그인 이벤트에만 반응한다 | `VelocityRule.java:42` `isLoginFailed() && hasAccount()` · `DeviceChangeRule.java:37` `isLoginSucceeded() && hasAccount()` · `GeoAnomalyRule.java:50` 동일(+`ipMasked` 로 geo 조회) |
| 계약은 살아 있다고 적는다 | `specs/contracts/events/auth-events.md:29/58/90` 의 `auth.login.attempted/failed/succeeded` 에 *Consumers: security-service (VelocityRule …)* — 발행자가 없다는 단서가 없다. 🔵 대조: 같은 파일 `:270` `auth.session.revoked` 는 *"현재 없음 — 미구현 … 계약이 없는 소비를 있는 것처럼 선언하지 않는다"* 로 정직하게 적혀 있다. `auth.session.created`(`:207`)도 발행자가 `LoginUseCase` 뿐이다 |
| 폼 로그인에 커스텀 핸들러가 없다 | `WebLoginSecurityConfig.java:136-140` *"No custom successHandler"* · `failureUrl("/login?error")` · `AuthenticationEventPublisher`/`ApplicationListener<AuthenticationSuccessEvent>` 0 |
| 살아 있는 보안 이벤트는 토큰 쪽뿐 | `RefreshTokenUseCase.java:91,166,216,235` 가 `auth.token.refreshed/reuse.detected`·`auth.session.revoked`·`auth.token.tenant.mismatch` 를 낸다(SAS `refresh_token` grant 경유). 🔵 2026-09-25 데모 창이 `auth.token.reuse.detected` → 자동 잠금 경로를 결과 상태로 PASS 확인(`TASK-MONO-672` 항목 15 ②) — **잠그는 쪽은 돈다, 로그인 쪽 입력이 없다** |

🔴 소셜 로그인(`OAuthLoginUseCase`)도 `AuthEventPublisher` 를 부르지 않는다 — 같은 공백의 둘째 입구다.

# Scope

## 포함

- **AC-1 (결정 불요)** — `auth-events.md` 의 `auth.login.attempted/failed/succeeded` · `auth.session.created` 에 «현재 발행자 없음 (BE-398 이후)»
  단서를 `auth.session.revoked` 와 같은 결로 단다. 🔴 규칙 설명은 지우지 않는다 — 소비자는 살아 있고, 비어 있는 것은 입력이다.
- **AC-2~ (소유자 결정 후)** — AC-0 에서 고른 범위를 폼 로그인(+소셜 로그인, 결정에 따라) 경로에 올린다.

## 제외

- security-service 규칙 자체의 변경(임계·점수) — 입력이 오기 전엔 조정 근거가 없다.
- `LoginUseCase` 삭제 — 되살리는 재료일 수 있다. 결정 뒤 별도 판단.

# Acceptance Criteria

- [ ] **AC-0 — 🔴 소유자 결정 (HARDSTOP-09 성격: 스펙에 없는 설계 선택)**. 셋은 서로 독립이다:
  - **ⓐ 이벤트만** — `auth.login.attempted/failed/succeeded` (+ `auth.session.created`?) 발행. 사용자 체감 동작 변화 **없음**.
  - **ⓑ rate-limit 도** — `auth.login.max-failure-count:5`·창 900초를 폼 로그인에 적용. 🔴 **사용자 동작이 바뀐다**(5회 실패 뒤 로그인 폼이 막힘) — 데모 방문자·촬영 계정이 잠길 수 있다.
  - **ⓒ 디바이스 세션도** — `DeviceChangeRule` 의 1차 신호(`isNewDevice`)가 여기서 나온다. 없으면 규칙은 `deviceFingerprint` 폴백으로만 돈다.
  - 구현 모양: ⓘ provider/핸들러가 발행기를 직접 부른다 · ⓘⓘ `LoginUseCase` 의 부수효과를 공용 헬퍼로 뽑아 둘이 공유(BE-309 `:177` 이 적은 원안) · ⓘⓘⓘ Spring `AuthenticationEventPublisher` 리스너. 🔵 권장은 결정 시점에 이 표를 다시 재고 내라.
- [ ] **AC-1** — 계약 문서 정정(위 포함 절). 🔴 결정 전에도 한다 — 문서가 거짓인 기간을 늘리지 않는다.
- [ ] **AC-2** — 🔴 **실패 이벤트의 계정 식별**: `VelocityRule` 은 `accountId` 가 있는 실패만 센다. 비밀번호가 틀린 경우 provider 는 자격을 이미 찾았으므로 `accountId` 를 채울 수 있다 — **채워야 한다**. 없는 이메일은 `accountId=null` 이 정상(계정 열거 방지와 충돌하지 않게 — 응답은 여전히 같은 `/login?error`).
- [ ] **AC-3** — 🔴 **결과로 판정**: 데모(또는 iam e2e compose)에서 ① 폼 로그인 성공 1회 → security-service `login_history` 에 `SUCCESS` 행 ② 같은 계정 비밀번호 오류 → `VelocityRule` 카운터 증가(Redis 키 또는 `suspicious_events`). 로그 침묵·단위 테스트 초록은 판정이 아니다(`TASK-MONO-717`·`718` 의 교훈).
- [ ] **AC-4** — 🔴 `ipMasked` 가 실제 클라이언트 IP 에서 나오는가 — 데모는 Traefik 뒤다. `X-Forwarded-For` 를 안 읽으면 GEO 규칙은 모든 로그인을 **프록시 IP 하나**로 본다(입력은 오는데 무의미). 판정 = 두 다른 출발지의 로그인이 서로 다른 `ipMasked` 를 남기는가.

# Related Specs

- `specs/contracts/events/auth-events.md` (계약 — AC-1 대상)
- `specs/contracts/http/auth-api.md` § TASK-BE-398 removal record
- `tasks/done/TASK-BE-309-auth-service-form-login-html-surface.md` (`:94` · `:177` — 이 공백의 출처)
- `tasks/done/TASK-BE-398-legacy-custom-jwt-flow-sunset-removal.md`
- `TASK-MONO-672` 항목 15 (2026-09-25 — 잠금 경로는 돈다)

# Related Contracts

- `auth.login.attempted` · `auth.login.failed` · `auth.login.succeeded` · `auth.session.created` — **스키마는 바꾸지 않는다**. 발행자를 되살리거나, 없다고 적는다.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 없는 이메일로 로그인 실패 | `auth.login.failed` 에 `accountId=null` — VELOCITY 는 무시(현행 규칙 그대로). 응답은 존재 여부를 드러내지 않는다 |
| 크로스 테넌트 폴백으로 자격을 찾은 경우(provider `:131-199`) | 이벤트의 `tenantId` 는 **실제 계정의 테넌트** — 틀리면 security-service 가 다른 테넌트 키로 센다(BE-259 per-tenant 카운터) |
| outbox 쓰기 실패 | 로그인 자체를 실패시키지 않는다(텔레메트리) — 단, 그 결정은 AC-0 에 명시하고 테스트로 박는다 |
| ⓑ 를 고르면 데모 계정이 잠긴다 | 데모 시드 계정은 방문자가 공유한다 — 잠금 해제 경로(시간 창)를 AC-0 결정문에 함께 적는다 |

# Failure Scenarios

1. **이벤트를 내는데 `accountId` 가 비어 있다** → 규칙이 전부 무시하고 «이벤트는 흐른다» 는 초록만 남는다(AC-2).
2. **단위 테스트로 닫는다** → 발행 호출은 증명돼도 규칙이 반응하는지는 모른다(AC-3 은 결과 상태).
3. **프록시 IP 로 발행한다** → GEO 입력은 오지만 모든 로그인이 같은 곳에서 온 것으로 보인다(AC-4).
4. **AC-1 을 구현과 묶어 미룬다** → 결정이 늦어지는 만큼 계약이 거짓인 기간이 늘어난다.
