# Task ID

TASK-MONO-752

# Title

`ADR-MONO-079` D5 — **셀러 구성원**: 사람의 풀 계정을 셀러에 연결(초대 → 로그인 본인 수락) · `SELLER` 사이트 역할 쓰기/회수 · 셀러 정지 = 역할 회수 (`TASK-MONO-745` 흡수분)

# Status

review

# Owner

monorepo

# Task Tags

- ecommerce
- iam
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (초대 수락 = 계정 탈취 경로가 될 수 있는 흐름 · 두 프로젝트)

---

# Dependency Markers

- **선행**: `ADR-MONO-079` ACCEPTED — A · `ADR-MONO-078` A(풀 계정 · `consumer_site_roles`)

# Goal

한 셀러에 사람 여럿을 구성원으로 붙인다(라이더 R4 기본값 — 역할 하나 `MEMBER`). 운영자가 콘솔 셀러 화면에서 이메일로 초대하고, 그 이메일의 사람이 **스토어에 로그인한 상태로** 수락해야 붙는다(이메일 일치만으로는 안 붙는다 — ADR-034 § 1.3). 수락 시 IAM 이 그 풀 계정에 `consumer_site_roles(account, ecommerce, SELLER)` 를 쓰고, 셀러 정지·폐점은 구성원의 그 역할을 **회수**한다(계정 잠금 아님). 셀러 기계 계정의 잠금(ADR-042 D4)은 그대로.

# Scope

## In Scope

- product-service: `seller_members(tenant_id, seller_id, account_id, role, status, joined_at)` · 초대(토큰 · 만료 · 1회) · 수락 API(로그인 본인 · 초대 이메일 = 계정 이메일) · 정지/폐점 시 구성원 역할 회수
- IAM: `consumer_site_roles` 쓰기/회수 내부 API(계약 먼저) — 풀 계정·스토어 멤버십 전제(없으면 멤버십 생성 규칙을 정한다)
- 콘솔 셀러 화면의 구성원 목록·초대
- `AccountStatusChangedSellerConsumer` 는 **기계 계정에만** 반응한다는 것을 시험으로 고정(구성원 한 명 잠금 ≠ 셀러 정지)

## Out of Scope

- 셀러가 **일하는 화면**(셀러 센터 · 운영자 토큰의 셀러 범위 클레임 주입) — ADR-079 범위 밖
- 구성원 등급(R4 밖)

# Acceptance Criteria

- [ ] **AC-1** — 🔴 대조군: 초대받지 않은 사람 · 다른 이메일 계정 · 만료/재사용 초대 → 수락 거절. 같은 시험에서 올바른 수락만 성공.
- [ ] **AC-2** — 수락 뒤 그 사람의 스토어 토큰 역할 = `["CUSTOMER","SELLER"]` · 팬 토큰에는 `SELLER` 없음(평탄화 금지).
- [ ] **AC-3** — 셀러 정지 → 구성원 토큰에서 `SELLER` 만 빠지고 `CUSTOMER` 와 팬 로그인은 그대로.
- [ ] **AC-4** — 구성원 계정 잠금은 셀러를 정지시키지 않는다 · 기계 계정 잠금은 지금처럼 정지시킨다.

# Related Specs

- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D5 · R4
- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md` § 4 · CORRECTION 2026-10-02
- `docs/adr/ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md` D4

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/`(사이트 역할 쓰기/회수 — 신설) · product-service 셀러 API

# Edge Cases

- 이미 다른 셀러의 구성원인 사람 — 여러 셀러 구성원 허용 여부를 정한다(`SELLER` 역할은 하나라 회수 시 다른 셀러 구성원 자격이 남으면 회수하지 않는다).
- 풀 계정이 아닌(사이트) 스토어 계정의 사람 — 수락 거절 또는 안내(사이트 계정은 `consumer_site_roles` 를 못 가진다).

# Failure Scenarios

1. 초대 이메일과 로그인 계정 이메일을 비교하지 않아, 초대 링크를 받은 누구나 셀러가 된다.
2. 셀러 정지가 사람 계정을 잠가 쇼핑·팬까지 막힌다.

---

# 구현 기록 (2026-10-02 UTC · 분석=Opus 5.5 / 구현=Opus 5.5)

