# Task ID

TASK-BE-668

# Title

iam 스펙 필수 metric 중 대시보드가 묻는데 없는 5개(837 D1)를 구현한다 — `account_signup_total` · `admin_audit_write_total` · `admin_audit_query_total` · `auth_token_issued_total` · `auth_token_refresh_total` — 그리고 대기 목록에서 지운다

# Status

review

# Owner

backend

# Task Tags

- observability
- account-service
- admin-service
- auth-service

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 (세 서비스 · 세는 지점을 결과별로 고르는 판단 — `TASK-BE-664` 와 같은 결)
> 기안 2026-11-25 UTC. 출처: `TASK-MONO-837` AC-5 «D1 = 구현» → 소유자 «권장안대로 진행».

# Dependency Markers

- 선행: `TASK-MONO-837`(분류) · `TASK-MONO-839`(대기 목록 `projects/iam-platform/infra/grafana/pending-metrics.txt` + 가드: 구현되면 그 항목이 남아 있는 한 **가드가 실패** 한다 — 이 티켓이 항목을 지워야 초록).
- 본보기: `TASK-BE-662`(admin `admin_command_total`) · `TASK-BE-664`(auth `AuthMetricsPort` + `MicrometerAuthMetrics`, 인프라 장애는 `failure` 로 세지 않는 판단).

# Goal

스펙(`specs/services/*/observability.md` «필수 메트릭»)이 요구하고 iam 대시보드 패널이 묻는데 코드가 내보내지 않아 패널이 빈 5개:

| 이름 | 서비스 | 라벨(스펙) | 묻는 패널 |
|---|---|---|---|
| `account_signup_total` | account | `result` (success/duplicate/validation_error) | `account-overview.json` «Signup Rate» |
| `admin_audit_write_total` | admin | `result` (success/failure) | `admin-overview.json` «Audit Write Rate» |
| `admin_audit_query_total` | admin | `query_type` | `admin-overview.json` «Audit Queries» |
| `auth_token_issued_total` | auth | `type` (access/refresh) | `auth-overview.json` «Tokens Issued» |
| `auth_token_refresh_total` | auth | `result` (success/expired/reuse_detected/revoked) | `auth-overview.json` «Refresh Token Rotation» |

# Scope

## In Scope

1. 다섯 metric 을 스펙 이름 · 라벨 그대로 등록하고 실제 사건 지점에서 센다. 서비스마다 기존 관례(port + Micrometer 어댑터 — auth 는 `AuthMetricsPort`, admin 은 `AdminActionAuditor` 주변)를 따른다.
2. **세는 지점 판단을 증거에 적는다**: 각 `result` 값이 코드의 어느 분기인지(파일:줄). 스펙 라벨 값 중 코드에 그 분기가 없는 것은 «0 으로 남는다» 고 적는다(664 의 `rate_limited` 처럼). 인프라 장애를 업무 실패로 세지 않는다(664 판단과 같은 이유 — 알림 오발).
3. `projects/iam-platform/infra/grafana/pending-metrics.txt` 에서 다섯 줄 삭제(남은 6줄은 837 D2 — 그대로).
4. 테스트: 서비스마다 metric 단위 테스트(결과별 increment) + 사건 지점 테스트(그 분기에서 port 가 불린다).

## Out of Scope

- 837 D2 의 다른 iam metric.
- 대시보드 수정(이미 스펙 이름을 묻는다).

# Acceptance Criteria

- [x] AC-1: 다섯 metric 이 실제 `PrometheusMeterRegistry` scrape 에 스펙 이름 · 라벨로 나온다(테스트로 — 이름 변환을 추측하지 않는다).
- [x] AC-2: 결과별 세는 지점이 증거에 파일:줄로 있고, 각 지점에 테스트가 있다.
- [x] AC-3: `check-alert-rule-metrics.sh` rc=0 — 대기 목록 iam 6 · ecommerce 1(또는 669 가 먼저 머지됐으면 0) · `check-metric-name-registry.sh` rc=0.
- [ ] AC-4: account · admin · auth `test` 통과 · 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) · PR CI 실패 0.

