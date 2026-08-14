# ADR-MONO-065 — wms admin 읽기 평면의 테넌트 축

**Status:** PROPOSED
**Date:** 2026-08-14
**주관 티켓:** `TASK-BE-583` (AC-1)
**선행:** [`ADR-MONO-064`](ADR-MONO-064-wms-outbound-tenant-visibility-plane.md) § D4 (이 결정을 여기로 분리) · [`ADR-MONO-019`](ADR-MONO-019-platform-console-customer-tenant-model.md) § D5 (entitlement-trust) · [`ADR-MONO-020`](ADR-MONO-020-operator-multitenant-assignment.md) § D3 (assume-tenant 최소권한) · [`ADR-MONO-022`](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) § D9 + `TASK-MONO-304` (outbound 격리) · [`ADR-MONO-030`](ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) § 1.1 (*"wms 는 단일 테넌트"*)
**선례:** [`projects/erp-platform/docs/adr/ADR-001`](../../projects/erp-platform/docs/adr/ADR-001-erp-event-plane-tenant-axis.md) — **같은 결함 클래스에서 반대 결론**. 왜 갈리는지는 § M7.

## History

- 2026-08-14 — PROPOSED.

  🔴 **소유자 지정이 이미 하나 있었고, 그것이 이 문서를 여는 근거다 — 그러나 이 문서를 ACCEPT 하지는
  않는다.** `TASK-BE-583` AC-1(A/B/C 중 티켓의 방향)에 대해 소유자가 **B(테넌트 축 신설)** 를
  지정했고, AC-1 자신이 *"B 면 ADR ACCEPTED 선행"* 이라 적고 있다. 그 지정은 **"축을 넣는 쪽으로
  간다"** 는 티켓 수준의 방향이고, *어떤 모양의 축인가* 는 아래 실측 M2·M3·M6 이 **새로 연 질문**이다
  (티켓이 쓴 "B = 스키마 + 프로젝션 + 8개 필터" 는 M2 앞에서 성립하지 않는다 — 8개 중 6개는 필터할
  테넌트가 상류에 아예 없다). 따라서 아래 § 선택지의 `B1`/`B2` 는 **AC-1 의 B 안에서 처음 갈리는
  분기**이며, `platform/architecture-decision-rule.md` § The ACCEPTED Gate 의 정확형 지정을 다시 받는다.

---

## Context

**질문 한 줄**: `admin-service` 의 8개 읽기 대시보드는 테넌트에 대해 **무엇을 약속하는가**.

### 이 질문이 열린 이유 — 두 ACCEPTED 결정이 같은 행을 반대로 판정한다

| 표면 | 인가 | 테넌트 필터 | 근거 결정 |
|---|---|---|---|
| `GET /api/v1/outbound/orders` (원시) | 입장 게이트 + `CallerScope` | **있음** — 자기 테넌트만 | `ADR-MONO-022` § D9 · `TASK-MONO-304` · `ADR-MONO-064` |
| `GET /api/v1/admin/dashboard/orders` (프로젝션) | `hasRole('WMS_VIEWER')` | **없음** | `ADR-MONO-019` § D5 · `ADR-MONO-020` D4 |

같은 토큰(`tenant_id=demo-corp`)이 같은 주문에 대해 원시 API 에선 격리되고 프로젝션에선 무필터다
(`ADR-MONO-064` § M5 실측: 프로젝션 `totalElements=1` · 원시 `0`). **어느 쪽도 버그로 들어온 게
아니다** — 둘 다 ACCEPTED 결정의 구현이고, 그래서 구현자가 고를 수 없다.

---

## 이 ADR 이 서 있는 실측 (2026-08-14, `main` = `84040b98b`)

`TASK-BE-583` AC-0/AC-3(2026-08-13, PR #3314)에서 확정된 것 — 대시보드 컨트롤러 **8개**(추정 6이
아니었다) · `admin-service` 전 계층 `tenant` grep **18건 전부 보안 설정 2파일**(컨트롤러·서비스·
리포지토리·프로젝션 **0건**, 대조군 `repository` grep 389건) · `admin_db` **22 테이블 217 컬럼에
테넌트 컬럼 0개** · `WMS_VIEWER` 는 `OperatorRoleDerivation` 이 **아무에게도 주지 않고**
entitlement 합성이 **유일 경로**이며 모집단은 `acme-corp · demo-corp · ecommerce · initech-corp ·
wms` **5개(고객 4 + 네이티브 1)**. 아래 M1~M8 은 그 위에 이 ADR 을 위해 새로 잰 것이다.

