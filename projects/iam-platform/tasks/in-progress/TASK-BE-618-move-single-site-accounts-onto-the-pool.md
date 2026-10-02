# Task ID

TASK-BE-618

# Status

in-progress

# Title

전역 소비자 계정 — 기존 **한 사이트 계정**을 **같은 id 로** 풀로 옮긴다 (`ADR-MONO-078` A · 계약 § 3)

# Owner

iam-platform

# Task Tags

- account-service
- auth-service
- data-migration

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (두 DB 에 걸친 이동 — 한 행이라도 남으면 그 행의 조회가 404)

---

# Dependency Markers

- **선행**: `TASK-BE-614`(풀 모델) · `TASK-BE-615`(풀 자격 로그인 — 옮긴 계정이 로그인할 길)
- **분리 출처**: `TASK-BE-614` AC-7 (착수 시 정정 2026-10-01 — 614 의 Goal 과 모순)

# Goal

078 이전에 **한 소비자 사이트에만** 계정이 있는 사람의 계정을 같은 id 로 `consumer-pool` 로 옮기고 그 사이트 멤버십을 만든다. id 가 그대로라 팬·스토어 데이터는 무변경이다. 두 사이트에 계정이 있는 사람은 대상이 아니다(`TASK-MONO-743` 묶기).

# Scope

## In Scope

- 이동 시점 결정: 일괄(내부 배치) / 다음 로그인 때 지연 — 하나를 고르고 근거를 적는다
- 같이 옮길 IAM 행: `accounts` · `profiles` · `account_status_history` · `credentials` · `refresh_tokens` · `social_identities` · `identities`
- 팬 `ARTIST` 역할 → `consumer_site_roles(account, fan-platform, ARTIST)`

## Out of Scope

- 운영자 측면 계정: 셀러(`TASK-MONO-745`) · 셀프 온보딩 운영자(`TASK-MONO-746`)
- 두 사이트 계정(`TASK-MONO-743`)

# Acceptance Criteria

- [ ] **AC-1** — 운영자 측면이 붙은 계정(셀러 · 셀프 온보딩 운영자)은 **제외**된다 — 시험으로.
  → 🟡 시험 작성 · **로컬 IT 미실행**(Docker 없음 — 아래 구현 기록): account `ConsumerPoolLegacyMoveIntegrationTest#sellerTwoSiteDeletedPoolTwin_notMoved` ·
  `#authRefusal_isSkip_rowsUnchanged`(OPERATOR_FACETED) · auth `ConsumerPoolLegacyMoveIntegrationTest#operatorFaceted_409_nothingChanged` ·
  admin `OperatorAssignmentCheckIntegrationTest#facet_bySubject/_byIdentity/_suspendedOperatorStillFaceted/_noMatch`.
  로컬 실행분(단위): `ConsumerPoolLegacyAccountMoverTest#seller_skipped/twoSite_skipped` · `MoveCredentialToConsumerPoolUseCaseTest#operatorFaceted` ✅.
- [ ] **AC-2** — 팬 `ARTIST` 역할이 `consumer_site_roles` 로 옮겨지고 `artists.account_id` 는 무변경 — 시험으로.
  → 🟡 account `ConsumerPoolLegacyMoveIntegrationTest#movesFanArtist_everyRow_sameId_noEvent`(id 무변경 + `consumer_site_roles = [ARTIST, FAN]`) — 로컬 IT 미실행.
  `artists.account_id` 는 fan-platform DB 라 id 무변경으로 대신 고정한다.
- [ ] **AC-3** — 옮긴 계정이 같은 비밀번호로 원래 사이트에 로그인되고, 그 사이트 데이터(팔로우·주문)가 같은 `sub` 로 보인다.
  → 🟡 auth `ConsumerPoolLegacyMoveIntegrationTest#move_credentialOnly_preMoveRefreshWorks_sameSubAfterLogin`(이동 전 로그인 → 이동 → 이동 전 refresh 200 ·
  새 로그인 `sub` 동일 · `tenant_id=fan-platform`) — 로컬 IT 미실행. 팔로우·주문은 다른 서비스 DB 라 «같은 `sub`» 로 대신 고정한다.