# Related Specs

- `specs/services/account-service/observability.md` · `admin-service/observability.md` · `auth-service/observability.md`

# Related Contracts

- 없음.

# Edge Cases

- `auth_token_refresh_total{result="reuse_detected"}` 와 이미 있는 `auth_token_reuse_detected_total` 은 같은 사건 — 둘 다 센다(스펙이 둘 다 요구).
- 토큰 발급이 여러 경로(로그인 · refresh · OAuth · SAS)에서 일어난다 → 발급 지점 하나(토큰 생성 서비스)에서 세면 경로마다 빠짐이 없다. 경로별로 세면 이중 계수 위험.

# Failure Scenarios

- 가입 실패를 예외 처리기에서 세다가 인프라 오류까지 `validation_error` 로 셈 → 664 판단 위반.
- 대기 목록 항목을 안 지움 → 가드 빨강(의도된 동작).

# Test Requirements

- 위 단위 · 지점 테스트 · 실제 scrape 이름 확인 · 서비스 test · 가드 · CI.

# Implementation Evidence (2026-11-25 UTC)

구현은 Opus 백엔드 에이전트에 위임(worktree `task/TASK-BE-668-impl`). 줄 번호는 이 커밋 기준.

- **AC-1** — 다섯 이름 모두 실제 `PrometheusMeterRegistry(PrometheusConfig.DEFAULT).scrape()` 로 확인(이름 변환을 추측하지 않음). 테스트가 관찰한 scrape 줄:
  - `account_signup_total{result="duplicate"} 1.0` · `…{result="success"} 0.0` · `…{result="validation_error"} 0.0` — `MicrometerSignupMetricsTest#prometheusScrapeCarriesTheSpecName`
  - `admin_audit_write_total{result="success"} 1.0` · `…{result="failure"} 0.0` — `AdminActionAuditorTest#auditWrite_prometheus_scrape_carries_the_spec_name`
  - `admin_audit_query_total{query_type="admin"} 1.0` · `…{query_type="all"} 0.0` — `AuditQueryUseCaseTest#queryMetric_prometheusScrapeCarriesTheSpecName`
  - `auth_token_issued_total{type="access"} 1.0` · `…{type="refresh"} 0.0` · `auth_token_refresh_total{result="reuse_detected"} 1.0` (나머지 셋 `0.0`) — `MicrometerAuthMetricsTest#prometheusScrapeCarriesTheSpecNames`(664 의 `auth_login_total` · `auth_token_reuse_detected_total` 도 같은 scrape 로 재확인).
  - 모든 시리즈는 생성 시 0 으로 선등록 → 패널이 no-data 가 아니라 0 을 읽는다. 테스트 classpath 에 `micrometer-registry-prometheus` 를 account · admin `build.gradle` 에 `testImplementation` 으로 추가(auth 는 665 에서 이미 있음 — 런타임은 셋 다 `libs/java-observability` 로 이미 있음).
