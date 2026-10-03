# Task ID

TASK-MONO-751

# Title

`ADR-MONO-079` D4-A — **콘솔 팬 화면**: 소속사 · 아티스트 · 그룹 관리 + BFF 라우트

# Status

review

# Owner

monorepo

# Task Tags

- platform-console
- frontend

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 (이커머스 상품·셀러 화면의 선례를 따르는 화면 작업)

---

# Dependency Markers

- **선행**: `TASK-MONO-748`(소속사 API) · `TASK-MONO-750`(운영자 관리 경로)

# Goal

콘솔에 팬 도메인 화면을 만든다 — 소속사 목록·생성·편집(셀러 연결 포함), 아티스트·그룹 목록·편집(소속 변경). 모양은 기존 `ecommerce/products/**` · `ecommerce/sellers/**` 화면과 BFF 프록시를 따른다.

# Scope

## In Scope

- console-web 라우트 · 기능 폴더 · BFF 라우트 · 콘솔 레지스트리의 `fan` 항목
- 단위 시험 · e2e(🔴 콘솔 전체 e2e 는 nightly 에서만 돈다 — 머지 뒤 다음 nightly 확인)

## Out of Scope

- 팬 커뮤니티 관리(대리 저작 금지 — 059)

# Acceptance Criteria

- [ ] **AC-1** — 플랫폼 운영자가 `fan-platform` 으로 전환해 소속사를 만들고 아티스트를 소속시킬 수 있다. → 🟡 **경로 전 구간 ✅, 살아 있는 한 번의 왕복은 ⚪** — 소속사 생성 화면(`/fan/agencies/new` → `POST /api/fan/agencies` → fan gateway `POST /api/v1/agencies`) · 아티스트 등록(소속사 선택 포함) · 아티스트/그룹 소속 변경(`PATCH …/agency`) 이 있고, 단위 시험이 **보내는 토큰 = assume 한 `fan-platform` 토큰 · 경로 · 본문 · 봉투 해제**를 고정한다(§ 구현 기록 § 시험). 플랫폼 운영자 데모 신원을 시드했다(소유자 결정). 콘솔 → IAM assume → fan 게이트웨이 → artist-service 를 실제로 한 번 도는 것은 Docker 꺼짐으로 **재지 못했다** — 머지 뒤 nightly/데모(AC-4) 몫.
- [x] **AC-2** — 소속사에 셀러를 연결하는 입력이 존재하는 셀러만 받는다(748 의 검증 오류를 화면에 표시). → 입력은 보이고(숨기지 않음), `422 STORE_SELLER_NOT_FOUND`/`STORE_SELLER_CLOSED` 는 검증 오류로, `503 STORE_SELLER_LOOKUP_UNAVAILABLE` 은 «확인할 수 없어 저장하지 않았다» 별도 상태로 표시 — `FanAgencyDetail.test.tsx` 6 · `fan-directory-api.test.ts`(라우트가 503 의 생산자 코드를 보존). 🔴 **지금은 모든 값이 503** — 748 의 `UnwiredStoreSellerDirectory`(전송 미결 → `TASK-MONO-759`). 화면이 그 사실을 미리 말한다.
- [x] **AC-3** — 고객사 운영자에게 `fan` 항목이 보이지 않는다(R3). → 두 층: ① admin-service 레지스트리가 비-플랫폼 운영자에게 `fan-platform` 을 **어느 상품에도** 나열하지 않는다(배정 행이 있어도) — `ConsoleRegistryUseCaseTest$FanProductPlatformOperatorOnly` 3 · ② 콘솔 사이드바의 「팬 디렉터리」 는 레지스트리가 `fan` 을 테넌트와 함께 줄 때만 렌더 — `fan-nav.test.tsx` 6. 둘 다 물림으로 빨강 확인(§ 물림).
- [ ] **AC-4** — 머지 뒤 다음 nightly e2e 결과 확인. → ⏳ 머지 뒤. 🔵 nightly 콘솔 e2e 에 팬 화면 스펙은 **없다**(새로 쓰지 않음 — 데모 플랫폼 운영자로 로그인하는 e2e 하네스가 없다). nightly 가 재는 것은 「레지스트리 7개 상품 파싱 + 사이드바 렌더가 기존 스펙을 깨지 않는가」 뿐이다(§ e2e 영향).

