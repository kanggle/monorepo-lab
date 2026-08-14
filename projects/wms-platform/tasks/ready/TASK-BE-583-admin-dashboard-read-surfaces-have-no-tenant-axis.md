# Task ID

TASK-BE-583

# Title

`admin-service` 대시보드 읽기 표면 6개에 **테넌트 축이 없다** — 원시 API 의 격리를 형제가 우회한다

# Status

ready

# Owner

wms-platform

# Task Tags

- security
- read-model

---

# 배경 — `ADR-MONO-064` § M5 가 발굴, § D4 가 여기로 분리

`TASK-BE-581` 을 재던 중, `TASK-MONO-304` 가 세운 테넌트 격리가 **정확히 한 표면**
(outbound 원시 API)에만 있고 **같은 데이터를 내주는 형제 표면에는 전혀 없다**는 것이
실측됐다. ADR 은 이 사실을 B 를 고르는 **근거**로 썼고(격리의 비용을 전부 치르면서
편익이 0 이었다), 고치는 것은 **§ D4 로 이 티켓에 분리**했다.

## 실측 (2026-08-13, 손대지 않은 데모 볼륨)

```java
// admin-service — OrderDashboardController
@RequestMapping("/api/v1/admin/dashboard/orders")
@PreAuthorize("hasRole('WMS_VIEWER')")     // ← 이게 전부. CallerScope 도 테넌트 필터도 없음
```

```
admin_db  information_schema 전수  22개 테이블  ⇒  tenant 컬럼 0개 (구조적으로 테넌트를 모른다)

같은 운영자 토큰(tenant_id=demo-corp):
  GET /api/v1/admin/dashboard/orders   →  totalElements = 1     ← 보인다
  GET /api/v1/outbound/orders          →  totalElements = 0     ← 안 보인다 (BE-581)
```

⇒ 관문 있는 표면은 굶고, **관문 없는 형제가 같은 행을 무필터로 내준다.**

🔵 erp 가 같은 모양을 먼저 겪었다 —
[`projects/erp-platform/docs/adr/ADR-001`](../../../erp-platform/docs/adr/ADR-001-erp-event-plane-tenant-axis.md) § 3:
*"불변식은 관문 없는 형제 5개가 16행으로 매일 깨고 있었고, 관문이 있는 하나만 굶었다."*

## 🔴 이 티켓은 "격리를 넣는다" 가 아니다 — **먼저 무엇이 맞는지 정해야 한다**

두 진술이 저장소 안에서 충돌한다:

| 출처 | 진술 |
|---|---|
| `PROJECT.md` § Out of Scope | **multi-tenant 는 의도적 제외** — *"단일 물류 센터 가정. 멀티 테넌트 확장 시 trait 재분류"* |
| `TASK-MONO-304` / `ADR-MONO-022` § D9 | outbound 주문은 테넌트로 격리된다 |

`ADR-MONO-064` 는 outbound 축에 대해서만 후자를 택했다. admin 프로젝션이 **의도적으로
전역 뷰**(wms 네이티브 운영자용)라면 그것을 명문화하는 것이 답이고, 아니라면 스키마
마이그레이션이 필요하다. **어느 쪽인지가 AC-1 이다.**

---

# Goal

`admin-service` 의 읽기 표면이 테넌트에 대해 **무엇을 약속하는지**가 결정되고, 그 약속이
코드와 계약 문서에서 일치한다. 전역 뷰라면 그렇게 명문화되고, 아니라면 격리가 들어간다.

---

# Scope

## In Scope

- **AC-0 실측**: dashboard 컨트롤러 전수(6개로 추정 — Order / Shipment / Throughput /
  Asn / Inventory / Alert)와 각각의 인가 술어. `admin_db` 테넌트 컬럼 재확인.
  **어떤 신원이 `WMS_VIEWER` 를 얻는지**도 함께(`OperatorRoleDerivation`) — 지금 실제로
  누가 이 표면에 닿는가가 위험의 크기다
- **AC-1 결정**: (A) 전역 뷰로 명문화 / (B) 테넌트 축 신설(스키마 + 프로젝션 + 필터).
  B 는 `ADR-MONO-064` 를 확장하는 결정이므로 **ADR 선행**
