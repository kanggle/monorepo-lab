# Task ID

TASK-BE-615

# Status

review

# Title

전역 소비자 계정 3단계 — 풀 계정의 authorize · **토큰 테넌트 = client** · 세션 게이트 · 로그아웃 범위 (`ADR-MONO-078` A)

# Owner

iam-platform

# Task Tags

- auth-service
- oidc
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (세션·토큰 발급 — 잘못되면 다른 사이트 권한이 실린다)

---

# Dependency Markers

- **선행**: `TASK-MONO-742` · `TASK-BE-614`
- **후속**: `TASK-BE-616`(동의 화면이 이 흐름에 끼어든다) · `TASK-MONO-743`

# Goal

풀 계정으로 로그인한 사람이 팬·스토어 어느 client 로 와도 **비밀번호 재입력 없이** 그 client 테넌트의 토큰을 받게 한다. `sub` 는 같고 `tenant_id` 는 client 의 테넌트, 역할은 그 사이트 역할만.

# Scope

## In Scope

- `CredentialAuthenticationProvider` — 소비자 client 로그인 조회에 풀 계정 추가(기존 사이트 계정과 공존하는 동안의 선택 규칙 포함)
- `TenantClaimTokenCustomizer` — 풀 principal 이면 `tenant_id` = client 테넌트, 역할 = 그 사이트 역할만(`TASK-MONO-742` AC-5)
- `AuthorizeSessionTenantGate` — 풀 principal 은 소비자 client 사이에서 재인증하지 않는다(멤버십이 없으면 `TASK-BE-616` 동의로)
- 로그아웃 범위

## Out of Scope

- 동의 화면 자체(`TASK-BE-616`), 소셜(`TASK-BE-617`)

# 착수 시 결정 (2026-10-01 UTC)

- **소유자 결정 — 로그아웃 범위 = 전체**(소유자 «진행», 2026-10-01): 어느 소비자 사이트에서 로그아웃하든 **IAM 브라우저 세션도 끝난다** — 공용 기기에서 한 사이트만 로그아웃하고 다른 사이트 로그인이 살아 있는 상태를 만들지 않는다. 이미 발급된 다른 사이트의 앱 세션(그 사이트의 토큰)은 그 사이트의 만료·자체 로그아웃을 따른다 — 이 범위를 시험으로 고정하고 사용자 문구에 적는다(AC-4).
- **소유자 결정 — 운영자 생성 확인은 옛 규칙 유지**(같은 메시지): admin-service `CreateOperatorUseCase` 의 «대상 테넌트에 계정이 있나» 확인은 **풀 멤버를 포함하지 않는다**(`TASK-BE-614` 가 넓힌 조회를 이 확인만 옛 범위로). 직원 계정 규칙은 `ADR-MONO-080` 후보(`TASK-MONO-746`)의 몫이다(AC-8).
- 🔴 **정정 — 플래그를 켜는 것은 이 티켓이 아니라 `TASK-BE-616`** (착수 시 정정): 이 티켓만으로는 **첫 방문 동의 화면이 없다**. 켜면 스토어에서 풀로 가입한 사람이 팬에 처음 갈 때 들어갈 길이 없다. 이 티켓은 «풀 계정이 **이미 멤버인** 사이트로 재입력 없이 토큰을 받는다» 까지를 만들고, 멤버가 아닌 사이트에서는 **토큰을 만들지 않는다**(616 의 동의 화면이 그 자리를 채운다). `iam.consumer-pool.enabled` 는 기본 꺼짐 그대로 둔다(AC-9).
# Acceptance Criteria