# Related Specs

- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D4-A
- `projects/platform-console/specs/`

# Related Contracts

- console-web(같은 출처 라우트, 서버 측) ↔ fan gateway(관리 경로) — 🔵 2026-10-02 `TASK-MONO-755` 정정: 원래 «console-bff ↔ fan gateway» 였으나 `ADR-MONO-081`(A) 로 콘솔 합성·프록시는 모두 console-web 서버다. 이 티켓의 «BFF 라우트» 는 `ecommerce/products/**` 와 같은 console-web 라우트를 뜻한다 — console-bff 에 새 코드를 쓰지 않는다.

# Edge Cases

- 소속사 삭제 시 소속 아티스트가 있으면 거절 또는 보관 — 748 의 규칙을 따른다.

# Failure Scenarios

1. 화면은 열리는데 전환 전 API 호출로 403 — 콘솔의 «전환 뒤에 묻는다» 규칙.

---

# 구현 기록 (2026-10-02 UTC · 분석·구현=Opus 5.5)

## 소유자 결정 (2026-10-03, 메인 세션 경유 — 원문 그대로)

> Demo visibility: **«플랫폼 운영자 데모 계정 시드»** — add ONE platform-operator demo identity (admin `admin_operators.tenant_id='*'`, with the credentials/IdP rows it needs) to the demo/dev seeds, and mention it in the demo guidance where `demo@demo.com` is described. R3 stays as is; `demo@demo.com` stays a customer (demo-corp) operator.

이행: `platform@demo.com` · 운영자 id `demo-platform` · 홈 테넌트 `'*'` · 역할 행 0 · 배정 행 0 · 비밀번호는 기존 데모와 **같은 Argon2id 해시**(새 평문 0). 안내 세 곳 — 콘솔 로그인(`DemoLoginCredentials`) · 론처(`infra/demo/aws/site/index.html`, 뷰어와 같은 모양) · 전역 가이드(`GlobalGuideScreen`).

## 무엇을 바꿨나

**iam / admin-service**
- `ProductCatalog` — 7번째 항목 `fan`(`Fan Platform`, 테넌트 슬러그 `fan-platform`, `baseRoute` `/fan`). 750 후속 «`ProductCatalog` 에 `fan` 없음» 해소.
- `ConsoleRegistryUseCase` — 비-플랫폼 운영자의 `tenants` 에서 `AdminOperator.isPlatformOperatorOnlyTenant`(=`fan-platform`)를 **항상** 뺀다(배정 행이 있어도). assume 게이트 step 2b 와 같은 술어 ⇒ 고객사 운영자의 `fan.tenants = []`.
- `migration-dev/R__seed_demo_platform_operator.sql`(신규) — 위 결정의 운영자 행.
- 계약 `console-registry-api.md`(7개 · `fan` 행 · 선택 규칙 (4)) · `multi-tenancy.md`.

**iam / auth-service**
- `migration-dev/R__seed_demo_platform_operator_credential.sql`(신규, `iam` 테넌트 · account_id `…ad06`) · `DemoPlatformOperatorSeedTest`(신규 4) · `build.gradle` 외부 입력 1줄(형제 파일 편집이 시험을 깨우게).

**fan / gateway-service**
- `allowed-audiences` 기본값에 `platform-console-web` 추가(mode **SHADOW 그대로**). assume 토큰의 `aud ∋ platform-console-web` 은 `AssumeTenantExchangeIntegrationTest` AC-6 가 이미 고정한 사실. `AudienceShippedConfigTest` · `architecture.md` 갱신. 750 후속 «allowed-audiences 에 콘솔 client 없음» 해소.