- 결정에 따른 구현 + 계약 문서(`specs/contracts/http/admin-service-api.md`) 갱신

## Out of Scope

- outbound 원시 API — `ADR-MONO-064` 가 이미 닫았다
- `admin-service` 의 쓰기 표면(User / Role / Setting) — 읽기 축만 본다

---

# ✅ AC-0 실측 (2026-08-13) — 전제 유지. 그리고 **누출 경로가 티켓이 적은 것과 다르다**

## ① 컨트롤러 전수 — **8개**다 (티켓의 "6개로 추정" 은 틀렸다)

`admin-service` 컨트롤러 **13개** 중 `/api/v1/admin/dashboard/**` 읽기 표면은 **8개**:

| 컨트롤러 | 경로 | 인가 |
|---|---|---|
| `AdjustmentAuditController` | `…/dashboard/adjustments` | `hasRole('WMS_VIEWER')` |
| `AlertDashboardController` | `…/dashboard/alerts` | `hasRole('WMS_VIEWER')` |
| `AsnDashboardController` | `…/dashboard/asns` | `hasRole('WMS_VIEWER')` |
| `InventoryDashboardController` | `…/dashboard/inventory` | `hasRole('WMS_VIEWER')` |
| `MasterRefController` | `…/dashboard/refs` | `hasRole('WMS_VIEWER')` |
| `OrderDashboardController` | `…/dashboard/orders` | `hasRole('WMS_VIEWER')` |
| `ShipmentDashboardController` | `…/dashboard/shipments` | `hasRole('WMS_VIEWER')` |
| `ThroughputDashboardController` | `…/dashboard/throughput` | `hasRole('WMS_VIEWER')` |

나머지 5개는 이 티켓 밖: `Assignment`/`Role`/`User` = `hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')`,
`Operations` = `hasRole('WMS_ADMIN')`, `Settings` = 컨트롤러엔 술어 없음(아래 ③).

## ② 🔴 테넌트 처리가 **어느 계층에도 없다** — 컨트롤러만 본 게 아니다

`admin-service` `src/main/java` 전체에서 `tenant` 를 grep 하면 **18건이고 전부 보안 설정
2파일**(`SecurityConfig`, `OAuth2ResourceServerConfig`)이다 — 즉 *토큰을 들일지 말지*만 정하고,
**컨트롤러·서비스·리포지토리·프로젝션에는 0건**이다.

```
tenant grep (main 전체)   18건  ← 전부 SecurityConfig / OAuth2ResourceServerConfig
대조군 repository grep    389건 ← 탐지기가 전 트리를 훑는다는 증거
admin_db 테넌트 컬럼      0개   (대조군: 22 테이블 · 217 컬럼을 스캔했다)
```

## ③ 🔵 `SettingsController` 의 "인가 없음" 은 **내 오탐이었다**

컨트롤러에 `@PreAuthorize` 가 없어 `PUT /{key}` 가 열려 있는 줄 알았다. 실제로는
`SettingsService.upsert` 에 `@PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")` 가
있고 `SettingsServiceAuthzTest` 가 OPERATOR 거부까지 단언한다. **인가는 서비스 계층에 있다**
— 이 저장소가 명시한 패턴이다. 🔴 컨트롤러 애너테이션은 "인가되는가" 의 **대리지표**였고,
그래서 ② 도 컨트롤러가 아니라 전 계층으로 다시 쟀다.

## ④ 🔴🔴 진짜 누출 경로 — `WMS_VIEWER` 는 **entitlement 만으로 합성**된다

```java
// admin-service SecurityConfig — JwtGrantedAuthoritiesConverter
if (TenantClaimValidator.isEntitled(jwt, ENTITLEMENT_DOMAIN)) {   // entitled_domains ∋ "wms"
    authorities.add(new SimpleGrantedAuthority(VIEWER_ROLE));     // ROLE_WMS_VIEWER
}
```

