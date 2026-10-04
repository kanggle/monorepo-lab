# Task ID

TASK-MONO-759

# Title

`ADR-MONO-079` D2 후속 — 팬 소속사 ↔ 스토어 셀러 연결의 **실제 셀러 조회 경로**(fan artist-service → ecommerce 셀러) — `TASK-MONO-748` AC-3 분리분

# Status

review (2026-10-03 UTC — 갈래 A + 도달 경로 R1 구현 · 단위/슬라이스 초록 · ⚪ Testcontainers IT 와 데모 창은 미측정)

# Owner

monorepo

# Task Tags

- fan-platform
- ecommerce
- cross-project

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (프로젝트 간 인증 · IdP/권한 카탈로그 · fail-closed)

---

# Dependency Markers

- **선행**: `TASK-MONO-748` (소속사 엔티티 · `StoreSellerDirectory` 포트 · 검증 규칙 · fail-closed 어댑터 `UnwiredStoreSellerDirectory`)
- **선행(결정)**: 소유자의 전송 방식 선택 — AC-0
- **출처**: 소유자 결정 2026-10-02 «분리 후 진행» — 748 의 끝난 부분을 먼저 머지하고 셀러 연결 검증(AC-3)만 이 티켓으로 분리

# ⏳ AC-0 — DO NOT START until the owner picks the transport

🔴 **착수 금지** — 소유자가 아래 갈래 중 하나를 **명시적으로** 고를 때까지. 이 티켓의 구현은 프로젝트 간 인증 결정이고(`TASK-MONO-748` HARDSTOP-09), 갈래 A 는 IdP 등록·권한 카탈로그 변경이라 **소유자 승인이 따로** 필요하다(`TASK-MONO-726` 이 같은 종류의 등록에 소유자 승인을 받았다).
착수 시 먼저: ① 소유자 선택을 이 절에 인용해 적는다 ② 아래 «현황» 을 재측정한다(product-service 가 여전히 JWT 를 검증하지 않는가 · artist-service 에 IdP 클라이언트가 여전히 없는가 · `WorkloadTenantCatalog` 의 현재 모양).

## 현황 (2026-10-02 실측 — `TASK-MONO-748` HARDSTOP-09)

- 스토어의 셀러 데이터는 ecommerce **product-service** 에 있고, product-service 는 **JWT 를 전혀 검증하지 않는다** — ecommerce 게이트웨이가 넣는 `X-Tenant-Id` / `X-User-Role` 헤더를 믿는다. `/internal/**` 표면이 없고, 셀러 읽기는 운영자 평면 `GET /api/admin/sellers/{sellerId}`(`X-User-Role == ECOMMERCE_OPERATOR`) 하나뿐이다.
- artist-service 는 **IdP 클라이언트가 없다**(리소스 서버 전용). 팬의 유일한 워크로드 클라이언트는 `community-service-client` 이고 그 범위·테넌트는 그것의 것이다.
- 스토어 테넌트(`ecommerce`)의 토큰을 워크로드가 얻으려면 assume-tenant(`WorkloadTenantCatalog`, `ADR-MONO-076`) 항목이 필요하다.
- 그래서 지금 배포되는 어댑터 `UnwiredStoreSellerDirectory` 는 항상 «검증 불가» — **셀러 연결은 전부 503 `STORE_SELLER_LOOKUP_UNAVAILABLE`, 저장 0**. 해제(`null`)는 된다.

## 갈래

| 갈래 | 무엇 | 대가 |
|---|---|---|
| **A** | **워크로드 `client_credentials` 토큰** — 새 IdP 클라이언트 등록(auth-service Flyway, 예 `artist-service-client`) + 셀러 읽기 범위(예 `store.seller.read`) + `WorkloadTenantCatalog` assume-tenant(`ecommerce`) + product-service 의 **내부 읽기 엔드포인트**(예 `GET /internal/sellers/{sellerId}`, 자체 JWT 체인 — product-service 첫 JWT 표면). `TASK-MONO-726` 모양 | 🔴 IdP·권한 카탈로그 변경 ⇒ **소유자 명시 승인** 필요. 동기 호출이라 쓰기 시점에 살아 있는 답 |
| **B** | **이벤트로 채운 셀러 상태 복제본**(artist-service 안) — 셀러 생성/정지/폐점 이벤트를 프로젝트 간 릴레이로 받아 `store_sellers(seller_id, status)` 를 유지하고 그것으로 검증 | 🔴 **ADR-079 라이더/개정** 필요(D2 는 «스토어의 셀러 조회로»). artist-service 의 Service Type 이 `rest-api` 단일 → `event-consumer` 추가. 복제 지연 동안 CLOSED 를 못 볼 수 있음(지연 상한을 정해야 함) |
| C | **공개 스토어 스냅숏**(`@demo/public-data` `store.json`) | **기본 기각** — 실시간이 아니고, CLOSED 셀러를 믿을 수 있게 보이지 않는다(공개 스냅숏은 판매 중 상품 중심). 쓰기 시점 검증이 아니다 |