- [ ] **AC-4** — 🔴 IAM 행 일곱 종류가 **전부** 옮겨진다 — 한 종류라도 남으면 실패하는 시험.
  → 🟡 정정 ③의 표대로: account IT `#movesFanArtist_everyRow_sameId_noEvent`(accounts · profiles · identities · account_roles→consumer_site_roles · 멤버십) +
  auth IT(credentials 만, refresh_tokens 그대로) — 로컬 IT 미실행. 🔴 `account_status_history` 는 **옮기지 않는다**(구현 기록 «설계와 다른 점 ①»).
- [ ] **AC-5** — 이동 중 실패가 «반쯤 옮겨진» 계정을 남기지 않는다(재시도 가능 또는 되돌림). 두 DB(account_db · auth_db)라 단일 트랜잭션이 아니다 — 순서와 멱등성으로 보장한다.
  → 🟡 account IT `#authFailure_rollsBackWholeAccount_thenRerunCompletes`(auth 실패 → 전 행 무변경 → 재실행 완결 → 세 번째 실행은 후보 아님) ·
  auth IT 재이동 `alreadyInPool` — 로컬 IT 미실행. 단위 `ConsumerPoolLegacyAccountMoverTest#move_bindingOrder_authLast` ✅(auth 가 마지막).
- [ ] **AC-6** — 기존 볼륨 시험(Testcontainers). 불가하면 ⚪ «못 쟀다, 이유».
  → 🟡 account IT 는 이전 코드가 쓰던 모양의 행을 **SQL 로** 심고(이동 전 계정 · 프로필 · 이력 · 신원 · `account_roles`) 그 위에서 돈다. 이 티켓은 마이그레이션을 더하지 않아
  «Flyway 를 옛 버전에서 멈추고 올리는» 시험은 해당이 없다. 로컬 Docker 가 없어 ⚪ **로컬에서는 못 쟀다** — CI `iam-integration-tests` 가 판정한다.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀 § 3

# Related Contracts

- `projects/iam-platform/specs/contracts/events/account-events.md` (이동은 `account.created` 를 내지 않는다)

# Edge Cases

- 같은 이메일로 다른 사이트에 계정이 생기는 경합(이동 중 가입) — § 2 의 공존 금지로 막힌다.

# Failure Scenarios

1. `credentials` 만 옮기고 `refresh_tokens` 를 남겨 refresh 가 `TOKEN_TENANT_MISMATCH` 로 실패.
   → 🔴 **착수 시 정정: 방향이 반대다.** 아래 «착수 시 정정 ①».
2. 셀러를 같이 옮겨 product-service 가 셀러를 놓친다.

---

# 착수 시 정정 (2026-10-02 UTC, 구현자)

> 티켓 본문(Goal · AC)은 기안 때의 글이다. 아래는 코드를 열어 잰 뒤의 정정이고, 구현은 이 절을 따른다.

**① `refresh_tokens` 는 옮기지 않는다 — 옮기면 그것이 결함이다.** refresh 미러 행의 테넌트는 «계정의 테넌트» 가 아니라
«세션 테넌트 = 그 토큰의 `tenant_id`» 다(`AuthorizationSessionTenant` 클래스 주석 · `TASK-BE-604`). 풀 principal 의 세션 테넌트는
**요청한 사이트**다(`TASK-BE-615`, `multi-tenancy.md` § 4 «refresh» 줄). 그러니 사이트 계정의 기존 refresh 행(`tenant_id` = 그 사이트)은
**이미 목표 모양**이다. `consumer-pool` 로 옮기면 `RefreshTokenUseCase` 가 제출된 토큰의 사이트와 DB 행의 `consumer-pool` 을 비교해
`TOKEN_TENANT_MISMATCH` 를 낸다 — Failure Scenario 1 이 막으려던 바로 그 실패를, 옮겨서 만든다. 블랙리스트 키도 행 테넌트다.
⇒ AC-4 의 «일곱 종류» 는 아래 ③의 표로 바뀐다. 시험은 «옮기지 **않았고**, 이동 뒤 refresh 가 된다» 를 고정한다.