### M1 — 콘솔은 8개 표면을 **전부** 읽는다

🔴 **첫 측정이 틀렸고, 술어가 원인이었다.** 리터럴 `admin/dashboard` 로 콘솔을 grep 하면 **2건**이
나오고 전부 주석이다. 콘솔은 경로를 **2단계**로 만든다 — `WMS_ADMIN_BASE_URL` + 상대 경로
(`{ method: 'GET', path: '/dashboard/alerts?…' }`). 리터럴 grep 은 그 조립을 못 본다. 올바른 술어
(상대 경로 리터럴)로 재측정:

```
features/wms-ops/api/wms-alerts-api.ts       /dashboard/alerts          (+ /alerts/{id}/acknowledge)
features/wms-ops/api/wms-inventory-api.ts    /dashboard/inventory · /dashboard/throughput · /dashboard/orders
features/wms-ops/api/wms-refs-api.ts         /dashboard/refs/{type}
features/wms-ops/api/wms-shipments-api.ts    /dashboard/shipments · /dashboard/asns · /dashboard/adjustments
features/wms-outbound-ops/…/outbound-shipment-api.ts   /dashboard/shipments?orderId
```

⇒ **8/8.** `TASK-BE-583` AC-1 이 적은 *"합성을 좁히면 콘솔 wms 대시보드가 전부 막힌다"* 는
**정확하다**(내가 중간에 "2개뿐" 으로 정정하려던 것이 오류였고, 대조군이 잡았다).

### M2 — 🔴🔴 8개 중 상류에 테넌트가 있는 표면은 **1개**다. 6개는 **0**이다

| 컨트롤러 | read-model 테이블 | 상류 도메인 | 테넌트 원천 |
|---|---|---|---|
| `OrderDashboardController` | `admin_order_summary` | outbound | **있음** — `outbound_order.tenant_id` |
| `ShipmentDashboardController` | `admin_shipment_summary` | outbound | **파생 가능** — `order_id` 로 주문에 조인 |
| `AsnDashboardController` | `admin_asn_summary` · `admin_inspection_summary` | inbound | 없음 |
| `InventoryDashboardController` | `admin_inventory_snapshot` | inventory | 없음 |
| `AdjustmentAuditController` | `admin_adjustment_audit` | inventory | 없음 |
| `AlertDashboardController` | `admin_alert_log` | inventory | 없음 |
| `MasterRefController` | `admin_{warehouse,zone,location,sku,lot,partner}_ref` | master | 없음 |
| `ThroughputDashboardController` | `admin_throughput_{inbound,outbound}_daily` | inbound+outbound | 없음 (집계) |

근거는 AC-3 의 `information_schema` 전수다 — **6 DB 91 테이블 중 테넌트 컬럼은
`outbound_order.tenant_id` 하나**. 즉 나머지 6개 표면은 *"필터를 안 건 것"* 이 아니라
**필터할 값이 상류에 존재한 적이 없다.**

⇒ 🔴 **티켓의 "B = 스키마 + 프로젝션 + 8개 필터" 는 성립하지 않는다.** 8개 중 2개만 테넌트 소유
데이터를 투영하고, 6개는 *창고 운영 데이터*(입고 예정 · 재고 스냅샷 · 조정 이력 · 알림 · 마스터
참조 · 일별 처리량)다. 그 6개에 테넌트를 넣으려면 **wms 를 3PL 로 모델링**해야 한다 —
`PROJECT.md` 가 *"단일 물류 센터 가정"* 으로 배제한 바로 그것.

### M3 — 🔴🔴 테넌트를 가진 그 1개조차, **행을 만드는 이벤트에 테넌트가 없다**

`admin_order_summary` 행은 `wms.outbound.order.received.v1` 이 만든다. 그 이벤트는 테넌트를
싣지 않는다 — `OutboundDomainEvent.tenantId()` 의 기본 구현이 `null` 이고, 이를 **재정의하는
이벤트는 정확히 둘**(`ShippingConfirmedEvent` · `OrderCancelledEvent`)뿐이다. 계약도 같은 말을 한다:

> `specs/contracts/events/outbound-events.md` L63 — *"Present **only** on the cross-project
> return-leg events (`outbound.shipping.confirmed`, `outbound.order.cancelled`)"*

