# Task ID

TASK-BE-506

# Title

두 가입 경로가 모든 소비자를 `FAN_PLATFORM` tenant 로 하드코딩한다 — 주석은 *"until BE-229"* 라는데 **BE-229 는 이미 done**. ecommerce 소비자까지 오배정되는지 **반경을 실측**하라 (조사 우선)

# Status

done

# Owner

iam-platform

# Task Tags

- investigate
- iam
- tenant

---

# 실측 결과 (AC-0) — 2026-07-13

**오배정은 실재한다. 그리고 반경은 티켓이 세운 2 곳이 아니라 8 파일 10 곳이다.**

세 줄 요약:

1. **경로**: ecommerce 소비자 가입은 실제로 `SignupUseCase`(폼) / `SocialSignupUseCase`(소셜)를 탄다. 체인이 코드로 닫힌다.
2. **값**: `accounts.tenant_id = fan-platform` · `credentials.tenant_id = fan-platform` · 토큰 `tenant_id` 는 **로그인 방식에 따라 갈린다** — 폼 로그인 `fan-platform`, 소셜 로그인 `ecommerce`. 즉 소셜 소비자는 **토큰과 계정 행이 서로 모순**된다.
3. **반경**: 하드코딩은 가입 2 곳이 아니라 **가입 2 + 조회 6 + 상수 javadoc 1 + 잔여 주석 1**. 소비자 표면 **전체**가 fan 전용이다 ⇒ **가입만 고치면 나머지 6 개 조회가 그 계정을 못 찾는다.** 그래서 수정은 국소가 아니고, 후속 `TASK-BE-507` 로 분리한다.

## (1) web-store 소비자 가입이 어느 use case 를 타는가 — **탄다. 둘 다.**

체인(각 링크를 직접 읽어 확인):

```
web-store 로그인/회원가입 → signIn('iam')   (shared/auth/auth.ts, client_id=ecommerce-web-store-client)
  → IAM auth-service /oauth2/authorize      (Spring Authorization Server)
  → IAM /login 페이지                        (WebLoginSecurityConfig @Order(0))
  → login.html:83  "계정이 없으신가요? 회원가입" → /signup      ← TASK-BE-470 이 뚫은 문
  → SignupPageController:59(GET) / :64(POST) → :101 accountServicePort.signup(...)
  → AccountServiceClient → POST /api/accounts/signup
  → SignupController:24 → SignupUseCase:35   →  TenantId.FAN_PLATFORM   ❌
```

소셜은 `login.html:78` → `/login/oauth/{provider}` → `OAuthLoginUseCase:204` → `POST /internal/accounts/social-signup` → `SocialSignupController:24` → `SocialSignupUseCase:33` → **역시 `FAN_PLATFORM`**.

**tenant 힌트는 한 층 위에 실재하는데 경계에서 버려진다.** `SavedRequestTenantResolver` 는 `/oauth2/authorize` 의 `client_id` 로부터 tenant 를 유도하며(자기 javadoc: *"a web-store client yields tenant_id=ecommerce"*), 소셜 컨트롤러는 그걸 principal `details` 에 실어 토큰까지 보낸다(`SocialLoginBrowserController:171`). 그런데 **`SignupPageController` 는 이 리졸버를 아예 주입받지 않고**, `AccountServiceClient.signup(email, password, displayName)` 바디에도 tenant 필드가 없다. account-service 쪽도 `SignupController` 에 `@RequestHeader` 가 0 개다 ⇒ **오늘 tenant 는 물리적으로 가입에 도달할 수 없다.**

## (2) 결과 값 — accounts / credentials / token

