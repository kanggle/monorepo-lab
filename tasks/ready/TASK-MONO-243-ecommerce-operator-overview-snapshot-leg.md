# Task ID

TASK-MONO-243

# Title

ecommerce 를 platform-console 의 **operator-overview 대시보드 6번째 스냅샷 카드**로 추가 (ADR-MONO-030 Step 4 facet (a-후속-2) — 콘솔 통합 수직 슬라이스 3). MONO-241 이 domain-health 대시보드에만 ecommerce 6번째 카드를 넣었고 operator-overview 는 여전히 5 도메인 → 대칭 완성. **net-new ecommerce operator-plane read 엔드포인트**(`GET /api/admin/products` 페이지 total = tenant 상품 수) + console-bff `EcommerceOverviewReadPort`/어댑터(credential=IamOidcAccessToken, X-Tenant-Id pass-through, cross-leg 401 collapse) + `OperatorOverviewCompositionUseCase.CARD_ORDER` 5→6 + 계약 §2.4.9.1 5→6 + console-web operator-overview 6번째 카드 렌더. **cross-project** (platform-console + ecommerce).

# Status

ready

# Owner

backend

# Task Tags

- monorepo
- cross-project
- console-integration
- adr-mono-030
- platform-console
- ecommerce

---

# Dependency Markers

- **선행 (prerequisite, 전부 done)**:
  - `TASK-MONO-241` — facet (a-후속): ecommerce 6번째 **domain-health** 카드 + `/ecommerce` 드릴인 + `DomainTarget.ECOMMERCE`(enum 끝) + `CredentialSelectionAdapter` `ECOMMERCE→IamOidcAccessToken` + `ecommerceRestClient` 빈 + `CompositionEngine` per-route `CARD_ORDER` 결합 해제 (done, squash `0134fb287` + close `782e59754`). 본 태스크는 그 인프라를 재사용해 **overview 쪽만 5→6**.
  - `TASK-BE-357` — Step 2 바깥 축: ecommerce gateway `TenantClaimValidator` 고정슬러그→entitlement-trust 진화 + product-service `TenantContext`/`TenantContextFilter`/repo chokepoint(`WHERE tenant_id`) (done). 본 태스크의 신규 read 엔드포인트는 이 tenant 스코핑을 재사용.
  - `TASK-BE-363` — Step 3 안쪽 축: product `seller_id` + `SellerScopeContext` (done). 신규 read 는 seller-scope chokepoint도 자동 통과(net-zero, 미제약).
  - `TASK-PC-FE-011` — operator-overview MVP composition route (done). 본 태스크가 5→6 확장하는 대상.
- **근거 (ADR)**: [ADR-MONO-030](../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) §3.4 Step 4 facet (a-후속-2); MONO-241 task § Out of scope 가 본 슬라이스(operator-overview 스냅샷 leg = net-new operator-plane read 엔드포인트 필요)를 명시적 후속으로 지정; 계약 §2.4.9.1 의 "Domain-set divergence from §2.4.9.2 (TASK-MONO-241)" 노트가 "ecommerce overview snapshot leg ... deferred follow-up (facet a-후속-2) requiring a net-new ecommerce operator-plane read endpoint" 로 본 태스크를 직접 가리킴.
- **scope 결정 (user, 2026-06-13)**: STEP A = facet **(a-후속-2)**. operator-overview 에 ecommerce 6번째 스냅샷 카드 추가로 health(6)·overview(6) 대칭 완성.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (cross-project: net-new ecommerce operator-plane 엔드포인트 authz 모델 + console-bff overview leg credential/tenant pass-through + 다계층 계약/테스트; 단순 복제 아님).

---

# Goal

