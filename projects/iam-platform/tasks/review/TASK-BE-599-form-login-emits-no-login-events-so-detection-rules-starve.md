# Task ID

TASK-BE-599

# Status

review — AC-0(**재결정 반영: ⓐ 만 + 데모 VELOCITY 완화**) · AC-1 · AC-2 완료(2026-09-25 UTC). 🔴 **AC-3 · AC-4 는 창(라이브 스택) 항목으로 열려 있다** — 아래 § 창 런북. 단위 테스트 초록은 AC-3 의 판정이 아니다.
🔵 2026-09-25 2차 커밋: 아래 1차 기록의 «머지 전 확인 2건» 은 소유자가 **결정했다** — AC-0 § 결정 변경 이력 과 § 재결정 반영 참조. 그 아래 1차 서술 중 디바이스 세션에 관한 부분은 **대체됨**(기록으로 보존).

> (이력, 1차 커밋) 🔴🔴 **머지 전에 소유자가 읽어야 할 것 두 개** — § 구현 기록의 «ⓐ 는 사용자 쪽 결과를 바꾼다» 와 «ⓒ 는 매 로그인을 새 디바이스로 만든다». 티켓 기안 시 ⓐ 를 «사용자 체감 동작 변화 없음» 이라 적었는데 **틀렸다**.

> (이력) ready — AC-0 은 소유자 결정(무엇을 폼 로그인 경로에 올릴지). 결정 전에는 AC-1(계약 문서 정정)만 착수 가능.

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

- [x] **AC-0 — 🔴 소유자 결정 (HARDSTOP-09 성격: 스펙에 없는 설계 선택)** — ✅ **결정됨 2026-09-25 (소유자)**. 원문:
  > *scope = ⓐ login events (auth.login.attempted / failed / succeeded) + ⓒ device session (so auth.session.created and isNewDevice flow). ⓑ rate-limit is NOT included — form login must NOT start blocking users after N failures (shared demo accounts).*
  - **ⓑ 는 명시적으로 기각됐다.** 이유 = 데모 시드 계정은 방문자가 공유한다 — 누군가 비밀번호를 5번 틀리면 다음 방문자가 15분 동안 로그인하지 못한다. 그래서 Edge Case 표의 «ⓑ 를 고르면 잠금 해제 경로를 결정문에 적는다» 는 해당 없음. 코드 결과: 폼 로그인 경로에 `LoginAttemptCounter` 가 없고, `auth.login.failed.failCount` 는 항상 `0`, `failureReason=RATE_LIMITED` 는 이 경로에서 나오지 않는다.
  - **outbox 실패 처리**(Edge Case 표가 «AC-0 에 명시하라» 고 한 것): 이벤트 쓰기(1차 커밋에서는 디바이스 세션 등록도 — 재결정으로 제거됨)는 **텔레메트리**다 — 실패해도 로그인 결과를 바꾸지 않는다(맞는 비밀번호는 성공, 틀린 비밀번호는 여전히 `/login?error`). 테스트로 박았다(`succeededTelemetryFailure_loginStillSucceeds` · `failedTelemetryFailure_stillBadCredentials`).
  - 🔵 **결정 변경 이력 (덧붙임 — 위 원문은 그 시점의 결정으로 보존):**
    1. **1차 결정 (2026-09-25)** — ⓐ + ⓒ, ⓑ 기각 (위 원문). 1차 커밋 `997700722` 가 그대로 구현했다.
    2. **구현 중 발견 2건** (§ 구현 기록 «소유자가 머지 전에 알아야 할 것»):
       ① ⓐ 가 VelocityRule(1시간 10회 → 점수 80 = AUTO_LOCK)에 입력을 주면서 **공유 데모 계정이 방문자 실수로 LOCKED** 될 수 있다 — ⓑ 를 기각한 것과 같은 종류의 결과.
       ② ⓒ 는 브라우저 폼에 기기 fingerprint 가 없어 D3 에 따라 **매 로그인이 새 기기**(`isNewDevice=true` 상시 → DEVICE_CHANGE 상시 발화, 세션 행 누적).
    3. **소유자 재결정 (2026-09-25)**:
       - **VELOCITY — «데모만 완화 + 결함 티켓»**: 운영 기본값(`DETECT_VELOCITY_THRESHOLD=10`)은 그대로. 데모에서만 security-service 에 매우 큰 임계를 준다(`infra/demo/iam-traefik.override.yml` security-service environment). nightly/CI 의 `docker-compose.e2e.yml` 에는 넣지 않는다.
       - **«LOCKED 계정도 폼 로그인 통과» 결함 → 별도 티켓으로 분리됨(번호는 기안 후 부여).** 이 티켓은 파일을 만들지 않는다.
       - **ⓒ — «ⓒ 는 빼고 ⓐ 만»**: 폼 로그인 경로에서 디바이스 세션 등록과 `auth.session.created` 발행을 제거. `auth.login.attempted/failed/succeeded` 는 유지하고 succeeded 의 `deviceId`/`isNewDevice` 는 `null`(계약상 optional·«알 수 없음»).
       - **ⓒ 후속 조건 = 안정적인 기기 식별 쿠키가 생길 때.** 그때 fingerprint 대신 그 쿠키를 기기 입력으로 삼아 재검토.
    ⇒ **현재 범위 = ⓐ 만** (+ 데모 VELOCITY 완화). ⓑ 기각 유지.
  - 원래 선택지(보존):
  - **ⓐ 이벤트만** — `auth.login.attempted/failed/succeeded` (+ `auth.session.created`?) 발행. 사용자 체감 동작 변화 **없음**.
  - **ⓑ rate-limit 도** — `auth.login.max-failure-count:5`·창 900초를 폼 로그인에 적용. 🔴 **사용자 동작이 바뀐다**(5회 실패 뒤 로그인 폼이 막힘) — 데모 방문자·촬영 계정이 잠길 수 있다.
  - **ⓒ 디바이스 세션도** — `DeviceChangeRule` 의 1차 신호(`isNewDevice`)가 여기서 나온다. 없으면 규칙은 `deviceFingerprint` 폴백으로만 돈다.
  - 구현 모양: ⓘ provider/핸들러가 발행기를 직접 부른다 · ⓘⓘ `LoginUseCase` 의 부수효과를 공용 헬퍼로 뽑아 둘이 공유(BE-309 `:177` 이 적은 원안) · ⓘⓘⓘ Spring `AuthenticationEventPublisher` 리스너. 🔵 권장은 결정 시점에 이 표를 다시 재고 내라.
