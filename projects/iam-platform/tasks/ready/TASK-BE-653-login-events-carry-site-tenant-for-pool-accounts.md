# Task ID

TASK-BE-653

# Title

Login and lock events of a consumer-pool account are recorded under `consumer-pool`, not the site the consumer logged in to — so a site tenant's IAM metrics and security rules never see its own consumers

# Status

ready

# Owner

backend

# Task Tags

- code
- event
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (event-contract change across auth-service → security-service, with a data question about rows written before the change)
> Drafted 2026-10-24 UTC from `TASK-MONO-811` AC-0. Owner decision 2026-10-24 (selection window): 811 counts by the tenant already recorded and states the pool exclusion; **site attribution is this ticket**.

# Dependency Markers

- 출처: `TASK-MONO-811` AC-0 (IAM overview metrics). 811 의 `loginSuccess` · `loginFailure` · `accountLocked` 는 이 티켓이 닫힐 때까지 소비자 풀 계정을 사이트 테넌트에서 뺀다.
- 관련: ADR-MONO-078 (one consumer login across fan and store) · `TASK-MONO-744` (데모 소비자 전원 풀 계정).

# Goal

When a consumer-pool account logs in to a site (or is locked), the security record says **which site**, so per-site views (metrics, and any per-tenant security rule) can count it there.

# Scope

## In Scope

- **AC-0 measurement:**
  - `CredentialAuthenticationProvider.java:401` and `OAuthLoginUseCase` publish `tenantId = credential.getTenantId()`. That value is `consumer-pool` for a pool account. List every publisher of `auth.login.*` and `account.locked` with the tenant it puts on the event.
  - Which security-service rules read `tenant_id` (VelocityRule, DeviceChangeRule, …)? What would a site tenant change for each?
  - Is a site-level lock (`consumer_site_memberships.status=LOCKED`) emitted as any event? Today it is not shown to be.
  - The spec conflict 811 found: `auth-events.md:50` (account's tenant) vs `:158` (token's tenant). 811 aligned `:158` to the code.