admin 프로젝션이 소비하는 **25개 토픽**(inbound 6 · inventory 7 · master 6 · outbound 7 중 실사용)
가운데 테넌트를 실은 것은 그 2개뿐이고, **행을 만드는 이벤트는 거기 없다.**

⇒ B 는 스키마 마이그레이션만이 아니라 **이벤트 계약 변경**을 포함한다(`order.received.v1` 에
`tenantId` 를 additive 로 싣고, wms 가 그것을 **해석**하기 시작한다 — 지금 계약은 명시적으로
*"wms does not interpret it, filter rows by it"* 이라고 적는다).

### M4 — 🔴🔴 저장소의 문서 **세 곳**은 admin-service 가 `tenant_id=wms` 만 받는다고 적는다. 코드는 아니다

```
specs/contracts/http/admin-service-api.md L33
  "RS256 JWT validated against IAM JWKS by both gateway and admin-service; tenant_id=wms enforced."

console-web/src/features/wms-ops/api/wms-client.ts (javadoc, § 2.4.5 인용)
  "the wms gateway + admin-service … enforce tenant_id=wms from the JWT claim itself"
  "wms resolves the tenant from the JWT tenant_id claim (=wms) … wms rejects cross-tenant producer-side."
```

실제 코드:

```java
// admin-service/OAuth2ResourceServerConfig
@Value("${wms.oauth2.required-tenant-id:wms}") …
return TenantClaimValidator.forTenant(requiredTenantId)
        .trustEntitledDomains()   // entitlement-trust dual-accept (ADR-MONO-019 § D5)
```

`required-tenant-id` 는 **도메인 키**로 읽히고(erp `ADR-001` §7 이 명명한 두-축 오독의 반대편 —
여기서는 *올바른* 쪽이다), 실제 수용 모집단은 **5개 테넌트**다(AC-0).

⇒ 🔴 **선택지 A("현 상태를 전역 뷰로 명문화")는 현 상태를 적는 작업이 아니다.** 저장소는 이미
현 상태와 **반대**로 적고 있으므로, A 는 *문서화*가 아니라 **세 문서를 뒤집는 결정**이다.
그리고 뒤집힌 문장은 *"고객사 4곳이 서로의 창고 데이터를 읽는다"* 가 된다.

### M5 — 합성은 사고가 아니라 **설계**다. 다만 설계가 정한 것은 "어느 도메인" 이지 "어느 행" 이 아니었다

```java
// admin-service/SecurityConfig — 주석까지 그대로
// Entitlement-trust dual-accept (ADR-MONO-019 § D5, ADR-MONO-020 D4 — TASK-MONO-162):
// a wms-entitled token (entitled_domains ∋ "wms") is granted ROLE_WMS_VIEWER so the
// @PreAuthorize("hasRole('WMS_VIEWER')") READ dashboards pass.
if (TenantClaimValidator.isEntitled(jwt, ENTITLEMENT_DOMAIN)) {
    authorities.add(new SimpleGrantedAuthority(VIEWER_ROLE));
}
```

`ADR-MONO-019` § D5 는 `entitled_domains` 를 **READ-only 가시성**으로 정의했고,
`ADR-MONO-020` 은 그 모델을 확장하면서 `rules/traits/multi-tenant.md` 를 인용해 범위를 못박았다:

> *"M1-M7 — the **isolation invariants preserved** while the **allowed-set widens** per active selection"*

⇒ 플랫폼 결정이 정한 것은 **"이 테넌트가 어느 도메인을 읽는가"** 이고, **"그 도메인 안에서 어느
행을 읽는가"** 는 각 도메인이 지키기로 되어 있었다. wms admin-service 는 **앞의 절반만 구현**했다.

🔵 두 표면이 각각 자기 단언에 대해 초록인데 **합성이 결함**인 모양이다 — entitlement 게이트도
`@PreAuthorize` 도 자기 일을 정확히 하고 있고, 아무도 교집합을 재지 않았다.

### M6 — 🔴 `multi-tenant` 로 재분류하면 wms 는 **당일 위반**이 된다

`rules/traits/multi-tenant.md` 는 선언 즉시 구속하는 M1~M7 을 싣는다. 오늘 상태와 대조:

| 규칙 | 요구 | wms 오늘 |
|---|---|---|
| M1 | 모든 영속 row 에 `tenant_id` **NOT NULL** | 91 테이블 중 **1개**, 그마저 nullable |
| M2 | 3-layer isolation | outbound 1개 서비스에만 (`CallerScope`) |
| M3 | cross-tenant read 는 **404**, 403 아님 | **403** (`TenantScopeDeniedException` — `ADR-MONO-064` 가 byte-unchanged 로 유지) |
| M6 | cross-tenant leak 회귀 테스트 필수 | outbound 만 (`OrderJpaRepositoryFilterIT`) |
| M7 | per-tenant rate limit / quota | 없음 |