- [x] **AC-1** — 계약 문서 정정(위 포함 절). 🔴 결정 전에도 한다 — 문서가 거짓인 기간을 늘리지 않는다.
  - ✅ 구현과 **같은 PR** 에서 했으므로 «발행자 없음» 단서 대신 **이 PR 이후의 사실**을 적었다: `auth-events.md` 에 § «로그인 이벤트의 발행 경로 (TASK-BE-599)» 신설 — 발행자 = `POST /login` → `CredentialAuthenticationProvider` → `LoginEventRecorder`, 필드별 채움 규칙(`accountId`·`tenantId`·`failureReason`·`failCount=0`·`sessionJti=null`·`isNewDevice`·`ipMasked`), rate-limit 미적용(ⓑ 기각), 소셜 로그인은 **아직 발행하지 않음**. 스키마는 한 글자도 안 바꿨다. BE-398 ~ 이 PR 머지 전 기간에는 발행자가 없었다는 사실도 남겼다.
  - 🔴 **덤으로 찾은 거짓 하나**: `auth.session.created` 의 *Consumers: security-service (DeviceChangeRule 입력 …)* — security-service 에 그 토픽의 `@KafkaListener` 가 **없다**(리스너는 `auth.login.attempted/failed/succeeded` · `auth.token.refreshed/reuse.detected` 뿐, `session.created` grep 0). `auth.session.revoked` 와 같은 결로 «현재 없음» 으로 고쳤다. DeviceChangeRule 은 `auth.login.succeeded.isNewDevice` 로 판정한다. 토픽 자체는 relay 매핑(`AuthOutboxPublisher:108`)과 e2e `kafka-init` 목록에 있다 — 발행은 되고 아무도 안 읽는다.
  - 같은 사실을 들고 있던 형제 문서 셋도 갱신: `specs/contracts/http/auth-api.md` § BE-398 제거 기록 · `specs/services/auth-service/architecture.md` § BE-398 노트 · `device-session.md` § D4(폼 로그인 경로의 차이).
  - 🔵 **2차 커밋(재결정 반영)**: 네 문서를 «ⓐ 만» 상태로 고쳤다 — `auth.session.created` 는 이제 **«현재 발행자 없음»**(코드는 호출자 없는 `LoginUseCase` 에만) + 소비자도 없음. succeeded 의 `deviceId`/`isNewDevice` 는 `null` — 계약 `auth.login.succeeded` 필드 노트가 두 필드를 **optional·additive** 로, `null` 을 «알 수 없음(legacy) — 소비자는 fingerprint fallback» 으로 정의하므로 계약과 맞다. device-session.md D4 의 폼 로그인 문단은 «1~3 을 수행하지 않는다 + 재검토 조건» 으로 바꿨다.
- [x] **AC-2** — 🔴 **실패 이벤트의 계정 식별**: `VelocityRule` 은 `accountId` 가 있는 실패만 센다. 비밀번호가 틀린 경우 provider 는 자격을 이미 찾았으므로 `accountId` 를 채울 수 있다 — **채워야 한다**. 없는 이메일은 `accountId=null` 이 정상(계정 열거 방지와 충돌하지 않게 — 응답은 여전히 같은 `/login?error`).
  - ✅ 비밀번호 불일치 → `failed(accountId=<resolved>, tenantId=<계정의 실제 테넌트>, CREDENTIALS_INVALID)`. 크로스-테넌트 폴백으로 찾은 경우도 **계정의** 테넌트(시작 client 의 테넌트 아님) — 테스트 `wrongPassword_crossTenantFallback_usesAccountTenant`. 없는 이메일 → `accountId=null` + 시작 client 테넌트(없으면 `fan-platform`). 테넌트 모호 → `LOGIN_TENANT_AMBIGUOUS` + `accountId=null`. 세 실패 모두 **같은 `BadCredentialsException("Invalid credentials")`** → 같은 `/login?error`.
  - 🔵 **bite 확인**: provider 의 실패 발행을 `accountId=null` + 클라이언트 테넌트로 바꾼 변이 → `CredentialAuthenticationProviderTest` **15 중 2 실패**(해당 두 AC-2 테스트) · 원복 후 15/15.