MONO-241 이 ecommerce 를 **domain-health** 대시보드의 6번째 카드로 넣었으나, **operator-overview** 대시보드(랜딩/홈)는 여전히 5 도메인 `[iam,wms,scm,finance,erp]` 이다 — 두 대시보드의 도메인 집합이 비대칭이다. 본 태스크는 ecommerce 를 operator-overview 의 **6번째 스냅샷 카드**(도메인 메트릭 = tenant 상품 수)로 추가해 대칭을 완성한다. 핵심 선행 작업은 **ecommerce 에 net-new operator-plane tenant-scoped read 엔드포인트**(현재 `AdminProductController` 는 전부 write)를 만드는 것으로, erp `GET /api/erp/masterdata/departments?...&size=1` 의 page-total 스냅샷 패턴을 미러한다.

# Scope

**cross-project** = ecommerce(net-new read 엔드포인트) + platform-console(console-bff overview leg + console-web 카드 + 계약). 단일 atomic PR (CLAUDE.md § Cross-Project).

## In scope

### A. 계약 (contract-first — 구현 전 갱신)

1. **`projects/platform-console/specs/contracts/console-integration-contract.md`**:
   - **§ 2.4.9.1 (Operator Overview)**: 카드 집합 `iam|wms|scm|finance|erp`(5) → **`ecommerce` 추가(6번째)**. 고정 leg 순서·envelope 스키마·"exactly 5 entries"·"all-down 5 cards" 류 문구를 **6** 으로 정합(`[iam,wms,scm,finance,erp,ecommerce]`).
   - **§ 2.4.9.1 "Composed producers" 표**: 6번째 행 추가 — `ecommerce` | `GET http://ecommerce.local/api/admin/products?page=0&size=1` (page total snapshot) | **IAM OIDC access token** — `getAccessToken()` | ecommerce product-service operator-plane read (본 태스크 신규) | tenant 상품 수(snapshot).
   - **§ 2.4.9.1 의 "Domain-set divergence from §2.4.9.2 (TASK-MONO-241)" 노트 해소**: overview 도 이제 6 → "health=6, overview=5 두 독립 표면" 문구를 "health=6, overview=6 — 대칭 회복(facet a-후속-2, 본 태스크)" 로 정정. ecommerce overview snapshot leg 가 "deferred" 라는 문장을 "landed by TASK-MONO-243" 로.
   - **ecommerce-leg 게이트-라우팅 topology 노트 추가**(scm-leg topology 노트와 동형): 다른 5 overview leg 는 direct-to-producer 이나, **ecommerce 의 product-service 는 JWT resource server 가 아니라 게이트 헤더-trust 서비스**(Step2 `TenantContextFilter` = X-Tenant-Id 헤더 신뢰)이므로 ecommerce overview leg 는 **ecommerce 게이트(`ecommerce.local`)를 경유**한다 — 게이트가 IAM OIDC 토큰 검증 + `account_type=OPERATOR` 강제 + `tenant_id`→`X-Tenant-Id` 주입 + 클라이언트 헤더 strip 을 수행하고, product-service 는 그 신뢰된 `X-Tenant-Id` 만 읽는다. (health leg 와 동일 `ecommerceRestClient`=`ecommerce.local` 경로 재사용.)
   - **§ 2.4 producer 계약(ecommerce)**: 신규 operator-plane read 엔드포인트 명세 추가(아래 B 와 정합). ecommerce 측 계약 파일(`projects/ecommerce-microservices-platform/specs/contracts/http/` 의 product/admin 계약)에 `GET /api/admin/products` 표면 추가 — paged list(`content[]`,`page`,`size`,`totalElements`), authz=게이트 entitlement-trust + OPERATOR, tenant-scoped, read-only.

### B. ecommerce 구현 — net-new operator-plane read 엔드포인트 (Java)