**platform-console / console-web**
- `features/fan-directory/**`(신규) — 서버 클라이언트(`callFlatEnvelopeGateway` 프로필 `fan`, 샘플 분기 먼저) · zod 타입 · 섹션 상태 · 훅 · 화면(소속사 목록/등록/상세[이름 변경·보관·셀러 연결], 아티스트 목록/등록/상세[프로필·공개/보관·소속], 그룹[ID 로 열기·생성]/상세[멤버·소속]) · 팬 전용 오류 문구(공유 `messageForCode` 의 `GROUP_NAME_CONFLICT` 는 IAM 운영자 그룹 뜻이라 안 씀).
- `app/(console)/fan/**`(신규 페이지 9 + `layout.tsx` = `DomainTenantGate productKey="fan"` + `_eligibility.ts`) · `app/api/fan/**`(같은 출처 라우트 11 핸들러 · console-bff 무변경).
- `ProductKeySchema` 에 `fan`(생산자와 같은 PR — 고정 멤버십 가드) · `env.ts` `FAN_GATEWAY_BASE_URL`/`FAN_TIMEOUT_MS` · `FanUnavailableError`.
- 사이드바: `NavParent.productKey?`(선택적 레지스트리 게이트) + `visibleGroups()` + `ConsoleSidebarNav availableProductKeys` — `(console)` 레이아웃이 「테넌트가 있는 상품」 을 넘긴다. 게이트 없는 기존 항목은 바이트 동일.
- 권한 지도 행 3(`DomainKey` 에 `fan`) · 샘플 원장(`fan` 표면 `pending` · 화면 9 `pending`).
- 계약 `console-integration-contract.md` § 2.2 표 · **§ 2.4.11 신설**.

**demo**
- `docker-compose.yml`(console-web 에 `FAN_GATEWAY_BASE_URL`) · `infra/demo/demo.env` — 가드 (u) 가 요구하는 짝. Traefik alias `fan-platform.${DEMO_DOMAIN}` 은 이미 있었다.
- `verify-demo-wrapper.sh` (z11) — 론처의 `c-platform-email` ↔ 자격증명 시드 이메일 컬럼 대조(뷰어와 같은 모양, 대조군 포함).

## 토큰이 artist-service 에 닿는 길

브라우저 → console-web 같은 출처 `/api/fan/**`(HttpOnly 쿠키, 브라우저는 토큰을 못 봄) → 서버가 `getDomainFacingToken()` = **assume 한 토큰**(`tenant_id=fan-platform` · `roles=[FAN_OPERATOR]` — `fan` 구독 V0031 에서 파생) 을 `Authorization` 에 붙여 `FAN_GATEWAY_BASE_URL/api/v1/{agencies,artists,artist-groups}/**` → fan 게이트웨이(테넌트 게이트 동등성 통과 · 역할 입장 · `/api/v1`→`/api`) → artist-service 디렉터리 매처(`ADMIN_ROLES ∋ FAN_OPERATOR`). `X-Tenant-Id` 없음, 운영자 토큰 안 씀. «전환 뒤에 묻는다»: `fan` 레이아웃의 `DomainTenantGate productKey="fan"` 이 전환 전(「테넌트 선택」)·다른 테넌트(불일치 안내)를 막아 페이지가 거절될 호출을 하지 않는다.

## 이탈 (티켓 문구와 다르게 한 것)

1. **그룹 «목록» 없음** — artist-service 에 그룹 목록 API 가 없다(계약 § Artist groups). 화면은 «ID 로 열기 + 생성» 이고 그 사실을 화면에 적었다. 목록은 생산자 변경이라 이 티켓에서 만들지 않았다.
2. **아티스트 목록은 PUBLISHED 만** — 생산자 디렉터리 검색 규칙. 새 아티스트(DRAFT)는 등록 직후 상세로 이동하고, 초안·보관은 «ID 로 열기».
3. **그룹 멤버 추가/제거·팬덤 화면 없음** — AC 는 «소속 변경» 까지. 멤버는 읽기만.
4. **2뎁스 «가이드 → 개요» 규칙(TASK-PC-FE-297)의 예외 1** — 「팬 디렉터리」 는 가이드·개요 없이 세 화면만 있다. `sidebar-nav-order-icons.test.tsx` 는 레지스트리 게이트 부모를 **이름으로** 빼고 나머지 6 도메인엔 규칙을 그대로 건다(일곱째 비게이트 부모는 여전히 빨강).
5. **R3 를 화면만이 아니라 레지스트리에서도** 닫았다 — 티켓은 콘솔 항목만 말했지만, 배정 행이 남은 고객사 운영자에게 레지스트리가 `fan-platform` 을 주면 스위처에 «전환하면 거절되는» 테넌트가 뜬다. assume 게이트와 같은 술어로 한 줄.