- [ ] **AC-3** — 🔴 **결과로 판정**: 데모(또는 iam e2e compose)에서 ① 폼 로그인 성공 1회 → security-service `login_history` 에 `SUCCESS` 행 ② 같은 계정 비밀번호 오류 → `VelocityRule` 카운터 증가(Redis 키 또는 `suspicious_events`). 로그 침묵·단위 테스트 초록은 판정이 아니다(`TASK-MONO-717`·`718` 의 교훈).
  - ⏳ **창 항목** — 로컬 Docker 데몬이 내려가 있어 이번에 못 쟀다. 런북 = 아래 § 창 런북 R1.
- [ ] **AC-4** — 🔴 `ipMasked` 가 실제 클라이언트 IP 에서 나오는가 — 데모는 Traefik 뒤다. `X-Forwarded-For` 를 안 읽으면 GEO 규칙은 모든 로그인을 **프록시 IP 하나**로 본다(입력은 오는데 무의미). 판정 = 두 다른 출발지의 로그인이 서로 다른 `ipMasked` 를 남기는가.
  - ⏳ **창 항목** — 런북 = § 창 런북 R2. 저장소만으로 잰 것(판정 아님, 입력):
    - 코드는 `X-Forwarded-For` 를 **직접 읽지 않는다** — 다른 진입점과 같은 `SessionContexts.fromRequest` → `request.getRemoteAddr()`. 전역 forwarding 설정은 **건드리지 않았다**(지시대로).
    - `apps/auth-service/src/main/resources/application.yml` 에는 `server.forward-headers-strategy` 가 **없다** → 오버레이 없이 프록시 뒤에 두면 프록시 주소가 찍힌다.
    - 🔵 그런데 **데모 오버레이 `infra/demo/iam-traefik.override.yml:163` 가 `SERVER_FORWARD_HEADERS_STRATEGY: FRAMEWORK` 를 이미 켠다**(원래 목적은 SAS 리다이렉트 호스트). FRAMEWORK = Spring `ForwardedHeaderFilter`. 그 필터가 `getRemoteAddr()` 를 `X-Forwarded-For` 로 바꾸는지 **spring-web 6.2.1 바이트코드(`javap`)로 확인**했다 — `ForwardedHeaderExtractingRequest` 가 `ForwardedHeaderUtils.parseForwardedFor(...)` 결과를 `remoteAddress` 필드에 담고 `getRemoteAddr()` 를 오버라이드한다. 필터 순서상(HIGHEST_PRECEDENCE → `RequestContextFilter` → 보안 체인) provider 가 `RequestContextHolder` 로 받는 요청은 래핑된 요청이다.
    - ⇒ **예측**: 데모에서는 `ipMasked` 가 Traefik 이 본 클라이언트 주소에서 나온다. 🔴 예측이지 판정이 아니다 — Traefik 이 `X-Forwarded-For` 를 어떻게 채우는지(신뢰하지 않는 입력 XFF 는 버리고 자기가 본 피어로 채움), EC2 앞에 다른 프록시가 있는지는 라이브에서만 보인다. 로컬 Docker Desktop 은 NAT 때문에 모든 로그인이 같은 주소일 수 있다(그러면 «두 출발지» 를 로컬에서 만들 수 없다 — R2 가 두 단계인 이유).
    - 오버레이 없이도 맞게 하려면 필요한 것: auth-service `application.yml` 에 `server.forward-headers-strategy: native`(Tomcat `RemoteIpValve`, 사설 대역 프록시만 신뢰) 또는 `framework`. 🔴 전역 설정이라 소셜 콜백·SAS 리다이렉트 호스트까지 바뀐다 — 이 티켓 범위 밖. 필요해지면 별도 티켓. 🔴 또 FRAMEWORK 는 들어오는 `X-Forwarded-For` 를 **출처와 무관하게** 믿는다 — 데모에서 auth-service 에 Traefik 을 거치지 않고 닿는 것은 `iam-e2e` · `traefik-net` 네트워크 안의 컨테이너뿐이라 위조 면은 네트워크 내부로 한정된다.

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

---

# 구현 기록 (2026-09-25 UTC)

## 🔵 재결정 반영 — 2차 커밋 (2026-09-25 UTC)

소유자 재결정(AC-0 § 결정 변경 이력 3)을 같은 PR 의 새 커밋으로 반영했다. **이 절이 현재 상태다** — 아래 1차 기록 중 디바이스 세션 · `auth.session.created` · «머지 전 확인» 에 관한 서술은 **대체됨**(그 시점의 기록으로 보존).

- **ⓒ 제거** — `LoginEventRecorder` 의 협력자는 `AuthEventPublisher` 하나뿐이다(`RegisterOrUpdateDeviceSessionUseCase` 의존 삭제). `recordSucceeded` 는 `publishLoginSucceeded(accountId, sessionJti=null, tenantId, ctx, deviceId=null, isNewDevice=null)` 한 번만 부르고 `auth.session.created` 는 내지 않는다.
  - 계약 적합성: `auth.login.succeeded` 필드 노트(TASK-BE-025)가 `deviceId`·`isNewDevice` 를 **optional·additive** 로, `isNewDevice=null` 을 «알 수 없음 (legacy) — 소비자는 fingerprint fallback» 으로 정의한다.
  - 소비자 동작: `DeviceChangeRule.evaluate` — `isNewDevice == null` 이면 폴백으로 가고, `fp == null || fp.isBlank()` 면 `DetectionResult.NONE`. 폼 로그인의 `deviceFingerprint` 는 `X-Device-Fingerprint` 헤더 값인데 브라우저 폼이 보내지 않으므로 **`null`** 이다(1차 기록의 `"unknown"` 은 `device_sessions` 컬럼의 sentinel 이고, 이벤트 payload 값이 아니다). ⇒ **발화하지 않는다.** 테스트로 박았다: `DeviceChangeRuleTest.formLoginShape_noDeviceSignal_doesNotFire`(security-service).
  - «폼 로그인은 디바이스 세션을 등록하지 않는다» 단언: `LoginEventRecorderTest.formLogin_doesNotRegisterDeviceSession`(생성자 파라미터가 정확히 `AuthEventPublisher` 하나 — 디바이스 세션 등록이 돌아오면 이 테스트가 깨져 결정을 강제한다) + `recordSucceeded_noDeviceFields_noSessionCreated`(`verifyNoMoreInteractions` — succeeded 외 발행 0).