- **AC-2** — 세는 지점(결과별)과 판단:
  - **`account_signup_total{result}`** — port `application/port/SignupMetricsPort` + 어댑터 `infrastructure/metrics/MicrometerSignupMetrics`(auth 664 와 같은 결).
    - `SignupUseCase.execute` 가 본문(`signUp`)을 감싸 결과를 한 번 센다: `duplicate` = `SignupUseCase.java:56`(본문의 중복 거절 넷 — 풀 공존 거절 · 사전 중복 검사 · auth-service 409 · 동시 INSERT 경합 `DataIntegrityViolationException` — 이 모두 `AccountAlreadyExistsException` 으로 나온다), `validation_error` = `:59`(`PasswordPolicyViolationException` · `IllegalArgumentException` — 둘 다 이 서비스가 `VALIDATION_ERROR` 로 답한다: Email 값 객체의 형식 거절 등), `success` = `:62`.
    - 요청 본문 `@Valid` 거절은 use case 에 닿지 않으므로 `GlobalExceptionHandler.java:250`(`handleValidation`, 거절된 파라미터가 `SignupController` 의 것일 때만 — `MethodArgumentNotValidException.getParameter().getContainingClass()`). `ObjectProvider` 라 어댑터가 없는 슬라이스 컨텍스트도 그대로 뜬다.
    - 🔵 **판단 — 무엇이 «가입 시도» 인가**: 공개 `POST /api/accounts/signup` 만. 알림 `AccountSignupFailureHigh`(duplicate + validation_error 비율 = 봇 가입)가 보는 흐름이 이것이다. 서버 간 계정 생성(소셜 첫 로그인 `SocialSignupUseCase`, 운영자 초대 풀 가입 `ConsumerPoolSignupUseCase`)은 세지 않는다.
    - 🔵 **인프라 장애는 세지 않는다**(664 판단과 같은 이유): tenant 없음/정지, auth-service 불가(`AuthServiceUnavailable`), DB 오류는 세 결과 어디에도 들어가지 않는다 — 테스트 `execute_authServiceUnavailable_propagatesAndSkipsEvent` 가 `assertNone`.
    - ⚪ 세지 않는 것: 깨진 JSON(`HttpMessageNotReadableException` → 400 `VALIDATION_ERROR`) — 공통 핸들러가 어느 엔드포인트인지 알 수 없어 signup 으로 귀속할 수 없다.
  - **`admin_audit_write_total{result}`** — `AdminActionAuditor`(TASK-BE-662 의 `admin_command_total` 과 같은 파사드). `admin_actions` 의 모든 행이 이 파사드를 지난다(두 writer 의 다른 호출자 0 — grep). 감사 원장 쓰기 한 번당 한 번:
    - fail-closed 쓰기 여섯(`recordStart` :137 · `recordCompletion` :152 · `record` :160 · `recordWithPermission` :177 · `recordDenied` :193 · `recordLogin` :257) → `writeFailClosed` :265 — 반환하면 `success` :272, 던지면 `failure` :269 후 재던짐(REQUIRES_NEW 커밋 실패도 프록시 밖인 여기서 보인다).
    - best-effort deny 넷(`recordCrossTenantDenied` :210 · `recordOrgNodeScopeDenied` :222 · `recordTenantPlacementDenied` :236 · `recordRoleGrantForbidden` :248) → `writeBestEffort` :279(기록 여부대로 :287). 이 writer 들은 실패를 삼키므로 `AdminActionDenyWriter` 네 메서드가 `void` → `boolean`(기록됨 `true` :164 · :240 · :318 · :388, 삼킨 실패 `false` :181 · :256 · :334 · :404)을 돌려주게 바꿨다. 삼켜지지 못하고 새어 나온 실패도 `failure` :284.
    - 🔵 **판단**: 이 metric 의 `failure` 는 **설계상 인프라 장애다** — 스펙 알림 `AdminAuditWriteFailing`(A10 fail-closed, «DB 점검, 모든 명령이 차단됨»)이 바로 그것을 본다. 664 의 «인프라 장애를 업무 실패로 세지 않는다» 와 충돌하지 않는다(여기서 업무 실패 축은 없다).
    - `isIdempotencyKeyUsed` 는 읽기라 세지 않는다.
  - **`admin_audit_query_total{query_type}`** — `AuditQueryUseCase.recordMetaAudit` 첫 줄 :249(`countQuery` :273). `query_type` = 운영자가 요청한 `source`(`admin` · `login_history` · `suspicious`, 없으면 `all`) — 스펙에 값 목록이 없어 계약(admin-api.md § GET /api/admin/audit, `AuditController.SOURCES`)의 닫힌 집합을 썼다(카디널리티 4). 테넌트 게이트를 통과해 읽기를 실행한 조회마다 한 번(성공 :233 · 읽기 실패 :220 두 경로 모두 `recordMetaAudit` 를 한 번 지난다), 메타-감사 쓰기 **전에** 센다 — 쓰기가 실패해도 읽기는 일어났다. 게이트 거절은 읽은 것이 없어 세지 않는다(그건 `admin_command_total{outcome=denied}`).
  - **`auth_token_issued_total{type}`** — 🔵 **판단 — 발급 지점**: auth-service 에서 토큰을 **찍어내는** 곳은 둘뿐이고 서로를 부르지 않는다. ① 레거시 `TokenGeneratorPort` 구현 `JwtTokenGenerator`(로그인 `LoginUseCase` · `/api/auth/refresh`) — 두 서명이 모두 성공한 뒤 `JwtTokenGenerator.java:127`(access) · `:128`(refresh). ② SAS 의 `OAuth2TokenGenerator` 빈 — `AuthorizationServerConfig.java:656` 에서 새 `CountingOAuth2TokenGenerator` 로 감쌌다: 위임이 토큰을 돌려줬을 때만 `CountingOAuth2TokenGenerator.java:43`(ACCESS_TOKEN) · `:45`(REFRESH_TOKEN), ID 토큰 · `null`(발급 거절) 은 세지 않는다. 이 빈은 SAS 기본 `authorization_code` · `client_credentials` 프로바이더(빈을 조회), `SasRefreshTokenAuthenticationProvider`, `AssumeTenantAuthenticationProvider`(둘 다 같은 빈을 넘겨받음)가 모두 쓴다 → 경로별로 세지 않으므로 빠짐 · 이중 계수 없음. 소셜/브라우저 로그인은 세션을 만들 뿐이고 토큰은 ②에서 찍힌다.
    - ⚪ 찍은 직후 그 요청이 실패하면(예: SAS 인가 저장 실패) 토큰은 찍혔지만 전달되지 않았다 — «발급» 을 «mint» 로 정의했다.
  - **`auth_token_refresh_total{result}`** — 두 refresh 엔드포인트(664 의 reuse 와 같은 두 곳, 서로 다른 엔드포인트라 이중 계수 없음):
    - 레거시 `RefreshTokenUseCase.execute`: `expired` = :71(JWT 파싱 실패 — 만료된 JWT 는 행보다 먼저 여기서 걸린다) · :85(행 없음) · :144(행 만료) — 이 엔드포인트가 `TOKEN_EXPIRED` 로 답하는 모든 곳. `reuse_detected` = :96(기존 `auth_token_reuse_detected_total` :94 바로 뒤 — **둘 다 센다**). `revoked` = :115(블랙리스트) · :127(invalidate-all 마커 + iat 추출 실패) · :131(iat 가 마커 이전) · :138(행 revoked). `success` = :191(모든 쓰기 뒤, 반환 직전).
    - SAS `SasRefreshTokenAuthenticationProvider.authenticate`: SAS 자체 토큰 상태 — `revoked` :227(`isInvalidated`) · `expired` :229(`isExpired`); 미러 행 — `revoked` :263 · `expired` :269; `reuse_detected` :647(`rejectReplay` 의 REUSE 분기, 기존 `tokenReuseDetected()` :645 바로 뒤); `success` :499(회전 트랜잭션이 커밋된 뒤).
    - ⚪ **0 으로 남거나 세지 않는 것**: 스펙의 네 값 밖의 거절 — 테넌트 불일치(`TOKEN_TENANT_MISMATCH`), 토큰을 소유하지 않은 클라이언트, 유예 창 안의 재제출(클라이언트 경합, 664 가 reuse 로도 세지 않는 것), SAS 에 없는 토큰(`findByToken` null), nbf 전 토큰 — 은 세지 않는다. 그 뒤의 인프라 실패(tenant type 조회 · 토큰 생성 · 회전 쓰기)도 세지 않는다 — 테스트 `refreshInfrastructureFailureNotCounted` · `rotationWriteFails_notCounted`.
    - ⚠️ **알려진 한계**: 레거시 경로의 Redis 장애 — `RedisTokenBlacklist.isBlacklisted` 와 `RedisBulkInvalidationStore.getInvalidatedAt` 은 fail-closed 로 «블랙리스트됨» / «지금 무효화됨» 을 **같은 값으로** 돌려준다 → use case 에서 진짜 폐기와 구별할 수 없어 Redis 장애 동안의 거절은 `revoked` 로 세진다. 포트 계약을 바꾸지 않고는 구별할 수 없어 이 티켓 범위 밖으로 두었다. 이 시리즈를 보는 배포 알림은 없다(대시보드 패널만) — 알림 오발은 없다.
  - 스펙 라벨 값 중 **코드에 분기가 없어 늘 0** 인 것: 없음(다섯 metric 의 모든 스펙 값에 분기가 있다).