**② 소셜 신원이 있는 계정은 이 단계에서 옮기지 않는다 (`TASK-BE-617` 로 인계).** 지금 소셜 로그인은 `(사이트 테넌트, provider, provider_user_id)`
로 신원을 찾는다. 신원 행을 `consumer-pool` 로 옮기면 그 조회가 비어 «새 소셜 가입» 으로 가고, 그 가입은 풀로 가서 같은 이메일의 (방금 옮긴) 풀 계정에
`409` 로 막힌다 — 그 사람의 소셜 로그인이 끊긴다. 옮기지 않고 계정만 옮겨도 신원 행 테넌트(사이트)와 계정 테넌트(풀)가 갈린다.
617 의 AC-3 이 «기존 테넌트별 소셜 신원은 그대로 동작한다» 를 이미 요구하므로, 소셜 신원이 있는 계정은 건너뛰고(`SOCIAL_LINKED`) 617 이 소셜 조회를
풀에 맞춘 뒤 같은 이동기로 옮긴다.

**③ 옮기는 행 (AC-4 의 대상, 정정본)**

| DB | 행 | 이동 | 근거 |
|---|---|---|---|
| account_db | `accounts` | `tenant_id` → `consumer-pool` | § 1 |
| account_db | `profiles` | 〃 | 읽기는 `account_id` 로만 하지만 계정과 같은 값을 둔다 |
| account_db | `account_status_history` | ~~〃~~ → 🔴 **옮기지 않는다** (구현 기록 «설계와 다른 점 ①» — append-only 트리거) | 읽기는 `account_id` 로만 |
| account_db | `identities` (그 계정의 `identity_id` 행) | 〃 | 풀 가입은 `(consumer-pool, email)` 로 신원을 만든다 — 같은 모양. 같은 `identity_id` 라 `credentials.identity_id` 는 무변경 |
| account_db | `account_roles` (그 사이트) | → `consumer_site_roles(account, site, role)` 후 원래 행 삭제 | § 1 · 복합 FK 때문에 계정 테넌트 변경 **전에** 지워야 한다 |
| account_db | `consumer_site_memberships` | **신설** `(account, site, ACTIVE, consented_at = 계정 생성 시각)` | 가입 = 그 사이트 동의(§ 2) |
| auth_db | `credentials` | `tenant_id` → `consumer-pool` | § 1 · 폼 로그인의 풀-먼저 |
| auth_db | `refresh_tokens` | **옮기지 않는다** | ① |
| auth_db | `social_identities` | **옮기지 않는다** — 있으면 계정째 건너뜀 | ② |
| auth_db | `oauth2_authorization`(SAS 세션) | 옮기지 않는다 | 이동 전 세션의 principal 은 사이트 principal 로 남는다 → ⑤ |

**④ 운영자 측면 판정.** 셀러 = 그 사이트에 저장된 `SELLER` 역할(account-service 가 직접 안다). 셀프 온보딩 운영자 = `admin_operators.oidc_subject`
가 이 계정 id — admin_db 에 있다. 거기에 더해 **운영자 신원 연결**(`LinkOperatorIdentityUseCase`, ADR-MONO-034 U3 — `admin_operators.identity_id` 가 이
계정의 신원)도 운영자 측면이다: 신원 행을 풀로 옮기면 그 운영자의 신원이 풀 신원이 된다. account-service 는 admin-service 를 부르지 않는다(반대 방향
의존이 이미 있어 순환). auth-service 는 이미 admin-service 를 부른다(`auth-to-admin.md`) — 그래서 판정은 auth-service 의 이동 엔드포인트가 admin-service
에 묻는다(**fail-closed**: 못 물으면 옮기지 않는다).

**⑤ 이동 전 세션(이미 로그인해 있던 사람).** 그 세션의 principal 은 사이트 principal 이라 refresh 때 `populateRoles(사이트, id)` 가
`GET /internal/tenants/{site}/accounts/{id}/roles` 를 부른다. 이동 뒤 그 사이트의 `account_roles` 는 비었으므로 시드만 실려 `ARTIST` 가 사라진다.
⇒ 그 조회를 § 5(사이트 조회는 그 사이트의 ACTIVE 풀 멤버를 포함)에 맞춰 넓힌다: 사이트 테넌트로 물었는데 그 계정이 그 사이트의 ACTIVE 풀 멤버면
`consumer_site_roles(account, site)` 를 답한다. 이동 전과 같은 역할 집합이 같은 «저장 역할만» 규칙으로 실린다.

# 결정 — 이동 시점: **일괄**(재실행 가능한 내부 유지보수 엔드포인트)