- **데모 VELOCITY 완화** — `infra/demo/iam-traefik.override.yml` security-service `environment` 에 `DETECT_VELOCITY_THRESHOLD: "1000000"`(주석: ⓑ 기각과 같은 이유 · 공유 계정 DoS · 운영 기본값 불변 · TASK-BE-599). `environment: *iam-oidc` 를 `<<: *iam-oidc` 병합 + 키 추가로 바꿨다(auth-service 가 같은 파일에서 쓰는 모양).
  - 컨테이너 env 로 가는지(Docker 없이): ① `infra/demo/projects.sh:35` 의 iam `-f` 목록에 이 오버레이가 있다 ② PyYAML 로 파일을 파싱하면 `services.security-service.environment` = `{OIDC_ISSUER_URL, OIDC_JWK_SET_URI, OIDC_TOKEN_URI, DETECT_VELOCITY_THRESHOLD: '1000000'}` — 앵커 병합이 살아 있고 키가 들어간다 ③ security-service `application.yml:117` 이 `${DETECT_VELOCITY_THRESHOLD:10}` 로 읽는다 ④ 저장소 전체에서 이 변수를 다시 설정하는 곳은 없다(`docker-compose.e2e.yml` 포함 — nightly/CI 는 운영 기본값 10 그대로). 값의 하한: `DetectionThresholds` 는 `<= 0` 을 기동 시 거부하고 타입은 `int` — 1,000,000 은 범위 안.
  - 🔵 완화는 **VELOCITY 발화만** 막는다: `VelocityRule.evaluate` 는 임계 비교 **전에** `counter.incrementAndGet` 을 하므로 Redis 카운터는 데모에서도 오른다 — 런북 R1 술어 2 는 그대로 유효하다.
- **«LOCKED 계정도 폼 로그인 통과»(발견 ③) → 별도 티켓으로 분리됨(번호는 기안 후 부여).**
- ⓒ 후속 조건 = **안정적인 기기 식별 쿠키가 생길 때.**

## 모양 — 어디에 붙였고 왜

AC-0 의 세 후보(ⓘ provider 직접 · ⓘⓘ 공용 헬퍼 · ⓘⓘⓘ `AuthenticationEventPublisher` 리스너)를 결정 시점에 다시 쟀다. **ⓘ + ⓘⓘ 의 합**을 택했다 — 부수효과는 공용 application 빈(`LoginEventRecorder`)에, 호출은 provider 에서.

| 후보 | 판정 | 이유 |
|---|---|---|
| Spring 성공/실패 핸들러 | ✗ | 실패 핸들러는 `AuthenticationException` 만 본다 — **비밀번호 불일치의 `accountId` 를 모른다**(AC-2 가 금지하는 모양). 알게 하려면 예외 서브클래스에 계정 id 를 실어 나르는 우회가 필요하고, 그 예외가 `ProviderManager`·`eraseCredentials` 경로를 지나며 살아남는지를 또 증명해야 한다 |
| `AuthenticationEventPublisher` 리스너 | ✗ | 같은 이유(실패 이벤트는 예외만) + 이 체인의 `ProviderManager` 는 `WebLoginSecurityConfig` 가 손으로 만들어 기본이 `NullEventPublisher` — 배선을 새로 깔아야 한다 |
| **provider → `LoginEventRecorder`** | ✓ | 자격 조회 결과(계정 id · **계정의 실제 테넌트** · 실패 사유)를 아는 유일한 자리. 성공 발행은 `tenantType` 해석 등 로그인을 거부할 수 있는 모든 단계 **뒤**에 둬서, 발행 뒤 로그인이 실패하는 경우가 없다 |