| 축 | 값 | 증거 |
|---|---|---|
| `accounts.tenant_id` | **`fan-platform`** | `SignupUseCase:35` → `Account.create` → `AccountJpaEntity.fromDomain:62` (서비스 내 **유일한** `accounts.tenant_id` writer). **DB 수준 경험 증거**: `AccountSignupIntegrationTest:90` (Testcontainers, CI Linux 에서 실제로 도는 IT) 이 `POST /api/accounts/signup` 후 `findByEmail(TenantId.FAN_PLATFORM, …)` 가 present 임을 단언한다. |
| `credentials.tenant_id` | **`fan-platform`** | `SignupUseCase:77-79` 가 `account.getTenantId().value()` 를 pass-through(BE-313) → `CreateCredentialUseCase:52` 가 그대로 기록. (null-fallback `"fan-platform"` 은 **발화조차 하지 않는다** — 하드코딩이 이미 non-null 을 보낸다.) |
| 토큰 `tenant_id` (폼 로그인) | **`fan-platform`** | `CredentialAuthenticationProvider:75` 가 **cross-tenant** `findAllByEmail` 로 credential 을 찾고 `:92-94` 에서 **그 행의 tenant** 를 principal `details` 에 싣는다 → `TenantClaimTokenCustomizer:238-244` 가 **principal details 를 client 메타데이터보다 우선**해 claim 으로 박는다. |
| 토큰 `tenant_id` (소셜 로그인) | **`ecommerce`** | `SocialLoginBrowserController:171` 이 **client 유도 tenant** 를 details 에 싣는다 → 같은 customizer 경로. **계정 행(fan-platform)과 모순.** |
| 토큰 `roles` | `[CUSTOMER]` (양쪽 다) | `TenantClaimTokenCustomizer:251-253` — roles 의 platform 키는 **client** 의 tenant(=`ecommerce`) ⇒ `RoleSeedPolicy:48` 이 `CUSTOMER` 시드. **그래서 토큰은 `tenant_id=fan-platform` + `roles=[CUSTOMER]` 라는 자기모순 상태로도 아무 게이트에 걸리지 않는다.** |

> **라이브 가입 실행은 이 세션에서 불발했다** — Docker 백엔드가 `timed out dialing Hyper-V socket` 으로 행이라 `docker-compose.e2e.yml` 스택을 못 띄웠다(jar 빌드까지는 성공). 그래서 (2)의 DB 축 증거는 **CI 에서 실제로 도는 Testcontainers IT** 로 대체했다 — 이 저장소 기준 Linux CI 가 IT 권위이므로 로컬 왕복보다 약하지 않다. 다만 **SAS authorization_code 토큰을 실제로 디코드한 아티팩트는 없다**(그 경로를 단언하는 테스트도 없다 — 아래 "왜 아무도 못 봤나"). 도커가 살아나면 확인용 왕복 1 회는 여전히 가치가 있다.

## (3) 오배정의 반경 — **모집단을 다시 셌다: 2 → 10**

티켓은 "두 가입 경로"라 했다. 전수 grep 결과 `until TASK-BE-229` 하드코딩은 **8 파일 10 곳**이었고, 그중 **6 곳은 가입이 아니라 조회**다:

| 사이트 | 무엇 | 영향 |
|---|---|---|
| `SignupUseCase:35` | 계정 **생성** | 모든 폼 가입 소비자 = fan-platform |
| `SocialSignupUseCase:33` | 계정 **생성** | 모든 소셜 가입 소비자 = fan-platform |
| `ProfileUseCase:26`, `:37` | `findById(FAN_PLATFORM, …)` | 프로필 조회/수정이 **fan 계정만** 찾는다 |
| `AccountStatusUseCase:46` | 〃 | 계정 상태 조회 〃 |
| `VerifyEmailUseCase:60` | 〃 | 이메일 인증 〃 |
| `SendVerificationEmailUseCase:61` | 〃 | 인증메일 재발송 〃 |
| `UpdateLastLoginUseCase:68` | 〃 (Kafka `auth.login.succeeded` 소비자) | **non-fan 계정의 로그인은 last_login 갱신이 조용히 no-op** (poison-pill 가드가 삼킨다). 이벤트에 `tenant_id` 자체가 없어 tenant-aware 로 만들 재료도 없다. |
| `TenantId:25` (javadoc) | 상수 설명 | "BE-229 pending" 이라는 거짓 선언의 출처 |

**이것이 수정 범위 판단을 뒤집는다.** 가입만 동적으로 바꾸면 `tenant=ecommerce` 계정이 태어나는데, **위 6 개 조회 경로가 그 계정을 못 찾는다** — 프로필 404, 상태 404, 이메일 인증 실패, last_login 미갱신. 즉 **부분 수정이 새 결함을 만든다.**

**이 결함 클래스는 이미 한 번 확인된 적이 있다**: `AccountQueryPortImpl:48-50` (TASK-BE-357) 이 관리자 이메일 검색에서 같은 잔여물을 걷어내며 주석에 이렇게 남겼다 — *"Was hard-coded to `TenantId.FAN_PLATFORM` (the stale `// until TASK-BE-229` residue) — which made every non-fan (e.g. ecommerce) account permanently un-findable by email."* **한 곳은 고쳤고, 나머지 6 곳은 남았다.**

### 누가 실제 피해자인가

