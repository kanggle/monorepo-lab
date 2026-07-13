# Task ID

TASK-BE-507

# Title

소비자 tenant 를 **해석**하라 — 가입 2 + 조회 6 이 `FAN_PLATFORM` 상수에 묶여 있고, 폼 로그인은 cross-tenant fail-closed 다. **D1 결정이 선행**한다(사람 게이트)

# Status

ready

# Owner

iam-platform

# Task Tags

- code
- iam
- tenant
- needs-decision

---

# ⚠️ D1 결정 게이트 — 코드보다 먼저

**이 티켓은 D1 이 확정되기 전까지 구현에 착수하면 안 된다.** 소비자의 `tenant_id` 가 무엇을 뜻하는지는 스펙 어디에도 없고(HARDSTOP-09 성격), 어느 쪽을 고르느냐에 따라 **로그인 동작과 데이터 모델이 갈린다**. 아래 두 선택지를 사람에게 제시하고 답을 받은 뒤 시작한다.

## D1 — 소비자 account 행의 `tenant_id` 는 무엇인가?

### 옵션 A — **가입한 플랫폼**(권장)

소비자는 자신이 등록한 플랫폼의 tenant 를 갖는다. web-store 에서 가입 → `tenant=ecommerce`, fan 앱에서 가입 → `tenant=fan-platform`.

- **근거**: 이미 시스템의 나머지가 이렇게 가정한다 — `SavedRequestTenantResolver` 가 client→tenant 를 유도하고, 소셜 토큰은 이미 `ecommerce` 를 싣고 있으며, e2e 픽스처(`iam-consumer-seed.sql:39`)조차 `tenant_id='ecommerce'` 로 소비자를 만든다. ecommerce 다운스트림 10 개 서비스가 `WHERE tenant_id` 로 스코프하므로, 소비자가 `ecommerce` 여야 데이터가 제자리에 앉는다. ADR-036(born-unified identity)이 **identity 는 tenant 를 가로질러 수렴**시키므로, account **행**이 tenant 별로 갈라져도 "한 사람"은 유지된다.
- **대가**: 같은 이메일이 두 플랫폼에 각각 account/credential 행을 갖게 된다 ⇒ **폼 로그인의 cross-tenant 모호성**(아래 D1-a)을 반드시 함께 해결해야 한다. 이것이 이 티켓이 국소가 아닌 첫째 이유다.

### 옵션 B — 소비자는 tenant 로 나누지 않는다

소비자 account 는 단일 소비자 tenant(예: 현행 `fan-platform`, 또는 새 `consumer`)에 머무르고, 플랫폼 소속은 tenant 가 아닌 다른 축(예: `entitled_domains`, roles)으로 표현한다.

- **근거**: 사람은 하나다. 같은 사람이 fan 과 ecommerce 를 모두 쓰면 계정이 둘로 갈라지는 게 오히려 이상하다. 로그인 모호성 문제가 아예 생기지 않는다.
- **대가**: ecommerce 다운스트림의 `WHERE tenant_id = 'ecommerce'` 스코핑과 정면 충돌한다 — 소비자 주문이 `fan-platform`(또는 `consumer`)으로 적히는 **현행 오배정을 설계로 승격**시키는 셈이라, 다운스트림 스코핑 축을 tenant 에서 다른 것으로 바꾸는 훨씬 큰 작업을 부른다. 게다가 `acceptAnyWellFormedTenant()` 를 영구화한다.

**권장 = A.** 시스템의 나머지가 이미 A 를 가정하고 있고, B 는 ecommerce 데이터 스코핑 축 자체를 재설계해야 한다. 다만 **A 는 D1-a 를 동반 결정으로 요구한다.**

### D1-a (A 를 고를 경우 필수) — 폼 로그인의 tenant 모호성을 어떻게 없애는가?

`CredentialAuthenticationProvider:75-84` 는 지금 `findAllByEmail(email)` 로 **tenant 를 무시하고** 찾은 뒤, **매치가 2 건 이상이면 `BadCredentialsException`** 으로 fail-closed 한다(주석: *"a tenant-chooser UI is a separate future task"*). A 를 채택해 같은 이메일이 fan+ecommerce 양쪽에 존재하게 되면 **그 사용자는 어느 쪽으로도 로그인할 수 없게 된다** — 즉 A 는 이 조회를 **client 의 tenant 로 스코프**하도록 함께 바꾸지 않으면 **로그인을 깨뜨린다**.