- **`LoginEventRecorder`** (`application/`, 신규) — `recordAttempted` · `recordFailed`(`failCount=0`) · `recordSucceeded`(= `RegisterOrUpdateDeviceSessionUseCase` → `auth.login.succeeded(deviceId, isNewDevice)` → 신규 세션이면 `auth.session.created`). 메서드마다 자기 트랜잭션(`@Transactional`) — provider 에는 트랜잭션이 없고, 디바이스 세션 등록이 `Propagation.MANDATORY` 라 여기서 연다.
- **텔레메트리 격리는 호출자(provider)에서** `telemetry(...)` 로 한다. 🔴 recorder 안에서 잡으면 안 된다: 안쪽 `@Transactional` 발행기가 예외를 던지면 트랜잭션이 rollback-only 로 표시되고, 잡아서 삼키면 커밋 시점에 `UnexpectedRollbackException` 으로 **다시 터진다**. 프록시 경계 밖에서 잡아야 롤백이 끝난 뒤 조용해진다.
- **`LoginHashes`** (`application/`, 신규) — `emailHash`(SHA-256(lower(email))[:10]) · `fingerprintHash`. `LoginUseCase.hashEmail/fingerprintHash` 는 이제 여기로 위임한다 — 두 발행자가 같은 계정에 다른 `emailHash` 를 내는 드리프트를 막으려고. 기준값은 JVM 밖(`sha256sum`)에서 재서 테스트에 박았다(`LoginUseCase` 와 비교하면 위임 뒤엔 동어반복이다).
- **`SessionContext`** 는 다른 진입점과 같은 `presentation.SessionContexts.fromRequest` 로 만든다. 🔵 infrastructure → presentation import 가 하나 생겼다 — 복제하면 IP 도출 규칙이 두 벌이 되고 AC-4 의 수정 지점이 갈라지므로 단일 출처를 택했다(이 provider 는 이미 사실상 인바운드 어댑터로 `application.port`·`application.exception` 을 import 한다).
- **`LoginUseCase` 는 남겼다**(삭제 안 함, recorder 로 리팩터도 안 함). 호출자 0 이라 리팩터의 이득이 없고, 13개 단위 테스트가 그 내부 호출 순서를 핀하고 있어 건드리면 리스크만 생긴다. 클래스 주석에 «호출자 없음 · 폼 로그인은 `LoginEventRecorder` · rate-limit 는 의도적으로 안 옮김» 을 적었다. 삭제/접기 판단은 여전히 별도.
- 공백 이메일/비밀번호 제출 → 이벤트 없음(식별자에 대한 로그인 시도가 아니라 형식 오류). `tenantTypePort` 장애(`AuthenticationServiceException`) → `attempted` 는 이미 나갔고 `failed` 는 안 낸다(계약 enum 에 해당 사유가 없고, 사용자 자격 실패가 아니다).
- 계정 상태(LOCKED/DORMANT/DELETED)는 **이 경로가 원래 조회하지 않는다** — 그래서 `ACCOUNT_*` 실패 사유도 안 나온다. 추가하지 않았다(범위 밖, 아래 발견 ③).

## 🔴🔴 소유자가 머지 전에 알아야 할 것

1. **ⓐ 는 사용자 쪽 결과를 바꾼다 — 기안의 «체감 변화 없음» 은 틀렸다.** VelocityRule 기본값은 `DETECT_VELOCITY_THRESHOLD=10` / 창 `3600`초 / 가중치 `80`(`security-service application.yml:117-119`)이고, 임계 도달 점수 80 은 `RiskLevel` 의 AUTO_LOCK 하한(`score >= 80`)이다. ⇒ **한 계정에 1시간 안에 비밀번호 오류 10번 → `security.auto.lock.triggered` → account-service 에서 계정 LOCKED**(이 경로는 `TASK-MONO-672` 항목 15 에서 결과 상태로 돈다고 확인된 그 경로). ⓑ 를 기각한 이유(공유 데모 계정이 N회 실패로 막히면 안 된다)와 **같은 종류의 결과**가, rate-limit 가 아니라 탐지 규칙 쪽에서 온다. 다만 차이: ⓑ 는 15분 자동 해제되는 로그인 차단이고, 이쪽은 계정 **상태**가 바뀐다(해제는 운영자 조치). 🔵 완화 요인: 폼 로그인 provider 는 계정 상태를 **조회하지 않으므로** LOCKED 여도 `POST /login` 자체는 여전히 통과한다(발견 ③ — 그 자체가 별개 결함 후보). 선택지: (a) 그대로 머지 — 탐지는 원래 이러라고 있는 것 (b) 데모 env 에서 `DETECT_VELOCITY_THRESHOLD` 를 올린다 (c) 규칙 변경(이 티켓 «제외» 범위). **결정은 소유자 몫 — 이 PR 은 (a) 상태다.**
2. **ⓒ 는 매 폼 로그인을 «새 디바이스» 로 만든다.** 브라우저 폼은 `X-Device-Fingerprint` 를 보내지 않는다(`templates/login.html` 에 fingerprint 0건) → fingerprint `"unknown"` → `device-session.md` **D3** 가 그 경우 «매 로그인마다 신규 device_id» 를 정한다. 결과: (i) `auth.login.succeeded.isNewDevice=true` 가 **매번** → DeviceChangeRule 이 **매 성공 로그인마다** 점수 50(ALERT) 을 낸다 — 규칙의 1차 신호가 상수가 돼 신호로서의 의미가 없다 (ii) `device_sessions` 에 행이 로그인마다 쌓이고 11번째부터 D4 eviction(`auth.session.revoked`, `EVICTED_BY_LIMIT`)이 돈다 — SAS 가 미러하는 `refresh_tokens` 행은 `device_id=NULL` 이라 **토큰이 끊기지는 않는다** (iii) 사용자 세션 목록 API(`ListSessionsUseCase`)에 이 행들이 보이기 시작한다. 스펙(D3)대로 구현했고 신호를 조작하지 않았다(예: fingerprint 가 없으면 `isNewDevice=null` 로 내는 것은 계약의 «이번 트랜잭션에서 새로 생성됨» 의미를 어긴다). 고치려면 브라우저 fingerprint 수집(로그인 폼 hidden field 등) 또는 규칙 쪽 조정 — 둘 다 이 티켓 밖.

## 발견 (이 티켓 밖 — 기록만)