- **Design (owner decision after AC-0):** an additive field (e.g. `siteTenantId`, the initiating client's tenant) vs re-defining `tenantId` for pool accounts. Re-defining changes what every consumer already relies on, so it needs a stated reason.
- Implementation in auth-service (publish), security-service (store + migration), and `TASK-MONO-811`'s metrics query (count by site).

## Out of Scope

- Back-filling rows written before the change. The old rows have no site, and the contract must say from when the counts are by site.

# Acceptance Criteria

- [x] **AC-0** — the list above, file:line.
  - → **닫힘 2026-10-24 UTC** (origin/main `60d4d96bd`). 측정은 Explore(opus)가 했다. 핵심 둘은 기안자가 원문으로 확인했다(`CredentialAuthenticationProvider.java:382` 에서 `clientTenant` 를 이미 계산함 · `UpdateLastLoginUseCase.java:79` 가 `findById(tenantId, accountId)` 로 찾음).
  - **발행자:** Kafka 로 내보내는 곳은 `OutboxAuthEventPublisher` 하나다(:78 · :96 · :174).
    - 폼 로그인에서 자격을 찾으면 `credential.getTenantId()` 다(`CredentialAuthenticationProvider.java:401-403`). 풀 계정이면 `consumer-pool` 이다.
    - 자격을 못 찾으면 `clientTenant ?: fan-platform` 이다(:389).
    - 소셜 로그인은 `account.tenantId()` 다(`OAuthLoginUseCase.java:107-112`).
    - `account.locked` 는 `account.getTenantId()` 다(`AccountEventFactory.java:77`). 자동 잠금도 이 경로를 타고, 그 요청 본문에 사이트가 없다(security `AccountServiceClient.java:144-151`).
  - **사이트를 알 수 있나:** 폼 로그인은 알 수 있다(`clientTenant`, 풀 자격은 소비자 사이트 client 에서만 고른다 — :279-302).
    - 소셜 로그인은 client 가 없으면 `fan-platform` 으로 메워져(`SavedRequestTenantResolver.java:161-173`) 팬 client 와 구별되지 않는다.
    - 사이트가 없는 풀 로그인이 셋 있다: 초대 수락(:212-219) · client 없음 교차 조회(:239-251) · 콘솔 client 교차 조회(`iam`, :230-236).
  - **tenant_id 를 읽는 것:** Velocity · DeviceChange · GeoAnomaly · ImpossibleTravel 규칙 · PII 마스킹(`PiiMaskingLogJpaRepository.java:47-48`, `account.deleted` 의 테넌트로 거른다) · account-service last-login(`UpdateLastLoginUseCase.java:79`).
    - tenantId 를 재정의하면 넷 다 쪼개지고, 🔴 마스킹 누락 · 🔴 last-login no-op 이 된다.
    - iam 밖 구독자는 0 이다.
  - **사이트 잠금**(멤버십 LOCKED)은 이벤트를 내지 않는다(`SiteMembershipLockUseCase.java:26-31`, `multi-tenancy.md:568`).
  - **신규 계정은 이미 사이트별로 센다**(`AccountMetricsQueryService.java:76-79`, 멤버십 `consented_at`).
  - 기존 결함, 기록만: `SecurityQueryService.java:41,72` 가 tenant 를 `fan-platform` 으로 하드코딩한다.
  - 스펙: `auth-events.md:50 · :158 · :170` 은 이제 서로 맞는다(계정 테넌트, 사이트는 후속). `admin-api.md:583-585` 는 «풀 제외 = 알려진 공백» 이다.
- [x] **AC-1** — owner decision recorded (field shape · what `*`-tenant views count).
  - → **소유자 결정 2026-10-24 UTC (선택창):**
    - **필드 추가 `siteTenantId`.** 시작 client 의 사이트이고, 풀 자격일 때만 채운다. 사이트가 없으면 null 이다. `tenantId` 는 바꾸지 않는다.
    - `login_history` 에 컬럼과 인덱스를 추가한다.
    - 지표에서 사이트 X 는 `tenant_id = X` 또는 `site_tenant_id = X` 다.
    - 탐지 규칙 · 마스킹 · last-login 은 무변경이다. 옛 행은 백필하지 않고, 계약에 시작 날짜를 적는다.
  - **`accountLocked` — 지금대로, 계약에 명시.** 계정 전체 잠금은 사이트 소속이 아니므로 `consumer-pool` 에 남는다. 사이트 뷰의 `accountLocked` 는 풀 계정을 세지 않는다. 이 티켓은 로그인 두 키(`loginSuccess` · `loginFailure`)만 다룬다.
  - 결정에서 따라 나오는 것 (따로 묻지 않음):
    - `*` 뷰는 필터가 없어 행을 한 번씩 센다(중복 없음).
    - `consumer-pool` 뷰는 `tenant_id='consumer-pool'` 그대로다(풀 로그인 전부).
    - 그래서 풀 로그인 한 건은 `consumer-pool` 뷰와 그 사이트 뷰에 함께 나타난다. 계약에 그렇게 적는다.
- [ ] **AC-2** — a pool account's login to site X is counted in X's `loginSuccess`. A non-pool account's count is unchanged (control group). Another site's login is not counted (isolation).
- [ ] **AC-3** — the IAM metrics contract (`admin-api.md` § GET /api/admin/metrics) drops the «pool excluded» line and states the date from which pool logins count by site.

# Related Specs

- `projects/iam-platform/specs/contracts/events/auth-events.md` · `account-events.md`
- `projects/iam-platform/specs/features/multi-tenancy.md` § consumer pool
- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md`

# Related Contracts

- `auth.login.attempted` / `auth.login.failed` / `auth.login.succeeded` · `account.locked`
- `projects/iam-platform/specs/contracts/http/admin-api.md` § GET /api/admin/metrics

# Edge Cases

- A pool consumer who belongs to two sites logs in once. Which site gets it? Probably the site of the initiating client; AC-0 confirms that every login has one.
- A login with no initiating client (direct API, tests) has no site. Fall back to the account's tenant, as today.

# Failure Scenarios

1. The new field is added to the event but security-service keeps counting `tenant_id`. Nothing changes, and the tests pass on non-pool fixtures. AC-2 uses a pool fixture for this reason.
2. `tenantId` is re-defined in place. VelocityRule then groups a pool consumer's failures per site instead of per account tenant, and its thresholds change meaning silently.