- [ ] **AC-1** — 풀 계정으로 팬 로그인 → 스토어 authorize: 폼 없이 토큰, `sub` 동일, `tenant_id=ecommerce`, 역할에 `FAN` 이 **없다**(역할 평탄화 금지).
- [ ] **AC-2** — 🔴 **대조군**: 묶이지 않은 **기존 사이트 계정** 세션은 지금처럼 재인증한다(`TASK-BE-605` 동작 유지) — 풀 계정만 통과한다.
- [ ] **AC-3** — 콘솔: 풀 계정 세션으로 콘솔에 가면 `TASK-BE-610` 규칙 그대로(운영자 자격이 없으면 들어가지 못한다). 콘솔 로그인의 이메일 교차 조회에서 같은 이메일이 팬·스토어에 **둘 다** 있어 나던 `LOGIN_TENANT_AMBIGUOUS` 가 풀 계정에서 어떻게 되는지 정하고 시험으로 고정(`ADR-MONO-078` D5 콘솔 칸).
- [ ] **AC-4** — 로그아웃 범위(한 사이트 / 전체 IdP 세션)를 정하고 기록, 시험으로 고정(`ADR-MONO-078` § 새로 생기는 위험).
- [ ] **AC-5** — 갱신 토큰: 스토어에서 받은 refresh 로 팬 토큰을 받지 못한다(`TOKEN_TENANT_MISMATCH` 규칙 유지).
- [ ] **AC-6** — `SsoTenantGateIntegrationTest` 등 기존 SSO 시험이 초록이고, 바뀐 기대값은 하나하나 이유를 적는다.
- [ ] **AC-8** — `CreateOperatorUseCase` 의 대상 테넌트 계정 확인이 풀 멤버를 **포함하지 않는다** — 대조 시험: 풀 가입 쇼핑객 이메일로 `ecommerce` 운영자 생성 → 지금처럼 «계정 없음» 거절. 콘솔 계정 운영 목록(§ 5)은 계속 풀 멤버를 포함한다.
- [ ] **AC-9** — `iam.consumer-pool.enabled` 기본값이 여전히 `false` 다. 이 티켓의 풀 로그인 경로는 풀 자격이 있을 때만 타므로 플래그와 무관하게 안전하다는 것을 시험으로 보인다(풀 자격 없는 기존 계정의 로그인·SSO·refresh 기대값 무변경).
- [ ] **AC-7 (대조군 — 고치기 전에 먼저 잰다)** — 🔴 **지금 셀러 계정은 스토어에서 쇼핑을 못 하는가.** 코드 읽기로는 그렇다: 셀러 계정은 `account_roles(ecommerce, acct, SELLER)` 를 갖고(`AccountServiceSellerProvisioner.java:196`), 발급 경로가 «저장 역할이 있으면 그것만, 없으면 시드» 라(`TenantClaimTokenCustomizer` `roles = stored.isEmpty() ? seed : stored`) 스토어 토큰이 `["SELLER"]` → web-store `signInCallback` 이 `CUSTOMER` 없음으로 `account_type_mismatch`. 🔴 **실측 아님** — 이 티켓의 구현 **전에** 통합 시험으로 이 동작을 단언해 기록한다(2026-10-01 분석, `ADR-MONO-078` 후속 결정). 풀 principal 의 역할 합치기(시드 ∪ 사이트 역할)를 넣은 뒤, 풀로 옮긴 셀러(`TASK-MONO-745`)의 스토어 토큰이 `["CUSTOMER","SELLER"]` 가 되는 시험은 745 의 AC 다 — 이 티켓은 «고치기 전» 칸만 소유한다.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § SSO(`TASK-MONO-742` 갱신본)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`(`TASK-MONO-742` 갱신본)

# Edge Cases

- 같은 이메일로 풀 계정 + 기존 사이트 계정이 공존 — 로그인 폼에서 어느 쪽이 맞는가.
- 스토어 쇼핑객이 `CUSTOMER` 역할이 없는 상태로 도착 — 스토어 `signInCallback` 이 `account_type_mismatch` 를 낸다. 동의(`TASK-BE-616`)가 역할을 주기 전에 토큰이 발급되지 않게 한다.

# Failure Scenarios

1. 풀 principal 의 저장 역할 전부(FAN+CUSTOMER)가 두 토큰에 다 실린다 — 계약 위반, 사이트 권한 누출.
2. 게이트를 풀 계정만이 아니라 **모든** 소비자 세션에 대해 끈다 — 묶이지 않은 기존 계정이 남의 사이트 토큰을 받는다.

---

# 구현 기록 (2026-10-01 UTC)

## 커밋 순서

1. `test(iam)` — **AC-7 대조군을 발급 변경 전에** 고정: `SellerStoreTokenRolesBaselineTest`(단위 2칸, 로컬 통과) ·
   `SellerStoreTokenRolesBaselineIntegrationTest`(통합). 측정값은 **예측과 같았다**: 셀러(ecommerce 사이트 계정, 저장 역할 `SELLER`)의
   스토어 토큰 `roles == ["SELLER"]`, `CUSTOMER` 없음(저장 역할이 시드를 대체). refresh 도 같다.
2. `feat(iam)` — 구현 + 계약 + 시험. 1 의 두 시험은 변경 후에도 **그대로 초록**(셀러는 풀 계정이 아니라 풀 경로를 타지 않는다).
3. `chore(iam)` — 이 기록.

## 만든 것

**auth-service**
- `CredentialAuthenticationProvider#poolCredentialFor` — 콘솔·풀 테넌트가 아닌 client 에서 그 이메일의 **풀 자격이 있을 때만**
  account-service 에 «이 client 테넌트가 소비자 사이트인가» 를 묻고, 맞으면 풀 자격을 먼저 쓴다. 풀 자격이 없으면 추가 비용은 인덱스 읽기 1회뿐,
  그 뒤 표는 바이트 그대로. 조회 실패는 fail-closed(`AuthenticationServiceException` → `/login?error`). 풀 principal details:
  `tenant_id=consumer-pool`(자격 행의 테넌트 — 상태 조회도 풀 테넌트로), `account_id` = 풀 계정 id.
