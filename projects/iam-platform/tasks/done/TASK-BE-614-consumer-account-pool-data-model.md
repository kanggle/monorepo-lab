# Task ID

TASK-BE-614

# Status

done (2026-10-01 UTC — PR #4089 squash `98e6c6dbe` · 통합 시험은 CI 실측, § CORRECTION)

# Title

전역 소비자 계정 2단계 — 소비자 계정 **풀** 데이터 모델 + 새 가입은 풀로 (`ADR-MONO-078` A)

# Owner

iam-platform

# Task Tags

- account-service
- auth-service
- migration

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (UNIQUE 제약·마이그레이션 — 기존 볼륨에서 깨지면 기동 실패)

---

# Dependency Markers

- **선행**: `TASK-MONO-742`(저장 모양 결정 AC-4 · 이벤트 결정 AC-3)
- **후속**: `TASK-BE-615`(로그인 + 플래그 켜기) · `TASK-BE-616` · `TASK-MONO-743` · `TASK-BE-618`(기존 한 사이트 계정의 같은 id 이동 — 이 티켓에서 분리)

# Goal

`TASK-MONO-742` 가 정한 저장 모양으로 소비자 계정 풀을 만들고, 소비자 client(팬·스토어)에서 새로 가입하는 사람이 **풀 계정**으로 태어나게 한다. 기존 사이트별 계정은 손대지 않는다 — 그 이동은 `TASK-BE-618` 이다.

🔴 **기능 플래그 뒤에, 기본값 꺼짐** (착수 시 정정, 2026-10-01): 이 티켓만 머지되면 새 가입자는 풀에 생기는데 풀 자격으로 로그인하는 경로는 `TASK-BE-615` 가 만든다 — 그 사이 main 에서 **가입한 사람이 로그인을 못 한다**. 그래서 풀 가입 경로는 `iam.consumer-pool.enabled`(기본 `false`) 뒤에 둔다. 꺼져 있으면 동작이 바이트 그대로 옛 규칙이고, 615 가 로그인과 함께 켠다. 실패는 «옛 동작» 쪽으로 떨어져야 한다.

# Scope

## In Scope

- account-service / auth-service 마이그레이션: 풀 계정 · 사이트 멤버십(사이트 테넌트 · 동의 시각) · 사이트별 역할의 저장
- 가입 경로(`SignupPageController` → 계정 생성): 소비자 client 에서 오면 풀로 — **플래그가 켜졌을 때만**
- 사이트 테넌트로 계정을 찾는 표면(`multi-tenancy.md` § 소비자 계정 풀 § 5 — `/internal/tenants/{t}/accounts` 목록·검색, 그것을 쓰는 admin-service 운영자 생성 확인)에 그 사이트 멤버 풀 계정 포함
- `TASK-MONO-742` AC-3 이 고른 이벤트 발행

## Out of Scope

- 로그인·토큰 발급(`TASK-BE-615`), 동의 화면(`TASK-BE-616`), 묶기(`TASK-MONO-743`)
- 기존 한 사이트 계정의 같은 id 이동(`TASK-BE-618`)

# Acceptance Criteria

- [ ] **AC-1** — 새 마이그레이션만 추가한다. 적용된 마이그레이션 파일은 고치지 않는다(Flyway 체크섬 — 기존 볼륨 기동 실패).
- [ ] **AC-2** — 🔴 마이그레이션이 **기존 볼륨**(현재 main 까지 적용된 DB)에 적용된다 — Testcontainers 로 이전 버전까지 올린 뒤 새 버전 적용. 불가하면 ⚪ «못 쟀다, 이유».
- [ ] **AC-3** — 소비자 client 로 가입한 계정은 풀에 생기고, 같은 이메일의 **기존 사이트별 계정이 있어도** 그것과 자동으로 묶이지 않는다(`ADR-MONO-078` D2) — 시험으로.
- [ ] **AC-4** — 콘솔(`iam`) 가입·운영자 계정 경로는 바뀌지 않는다(D1) — 기존 시험 그대로 초록.
- [ ] **AC-6 (구현자 기본값 — 소유자가 뒤집을 수 있다)** — 운영자 측면(셀러 · 셀프 온보딩 운영자)이 붙어 **아직 옮기지 않은** 사이트 계정의 이메일로 소비자 client 풀 가입이 오면 **거절**한다(`multi-tenancy.md` § 소비자 계정 풀 § 3). 대조 시험: 셀러 계정이 있는 이메일로 팬 풀 가입 → 거절, 셀러 계정은 그대로 로그인된다. 🔵 2026-10-01 소유자가 «셀러를 풀에 포함» 을 결정해 이 거절은 **임시**다 — 셀러는 `TASK-MONO-745`, 셀프 온보딩 운영자는 `ADR-MONO-080` 후보(`TASK-MONO-746`)에서 풀로 옮겨지며 그때 사라진다.
- [ ] ~~**AC-7**~~ — **`TASK-BE-618` 로 이관**(착수 시 정정 2026-10-01: Goal 은 «기존 계정 무변경» 인데 이 AC 는 기존 계정 이동을 시험했다 — 기안자의 모순. 이동은 두 DB 에 걸친 별도 작업이다).
- [ ] **AC-8** — 🔴 `iam.consumer-pool.enabled` 가 **꺼져 있으면**(기본) 가입·조회·이벤트가 이 티켓 이전과 같다 — 기존 가입·로그인 시험이 기대값 변경 없이 초록. 켜졌을 때의 동작은 AC-3·AC-6·AC-9·AC-10 이 잰다.
- [ ] **AC-9** — 사이트 테넌트로 계정을 찾는 표면(§ 5)이 그 사이트 멤버 풀 계정을 포함한다: `ecommerce` 목록·이메일 검색에 풀 가입 쇼핑객이 나온다. 🔴 대조군: 멤버십 **없는** 사이트로는 나오지 않는다(풀 가입 팬이 `ecommerce` 목록에 없다).
- [ ] **AC-10** — `account.created` 가 풀 가입 시 **가입한 사이트 테넌트**로 1회 나간다(`tenantId` ≠ `consumer-pool` 단언).
- [ ] **AC-5** — 테넌트 누출 시험(`multi-tenancy.md:380-395` 규칙)이 풀 계정에 대해 무엇을 보장하는지 갱신하고 초록.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md`(`TASK-MONO-742` 갱신본)
- `projects/iam-platform/specs/services/auth-service/data-model.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/events/account-events.md`(`TASK-MONO-742` 갱신본)

# Edge Cases

- 풀 계정과 같은 이메일의 **기존** 사이트 계정이 공존하는 동안 로그인 조회가 어느 쪽을 고르는가 — `TASK-BE-615` 에 넘기되 이 단계의 데이터가 그 구분을 표현할 수 있어야 한다.

# Failure Scenarios

1. UNIQUE `(tenant_id,email)` 를 풀어 기존 사이트 계정과 풀 계정이 **같은 행**으로 섞인다.
2. 신선 볼륨 CI 만 보고 기존 볼륨에서 실패하는 마이그레이션이 머지된다.

---

# 구현 기록 (2026-10-01 UTC)

## 만든 것

- **플래그** `iam.consumer-pool.enabled` (env `IAM_CONSUMER_POOL_ENABLED`, 기본 `false`) — account-service `application.yml`.
  읽는 곳은 한 곳: `infrastructure/config/ConsumerPoolFlagProperties` → 포트 `application/port/ConsumerPoolFlag`.
  분기하는 곳은 `application/service/ConsumerAccountPool`(`signupGoesToPool` · `lookupsIncludePoolMembers`) 뿐이고,
  그것을 `SignupUseCase` · `TenantAccountQueryUseCase` · `AccountSearchQueryService` 가 묻는다.
  🔵 auth-service · admin-service 는 이 티켓에서 **분기하지 않는다** — 플래그를 두지 않았다(아래 «auth-service»).
- **마이그레이션** (새 파일만 — AC-1): `V0029__seed_consumer_pool_tenant.sql`(테넌트 행) ·
  `V0030__create_consumer_site_memberships_and_roles.sql`(두 테이블). 직전 최고 = V0028 확인. H2/테스트 미러 트리는 없다
  (account-service 테스트 리소스는 `application-test.yml` 하나, 시험은 MySQL Testcontainers).
- **도메인**: `TenantId.CONSUMER_POOL` · `isConsumerPool()`, `Tenant.isConsumerSite()`, `domain/consumerpool/ConsumerSiteMembership(+Status)`,
  포트 `ConsumerSiteMembershipRepository`, `TenantRepository.findAllByTenantType`, `AccountRepository.findAllInSiteIncludingPoolMembers` ·
  `findByIdInSiteIncludingPoolMembers`.
- **가입**(`SignupUseCase`): 플래그 ON ∧ 가입 테넌트가 소비자 사이트 → 계정·identity·자격은 `consumer-pool`, 프로필 그대로, 같은 tx 에서
  멤버십 `(account, 사이트, ACTIVE, consented_at = 계정 created_at)`, `account.created` 는 **사이트**로 1회. 플래그 OFF 는 기존 줄이 그대로 돈다.
- **이벤트**: `AccountEventFactory.createdEvent(account, tenantId, …)` 4-인자 추가. 🔴 발견: `OutboxAccountEventPublisher.publishAccountCreated` 가
  넘겨받은 `tenantId` 를 검사만 하고 **payload 에는 `account.getTenantId()` 를 썼다** — 풀 계정이면 `consumer-pool` 이 그대로 나갈 뻔했다.
  이제 넘겨받은 값을 싣는다. 기존 호출자 셋(가입·소셜 가입·프로비저닝)은 모두 `account.getTenantId().value()` 를 넘기므로 바이트 동일.
- **조회 표면**(§ 5): `/internal/tenants/{t}/accounts` 목록 · `/{accountId}` 단건, `/internal/accounts?tenantId&email`(콘솔 계정 운영 ·
  admin-service `CreateOperatorUseCase` 가 쓰는 그 검색) — 플래그 ON 이면 «계정 테넌트 = t **또는** (풀 ∧ t 의 ACTIVE 멤버)». `*` 는 그대로.
- **auth-service**: 코드·스키마 변경 없음. 확인: 자격 생성(`POST /internal/auth/credentials` → `CreateCredentialUseCase`)은 받은 `tenantId` 를
  그대로 쓰고, DTO 패턴 `^[a-z][a-z0-9-]{1,31}$` 이 `consumer-pool` 을 통과시킨다. 풀 자격 = `tenant_id='consumer-pool'` 행 — auth data-model 그대로.
  `SignupPageController` 는 바꿀 것이 없다: 테넌트 결정은 account-service 가 하고, 거절은 기존 409 경로로 돌아온다.
- **admin-service**: 코드 변경 없음. 확인: `CreateOperatorUseCase` → `AccountServiceClient.search(tenantId, email)` → `GET /internal/accounts`
  → `AccountSearchQueryService.search` — 바꾼 경로를 탄다.

## 계약이 남긴 선택 — 내가 정한 것

| # | 선택 | 정한 값 | 이유 |
|---|---|---|---|
| D-1 | `consumer-pool` 의 `tenant_type` | `B2C_CONSUMER` | 컬럼엔 CHECK 가 없지만 `TenantJpaEntity` 가 enum `{B2C_CONSUMER, B2B_ENTERPRISE}` 로 읽어 제3의 값은 이 행의 모든 조회를 깨뜨린다. 풀은 소비자 쪽 저장이므로 B2C. «소비자 **사이트**» 판정은 이 id 를 명시적으로 뺀다 |
| D-2 | 소비자 사이트 판정 | `tenants.tenant_type = B2C_CONSUMER` ∧ id ≠ `consumer-pool` (`Tenant.isConsumerSite()`) | 문자열 하드코딩 대신 검증 가능한 데이터. 지금 운영 마이그레이션 기준 해당 = `fan-platform` · `ecommerce` 둘 (dev 시드의 B2C 0) |
| D-3 | 거절 응답 | 기존 중복 응답 그대로 — `AccountAlreadyExistsException` → `409 ACCOUNT_ALREADY_EXISTS` → 가입 화면 «이미 가입된 이메일입니다. 로그인해 주세요.» | 새 열거 경로를 만들지 않는다(§ 2 마지막 문장). 🔴 계약은 «로그인한 뒤 **전환**» 안내를 말하지만 전환 흐름은 `TASK-MONO-743` 이고, 문구를 바꾸면 플래그 OFF 의 중복 응답도 바뀐다(AC-8) — **문구는 그대로 두었다** |
| D-4 | AC-6 운영자 측면 판정 | 별도 판정 없음 — § 2 의 «소비자 사이트에 같은 이메일 계정이 있으면 거절» 하나가 셀러·D5 운영자를 포함한다 | 둘 다 소비자 사이트(`ecommerce`/`fan-platform`)의 사이트 계정이다. D5 운영자 연결(`admin_operators.oidc_subject`)은 admin DB 에 있어 account-service 가 볼 수 없는데, 이 단계에선 볼 필요가 없다. `TASK-BE-618` 이 단일 사이트 계정을 옮긴 뒤에도 남는 사이트 계정이 곧 운영자 측면·두 사이트 계정이라 같은 술어가 계속 문다 |
| D-5 | 사이트 계정 존재 질의 | 소비자 사이트마다 테넌트 범위 `existsByEmail(site, email)` (정지 사이트 포함) | 테넌트 없는 조회를 새로 만들지 않는다(§ 격리 회귀 방지 — 예외 등록 불필요) |
| D-6 | `consumer-pool` 을 직접 지명한 가입(`X-Tenant-Id: consumer-pool`) | **플래그와 무관하게** `TenantNotFoundException`(404 `TENANT_NOT_FOUND`) — `ActiveTenantGuard` | 행이 생기기 전과 같은 응답. 안 막으면 V0029 만으로(플래그 OFF 에서도) 멤버십 없는 풀 계정이 생길 수 있었다 |
| D-7 | 멤버십 → `accounts` FK 삭제 동작 | `ON DELETE CASCADE` | 스펙 침묵. `account_roles` 와 같은 결. 공유 시험 컨테이너의 `DELETE FROM accounts` 정리가 RESTRICT 면 깨진다 |
| D-8 | `status` 값 제약 | `CHECK (status IN ('ACTIVE','LEFT'))` | 스펙의 두 값. V0021 의 구독 상태 CHECK 와 같은 결 |
| D-9 | 멤버십 쓰기 | 네이티브 `INSERT` + `flushAutomatically` | Hibernate 삽입 정렬이 매핑 없는 FK 를 모르므로 계정 INSERT 가 먼저 나가도록 강제 |
| D-10 | 사이트 목록 항목의 `tenantId` 필드 | 풀 멤버는 **저장값 `consumer-pool`** 을 그대로 보인다 | 내부 표면이고 정직한 값이 두 종류를 구분하게 한다. 토큰이 아니므로 § 1 의 «토큰에 절대 안 나온다» 와 충돌하지 않는다 |
| D-11 | 플래그 OFF 의 조회 | 옛 쿼리를 그대로 호출(새 쿼리는 OFF 에서 안 탐) | 멤버십이 0행이면 결과는 같지만 «바이트 동일» 을 SQL 수준에서 지키려고 |

## 계약 대비 차이 · 안 한 것 (리뷰어가 줄 단위로 볼 곳)

- 🔴 **`tenants` 목록에 `consumer-pool` 이 보인다 — 플래그와 무관.** V0029 는 계약 § 1 이 요구한 행이고, `GET /internal/tenants` (콘솔 테넌트 스위처)
  · `OrgNode` 백필 시험의 테넌트 순회가 이 행을 센다. 숨기지 않았다(숨기면 `effectiveEntitledDomains` 등 기존 경로가 404 로 바뀐다). 운영자가 스위처에서
  `consumer-pool` 로 전환하면 풀 계정 전체 목록을 본다 — 이것을 막을지는 소유자 결정 거리다.
- 🔴 **`CreateOperatorUseCase` 의 «대상 테넌트에 가입 계정이 있어야 한다»(TASK-MONO-334) 검사가 플래그 ON 에서 풀 멤버로 통과한다.** § 5 를 글자대로 따른 결과다.
  그 다음 줄 `resolveOrCreateIdentity(ecommerce, email)` 은 `ecommerce` 테넌트에 identity 를 **새로** 만든다(풀 계정의 identity 는 `consumer-pool`).
  § 3 은 «운영자 규칙을 바꾸는 결정은 080 이 먼저» 라고 한다 — 플래그를 켜는 `TASK-BE-615` 전에 소유자가 정해야 한다(① 검색은 넓히되 운영자 생성은
  사이트 계정만 보게 별도 질의 · ② 그대로 둔다).
- **사이트 표면의 쓰기 경로는 넓히지 않았다** — `/internal/tenants/{t}/accounts/{id}/roles|status|password-reset` 와 `/internal/accounts/{id}/lock|unlock|delete`
  (헤더가 사이트를 말할 때)는 풀 계정을 여전히 404 로 본다. 풀 계정의 상태 전이는 «계정 하나의 일» 이라 `consumer-pool` 로 다뤄야 하고(account-events.md
  § status.changed), 사이트 역할은 `consumer_site_roles` 로 가야 한다 — 쓰는 쪽(`TASK-BE-615`/`618`/`MONO-745`) 과 함께.
- **목록 항목의 `roles`** 는 `account_roles` 만 읽는다 — 풀 멤버는 빈 배열. `consumer_site_roles` 의 작성자가 이 티켓엔 없다(같은 후속).
- **소셜 가입**(`SocialSignupUseCase`)은 손대지 않았다 — 계약상 `TASK-BE-617`. `ActiveTenantGuard` 의 D-6 변경만 공유한다(그 경로도 행 생성 전과 같은 응답).
- 가입 직후 리다이렉트(`/login?registered`)에서 풀 계정은 **로그인되지 않는다** — `TASK-BE-615`. 플래그 기본 OFF 가 이것을 막는다.

## 게이트 (각각 단독 실행 · `cmd > log 2>&1; echo rc=$?`)

| 게이트 | rc | 비고 |
|---|---|---|
| `:projects:iam-platform:apps:account-service:check` | 0 | 단위·슬라이스. 🔴 `@Tag("integration")` 은 `check` 가 **제외**한다(`projects/iam-platform/build.gradle` `test { excludeTags 'integration' }`) — 새 IT 두 개는 **컴파일만** 됐다 |
| `:projects:iam-platform:apps:auth-service:check` | 0 | 코드 변경 없음 |
| `:projects:iam-platform:apps:admin-service:check` | 0 | 코드 변경 없음 |
| `:account-service:integrationTest` | ⚪ 못 돌렸다 | 이 호스트에 Docker 데몬 없음(`docker info` → `dockerDesktopLinuxEngine` 파이프 없음). JPA 슬라이스(`*JpaRepositoryTest` 8개 스위트)도 같은 이유로 SKIPPED. CI `iam-integration-tests` 가 잰다 |
| 새 JPQL 4개(목록·count·이메일·단건) | 파싱 OK | Docker 없이 대신 잰 것: 실제 엔티티 메타모델로 Hibernate `SessionFactory`(MySQL dialect, JDBC 접근 끔)를 띄워 `AccountJpaRepository` 의 모든 JPQL `@Query` 를 `createQuery` — 의미 분석까지 통과. 스크래치 시험, 커밋 안 함. 🔴 **실행 결과(행이 맞게 걸러지는가)는 아니다** |
| `scripts/check-index-queue-drift.sh` | 0 | `git add` 후 실행 |
| `scripts/check-task-id-collision.sh` | 0 | 〃 |
| `scripts/check-walkthrough-ledger-drift.sh` | 0 | 〃 |
| `scripts/check-flyway-version-collision.sh` | 0 | 모집단 299 마이그레이션 / 27 디렉터리 (스테이지 후) |
| `scripts/check-flyway-unresolvable-placeholder.sh` | 0 | |
| `scripts/check-dev-seed-migration-band.sh` | 0 | |

## AC

- ✅ **AC-1** — 새 파일 V0029 · V0030 만. 기존 마이그레이션 diff 0.
- ⚪ **AC-2** — 시험은 썼다: `ConsumerPoolMigrationOnExistingVolumeIntegrationTest`(별도 DB 에 Flyway `target=28` → 사이트 계정·프로필·셀러 역할 기록 →
  최신까지 → 새 버전이 29·30 **만** 적용 · 기존 행 바이트 동일 · 새 FK/CHECK 가 문다 · 재실행 no-op). **못 쟀다 — Docker 없음.** CI integrationTest 가 잰다.
- ⚪ **AC-3** — 단위로 ✅(`SignupUseCaseConsumerPoolTest`: 풀 계정 + 멤버십 + 풀 자격 / 같은 이메일 팬 계정이 있으면 409, 저장·자격·이벤트 0).
  DB 수준 `ConsumerPoolSignupIntegrationTest#storeSignup_bornInPool` · `#emailWithSiteAccount_isRefused_notPaired` 는 **못 쟀다 — Docker 없음**.
- ✅ **AC-4** — 콘솔(`iam`)은 `tenants` 행이 없어 가입 자체가 없다(BE-581) — 바뀐 것 없음. 비소비자 테넌트는 플래그 ON 에서도 테넌트별 가입(`nonConsumerTenant_keepsPerTenantSignup`).
  운영자 경로(admin-service)는 코드 변경 0, `admin-service:check` rc=0. 단, 위 «CreateOperatorUseCase» 항목 — 플래그 ON 의 행동 변화가 있다(OFF 에선 없다).
- ⚪ **AC-6** — 단위 술어는 AC-3 의 거절과 같다(D-4). DB 시험 `#sellerEmail_fanPoolSignup_refused_sellerUntouched`(셀러 행·SELLER 역할 그대로, 자격 쓰기 0)는
  **못 쟀다 — Docker 없음**. «셀러는 그대로 로그인된다» 는 이 티켓이 auth-service 로그인 경로를 바꾸지 않았다는 것(코드 diff 0)으로만 말할 수 있다 — 로그인을 실행해 잰 것은 아니다.
- ✅ **AC-8** — 플래그 OFF: `SignupUseCaseTest`(10) · `AccountSearchQueryServiceTest`(13) · `AccountEventFactoryTest` 기존 칸이 **기대값 변경 없이** 초록
  (바뀐 것은 생성자 배선에 mock 하나 추가뿐). 새 `FlagOff` 칸이 «멤버십 쓰기 0 · 다른 사이트 조회 0 · 옛 쿼리 호출» 을 단언. auth/admin `check` rc=0.
  🔴 예외 하나: `tenants` 목록의 `consumer-pool` 행(위) — 플래그로 끌 수 없는 계약 § 1 의 결과.
- ⚪ **AC-9** — 서비스 분기는 단위로 ✅(`AccountSearchQueryServiceConsumerPoolTest`), 쿼리 의미(스토어 목록·검색·단건에 풀 쇼핑객 있음 / 풀 팬 없음 — 대조군)는
  `ConsumerPoolSignupIntegrationTest#siteLookups_includeMembers_excludeNonMembers` — **못 쟀다 — Docker 없음**.
- ⚪ **AC-10** — 단위 ✅(이벤트 tenant = `ecommerce` ≠ `consumer-pool`, 팩토리 4-인자 시험). outbox 행 1개·payload `tenantId` 단언 IT 는 **못 쟀다 — Docker 없음**.
- ⚪ **AC-5** — 갱신한 보장: 풀 계정은 (a) 테넌트 범위 `findById(site, id)` 로는 어느 사이트에서도 안 잡힌다 (b) 멤버가 아닌 사이트 · B2B 테넌트(`erp`)의
  목록·검색에 없다 (c) 멤버십이 `LEFT` 가 되면 그 사이트 표면에서 사라진다. `ConsumerPoolSignupIntegrationTest#poolAccount_doesNotLeak` — **못 쟀다 — Docker 없음**.

## 추가 (리뷰 요청, 2026-10-01 UTC) — «consumer-pool 은 어떤 토큰에도 안 나온다» 의 강제

**왜**: 계약 문장(jwt-standard-claims.md `tenant_id` 행 · multi-tenancy.md § 소비자 계정 풀 § 1)을 지키는 게이트가 **하나도 없었다**.
admin-service `ManageOperatorAssignmentUseCase.assignOperator` 는 테넌트 존재를 검증하지 않고(**기존 결함**), auth-service
`AssumeTenantAuthenticationProvider` 는 배정 행만 요구한다 — V0029 가 `consumer-pool` 을 **보이는 ACTIVE 행**으로 만들면서 «플랫폼 관리자가
운영자를 consumer-pool 에 배정 → assume → `tenant_id=consumer-pool` 토큰» 이 도달 가능해졌다. 확인하며 더 찾은 두 경로:
(a) platform-scope(`*`) 운영자는 배정 행 없이도 `OperatorAssignmentCheckUseCase` 가 **모든** 비-공백 테넌트에 assigned 를 준다.
(b) 풀 자격 행(`credentials.tenant_id=consumer-pool`)이 있으면(플래그 ON 가입 뒤 OFF 로 돌린 경우 등) 콘솔 교차 테넌트 폼 로그인이 그 자격을 골라
세션 테넌트 = `consumer-pool` 이 되고, `authorization_code`/`refresh_token` 발급이 그 값을 싣는다.

**발급자(auth-service)** — 상수 한 곳: `TenantContext.CONSUMER_POOL_TENANT_ID` (+ `isConsumerPool`).
- `TenantClaimTokenCustomizer.customize` 끝에서 **모든 grant 분기 뒤 한 번** `tenant_id` 를 읽어 풀 값이면 `invalid_grant` 로 거절 — 토큰 없음.
  `client_credentials` · `authorization_code` · `refresh_token` · 두 `token-exchange` 모양 · 액세스/ID 토큰 전부를 덮고, 나중에 추가되는 grant 도 덮는다.
  결정: 풀 principal 을 사이트 테넌트로 바꿔 싣는 것은 `TASK-BE-615` 의 일이라, 그 전까지 가장 안전한 동작은 **발급 거절**이다(경로 (b) 의 로그인 세션은 생겨도 토큰은 없다).
- `AssumeTenantAuthenticationProvider.authenticate` — 두 분기(운영자·워크로드) **앞에서** 선택 테넌트가 풀이면 `invalid_grant`. admin-service 게이트도,
  토큰 생성기도 부르지 않는다(배정 행이 있어도).

**배정 표면(admin-service)** — 상수 한 곳: `AdminOperator.CONSUMER_POOL_TENANT_ID` (+ `isConsumerPool`), `PLATFORM_TENANT_ID` 옆.
- `ManageOperatorAssignmentUseCase.assignOperator` → `TenantScopeDeniedException`(기존 403 `TENANT_SCOPE_DENIED` 형태), SUPER_ADMIN 포함, 행·감사 없음.
- `OperatorAssignmentCheckUseCase.check` → 풀이면 `notAssigned` (platform-scope 운영자·기존 배정 행이 있어도).
- `ManageSubscriptionUseCase.subscribe` → `TenantScopeDeniedException` (account-service 호출 전).
- `PartnershipManagementUseCase.invite` → host 또는 partner 가 풀이면 `IllegalArgumentException`(기존 VALIDATION_ERROR — 같은 자리의 플랫폼 센티넬 거절과 같은 형태).
- 손대지 않은 것: `unassignOperator`(기존 행 제거는 막을 이유가 없다) · 구독 `changeStatus`(좁히는 방향) · partnership 의 accept/suspend 등(invite 가 막히면 행이 생기지 않는다).

**추가한 시험**
- auth: `AssumeTenantConsumerPoolRefusalTest` — `poolSelected_refused_evenWithAssignment`(배정 게이트가 «예» 라고 할 상태에서 `invalid_grant`, 게이트·생성기 호출 0) ·
  `ordinaryTenant_withSameAssignment_mints`(대조군).
- auth: `TenantClaimConsumerPoolRefusalTest` — `authorizationCode_poolPrincipal_refused` · `refresh_poolPrincipal_refused` · `idToken_poolPrincipal_refused` ·
  `clientCredentials_poolClient_refused` · `authorizationCode_siteTenant_mints`(대조군).
- auth IT: `AssumeTenantExchangeIntegrationTest#consumerPool_refusedEvenWhenAssigned` — ⚪ **못 돌렸다(Docker 없음)**.
- admin: `ConsumerPoolTenantRefusalTest` — `assignOperator_toPool_refused` · `assignOperator_toOrdinaryTenant_created`(대조군) ·
  `assignmentCheck_pool_notAssigned_evenForPlatformScopeWithRow`(같은 운영자로 일반 테넌트는 assigned — 대조군 포함) · `subscribe_pool_refused`.
- admin: `PartnershipManagementUseCaseTest#invite_consumerPool_rejectedOnEitherSide`.

**게이트 (이 추가분, 각각 단독 실행)**

| 게이트 | rc |
|---|---|
| `:projects:iam-platform:apps:account-service:check` | 0 |
| `:projects:iam-platform:apps:auth-service:check` | 0 |
| `:projects:iam-platform:apps:admin-service:check` | 0 |
| `git add` 후: `check-index-queue-drift` · `check-task-id-collision` · `check-walkthrough-ledger-drift` · `check-flyway-version-collision` · `check-flyway-unresolvable-placeholder` · `check-dev-seed-migration-band` · `check-seed-catalogue-parity` · `check-shared-lib-jpa-scan` | 각각 0 |
| auth `integrationTest` | ⚪ 못 돌렸다 — Docker 없음 |

## 후속

- `TASK-BE-615` 는 플래그를 켜기 전에, 이 티켓에서 찾은 운영자 생성 상호작용을 정해야 한다: `CreateOperatorUseCase` 가 넓어진 검색으로 풀 멤버에 대해
  통과한 뒤 `resolveOrCreateIdentity(site, email)` 이 사이트 테넌트에 identity 를 **새로** 만든다. 이것은 ADR-080 경계이므로 소유자가 달리 정하지 않는 한
  615 는 운영자 생성을 옛 규칙에 묶어 둬야 한다(예: 그 검사에서만 풀 멤버를 뺀다).
- 가입 거절 문구는 계약 § 2 의 «로그인 후 전환» 안내가 아니라 기존 «이미 가입된 이메일입니다. 로그인해 주세요.» 다 — 전환 흐름인 `TASK-MONO-743` 으로 미룬다.
  플래그 OFF 의 바이트 동일성을 지키기 위해서다.
- (앞서 적은 것) `tenants` 목록의 `consumer-pool` 노출 여부 — 소유자 결정. 풀 계정의 상태 전이·사이트 역할 쓰기 표면(위 «쓰기 경로»).

---

## 검증 (2026-10-01 UTC · 분석=Opus 5.5 — 구현 에이전트와 별개)

- **계약 대조**: V0029/V0030 이 `account-service/data-model.md` 의 두 신설 테이블과 컬럼·PK·FK 단위로 일치. 플래그 이름·기본값 `iam.consumer-pool.enabled=false` 일치.
- **발급자 게이트의 위치**: `TenantClaimTokenCustomizer.customize` 의 유일한 조기 `return` 은 «access·id 토큰이 아님» 분기(refresh 토큰 자체는 불투명 값 — 클레임 없음). 게이트는 모든 grant 분기 **뒤**에 있어 우회 경로가 없다.
- **bite 직접 확인**: 게이트 호출(`refuseConsumerPoolTenant`)을 주석 처리하고 `TenantClaimConsumerPoolRefusalTest` + `AssumeTenantConsumerPoolRefusalTest` 실행 → **7개 중 4개 실패**(authorization_code · refresh_token · client_credentials · id_token 거절 칸). 실패하지 않은 3개 = 대조군 1(사이트 테넌트는 발급) + assume-tenant 2(그쪽은 공급자 자체 게이트가 막는다 — 별도 장치). 되돌린 뒤 7/7 통과, 작업 트리 무변경 확인.
- ⚪ **통합 테스트는 이 호스트에서 한 번도 돌지 않았다**(Docker 없음) — AC-2/3/5/6/9/10 의 DB 수준 판정은 CI `integrationTest` 가 첫 실측이다.

---

## CORRECTION (2026-10-01 UTC) — ⚪ 로 적은 통합 시험 칸은 **CI 가 쟀다**

이 파일의 «구현 기록» 은 AC-2/3/5/6/9/10 의 DB 수준 판정을 ⚪ «못 쟀다 — 로컬 Docker 없음» 으로 적었다. 그 기록은 작성 시점에 참이었고 고치지 않는다. 그 뒤 PR #4089 의 CI 가 처음으로 쟀다:

| 런 | 결과 |
|---|---|
| 1차 (`Integration (iam B)`) | **1/92 실패** — AC-2 기존 볼륨 시험의 **픽스처** 결함: V0028 상태에 넣는 `profiles` 행이 `tenant_id`(NOT NULL, 기본값 없음)를 빠뜨려 `initializationError`. 마이그레이션 대상에 닿기 전에 죽었다 — 마이그레이션 결함 아님. 수정 커밋 `1d0b07e20` |
| 2차 (job `110527622619`) | **통과** — 로그에서 실행·통과를 확인한 칸: AC-2(V0029·V0030 만 적용 · 기존 행 바이트 그대로 · 새 FK/CHECK 가 실제로 문다 · 재실행 no-op) · AC-3/AC-10(스토어 풀 가입 → `consumer-pool` 계정 + `ecommerce` 멤버십 + 풀 자격 + `account.created` 사이트 테넌트) · AC-3 §2(같은 이메일 팬 계정 → 409) · AC-5(비멤버 사이트 · B2B 테넌트 · LEFT 에서 안 보임) · AC-6(셀러 이메일 팬 풀 가입 거절, 셀러 계정 무변경) · AC-9(ecommerce 목록·검색·단건에 풀 쇼핑객, 풀 팬은 없음) · `AssumeTenantExchangeIntegrationTest` consumer-pool 거절 |

⇒ 위 AC 들은 **CI 실측으로 닫혔다**. 머지 `98e6c6dbe`(#4089), 머지 전 체크 19/19 통과 · 실패 0.