2. **`AdminProductController` (`/api/admin/products`) 에 `@GetMapping` 추가** — paged product 요약 list 반환(`ProductListResponse`: `content[]`,`page`,`size`,`totalElements`). 기존 `QueryProductService.findAll(categoryId, status, page, size)` 재사용(공개 `ProductController` 와 동일 query 경로, **operator 평면**). optional `@RequestParam` `categoryId`/`status`/`page`(기본 0)/`size`(기본 20, MAX 100 cap) — 공개 컨트롤러 미러.
   - **authz 설계 결정 (중요, 문서화)**: 본 GET 은 **`X-User-Role==ADMIN` 검사를 하지 않는다**(write 엔드포인트와 의도적 분기). 이유: (1) operator-overview leg 의 호출 주체는 platform-console **operator**(IAM OIDC 토큰, `account_type=OPERATOR`)이지 ecommerce-local ADMIN-role 사용자가 아니다 — operator 토큰엔 ecommerce `ADMIN` role claim 이 없다; (2) 인가는 **게이트 계층의 entitlement-trust**(검증된 IAM OIDC 토큰 + `/api/admin/**` 에 대한 `account_type=OPERATOR` 강제[`AccountTypeEnforcementFilter`] + non-blank `tenant_id`[`TenantClaimValidator`])가 담당하고, **tenant 격리는 repo chokepoint `WHERE tenant_id`** 가 보장한다(M6); (3) read-only 스냅샷이므로 write 의 ecommerce-local RBAC 와 다른 관심사. → erp/finance overview leg 가 도메인-local admin role 없이 federation entitlement-trust 로만 인가되는 선례와 동형.
   - **메트릭 정의**: console-bff 가 `?page=0&size=1` 로 호출 → `totalElements` = **해당 tenant 의 총 상품 수**(status 필터 없음 = 카탈로그 크기). `ProductStatus` 가 `ON_SALE|SOLD_OUT|HIDDEN`(불리언 active 없음)이라 "활성" 의미가 모호 → 총 상품 수로 정직하게 정의(셋업 중 tenant 가 0 ON_SALE 이어도 오도하지 않음). status 파라미터는 옵션으로 지원하되 leg 는 미전달.
   - **tenant 스코핑**: `QueryProductService.findAll` → `ProductQueryPort.findSummaries` → repo adapter 의 `WHERE tenant_id`(Step2 chokepoint) + `SellerScopeContext`(미제약=net-zero). 게이트가 주입한 `X-Tenant-Id` → `TenantContextFilter` → `TenantContext` → 캐시키 tenant-prefix. 별도 코드 불요(기존 chokepoint 통과).
   - **변경 최소**: 신규 컨트롤러 분리보다 `AdminProductController` 에 GET 1개 추가(같은 `/api/admin/products` 매핑, 게이트 라우트 이미 존재 `Path=/api/products/**,/api/admin/products/**`). DTO(`ProductListResponse`)·service·port 전부 기존 재사용.

3. **ecommerce 테스트**:
   - `AdminProductController` GET 슬라이스/단위 테스트 — paged 반환, `totalElements` 정합, ADMIN-role 헤더 없이 200(authz 분기 단언), tenant 헤더 전파.
   - **M6 tenant 격리 IT 보강**: 신규 read 가 cross-tenant 누출 0(tenant A 토큰/헤더로 tenant B 상품 카운트 불가) — 기존 `MultiTenantIsolationIntegrationTest`(product) 에 read-count leg 추가 또는 신규 단언(`@SpringBootTest(classes=ProductServiceApplication.class)` 핀 = Step2/3 트랩 회피).

### C. console-bff 구현 (Java) — overview leg 추가

4. **`EcommerceOverviewReadPort`** (interface, `ErpDepartmentsReadPort` 미러 — `extends DomainReadPort<Map<String,Object>>`) + **`EcommerceOverviewReadAdapter`** (`@Qualifier("ecommerceRestClient")`, `ErpDepartmentsReadAdapter` 미러): `RestClientHelper.authenticatedGet(client, uri→ path("/api/admin/products").queryParam("page",0).queryParam("size",1).build(), tenantId, credential)` 호출. 반환 `Map<String,Object>`(producer body) 그대로 또는 `{productCount: totalElements}` 정규화.
   - ⚠️ **health 의 `EcommerceHealthReadAdapter`(credential-less actuator)와 별개** — overview leg 는 credential 有(IamOidcAccessToken) + tenant pass-through. 둘 다 `ecommerceRestClient`(=`ecommerce.local` 게이트) 재사용하나 경로·인가가 다름(health=`/actuator/health` 무인증, overview=`/api/admin/products` operator 토큰).