- `CredentialRepository#findPoolCredentialByEmail` (default 메서드, 풀 테넌트 범위 조회).
- `TenantContext#poolPrincipalMapsTo(clientTenant)` — 풀 principal 을 사상할 client 테넌트의 I/O 없는 사전 필터(콘솔 `iam`·풀 자신 제외). 폼 · 게이트 ·
  세션 테넌트 · 발급자가 공유한다.
- `AuthorizationSessionTenant` — 풀 principal → **요청 client 의 사이트**(trim). claim · refresh 미러 행 비교 · 게이트가 같은 값을 본다.
  `isPoolPrincipal` 추가. 사이트별 principal 은 무변경.
- `TenantClaimTokenCustomizer#customizeForPoolPrincipal` (`authorization_code` · `refresh_token`, 액세스·ID 토큰): `sub` = 풀 계정(기존
  `alignSubToAccountId`), `tenant_id` = 사이트, `tenant_type` = account-service 의 사이트 `tenant_type`, `entitled_domains` = 사이트(기존 fail-soft),
  `roles` = `RoleSeedPolicy.seed(사이트)` ∪ `siteRoles`(시드 먼저, 중복 제거). **ACTIVE 멤버십이 아니면 `invalid_grant`** — 조회 실패도 같다(fail-closed).
  `refuseConsumerPoolTenant`(BE-614) **유지** — 풀 principal 이 사이트로 사상된 뒤에 돌므로 사이트 client 에서는 풀 값을 보지 않고, 콘솔에서는 여전히 문다.
- `AuthorizeSessionTenantGate#consumerSiteServesPoolPrincipal` — 풀 세션 · 콘솔 아닌 client: 소비자 사이트면 통과(멤버든 아니든), 아니면 재인증,
  조회 실패면 재인증. 콘솔은 BE-610 규칙 그대로(세션 테넌트 `consumer-pool` ≠ `iam` → iam 자격 조회).
- `AccountServicePort#getConsumerSiteMembership` + `AccountServiceClient` 구현(200 만 답, 404 포함 나머지는 `AccountServiceUnavailableException`).
  `ConsumerSiteMembershipLookupResult#isActiveMember`.