- 권장: **client-scoped 조회**. 로그인 폼은 `/oauth2/authorize` 의 saved request 뒤에서 뜨므로 `SavedRequestTenantResolver` 가 이미 그 자리에서 client 의 tenant 를 준다(소셜 경로가 그렇게 쓰고 있다). `findByTenantIdAndEmail(clientTenant, email)` 로 바꾸면 모호성 자체가 소멸한다.
- ADR 이 필요한가? — A + client-scoped 조회는 기존 구조(client→tenant 유도)의 **완성**이지 새 아키텍처가 아니므로, 이 티켓의 D1 기록으로 충분하다고 본다. 사람이 ADR 을 원하면 `ADR-MONO-0NN` 으로 승격.

---

# Goal

`TASK-BE-506` 실측이 확정한 결함을 고친다: **소비자의 tenant 가 가입 시점에 해석되지 않아**, ecommerce 소비자가 `tenant=fan-platform` 계정으로 태어나고(토큰은 로그인 방식에 따라 `fan-platform`/`ecommerce` 로 갈리며), account-service 의 소비자 조회 6 곳이 fan 계정만 찾는다.

D1=A 를 전제로 한 목표 상태:

- 가입(폼/소셜)이 **initiating OIDC client 의 tenant** 로 account 행을 만든다.
- account-service 의 소비자 조회 경로가 **호출자의 tenant** 로 계정을 찾는다(더 이상 `FAN_PLATFORM` 상수 아님).
- 폼 로그인이 **client 의 tenant 로 스코프**되어 cross-tenant 모호성이 소멸한다.
- 기존 fan 소비자는 무손실(추가 tenant 는 additive).

# Scope

## In Scope

**auth-service (tenant 를 흘려보내는 쪽)**

- `SignupPageController` 에 `SavedRequestTenantResolver` 주입 → 해석된 tenant 를 `AccountServicePort.signup(...)` 에 전달(`AccountServiceClient` 바디 또는 `X-Tenant-Id` 헤더).
- `OAuthLoginUseCase:204` 의 `socialSignup(...)` 호출에 이미 손에 든 `tenantId`(`resolveBrowserLogin(command, tenantId)`) 를 전달 — **지금은 갖고 있으면서 안 넘긴다**.
- `CredentialAuthenticationProvider` 폼 로그인 조회를 **client-scoped** 로(D1-a). 모호성 fail-closed 코드는 안전망으로 유지하되 도달 불가가 되는 게 정상.

**account-service (tenant 를 받는 쪽)**

- `SignupController` / `SocialSignupController` 가 tenant 입력을 수용(헤더 권장 — `TenantId.fromHeaderOrDefault` 가 이미 존재, BE-467). **미지정 시 `fan-platform` 기본값**으로 하위호환.
- `SignupCommand` / `SocialSignupCommand` 에 tenant 필드, `SignupUseCase:35` / `SocialSignupUseCase:33` 상수 제거.
- 수용한 tenant 는 `ProvisionAccountUseCase:58-62` 와 동일하게 **존재 + ACTIVE 검증**(미등록 tenant 로 계정이 태어나지 않도록).
- **조회 6 곳을 tenant-aware 로**: `ProfileUseCase:26,:37` · `AccountStatusUseCase:46` · `VerifyEmailUseCase:60` · `SendVerificationEmailUseCase:61` · `UpdateLastLoginUseCase:68`. 호출자(게이트웨이 `X-Tenant-Id` / 토큰 claim)에서 tenant 를 받아 스코프한다. **선례**: `AccountQueryPortImpl:48-50`(TASK-BE-357)이 같은 작업을 관리자 이메일 검색에서 이미 했다 — **그 패턴을 따른다.**
- `UpdateLastLoginUseCase` 는 **이벤트에 `tenant_id` 가 없다**(`auth.login.succeeded`). 이벤트 계약에 tenant 를 추가하거나(발행자=auth-service), accountId 로 tenant-agnostic 조회를 허용하는 별도 포트를 두거나 — 둘 중 하나를 골라 기록할 것.

**계약**