- **fan-platform 소비자** — **우연히 정답**. 상수가 자기 tenant 와 같다. 회귀 위험만 있고 결함은 없다.
- **ecommerce 소비자** — **유일한 라이브 피해자**. 계정/크리덴셜은 `fan-platform`, 토큰은 로그인 방식에 따라 `fan-platform`/`ecommerce`. 게이트웨이가 `acceptAnyWellFormedTenant()` 라 403 이 안 뜨고(`TenantClaimValidator:189-191`), 다운스트림 서비스들이 그 claim 을 `X-Tenant-Id` → `TenantContext` → `WHERE tenant_id` 로 쓰므로 **조용한 데이터 오스코핑**이다(주문이 `fan-platform` 으로 적히고, `ecommerce` 로 시드된 카탈로그/주문 조회는 빈 결과).
- **scm/wms/erp/finance 스태프 계정** — 가입이 아니라 `POST /internal/tenants/{tenantId}/accounts`(`ProvisionAccountUseCase:55`, path variable → tenant)로 만들어지므로 **생성은 정상**. 다만 위 6 개 조회 경로에는 **이미 오늘도 안 잡힌다**(BE-357 이 고친 이메일 검색과 같은 이유).
- **미래의 모든 소비자 플랫폼** — 같은 결함을 그대로 물려받는다.

### 왜 아무 테스트도 못 잡았나 — **불가능한 픽스처**

`projects/ecommerce-microservices-platform/apps/web-store/e2e/fixtures/iam-consumer-seed.sql:39` 이 소비자 크리덴셜을 **`tenant_id='ecommerce'` 로 직접 INSERT** 한다. 주석까지 달려 있다 — *"tenant_id='ecommerce' matches the V0012-seeded ecommerce-web-store-client."* **그런데 프로덕션에는 `tenant=ecommerce` 소비자를 만들 수 있는 경로가 없다.** e2e 는 SQL 로 손수 빚은, **프로덕션이 도달할 수 없는 상태**를 초록으로 검증해 온 것이다(`env_test_fixture_impossible_input_proves_nothing` 그대로). 반대편에서는 `AccountSignupIntegrationTest:90` 등 IT 들이 **fan-platform 을 고정**해 하드코딩을 "요구사항"처럼 못 박고 있다 — 단, **비-fan 가입을 요구하는 테스트는 0 건**이므로 이 고정은 요구가 아니라 관성이다.

### 주석은 왜 거짓인가 (F1 판정)

`TASK-BE-229` 는 **auth-service** 가 `credentials.tenant_id` 를 *소비*해 JWT claim/refresh row 에 싣는 일이었다(BE-229 In Scope 전문 확인). **account-service 에 tenant 입력을 도입하는 항목은 BE-229 스코프에 애초에 없었다.** 따라서 *"until TASK-BE-229 introduces dynamic tenant injection"* 은 **도착할 수 없는 약속**이었다 — BE-229 가 이 경로를 "의도적으로 제외"한 게 아니라, **아무도 이 경로의 주인이 아니었다.** ⇒ F1 회피: 주석 정정만으로 끝낼 사안이 **아니다**(오배정이 실재하므로). 주석도 고치고(AC-2), 수정은 BE-507 로 뺀다(AC-1).

## AC-1 판정 — **국소 수정 아님. `TASK-BE-507` 로 분리한다.**

수정이 국소가 아닌 이유 3 가지(각각 독립적으로 치명적):

1. **조회 6 곳 동반 이동** — 위 표. 가입만 고치면 새 tenant 의 계정이 자기 서비스에서 미아가 된다.
2. **폼 로그인이 cross-tenant 조회 + 모호성 fail-closed** — `CredentialAuthenticationProvider:75-84` 는 `findAllByEmail(email)`(tenant 무시) 후 **매치가 2 건 이상이면 `BadCredentialsException`**. 같은 이메일이 fan 과 ecommerce 양쪽에 생기는 순간 **그 사용자는 어느 쪽으로도 로그인 불가**가 된다. 즉 tenant 를 동적으로 만들려면 **로그인 조회를 client 의 tenant 로 스코프하는 결정**이 선행돼야 한다.
3. **소비자 tenant 의 의미가 스펙에 없다** — 소비자의 `tenant_id` 는 (a) 가입한 플랫폼인가, 아니면 (b) 사람은 하나이므로 tenant 로 쪼개면 안 되는가? ADR-036(born-unified identity)은 identity 를 수렴시키지만 account **행**의 tenant 분할 여부는 말하지 않는다. **아키텍처 결정 없이 코드로 고를 수 없다(HARDSTOP-09 성격).**

기존 `fan-platform` 로 찍힌 ecommerce 소비자의 **소급 재배정은 Out of Scope**(원 티켓 명시) — BE-507 이 결정 후 별도 판단.

