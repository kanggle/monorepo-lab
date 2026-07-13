# Task ID

TASK-BE-507

# Title

소비자 tenant 를 **해석**한다 — 가입 2 + 조회 6 의 `FAN_PLATFORM` 상수 제거, 폼 로그인 client-scoped 화. **D1 = A (2026-07-13 사용자 결정)**

# Status

done

# Owner

iam-platform

# Task Tags

- code
- iam
- tenant

---

# D1 결정 (2026-07-13, 사용자) — **A: 가입한 플랫폼이 곧 tenant**

소비자 account 행의 `tenant_id` = **그가 등록한 OIDC 클라이언트의 tenant**. web-store 가입 → `ecommerce`, fan 앱 가입 → `fan-platform`.

**A 는 D1-a 를 강제한다**(원 티켓에 명시). 권장안대로 채택:

> **D1-a — 폼 로그인 조회를 client tenant 로 스코프하되, 미스 시 cross-tenant 로 폴백한다.**

**폴백이 이 티켓의 안전장치다.** 스코프 조회로 *교체*만 하면 **기존 ecommerce 쇼핑객이 전원 로그인 불가**가 된다 — 그들의 credential 행은 (BE-506 이 실측했듯) 전부 `fan-platform` 이므로 `ecommerce` 스코프 조회가 미스한다. 그래서:

| 사용자 | 조회 | 결과 |
|---|---|---|
| BE-507 **이전** 가입자 (전원 `fan-platform`) | client(`ecommerce`) 스코프 → **미스** → cross-tenant 폴백 → 1건 | **오늘과 동일하게 로그인** (net-zero, 마이그레이션 불필요) |
| BE-507 **이후** ecommerce 가입자 | client(`ecommerce`) 스코프 → **히트** | tenant 가 정확한 토큰 |
| 같은 이메일이 fan + ecommerce 양쪽 | client 로 스코프되어 각자 히트 | **각 플랫폼에서 로그인 성공** (구: 둘 다 `BadCredentials`) |

모호성 fail-closed 가드는 **유지**하되, 개시 클라이언트가 없는 호출(직접 `/login` 방문)에서만 도달 가능해진다.

---

# 구현 결과 (2026-07-13)

## auth-service — tenant 를 흘려보내는 쪽

- **`SignupPageController`** — `SavedRequestTenantResolver` 주입. 저장된 `/oauth2/authorize` 요청에서 client tenant 를 해석해 `AccountServicePort.signup(..., tenantId)` 로 전달. **소셜 컨트롤러는 이 리졸버를 처음부터 쓰고 있었고 폼 가입만 안 쓰고 있었다** — BE-506 이 짚은 끊긴 링크가 이것이다.
- **`OAuthLoginUseCase`** — `resolveSocialLogin(command, tenantId)` 로 tenant 스레딩. **browser 경로는 이미 손에 쥔 `tenantId` 를 `socialSignup` 에 안 넘기고 있었다**(이제 넘긴다). legacy custom-JWT `callback` 경로는 개시 클라이언트가 없으므로 `null` → fan 유지(net-zero).
- **`AccountServicePort` / `AccountServiceClient`** — `signup` / `socialSignup` 에 `tenantId` 파라미터. 값이 있으면 `X-Tenant-Id` 헤더로 송신, 없으면 **헤더 자체를 생략**(= account-service 가 fan 으로 핀).
- **`CredentialAuthenticationProvider`** — D1-a. `resolveCredential()` = client-scoped(`findByTenantIdAndEmail`) 우선 → 미스 시 기존 cross-tenant(`findAllByEmail` + 모호성 fail-closed) 폴백. client tenant 는 `RequestContextHolder` → `SavedRequestTenantResolver` 로 얻고, 요청 컨텍스트/저장요청이 없으면 `null` → 레거시 동작 그대로.

## account-service — tenant 를 받는 쪽

