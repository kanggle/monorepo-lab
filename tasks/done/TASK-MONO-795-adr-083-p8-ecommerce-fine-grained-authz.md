# Task ID

TASK-MONO-795

# Title

`ADR-MONO-083` P8 — E-commerce 세분 서버 인가: 관리 영역별 조회/변경 역할 · 게이트웨이 단독 판정을 **서비스 재검사**까지 · `ECOMMERCE_OPERATOR` 는 진입 역할로 · iam 카탈로그 확장

# Status

done

# Owner

monorepo

# Task Tags

- code
- api
- test
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (10 서비스 · 게이트웨이 · 원자 PR — 크기가 크면 AC-0 에서 서비스 묶음별 슬라이스로 나눈다)
> 🔴 **cross-project 원자 PR(슬라이스마다)**: ecommerce 인가 + iam 카탈로그 · «이전 호환» E-commerce 세트 갱신.

# Dependency Markers

- **선행**: `TASK-BE-631` · `TASK-BE-632`.
- 형제: `TASK-MONO-792` · `793` · `794`.

# Goal

지금 관리 표면의 유일한 판정은 게이트웨이 `AccountTypeEnforcementFilter` 의 `roles ∋ ECOMMERCE_OPERATOR` 이고(`:99-108`), 서비스 다수가 `permitAll` 로 헤더를 믿는다(`order-service …/OrderSecurityConfig.java:146-157`). 주문 · 상품 · 회원 · 정산 · 프로모션 · 배송 · 알림 · 검색을 조회/변경으로 나누고, **서비스 단에서도** 같은 역할을 검사한다.

# Scope

## In Scope

- 계약 먼저: ecommerce 서비스 명세 · 관리 API 계약에 역할 · 판정 표.
- 서비스별 재검사(`OperatorRoleGuard` 사본들을 한 술어로 — 형제 서비스 grep 먼저, CLAUDE.md § 10) · 게이트웨이 경로 판정과 일치.
- `ECOMMERCE_OPERATOR` = 진입 역할. 소비자(`CUSTOMER`) 경로 · «운영자-공개» 읽기 트리(`/api/promotions` · `/api/shippings` · `/api/notifications`) · `/internal/sellers/**` 워크로드 분기 무변경.
- iam 카탈로그 · «이전 호환» E-commerce 세트 · 시스템 프리셋.

## Out of Scope

- 판매자 범위(`X-Seller-Scope`) 주입(ADR-030 Step 4 — 데이터 범위).

# Acceptance Criteria

