# Task ID

TASK-MONO-759

# Title

`ADR-MONO-079` D2 후속 — 팬 소속사 ↔ 스토어 셀러 연결의 **실제 셀러 조회 경로**(fan artist-service → ecommerce 셀러) — `TASK-MONO-748` AC-3 분리분

# Status

ready

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
- [ ] **AC-1** (`TASK-MONO-748` AC-3 원문) — 셀러 연결: 존재하는 ACTIVE 셀러 → 저장 · 없는/`CLOSED` 셀러 → 거절 · 셀러 조회 실패 → 저장 안 함(fail-closed). 🔴 **실제 전송 경로 위에서**(포트 대역이 아니라) 시험한다.
- [ ] **AC-2** — 조회 실패 대조군: 스토어/IdP 를 내린 상태에서 연결 시도 → 503 · 저장값 불변. 그리고 같은 시험 안에서 정상 경로 200(«열린 경로 + 닫힌 경로»).
- [ ] **AC-3** — (A) 새 워크로드 자격은 **셀러 읽기만** 된다: 같은 토큰으로 셀러 변경·다른 테넌트 assume 은 거절 / (B) 복제 지연·순서 뒤집힘(정지 후 재활성)의 결과가 시험으로 고정.
- [ ] **AC-4** — `UnwiredStoreSellerDirectory` 가 제거되거나 비활성일 때도 **허용 쪽 빈이 생기지 않는다**(빈이 0개면 기동 실패 or fail-closed 기본값 — 748 `AgencyDisplayTest.unwiredDirectoryIsFailClosed` 의 성질 유지).

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