⇒ `WMS_VIEWER` 보유자는 *"그 역할을 부여받은 운영자"* 가 아니라 **wms-entitled 테넌트의 모든
토큰**이다. 실측 모집단(`tenant_domain_subscription` where `domain_key='wms'`):

```
acme-corp · demo-corp · ecommerce · initech-corp · wms      (5개 — 고객 테넌트 4 + 네이티브 1)
```

그리고 `OperatorRoleDerivation` 의 `WMS_OPERATOR_ROLES` 에는 **`WMS_VIEWER` 가 없다**
(`WMS_OPERATOR` · `OUTBOUND_READ/WRITE` · `INBOUND_READ/WRITE` · `INVENTORY_READ/WRITE` ·
`MASTER_READ` 뿐) ⇒ **VIEWER 로 가는 유일한 경로가 이 합성**이다.

⇒ **고객 테넌트 4곳이 서로의 창고 데이터를 8개 대시보드에서 전부 읽는다.**

## ⑤ 노출은 **실재하되 현재 부피가 작다** — 그리고 `ADR-MONO-064` 가 이것을 키웠다

```
admin_order_summary 1 · admin_asn_summary 1 · admin_inventory_snapshot 1 ·
admin_throughput_inbound_daily 1 · 나머지 4개 0행
outbound_order:  SO-DEMO-0001 <NULL> · SO-AC3-181910 demo-corp
```

기계는 완전히 살아 있고 데이터만 적다. 🔴 그리고 정직하게 적는다 — 누출 자체는
`FULFILLMENT_ECOMMERCE` 주문에 대해 **이전부터 있었고**(그 주문들은 원래 테넌트를 지녔다),
`ADR-MONO-064` § D1 이 `MANUAL` 주문까지 테넌트를 갖게 하면서 **대상 집합이 넓어졌다.**
D1 이 결함을 만든 것은 아니지만, D1 이후 이 티켓은 더 미룰 수 없다.

---

# Acceptance Criteria

- [x] **AC-0 (실측) — 완료 2026-08-13.** 컨트롤러 8개(추정 6 → 실측 8) · 전 계층 tenant 0건
      (대조군 389) · `admin_db` 0/217 컬럼 · 🔴🔴 신규: `WMS_VIEWER` 는 **entitlement 합성**이
      유일 경로이고 모집단이 **고객 테넌트 4곳**이다 · 🔵 `SettingsController` 인가 부재는
      **내 오탐**(서비스 계층에 있음). 상세는 위 §