## 시험 (명령 · rc · 개수) — 파이프 없이 `> log; echo rc=$?`

| 대상 | 명령 | rc | 결과 |
|---|---|---|---|
| console-web 단위 | `npx vitest run`(인자 없음 — `--maxWorkers` 안 줌) | 0 | **334 파일 · 3734 시험** 통과. 신규 4 파일 29 시험(`fan-directory-api` 12 · `fan-nav` 6 · `FanAgencyDetail` 6 · `fan-eligibility` 5) |
| console-web 타입 | `npx tsc --noEmit` | 0 | |
| console-web 린트 | `npx next lint` | 0 | No ESLint warnings or errors |
| iam admin-service | `./gradlew :projects:iam-platform:apps:admin-service:test` | 0 | 888 · 0 fail · 58 skipped(`@Tag("integration")`) — `ConsoleRegistryUseCaseTest` 21(신규 3) |
| iam auth-service | `…:auth-service:test` | 0 | 1015 · 0 fail · 33 skipped — `DemoPlatformOperatorSeedTest` 4 |
| fan gateway-service | `:projects:fan-platform:apps:gateway-service:test` | 0 | 50 · 0 fail — `AudienceShippedConfigTest` 7 |
| 가드 | `check-dev-seed-migration-band.sh` · `check-gateway-drift.sh` · `check-demo-resolver-copies.sh` · `check-message-backticks.sh` · `check-internal-caller-addresses.sh` · `check-build-context-declarations.sh` · `check-seed-catalogue-parity.sh` · `node check-fetch-resolution.mjs` · `node check-client-graph-backend-origins.mjs` | 전부 0 | 스테이지 뒤 재실행 |
| 데모 래퍼 | `bash infra/demo/verify-demo-wrapper.sh`(비-live) | 0 | «정적 검증 PASS» — (u) «콘솔 '.local' 기본값 키 20 개가 demo.env + compose 양쪽에» · (z11) 플랫폼 운영자 이메일 ↔ 시드 일치 · 대조군 통과 |

pnpm: 이 worktree 에 `pnpm install --frozen-lockfile` 을 **실제로** 돌렸다(정션 아님, rc=0).

## 물림(bite) — 둘, 전부 복사로 원복(`cmp` 동일, `git checkout` 안 씀)

1. console-nav-config 의 `productKey: 'fan'` 줄 삭제(게이트 제거) ⇒ `fan-nav` 4 · `sidebar-nav-order-icons` 1 빨강(rc=1). 원복 후 초록.
2. admin `ConsoleRegistryUseCase` 의 R3 줄을 `if (false && …)` ⇒ `customerOperator_assignmentRow_stillRefused` 빨강(21 중 1, rc=1). 원복 후 초록. 🔵 다른 두 R3 시험(홈 demo-corp)은 **원래도** 초록이다 — 그 모양은 기존 «홈 ∩ 바인딩» 이 이미 막는다. 이 줄이 새로 막는 것은 «배정 행이 있는 고객사 운영자» 뿐이고, 그 칸만 빨개진 것이 그 사실의 측정이다.

## e2e 영향

`projects/platform-console/**/e2e` grep: 바꾼 testid·heading·nav 를 참조하는 스펙 0(`overview-consolidation.spec.ts` 는 `nav-dashboards`·`nav-erp*` 만 — 무변경). 새 testid 는 전부 `fan-*` / `nav-fan*`. 다음 nightly(`nightly-e2e.yml` 콘솔 잡)가 재는 것: 레지스트리 응답이 7개 상품이 된 뒤에도 카탈로그·사이드바가 파싱·렌더되는가(그 잡의 운영자가 `'*'` 이면 「팬 디렉터리」 부모가 하나 더 보인다 — 개수를 세는 스펙 없음). 팬 화면 자체는 nightly 가 **재지 않는다**.