⇒ **B2(재분류)는 라벨 변경이 아니다.** 그리고 M3 은 `ADR-MONO-064` 가 *"그 술어는 옳았다"* 며
명시적으로 유지한 행동을 뒤집으라는 요구가 된다.

### M7 — 🔵 erp 는 같은 모양에서 **반대로** 결정했다. 갈리는 지점은 하나다

erp `ADR-001` 은 *"관문 있는 하나만 굶고 관문 없는 형제 5개가 매일 불변식을 깬다"* 는 **동일한
관측**에서 출발해 **D**(관문을 낮추고 봉투는 사실을 싣는다)를 골랐다. 근거는 그 ADR 의 재측정 ①:

> *"'데이터가 다중 테넌트' 가 아니다 — **단일인데 이름이 다르다**"* ⇒ 격리할 두 번째 테넌트가
> **0개**인데 격리 기계를 짓지 않는다.

wms 는 그 전제가 **반대**다:

- wms-entitled 테넌트 **5개**(고객 4 + 네이티브 1) — AC-0 실측
- ecommerce 풀필먼트 경로가 **테넌트별 주문을 실제로 만든다**(`FulfillmentRequestedConsumer` 가
  봉투의 `tenantId` 를 주문에 싣는다) — 그리고 `ADR-MONO-030` 이 ecommerce 를 멀티벤더 SaaS 로
  승격했으므로 **서로 다른 고객 테넌트의 주문이 같은 `outbound_order` 에 공존하는 것이 설계**다

🔴 **그러나 정직하게**: 오늘 `admin_order_summary` 는 **1행**이고 그 행의 테넌트도 하나다. 즉
누출은 **구조적으로 확정**돼 있으나(테넌트 컬럼이 없으므로 어떤 필터도 존재할 수 없다) **아직
실현되지 않았다.** 두 번째 테넌트의 주문을 만들어 cross-tenant 읽기를 라이브로 시연하지는
**않았다** — 이 ADR 은 그 시연 위에 서 있지 않고, `admin_db` 217 컬럼 전수 위에 서 있다.

### M8 — 🔴 `ADR-MONO-064` 가 남긴 계약 드리프트 (이 ADR 의 결정 아님 · 기록)

`ADR-MONO-064` D1 이후 `MANUAL` 주문도 테넌트를 갖는다. 그런데 `outbound-events.md` L63 은 여전히:

- *"Omitted / `null` for B2B (`MANUAL`/`WEBHOOK_ERP`) orders"* → **거짓**. `CancelOrderService:132`
  가 `saved.getTenantId()` 를 그대로 싣는다.
- *"wms does **not** interpret it, filter rows by it, or change any gate"* → **거짓**(D2).
- *"wms stays single-tenant (ADR-MONO-030 §1.1)"* → `ADR-MONO-030` § 1.1 자신도 wms 의
  `tenant_id` 를 *"an **opaque correlation** column (NOT an isolation key … no row filter)"* 로
  적고 있어 **같이 낡았다**.

BE-581 의 PR 은 `outbound-service-api.md`(HTTP)만 정정했고 **이벤트 계약은 손대지 않았다.**
어느 선택지를 고르든 이 세 문장은 정정돼야 한다(§ 라이더 R2).

---

## 선택지

### A — 현 상태를 "전역 뷰" 로 명문화한다

`admin-service` 대시보드는 wms 창고의 전역 뷰이며 테넌트로 나뉘지 않는다고 계약에 적는다.
스키마·코드 변경 0.

- 🔴 **M4 가 전제를 깬다** — 저장소 세 곳이 이미 *반대*(`tenant_id=wms` 만 수용)로 적고 있어,
  A 는 문서화가 아니라 **세 문서를 뒤집는 결정**이다.
- 🔴 뒤집힌 문장의 내용이 *"고객 테넌트 4곳이 서로의 창고 데이터를 8개 표면에서 읽는다"* 이고,
  이것은 `ADR-MONO-019` § D5 가 *"isolation invariants preserved"* 로 전제한 것과 충돌한다(M5).