- [x] **AC-1 (결정) — 완료 2026-08-14. `ADR-MONO-065` ACCEPTED — `B1` + `R1=a`.**
      소유자가 두 번 지정했다: ① 티켓 방향 **`B`**(축을 넣는다) ② ADR 정확형 **`B1`**(소유 데이터
      2개 테이블에만 축, `traits` 불변) + **`R1=a`**(창고 전역 6개는 wms-entitled 테넌트 전체에
      공개). 결정 본문 D1~D5 는
      [`ADR-MONO-065` § 결정](../../../../docs/adr/ADR-MONO-065-wms-admin-read-plane-tenant-axis.md)
      이며, 이 ACCEPT 가 인가하는 것은 **AC-2 착수뿐**이다(HARDSTOP-09 해제 — 계약의 *내용*은
      승인되지 않았다).
      🔵 **A · B2 · C · D 는 배제**됐다. B2(재분류)는 **영구 배제가 아니며**, 창고 운영 데이터에
      소유자가 생기는 시점(3PL 전환)에 다시 열린다.

      ① 과 ② 사이에 ADR 을 연 이유(실측이 B 안에서 새 분기를 열었다):
      **(M2)** 8개 중 테넌트 소유 데이터를 투영하는 표면은 **2개**뿐이고 나머지 6개는 상류에
      테넌트가 **존재한 적이 없다**(창고 운영 데이터) ⇒ 아래 AC-2 가 적은 *"마이그레이션 +
      프로젝션 + 필터"* 의 대상이 8이 아니라 2다 ·
      **(M3)** 그 2개조차 **행을 만드는 이벤트**(`outbound.order.received.v1`)에 테넌트가 없어
      **이벤트 계약 변경**이 포함된다 ·
      **(M6)** `multi-tenant` 재분류는 `rules/traits/multi-tenant.md` 의 M1/M3/M7 **당일 위반**이다
      (특히 M3 의 404-over-403 은 `ADR-MONO-064` 가 옳다며 byte-unchanged 로 유지한 술어를 뒤집는다).
      ⇒ **`B1`** 과 **`B2`** 중 정확형 지정을 다시 받았고, **`B1`** 이 왔다. 🔵 그리고 ADR 의
      **M4** 가 A 의 전제를 한 번
      더 깼다 — 저장소 문서 **세 곳**(`admin-service-api.md` L33 · console `wms-client.ts` ·
      console-integration-contract § 2.4.5)이 *"admin-service 는 `tenant_id=wms` 를 enforce 한다"* 고
      적고 있어, A 는 명문화가 아니라 **세 문서를 뒤집는 결정**이다.
      🔵 M1 은 아래 원문의 *"콘솔이 전부 막힌다"* 를 **확증**했다(콘솔은 base URL + 상대경로 2단계로
      조립하므로 리터럴 grep 이 2건만 잡는다 — 올바른 술어로 **8/8**).

      원문(2026-08-13 판정) — A/B 중 하나 + 근거. **B 면 ADR ACCEPTED 선행**.
      🔴 **AC-0 이 A 의 전제를 깼다** — A("전역 뷰로 명문화")는 보유자가 *wms 네이티브
      운영자*일 때만 정직하다. 실측 보유자는 **고객 테넌트 4곳**이므로, 지금 상태를 그대로
      명문화하면 *"고객사가 서로의 창고 데이터를 본다"* 를 제품 사양으로 적는 것이 된다.
      ⇒ A 를 고르려면 **합성 범위를 함께 좁혀야** 하고, 그러면 `WMS_VIEWER` 를 주는 경로가
      저장소에 하나도 남지 않아(④) **콘솔 wms 대시보드가 전부 막힌다**. 즉 A 도 무비용이 아니다
- [ ] **AC-2 (구현) — `ADR-MONO-065` D1~D4 대로.** 순서가 구속력을 갖는다(계약 먼저):
      1. **이벤트 계약 개정(D2)** — `specs/contracts/events/outbound-events.md` 에
         `wms.outbound.order.received.v1` 봉투의 `tenantId` 를 additive 로 정의하고,
         *"wms does **not** interpret it, filter rows by it"* 문장을 **철회**한다
      2. **HTTP 계약 개정(D3)** — `specs/contracts/http/admin-service-api.md` 에
         (a) orders/shipments 두 표면은 **호출자 테넌트로 격리**됨을, (b) 나머지 **6개는
         wms-entitled 테넌트 전체에 공개되는 창고 전역 뷰**임을 명문화한다.
         🔴 동시에 L33 의 *"`tenant_id=wms` enforced"* 를 정정한다(M4)
      3. **마이그레이션 2 테이블(D1)** — `admin_order_summary` · `admin_shipment_summary` 에
         `tenant_id` (nullable — 기존 행은 소급 채우지 않는다)
      4. **생산 측(D2)** — `OrderReceivedEvent` 가 주문의 `tenantId` 를 싣고,
         `OutboundProjectionConsumer` 가 봉투 값을 **그대로** 기록한다(조회·추론 금지)
      5. **읽기 측(D1)** — `admin-service` 에 호출자-테넌트 해석기를 두고(지금 전 계층 0건)
         두 컨트롤러가 **JWT 의** 테넌트로 필터한다. 클라이언트 파라미터를 신뢰하지 않는다
      6. **회귀 테스트** — cross-tenant 읽기 차단 단언(대조군 = 다른 테넌트의 행이 실제로
         존재하는 픽스처). 🔴 대조군 없는 필터 테스트는 상수 비교와 구별되지 않는다
         (§ Failure Scenarios 1번)
      🔵 `traits` 는 건드리지 않는다(D4) — `PROJECT.md` · `rules/traits/` 변경 **없음**