---

# Dependency Markers

- **발굴 경위**: `TASK-MONO-381` 구현 실측 중(2026-07-13) — web-store role 가드가 vacuous 한 원인을 파다 소비자 tenant 배정이 신뢰 불가임을 발견.
- **선행 (done)**: `TASK-BE-228`(account tenant schema) · `TASK-BE-229`(auth jwt tenant claim) · `TASK-BE-230`(gateway tenant propagation) · `TASK-BE-357`(같은 잔여물을 관리자 이메일 검색에서 제거한 **선례**).
- **후속 (이 조사의 산물)**: `TASK-BE-507` — 소비자 tenant 해석(가입+조회+로그인) 배선. **D1 결정 게이트 포함.**
- **연관**: `TASK-MONO-381`(이 조사에 park 되어 의존 — 아래) · `TASK-BE-313`(credential tenant pass-through).

## `TASK-MONO-381` 에 주는 답

381 은 "tenant 배정이 신뢰 가능해지면 (a)/(b) 를 정한다"며 이 조사에 park 됐다. **답: 오늘 소비자 tenant 는 신뢰 불가이며, BE-507 이 끝나기 전에는 신뢰 가능해지지 않는다.** 추가로 381 이 알아야 할 실측 사실 하나 — **role 가드가 vacuous 한 직접 원인은 tenant 가 아니다**: `TenantClaimTokenCustomizer:251-253` 이 roles 를 **client 의 platform**(`ecommerce`)으로 시드하므로(`RoleSeedPolicy:48`), 저장된 role 이 0 건인 임의의 인증 사용자에게도 `CUSTOMER` 가 붙는다. tenant 를 고쳐도 **roles 시드 정책을 건드리지 않으면 381 의 가드는 여전히 vacuous** 하다.

---

# Scope

## In Scope (이 티켓에서 완료)

- **AC-0 반경 실측** — 위 기록이 산출물.
- **AC-2 stale 주석 정정** — 코드 동작 0 변경(주석/javadoc 만).

## Out of Scope (→ `TASK-BE-507`)

- tenant 동적 해석 구현(가입 2 + 조회 6 + 로그인 스코프 + 계약 갱신).
- 기존 `fan-platform` 소비자 소급 재배정.
- `RoleSeedPolicy` 시드 정책(→ `TASK-MONO-381`).
- web-store `/signup` 라우트가 `GET /api/auth/signin/iam` 로 리다이렉트해 NextAuth 가 이를 거부할 가능성 — **이 세션에서 검증 못 함**(`@auth/core` 가 체크아웃에 설치돼 있지 않아 정적 확인 불가). 사실이면 web-store 회원가입 **버튼** 자체가 config 에러로 끝난다는 뜻이지만, **이 티켓의 결론은 여기에 의존하지 않는다**(소비자는 로그인 페이지의 "회원가입" 링크로 IAM 가입에 도달한다). 별도 확인 필요.

---

# Acceptance Criteria

- [x] **AC-0 (반경 실측 — 본체)** — 위 "실측 결과" 참조. (1) 폼=`SignupUseCase`, 소셜=`SocialSignupUseCase` **둘 다 탄다**. (2) accounts/credentials = `fan-platform`, 토큰 = 폼 `fan-platform` / 소셜 `ecommerce`(계정 행과 모순). (3) 반경은 8 파일 10 곳 — 가입 2 + **조회 6** + 상수 javadoc. 피해자 = ecommerce 소비자(조용한 데이터 오스코핑), fan 은 우연히 정답. **"stale 주석 = 결함" 속단 회피**: BE-229 스코프 전문을 읽어 *"약속 자체가 BE-229 것이 아니었다"* 를 확인했다.
- [x] **AC-1** — 오배정 실재 확인. 수정은 **국소가 아님**(조회 6 곳 동반 + 로그인 cross-tenant fail-closed + 소비자 tenant 의미 미정) ⇒ **`TASK-BE-507` 로 분리**, 정확한 범위와 D1 결정 게이트를 남겼다.
- [x] **AC-2 (stale 주석 정정)** — `until TASK-BE-229` 를 달고 있던 **10 곳 전부** 정정(가입 2 + 조회 6 + `TenantId` javadoc + `UpdateLastLoginUseCase` 의 이벤트 주석). 각 사이트는 "FAN_PLATFORM 은 해석된 tenant 가 아니라 컴파일타임 상수" 라는 사실만 남기고, 전말은 `TenantId.FAN_PLATFORM` javadoc 에 1 회 기술.
- [x] **AC-3 (커버리지 무손실)** — 주석/javadoc 전용 변경, **코드 동작 0 변경** ⇒ fan 가입/로그인 회귀 없음. `compileJava` + account-service 테스트 그린으로 확인.