## ✅ 소유자 선택 (2026-10-03 UTC) — 갈래 A, IdP 등록·권한 카탈로그 변경 승인 포함

소유자가 고른 선택지(원문 그대로): «A: 워크로드 토큰 + 내부 읽기 (Recommended)»

선택지 본문(원문 그대로): "artist-service용 IdP 클라이언트를 등록하고 셀러 읽기 범위·ecommerce assume-tenant를 줍니다. product-service에 내부 셀러 읽기 API(첫 JWT 표면)를 만들어 동기로 묻습니다. 이 선택은 IdP 등록과 권한 카탈로그 변경에 대한 승인을 포함합니다."

⇒ AC-0 의 두 요건(갈래 선택 · 갈래 A 의 IdP 등록·권한 카탈로그 변경 명시 승인)이 모두 인용됐다.

## 현황 재측정 (2026-10-03 UTC · 정적 · `origin/main` `108c26461`)

| 전제 | 결과 |
|---|---|
| product-service 가 JWT 를 검증하지 않는다 | ✅ 여전히 참 — `build.gradle` 에 `spring-boot-starter-security`/`oauth2-resource-server` 0건, `SecurityFilterChain` 0건, 컨트롤러에 `/internal/**` 매핑 0건(`/internal` 문자열은 iam 을 **부르는** 클라이언트에만 있다) |
| artist-service 에 IdP 클라이언트가 없다 | ✅ 여전히 참 — 저장소 전체에 `artist-service-client`·`store.seller` 0건 |
| `WorkloadTenantCatalog` 의 모양 | 항목 1개 — `product-service-client → {ecommerce, demo-corp}`. 교환 grant 보유 = `platform-console-web`(운영자 분기) + `product-service-client` |

## 🔴 HARDSTOP-09 (2026-10-03 UTC) — 갈래 A 가 정하지 않은 것: **fan → store 의 도달 경로**

구현 착수 전 배선을 재다가 나왔다. 갈래 A 는 «무엇을 부르나»(product-service 내부 읽기) 와 «어떤 자격으로»(워크로드 토큰 + assume-tenant) 를 정했고, **«어느 길로 닿나»** 는 정하지 않았다 — 그리고 지금 저장소에는 그 길이 **없다**.

| 측정 | 결과 |
|---|---|
| artist-service 의 네트워크 | 로컬 `fan-platform-net` 만 · 데모는 `fan-identity.override.yml` 이 `traefik-net` 을 더한다 |
| product-service 의 네트워크 | 로컬·데모 모두 `ecommerce-net` 만 — `infra/demo/*.yml` 어디에도 product-service 를 다른 망에 붙이는 오버레이 0건 |
| ecommerce 게이트웨이 | `/internal/**` 라우트 0건 · `AccountTypeEnforcementFilter` 는 «그 밖의 인증 라우트 → `CUSTOMER` 역할 필수» 라 역할 없는 워크로드 토큰을 403 · `allowed-audiences` = `platform-console-web,ecommerce-web-store-client`(SHADOW) |

⇒ 두 서비스는 **공유 망이 0** 이고(`ADR-MONO-076` § Context 가 iam 에서 잰 것과 같은 모양), 스토어의 공개 입구(게이트웨이)는 내부 경로를 싣지 않는다. 내부 읽기를 만들어도 artist-service 는 거기 닿지 못한다.