- **AC-2 테스트** — 지점마다 단언:
  - auth: `MicrometerAuthMetricsTest` +3(타입 2 · 결과 4 선등록 · 각자 증가 · scrape) · 새 `CountingOAuth2TokenGeneratorTest` 4(access · refresh · ID 토큰 안 셈 · null 안 셈) · `JwtTokenGeneratorTenantClaimTest` +2(한 쌍 = access 1 · refresh 1, fail-closed 거절 = 0) · `RefreshTokenUseCaseTest` 11개 기존 케이스에 결과 단언 + 새 2(행 만료 → expired, 인프라 실패 → 안 셈) · `SasRefreshTokenAuthenticationProviderTest` 9개 기존 케이스에 결과 단언 + 새 4(미러 행 만료 · SAS invalidated · SAS 만료 · 회전 쓰기 실패 → 안 셈).
  - account: 새 `MicrometerSignupMetricsTest` 3 · `SignupUseCaseTest` 8개 기존 케이스에 결과 단언(장애 · 테넌트 거절은 `assertNone`) + 새 2(동시 INSERT 경합 → duplicate, Email 값 객체 거절 → validation_error) · `SignupControllerSliceTest` 6개 케이스에 단언(`@Valid` 거절 둘 → `VALIDATION_ERROR` 한 번, use case 가 세는 결과는 핸들러가 port 를 건드리지 않음 = 이중 계수 없음) · 새 `GlobalExceptionHandlerSignupMetricsTest` 3(signup 의 `@Valid` → 1, 다른 컨트롤러의 `@Valid` → 0, 어댑터 없음 → 응답 불변).
  - admin: `AdminActionAuditorTest` +9(선등록 · fail-closed 여섯 성공 6 · 던짐 → failure · recordStart 실패 · best-effort 기록/삼킨 실패/새어 나온 실패 · scrape) · `AuditQueryUseCaseTest` 기존 5개 케이스에 단언(게이트 거절 → 0, 읽기 실패 · 메타-감사 쓰기 실패 → 1) + 새 3(선등록 · suspicious · scrape).