**account-service**
- `GET /internal/tenants/{tenantId}/consumer-members/{accountId}` — `ConsumerSiteMembershipController` → `GetConsumerSiteMembershipUseCase`.
  항상 200: `{accountId, siteTenantId, consumerSite, siteTenantType, membershipStatus, siteRoles}`. 계정은 `findById(CONSUMER_POOL, id)` 로만 —
  테넌트 없는 조회 신설 없음. `ConsumerSiteMembershipRepository#findSiteRoles(site, account)`(네이티브 SELECT, 그 사이트만).
  플래그와 무관(이미 있는 풀 계정은 플래그와 상관없이 로그인돼야 한다).
- `GET /internal/accounts?excludePoolMembers=true` — 풀 이전 쿼리(사이트 자기 계정만). 기본 `false` → BE-614 동작 그대로.

**admin-service**
- `AccountServiceClient#searchSiteAccounts` (`excludePoolMembers=true` 를 붙인다) — `CreateOperatorUseCase` 가 이것을 부른다. 콘솔 계정 운영의
  `search` 는 URL 바이트 그대로(파라미터 없음).

**계약 · 스펙** — `auth-to-account.md`(새 엔드포인트 절) · `admin-to-account.md`(`excludePoolMembers` 행) · `multi-tenancy.md § 소비자 계정 풀 § 4`
(콘솔 교차 조회의 실제 결과 · 멤버십 없는 사이트의 616 전 결과 · B2B client · 로그아웃 범위) · `account-service/architecture.md`(내부 엔드포인트 한 줄).
`platform/contracts/jwt-standard-claims.md` 는 **고치지 않았다** — 새 claim 없음, 뜻은 742 갱신본 그대로 구현했다.

## 계약이 남긴 선택 — 내가 정한 것

| # | 선택 | 정한 값 | 이유 |
|---|---|---|---|
| D-1 | 멤버십 없는 사이트(616 전)의 결과 | authorize 는 **통과(코드 발급)**, **토큰 엔드포인트가 `invalid_grant`** (`error_description` = «no active consumer-site membership for this site») | 재인증으로 답하면 루프다 — 그 client 의 폼이 풀 자격을 먼저 골라 같은 세션 → 같은 게이트. authorize 에서 `access_denied` 리다이렉트를 직접 만들려면 게이트(필터)가 `redirect_uri` 검증을 SAS 밖에서 다시 해야 한다 — 그 대신 «토큰을 만드는 유일한 곳»(발급자)에서 막았다. refresh 도 같은 곳을 지나므로 `LEFT` 가 되면 다음 갱신부터 막힌다. 616 의 동의 화면은 게이트 자리(코드 발급 전)에 들어간다 |
| D-2 | 엔드포인트 모양 | `GET /internal/tenants/{site}/consumer-members/{accountId}`, **항상 200**, 사이트 판정(`consumerSite`) · `siteTenantType` 동봉 | 사이트가 범위의 첫 인자(형제 `/internal/tenants/{t}/accounts/{id}/roles` 와 같은 결). «아니오» 를 200 본문에 담아 404 = «엔드포인트 없음»(옛 account-service)으로만 남게 했다 → auth 는 404 를 실패로 읽고 fail-closed. 한 호출로 폼(사이트 판정)·게이트(사이트 판정)·발급(멤버십·역할·tenant_type)을 다 답한다 — 새 에러 코드 없음 |
| D-3 | 「소비자 사이트」 판정 위치 | account-service `Tenant.isConsumerSite()`(BE-614 D-2) — auth 는 묻기만 | client 행의 `tenant_type` 은 `B2C`(V0012)·`B2C_CONSUMER` 가 섞여 있어 판정 근거가 못 된다. `TenantTypeResolver` 는 404 를 `B2C_CONSUMER` 로 폴백해 «모르는 테넌트 = 소비자 사이트» 가 된다 |
| D-4 | B2B client(wms·erp…)의 풀 세션 · 풀 자격 | 게이트 = **재인증**, 폼 = 풀 자격을 **보지 않는다** | 「소비자 client」 는 로그인 표에서 «콘솔이 아닌 모든 client» 지만, 풀-먼저를 B2B 에 적용하면 풀 쇼핑객이면서 wms 직원인 사람이 wms 에 못 들어간다 |
| D-5 | 콘솔 + 풀 계정 (AC-3, 계약 § 4 마지막 줄) | 교차 조회는 풀 자격 **하나로 풀려 로그인된다**(`LOGIN_TENANT_AMBIGUOUS` 아님). 세션 테넌트는 `consumer-pool` 그대로(콘솔로 사상 안 함) → 발급자가 **거절**(`invalid_grant`, BE-614 게이트). 풀 세션으로 콘솔 SSO 도 BE-610 규칙 그대로(iam 자격 있으면 재인증, 없으면 통과 → 토큰 거절) | D1 «풀 계정은 운영자 권한을 주지 않는다». 콘솔 토큰을 사이트로 만들 근거가 없고(어느 사이트?), `iam` 으로 만들면 소비자가 콘솔 테넌트 토큰을 받는다. 풀 계정의 운영자 경로는 `ADR-MONO-080` 후보(`TASK-MONO-746`) |
| D-6 | 로그아웃 (AC-4, 소유자 결정 «전체») | **코드 변경 없음** — RP 로그아웃(`/connect/logout`)이 IAM 브라우저 세션을 무효화하고, 그 세션은 브라우저에 하나라 이미 «전체» 다. 시험으로 고정만 | 아래 «사용자 문구». 다른 사이트의 이미 받은 refresh 는 IAM 이 폐기하지 않는다(그 사이트의 만료·자체 로그아웃) — 소유자 결정 문장과 같다 |
| D-7 | 운영자 생성 확인 (AC-8, 소유자 결정 «옛 규칙») | 검색 API 에 `excludePoolMembers` 쿼리 플래그, admin 은 새 메서드 `searchSiteAccounts` | 콘솔 계정 운영 목록(§ 5)은 그대로 풀 멤버 포함. 플래그와 무관하게 좁힌다 |
| D-8 | 풀 principal 의 로그인 이벤트 테넌트 | `consumer-pool`(자격 행 = 계정의 테넌트) | 기존 규칙(«계정 자신의 테넌트», BE-259) 그대로 둔 결과. security-service 가 이 값을 어떻게 세는지는 이 티켓에서 바꾸지 않았다 — 아래 «후속» |