- [x] **AC-0** — 재측정: 관리 컨트롤러 전수 · 게이트웨이 경로 판정 · 서비스별 재검사 유무 · 업무 구분안(OD-6) · 슬라이스 분할 계획. → § AC-0 기록(2026-10-16 UTC) — 측정 완료 · STOP 뒤 § 소유자 결정(2026-10-16 UTC) 수령 — Q1 역할 13 · Q2 정산 통제 역할 없음 · Q3 `libs/java-web` 기전 하나 · Q4 원자 PR(슬라이스 분할 = **하지 않는다**).
- [x] **AC-1** — 🔴 대조군: 주문 세트만 → 상품 변경 403 · 주문 200 / 조회 세트 → 변경 403 / 진입 역할만 → 업무 변경 403 / 🔴 게이트웨이를 거치지 않은 서비스 직접 호출도 같은 판정. → 서비스 게이트 시험 9(order 5 · product 24 · user 3 · settlement 9 · notification 5 · search 1 — 슬라이스 · promotion 7 · shipping 4 — 응용 서비스, **헤더만 넣는 직접 호출** = 게이트웨이 없는 판정): 다른 영역 세트 → 전 경로 403 · 영역 조회 세트 → 쓰기 403 · `ECOMMERCE_OPERATOR` 단독 → 403 · `CUSTOMER` · 빈/없는 헤더 → 403 · 대소문자 무시 · 부분 문자열 불인정. auth `ecom795_*`(주문 세트 → 진입 역할 + 주문 역할만 · 배정된 진입 역할 단독 → 0 · 파트너십 파생) · admin 진입 역할 위임 422. § Red · § Green.
- [x] **AC-2** — 이전 호환 대조군 · 소비자 경로 무변경 대조군. → 이전 호환 세 고리: V0059 IT(legacy-compat = 카탈로그 · `ECOMMERCE_OPERATOR` 세트 → 13 역할 · 파트너십 2 scope) · auth(`ecom795_legacyCompat_fullEcommerce` — legacy-compat → 진입 역할 + 13) · 서비스(게이트 시험의 legacy-compat 행 — 58 경로 전부 통과). 소비자: 게이트웨이 시험(업무 역할은 소비자 트리를 열지 않는다) · 브랜치 nightly web-store full-stack 14 passed(`account-type-guard` 2 포함) · ecommerce×wms 주문처리 고리 2/0. ⚪ 라이브는 AC-3.
- [x] **AC-3** — 배포 상태를 따로 적는다. → § 배포 상태 (2026-10-16 · 머지 전) · **§ 배포 상태 — 28차 창 (2026-10-16 UTC)**: `main` #4387 `2eef1d91f` · AMI `ami-00ef8cc1057cca59b` @ `8905d2c29` · 라이브 ✅.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D2 · D3 · § 새로 생기는 위험(E-commerce 행)
- `projects/ecommerce-microservices-platform/specs/services/*/architecture.md`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/*` · iam `admin-api.md` § 카탈로그

# Edge Cases

- 운영자-공개 읽기 트리에서 운영자/소비자 판정이 서비스 헤더로 내려가는 곳 — 새 역할로도 같은 분기.
- 플랫폼 와일드카드 GET 전용 — 무변경.

# Failure Scenarios

1. 게이트웨이 경로 판정에만 세분을 넣는다 — 서비스 간 직접 호출 · 헤더 위조가 우회한다.
2. 서비스마다 사본 술어를 따로 고친다 — 한 사본만 고쳐진다.

---

# AC-0 기록 — 2026-10-16 UTC (base `cf36d1609` = 794 머지 직후 · `origin/main` 과 차이 0) · 🔴 **STOP — 착수 전 소유자 결정 필요**

> 측정 = 코드 읽기(정적). 라이브 DB · 라이브 토큰은 열지 않았다(⚪). 이 기록 뒤 구현 커밋은 **없다** — Q1~Q4 의 답을 받은 뒤 계약부터 시작한다. 선례 = `TASK-MONO-792`(SCM) · `793`(ERP) · `794`(Finance) — 그 세 티켓의 상시 결정은 § 5 에서 **유추로 그대로 적용**하고 질문으로 올리지 않았다. 질문으로 올린 것은 OD-6 이 **글자 그대로 적용되지 않는 곳**과 **선례가 답하지 않는 구조 결정**뿐이다.

## 0. 선행 조건 — 충족

- `TASK-BE-631` · `TASK-BE-632` done. auth 의 진입 역할 파생(`OperatorRoleDerivation.ENTRY_ROLES` — SCM · ERP · Finance 3 행)과 ceiling 은 이미 데이터로 있다 ⇒ E-commerce 는 **한 행 + ceiling 갈래 하나**(`case "ecommerce" -> List.of("ECOMMERCE_OPERATOR")` → `ECOMMERCE_CEILING`).
- 🔵 **파생은 assume · 파트너십 경로에만 걸린다**(`TenantClaimTokenCustomizer.java:996` · `:1120`). 저장 역할 경로(`populateRoles` — 소비자 · 스토어프런트)는 건드리지 않는다 ⇒ web-store e2e 의 `e2e-no-customer-role` 픽스처(저장 역할 `["ECOMMERCE_OPERATOR"]` → 스토어프런트가 튕김, `web-store/e2e/fixtures/iam-consumer-seed.sql:99-111`)는 그대로 산다(추론 — 브랜치 nightly 로 확인 예정).
- Flyway 다음 빈 번호 = **`V0059`**(`origin/main` `cf36d1609` — `V0058` 이 마지막).

## 1. 지금 판정 — 게이트웨이 하나 + 서비스 사본 8

**게이트웨이** `AccountTypeEnforcementFilter.java:99-116` (메서드 · 경로만 보는 거친 판정):

| 경로 | 통과 조건 |
|---|---|
| `/api/admin/**` | `roles ∋ ECOMMERCE_OPERATOR` ∨ (`GET/HEAD` ∧ `tenant_id='*'` — TASK-BE-506 와일드카드 읽기) |
| 운영자-공개 트리 `/api/promotions` · `/api/shippings` · `/api/notifications` | `CUSTOMER` ∨ `ECOMMERCE_OPERATOR` (엔드포인트별 운영자/소비자 구분은 서비스) |
| `/internal/sellers/**` | 범위 `store.seller.read` ∧ `tenant_id=ecommerce` ∧ 안전 메서드 (워크로드 — 역할 무관) |
| 그 밖의 인증 경로(`/api/search/**` 포함) | `CUSTOMER` |

`X-User-Role` = `roles` 클레임 콤마 결합(`JwtHeaderEnrichmentFilter` · `JwtClaims.role`), 빈 값 = 역할 없음. 클라이언트가 보낸 `X-User-*` · `X-Tenant-Id` 는 먼저 벗긴다(`IdentityHeaderStripFilter.BASELINE_HEADERS`).

**서비스 단 재검사** — 있는 곳 8 사본, 없는 곳 2 컨트롤러 묶음:

| 서비스 · 표면 | 재검사 | 술어 사본 |
|---|---|---|
| order `/api/admin/orders/**` | ❌ **없음**(`OrderSecurityConfig.java:146-157` `permitAll` · `AdminOrderController` 무검사) | — |
| product `/api/admin/products/**` · `…/images/**` | ❌ **없음**(`AdminProductController` · `AdminProductImageController` — TASK-BE-366 이 서비스 검사를 걷어냄) | — |
| product `/api/admin/sellers/**` | ✅ `X-User-Role ∋ ECOMMERCE_OPERATOR` | `AdminSellerController.hasAdminRole` · `AdminSellerMemberController.validateAdminRole` (2) |
| user `/api/admin/users/**` | ✅ | `AdminUserController.hasAdminRole` (1) |
| settlement `/api/admin/settlements/**` | ✅ | `presentation/OperatorRoleGuard` (1) |
| promotion `/api/promotions/**`(운영자 7) | ✅ (응용 서비스) | `application/service/OperatorRoleGuard` (1) |
| shipping `/api/shippings/**`(운영자 4) | ✅ (응용 서비스) | `application/service/OperatorRoleGuard` (1) |
| notification `/api/notifications/templates/**` | ✅ | `TemplateController.hasAdminRole` (1) — 🔴 **이 사본만 대소문자 구분**(`equals`, 나머지 7 은 `equalsIgnoreCase`) = Failure 2 가 이미 한 번 일어난 흔적 |
| search `/api/search/admin/reindex` | ✅ | `SearchAdminController.hasAdminRole` (1) |

⇒ **Failure 1 은 지금 사실이다**: order · product(상품 · 이미지) 20 경로는 게이트웨이가 유일한 판정이다. 세분을 게이트웨이에만 넣으면 그대로 남는다.

## 2. 엔드포인트 전수 (운영자 경로 **58** = 조회 28 · 변경 30 — 컨트롤러 `@*Mapping` 전수)

| 영역(OD-6) | 서비스 · 경로 | 조회 | 변경 |
|---|---|---|---|
| 주문 | order `/api/admin/orders` | `GET /summary` · `/insights` · 목록 · `/{id}` (4) | 상태 전이 `POST /{id}/status` (1) |
| 상품(+판매자) | product `/api/admin/products` | 목록 · `/summary` · `/{id}` (3) | 등록 · `PATCH /{id}` · `DELETE /{id}` · 옵션 등록 · 옵션 `PATCH` · 옵션 `DELETE` · 재고 `PATCH /{id}/stock` (7) |
| 〃 | product `/api/admin/products/{id}/images` | 목록 (1) | `POST /upload-url` · 등록 · `PATCH` · `DELETE` (4) |
| 〃 | product `/api/admin/sellers` | `/summary` · 목록 · `/{id}` (3) | 등록 · `/provision` · `/suspend` · `/close` (4) |
| 〃 | product `/api/admin/sellers/{id}` | `/members` (1) | `/invitations` (1) |
| 회원 | user `/api/admin/users` | `/summary` · 목록 · `/{id}` (3) | **없음 (0)** |
| 정산 | settlement `/api/admin/settlements` | `/accruals` · `/sellers/{id}/balance` · `/commission-rates/{id}` · `/periods` 목록 · `/periods/{id}/payouts` (5) | 수수료율 `PUT` · 기간 개시 `POST /periods` · 🔴 기간 마감 `/close` · 🔴 **지급 실행** `/payouts/execute` (4) |
| 프로모션 | promotion `/api/promotions` | `/summary` · 목록 · `/{id}` (3) | 등록 · `PUT` · `DELETE` · 쿠폰 발급 `/{id}/coupons/issue` (4) |
| 배송 | shipping `/api/shippings` | `/summary` · 목록 (2) | 상태 `PUT /{id}/status` · 추적 갱신 `POST /{id}/refresh-tracking` (2) |
| 알림 | notification `/api/notifications/templates` | `/summary` · 목록 · `/{id}` (3) | 등록 · `PUT /{id}` (2) |
| 검색 | search `/api/search/admin` | **없음 (0)** | 재색인 `POST /reindex` (1) — 🔵 계약상 «게이트웨이로 노출 안 함 · 서비스 내부 포트만»(`search-api.md:68-73`). 실제로 게이트웨이는 `/api/search/**` 에 `CUSTOMER` 를 요구하므로 운영자 토큰은 거기서 403 — 이 경로의 운영자 호출자 = **0** |

**운영자 경로가 아닌 것(무변경 · 소비자/워크로드)**: `/api/orders/**` · `/api/payments/**` · `/api/reviews/**` · `/api/users/me/**` · `/api/wishlists/**` · `/api/coupons/**` · `/api/shippings/orders/{orderId}`(구매자 소유권 검사) · `/api/notifications/me/**` · `/api/notifications/vapid-public-key` · `/api/shippings/carrier-webhook`(공개) · `/api/seller-invitations/accept` · `/api/products/**`(공개 읽기) · `/api/search/products` · `/internal/sellers/**`(워크로드) · `/api/internal/**`(order · promotion — `client_credentials` 주체 고정 체인). 결제 환불 `POST /api/payments/{id}/refund` 는 `X-User-Id` 소유 검사만 하는 **구매자** 경로다(운영자 경로 아님).

## 3. 호출자 전수

- **서버 쪽 교차 도메인 HTTP 호출자 = 0**: ecommerce 운영자 경로를 부르는 코드는 ecommerce 밖에서 `platform-console` · 데모 시드 · iam dev 시드 주석뿐(`projects/*/apps/*/src/main` grep). 🔵 **WMS × ecommerce 주문처리 고리는 Kafka 뿐**(`ecommerce.fulfillment.requested.v1` → wms outbound `FulfillmentRequestedConsumer` · 출고 확정 → shipping) — HTTP · 운영자 토큰 없음. nightly `ecommerce-fulfillment-e2e-full`(Testcontainers order + shipping)의 시험 코드에 `X-User-Role` · `/api/admin` 0건 ⇒ **무영향**(추론 — 브랜치 nightly 로 확인).
- **도메인 안 운영자 토큰 호출 = 0**: 서비스 간 HTTP 는 전부 공개 · 소비자 · 워크로드 경로(search → `/api/products` · user → `/api/products/{id}` · review → `/api/orders/verify-purchase` · order → `/api/coupons/{id}/apply` · release · batch → `/api/internal/**` · `/api/search/products` · `/api/products`). 792 Q2 / 793 Q3 같은 «좁은 교차 허용» 이 **필요한 자리가 없다**.
- **개인/본인 엔드포인트(운영자)= 0**: 운영자 쪽 «나에 관한 것» 경로가 없다. `/me` 경로는 전부 소비자 것.
- **소비자 토큰 vs 운영자 토큰**: 소비자 = 스토어프런트 클라이언트 · 저장 역할 경로 → `CUSTOMER`(`RoleSeedPolicy.java:56`). 운영자 = assume-tenant → (지금) `ECOMMERCE_OPERATOR` / (이후) 업무 역할 + 파생 진입 역할. **업무 역할이 소비자 토큰에 실리는 길은 없다** — 파생 · 상한은 assume · 파트너십 경로에만 있다(§ 0). ⇒ 운영자-공개 트리의 운영자/소비자 분기는 «업무 역할 보유» 로 바꿔도 소비자 쪽 판정이 바뀌지 않는다.
- **콘솔**(같은 운영자 토큰 — `getDomainFacingToken`): E-commerce 9 화면 전부 `serverRequires = ECOMMERCE_OPERATOR`(`permission-map.ts:285-289,841-…` · 계약 `console-integration-contract.md` § 2.4.3.4). 진입 역할이 파생되므로 업무 역할 하나라도 가진 운영자는 **게이트웨이를 계속 통과**한다(코드 근거: 게이트웨이 무변경 + `withDerivedEntryRoles`). 화면별 업무 역할 신호 = 새 콘솔 티켓(굽기 라이브 뒤에만 머지 — PC-FE-342/343/344 와 같은 모양), 구현 단계에서 기안.
- **와일드카드 `'*'`(플랫폼 SUPER_ADMIN 기본 토큰 — 역할 없음)**: 게이트웨이가 `/api/admin/**` GET 을 연다. 서비스 단에서 실제로 200 이 되는 곳 = **재검사가 없는 order 조회 4 · 상품/이미지 조회 4** 뿐(운영 개요 ecommerce 카드 = `GET /api/admin/products?page=0&size=1`). 판매자 · 회원 · 정산 조회는 지금도 서비스가 403(`X-User-Role` 빈 값). federation e2e 스펙에 ecommerce 도메인 호출 0 · 콘솔 e2e 에 ecommerce 화면 호출 0(`global-setup.ts` 의 테넌트 목록뿐).
- **데모 시드** `infra/demo/seed/seed-ecommerce.sh:413-` — 운영자 assume(`ecommerce`)로 판매자 등록 · 활성화 · 수수료율 · 정산 기간 · 알림 템플릿 · 프로모션 · 쿠폰 발급 · 배송 상태. 그 운영자(`demo-operator`)는 `legacy-compat`(`R__seed_zz_demo_domain_permission_sets.sql`) ⇒ V0059 이전으로 따라온다(모든 변경 역할 필요 — 아래 Q 들의 답과 무관하게 legacy-compat 은 전부를 받는다). `demo-cs`(ecommerce 한정) 도 legacy-compat.
- **이전할 세트**: `ECOMMERCE_OPERATOR` 를 담은 세트 = 정적으로 `ecommerce-full`(`V0052:156`) · `legacy-compat` · `all-domains-full` + ⚪ 관리자가 만든 테넌트 세트 · 파트너십 `delegated_scope`/`participant_scope`. 라이브 행 수 ⚪ → 672 다음 창.
- **시험 영향(구현 때)**: ecommerce 시험 **45 파일 · 269 곳**이 `ECOMMERCE_OPERATOR` 를 쓴다(슬라이스 · IT · 게이트웨이 필터 시험 — 대부분 «운영자 헤더 → 200» 단언이라 «진입 역할만 → 403» 으로 갈라진다).

## 4. OD-6 기본값과 대조 — 그대로 못 가는 점

1. **회원 = 조회만 있다**(변경 0). «영역마다 READ/WRITE» 를 글자 그대로 하면 `ECOMMERCE_MEMBER_WRITE` 는 **어떤 서버도 검사하지 않는 카탈로그 행**이 된다 — ADR-083 본문(«세분 세트는 그 도메인 서버가 세분 역할을 검사한 뒤에만 카탈로그에 들어간다») · D2 와 충돌.
2. **검색 = 조회 0 · 변경 1(재색인)이고, 그 하나는 운영자가 게이트웨이로 닿을 수 없다**. 글자 그대로면 `ECOMMERCE_SEARCH_READ`(검사처 0) + `ECOMMERCE_SEARCH_WRITE`(콘솔 호출자 0 · 게이트웨이 도달 불가) — 둘 다 «세분된 척» 이 된다.
3. **정산 지급 실행 · 기간 마감** — 돈이 나가는 쓰기다. 티켓 · ADR 은 E-commerce 의 고위험 쓰기를 따로 지목하지 않았다(상시 결정 = CONTROL 은 지목된 곳에만). 그러나 794 Q1(원장 기간 마감 · 환율 덮어쓰기) 과 **모양이 같다** ⇒ 유추로 정하지 않고 묻는다.
4. **«한 술어»(Failure 2)의 집** — 선례 셋은 필터 체인(`SecurityConfig`)에서 판정해 사본 문제가 없었다. E-commerce 는 서비스 8 곳의 헤더 술어 사본이고, 사본을 하나로 모으는 집이 **구조 결정**이다(CLAUDE.md: 프로젝트 범위 `libs/` 는 ADR 필요 · 저장소 루트 `libs/` 는 프로젝트 무관 내용만).

## 5. 상시 결정의 유추 적용 (질문 아님 — 792 · 793 · 794 그대로)

- 역할 = 영역별 READ/WRITE · GET = READ ∨ WRITE · 그 밖 = WRITE. 진입 역할 `ECOMMERCE_OPERATOR` = auth `ENTRY_ROLES` 한 행으로 파생 · 카탈로그에서 제거 · 배정 · 위임된 것은 버림 · 진입 역할 위임 → 422.
- **서비스 단 판정**: 운영자 경로 58 전부를 서비스가 **업무 역할로** 다시 검사한다(지금 무검사인 order · 상품 · 이미지 20 경로 포함 — Failure 1). 진입 역할 단독 → 403 · `CUSTOMER` → 403 · 빈 `X-User-Role` → 403.
- **게이트웨이 = 무변경**(진입 역할 판정 그대로). 파생 진입 역할은 «그 도메인 업무 역할 ≥ 1» 과 같은 뜻이므로 게이트웨이는 계속 거친 문, 세분은 서비스 하나에서만 판정한다 — 경로 × 역할 표를 게이트웨이에 두 번째로 두지 않는다(두 사본 = Failure 2 의 다른 모양). 게이트웨이 시험에 «업무 역할 + 파생 진입 역할 토큰 → 통과» 대조를 더한다.
- **운영자-공개 트리의 분기** = «그 영역 업무 역할 보유» 로. 소비자 엔드포인트(`/me` · 소유권 경로 · 웹훅)는 손대지 않는다.
- **와일드카드 `'*'` GET = 지금 닿는 곳 그대로**: order 조회 4 · 상품/이미지 조회 4 는 서비스 판정에 «`GET` ∧ `X-Tenant-Id='*'`» 갈래를 둔다(운영 개요 카드 유지 — TASK-BE-506). 판매자 · 회원 · 정산 등 지금 403 인 곳은 **넓히지 않는다**(회원 = PII).
- 구독 전용 판정 = E-commerce 에는 **없다**(서비스가 `entitled_domains` 를 읽지 않는다 — 걷어낼 것 0). 테넌트 미선택 운영자(기본 토큰 — 역할 0)의 E-commerce 403 = 지금과 같음.
- V0059: `ECOMMERCE_OPERATOR` 를 담은 **모든** 세트 · 파트너십 scope 에 새 업무 역할 전부를 넣은 뒤 `ECOMMERCE_OPERATOR` 삭제(넓힌 뒤 지움) · 형태 시험 + IT 로 고정.
- 콘솔 = 새 ready 콘솔 티켓(«795 를 실은 굽기 라이브 확인 뒤에만 머지»). 이 PR 은 백엔드 + 계약만.
- 범위(scope) 제외: E-commerce 서비스는 운영자 판정에 범위를 읽지 않는다(`store.seller.read` 는 `/internal/sellers/**` 워크로드 전용) ⇒ `OPERATOR_EXCLUDED_SCOPES` 추가 없음.

## 6. 질문과 추천 (소유자)

| Q | 질문 | 추천 (구현자 선호) | 대안 |
|---|---|---|---|
| **Q1** (§ 4-1 · 4-2) | 회원 · 검색 영역의 역할 구성 | **역할 13** — `ECOMMERCE_ORDER_READ/WRITE` · `ECOMMERCE_PRODUCT_READ/WRITE`(상품 · 이미지 · 판매자 · 판매자 구성원 — OD-6 «상품(+판매자)» 글자 그대로) · **`ECOMMERCE_MEMBER_READ` 만**(변경 경로 0 — WRITE 행은 만들지 않는다) · `ECOMMERCE_SETTLEMENT_READ/WRITE` · `ECOMMERCE_PROMOTION_READ/WRITE` · `ECOMMERCE_SHIPPING_READ/WRITE` · `ECOMMERCE_NOTIFICATION_READ/WRITE`. **검색 재색인 = `ECOMMERCE_PRODUCT_WRITE`**(색인은 상품 카탈로그의 파생물 · 검색 역할 0 — 카탈로그에 «검사처는 있으나 닿을 길 없는» 행을 두지 않는다). 프리셋: 영역별 `ecommerce-<area>-read` 는 두지 않고 `ecommerce-read`(조회 7) · `ecommerce-<area>-operate`(조회+변경, 6 영역) · `ecommerce-full`(13) + `all-domains-full` · `legacy-compat` 확장 | (가) 16 역할 글자 그대로(`MEMBER_WRITE` · `SEARCH_READ` 는 검사처 0) · (나) 검색만 `ECOMMERCE_SEARCH_WRITE` 1 행(콘솔 · 게이트웨이 경로 0) · (다) 판매자를 `ECOMMERCE_SELLER_READ/WRITE` 로 분리(판매자 등록 · 활성화는 IAM 계정을 만든다 — 상품 편집 담당자에게 같이 가는 것을 피하려면) · 프리셋 묶음은 이견 있으면 조정 |
| **Q2** (§ 4-3) | 정산 **지급 실행**(`POST /periods/{id}/payouts/execute`) · **기간 마감**(`/close`)의 등급 | **분리하지 않는다 — `ECOMMERCE_SETTLEMENT_WRITE`**. 티켓 · ADR 이 지목하지 않은 등급을 새로 만들지 않는다는 상시 결정 · 콘솔 정산 화면이 지금 이 둘을 같은 화면에서 부른다(`settlements-api.ts`) · 정산 세트 자체가 이미 주문 · 상품 담당자와 갈린다(이번 PR 의 효과) | `ECOMMERCE_SETTLEMENT_CONTROL` 신설(794 Q1 과 같은 모양 — 지급 실행 · 기간 마감 · 수수료율, V0059 가 기존 세트에 함께 넣어 기존 운영자 무변경, `ecommerce-settlement-operate` 프리셋에는 없음) |
| **Q3** (§ 4-4 · Failure 2) | 서비스 8 곳 사본 술어를 모을 **집** | **저장소 루트 `libs/java-web` 에 프로젝트 무관 기전 하나** — 예 `com.example.web.security.IdentityHeaderRoles`: 게이트웨이가 주입한 콤마 결합 역할 헤더를 분리 · trim · 대소문자 무시 소속 검사(`holdsAny(header, roles)`) — 역할 **이름은 담지 않는다**(HARDSTOP-03 무관). 각 서비스는 자기 영역의 상수 2 개와 엔드포인트 → 역할 대응만 갖고, 8 사본(`OperatorRoleGuard` 3 · `hasAdminRole`/`validateAdminRole` 5)은 지운다. 영역 역할 이름은 서비스 하나만 쓰므로(주문 역할은 order 만) 프로젝트 범위 공유 모듈이 필요 없다 — 공유되는 것은 **기전**뿐. ecommerce 10 서비스 모두 이미 `:libs:java-web` 의존. 가드: 시험 하나가 ecommerce `src/main` 에 `split(",")` 로 역할 헤더를 가르는 사본이 다시 생기지 않음을 단언 | (가) 프로젝트 범위 모듈 `projects/ecommerce-microservices-platform/libs/operator-roles`(역할 이름 + 술어 — CLAUDE.md 상 **ADR 필요** · CI 가드 표면 점검) · (나) 서비스별 사본 유지 + 사본 동치 시험(Failure 2 를 시험으로만 막음 — 비추천) |
| **Q4** (AC-0 «슬라이스 분할 계획») | 한 PR 인가 서비스 묶음별 슬라이스인가 | **원자 PR 하나**(792~794 와 같은 모양). 슬라이스로 나누면 iam(카탈로그 · V0059 · 파생)이 먼저 나간 순간 **아직 진입 역할만 검사하는 서비스**가 «주문 세트만» 운영자에게 정산 · 회원 · 프로모션을 연다 — 카탈로그가 세분된 척하는 창이 생긴다(ADR-083 본문 위반). 크기(서비스 9 · 시험 45 파일)는 커밋을 서비스별로 나눠 검토 단위를 만든다 | (가) 슬라이스 2~3: 서비스 쪽을 먼저(그 사이는 «업무 역할 ∨ `ECOMMERCE_OPERATOR`» 이중 수용) → iam 을 마지막에 — 각 슬라이스가 따로 굽기 · 라이브 확인을 요구해 창이 늘어난다 |
| 순서 | 콘솔 데이터 | 별도 콘솔 PR, 굽기 라이브 뒤(792~794 와 같음) | — |

## 7. 배포 순서 (구현 뒤 — 미리 적는다)

auth(파생 · ceiling) · admin(카탈로그 · V0059) · ecommerce 서비스 9(product · order · user · settlement · promotion · shipping · notification · search + 게이트웨이 시험만)는 **한 굽기**에 함께. 갈라지면: auth/admin 만 먼저 → 토큰에 업무 역할 + 파생 `ECOMMERCE_OPERATOR`, 옛 서비스는 진입 역할만 보므로 **세분 없이 전부 열림**(퇴행은 아니나 카탈로그가 거짓) · 서비스만 먼저 → 옛 토큰(`ECOMMERCE_OPERATOR` 만)으로는 E-commerce 운영 전 화면 403(데모 시드 백오피스 단계 실패).

# 소유자 결정 — 2026-10-16 UTC (코디네이터 경유 · § 6 추천 그대로)

| Q | 결정 |
|---|---|
| Q1 | **역할 13** — `ECOMMERCE_ORDER_READ/WRITE` · `ECOMMERCE_PRODUCT_READ/WRITE`(상품 · 이미지 · 판매자 · 판매자 구성원 · **검색 재색인**) · `ECOMMERCE_MEMBER_READ` **만** · `ECOMMERCE_SETTLEMENT_READ/WRITE` · `ECOMMERCE_PROMOTION_READ/WRITE` · `ECOMMERCE_SHIPPING_READ/WRITE` · `ECOMMERCE_NOTIFICATION_READ/WRITE`. 프리셋 `ecommerce-read` · `ecommerce-<area>-operate` ×6 · `ecommerce-full` + `all-domains-full` · `legacy-compat` 확장 |
| Q2 | 정산 지급 실행 · 기간 마감 = `ECOMMERCE_SETTLEMENT_WRITE`(통제 역할 없음) |
| Q3 | 저장소 루트 `libs/java-web` 에 **프로젝트 무관 기전 하나**(역할 이름 · 프로젝트 문구 없음 — HARDSTOP-03 · `platform/shared-library-policy.md`). 각 서비스는 자기 영역 상수만. 사본 8 삭제 — 비교 규칙(대소문자 무시 · trim · 콤마)을 **한 곳에서 정의하고 시험**. 새 사본이 생기면 실패하는 시험/가드. 공유 경로이므로 `:libs:java-web` 에 의존하는 CI 대상 모듈 전부를 쓸어 검사 |
| Q4 | 원자 PR 하나 |
| 그 밖 | `'*'` 와일드카드 GET = 지금 그대로(order 조회 4 · 상품/이미지 조회 4) · 넓히지 않음. 게이트웨이 무변경 + 새 시험(업무 역할 + 파생 진입 역할 → 통과). web-store · 소비자 경로 무변경 |

## 구현 노트 (결정의 적용 범위 — 판단을 적는다)

- 🔵 **판정 위치 = 지금 검사가 있던 자리 그대로**(controller 또는 응용 서비스). order · 상품 · 이미지 20 경로는 검사가 **없던** 자리라 controller 메서드 첫 줄에 넣었다(`OrderOperatorAccess` · `ProductOperatorAccess`). promotion · shipping 은 응용 서비스의 `OperatorRoleGuard`(같은 클래스 이름 · `requireRead/requireWrite` 로 갈라짐) — 판정 위치를 옮기지 않았다(구조 변경 0).
- 🔵 **«한 술어» (Q3)** = `libs/java-web` `com.example.web.security.RoleListHeader` — 비교 규칙(콤마 · trim · `Locale.ROOT` 대소문자 무시 · 원소 동치 · 빈 헤더 = 역할 없음)만 갖고 **역할 이름은 없다**(HARDSTOP-03 무관, `shared-library-policy.md` «shared security helpers» — 기존 라이브러리에 클래스 하나, 새 라이브러리 아님 · 순수 JDK 라 reactive 게이트웨이 클래스패스에도 안전). 영역 상수는 서비스마다 자기 것만(주문 역할은 order 만 안다). 사본 8 삭제 — notification 의 대소문자 구분 분기는 **구성으로** 사라졌다(같은 메서드를 부른다). 재발 가드 = gateway-service `RoleHeaderCopyGuardTest`(술어 `\w*[Rr]ole\w*.split(` · bite 양방향 · 모집단 하한 > 100 파일) — 스크립트 가드가 아니라 시험으로 둔 이유: `scripts/` 에 파일을 더하면 가드 수 계수(`check-ls-files-guard-count.sh`)와 ci.yml 배선이 함께 움직이고, 이 규칙의 모집단은 ecommerce `src/main` 하나라 그 프로젝트의 빌드 레인이 가장 가까운 집이다.
- 🔵 **`:libs:java-web` 의존 모듈 쓸기**: `settings.gradle` 에 포함된 의존 모듈 **39** 개 `compileTestJava` 초록(로컬) · java-web `:test` 초록. 의존을 선언했으나 빌드에 포함되지 않은 6 개(ecommerce `auth-service` 등 은퇴 모듈)는 대상 아님. 추가만 한 클래스라 기존 소비자의 시험 결과는 바뀌지 않는다(추론) — CI Build 레인이 libs 변경으로 전 프로젝트를 돌린다.
- 🔵 **와일드카드 `'*'`**: 서비스 판정의 «`X-Tenant-Id = '*'`» 갈래 = 주문 조회 4 · 상품 조회 3 · 이미지 목록 1 **뿐**(지금 닿는 곳 그대로). `X-Tenant-Id` 는 게이트웨이가 클라이언트 값을 벗기고 토큰에서 다시 넣는다(`IdentityHeaderStripFilter.BASELINE_HEADERS`).
- 🔵 **게이트웨이 무변경** + 시험 3(업무 역할 하나 + 파생 진입 역할 → `/api/admin/**` · 운영자-공개 트리 통과 / 진입 역할 없는 업무 역할 → 403(파생에 기대는 것을 고정) / 업무 역할은 소비자 트리를 열지 않는다).
- 🔵 **검색 재색인**: 서비스 판정 = `ECOMMERCE_PRODUCT_WRITE`. 게이트웨이는 `/api/search/**` 에 `CUSTOMER` 를 요구하므로 운영자 토큰은 여전히 게이트웨이에서 403 — 계약(«내부 포트 전용») 그대로, 이 PR 은 그 경로를 열지 않는다.
- 🔵 **정당하게 바뀐 시험**(이 PR 의 판정이 바꾼 것만): ecommerce 기존 시험 43 파일의 운영자 헤더 `ECOMMERCE_OPERATOR` → `ECOMMERCE_OPERATOR,<영역>_READ,<영역>_WRITE`(회원 = `MEMBER_READ`) · order/상품/이미지 시험의 «헤더 없이 200(게이트 위임)» → 업무 역할 헤더(이름도 고침) · `InternalSellerChainTest` 헤더 신뢰 경로 · admin 카탈로그 28 → 40 · 프리셋 26 → 33(V0056/57/58 형태 · V0057/58 IT) · V0052 형태 시험의 «V0052 시절 카탈로그» · auth `OperatorRoleDerivationTest` ecommerce 상한 · `TenantClaimTokenCustomizerTest` legacy-compat 목록 · BE-632 ecommerce 세트 시험 · `DemoCsOperatorDerivedRolesTest` 공개 역할 집합(ECOMMERCE_OPERATOR + 13 + WMS) · `AssumeTenantExchangeIntegrationTest` STEP 5 배정 역할(`ECOMMERCE_OPERATOR` → `ECOMMERCE_ORDER_READ` — 진입 역할은 이제 배정에서 버려진다).
- ⚪ `R__seed_demo_cs_operator.sql` 머리 주석의 «holds ECOMMERCE_OPERATOR» 문장은 고치지 않았다 — R__ 체크섬이 바뀌어 반복 이전이 다시 돈다. 문장은 여전히 참(파생 진입 역할)이고 역할 집합은 `DemoCsOperatorDerivedRolesTest` 가 고정한다.

# Red — 2026-10-16 UTC (커밋 `9b923356b` · 시험만 · run `38873993204`)

| 레인 | job id | 결과 |
|---|---|---|
| CI `Build & Test (JDK 21, Linux)` | `116884609850` | 실패 **21** — gateway 1(`RoleHeaderCopyGuardTest` — 사본 8 발견) · notification 4 · order 9 · product 7. 전부 단언(`java.lang.AssertionError` 20 · `AssertionFailedError` 1 — 로그 `FAILED` 다음 줄에서 셈). 🔵 이 단계는 실패 4 태스크에서 멈춰 user · settlement · search · promotion · shipping · iam 단위 시험은 **실행되지 않았다** ⇒ 아래 로컬 줄 |
| CI `Integration (iam A, Testcontainers)` | `116884611247` | admin `:integrationTest` **332 · 4 실패**(V0059 IT 2 — 파일 없음 단언 · V0057/V0058 IT `presetsAfterBoot` 2 — 프리셋 33) — 전부 단언 |
| CI `Integration (iam B, Testcontainers)` | `116884611297` | auth `:integrationTest` **158 · 1 실패**(`AssumeTenantExchangeIntegrationTest` STEP 5 — 배정된 업무 역할에서 진입 역할 파생 없음) — 단언 |
| CI `Integration (ecommerce A/B/C)` | `116884611176` · `116884611285` · `116884611204` | 초록 — red 커밋은 IT 를 바꾸지 않았다(통제) |
| 로컬(같은 커밋) | — | 새 게이트 시험 **39 실패**(order 9 · product 7 · user 3 · settlement 4 · promotion 4 · shipping 4 · notification 4 · search 3 · gateway 1 — 전부 단언, 결과 XML 의 `type` 에서 셈) · admin `:test` **1393 · 18** · auth `:test` **1288 · 9** — 전부 단언. 통과 쪽 = 허용 대조군(operate 세트 · legacy-compat · 게이트웨이 시험 3) |

# Green — 2026-10-16 UTC (커밋 `992ac70c6` · run `38877656055`)

| 레인 | job id | 결과 |
|---|---|---|
| CI `Build & Test (JDK 21, Linux)` | `116896562725` | ✅ — `libs:java-web` · ecommerce 11 서비스(gateway · order · product · user · settlement · promotion · shipping · notification · search · payment · review) · iam 5 서비스의 `:test` 가 **실제로 실행**(로그의 `Task …:test` 줄 — FROM-CACHE 아님, batch-worker · iam e2e 만 캐시)되고 BUILD SUCCESSFUL. 🔵 libs 변경이라 전 프로젝트가 이 레인에 들어온다(wms · scm · erp · finance · fan 은 캐시 적중 — 입력 불변) |
| CI `Integration (iam A, Testcontainers)` | `116896563933` | ✅ **356 PASSED · 0 FAILED**(로그에서 셈) — `V0059EcommerceRoleMigrationIntegrationTest` 2/2 · V0057/V0058 `presetsAfterBoot` PASSED |
| CI `Integration (iam B, Testcontainers)` | `116896564153` | ✅ **353 PASSED · 0 FAILED** — `AssumeTenantExchangeIntegrationTest` STEP 5 PASSED |
| CI `Integration (ecommerce A/B/C, Testcontainers)` | `116896564135` · `116896564019` · `116896564126` | ✅ **125 · 70 · 223 PASSED · 0 FAILED** — 업무 역할 헤더로 바꾼 IT(프로모션 CRUD · 쿠폰 발급 · 배송 · 알림 · 회원 · 다중 테넌트 · 셀러 스코프) 포함 |
| CI 그 밖 | run `38877656055` | 실행된 잡 **38 전부 success**(필수 4 포함 · Flyway 버전 충돌 · 자리표시자 · 오류 코드 레지스트리 · 공유 라이브러리 위생 · 컨트롤러 슬라이스 이름) · 31 skipped(경로 필터) |
| 로컬(같은 코드) | — | java-web · ecommerce 11 · iam admin/auth `:test` rc=0 · java-web 의존 39 모듈 `compileTestJava` rc=0 · 스테이지 뒤 가드 8(Flyway 버전 충돌 · 자리표시자 · 오류 코드 레지스트리 2 · libs CI 커버리지 · INDEX · 티켓 ID · walkthrough) rc=0 |

## 브랜치 nightly 전용 스위트 — 2026-10-16 UTC (커밋 `992ac70c6`)

| 워크플로 | run | 결과 (job 로그 요약 줄에서 셈) |
|---|---|---|
| `federation-hardening-e2e` | `38879384218` (success) · 전체 스택 job `116902881046` | **20 passed · 1 flaky · 0 failed**. flaky 1 = `tenant-switch-rescope.spec.ts:110` — 기지의 `TASK-BE-516`, 이 티켓과 무관(federation 스택에 ecommerce 도메인 호출 0 — AC-0 § 3) |
| `nightly-e2e` 1차 | `38879390811` attempt 1 (failure) | ecommerce · 콘솔 · web-store 잡 전부 초록. 실패 2 = `E2E full (fan-platform v1 live-trio)` `116902164084` · `E2E full (scm-platform v1 cross-service)` `116902163990` — 원인 = **의존성 해석**(`Could not find resilience4j-annotations-2.2.0.jar` — `libs:payment-core` · scm procurement 의 `compileJava`), 시험 단언 아님 |
| `nightly-e2e` 재실행 | `38879390811` attempt 2 (failure) | 같은 두 잡이 같은 원인으로 다시 실패(`116908323426` · `116908323581`). 🔴 **이 PR 이전부터의 것**: 기준 브랜치의 nightly `38863691082`(`cf36d1609` = 794 머지, 이 브랜치의 base)도 `E2E full (fan-platform v1 live-trio)` 가 **같은 jar 해석 실패**로 빨갛다 ⇒ 795 의 코드가 아니라 러너의 의존성 캐시/저장소 쪽 ⚪(로컬 `curl` 로 그 jar 는 200). 이 티켓에서 고치지 않는다 — 이슈 **#4386**(794 창에서 자동 개설, 열림)이 같은 빨강을 들고 있다 |
| ↳ ecommerce 관련 잡(재실행) | 위 run | web-store full-stack `116908325894` **14 passed · 0 failed**(필수 6 스펙 실행 · `account-type-guard` 2 — CUSTOMER 없는 ecommerce 계정 거부 포함) · 콘솔 full-stack `116908326105` **12 passed · 0 failed** · ecommerce×wms 주문처리 고리 `116908325182` **tests=2 failures=0** · iam docker-compose `116908384194` **tests=3 failures=0** · wms gateway-master 초록 |

# 배포 상태 (AC-3) — 2026-10-16 UTC

| 층 | 상태 |
|---|---|
| 기준 브랜치 | ❌ 미머지 — PR #4387 DRAFT (머지 · ready 전환은 이 에이전트 범위 밖) |
| AMI | ⚪ — 굽기는 소유자 명령. 이 PR 의 백엔드(ecommerce 9 서비스 · admin V0059 · auth 파생)는 **한 굽기에 함께** 실려야 한다(§ 7 — 갈라지면: iam 만 먼저 → 세분 없이 전부 열림(카탈로그가 거짓) · ecommerce 만 먼저 → 옛 토큰(`ECOMMERCE_OPERATOR` 만)으로 E-commerce 운영 전 화면 403 + 데모 시드 백오피스 단계 실패). 792 · 793 · 794 와 같은 굽기에 실려도 된다 |
| 데모 창 라이브 | ⚪ — 위 둘이 없다. 측정 항목은 `TASK-MONO-672` § «Next window — TASK-MONO-795» (V0059 행 수 앞/뒤 · AC-1/2 라이브 · 직접 호출 대조 · 와일드카드 · 소비자 비회귀) |
| 콘솔 | 변경 없음(이 PR). E-commerce 신호는 `TASK-PC-FE-345`(ready) — **굽기 라이브 확인 뒤에만 머지** |
| web-store | 변경 없음 — 소비자 토큰은 `CUSTOMER` 만, 업무 역할은 assume · 파트너십 경로에만 실린다 |

# 배포 상태 — 28차 창 (2026-10-16 UTC) — AC-3 닫힘

| 층 | 상태 |
|---|---|
| `main` | ✅ PR #4387 → `2eef1d91f` (2026-10-16T09:55:41Z) · 머지 시점 `statusCheckRollup` 실패 0 (38 pass / 31 skipped) |
| AMI | ✅ `ami-00ef8cc1057cca59b` (`RepoCommit` `8905d2c29`, `2eef1d91f` 가 조상) — ecommerce 9 서비스 · admin V0059 · auth 파생이 **한 굽기**(§ 7 의 갈라짐 없음) · 792 · 793 · 794 와 같은 굽기 · Flyway admin V0052..V0059 전부 success · `seed-ecommerce` 상품 42(`ecommerce`) |
| 데모 창 라이브 | ✅ 아래 |
| 콘솔 | `TASK-PC-FE-345` 머지 게이트 충족(그 티켓에 한 줄 적음) |

**V0059 행 수(읽기 전용).** 신선 볼륨이라 «앞» = `ECOMMERCE_OPERATOR` 를 가진 프리셋 3개(`ecommerce-full` · `all-domains-full` · `legacy-compat`)로 셈한다. 뒤: `sets_with_ecommerce_operator` **0** · `sets_with_settlement_write` **4** (= 3 + 1) · `sets_with_member_read` **4** (= 3 + 1) · `partnerships_with_ecommerce_operator` **0** — 완료 판정 주석과 일치.

**AC-2 라이브(이전 호환)** ✅ `demo@demo.com`(legacy-compat) → `roles` ∋ `ECOMMERCE_OPERATOR`(파생) + E-commerce 13 역할 · assume `ecommerce` → `/api/admin/orders` · `products` · `users` · `sellers` 200.

**AC-1 라이브 대조군 — 게이트웨이와 서비스 직접 둘 다** ✅ `requester@demo.com`(`demo-corp`), 프리셋 하나씩 → 바이트 동일 복원:
- `ecommerce-order-operate` 만 → orders 200 · products POST **403** · users **403** · 주문 상태 변경(없는 주문) 404(게이트 통과).
- `ecommerce-read` 만 → orders · products · users · sellers 200 · 쓰기 **403**.
- E-commerce 세트 없음(`wms-inventory-operate` 만) → 게이트웨이 경유 orders **403**.
- 🔴 **직접 호출(우회) 대조**: 호스트 안에서 `X-User-Role: ECOMMERCE_OPERATOR` 만 → order-service **403** · product-service **403**; `ORDER_READ` / `PRODUCT_READ` 를 실으면 200. 795 전에는 이 둘이 200(게이트웨이가 유일한 판정)이었다. 16:27Z 에 따로 한 번 더 재서 같음.
- ⚪ **와일드카드 비회귀** — 데모에 IdP 로그인이 되는 무제한 `'*'` `SUPER_ADMIN` 이 없다(`platform@demo.com` 은 `fan-platform` 한정 · `admin`/`dev1` 은 자격 없음). `demo@demo.com`(`demo-corp`) 의 개요 ecommerce 카드는 렌더 · «상품 수 0» — `demo-corp` 상품 `totalElements` 0(http 200), `ecommerce` 는 42 ⇒ 0 은 맞는 데이터이지 거절이 아니다(이 칸을 대신하지 않는다). 이 ⚪ 는 AC 가 아니고 `TASK-MONO-672` § Order «Still open» 3 이 들고 간다.
- ⚪ 미측정(AC 아님 — 672 런북의 부가 칸): `demo-cs` assume 의 E-commerce 역할 · 콘솔 E-commerce 9 화면 · 소비자 비회귀(web-store 로그인 + 주문 + 결제 + 배송 + 알림). 같은 창에서 스토어 신규 가입 → 첫 로그인은 성공했다(`TASK-FE-108` AC-3) — 주문 · 결제 경로는 아니다.

기록 위치: `TASK-MONO-672` § 28th window — harvest.

# 닫기 (2026-10-16 UTC) — in-progress → done

- (a) `gh pr view 4387`: `state=MERGED` · `mergedAt` 2026-10-16T09:55:41Z · `mergeCommit` `2eef1d91f`. (b) `origin/main`(`1c8ee22cb`)의 조상. (c) 머지 시점 실패 체크 0.
- (d) AC 절을 열어 동사별로: AC-0 재측정 + 소유자 결정 ✅ · AC-1 대조군(서비스 직접 + 게이트웨이) ✅ · AC-2 이전 호환 · 소비자 무변경 대조군 ✅(시험 · 브랜치 nightly) · AC-3 «배포 상태를 따로 적는다» = 위 표 ✅. 전부 닫힘.
- `review/` 단계는 거치지 않고 바로 `done/`: 구현 PR 은 머지 시점에 검토됐고, 마지막 AC 는 배포 상태 기록뿐이었다.