- 🔵 유일한 장점: 비용 0이고, **6개 표면에 대해서는 사실상 옳다**(M2 — 창고 운영 데이터).

### B1 — 테넌트 소유 데이터에만 축을 넣는다. 나머지는 창고 전역으로 명문화한다 *(신규 — M2 가 열었다)*

- `admin_order_summary` · `admin_shipment_summary` 에 `tenant_id` 추가, 프로젝션이 채우고
  두 컨트롤러가 호출자 테넌트로 필터한다.
- 나머지 **6개 표면은 창고 전역**임을 계약에 명문화한다(M2 — 상류에 테넌트가 없으므로 이것이
  사실이다). `PROJECT.md` 의 `traits` 는 **바꾸지 않는다**.
- 필요한 것: (a) `outbound.order.received.v1` 봉투에 `tenantId` additive 추가 + 계약 개정(M3),
  (b) 마이그레이션 2 테이블, (c) `admin-service` 에 호출자-테넌트 해석기 신설(지금 전 계층 0건),
  (d) cross-tenant 회귀 테스트.
- ✅ **누출의 실체를 정확히 닫는다** — 테넌트가 소유한 데이터는 격리되고, 소유자가 없는 데이터는
  격리한다고 거짓말하지 않는다.
- ✅ `ADR-MONO-064` 의 격리 축(`tenant_id` 하나)을 **형제 표면으로 연장**할 뿐, 새 축을 만들지 않는다.
- 🔴 **기존 프로젝션 행은 테넌트가 없다.** 소급 채움은 원본 조인이 필요하고 원본 자체에
  `tenant_id IS NULL` 행이 있다(`ADR-MONO-064` 가 소급 stamp 를 금지). ⇒ **복구 경로는 볼륨 초기화
  + 재시드**로 064 와 동일하다.
- 🔴 **6개 표면은 여전히 고객 테넌트에게 열린다** — B1 은 *격리*를 정하지 창고 전역 데이터의
  *인가*를 정하지 않는다(§ 라이더 R1).

### B2 — wms 를 `multi-tenant` 로 승격한다

`PROJECT.md` `traits` 에 `multi-tenant` 추가 ⇒ `rules/traits/multi-tenant.md` 가 룰 레이어에 로딩되고
M1~M7 이 전 서비스에 구속력을 갖는다. 3PL 모델(창고 데이터도 고객이 소유)로의 전환.

- ✅ 저장소의 모순이 **한 번에** 정리된다 — `PROJECT.md` · `ADR-MONO-030` § 1.1 · 이벤트 계약이
  전부 같은 말을 하게 된다.
- 🔴 **M6: 당일 위반이 5개 규칙에 걸친다** — 91 테이블 NOT NULL 마이그레이션 · 403→404 전환
  (`ADR-MONO-064` 가 옳다고 명시 유지한 술어를 뒤집는다) · per-tenant quota 신설 · 5개 서비스
  회귀 테스트.
- 🔴 erp `ADR-001` 이 **B(재분류)를 명시 배제**한 근거와 같은 형태의 비용이다. 다만 erp 의
  배제 근거(*"격리할 두 번째 테넌트가 0개"*)는 wms 에 **적용되지 않는다**(M7).
- 🔵 이것이 "올바른 최종 상태" 일 수 있으나, **이 티켓의 크기가 아니다** — 로드맵으로 쪼개야 한다.

### C — 문서가 이미 말하는 대로 코드를 되돌린다 *(신규 — M4 가 열었다)*

`admin-service` 의 입장 게이트에서 `.trustEntitledDomains()` 를 걷어 fixed-slug `tenant_id=wms`
로 되돌린다. 스키마 0 · 마이그레이션 0 · 계약 개정 0(계약이 이미 그렇게 적혀 있다).

- ✅ **누출이 완전히 닫힌다** — 고객 테넌트는 admin-service 에 아예 입장하지 못하므로
  `WMS_VIEWER` 합성도 무의미해진다.
- ✅ 저장소 세 문서와 코드가 **일치**하게 된다(M4).
- 🔴 **콘솔 wms 섹션이 통째로 죽는다** — 콘솔은 assume-tenant 로 `tenant_id=demo-corp` 인 토큰을
  보내고(M1: 8개 표면 전부), 단일 계정 올-도메인 데모의 전제가 바로 그 assume 이다.
  `ADR-MONO-064` 가 A 를 배제한 것과 **같은 이유**로 데모가 깨진다.