- **생성 2**: `SignupController` / `SocialSignupController` 가 `X-Tenant-Id`(선택) 수용 → `SignupCommand` / `SocialSignupCommand` 의 `tenantId` → `TenantId.fromHeaderOrDefault(...)`(BE-467 이 이미 만들어 둔 리졸버 재사용 — 부재/공백/`*` → fan). `SignupUseCase` / `SocialSignupUseCase` 의 `TenantId.FAN_PLATFORM` **상수 제거**.
- **유령 tenant 방지**: 두 use case 모두 `requireActiveTenant()` — 존재 + ACTIVE 검증(`ProvisionAccountUseCase` 패턴). **없으면 FK 위반이 `DataIntegrityViolationException` → `AccountAlreadyExistsException`(409) 으로 잘못 매핑된다**(원인이 tenant 인데 "이메일 중복"으로 보고). 404 `TENANT_NOT_FOUND` / 409 `TENANT_SUSPENDED`(핸들러 실측값 — 처음에 403 으로 적었다가 `GlobalExceptionHandler:171` 확인 후 정정).
- **조회 6 → tenant-aware** (BE-467 의 net-zero 오버로드 관용구 그대로):
  - `ProfileUseCase.getMe/updateProfile`, `AccountStatusUseCase.getStatus`(+ 공개 delete) ← 컨트롤러가 게이트웨이 전파 `X-Tenant-Id` 를 전달.
  - `SendVerificationEmailUseCase` ← 동일.
  - **`VerifyEmailUseCase`** ← **헤더가 도달할 수 없다**(토큰 자체가 인증). `AccountRepository` 는 스펙 규칙상 tenant 없는 `findById` 를 **금지**하므로, tenant 를 **토큰에 실었다**: `EmailVerificationTokenStore` 가 `token → (tenantId, accountId)` 를 저장(Redis 값 `{tenantId}|{accountId}`). 구분자 없는 값 = BE-507 이전 발급 토큰 → `fan-platform` 폴백(그때는 그것만 가능했으므로 **추정이 아니라 정확**; TTL 24h 후 소멸).
  - **`UpdateLastLoginUseCase`** ← **AC-6 은 공짜였다**: `auth.login.succeeded` 페이로드는 **BE-248 부터 `tenantId` 를 필수로 싣고 있었고**(계약 schema v2, 발행자 `requireTenantId()` fail-closed) `LoginSucceededConsumer` 가 **읽지 않았을 뿐**이다. 이제 읽는다. (코드 주석은 *"이벤트가 tenant_id 를 안 나른다"* 고 **거짓 선언**하고 있었다 — BE-506 기록도 그 주석을 믿고 옮겨 적었다가 여기서 정정.)

## 계약

- `specs/contracts/http/account-api.md` — 소비자 표면의 `X-Tenant-Id` 규약 + signup 헤더/에러(404/409) + verify-email 예외 사유.
- `specs/contracts/http/internal/auth-to-account-social.md` — social-signup 의 `X-Tenant-Id`.
- `auth-events.md` **무변경** — 이미 `tenantId` 필수였다(위 참조).

---

# Acceptance Criteria

- [x] **AC-0 (D1 게이트)** — 사용자 결정 A 수령(2026-07-13). D1-a = scoped-first + cross-tenant fallback.
- [x] **AC-1** — ecommerce client 가입 소비자의 `accounts.tenant_id` / `credentials.tenant_id` / 토큰이 모두 `ecommerce`. 폼: `SignupPageControllerTest`(client tenant 전달) + `SignupUseCaseTest`(계정 행 + credential pass-through + `account.created` 이벤트 3축) + `AccountServiceClientUnitTest`(**WireMock 으로 헤더가 실제로 프로세스를 떠나는지** 단언). 소셜: `SocialSignupUseCaseTest`.
- [x] **AC-2** — 조회 6 곳 tenant-aware. `VerifyEmailUseCaseTest`(토큰이 실어온 ecommerce 로 스코프) · `SendVerificationEmailUseCaseTest` · `LoginSucceededConsumerUnitTest`(이벤트 tenant 사용) · 컨트롤러 슬라이스 3종.
- [x] **AC-3** — 폼 로그인 client-scoped + 폴백. **기존 fan 계정이 ecommerce 클라이언트로 로그인해도 폴백이 찾는다**(마이그레이션 없이 무손실).
- [x] **AC-4 (fan 무손실)** — 코드 경로 전부 net-zero 기본값. account 496 / auth 619 테스트 **실패 0**.
- [x] **AC-5** — 계약 2종 갱신(+`auth-events.md` 는 변경 불필요임을 확인).
- [x] **AC-6** — `UpdateLastLoginUseCase` 는 **이벤트의 `tenantId`** 를 쓴다(계약 변경 불필요). 레거시/누락 이벤트는 fan 폴백 + 파티션 미독.

## 검증

- **단위**: account-service **496 tests / 0 failures / 0 errors** (47 skip = 기존 Docker 게이트), auth-service **619 / 0 / 0** (26 skip).
- **mutation-check (가드가 무는가)**: `SignupUseCase` 의 tenant 해석을 `TenantId.FAN_PLATFORM` 상수로 **되돌리자 BE-507 가드 3개가 정확히 FAILED**, 나머지 14개는 통과(= 오탐 0). 되돌린 뒤 복구 확인.
- **미실행**: Testcontainers IT / e2e — 이 호스트의 Docker 백엔드가 행(`timed out dialing Hyper-V socket`). **CI(Linux)가 IT 권위**이므로 PR CI 에서 확인해야 한다.