5. **`OperatorOverviewCompositionUseCase`**:
   - `CARD_ORDER` `List.of(IAM,WMS,SCM,FINANCE,ERP)` → **`List.of(IAM,WMS,SCM,FINANCE,ERP,ECOMMERCE)`** (ECOMMERCE 끝, EnumMap iteration 기존 5 byte-stable).
   - `ecommerceOverviewPort` 생성자 주입 + credential 사전 해소 맵에 ECOMMERCE 포함(이미 `CredentialSelectionAdapter` 가 `ECOMMERCE→IamOidcAccessToken` 매핑) + `legBodies.put(ECOMMERCE, () -> timed(ECOMMERCE, () -> ecommerceOverviewPort.read(tenantId, credential)))`(다른 도메인 leg 시그니처 미러).
   - javadoc 의 "exactly 5 legs"·"deliberately does NOT include ECOMMERCE"(MONO-241 주석) → "6 legs `[IAM,WMS,SCM,FINANCE,ERP,ECOMMERCE]`(facet a-후속-2, TASK-MONO-243)" 로 정정.
   - **cross-leg 401 collapse**: ecommerce leg 의 401(게이트 토큰 거부)도 다른 leg 와 동일하게 composition-level 401 로 collapse(공유 토큰 discipline). degraded/forbidden(403 TENANT_FORBIDDEN, timeout, 5xx)는 카드 단위 degrade.

6. **`OperatorOverviewResponse`** javadoc: "exactly 5 entries in fixed order `[gap,wms,scm,finance,erp]`" → "exactly 6 entries ... `[gap,wms,scm,finance,erp,ecommerce]`".

7. **console-bff 테스트 5→6 정합**:
   - `OperatorOverviewCompositionUseCaseTest` — leg 6개 단언, ecommerce leg ok(`{productCount:N}`)/degraded(timeout/5xx)/forbidden(403 TENANT_FORBIDDEN)/cross-leg-401-collapse 케이스(기존 erp/finance 케이스 미러), all-down 시 6 leg + 200 방출.
   - `OperatorOverviewSliceTest`(있으면) — envelope 카드 6개, 고정 순서.
   - `DomainHealthCompositionUseCaseTest` / health 쪽 — **6 불변** 회귀(net-zero, 변경 없음 확인).
   - `ConsoleBffSmokeIntegrationTest`(있으면) — overview 6 / health 6.

### D. console-web 구현 (TS/Next.js)

8. **`src/features/operator-overview/api/operator-overview-types.ts`** (⚠️ MONO-241 식 하드코딩 함정 — 이 파일에 5 가 박혀있음):
   - `CARD_ORDER = ['iam','wms','scm','finance','erp']` → **`+ 'ecommerce'`**.
   - `OperatorOverviewSchema` 의 `.length(5)` → **`.length(6)`**(또는 `.length(CARD_ORDER.length)`).
   - `.refine(...)` 의 set-equality 메시지 `'[gap,wms,scm,finance,erp]'` → 6 도메인 반영.
   - 신규 **`EcommerceDataSchema`** = `{ productCount: z.number().int().nonnegative().nullable().optional() }.passthrough()`(다른 도메인 data 스키마 미러, 방어적 narrow).
9. **operator-overview 카드 렌더 컴포넌트**(`OperatorOverviewScreen` 또는 카드 map): ecommerce 카드 렌더 추가 — `productCount` 표면("상품 N개" 류 read-only), `DomainKey` 라벨/아이콘 맵에 ecommerce 확장(TS-exhaustive). data-driven map 이면 라벨만, 도메인별 switch 면 case 추가.
10. **console-web 테스트**: operator-overview 6 카드 단언, `OperatorOverviewSchema.parse` 가 6-카드 envelope accept(5-카드 reject), ecommerce 카드 렌더 스냅샷/단언.