| 후보 | 장점 | 단점 |
|---|---|---|
| **일괄** ✅ | 결정적 · 시험 가능 · 로그인 경로 무변경 · 휴면 계정도 옮겨져 § 2 의 «사이트 계정 이메일 거절» 이 빨리 줄어든다 | 실행 주체가 필요(데모는 `TASK-MONO-744`) |
| 다음 로그인 때 | 활동 계정만 | 로그인 한 번에 세 서비스 쓰기가 끼어든다(지연 · 실패가 로그인 실패) · 휴면 계정은 영영 사이트 계정 · 운영자 판정이 로그인 경로에 들어간다 |

- 🔴 **재실행 가능해야 한다.** 내부 프로비저닝은 080 전까지 사이트 테넌트에 계정을 만든다(§ 3 마지막 문단) — 한 번 돌려도 새 사이트 계정이 계속 생긴다.
- 실행 주체: `POST /internal/consumer-pool/legacy-moves`(account-service). 데모 실행은 `TASK-MONO-744` 의 몫이다(재굽기와 함께).

# 결정 — 순서와 멱등성 (AC-5)

한 계정 = account-service 트랜잭션 하나 안에서: 계정 행 잠금 → 자격 검사 → 역할 이동·멤버십 생성 → 테넌트 값 변경 → **auth-service 이동 호출**
→ 커밋. auth 호출이 거절·실패하면 예외로 account 트랜잭션이 되돌아가 **아무것도 옮겨지지 않는다**(가입 `SignupUseCase` 와 같은 모양 — 원격 쓰기가
트랜잭션 안의 마지막 단계). 남는 창은 하나 — auth 커밋 뒤 account 커밋이 실패한 경우(자격만 풀). 그 계정은 여전히 사이트 계정이라 다음 실행의
후보로 다시 잡히고, auth 이동은 «이미 풀이면 성공» 이라 그대로 완결된다. 그 창 동안 폼 로그인은 풀 자격 → 풀 principal → 멤버십 없음 → 동의 화면이며,
동의는 풀 계정이 아니라 쓰지 않고 «멤버 아님» 으로 답한다(`ConsentToConsumerSiteUseCase`) — 그 사람은 재실행 전까지 들어가지 못한다(토큰 없음,
남의 계정에 붙는 일 없음). 이 창은 «auth 커밋 성공 + account 커밋 실패» 라는 드문 경우에만 열리고, 실행 보고서의 `failed` 로 드러난다.
---

# 구현 기록 (2026-10-02 UTC, 구현자)

## 바뀐 것

**계약·스펙 (먼저 — 커밋 1)**: `specs/contracts/http/internal/account-maintenance-internal.md`(신설 — `POST /internal/consumer-pool/legacy-moves`) ·
`auth-internal.md`(`POST /internal/auth/consumer-pool/moves`) · `auth-to-admin.md`(`GET /internal/operators/facet`) · `account-internal-provisioning.md`(roles GET 넓힘, 정정 ⑤) ·
`specs/features/multi-tenancy.md` § 3(시점 = 일괄, 옮기는 행 정본 표) · § 5(roles GET) · § 7(시험 자리) · `account-events.md`(이동은 `account.created` 없음 — 시험 고정) ·
account/auth `data-model.md` · `platform/error-handling.md`(`CONSUMER_POOL_DISABLED` · `POOL_MOVE_OPERATOR_FACETED` · `POOL_MOVE_SOCIAL_LINKED` · `POOL_MOVE_CREDENTIAL_EXISTS` ·
`POOL_MOVE_CREDENTIAL_TENANT_MISMATCH` 등록) · `tasks/ready/TASK-BE-617-*.md` § 인계.

**account-service**: `ConsumerPoolLegacyMoveController` → `ConsumerPoolLegacyMoveUseCase`(배치, 비트랜잭션 · 플래그 게이트 · 보고서 · 커서) →
`ConsumerPoolLegacyAccountMover`(계정 하나 = `REQUIRES_NEW` 트랜잭션, 순서 고정 · auth 마지막) → `ConsumerPoolLegacyMoveRepository`(도메인 포트) /
`ConsumerPoolLegacyMoveRepositoryImpl`(JDBC). `AuthServicePort#moveCredentialToConsumerPool` + `CredentialPoolMoveRefused` · `AuthServiceClient`(409 `code` → 사유).
`GetAccountRolesUseCase` 넓힘(정정 ⑤). `ConsumerPoolDisabledException` → `409 CONSUMER_POOL_DISABLED`. 도메인 `LegacySiteAccount` · `LegacyMoveCandidate`.
결과 `LegacyMoveOutcome` · `ConsumerPoolLegacyMoveResult`. 마이그레이션 없음.