---

# Out of Scope (남은 것)

- **기존 `fan-platform` 로 찍힌 ecommerce 소비자의 소급 재배정** — 프로덕션 데이터. 이 티켓은 additive(폴백이 그들을 계속 살린다). 재배정을 원하면 별도 티켓 + 별도 판단.
- **`RoleSeedPolicy` 시드 정책** → `TASK-MONO-381`. **tenant 를 고쳐도 381 의 role 가드는 여전히 vacuous** 하다(BE-506 실측).
- **ecommerce 게이트웨이 `acceptAnyWellFormedTenant()` 제거** — **이제야 검토 가능해졌다**(새 소비자가 진짜 `ecommerce` tenant 를 갖는다). 다만 **기존 fan 소비자가 폴백으로 살아 있는 동안은 제거하면 그들이 403** 이 된다 ⇒ 소급 재배정 이후에나 가능. 후속 티켓의 선행조건으로 기록.
- **web-store `/signup` 라우트가 NextAuth 를 GET 으로 때리는 문제**(BE-506 에서 검증 불가로 남김) — 별도 확인 필요.

---

# Related Specs

- `projects/iam-platform/tasks/review/TASK-BE-506-*.md` — **반경 실측(이 티켓의 근거)**
- `.../account-service/.../domain/tenant/TenantId.java` — `FAN_PLATFORM` = 이제 상수 핀이 아니라 **폴백**
- `.../account-service/.../infrastructure/persistence/AccountQueryPortImpl.java:48-50` — 따랐던 선례(TASK-BE-357)
- `.../auth-service/.../infrastructure/security/SavedRequestTenantResolver.java` — client→tenant 유도(기존 자산)
- `.../auth-service/.../infrastructure/security/CredentialAuthenticationProvider.java` — D1-a

# Related Contracts

- `specs/contracts/http/account-api.md` · `specs/contracts/http/internal/auth-to-account-social.md`
- `specs/contracts/events/auth-events.md` (무변경 — `tenantId` 이미 필수)

# Edge Cases

- **tenant 미지정 호출** → `fan-platform`. 하위호환 기본값이지 기대값이 아니다.
- **미등록/SUSPENDED tenant** → 404/409 로 거부. 계정이 유령 tenant 로 태어나지 않는다.
- **같은 사람, 두 플랫폼** → account/credential 행 2개, identity 1개(ADR-036 `reuseExisting` 수렴). 로그인은 client 로 갈린다.
- **소셜 기존 계정** — tenant 스코프 조회이므로 "다른 tenant 의 같은 이메일"은 더 이상 같은 계정이 아니다(테스트로 고정).
- **인-플라이트 이메일 인증 토큰**(구 형식) → fan 폴백으로 계속 동작.

# Failure Scenarios

- **F1 — 가입만 고치고 조회 6 곳을 안 고친다** → 회피함(함께 이동).
- **F2 — 로그인 스코프를 단순 교체한다** → **기존 소비자 전원 로그인 불가.** 폴백으로 회피함(D1-a).
- **F3 — 기존 fan 소비자 회귀** → net-zero 기본값 + 전체 단위 그린.
- **F4 — 소급 재배정을 끼워넣는다** → 하지 않음. Out of Scope.

# Definition of Done

- [x] D1 결정 기록 → 구현 → 계약 갱신 → 테스트(+mutation-check).
- [ ] CI GREEN 확인 (Testcontainers IT 는 CI 가 권위 — 로컬 Docker 행).
- [ ] `projects/iam-platform/tasks/INDEX.md` done entry (머지 후 close chore).

---

# Provenance

`TASK-BE-506`(반경 실측)의 산물. 506 은 하드코딩 모집단을 2 → **10 곳**으로 재계수해 **부분 수정이 새 결함을 만든다**는 것을 확정했고, 이 티켓은 그 10 곳을 **한 번에** 움직였다.

**이 티켓이 다시 확인한 것**: 코드 주석은 증거가 아니다. `UpdateLastLoginUseCase` 의 *"이벤트가 tenant_id 를 안 나른다"* 는 주석은 **거짓**이었고(BE-248 이 이미 필수 필드로 만들었다), 나는 BE-506 기록에 그 주석을 **사실로 옮겨 적었다가** 발행자 코드(`OutboxAuthEventPublisher:159` `requireTenantId`)와 계약(schema v2)을 직접 읽고서야 정정했다. **선행 문서의 진술은 출처가 아니라 가설이다** (`feedback_recount_population_dont_inherit_scope`).

분석·구현=Opus 4.8.