```
[VIOLATION] HARDSTOP-09: Task `TASK-MONO-759` (갈래 A) requires an architecture decision — the network path by which fan artist-service reaches ecommerce product-service's new `/internal/sellers/{sellerId}` — that the owner's choice of 갈래 A and no spec/ADR documents.
[WHY] artist-service and product-service share no docker network locally or in the demo, and the ecommerce gateway routes no `/internal/**` path (its AccountTypeEnforcementFilter would 403 a role-less workload token and its audience allowlist does not name a new client). Each way to open a path is a new edge/topology decision: (1) an ecommerce-gateway route for `/internal/sellers/**` + a workload branch in AccountTypeEnforcementFilter + an audience-allowlist entry — the iam precedent (`/internal/tenants/**` on the iam gateway, kept by ADR-MONO-076 Consequences 4) but a new public-edge exposure for the store; (2) joining product-service to a shared network (e.g. `traefik-net`) and calling it by container name — the shape TASK-MONO-721 offered as ⓐ «네트워크» and the owner did not choose there; (3) a dedicated Traefik router (internal hostname + path) to product-service. Picking one here would decide the store's edge implicitly.
[REMEDIATION] Choose one:
  1. Owner picks the reach path (R1 gateway route · R2 shared network · R3 Traefik router), recorded in this ticket's AC-0 (or as an ADR-MONO-079 rider); then implement 갈래 A end to end in one PR (contracts → V0042 IdP client + WorkloadTenantCatalog entry → product-service `/internal/**` JWT chain → artist-service HTTP adapter → compose/demo wiring + `check-internal-caller-addresses.sh` rows).
  2. Split: land the decided parts now (contracts, IdP client, catalog entry, product-service internal chain, artist-service adapter with its base URL as configuration) with the deployed link still answering 503 until the reach path is decided — the fail-closed state 748 already ships; the reach path becomes its own AC/ticket.
[REFERENCE] CLAUDE.md § Hard Stop Rules · platform/hardstop-rules.md#hardstop-09 · ADR-MONO-076 § Context (공유 네트워크 0) · TASK-MONO-721 AC-0 ⓐ · projects/ecommerce-microservices-platform/apps/gateway-service/src/main/java/com/example/gateway/filter/AccountTypeEnforcementFilter.java · infra/demo/fan-identity.override.yml
```

⇒ 이 티켓은 `ready/` 에 남는다(구현 0줄). 다음 행동 = 소유자의 도달 경로 선택(또는 분리 결정).

### ✅ 해소 — 소유자 결정 (2026-10-03 UTC): 도달 경로 = **R1 스토어 게이트웨이 경유**

소유자가 고른 선택지(원문 그대로): «R1: 스토어 게이트웨이 경유 — ecommerce 게이트웨이에 `/internal/sellers/**` 라우트를 추가하고, 워크로드 토큰은 CUSTOMER 역할 검사 대신 범위·테넌트로 거릅니다. 새 클라이언트는 audience 허용 목록에 넣습니다. iam이 `/internal/tenants/**`를 게이트웨이로 실어 나르는 것과 같은 모양이고 신뢰 경계가 게이트웨이에 남습니다. 대신 공개 게이트웨이에 내부 경로가 하나 생깁니다(JWT·범위·테넌트로 보호).»

⇒ HARDSTOP-09 해소. 갈래 A + R1 을 한 PR 로 구현한다. 게이트웨이 쪽 구속(조정자 전달): iam 게이트웨이의 `/internal/tenants/**` 처리를 형제 선례로 복사 · `AccountTypeEnforcementFilter` 의 워크로드 분기는 **`/internal/sellers/**` 만, 셀러 읽기 범위 + 테넌트 `ecommerce` 를 실은 토큰만** · 그 밖의 모든 경로는 CUSTOMER 규칙 그대로 · audience 허용 목록에 새 클라이언트 추가(SHADOW 유지, 전환 금지).

# Goal

`TASK-MONO-748` 이 만든 `StoreSellerDirectory` 포트에 **실제 조회 경로**를 붙여, 소속사 → 스토어 셀러 연결(`PATCH /api/agencies/{id}/store-seller`)이 운영에서 «존재하는 ACTIVE 셀러 → 저장» 이 되게 한다. 검증 규칙과 fail-closed 성질은 748 그대로다.

# Scope

## In Scope

- 소유자가 고른 갈래의 전송 구현 + `StoreSellerDirectory` 실제 어댑터(`UnwiredStoreSellerDirectory` 교체 — 🔴 **허용 스텁으로 교체 금지**)
- 스토어 쪽 계약(갈래 A: `product-api.md` 에 내부 읽기 엔드포인트 / 갈래 B: 이벤트 계약 + 복제 테이블) — **계약 먼저**
- (A) IdP 클라이언트 등록 · 범위 · assume-tenant 항목 · product-service 내부 JWT 체인 / (B) ADR-079 라이더 · 소비자
- `artist-api.md` § Store seller verification 의 «NOT WIRED» 문구 · `dependencies.md` 갱신

## Out of Scope

- 검증 규칙 변경(아래 «상태 규칙» 그대로) · 소속사 CRUD/이전(748) · 콘솔 화면(751) · 셀러 구성원(752)

## 가져오는 계약 (748 에 이미 있음)

**호출자 쪽** (`projects/fan-platform/specs/contracts/http/artist-api.md` § Store seller verification): artist-service 는 `StoreSellerDirectory.findStatus(sellerId)` 로 묻는다.
- `Optional.of(status)` — 스토어 `SellerStatus` 이름 그대로 — 또는 `Optional.empty()` 는 **확정적 «없음»**(예 스토어의 404 `SELLER_NOT_FOUND`)에만
- 그 밖의 모든 것(전송 오류·타임아웃·인증 실패·모르는 응답) → `StoreSellerLookupUnavailableException`

**스토어 쪽 요구**: 스토어 테넌트(`ecommerce`) 안에서 **셀러 하나를 id 로 읽기** → 최소 `{ "sellerId", "status" }`, 그리고 **구별되는 not-found**.

**상태 규칙** (748 `AgencyService`): `ACTIVE` / `SUSPENDED` / `PENDING_PROVISIONING` → 저장 · 없음 → 422 `STORE_SELLER_NOT_FOUND` · `CLOSED` → 422 `STORE_SELLER_CLOSED` · 조회 실패·모르는 상태 → 503 `STORE_SELLER_LOOKUP_UNAVAILABLE`, **저장 안 함**(fail-closed).

# Acceptance Criteria

- [x] **AC-0** — 소유자의 갈래 선택이 이 티켓에 인용돼 있다(⏳ 그 전 착수 금지). 갈래 A 면 IdP 등록·권한 카탈로그 변경의 **명시 승인**도 인용. — ✅ 2026-10-03 UTC 갈래 A + 승인 인용(§ 소유자 선택). 🔴 단, 재측정에서 **도달 경로 HARDSTOP-09** 가 나와 구현은 대기(§ HARDSTOP-09).
- [x] **AC-1** (`TASK-MONO-748` AC-3 원문) — 셀러 연결: 존재하는 ACTIVE 셀러 → 저장 · 없는/`CLOSED` 셀러 → 거절 · 셀러 조회 실패 → 저장 안 함(fail-closed). 🔴 **실제 전송 경로 위에서**(포트 대역이 아니라) 시험한다.
- [x] **AC-2** — 조회 실패 대조군: 스토어/IdP 를 내린 상태에서 연결 시도 → 503 · 저장값 불변. 그리고 같은 시험 안에서 정상 경로 200(«열린 경로 + 닫힌 경로»).
- [x] **AC-3** — (A) 새 워크로드 자격은 **셀러 읽기만** 된다: 같은 토큰으로 셀러 변경·다른 테넌트 assume 은 거절 / (B) 복제 지연·순서 뒤집힘(정지 후 재활성)의 결과가 시험으로 고정.
- [x] **AC-4** — `UnwiredStoreSellerDirectory` 가 제거되거나 비활성일 때도 **허용 쪽 빈이 생기지 않는다**(빈이 0개면 기동 실패 or fail-closed 기본값 — 748 `AgencyDisplayTest.unwiredDirectoryIsFailClosed` 의 성질 유지).

# Related Specs

- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D2
- `docs/adr/ADR-MONO-076-which-workload-credential-may-act-on-which-tenant.md` (assume-tenant, 갈래 A)
- `projects/fan-platform/specs/services/artist-service/{architecture,dependencies}.md`
- `projects/ecommerce-microservices-platform/specs/services/product-service/`

# Related Contracts

- `projects/fan-platform/specs/contracts/http/artist-api.md` § Agencies › Store seller verification (호출자 쪽 — 748)
- `projects/ecommerce-microservices-platform/specs/contracts/http/product-api.md` (갈래 A: 내부 셀러 읽기 추가) / 이벤트 계약(갈래 B)

# Edge Cases

- 셀러가 `SUSPENDED` — 저장한다(D2 는 «없거나 CLOSED» 만 거절, 정지는 되돌릴 수 있다).
- 이미 연결된 셀러가 나중에 `CLOSED` 가 됨 — 이 티켓은 **쓰기 시점** 검증만 한다. 기존 연결의 사후 정리는 별도 결정(필요하면 후속 티켓).
- 스토어가 모르는 상태 문자열(새 enum 값) — 503, 저장 안 함.

# Failure Scenarios

1. 조회 장애 때 검증 없이 저장해 존재하지 않는 셀러를 가리킨다(748 Failure Scenario 2 — 이 티켓에서 처음 실제 경로로 노출된다).
2. (A) 새 워크로드 자격이 셀러 읽기보다 넓다(쓰기·다른 테넌트) — 권한 카탈로그가 «무엇을» 과 «어디에» 를 둘 다 좁혀야 한다.
3. (B) 복제본이 비어 있는 첫 기동에서 모든 셀러를 «없음»(422)으로 답한다 — «아직 모른다» 와 «없다» 를 구별해야 한다(그 동안은 503).

---

# 구현 기록 (2026-10-03 UTC · 분석=Opus 5.5 / 구현=Opus 5.5)

갈래 A + 도달 경로 R1 을 한 PR 로. 계약 먼저, 그 다음 IdP → 받는 쪽(product-service) → 엣지(ecommerce 게이트웨이) → 보내는 쪽(artist-service) → 배선.

## 무엇을 바꿨나

| 층 | 변경 |
|---|---|
| 계약 | ecommerce `product-api.md` § Internal seller read 신설(`GET /internal/sellers/{sellerId}` → `{sellerId, status}` · 404 `SELLER_NOT_FOUND` · 401/403 두 층 표) · `iam-integration.md`(audience 목록 · 규칙 6 워크로드 예외 · 오류 행 · 클라이언트 표) · fan `artist-api.md` § Store seller verification(«NOT WIRED» → 전송 3단계 + 매핑 표) · artist-service `dependencies.md` / `architecture.md` · `platform/contracts/jwt-standard-claims.md` 규칙 6 ecommerce 하위 항목 1줄 |
| IdP | auth-service **`V0042__seed_artist_service_workload_client.sql`** — `oauth_scopes` 에 `store.seller.read`(V0032 모양) · `artist-service-client`(`fan-platform`/`B2C`, `client_credentials`, 범위 `store.seller.read` 하나, 공유 dev BCrypt) · 교환 grant 는 **별도 `UPDATE … WHERE client_id=`**(V0037 모양 — `WorkloadTenantCatalogTest` 의 문장 단위 귀속을 지키려고). `WorkloadTenantCatalog`: `artist-service-client → {ecommerce}` (demo-corp·fan-platform 제외 — 사유 주석) · `WorkloadRoleCatalog`: **빈 맵** · 인구조사 18/12/6 → **19/13/6** |
| 받는 쪽 | product-service 첫 JWT 표면 `ProductSecurityConfig`(형제 `OrderSecurityConfig` 복사): `/internal/**` 체인 — 디코더 안에서 timestamp + issuer + `RequiredScopeValidator(store.seller.read)` + `TenantClaimValidator.forTenant("ecommerce")`(와일드카드·entitlement 없음) → 실패 401; `GET /internal/sellers/*` 만 `authenticated`, 그 밖 `denyAll` → 403. 나머지 전부 permit-all 체인(헤더 신뢰 그대로). `InternalSellerController` — **테넌트는 토큰에서**(헤더 아님). 기존 슬라이스 8개는 `@Import(ProductSecurityConfig)` 로 실제 체인 아래서 돈다 |
| 엣지 | ecommerce 게이트웨이 라우트 `product-service-internal`(`Path=/internal/sellers/**`, `StripPrefix=0` — iam `account-service-internal` 모양) · `AccountTypeEnforcementFilter` 에 그 접두사 **하나만** 의 분기: GET/HEAD **∧** `scope ∋ store.seller.read` **∧** `tenant_id == ecommerce` **∧** 정규화된 경로(디코드 경로·raw URI 둘 다 — `..`·`//`·`;`·`%` 거절). 그 경로에서 CUSTOMER/OPERATOR 규칙은 적용되지 않는다(= CUSTOMER 토큰 거절). 다른 모든 경로 무변경 · audience 목록에 `artist-service-client` 추가(**SHADOW 유지**) |
| 보내는 쪽 | artist-service `HttpStoreSellerDirectory`(200 알려진 상태 → 그 값 · 404+`SELLER_NOT_FOUND` → empty · **그 밖 전부** → `StoreSellerLookupUnavailableException`, 200 의 `sellerId` 불일치·모르는 상태 포함) · `StoreTenantTokenProvider`(product-service `TenantScopedIamTokenProvider` 의 고정-테넌트 복사 — `libs/` 승격은 ADR 몫이라 복사, 주석에 사유) · `StoreSellerDirectoryConfig` 가 **유일한** 빈 · `UnwiredStoreSellerDirectory` **삭제** |
| 배선 | ecommerce compose product-service `PRODUCT_INTERNAL_OAUTH2_JWK_SET_URI`/`_ISSUER` · fan compose artist-service `IAM_TOKEN_URI`/`STORE_SELLER_BASE_URL` · `infra/demo/demo.env` 세 키(product 두 값은 order-service 행과 같은 값, `STORE_SELLER_BASE_URL=http://ecommerce.${DEMO_DOMAIN}`) · `scripts/check-internal-caller-addresses.sh` product-service 행 확장 + artist-service 행 추가 |

## AC

| AC | 판정 | 증거 |
|---|---|---|
| **AC-0** | ✅ | § 소유자 선택(갈래 A + 승인) · § HARDSTOP-09 해소(R1) — 둘 다 원문 인용 |
| **AC-1** | ✅ (실제 전송) | `AgencyStoreSellerTransportTest` — 실제 `AgencyService` + **운영 배선(`StoreSellerDirectoryConfig`)으로 만든** 어댑터가 소켓 위 IdP·스토어 대역(`FakeIdpAndStore`, cc → 교환 → GET)과 실제 HTTP 로 통신, 포트 스텁 0: ACTIVE 저장 · 없음 422 · CLOSED 422 · SUSPENDED 저장(Edge 1) · 500 → 503 미저장, 저장값은 저장소에서 다시 읽어 판정(스토어 호출 4회 확인). 매핑 16칸 `HttpStoreSellerDirectoryTest`(코드 없는 404 · 401/403 · 모르는 상태(Edge 3) · 깨진 본문 · 다른 셀러 답 · 스토어 다운 · 타임아웃 · IdP 다운 · 교환 거절 → 전부 unavailable). 스프링 배선 판: `StoreSellerLinkTransportIntegrationTest`(⚪ CI) |
| **AC-2** | ✅ | `AgencyStoreSellerTransportTest.ac2_openClosedOpen` — **한 시험 안에서** 열림(200 저장) → 스토어 다운(503, 저장값 불변) → 열림(같은 셀러 200) → IdP 다운(503, 불변, 스토어 호출 0) → 열림. IT 판도 같은 모양(⚪ CI) |
| **AC-3** | ✅ | 쓰기 불가: product-service `InternalSellerChainTest.CannotWrite`(PATCH/POST/DELETE `/internal/sellers/**` · 다른 `/internal/**` → 403, 서비스 호출 0 · 운영자 쓰기 경로 `/api/admin/sellers/{id}/close` 는 그 토큰만으로 403 `ACCESS_DENIED`) · 게이트웨이 `SellerReadWorkloadAdmissionTest`(쓰기 동사 403 · **`application.yml` 의 모든 다른 라우트 × 4동사에서 CUSTOMER 없는 워크로드 토큰 403** · CUSTOMER/OPERATOR 토큰은 `/internal/sellers/**` 403 · 다른 테넌트/와일드카드/무테넌트 403 · 비정규 경로 403) · 역할 0(`WorkloadRoleCatalog` 빈 맵, `exactlyOneWorkloadClientIsGrantedAnything` 무변). 다른 테넌트 assume 불가: `WorkloadTenantCatalogTest.artistServiceClientIsConfinedToTheStoreTenant`(demo-corp·fan-platform·wms·scm·erp·finance·iam·global·`*` 전부 false) + `WorkloadAssumeTenantProviderTest`(실제 provider 로 demo-corp/wms/fan-platform 교환 → `invalid_grant`, 민트 0; ecommerce 는 성공 + 범위 = `store.seller.read` 만). 받는 쪽도: product-service 는 범위·테넌트가 틀린 토큰을 401 (`InternalSellerChainTest.Refused` 7칸 — CUSTOMER 토큰 · 다른 테넌트 · `*` · 교환 안 한 fan 토큰 · 다른 issuer · 다른 키) |
| **AC-4** | ✅ | `StoreSellerDirectoryConfigTest` — 기본 = HTTP 어댑터 1개 · 닿지 않는 설정 → unavailable(상태를 지어내지 않음) · 빈 설정 5종 → **기동 실패** · 설정 제거(빈 0개) → 포트 소비자 기동 실패 · 구조: `@Bean` 메서드 정확히 1개 · 메인 코드의 `StoreSellerDirectory` 구현은 `HttpStoreSellerDirectory` 하나뿐(always-ACTIVE 스텁이 생기면 빨강). 748 의 `unwiredDirectoryIsFailClosed` 는 이 클래스로 옮겼다 |

## bite (주입 확인 → 빨강 → 복사로 복원 → `BITE` grep 0)

- artist-service: `HttpStoreSellerDirectory` 의 «그 밖 → unavailable» 을 `return Optional.of("ACTIVE")` 로 → `*StoreSeller*` **4 FAILED** (rc=1) → 복원.
- 게이트웨이: 워크로드 분기에 `|| hasRole(roles, "CUSTOMER")` → `SellerReadWorkloadAdmissionTest` **1 FAILED**(CUSTOMER 토큰 칸) (rc=1) → 복원.
- 가드: fan compose 에서 `STORE_SELLER_BASE_URL` 삭제 → rc=1 `DRIFT § artist-service` · ecommerce compose 에서 `PRODUCT_INTERNAL_OAUTH2_ISSUER` 삭제 → rc=1 `DRIFT § product-service` → 복원 rc=0.
- 🔴 첫 실행에서 게이트웨이 시험 하나가 **내 시험의 결함**으로 빨갰다: `MockServerHttpRequest.method(m, String)` 이 `UriComponentsBuilder` 를 거쳐 `//` 를 접어 버려 «`/internal/sellers//s-1` 거절» 칸이 필터에 정규화된 경로를 넘겼다. 시험을 raw `URI` 로 바꾸고, 필터는 디코드 경로와 raw URI 를 **둘 다** 검사하게 했다.

## 게이트 기록 (rc · 개수 — JUnit XML 합산)

| 게이트 | 결과 |
|---|---|
| `auth-service:test` / `:check` | rc=0 · 1014 tests · 0 fail · 33 skip(기존 Docker IT) / rc=0 |
| `product-service:test` / `:check` | rc=0 · 438 tests · 0 fail / rc=0 |
| ecommerce `gateway-service:test` / `:check` | rc=0 · 159 tests · 0 fail / rc=0 (첫 실행 1 fail = 시험 결함, 위 bite 절) |
| `artist-service:test` / `:check` | rc=0 · 262 tests · 0 fail / rc=0 |
| `infra/demo/verify-demo-wrapper.sh` (정적) | rc=0 «정적 검증 PASS» |
| 필수 3종 + `check-flyway-version-collision` · `check-flyway-unresolvable-placeholder` · `check-dev-seed-migration-band` · `check-internal-caller-addresses`(12키 · self-test rc=0) · `check-gateway-drift` · `check-service-map-drift` · `check-jwt-claims-registry` · `check-ls-files-guard-count` · `check-required-check-names` | 전부 rc=0 (스테이지 후) |
| `scripts/check-*.sh` 전수 37개(스크립트를 고쳤으므로) | 35 rc=0 · 2 rc=1 = **환경**: `check-erp-single-tenant-ratchet`(erp MySQL 컨테이너 필요 — Docker 미기동) · `check-prerendered-demo-verdict`(`DEMO_API_BASE` + web-store 빌드 필요). 둘 다 이 변경과 무관한 입력 |

## ⚪ 안 잰 것

- **Testcontainers IT**: `StoreSellerLinkTransportIntegrationTest`(artist-service) 는 작성만 — 로컬 Docker 미사용, CI `integrationTest` 레인이 첫 실행. product-service·auth-service 의 기존 IT 도 이 PR 에서 로컬로 안 돌렸다(product-service 에 Spring Security 가 처음 들어갔으므로 `@SpringBootTest` + MockMvc IT 들이 permit-all 체인 아래 그대로인지는 CI 가 판정).
- **데모 창**: fan 컨테이너 → `ecommerce.<데모도메인>` → 게이트웨이 → product-service 의 실제 도달, IdP 의 실제 교환(실 `V0042` 행 · 실 `WorkloadTenantCatalog`), 실제 토큰의 `iss` 와 `PRODUCT_INTERNAL_OAUTH2_ISSUER` 일치 — 전부 미측정. 717/718/721 이 보인 대로 배선은 고침이 아니다. 판정 술어: 데모에서 소속사에 `default` 셀러를 연결 → 200 이고 `agencies.store_seller_id` 가 바뀐다(결과 상태). 🔴 신선 볼륨/재굽기 필요(V0042 · compose).
- 로컬 `*.local` 기본값이 fan 컨테이너 안에서 해소되는지(형제 community-service 의 `iam.local` 과 같은 가정).

# 데모 창 — 19차 창 (2026-10-04 UTC · 인스턴스 i-08d452973ebf789be · AMI ami-00815e1f9614cda90 · 커밋 2a49dfb48) — ⚪ 판정 못 함 (review 유지)

`platform@demo.com`(테넌트 `fan-platform` 자동 선택) → `/fan/agencies` → «팬 디렉터리 정보를 일시적으로 불러올 수 없습니다». 소속사 화면에 도달하지 못해 셀러 연결을 시도하지 못했다 ⇒ 이 티켓의 술어(`default` 연결 → 200 · `store_seller_id` 변경)는 **잴 수 없었다**. 759 의 결함이라는 증거도, 아니라는 증거도 아니다.

원인(이 티켓 밖): Vercel 로그에서 같은 요청이 `fan_ok status=200 path=/api/v1/agencies` 다음 **1ms 뒤** `fan_error` — 게이트웨이·artist-service 는 200 을 줬고 콘솔이 **본문 파싱에서** 실패했다. artist-service 의 실효 `ObjectMapper` 가 `RedisCacheConfig` 의 것이라(Boot 자동설정이 물러남) `WRITE_DATES_AS_TIMESTAMPS` 가 켜져 있고, `AgencyView.createdAt/updatedAt`(`Instant`)이 숫자로 나간다 — 콘솔 `AgencySchema` 는 문자열(계약 `artist-api.md` 도 ISO 문자열). 이 기전은 `GlobalExceptionHandlerEnvelopeContractTest` 가 이미 실측해 적어 둔 것이다(«오류 봉투만 고쳤다»). ⚪ 응답 본문 자체는 직접 보지 못했다(콘솔 토큰 없이는 못 받는다) — 로그 순서 + 코드 + 기록된 실측의 추론. 소유자 결정(2026-10-04): **백엔드를 고쳐 다음 재굽기에 싣는다**(콘솔이 숫자를 받아주는 우회는 하지 않는다) → `TASK-FAN-BE-050`. 이 티켓의 데모 판정은 그 재굽기 창에서.

🔵 곁발견: 콘솔 `AgencyDetail.tsx` 의 안내(`fan-agency-seller-unwired-note` — «스토어 조회가 아직 연결 안 됨», 머리 주석 25–32행)는 759 이후로 사실이 아니다. 화면 판정 때 함께 고칠 것.

---

## CORRECTION (2026-10-04 UTC)

위 곁발견을 별도 PR(`fix/console-agency-seller-stale-note`)에서 처리했다 — `AgencyDetail.tsx` 의 낡은
`fan-agency-seller-unwired-note` 안내와 머리 주석을 걷어내고, 503 상태 문구의 "아직 배선 안 됨" 표현도
지금 동작(실 스토어 조회 · fail-closed)에 맞게 고쳤다. 단위 시험(`FanAgencyDetail.test.tsx`)도 함께 갱신.