- ① `auth.session.created` 소비자 부재 — AC-1 에서 계약을 고쳤다. 소비를 구현할지는 별개.
- ② **소셜 로그인 후속** — `SocialLoginBrowserController` / `OAuthLoginUseCase.resolveBrowserLogin` 도 아무 로그인 이벤트를 안 낸다. 이번에 넣지 않은 이유(«공용 컴포넌트가 사소한 배선으로 붙는가» 에 아니오): (a) **테넌트** — 소셜 경로의 테넌트는 시작 client 에서 온 값(`resolution.tenantId()`)이고, BE-507 이전에 태어난 계정은 실제 테넌트가 다를 수 있다 — 폼 경로에서 AC-2 가 금지한 바로 그 불일치라, 어느 값을 낼지 결정이 필요하다 (b) **실패 이벤트** — 소셜 실패(state 불일치 · provider 오류 · 이메일 없음)는 `emailHash` 도 `accountId` 도 없는 경우가 대부분이라 계약의 `emailHash` 필수와 맞지 않는다 (c) `loginMethod=OAUTH_<PROVIDER>` 매핑과 폐기 예정 오버로드 정리. 성공 한 줄만 붙이는 것은 쉽지만 (a) 를 결정 없이 고르면 이 티켓이 막으려던 결함을 소셜 쪽에 새로 만든다. ⇒ **후속 티켓 필요**(이 티켓은 파일을 만들지 않는다 — 지시대로). 계약 문서에는 «소셜은 아직 발행하지 않음» 으로 적어 뒀다.
- ③ **폼 로그인은 계정 상태를 안 본다** — LOCKED/DORMANT/DELETED 계정도 `POST /login` 을 통과한다(provider 에 `getAccountStatus` 호출 0, auth-service 에 `account.locked` 소비자 0). 그래서 위 ①번 자동 잠금도 **폼 로그인을 막지는 못한다**. `LoginUseCase` 는 확인했었다(`checkAccountStatus`). 별개 결함 후보 — 이 티켓 범위 밖. → **별도 티켓으로 분리됨(번호는 기안 후 부여)** — 소유자 결정 2026-09-25.
- ④ AC-4 의 X-Forwarded-For — 위 AC-4 하위 항목.

## 테스트 (로컬, 2026-09-25)

- `./gradlew :projects:iam-platform:apps:auth-service:test --console=plain > log 2>&1` → **`rc=0`**, 결과 XML 합계 **101 스위트 · 745 테스트 · 실패 0 · 에러 0 · 스킵 28**. (이 `test` 태스크는 `@Tag("integration")` 을 제외한다.)
  - `CredentialAuthenticationProviderTest` **15/15**(기존 4 + BE-599 11: 성공 · 비밀번호 불일치 accountId · 크로스-테넌트 실패/성공이 계정 테넌트 · 없는 이메일 null+같은 응답 · client 테넌트 · 모호 · 성공 텔레메트리 실패에도 로그인 성공 · 실패 텔레메트리 실패에도 BadCredentials · 공백 입력 무이벤트 · IP=remoteAddr)
  - `LoginEventRecorderTest` **6/6**(attempted 통과 · failCount 0 · 신규 디바이스 순서+session.created · 기존 디바이스 session.created 없음 · 발행 실패 전파 · 해시 기준값)
  - `LoginUseCaseTest` **13/13**(해시 위임 후 무변화)
- bite: AC-2 변이 → 15 중 **2 실패**, 원복 → 15/15 (위 AC-2).
- 🔴 **Testcontainers 통합 테스트는 로컬에서 안 돌았다**(Docker 데몬 다운) — `FormLoginIntegrationTest` 등은 이제 폼 로그인마다 `device_sessions`·`auth_outbox` 에 행을 쓴다. **CI `Integration (iam …)` 에서 판정.**
- 🔵 **2차 커밋 재실행 (2026-09-25)** — 결과 디렉터리를 지우고 다시 돌렸다:
  - `./gradlew :projects:iam-platform:apps:auth-service:test --console=plain > log 2>&1` → **`rc=0`**, **101 스위트 · 745 테스트 · 실패 0 · 에러 0 · 스킵 28**. `LoginEventRecorderTest` **6/6**(attempted · failCount 0 · succeeded 는 deviceId/isNewDevice/sessionJti null + 다른 발행 0 · 생성자에 디바이스 세션 협력자 없음 · 발행 실패 전파 · 해시 기준값) · `CredentialAuthenticationProviderTest` **15/15** · `LoginUseCaseTest` **13/13**.
  - `./gradlew :projects:iam-platform:apps:security-service:test --tests '…DeviceChangeRuleTest'` → **`rc=0`**, **9/9**(신규 `formLoginShape_noDeviceSignal_doesNotFire` 포함).
  - IT: 폼 로그인은 이제 `auth_outbox` 에만 쓰고 `device_sessions` 에는 쓰지 않는다 — 여전히 **CI 판정**.

# 창 런북 (AC-3 · AC-4)

🔴 **0단계 — 잰 것이 이 코드인가.** 데모 백엔드는 구워진 이미지에서 돈다 — 이 PR 이 머지된 뒤 **재굽기/재빌드 전에는 데모에 없다**. 판정 전에 `docker inspect -f '{{.Created}}' <auth-service 컨테이너의 이미지>` 가 이 PR 머지 시각보다 **뒤**인지 대조하라. 로컬이면 이 브랜치에서 `docker compose -f projects/iam-platform/docker-compose.e2e.yml build auth-service` 후 `up -d`.
🔴 **공유 데모 계정에서 비밀번호 오류는 2회 이하로** — 10회/1시간이면 VelocityRule 이 계정을 자동 잠근다(§ 소유자 1). 🔵 (2차) 데모 오버레이는 이제 임계를 1,000,000 으로 둬서 데모에서는 잠기지 않는다 — 단 **로컬 e2e compose 만으로 띄우면 운영 기본값 10** 이다. 어느 쪽인지 `docker inspect <security-service 컨테이너> --format '{{range .Config.Env}}{{println .}}{{end}}'` 에서 `DETECT_VELOCITY_THRESHOLD` 로 확인하라(데모 오버레이 적용 여부의 판정이기도 하다).