## 사용자 문구 (AC-4 — 로그아웃 범위)

> 한 사이트에서 로그아웃하면 **통합 로그인(IAM)에서도 로그아웃**됩니다. 다른 사이트로 가면 다시 로그인 화면이 나옵니다.
> 이미 열려 있던 다른 사이트의 화면은 그 사이트의 로그인 유지 시간이 끝나거나 그 사이트에서 로그아웃할 때까지 유지됩니다.

## 바뀐 기대값 (AC-6) — 하나하나

- **기존 SSO · 로그인 · refresh 통합 시험** (`SsoTenantGateIntegrationTest` · `CrossTenantLoginRefreshIntegrationTest` · `FormLoginIntegrationTest` …): **기대값 변경 0**
  (파일 diff 0). 풀 자격이 없으면 풀 경로를 타지 않는다.
- **기존 단위 시험**: 기대값 변경 0. 바뀐 것은 **배선뿐** —
  `AuthorizeSessionTenantGateTest`(생성자에 `AccountServicePort` mock 추가) · `AccountSearchControllerSliceTest`(컨트롤러가 6-인자 `search` 를 부르므로 스텁에 `eq(false)` 추가) ·
  admin `CreateOperatorUseCaseTest` · `OperatorAdminIntegrationTest` · `OperatorAdminScopeConfinementIntegrationTest`(스텁 대상 `search` → `searchSiteAccounts`, 값 그대로).
- 🔴 **의미가 바뀐 칸 — `TenantClaimConsumerPoolRefusalTest`(BE-614) 의 `authorizationCode_poolPrincipal_refused` · `refresh_poolPrincipal_refused` ·
  `idToken_poolPrincipal_refused`**: 여전히 초록이지만 **거절 이유가 바뀌었다**. 그 client 가 `ecommerce` 라 이제 풀 principal 이 사이트로 사상되고,
  mock 의 멤버십 조회가 `null` 을 돌려 «멤버십 없음» 으로 거절된다 — BE-614 발급자 게이트까지 가지 않는다. 그 게이트가 아직 무는 자리(콘솔 client)는
  새 칸 `TenantClaimPoolPrincipalTest#consoleClient_poolPrincipal_refusedByIssuerGate` 가 고정한다(설명에 `consumer-pool` 포함 단언). BE-614 의 bite 기록
  («게이트를 끄면 7 중 4 실패») 은 **이제 재현되지 않는다** — 사이트 client 칸 3개는 615 의 거절이 먼저 막는다.