## Out of scope (명시적 후속)

- **facet (b) 셀러 정산/수수료** — 별 태스크(설계 먼저, STEP B). 본 태스크는 read-only overview 스냅샷.
- **/ecommerce 드릴인 풍부한 운영 표면**(상품/주문/셀러 CRUD) — MONO-241 v1(라우트+health)에서 이미 최소 충족; 운영 표면 확장은 별도.
- **상품 수 외 추가 메트릭**(주문 수·셀러 수·GMV 등) — 본 슬라이스는 단일 카탈로그-크기 메트릭. order-service operator read 등은 후속.
- **federation-hardening-e2e Playwright 렌더 단언** — nightly. 본 슬라이스는 console-bff slice/unit + ecommerce slice/IT + console-web 테스트로 증명.
- 나머지 11 서비스 tenant 전파(c), ADR-022 이벤트 스레딩(d), M7 quota(e), 셀러 온보딩(f).

# Acceptance Criteria

- **AC-1 (계약 우선)**: console-integration-contract.md §2.4.9.1 가 ecommerce 를 6번째 overview 카드로 포함(producers 표 6행, response schema/invariants "exactly 6", divergence 노트 해소 = health 6/overview 6 대칭), ecommerce-leg 게이트-라우팅 topology 노트 추가; ecommerce product 계약에 `GET /api/admin/products` operator-plane read 표면 추가. 구현 전 갱신.
- **AC-2 (ecommerce read 엔드포인트)**: `GET /api/admin/products` 가 paged 요약(`content[]`,`page`,`size`,`totalElements`) 반환; `?page=0&size=1` → `totalElements` = tenant 총 상품 수; ADMIN-role 헤더 없이 200(authz=게이트 entitlement-trust+OPERATOR, controller 는 role 미검사); tenant-scoped(repo chokepoint).
- **AC-3 (tenant 격리 — M6)**: 신규 read 가 cross-tenant 누출 0 — tenant A 컨텍스트(`X-Tenant-Id=A`)로 tenant B 상품이 카운트/노출되지 않음. product M6 IT 로 단언(`classes=ProductServiceApplication.class` 핀).
- **AC-4 (console-bff overview 6 leg)**: `OperatorOverviewCompositionUseCase.compose()` 가 6 leg(ecommerce 포함, 고정 순서 IAM,WMS,SCM,FINANCE,ERP,ECOMMERCE)을 방출; ecommerce leg = `GET /api/admin/products?page=0&size=1` credential=IamOidcAccessToken + X-Tenant-Id pass-through; all-down 시에도 6 leg + 200.
- **AC-5 (leg 결과 분류)**: ecommerce leg ok→`data={productCount:N}`; 403 TENANT_FORBIDDEN→`forbidden`; timeout/5xx→`degraded`; **401→composition-level 401 collapse**(cross-leg discipline, 다른 leg 와 동일).
- **AC-6 (console-web 6 카드)**: operator-overview 가 6번째 ecommerce 카드를 렌더(`productCount` 표면); `OperatorOverviewSchema` `.length(6)` + CARD_ORDER ecommerce 포함; 5-카드 envelope 는 reject.
- **AC-7 (health net-zero)**: domain-health 대시보드(§2.4.9.2)는 **6 불변** — 본 태스크가 건드리지 않음. health 테스트 byte-equal GREEN.
- **AC-8 (빌드 GREEN)**: 영향 서비스 `:check` GREEN — ecommerce product-service(`:projects:ecommerce-microservices-platform:apps:product-service:check`) + console-bff(`:projects:platform-console:apps:console-bff:check`) + console-web(`pnpm test`/lint/tsc). HARDSTOP-03 N/A.
- **AC-9 (net-zero degrade / standalone)**: ecommerce 게이트/product-service 부재(overview leg DOWN) 시 operator-overview 는 ecommerce 카드만 `degraded`, 나머지 5 카드 정상; 콘솔 셸 정상. ecommerce standalone(콘솔 무관)도 영향 없음(read 엔드포인트는 게이트 entitlement-trust 로만 게이트, 콘솔 외 호출자엔 invisible).