## 로컬 판정

- ✅ 위 단위·린트·타입·가드 전부 rc=0.
- ⚪ **Testcontainers(`@Tag("integration")`) 미실행** — Docker 꺼짐. 해당: `ConsoleRegistryIntegrationTest`(상품 수 6→7 로 갱신 — CI 판정) · 새 R__ 시드가 실제 MySQL 에 적용되는가 · 데모 운영자 로그인/토큰 교환.
- ⚪ **콘솔 → assume → fan 게이트웨이 → artist-service 실왕복**(AC-1 의 마지막 칸) — 하네스 없음 + Docker 꺼짐.
- ⚪ **데모에 그 신원이 보이는 시점** — 데모 백엔드는 구워진 클론에서 돈다: 새 R__ 시드는 **재굽기 뒤**에야 데모 DB 에 있다. 론처 문구는 그 전에도 보인다(뷰어와 같은 처지).
- 🔵 audience: SHADOW 라 이전에도 거절은 없었고(로그만), 이제 콘솔 client 가 목록에 있어 그 mismatch 로그도 사라진다. ENFORCE 전환은 `TASK-MONO-697` 몫 — 그 티켓의 «측정된 목록» 에 이 값이 들어갔음을 알린다.
- 🔴 **알려진 한계(시드 머리 주석에 기록)**: `'*'` 운영자는 팬 디렉터리만이 아니라 **모든 등록 테넌트**를 assume 할 수 있다(이 코드베이스의 «플랫폼 운영자» 정의). 공개 데모에서 이 계정은 도메인 테넌트들도 운영할 수 있다. 역할 행이 없어 IAM 관리 화면은 403.

## 후속 (이 티켓이 만든 사실)

- `TASK-MONO-759` — 셀러 연결 전송이 배선되면 소속사 상세의 «지금은 거절됩니다» 안내(`fan-agency-seller-unwired-note`)를 걷을 것.
- 그룹 목록 API(생산자) — 생기면 `/fan/groups` 를 목록으로.

## CORRECTION (2026-10-03 UTC)

위 «로컬 판정» 의 🔴 **알려진 한계**(`'*'` 데모 운영자가 모든 등록 테넌트를 assume 할 수 있다)는 **닫혔다**. 위 본문은 그대로 두고 여기 덧붙인다.

**소유자 결정 (2026-10-03, 메인 세션 경유 — 원문)**: **«데모 운영자는 팬 전용으로»** — the demo platform-operator identity must be able to assume **only `fan-platform`**.