## 게이트 (각각 단독 실행 · `cmd > log 2>&1; echo rc=$?`)

| 게이트 | rc | 비고 |
|---|---|---|
| `:projects:iam-platform:apps:auth-service:check` | 0 | `@Tag("integration")` 은 `check` 가 제외 — 새 IT 2개(`ConsumerPoolSsoIntegrationTest` · `SellerStoreTokenRolesBaselineIntegrationTest`)는 **컴파일만** |
| `:projects:iam-platform:apps:account-service:check` | 0 | 새 IT `ConsumerSiteMembershipLookupIntegrationTest` 컴파일만 |
| `:projects:iam-platform:apps:admin-service:check` | 0 | |
| 통합 시험 3개 | ⚪ 로컬 미실행 | 이 호스트에 Docker 없음. **CI 가 첫 실측** |
| `git add` 후 `scripts/check-index-queue-drift.sh` | 0 | |
| `scripts/check-task-id-collision.sh` | 0 | |
| `scripts/check-walkthrough-ledger-drift.sh` | 0 | |
| `scripts/check-jwt-claims-registry.sh` | 0 | 새 claim 없음 |
| `scripts/check-error-code-registry.sh` | 0 | 새 에러 코드 없음(거절은 `invalid_grant` + 설명 문장) |
| `scripts/check-internal-caller-addresses.sh` | 0 | 새 내부 호출(auth → account) |
| `scripts/check-flyway-version-collision.sh` | — | 마이그레이션 추가 없음 → 대상 아님 |

새 단위·슬라이스 시험(로컬 실행·통과 수): auth `TenantClaimPoolPrincipalTest` 11 · `AuthorizeSessionTenantGatePoolTest` 7 · `AuthorizationSessionTenantPoolTest` 4 ·
`CredentialAuthenticationProviderPoolTest` 7 · `SasRefreshTokenAuthenticationProviderTest` +3(27) · `SellerStoreTokenRolesBaselineTest` 2;
account `GetConsumerSiteMembershipUseCaseTest` 6 · `ConsumerSiteMembershipControllerSliceTest` 3 · `AccountSearchExcludePoolMembersTest` 4 · `AccountSearchControllerSliceTest` +1(9);
admin `CreateOperatorUseCaseTest` +1(22) · `AccountServiceClientUnitTest` +1(16).

## bite (구현 커밋 위에서, 되돌린 뒤 `git diff` 비어 있음 · 재실행 초록 확인)

| 끈 것 | 돌린 시험 | 결과 |
|---|---|---|
| (a) 풀 → 사이트 사상: `AuthorizationSessionTenant.mapsPoolPrincipalTo` 를 `false` 로(공유 술어 — 발급자 · 게이트 · 세션 테넌트가 같이 꺼진다) | `TenantClaimPoolPrincipalTest` · `AuthorizeSessionTenantGatePoolTest` · `AuthorizationSessionTenantPoolTest` · `SasRefreshTokenAuthenticationProviderTest` | **49 중 17 실패** (9/11 · 4/7 · 2/4 · 2/27). 살아남은 칸 = 콘솔 칸 · 사이트별 대조군 · 기존 refresh 칸 — 사상과 무관한 칸들 |
| (b) «멤버십 없으면 토큰 없음»: `customizeForPoolPrincipal` 의 `!isActiveMember()` 조건 제거 | `TenantClaimPoolPrincipalTest` · `TenantClaimConsumerPoolRefusalTest` | **16 중 3 실패** — 정확히 거절 칸 3개(멤버십 없음 · LEFT · B2B). BE-614 5칸은 초록(mock 조회가 `null` → 여전히 거절) |