**auth-service**: `InternalConsumerPoolMoveController` → `MoveCredentialToConsumerPoolUseCase`(판정 순서 · `credentials.tenant_id` 만) · `OperatorFacetPort` /
`AdminOperatorFacetClient`(같은 base-url, **별도** circuit breaker `adminOperatorFacet`, fail-closed) · `CredentialRepository#findIdentityId/#moveToTenant`(native, `version + 1`) ·
`SocialIdentityRepository#existsByAccountId`.

**admin-service**: `OperatorFacetController`(`GET /internal/operators/facet`) → `OperatorFacetQueryUseCase` → `AdminOperatorPort#existsOperatorFacet`(상태 무관).

**시험**: account `ConsumerPoolLegacyMoveUseCaseTest` · `ConsumerPoolLegacyAccountMoverTest` · `GetAccountRolesUseCaseTest` · `AuthServiceClientUnitTest`(+4) ·
`ConsumerPoolLegacyMoveIntegrationTest`(IT, 공유 컨텍스트 `AbstractConsumerPoolIntegrationTest` 하위) / auth `MoveCredentialToConsumerPoolUseCaseTest` ·
`InternalConsumerPoolMoveControllerSliceTest` · `ConsumerPoolLegacyMoveIntegrationTest`(IT) / admin `OperatorFacetControllerSliceTest` ·
`OperatorAssignmentCheckIntegrationTest`(+4 — 새 컨텍스트를 만들지 않으려고 기존 클래스에 더함).

## 로컬에서 돌린 것 (rc 는 파일로 받아 따로 읽었다)

| 명령 | 결과 |
|---|---|
| `./gradlew :…:account-service:compileTestJava :…:auth-service:compileTestJava :…:admin-service:compileTestJava` | rc=0 (IT 소스 포함 컴파일) |
| `./gradlew :…:account-service:check :…:auth-service:check :…:admin-service:check --continue` | rc=0 — 결과 XML 93 · 127 · 137 개, 실패 0 (`@Tag("integration")` 제외 — CI 의 Docker-free 레인과 같은 집합) |
| 새 단위·슬라이스 클래스 (위 목록) | 전부 통과 — 4 · 7 · 6 · 13(+4) / 8 · 6 / 3 |
| `scripts/check-error-code-registry.sh` | rc=0 |
| 가드(전부 `git add` **뒤**에 — 모집단이 `git ls-files`) | `check-controller-slice-naming` rc=0(149 개 — 스테이지 전엔 147, 새 슬라이스 둘이 그제야 보였다) · `check-index-queue-drift` rc=0 · `check-task-id-collision` rc=0 · `check-walkthrough-ledger-drift` rc=0 · `check-service-type-drift` rc=0 · `check-flyway-version-collision` rc=0(마이그레이션 없음) |

⚪ **못 돌린 것: 세 서비스의 새 통합 시험 전부**(account · auth `ConsumerPoolLegacyMoveIntegrationTest`, admin `OperatorAssignmentCheckIntegrationTest` 의 facet 넷).
이유: 이 호스트에 Docker 데몬이 없다(`docker info` → `dockerDesktopLinuxEngine` 파이프 없음) — Testcontainers 가 뜨지 않는다. CI `iam-integration-tests` 가 처음 잰다.
그래서 AC-1~6 은 위에서 🟡(시험 작성 · 미측정)로 두었다 — 닫는 것은 CI 초록을 본 뒤다.

## 설계와 다른 점 (이유와 함께)