**기제 — `admin_operators.confined_tenant_id`(데이터 기반, 이메일·운영자 id 하드코딩 없음)**
- 왜 이것: 기존 기제를 먼저 찾았다 — 배정 행(`operator_tenant_assignment`)은 `'*'` 운영자에겐 **보지도 않는다**(step 2 가 그 앞에서 `assigned=true`), 파트너십·org-node 는 다른 축. 운영자별 제한은 없었다. 그래서 가장 좁은 명시적 기제를 새로 뒀다: 비-NULL 이면 «이 운영자가 assume 할 수 있는 유일한 테넌트». NULL = 제한 없음(기존 모든 운영자 · 백필 없음 ⇒ 실제 플랫폼 운영자 동작 불변). 쓰기 API 없음(시드/데이터로만).
- admin-service `V0046__add_admin_operator_confined_tenant_id.sql`(다음 빈 번호 — prod 최대 V0045, dev 최대 V0028) · `AdminOperatorJpaEntity`/`OperatorView`(옛 13-인자 생성자 유지 ⇒ 기존 호출 12곳 무변경)/`JpaAdminOperatorAdapter`.
- `OperatorAssignmentCheckUseCase` **step 1b** — ACTIVE 확인 직후, **step 2(`'*'` = 모든 테넌트)보다 먼저** `isConfinedAway(confined, tenant)` 면 `assigned=false`(기존 거절과 같은 답 — 새 오류 코드 없음, auth-service 쪽은 기존 `invalid_grant`). 좁히기만: 같은 테넌트면 아래 단계가 그대로 판정 ⇒ R3(step 2b)는 묶인 고객사 운영자에게도 `fan-platform` 을 거절.
- `ConsoleRegistryUseCase` — 모든 상품의 `tenants` 를 마지막에 그 테넌트로 좁힌다(같은 술어) ⇒ 데모 운영자의 레지스트리는 `fan → [fan-platform]`, 나머지 전부 `[]`(iam 포함).
- 시드 `R__seed_demo_platform_operator.sql` — `confined_tenant_id='fan-platform'`(INSERT + ON DUPLICATE UPDATE), «KNOWN LIMIT» 문구 삭제 → «선택 4» 로 대체. `DemoPlatformOperatorSeedTest` +1(값 고정).
- 계약·스펙 먼저: `internal/auth-to-admin.md` 판정 규칙 0 · `console-registry-api.md` 선택 규칙 5 · `multi-tenancy.md` · admin `data-model.md`(컬럼 행 + 마이그레이션 노트). `ADR-MONO-079` 끝에 «R3 보강 기록» 한 절(덧붙임만). 전역 가이드 문구 한 줄.

**대조군 (증거)**

| 칸 | 시험 | 결과 |
|---|---|---|
| 데모 운영자 → `fan-platform` 허용 | `ConfinedOperatorAssumeGateTest.demoPlatformOperator_fanPlatform_isAssigned` · IT `confinedPlatformOperator_fanPlatform_assigned` | ✅ / ⚪(IT) |
| 데모 운영자 → `ecommerce`·`wms`·`scm`·`erp`·`finance`·`demo-corp`·`iam`·`acme-corp` 거절, step 1b 아래는 묻지도 않음 | `…everyOtherTenant_isRefused` ×8 · IT `…otherTenants_refused` | ✅ / ⚪(IT) |
| 일반 `'*'` 운영자 → 불변(모든 테넌트) | `…normalPlatformOperator_unchanged` ×5 · `ConsoleRegistryUseCaseTest.unconfinedPlatformOperator_unchanged` · IT 의 `SUPER_SUBJECT → ecommerce` | ✅ / ⚪(IT) |
| `demo@demo.com` 모양(demo-corp) → 자기 테넌트 불변 · `fan-platform` 여전히 거절 | `…demoCustomerOperator_ownTenant_unchanged` · `…demoCustomerOperator_fanPlatform_stillRefused` | ✅ |
| 좁히기만(묶여도 열리지 않음) | `…confinementNeverOpens_customerConfinedToFan_stillRefused` | ✅ |
| 레지스트리 = `fan` 만 | `ConsoleRegistryUseCaseTest.confinedPlatformOperator_listsOnlyFan` | ✅ |

**물림**: step 1b 를 `if (false && …)` ⇒ `ConfinedOperatorAssumeGateTest` 18 중 8 빨강(«ecommerce not-assigned» 포함, rc=1) → 복사로 원복(`cmp` 동일).

**명령 (rc · 개수)**: admin-service `:test` rc=0 — 908 · 0 fail · 58 skipped(`ConfinedOperatorAssumeGateTest` 18 · `ConsoleRegistryUseCaseTest$FanProductPlatformOperatorOnly` 5) · auth-service `:test` rc=0 — 1016 · 0 fail · 33 skipped(`DemoPlatformOperatorSeedTest` 5) · console-web `tsc` 0 · `next lint` 0 · `GlobalGuideScreen.test.tsx` 0 · 가드는 커밋 기록 참조.

**⚪**: `OperatorAssignmentCheckIntegrationTest` 의 새 2칸 · V0046 이 실제 MySQL 에 적용되는지 — Docker 꺼짐(CI 판정). 데모 DB 반영은 여전히 재굽기 뒤. 새 오류 코드 없음 ⇒ 오류 코드 레지스트리 변경 없음.