## R1 — AC-3 (결과 상태)

로컬 iam e2e compose 기준(auth-service `localhost:18081`, 데모면 `http://iam.<DEMO_DOMAIN>`). `<EMAIL>`/`<PASSWORD>` = 존재하는 자격(시드 계정 또는 `/signup` 으로 만든 계정) — 🔴 자리표시자는 실제 값으로 바꿔서 실행.

```bash
B=http://localhost:18081; J=$(mktemp)
# 1) CSRF 토큰 + 세션 쿠키
T=$(curl -s -c "$J" "$B/login" | sed -n 's/.*name="_csrf" value="\([^"]*\)".*/\1/p'); echo "csrf=${T:0:8}…"
# 2) 성공 로그인 — 기대: 302 Location 이 /login?error 가 아님
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' -b "$J" -c "$J" \
  --data-urlencode "username=<EMAIL>" --data-urlencode "password=<PASSWORD>" --data-urlencode "_csrf=$T" "$B/login"
# 3) 같은 계정, 틀린 비밀번호 1회 — 기대: 302 → /login?error (새 CSRF 로)
J2=$(mktemp); T2=$(curl -s -c "$J2" "$B/login" | sed -n 's/.*name="_csrf" value="\([^"]*\)".*/\1/p')
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' -b "$J2" -c "$J2" \
  --data-urlencode "username=<EMAIL>" --data-urlencode "password=wrong-$RANDOM" --data-urlencode "_csrf=$T2" "$B/login"
```
(CSRF 필드명이 `_csrf` 가 아니면 `templates/login.html:64` 의 `${_csrf.parameterName}` 렌더 결과를 보고 맞춘다.)

읽을 것 (relay 지연을 감안해 몇 초 뒤):
- auth DB: `SELECT event_type, JSON_EXTRACT(payload,'$.payload.accountId'), JSON_EXTRACT(payload,'$.payload.tenantId'), created_at FROM auth_db.auth_outbox ORDER BY created_at DESC LIMIT 6;` — 기대 `auth.login.attempted`(×2) · `auth.login.succeeded` · `auth.login.failed`, **failed 의 accountId 가 NULL 이 아님**. (2차: `auth.session.created` 는 **나오지 않아야** 한다 — 나오면 ⓒ 제거가 배포 이미지에 없다는 뜻.) (컬럼명이 다르면 `DESCRIBE auth_db.auth_outbox` 로 맞춘다 — 판정 대상은 행의 존재와 accountId.)
- security DB: `SELECT outcome, account_id, tenant_id, ip_masked, occurred_at FROM security_db.login_history WHERE account_id='<ACCOUNT_ID>' ORDER BY occurred_at DESC LIMIT 5;`
- Redis: `redis-cli --scan --pattern 'security:velocity:*:<ACCOUNT_ID>:*'` 후 `GET` — 키 형식 `security:velocity:{tenantId}:{accountId}:{windowSeconds}`.

**판정 술어** (셋 다 참이어야 PASS):
1. `login_history` 에 이 계정의 `outcome='SUCCESS'` 행이 **2단계 이후 시각**으로 1건 이상.
2. `login_history` 에 이 계정의 `outcome='FAILURE'` 행 + velocity 키 값이 3단계 전보다 **1 증가**(3단계 전 값을 먼저 읽어 둬라 — 이미 있던 키를 «증가» 로 오독하지 않게). 데모 완화(임계 1,000,000)와 무관하게 성립한다 — `VelocityRule` 은 임계 비교 전에 카운터를 올린다.
3. 🔵 **대조군**: 없는 이메일로 틀린 로그인 1회 → `auth_outbox` 에 `auth.login.failed`(accountId NULL) 가 **생기고**, velocity 키는 **안 생긴다**(VelocityRule 은 accountId 없는 실패를 무시) — 술어 2 가 «아무 실패나 센다» 가 아님을 확인.

## R2 — AC-4 (ipMasked 가 클라이언트에서 오는가)

두 단계. 1단계는 기전, 2단계가 판정이다.
1. **기전 (로컬 가능)** — 오버레이가 켜는 설정이 실제로 켜졌는가: `docker inspect <auth-service 컨테이너> --format '{{range .Config.Env}}{{println .}}{{end}}' | grep FORWARD` → `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK` 기대. 켜졌다면 네트워크 **안에서** XFF 를 실어 보내 본다: `docker exec <같은 네트워크의 curl 가능한 컨테이너> …` 로 R1 의 성공 로그인을 `-H 'X-Forwarded-For: 198.51.100.9'` 와 함께 → `login_history.ip_masked` 가 `198.51.*.*` 면 필터가 동작한다. 🔴 이것은 «헤더를 읽는다» 의 증명이지 «Traefik 이 맞는 값을 넣는다» 의 증명이 아니다.
2. **판정 (라이브)** — 데모 공개 주소로 **서로 다른 두 네트워크**(예: 유선 + 휴대폰 핫스팟)에서 각각 성공 로그인 1회 → `SELECT ip_masked, occurred_at FROM security_db.login_history WHERE account_id='<ACCOUNT_ID>' AND outcome='SUCCESS' ORDER BY occurred_at DESC LIMIT 2;` **PASS = 두 행의 `ip_masked` 가 서로 다르고, 둘 다 Docker/사설 대역(`172.16-31.*`, `10.*`, `192.168.*`)이 아니다.** 둘 다 같은 사설 주소면 FAIL(프록시 주소가 찍힌다) — 그때 원인 후보는 오버레이 미적용 · Traefik 앞의 또 다른 프록시 · Traefik `forwardedHeaders` 설정.