# Related Specs / Code

- ADR: `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` §3.4 Step 4 facet (a-후속-2).
- 계약: `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.9.1(overview 5→6)/§2.4.5/6(credential, ECOMMERCE 이미 있음) + ecommerce product http 계약.
- ecommerce 코드:
  - `apps/product-service/.../presentation/controller/AdminProductController.java` (GET 추가)
  - `apps/product-service/.../presentation/controller/ProductController.java` (공개 GET 미러 — query 재사용 참조)
  - `apps/product-service/.../application/service/QueryProductService.java` + `.../application/port/ProductQueryPort.java` (재사용)
  - `apps/product-service/.../presentation/dto/ProductListResponse.java`(=`totalElements`) + `.../application/dto/ProductListResult.java`
  - `apps/product-service/.../domain/tenant/TenantContext.java` + `TenantContextFilter`(Step2 chokepoint) / `.../domain/seller/SellerScopeContext.java`(Step3)
  - `apps/gateway-service/.../filter/AccountTypeEnforcementFilter.java`(/api/admin/→OPERATOR) + `.../security/TenantClaimValidator.java`(entitlement-trust) + `.../filter/JwtHeaderEnrichmentFilter.java`(X-Tenant-Id 주입) + `application.yml`(product-service 라우트 `Path=/api/products/**,/api/admin/products/**` 이미 존재)
- console-bff 코드(미러 선례):
  - `.../application/usecase/OperatorOverviewCompositionUseCase.java` (CARD_ORDER 5→6, leg 추가)
  - `.../application/port/outbound/ErpDepartmentsReadPort.java` (미러) → 신규 `EcommerceOverviewReadPort.java`
  - `.../adapter/outbound/http/ErpDepartmentsReadAdapter.java` (미러) → 신규 `EcommerceOverviewReadAdapter.java`
  - `.../adapter/outbound/http/CredentialSelectionAdapter.java` (`ECOMMERCE→IamOidcAccessToken` 이미 있음)
  - `.../infrastructure/config/RestClientConfig.java` (`ecommerceRestClient` 이미 있음)
  - `.../adapter/inbound/web/OperatorOverviewResponse.java` (javadoc 5→6)
  - `.../application/composition/CompositionEngine.java` (MONO-241 결합 해제 — 변경 불요, legBodies-iterate)
- console-web 코드:
  - `src/features/operator-overview/api/operator-overview-types.ts` (CARD_ORDER/`.length(5)`/refine/EcommerceDataSchema)
  - `src/features/operator-overview/api/operator-overview-api.ts`(스키마 재사용 — 변경 불요 확인)
  - operator-overview 카드 렌더 컴포넌트(`OperatorOverviewScreen` 또는 dashboards overview page)
- 참조 선례: TASK-MONO-241(health 6 카드 + 인프라), TASK-PC-FE-011(overview MVP), TASK-PC-FE-010(erp departments leg 미러), TASK-BE-357/363(tenant/seller chokepoint).

# Related Contracts

- `console-integration-contract.md` §2.4.9.1(overview 5→6) — 카드 멤버십 확장. 이벤트 계약 없음(동기 read 경로).
- ecommerce product http 계약 — `GET /api/admin/products` operator-plane read 표면 신규(producer SoT).

# Edge Cases / Failure Scenarios

- **하드코딩 length 함정(MONO-241 교훈 재발 방지)**: console-web `operator-overview-types.ts` 에 `.length(5)` + `CARD_ORDER` 5-원소가 박혀있음(data-driven 아님). 6 으로 안 바꾸면 6-카드 envelope 가 `OperatorOverviewSchema.parse` 에서 throw → operator-overview 전체 "bff-unavailable" degrade. 반드시 `.length(6)` + CARD_ORDER ecommerce + refine 메시지.
- **ADMIN-role 게이트 함정**: 신규 GET 에 기존 write 처럼 `validateAdminRole(X-User-Role==ADMIN)` 를 넣으면 operator 의 IAM OIDC 토큰(ecommerce ADMIN role 없음)이 403 → overview 카드 항상 `forbidden`. → GET 은 role 미검사(인가는 게이트 OPERATOR+entitlement-trust). AC-2 에서 단언.
- **direct-to-producer 가정 함정**: 다른 5 overview leg 는 direct-to-producer 이나 ecommerce product-service 는 JWT resource server 가 아니라 게이트 헤더-trust 서비스 → **반드시 게이트(`ecommerce.local`) 경유**. console-bff 가 product-service 직접 호출하면 X-Tenant-Id/X-User-* 를 console-bff 가 위조해야 하고 account_type/JWT 검증 우회 → 보안 스멜. `ecommerceRestClient`(=ecommerce.local) 재사용으로 게이트가 검증.
- **EnumMap 순서 함정**: ECOMMERCE 는 이미 `DomainTarget` enum 끝(MONO-241) → 기존 5 도메인 EnumMap iteration byte-stable. (본 태스크는 enum 미변경.)
- **cross-leg 401 collapse vs per-card degrade**: ecommerce 게이트가 토큰 401(invalid)→composition 401 collapse(공유 토큰); 403 TENANT_FORBIDDEN(tenant 미구독)→카드 `forbidden`; timeout/5xx→카드 `degraded`. 혼동 금지(기존 erp/finance 패턴 동일).
- **base-url**: `consolebff.outbound.ecommerce.base-url` 은 MONO-241 에서 이미 추가(health leg) → overview adapter 가 동일 빈 재사용. 신규 yml 키 불요(확인만).
- **상품 0개 tenant**: `totalElements=0` → 카드 `ok` + `productCount:0`("상품 0개"). degrade 아님(정상 빈 카탈로그). console-web 가 0 을 falsy 로 숨기지 않게 주의(`nullable().optional()` + 명시 0 렌더).
- **ecommerce IT 트랩(Step2/3 동일)**: product-service 신규 read 의 M6 IT 는 `@SpringBootTest(classes=ProductServiceApplication.class)` 핀(다중 @SpringBootConfiguration 잠복-red 회피). ⚠️ecommerce 는 PR-gated IT 매트릭스 부재(Build&Test 게이트) + Rancher npipe degraded 면 compiled-only → unit/slice GREEN 이 권위, M6 IT 는 핀+컴파일 확인. console-bff IT 는 CI 실행(권위).

# Notes

- ⚠️ JDT.LS OOM 리스크 — STEP A(facet a-후속-2)만 종결하고 체크포인트. 정산(b) 설계는 컨텍스트 여유 보고.
- 구현은 격리 worktree `mlab-mono243`(브랜치 `task/mono-243-operator-overview-ecommerce-leg`, base=origin/main `751072fc1`)에서만. 메인 체크아웃(`task/pc-fe-074-spec`) + 동시 세션 worktree(`mlab-be365`=settlement WIP) 미접촉.
- ⚠️ **서브에이전트 위임 시 worktree 절대경로 명시 + 위임 직후 메인 `git status --porcelain -- projects/` 로 stray 누출 확인**(CLAUDE.md worktree-isolation subagent-dispatch = MONO-242).
- 머지 후 close-chore: review→done 은 Status 만, closure narrative 는 커밋 메시지(HARDSTOP-05). 3-dim 검증(state=MERGED / origin tip 일치 / pre-merge 0 failing required).
- 동시 세션 TASK 카운터 충돌 주의 — 머지 직전 root `tasks/` MONO 최대값 재확인(현재 242 done 기준 243).