- [ ] **AC-4 (계약 드리프트 정정 · `ADR-MONO-065` R2)** — `ADR-MONO-064` D1/D2 이후 거짓이 된
      세 문장을 정정한다: `specs/contracts/events/outbound-events.md` L63 의
      *"Omitted / null for B2B (MANUAL/WEBHOOK_ERP) orders"*(→ `CancelOrderService:132` 가
      `saved.getTenantId()` 를 그대로 싣는다) · *"wms does not interpret it, filter rows by it"*
      (→ D2 가 필터한다) · `docs/adr/ADR-MONO-030` § 1.1 facet-d 의 *"opaque correlation column
      (NOT an isolation key … no row filter)"*. 🔵 **결정이 아니라 사실 정정**이므로 ADR 지정을
      기다리지 않는다 — 다만 AC-2 가 같은 파일을 여므로 함께 내는 것이 자연스럽다
- [x] **AC-3 (형제 전수) — 완료 2026-08-13. 답은 "구조적으로 admin-service 하나".**
      `information_schema` 전수(손으로 안 적음) 6개 DB · **91개 테이블**에서 테넌트 컬럼은
      `outbound_db.outbound_order.tenant_id` **하나**뿐이다. ⇒ 다른 서비스(inbound ·
      inventory · master · notification)는 **누출할 테넌트 데이터 자체가 없다**.
      테넌트 축을 가진 서비스는 outbound 하나이고, 그것을 지키는 `CallerScope` 도
      outbound 에만 있으며, `admin-service` 는 그 데이터를 **축 없이 프로젝션**한다.
      🔵 즉 "0건" 이 아니라 **"모집단이 1이고 그 1이 이 티켓"** 이다

---

# Related Specs

- [`docs/adr/ADR-MONO-064`](../../../../docs/adr/ADR-MONO-064-wms-outbound-tenant-visibility-plane.md) § M5 · § D4 — 이 티켓의 출처
- [`docs/adr/ADR-MONO-065`](../../../../docs/adr/ADR-MONO-065-wms-admin-read-plane-tenant-axis.md) — **AC-1 의 결정 문서. ACCEPTED — `B1` + `R1=a`.** 실측 M1~M8 · 선택지 A/B1/B2/C/D · 결정 D1~D5
- `projects/wms-platform/PROJECT.md` § Out of Scope (`multi-tenant`)
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.3
- `TASK-MONO-304` — outbound 격리 규칙의 출처

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md`

# Edge Cases

- `admin_order_summary` 는 프로젝션이라 **원본보다 뒤쳐질 수 있다** — 실측 시점에
  원본 `outbound_order.status = PICKING` 인데 프로젝션은 `RECEIVED` 였다. 격리를 넣든
  안 넣든 이 지연은 별개 사안이며, 여기서 고치지 않는다
- B 를 고르면 **기존 프로젝션 행에 테넌트가 없다** — 소급 채움은 원본 조인이 필요하고,
  원본 자체가 `tenant_id IS NULL` 인 행이 있다(`ADR-MONO-064` 가 소급 stamp 를 금지했다).
  ⇒ 마이그레이션은 코드 문제 이전에 **데이터 문제**다

# Failure Scenarios

- **컨트롤러에 필터만 넣고 스키마를 안 바꾼다** → 필터할 컬럼이 없어 상수 비교가 되고,
  가드는 영원히 초록이면서 아무것도 안 지킨다
- **"전역 뷰다" 를 주석에만 적고 계약 문서를 안 고친다** → 다음 사람이 계약을 읽고
  격리가 있다고 착각한다. `ADR-MONO-064` 가 정정해야 했던 것이 정확히 그런 문장이었다
- **AC-0 없이 착수** → dashboard 컨트롤러가 6개라는 것도 추정이다. 세지 않고 시작하면
  낙오가 남는다

# Definition of Done

- [x] AC-0 실측 기록
- [x] A/B 결정 + 근거 (B 면 ADR ACCEPTED 선행) — `ADR-MONO-065` **ACCEPTED — `B1` + `R1=a`**
- [ ] 구현 + 계약 문서 일치
- [ ] `ADR-MONO-064` 가 남긴 이벤트 계약 드리프트 3문장 정정 (AC-4)
- [ ] Ready for review