## 무엇을 정했나 (티켓이 «정한다» 라고 남긴 셋)

| 질문 | 결정 | 근거 |
|---|---|---|
| 이메일 비교는 **어디서** | **IAM** 이 한다 — 수락 때 product-service 는 초대 이메일을 `expectedEmail` 로 넘기고, IAM 이 풀 계정 자신의 이메일과 비교(대소문자·앞뒤 공백 무시) | 계정 이메일의 주인은 IAM 이다. 액세스 토큰의 `email` 클레임은 요청 scope 에 달려 있어(`TenantClaimTokenCustomizer` `SCOPE_EMAIL`) 입력으로 못 쓴다 · 게이트웨이 `X-User-Email` 은 `skipIfNull` |
| 멤버십 생성 규칙 (In Scope 2) | **쓰기는 멤버십을 만들지 않는다** — 그 사이트 ACTIVE 멤버십이 없으면 `409 SITE_MEMBERSHIP_REQUIRED` | 멤버십 = 동의(ADR-078 D3). 수락자는 스토어에 로그인해 있으므로 이미 ACTIVE 멤버다(멤버십 없으면 토큰 없음, BE-615). 없다는 것은 «로그인한 스토어 사용자가 아님» 이다 — 고치지 않고 거절한다. FK 도 어차피 막는다 |
| 여러 셀러 구성원 (Edge 1) | **허용**. 회수는 그 사람이 **다른 ACTIVE 셀러의 ACTIVE 구성원**으로 남아 있으면 IAM 을 부르지 않고 이 셀러 행만 `REVOKED` | 사이트 역할 `SELLER` 는 하나 |
| 사이트(풀 밖) 계정 (Edge 2) | 거절 — IAM `409 SITE_ROLE_REQUIRES_POOL_ACCOUNT` → 스토어 `409 SELLER_MEMBER_ACCOUNT_NOT_ELIGIBLE` | 사이트 계정은 `consumer_site_roles` 를 못 가진다 |
| 쓸 수 있는 역할 | **닫힌 목록** `(ecommerce, SELLER)` 하나(`GrantableSiteRoles`) — 그 밖은 `400 SITE_ROLE_NOT_GRANTABLE` | 사이트 역할은 소비자 토큰에 실리고 스토어 게이트웨이는 역할 이름으로 운영자 경로를 연다 ⇒ 열린 이름이면 호출자가 쇼핑객에게 `ECOMMERCE_OPERATOR` 를 찍을 수 있다 |
| 정지 회수 실패 | 구성원은 IAM 이 확인할 때까지 `ACTIVE` 로 남고, **이미 정지/폐점된 셀러에 SUSPEND/CLOSE 를 다시 보내면 그 회수만 재시도**(기계 계정 잠금은 다시 안 보냄) | 회수 fail-soft 이되 «조용히 남는 역할» 이 없게 |

## 무엇을 바꿨나

**계약·스펙 먼저**
- 신설 `projects/iam-platform/specs/contracts/http/internal/consumer-site-roles.md` — `PATCH /internal/tenants/{tenantId}/accounts/{accountId}/site-roles:grant` · `…:revoke`(규칙 순서 · 닫힌 목록 · 멤버십 규칙 · 감사행 · 이벤트 없음)
- `iam-platform/specs/services/account-service/data-model.md` § consumer_site_roles — HTTP 쓰기 경로 한 줄
- `ecommerce/specs/contracts/http/product-api.md` — § Seller members(목록·초대·수락, 수락 검사 순서) · SUSPEND/CLOSE 회수 단락
- `ecommerce/specs/contracts/http/internal/product-to-account.md` § 5(grant — **fail-closed**, 오류 1:1 매핑) · § 6(revoke — fail-soft·재시도)
- `ecommerce/specs/contracts/events/account-lifecycle-subscriptions.md` — 역방향 투영은 **기계 계정만**(고정 시험 이름)
- `ecommerce/specs/services/product-service/architecture.md` — 셀러 구성원 절
- `platform-console/specs/contracts/console-integration-contract.md` § sellers — #7 · #8
- `platform/error-handling.md` — Product 6행(`SELLER_NOT_ACTIVE` · `SELLER_INVITATION_{NOT_FOUND,EXPIRED,ALREADY_USED,EMAIL_MISMATCH}` · `SELLER_MEMBER_ACCOUNT_NOT_ELIGIBLE`) · account-service 4행(`SITE_ROLE_NOT_GRANTABLE` · `SITE_ROLE_EMAIL_MISMATCH` · `SITE_ROLE_REQUIRES_POOL_ACCOUNT` · `SITE_MEMBERSHIP_REQUIRED`) · `rules/domains/saas.md`(4) · `rules/domains/ecommerce.md`(섹션 대응 1행)