- `specs/contracts/http/account-api.md` — signup 이 tenant 입력을 받는다.
- `specs/contracts/http/internal/auth-to-account.md` — `signup` / `socialSignup` 에 tenant.
- `specs/contracts/events/auth-events.md` — `auth.login.succeeded` 에 `tenant_id`(위 선택 시).
- **계약 먼저, 구현 나중**(CLAUDE.md Contract Rule).

**테스트 (이 결함이 왜 안 보였는지에 대한 답이어야 한다)**

- ecommerce client 로 가입 → `accounts.tenant_id='ecommerce'` + `credentials.tenant_id='ecommerce'` + **토큰 `tenant_id` claim = `ecommerce`** 를 단언하는 IT. **오늘 이 단언을 하는 테스트가 0 건이다.**
- fan 회귀 무손실: 기존 fan 가입/로그인 IT 전량 그린(`AccountSignupIntegrationTest` 등은 tenant 를 명시하도록 갱신).
- 같은 이메일 fan+ecommerce 동시 존재 → **각 client 로 각각 로그인 성공**(D1-a 가 실제로 모호성을 없앴는지).
- `e2e/fixtures/iam-consumer-seed.sql` 의 **SQL 직접 INSERT 를 실제 가입 흐름으로 대체할 수 있는지** 확인 — 대체 가능해지는 것이 이 티켓이 성공했다는 가장 강한 신호다(픽스처가 더 이상 프로덕션 도달 불가 상태를 빚지 않는다).

## Out of Scope

- **기존 `fan-platform` 로 찍힌 ecommerce 소비자의 소급 재배정** — 프로덕션 데이터(ADR-036 P4 와 같은 성격). 별도 티켓. 이 티켓은 **additive**: 새 가입만 올바른 tenant 를 받는다.
- `RoleSeedPolicy` 의 client-platform 시드 정책 → `TASK-MONO-381`. **주의: tenant 를 고쳐도 381 의 role 가드는 여전히 vacuous** 하다(BE-506 실측).
- ecommerce 게이트웨이의 `acceptAnyWellFormedTenant()` 제거 — 이 티켓이 끝나야 **비로소 제거를 검토할 수 있게** 된다(소비자가 진짜 `ecommerce` tenant 를 갖게 되므로). 제거 자체는 후속.

---

# Acceptance Criteria

- [ ] **AC-0 (D1 게이트)** — D1(+A 면 D1-a)에 대한 사람의 결정이 티켓에 기록되기 전에는 코드 변경 0. 결정 없이 착수하면 **아무 결정 없이 일어난 아키텍처 변경**이 된다.
- [ ] **AC-1** — ecommerce web-store client 로 가입한 소비자의 `accounts.tenant_id` / `credentials.tenant_id` / 토큰 `tenant_id` claim 이 **모두 `ecommerce`** 다(폼·소셜 **양쪽**). IT 로 단언.
- [ ] **AC-2** — account-service 소비자 조회 6 곳이 호출자 tenant 로 스코프된다. `tenant=ecommerce` 계정의 프로필 조회/수정·상태 조회·이메일 인증이 동작한다.
- [ ] **AC-3** — 폼 로그인이 client-scoped 조회로 바뀌어, 같은 이메일이 두 tenant 에 존재해도 **각 플랫폼에서 로그인이 성공**한다(현행: 둘 다 `BadCredentialsException`).
- [ ] **AC-4 (fan 무손실)** — 기존 fan-platform 소비자 가입/로그인/프로필 회귀 없음. tenant 미지정 호출은 `fan-platform` 으로 하위호환.
- [ ] **AC-5** — 계약 3 종 선행 갱신(구현 전).
- [ ] **AC-6** — `UpdateLastLoginUseCase` 의 tenant 부재 문제 처리 방식이 기록되고 구현된다(이벤트에 tenant 추가 vs tenant-agnostic 포트).

# Related Specs