1. 🔴 **`account_status_history` 는 옮기지 않는다** (정정 ③ 표는 «옮김»). 그 표는 DB 트리거로 **UPDATE 가 금지된 append-only** 다(`V0004`, audit-heavy A3) —
   옮기려면 트리거를 끄는 마이그레이션이 필요하고 그것은 감사 불변식을 깬다. 읽기는 전부 `account_id` 로만 한다(`AccountStatusHistoryJpaRepository`) — 404 가 나지 않는다.
   이동 사실을 이력 행으로 «덧붙이는» 안도 기각했다: `GET …/status` 가 최신 행의 사유를 «현재 상태의 사유» 로 보이므로, 잠긴 계정의 사유가 `ADMIN_LOCK` 대신 이동 코드로 바뀐다.
   감사는 구조화 로그(계정 id · 사이트 · 결과)로 한다 — admin-service backfill 선례. 스펙(multi-tenancy § 3 표 · data-model)도 그렇게 고쳤다.
2. **요청에 커서 `afterAccountId` 와 응답 `nextAfterAccountId` 를 더했다** (계획은 `limit` 만). 건너뛴 계정은 영구 후보라, 커서 없이 `limit` 만 있으면
   건너뛸 계정이 `limit` 개를 넘는 순간 매 실행이 같은 머리만 보고 뒤를 영영 못 본다.
3. **auth 409 코드는 `POOL_MOVE_` 접두어를 붙였다** — `POOL_MOVE_OPERATOR_FACETED` · `POOL_MOVE_SOCIAL_LINKED` · `POOL_MOVE_CREDENTIAL_EXISTS`. 전역 레지스트리에
   `SOCIAL_LINKED` 같은 맨 이름은 뜻이 모호하다. account 쪽 건너뛰기 사유는 계획대로 접두어 없는 이름이다.
4. **네 번째 auth 거절 `POOL_MOVE_CREDENTIAL_TENANT_MISMATCH` 를 더했다** — 자격 행이 풀도 그 사이트도 아닌 테넌트에 있는 경우(BE-507 이전 이상 행). 그대로 옮기면
   다른 사이트 자격을 풀로 끌고 가고, «없음» 으로 답하면 계정만 옮겨져 그 사람이 로그인 못 한다 — 조사 대상으로 남긴다.
5. **두 사이트 판정은 상태 무관**(DELETED 쌍둥이도 `TWO_SITE`) · **`IDENTITY_CONFLICT` 는 «같은 `primary_email` 의 풀 신원» 외에 «그 신원을 다른 계정도 가리킴» 도 포함** —
   둘 다 보수적인 쪽(옮기지 않음)이다.
6. **`SOCIAL_LINKED` 는 테넌트 무관**(그 계정의 `social_identities` 행이 어느 테넌트에든 있으면) — BE-507 이전 계정은 신원 행 테넌트와 계정 테넌트가 다를 수 있다(BE-602).
7. **저장돼 있던 시드 역할(`FAN`)도 `consumer_site_roles` 로 그대로 옮긴다** — data-model 의 «시드는 저장하지 않는다» 는 가입·동의 쓰기의 규칙이고, 이동 전 세션(사이트 principal)은
   «저장 역할만» 규칙으로 정정 ⑤의 넓힌 조회를 읽으므로 저장 집합이 그대로 가야 이동 전과 같은 `[ARTIST, FAN]` 이 실린다. 풀 발급은 `시드 ∪` 라 중복이 결과를 바꾸지 않는다. data-model 에 예외로 적었다.
8. `consented_at = created_at` 은 SQL 안에서 복사한다(`INSERT … SELECT created_at`) — JVM 시간대 왕복이 끼지 않게.
9. 플래그 꺼짐 거절(`CONSUMER_POOL_DISABLED`)은 **단위 시험**으로만 고정했다(`ConsumerPoolLegacyMoveUseCaseTest#flagOff_refusesWholeRun`) — IT 를 플래그 꺼짐으로 돌리려면
   공유 컨텍스트를 갈라야 하고, 그것이 CI MySQL «Too many connections» 의 원인이었다(`AbstractConsumerPoolIntegrationTest` javadoc).

## 남는 위험 (재지 않은 것)

- 동시에 같은 계정의 **프로필**을 load-modify-save 하던 요청은 `profiles.tenant_id` 를 사이트로 되쓸 수 있다(`profiles` 에 `version` 이 없다). 읽기가 `account_id` 로만이라 결과는 무해하다.
  `accounts` · `identities` · `credentials` 는 `version + 1` 로 그 경합을 낙관적 락 실패로 바꿨다.
- auth-service 의 이동 호출은 5xx 에 재시도한다(공용 `ResilienceClientFactory` — 최대 3회). 이동은 멱등이라 결과는 같다.