**IAM account-service** (마이그레이션 없음 — `consumer_site_roles` 는 V0030 에 이미 있다)
- `ConsumerSiteRoleController` · `ConsumerSiteRoleWriteUseCase` · `GrantableSiteRoles` · 예외 4 · DTO 3 · 저장소 `addSiteRole`/`removeSiteRole`(네이티브) · `GlobalExceptionHandler` 4
- 🔵 **새 IdP 클라이언트·권한 카탈로그 변경 없음**: 경로가 이미 라우팅되는 `/internal/tenants/{tenantId}/**` 이고 `{tenantId}`=`ecommerce` 는 product-service 가 이미 교환하는 테넌트다(`TenantScopedIamTokenProvider`, ADR-MONO-076) ⇒ Hard Stop 해당 없음

**ecommerce product-service**
- `V22__create_seller_members.sql`(postgres) + `migration-h2/V15__create_seller_members.sql`(쌍둥이 — h2 트리엔 `sellers` 가 없어 FK 없음이 정상)
  - `seller_members(tenant_id, seller_id, account_id, role, status, joined_at)` PK `(tenant_id, seller_id, account_id)` · role `MEMBER` · status `ACTIVE|REVOKED`
  - `seller_member_invitations(id, tenant_id, seller_id, email, token_hash, status, expires_at, invited_by, created_at, accepted_at, accepted_account_id)` · `token_hash` UNIQUE(SHA-256 hex, 원문 저장 0) · status `PENDING|ACCEPTED`
- API: `GET /api/admin/sellers/{sellerId}/members` · `POST /api/admin/sellers/{sellerId}/invitations`(201, 토큰 1회 · 7일 `seller.invitation.ttl`) · `POST /api/seller-invitations/accept`(소비자 평면, 계정은 `X-User-Id` 에서만)
- `SellerMemberService`(초대·목록·수락·`revokeMemberRoles`) · `SellerMemberPersistence`(수락 DB 반쪽 = 한 트랜잭션: 셀러 ACTIVE 재확인 → 조건부 ACCEPTED → 구성원 upsert) · IAM 부여 뒤 DB 실패 시 **보상 회수** · `AccountServiceSellerSiteRoleClient`(기존 provisioner 와 같은 RestClient·교환 토큰·테넌트 경로)
- `RegisterSellerService` — `suspend`·`close`·`suspendByLockedAccount` 가 `revokeMemberRoles` 를 부른다(사람 계정 lock 호출 없음)
- 🔴 기존 볼륨 IT `ArtistGoodsSeedOnExistingVolumeIntegrationTest` — 업그레이드·재실행을 **target `21`** 로 고정(안 하면 V22 가 «V21 만 적용»·«재실행 no-op» 을 깬다). 다른 `flyway(null)` 기존 볼륨 IT: product-service 에 없음 · IAM 은 마이그레이션 추가 없음

**ecommerce gateway** — product 라우트에 `/api/seller-invitations/**`(SecurityConfig 기본 갈래 = 인증 + `CUSTOMER`) · `RouteService` 한 줄

**console-web** (셀러 화면 파일만) — `SellerMembers.tsx`(목록·초대·토큰 1회 표시, ACTIVE 셀러만 초대) · `SellerDetail` 에 붙임 + 정지/폐점 확인 문구에 «구성원 셀러 권한 회수(잠금 아님)» · 프록시 `sellers/[id]/members`(GET) · `sellers/[id]/invitations`(POST, zod → 422) · 훅 · 타입 · 샘플 픽스처(`/members` 경로, 없으면 샘플 방문자에게 503)

## 테스트