- 🔴 `ADR-MONO-019` § D5 / `ADR-MONO-020` D4 / `TASK-MONO-162` 를 **되돌리는** 결정이다 —
  entitlement-trust 는 이 저장소의 5개 도메인 공통 모델이고, wms 만 빠지면 그 모델이 깨진다.

### D — `WMS_VIEWER` 합성을 제거하고 부여 경로로 옮긴다

`SecurityConfig` 의 합성을 걷고 `OperatorRoleDerivation.WMS_OPERATOR_ROLES` 에 `WMS_VIEWER` 를
넣는다. **누가** 보는지를 좁힌다.

- ✅ 스키마 0. entitlement-trust 모델도 유지된다(도메인 입장은 그대로, 역할만 부여로).
- 🔴 **한 테넌트가 다른 테넌트를 보는 구조는 그대로 남는다** — 부여받은 운영자가 여전히 무필터로
  전 테넌트 행을 읽는다. 노출 *주체*만 줄고 *구조*는 안 바뀐다.
- 🔴 실질 효과가 작다 — 지금 `WMS_VIEWER` 를 얻는 신원과 `WMS_OPERATOR` 를 얻는 신원이 데모에서
  같다(둘 다 assume 한 고객 테넌트 운영자).

---

## 추천 (소유자 결정 아님)

**B1 + 라이더 R1 을 함께 답한다** `(분석=Opus 5 / 구현 권장=Opus — 이벤트 계약 + 마이그레이션 + 신규 스코프 해석기)`

1. **M2 가 문제의 실제 모양을 바꿨다.** 누출은 "8개 표면" 이 아니라 **테넌트 소유 데이터를 투영하는
   2개**다. 나머지 6개는 격리할 소유자가 없는 창고 운영 데이터이고, 거기에 축을 넣는 것은
   `PROJECT.md` 가 배제한 3PL 모델링이다.
2. **A 는 M4 로, C 는 데모로, D 는 구조로 각각 막힌다.** B2 는 옳은 방향일 수 있으나 M6 의
   5개 규칙 위반을 이 티켓 안에서 청산할 수 없다.
3. **B1 은 새 축을 만들지 않는다** — `ADR-MONO-064` 가 이미 확정한 `tenant_id` 축을 형제 표면으로
   연장할 뿐이고, 복구 경로(볼륨 초기화 + 재시드)도 064 와 같다.

🔵 **B2 를 배제하자는 것이 아니다.** B1 은 B2 로 가는 길을 막지 않는다(테넌트 소유 데이터에
축이 생기는 것은 어느 쪽이든 필요하다). B2 를 지금 고르면 로드맵으로 쪼개야 하고, 그 첫 단계는
**B1 과 같은 작업**이다.

---

## 라이더 — 어느 선택지를 고르든 함께 답해야 하는 것

### R1 — 창고 전역 6개 표면을 **누가** 읽는가

B1 은 *격리 축*을 정하지 *인가*를 정하지 않는다. 고객 테넌트 `acme-corp` 가 창고의 재고
스냅샷 · 입고 예정 · 조정 이력 · 알림 · 마스터 참조를 읽어도 되는가?

- **R1-a**: 읽어도 된다 — 창고 운영 정보는 입주 고객에게 공개되는 것이 3PL 관행이다(현 상태 유지).
- **R1-b**: 안 된다 — 그 6개는 `WMS_OPERATOR` 이상(부여 역할)으로 올리고 `WMS_VIEWER` 합성은
  테넌트 소유 2개 표면에만 남긴다. 🔴 콘솔 영향 측정 필요(M1: 6개 전부 콘솔이 읽는다).

🔴 **조용히 빠뜨리는 것은 답이 아니다.** B1 만 실행하고 R1 을 안 정하면, 계약에는 *"6개는 창고
전역"* 이라고 적히지만 **누구에게 전역인지**가 여전히 안 적힌 채 남는다 — M4 가 지적한 것과
같은 종류의 공백이 다시 생긴다.

### R2 — M8 의 계약 드리프트는 누가 정정하는가

`outbound-events.md` L63 의 세 문장 + `ADR-MONO-030` § 1.1 facet-d 의 *"opaque correlation …
no row filter"*. **이 ADR 의 결정 사항이 아니라 사실 정정**이며, 이 티켓의 구현 AC 에 싣는 것이
자연스럽다(B1 은 어차피 `order.received.v1` 봉투를 고치므로 같은 파일을 연다).

---

## 결정

*(PROPOSED — 소유자의 정확형 지정 대기.)*