---

# Related Specs

- `projects/iam-platform/apps/account-service/.../application/service/SignupUseCase.java:35` · `SocialSignupUseCase.java:33`
- `.../application/service/{ProfileUseCase,AccountStatusUseCase,VerifyEmailUseCase,SendVerificationEmailUseCase,UpdateLastLoginUseCase}.java` — fan 고정 조회 6 곳
- `.../infrastructure/persistence/AccountQueryPortImpl.java:48-50` — 같은 잔여물의 **선례 수정**(TASK-BE-357)
- `projects/iam-platform/apps/auth-service/.../infrastructure/security/SavedRequestTenantResolver.java` — 버려지고 있는 tenant 힌트
- `projects/iam-platform/apps/auth-service/.../infrastructure/security/CredentialAuthenticationProvider.java:75-84` — cross-tenant 조회 + 모호성 fail-closed
- `projects/ecommerce-microservices-platform/apps/web-store/e2e/fixtures/iam-consumer-seed.sql:39` — 프로덕션 도달 불가 픽스처
- `projects/iam-platform/tasks/done/TASK-BE-229-auth-jwt-tenant-claim.md` — 주석이 가리킨 "완료된" 선행(스코프 전문 확인)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — `tenant_id`
- (BE-507 에서 갱신 예정) `specs/contracts/http/account-api.md` · `specs/contracts/http/internal/auth-to-account.md`

---

# Edge Cases

- **`fan-platform` 이 의도된 기본값일 가능성** — 실측으로 배제. account-service 는 fan 태생이 맞지만, **BE-357 이 이미 같은 상수를 "잔여물"로 판정하고 한 곳을 걷어냈다**. 의도된 설계라면 그 수정이 존재할 수 없다.
- **폼 vs 소셜 경로 차이** — 실재한다. 소셜은 **토큰만** client tenant 를 받아 계정 행과 어긋난다(폼은 양쪽 다 fan). 반경이 다르므로 BE-507 은 두 경로를 각각 배선해야 한다.

# Failure Scenarios

- **F1 — "stale 주석" 을 결함으로 속단** → 회피함. BE-229 스코프 전문을 읽었고, 결함 판정의 근거는 주석이 아니라 **오배정의 실재**(코드 체인 + IT + BE-357 선례)다.
- **F2 — 동적 tenant 로 바꿨더니 기존 fan 소비자가 깨진다** → 이 티켓은 코드 동작을 0 변경했으므로 발생 불가. BE-507 이 additive/net-zero 로 다뤄야 할 리스크로 이관(특히 로그인 모호성 fail-closed).

# Test Requirements

- AC-0 의 증거 = 코드 체인 + **CI 에서 도는 Testcontainers IT**(`AccountSignupIntegrationTest:90`). 라이브 왕복은 Docker 백엔드 행으로 미실행(위 주석).
- AC-2/AC-3 = 주석 전용 ⇒ 기존 테스트 전량 그린이 곧 무손실 증거.

# Definition of Done

- [x] AC-0 실측 기록 (본 문서).
- [x] AC-2 주석 정정 10 곳.
- [x] AC-1 → `TASK-BE-507` 분리(결정 게이트 포함).
- [ ] `projects/iam-platform/tasks/INDEX.md` done entry (머지 후 close chore).

---

# Provenance

발굴 2026-07-13 — `TASK-MONO-381` 구현 실측 중. 실측 2026-07-13.

**모양이 이 저장소가 반복해 배운 것과 같다: 선언↔진실 드리프트** — 그리고 이번에도 **선행 문서의 숫자는 출처가 아니라 가설이었다**. 티켓은 "두 가입 경로"라 적었지만 전수 grep 은 **10 곳**을 냈고, 그 6 곳이 조회 경로라는 사실이 **수정 범위 판정을 뒤집었다**(`feedback_recount_population_dont_inherit_scope`). 결정적 반증 방지 장치는 **BE-229 스코프 전문을 읽은 것**이었다 — 주석이 지목한 티켓이 그 약속을 한 적이 없음을 확인하지 않았다면, "BE-229 가 의도적으로 제외했다"는 반대 결론으로 새기 쉬웠다.

분석=Opus 4.8 / BE-507 구현 권장=Opus (2 서비스 + 계약 + 로그인 스코프 결정).