- IAM: `ConsumerSiteRoleWriteUseCaseTest`(16 — 🔴 이메일 대조군 · 닫힌 목록 · 멤버십 없음/LEFT · 사이트 계정 · 회수가 lock·멤버십을 안 건드림) · `ConsumerSiteRoleControllerSliceTest`(8) · `ConsumerSiteRoleWriteIntegrationTest`(MySQL, ⚪ 아래)
- product-service: `SellerMemberServiceTest`(12 — 🔴 AC-1 대조군 한 시험 · 이중 제출 · 해시만 저장 · 비활성 셀러 · 다른 테넌트 · 보상 회수 · AC-3 정지/폐점/다른 셀러 유지/재시도/재초대) · `SellerMemberLockDoesNotSuspendSellerTest`(3 — AC-4, **실제** 소비자→`RegisterSellerService`) · `SellerInvitationTest`(3) · `AccountServiceSellerSiteRoleClientTest`(5, WireMock) · `SellerMemberControllerSliceTest`(7) · `SellerMembersMigrationTwinTest`(1, h2 실적용) · `SellerMemberIntegrationTest`(Postgres, ⚪)
- console-web: `ecommerce-seller-members.test.tsx`(7) · `ecommerce-seller-members-proxy.test.ts`(6)

## 로컬 판정

| 명령 | 결과 |
|---|---|
| `./gradlew :projects:iam-platform:apps:account-service:test --offline` | rc=0 · 626 tests · 0 fail · 47 skipped(Docker 의존) |
| `./gradlew :…:product-service:test :…:gateway-service:test --offline` | rc=0 · product 426 / 0 fail · gateway 148 / 0 fail |
| `./gradlew :projects:iam-platform:apps:auth-service:test --tests '*TenantClaimPoolPrincipalTest*'` | rc=0 · 11/0 (AC-2 발급 쪽: «시드 ∪ 그 사이트 역할 — 스토어 SELLER 는 스토어 토큰에만», BE-615 기존 시험) |
| console-web `npx vitest run` | rc=0 · 330 files · 3703 tests |
| console-web `npx tsc --noEmit` · `npx next lint` | rc=0 · rc=0 |
| 가드(스테이지 후) `check-flyway-version-collision` · `check-error-code-registry` · `check-domain-error-code-registry` · `check-dev-seed-migration-band` · `check-seed-catalogue-parity` · `check-flyway-unresolvable-placeholder` · `check-controller-slice-naming` · `check-gateway-drift` · `check-internal-caller-addresses` · `check-jwt-claims-registry` · `check-message-backticks` · `check-claude-reference-integrity` · `check-shared-lib-jpa-scan` · `check-service-map-drift` · `check-lifecycle-stage-dirs` | 전부 rc=0 (`check-controller-slice-naming` 은 처음 rc=1 — 시험 이름 `…ControllersSliceTest` → `SellerMemberControllerSliceTest` 로 고친 뒤 0) |

**bite (복사본으로 되돌림, `git checkout` 아님)**
1. IAM 이메일 검사 끄기(`if (false && !sameEmail…)`) → `ConsumerSiteRoleWriteUseCaseTest` **16 중 1 빨강**(🔴 대조군: `SiteRoleEmailMismatchException` 기대, 실제는 다음 단계 예외) → 복원 후 16/0
2. `RegisterSellerService` 의 회수 호출 3곳 무력화 → `*SellerMember*` **22 중 6 빨강**(정지·폐점·다른 셀러·재시도·재초대·기계 계정 잠금→회수) → 복원 후 전체 초록

## ⚪ 측정 안 함 (이유)