- `projects/iam-platform/tasks/review/TASK-BE-506-*.md` — **반경 실측(이 티켓의 근거 전문)**
- `projects/iam-platform/apps/account-service/.../domain/tenant/TenantId.java` — `FAN_PLATFORM` javadoc 에 전말 기술
- `projects/iam-platform/apps/account-service/.../infrastructure/persistence/AccountQueryPortImpl.java:48-50` — 따라야 할 **선례**(TASK-BE-357)
- `projects/iam-platform/apps/account-service/.../application/service/ProvisionAccountUseCase.java:55-62` — tenant 수용 + ACTIVE 검증의 **기존 패턴**
- `projects/iam-platform/apps/auth-service/.../infrastructure/security/SavedRequestTenantResolver.java` — client→tenant 유도(이미 존재)
- `projects/iam-platform/specs/features/multi-tenancy.md`

# Related Contracts

- `specs/contracts/http/account-api.md` · `specs/contracts/http/internal/auth-to-account.md` · `specs/contracts/events/auth-events.md`
- `platform/contracts/jwt-standard-claims.md` — `tenant_id`

# Edge Cases

- **tenant 미지정 호출**(레거시/내부) → `fan-platform` 기본값. 하위호환이지, 새 코드가 기대도 되는 값은 아니다.
- **미등록/SUSPENDED tenant 를 client 가 들고 옴** → `ProvisionAccountUseCase` 와 같은 방식으로 거부(계정이 유령 tenant 로 태어나지 않게).
- **같은 사람, 두 플랫폼** → account 행 2 개 / credential 2 개 / identity 1 개(ADR-036 born-unified 가 `reuseExisting` 로 수렴). identity 축이 실제로 수렴하는지 IT 로 확인할 것.
- **소셜 기존 계정** — `SocialSignupUseCase` 는 이메일이 이미 있으면 기존 계정을 돌려준다. tenant 가 붙으면 "다른 tenant 의 같은 이메일" 이 더 이상 같은 계정이 아니다. 이 전이를 명시적으로 테스트.

# Failure Scenarios

- **F1 — 가입만 고치고 조회 6 곳을 안 고친다** → `tenant=ecommerce` 계정이 자기 서비스에서 미아가 된다(프로필 404 / 이메일 인증 실패). **BE-506 이 이 함정을 실측으로 짚었다 — 함께 움직여야 한다.**
- **F2 — 로그인 스코프(D1-a)를 빼먹는다** → 같은 이메일이 두 tenant 에 생기는 순간 **그 사용자는 어디로도 로그인 불가**(현행 fail-closed). 가입 수정이 곧바로 로그인 장애를 만든다.
- **F3 — 기존 fan 소비자 회귀** → tenant 미지정 기본값 + 기존 IT 전량 그린으로 방어.
- **F4 — 소급 재배정을 슬쩍 끼워넣는다** → 프로덕션 데이터 변경. Out of Scope. 별도 티켓 + 별도 판단.

# Test Requirements

- **IT (Testcontainers)**: ecommerce client 가입(폼/소셜) → DB 3 축 + 토큰 claim 단언. fan 회귀. 이메일 중복 across tenants 로그인 성공.
- **Unit**: `SignupUseCase`/`SocialSignupUseCase` 가 주입된 tenant 를 쓰는지(현행 strict-stub 테스트들이 `FAN_PLATFORM` 을 고정하고 있으므로 함께 갱신 — 이 고정은 요구가 아니라 관성이다, BE-506 실측).
- **회귀 가드**: "소비자 가입 tenant" 를 단언하는 테스트가 **최초로** 생긴다. 이게 없어서 이 결함이 살아남았다.

# Definition of Done

- [ ] D1 결정 기록 → 구현 → 계약 선행 갱신 → 테스트 → 리뷰.
- [ ] `projects/iam-platform/tasks/INDEX.md` done entry.
- [ ] 후속 판단 남기기: (a) 소급 재배정 티켓 필요 여부, (b) ecommerce 게이트웨이 `acceptAnyWellFormedTenant()` 제거 가능 여부.

---

# Provenance

`TASK-BE-506`(반경 실측, 2026-07-13)의 산물. 506 은 **주석이 거짓임을 확인하는 데 그치지 않고**, 하드코딩 모집단을 2 → **10 곳**(가입 2 + 조회 6 + javadoc)으로 재계수해 **부분 수정이 새 결함을 만든다**는 사실을 확정했다. 그래서 이 티켓의 첫 AC 는 코드가 아니라 **결정**이다.

분석=Opus 4.8 / 구현 권장=**Opus** (2 서비스 + 계약 3 종 + 로그인 조회 스코프 변경 — 국소 아님).