---

## CORRECTION (2026-09-25 UTC)

🔴 **위 «값의 하한: `DetectionThresholds` 는 `<= 0` 을 기동 시 거부하고 타입은 `int` — 1,000,000 은 범위 안» 은 거짓이었다.**
`security-service/…/infrastructure/config/DetectionProperties.java:64` 가 `velocity.threshold` 에 `@Min(1) @Max(10_000)` 을 건다.
데모 오버레이의 `DETECT_VELOCITY_THRESHOLD: "1000000"` 은 상한을 넘어 **security-service 가 기동에 실패**했다 — 15차 AMI
(`ami-004f04b67daf40b89`, 2026-09-25) 첫 부팅에서 `APPLICATION FAILED TO START … Property: security.detection.velocity.threshold
… must be less than or equal to 10000` 재시작 루프(restarts=14)를 실측.

- **지금 사실**: 오버레이 값은 **`"10000"`**(허용 상한 — 1시간에 1만 번이라 데모에선 사실상 미발화). 저장소 수정은 같은 날
  `fix(demo)` PR, 라이브 인스턴스(`i-09f10c696375ba99b`)의 클론은 SSM 으로 같은 값으로 고쳐 security-service 재생성 →
  **running · healthy · restarts=0** 확인.
- 위 런북·본문의 «1,000,000» 은 모두 **10000** 으로 읽어라(판정 술어는 불변 — `VelocityRule` 은 임계 비교 전에 카운터를 올린다).
- 🔵 왜 못 잡았나: 값 검증을 «타입 범위» 로 추론하고 바인딩 제약(`@Max`)을 읽지 않았다. 데모 오버레이 값은 단위 테스트·CI(e2e compose 는
  이 오버레이를 안 쓴다)가 **어디서도 기동시켜 보지 않는다** — 첫 부팅이 첫 판정이었다.

## CORRECTION (2026-09-26 UTC) — 창 판정: AC-3 🟢 PASS · AC-4 🟡 기전 PASS / 라이브 ⚪

15차 AMI(`46aa3191`, VELOCITY=10000 고친 클론) 데모. 상세 = `TASK-MONO-672` § 2026-09-26 창 수확 § 로그인 판정.

- **AC-3 R1.1** — 일회용 A·B 의 폼 로그인 → `security_db.login_history` 에 `ATTEMPTED` → **`SUCCESS`** (tenant `fan-platform`).
- **AC-3 R1.2** — B 틀린 비밀번호 → `/login?error` · `FAILURE` 행 · `security:velocity:fan-platform:<B>:3600` **없음 → 1**(이전 값을 먼저 읽었다).
- **AC-3 R1.3 대조군** — 없는 이메일 틀린 로그인 → velocity 키 총수 **1 → 1**(계정 없는 실패는 안 센다). 🔴 `auth_outbox` 의 `auth.login.failed` 행은
  **확인 못 했다** — auth `outbox` 에 `auth.*` 행이 하나도 안 보였다(발행 뒤 삭제로 보임). 술어 2 의 판별은 velocity 키로 성립한다.
- **AC-4 R2.1 기전** — `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK` · XFF `198.51.100.9` 로그인 → `ip_masked=198.51.*.*`(헤더 없는 로그인 = `172.19.*.*`). 🟢
- **AC-4 R2.2 라이브** — ⚪ 미실행: 서로 다른 두 네트워크에서 공개 주소로 로그인해야 한다(소유자 기기). **AC-4 는 열려 있다.**

⇒ **AC-3 닫힘 · AC-4 열림.**

## CORRECTION (2026-09-26 UTC) — AC-4 라이브 판정: 🟢 PASS

16차 AMI(`ami-0134ac19b5c15ef0d`, RepoCommit `58d4920c4`) 데모 · 인스턴스 `i-0917a5e39bd75f1d3`. 소유자가 서로 다른 두 네트워크에서 `console.hubwang.com` 폼 로그인(`demo@demo.com`, iam 자격 `…ad03`).
판정 = security `login_history` 의 `ip_masked`:

| 시각 (UTC) | 기기 · 네트워크 | `ip_masked` |
|---|---|---|
| 16:05:33 | PC Chrome · 가정 회선 | `119.204.*.*` |
| 16:07:37 | 휴대폰 Safari · 이동통신(Wi-Fi 끔) | `118.235.*.*` |

⇒ 서로 다른 공인 대역이 기록됐고 게이트웨이 뒤 내부 주소(`172.*`)가 아니다 — 라이브 경로에서 XFF 전달이 성립한다. **AC-4 닫힘.**
🔴 측정 중 밟은 함정(기록): 첫 휴대폰 로그인(16:02:54, Safari)은 같은 Wi-Fi 라 `119.204.*.*` 였고, Wi-Fi 를 끈 뒤 같은 탭으로 다시 들어가면 **SSO 라 로그인 이벤트가 새로 생기지 않는다** — 시크릿 탭으로 비밀번호를 다시 입력해야 표본이 된다.