- **Testcontainers IT 셋** — `ConsumerSiteRoleWriteIntegrationTest`(MySQL) · `SellerMemberIntegrationTest`(Postgres) · `ArtistGoodsSeedOnExistingVolumeIntegrationTest`(target 21 고정분): `@Tag("integration")` 이고 이 호스트는 Docker 가 꺼져 있다 ⇒ CI `integrationTest` 가 첫 실행이다. 그 사이 네이티브 INSERT 의 FK·JPQL 조인·조건부 UPDATE 는 **인메모리 대역으로만** 잰 상태다.
- **AC-2·AC-3 의 «실제 토큰»** — 수락/정지 뒤 스토어·팬 토큰을 실제로 발급해 본 적 없다(스택 기동 필요). 판정은 **조합**이다: ① IAM 이 `(account, ecommerce, SELLER)` 만 쓰고/지운다(단위·IT) ② 발급은 «시드 ∪ **그 사이트** 역할»(BE-615 `TenantClaimPoolPrincipalTest` 11/0) ③ 회수는 계정·멤버십을 안 건드린다(단위·IT) ⇒ 스토어 `["CUSTOMER","SELLER"]` → 정지 뒤 `["CUSTOMER"]`, 팬은 `SELLER` 없음. 라이브 확인은 재굽기 뒤 창의 몫이다.
- **스토어 수락 화면** — In Scope 에 없다(product-service API · 콘솔 초대만). 지금 수락은 로그인한 스토어 토큰으로 `POST /api/seller-invitations/accept` 를 직접 불러야 한다 ⇒ **web-store 수락 화면은 후속 티켓이 필요하다**(이 티켓은 기안하지 않았다 — 소유자 판단).
- 콘솔 e2e(`nightly-e2e.yml`) — 셀러 화면을 건드리는 e2e 스펙이 **없다**(grep `ecommerce/sellers`·`seller-detail` in `*.spec.ts` = 0건) ⇒ 머지 뒤 nightly 에서 볼 것도 없다.

## Acceptance Criteria 판정

- [x] **AC-1** — `SellerMemberServiceTest#ac1_controlGroup`(한 시험: 초대 없음 404 · 다른 이메일 403 + 행·역할 0 · 거절이 초대를 소모하지 않음 · 올바른 수락 성공 · 다른 계정 재사용 409 · 만료 410) + IAM 쪽 대조군 `ConsumerSiteRoleWriteUseCaseTest` + bite 1
- [x] **AC-2** — 조합으로(위 ⚪ 두 번째 줄): IAM 쓰기 `(account, ecommerce, SELLER)` 한 행 · 팬 사이트 `SELLER` 는 닫힌 목록 밖(`sellerOnFanSite_notGrantable`) · 발급 규칙 BE-615 시험
- [x] **AC-3** — `SellerMemberServiceTest$Revocation`(정지·폐점 → revoke, lock 은 기계 계정에만) + IAM `removesRole_neverLocksOrLeaves` + bite 2. 실제 토큰은 ⚪
- [x] **AC-4** — `SellerMemberLockDoesNotSuspendSellerTest`(구성원 잠금 `consumer-pool`·스토어 테넌트 둘 다 → 셀러 ACTIVE · 기계 계정 잠금 → SUSPENDED + 구성원 회수)

## 이탈

- 오류 코드 10개 신설(티켓에 이름 없음) — 레지스트리·도메인 파일에 먼저 등록.
- 콘솔 샘플 픽스처(`src/shared/sample/fixtures/ecommerce.ts`)를 건드렸다 — 셀러 화면 밖 파일이지만, 안 하면 샘플 방문자에게 새 구성원 칸이 503 으로 뜬다.
- 게이트웨이 라우트 1줄(티켓 Scope 에 명시 없음) — 소비자 수락 경로가 product-service 에 닿으려면 필요하다.

---

## CORRECTION (2026-10-03 UTC) — 닫기 판정: review 유지

- (a) #4126 `MERGED` · (b) 스쿼시 `761f5a58b` 가 `origin/main` 에 포함 · (c) 머지 시점 rollup 실패 0/69 — iam A·B · ecommerce A·B·C 통합 잡 전부 실제로 돌아 SUCCESS.
- (d) **닫지 않는다.** AC-1(수락 대조군)·AC-4(구성원 잠금 ≠ 셀러 정지)는 시험으로 닫혔다(`SellerMemberServiceTest#ac1_controlGroup` · `SellerMemberLockDoesNotSuspendSellerTest`, CI 통합 SUCCESS). 그러나 **AC-2·AC-3 의 동사는 «토큰 역할 = …»** 이고, 수락·정지 뒤의 실제 토큰은 아직 한 번도 발급되지 않았다 — 지금 근거는 «IAM 이 쓰는 행 + 토큰 발급 규칙(`TenantClaimPoolPrincipalTest`)» 의 **조합**이지 관측이 아니다. 재굽기 뒤 창에서 스토어 토큰 `["CUSTOMER","SELLER"]` → 정지 → `["CUSTOMER"]` · 팬 토큰에 `SELLER` 없음을 실제로 읽어 닫는다.