## AC

- ⚪ **AC-1** — 단위로 ✅: 같은 풀 principal 이 스토어 client → `tenant_id=ecommerce` · `roles=[CUSTOMER]`(FAN 없음), 팬 client → `fan-platform` · `[FAN]`,
  `sub` 동일(`TenantClaimPoolPrincipalTest`), 게이트 통과(`AuthorizeSessionTenantGatePoolTest`). 브라우저 경로 끝까지(팬 로그인 → 스토어 authorize 가 폼 없이 코드 →
  두 토큰 디코드) = `ConsumerPoolSsoIntegrationTest#poolLoginOnFan_thenStoreWithoutForm_sameSub_siteTenant_noFlattening` — **로컬 미실행, CI 가 첫 실측**.
- ✅ **AC-2** — 사이트별 세션은 지금처럼 재인증: `AuthorizeSessionTenantGateTest` 15칸 기대값 무변경 + `AuthorizeSessionTenantGatePoolTest#siteSession_otherConsumerSite_stillReauthenticates`
  (풀 조회 0회 단언). `SsoTenantGateIntegrationTest` 는 파일 diff 0 — 통합 실측은 CI.
- ⚪ **AC-3** — 정한 것: D-5. 단위 ✅(`CredentialAuthenticationProviderPoolTest#console_poolOnlyEmail_resolvesToThePoolCredential` · `#console_twoSiteAccounts_stillAmbiguous`
  대조군 · 게이트 콘솔 2칸 · 발급자 콘솔 칸). 통합 2칸(`#poolSession_onConsole_noConsoleToken` · `#consoleFormLogin_poolOnlyEmail_resolvesButNoToken`) **CI 첫 실측**.
- ⚪ **AC-4** — 범위 = 전체(소유자 결정), 문구 위. 시험 `ConsumerPoolSsoIntegrationTest#logoutFromStore_endsIamSession_fanAppSessionFollowsItsOwnExpiry`
  (스토어 `id_token_hint` RP 로그아웃 → 세션 무효 → 팬 authorize `/login` → 팬 refresh 200) — **로컬 미실행, CI 첫 실측**. 🔴 `/connect/logout` 을 MockMvc 로
  모는 시험은 이 저장소에 처음이다 — CI 에서 빨개지면 SAS 로그아웃 검증(sid/principal)부터 볼 것.
- ⚪ **AC-5** — 단위 ✅: 스토어 인가의 풀 principal → 비교값 `ecommerce`, 회전 행·이벤트 `ecommerce`; 미러 행 `fan-platform` → `TOKEN_TENANT_MISMATCH`;
  스토어 refresh 를 팬 client 가 내밀면 `invalid_grant`(client 결속). 사이트별 비교는 `AuthorizationSessionTenant` 의 풀 분기 밖이라 바이트 그대로(기존 24칸 무변경).
  통합 `#refresh_storeTokenNeverYieldsFanToken` **CI 첫 실측**.
- ⚪ **AC-6** — 위 «바뀐 기대값». 기존 SSO 통합 시험은 기대값 변경 0 이지만 **이 호스트에서 실행하지 못했다** — 초록은 CI 가 판정한다.
- ⚪ **AC-7** — 커밋 1(발급 변경 **전**). 단위 2칸 ✅ — 예측대로 `[SELLER]` 만, `CUSTOMER` 없음. 변경 후에도 같은 2칸 ✅(재실행). 통합 칸 **CI 첫 실측**.
  풀로 옮긴 셀러의 `["CUSTOMER","SELLER"]` 는 745 의 AC 다(이 티켓 단위 시험 `siteRoles_unionedWithSeed_onlyThatSite` 가 합집합 규칙만 보인다).