- **AC-3** — 대기 목록에서 다섯 줄 삭제(남은 6 줄 = 837 D2, 그대로). 스테이지 뒤:
  - `bash scripts/check-alert-rule-metrics.sh` → rc=0: `check-alert-rule-metrics: OK — 55 metric reference(s) in 3 rules file(s), all exported by code, libs, a record rule, or a framework; 20 runbook link(s), all to tracked files; 239 reference(s) in 20 dashboard(s), all exported except 7 listed as pending.`(iam 6 + ecommerce 1)
  - 🔵 bite: 대기 목록만 HEAD 로 되돌려 다시 돌리면 rc=1 — `account_signup_total` · `admin_audit_query_total` · `admin_audit_write_total` · `auth_token_issued_total` · `auth_token_refresh_total` 다섯 모두 `now EXPORTED: delete the entry` → 가드가 다섯 이름을 내보냄으로 본다는 것을 확인하고 원복.
  - `bash scripts/check-metric-name-registry.sh` → rc=0: `check-metric-name-registry: OK — 112 literal metric name(s) across 31 service(s), each documented in its project's specs/.`(새 이름은 상수로 등록되어 이 가드의 리터럴 집합에는 안 잡힌다 — 664 와 같은 방식; 다섯 이름 모두 스펙에 이미 있다.)
- **AC-4** — 서비스 `test`: `./gradlew :projects:iam-platform:apps:account-service:test :projects:iam-platform:apps:admin-service:test :projects:iam-platform:apps:auth-service:test --continue` **rc=0**(26분 33초) — JUnit XML 합계: account 804 · admin 1468 · auth 1359, 실패 0 · 오류 0 · skip 0 · PR CI 대기.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