- ⚪ **AC-8** — 단위 ✅: account `AccountSearchExcludePoolMembersTest`(풀 켜짐 + 좁은 질의 → 옛 쿼리, 기본 질의 → 풀 쿼리 — 양쪽 대조군), admin
  `CreateOperatorUseCaseTest#createOperator_poolShopperEmail_refusedAsNoAccount_siteOnlySearch`(사이트 계정 0 → `OperatorAccountNotFoundException`, 넓은 `search` 호출 0),
  `AccountServiceClientUnitTest`(`searchSiteAccounts` 만 `excludePoolMembers=true`). MySQL 실측(풀 가입 쇼핑객 이메일: 좁은 질의 0건 / 기본 질의 1건) =
  `ConsumerSiteMembershipLookupIntegrationTest#operatorCreationSearch_excludesPoolMembers_consoleSearchKeepsThem` **CI 첫 실측**.
- ✅ **AC-9** — `iam.consumer-pool.enabled` 기본값 `false` 그대로(파일 diff 0). 풀 경로는 풀 자격/풀 principal 이 있을 때만: `CredentialAuthenticationProviderPoolTest#consumerClient_noPoolCredential_unchanged`
  (사이트 조회 0) · `TenantClaimPoolPrincipalTest#sitePrincipal_untouched`(멤버십 조회 0) · 게이트 사이트 대조군(풀 조회 0) · 기존 단위 시험 기대값 무변경.

## 후속 · 안 한 것

- **풀 principal 의 로그인 이벤트 테넌트 = `consumer-pool`**(D-8). security-service 의 테넌트별 카운터·잠금 이벤트가 이 값을 받는다. 자동 잠금
  (`account.locked` → account-service `/lock` 에 테넌트 헤더)과의 상호작용은 이 티켓에서 재지 않았다 — 616 이 플래그를 켜기 전에 확인할 것.
- 멤버십 조회 호출 수: 풀 principal 의 발급 1회당 액세스 + ID 토큰 = 2회(refresh 도). 캐시하지 않았다(멤버십 `LEFT` 가 즉시 반영되는 쪽을 택함).
- 풀 계정의 상태 전이 · `consumer_site_roles` 쓰기 표면은 여전히 없다(BE-618 / MONO-745).
- 동의 화면은 616 — D-1 의 «코드는 나가고 토큰이 거절» 자리를 그 화면이 대신한다.

---

## 검토 (2026-10-01 UTC · 분석=Opus 5.5 — 구현 에이전트와 별개)

- **멤버십 없는 사이트 = 인가 통과 → 토큰 단계 `invalid_grant`**(D-1): 재인증으로 답하면 폼 로그인이 다시 풀 자격을 골라 같은 게이트로 돌아오는 **무한 반복**이 된다 — 그래서 반복 없는 쪽을 택한 판단에 동의한다. 플래그가 꺼져 있어 운영에는 닿지 않고, `TASK-BE-616` 의 동의 화면이 이 자리를 대신한다.
- **콘솔 + 풀 자격**: 교차 조회가 풀 행 하나로 풀려 로그인은 되지만, 세션 테넌트가 `consumer-pool` 이라 콘솔 토큰은 `TASK-BE-614` 발급자 게이트가 거절한다 — 풀 계정의 콘솔 사용은 `ADR-MONO-080` 몫이라는 D1 과 일치한다.
- **`TASK-BE-614` 의 «bite 4/7» 은 그 날짜의 사실이다**: 이 티켓 이후 그 시험의 풀 칸 셋은 «멤버십 없음» 거절이 먼저 막는다(같은 결과, 다른 장치). 614 의 기록을 고치지 않는다 — 발급자 게이트는 이제 콘솔 경로 시험(`TenantClaimPoolPrincipalTest#consoleClient_poolPrincipal_refusedByIssuerGate`)이 문다.
- **후속(이 티켓 밖)**: 풀 principal 의 로그인 이벤트가 `tenant_id=consumer-pool` 을 싣는다 — security-service 자동 잠금이 그 값으로 account-service 를 부를 때의 동작은 측정하지 않았다(계정 행이 `consumer-pool` 에 있으므로 맞물릴 가능성이 높지만 미측정). `TASK-BE-616` 착수 전에 확인한다.
- **배포 순서**: account-service 가 auth-service 보다 먼저이거나 함께 — 거꾸로면 풀 principal 만 토큰을 못 받는다(기존 계정 무영향).
