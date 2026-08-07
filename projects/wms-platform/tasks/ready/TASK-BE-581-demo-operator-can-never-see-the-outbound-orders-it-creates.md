# Task ID

TASK-BE-581

# Title

데모 운영자가 **자기가 만든 출고 주문을 볼 수 없다** — `/wms/outbound` 는 구조적으로 빈다

# Status

ready

# Owner

wms-platform

# Task Tags

- bug
- demo

---

# 배경 — `TASK-MONO-510` 이 발굴 (AC-8). 그리고 **1회차 AC-0 의 결론을 뒤집는다**

MONO-510 1회차는 이렇게 결론지었다:

> **WMS 는 데이터에 테넌트가 거의 없다.** `tenant_id` 컬럼을 가진 테이블은 5개 DB
> 통틀어 `outbound_db.outbound_order` 하나뿐이다 ⇒ "200 + 빈 배열" 위험은
> **구조적으로 해당 없음**.

🔴 **정반대다.** 유일하게 테넌트를 가진 그 테이블이 **정확히 데모 운영자가 스코프
당하는 대상**이다. "하나뿐" 은 위험이 작다는 뜻이 아니라 **위험이 거기 전부 몰려
있다**는 뜻이었다.

## 실측 (2026-08-06)

```
시드가 실제 API 로 생성:  POST /api/v1/outbound/orders  → 201
DB:                       outbound_order  SO-DEMO-0001 | PICKING | tenant_id = NULL
같은 토큰으로 조회:        GET /api/v1/outbound/orders?size=100
                          → 200 {"content":[], "totalElements":0}
토큰:                     tenant_id = "demo-corp"
```

**만든 주체가 만든 것을 못 본다.** 200 이라 엣지·헬스·가드는 전부 초록이다
(`TASK-BE-576` 과 같은 모양 — 그때도 200 이었다).

## 원인 — 설계다. 다만 데모 운영자에게는 닫힌 문이다

`SecurityContextCallerScopeProvider`:

```
tenant_id ∈ { null, "", "wms"(required-tenant-id), "*" }  → CallerScope.unrestricted()
그 외 (예: "demo-corp")                                    → CallerScope.restrictedTo(tenantId)
```

`OrderQueryCommand.withTenantScope` Javadoc(원문):

> *a tenant-scoped caller may only ever see its own ecommerce orders, regardless
> of any client-supplied source* — `source` 를 **`FULFILLMENT_ECOMMERCE` 로 덮는다.**

그리고 🔴 **`withTenantScope` 는 `OrderQueryCommand` 에만 있다** — 생성 경로
(`ReceiveOrderService`)는 `tenant_id` 를 **설정하지 않는다**(전수 확인: `withTenantScope`
호출처는 `CallerScope.apply` 하나, 그 인자는 쿼리 커맨드뿐).

⇒ 조합하면: **테넌트 스코프 호출자는 주문을 만들 수는 있지만(201) 그 주문은
`tenant_id=NULL · source=MANUAL` 이라 자기 조회 조건(`tenant_id=demo-corp` AND
`source=FULFILLMENT_ECOMMERCE`)에 절대 걸리지 않는다.**

`demo-corp` 는 `wms` 도 `*` 도 아니므로 **데모 운영자는 항상 restricted** 다.

## 파급

🔴🔴 **정정의 정정 (2026-08-06, `TASK-BE-582` 구현 중 런타임 실측).**

이 절에는 하루 전 "콘솔은 이 엔드포인트를 쓰지 않는다 — `/dashboard/orders`(admin
프로젝션)를 읽는다" 는 정정이 붙어 있었다. **그 정정이 틀렸다. 아래 원문이 맞다.**

**측정** — BE-582 를 고쳐 `admin_order_summary` 를 1행으로 채운 직후, 같은 세션 쿠키로
콘솔을 부르며 `platform-console-web` 로그를 봤다:

```
console-web 로그  {"msg":"wms_outbound_ok","path":"/orders?page=0&size=20"}
env              WMS_OUTBOUND_BASE_URL=http://wms.local/api/v1/outbound
⇒ 실제 상류       GET /api/v1/outbound/orders     (outbound-service 원시 API)

같은 순간
GET /api/v1/admin/dashboard/orders (운영자 토큰)  totalElements=1  ← 프로젝션은 찼다
GET /api/wms/outbound (콘솔 BFF, 데모 세션)       elements=0       ← 화면은 여전히 빈다
```

즉 **프로젝션이 차도 콘솔 출고 화면은 비고**, 남은 원인은 이 티켓의 테넌트 스코프다.

🔵 **왜 틀렸나 — 두 번**. 처음엔 원시 API 의 "200 + 빈 배열" 을 보고 콘솔도 같은 경로일
것이라 가정했다. 정정할 때는 반대로 `wms-ops/api/wms-inventory-api.ts` 의
`callWmsAdmin('/dashboard/orders')` 를 보고 **그게 배선돼 있다고 가정**했다. 실은 그
함수는 **어떤 라우트도 호출하지 않는 죽은 코드**이고, 콘솔이 쓰는 건 이름이 같은 다른
모듈의 `wms-outbound-ops/api/outbound-api.listOrders` 다. 정적 grep 은 같은 이름의 세
`listOrders` 를 구분해 주지 않는다 — **상류는 런타임 로그로 물어야 한다.**
[[feedback_absence_verdict_from_a_proxy_is_not_a_measurement]] ·
[[feedback_data_nobody_renders_is_the_prior_question]]

- MONO-510 의 AC-2 에서 이 화면은 **시드로는 통과할 수 없다** — BE-582 를 고친 뒤에도
  0 이었다(실측). 남은 원인은 이 티켓 하나다.
- 원시 API 를 채우려면 ecommerce↔wms 풀필먼트 루프(ADR-022)가 살아 있어 `demo-corp`
  소유의 `FULFILLMENT_ECOMMERCE` 주문이 실제로 흘러야 한다.

---

# Goal

데모 운영자가 콘솔 `/wms/outbound` 에서 **비어 있지 않은** 목록을 본다. 또는
"이 화면은 ecommerce 슬라이스가 함께 떠야 찬다" 가 **문서가 아니라 제품 동작으로**
설명된다(빈 목록에 그 이유가 표시된다).

---

# Scope

## In Scope

- 데모 운영자가 wms 출고를 볼 수 있게 하는 방법의 **결정**. 후보:
  - (A) 데모 운영자에게 wms-네이티브 스코프를 준다(= unrestricted). `TASK-BE-576`
    이 ecommerce 에서 택한 방향("데이터가 사는 테넌트를 준다")과 같은 계열
  - (B) 생성 경로가 호출자 테넌트를 `tenant_id` 에 박는다. 🔴 그러면 조회 조건의
    `source=FULFILLMENT_ECOMMERCE` 도 함께 풀어야 하고, 그것은 **격리 규칙 변경**이다
    (`TASK-MONO-304` 가 세운 것) ⇒ **ADR 필요**
  - (C) 데모에서 ecommerce 슬라이스를 함께 띄워 진짜 풀필먼트 주문을 만든다
    (제품 변경 0. 대신 메모리 예산이 늘고 `TASK-MONO-399` AC-2 와 얽힌다)

## Out of Scope

- `tenant_id` 를 `dbexec` 로 손으로 박아 화면만 채우는 것 — **존재할 수 없는 상태**를
  만든다(`source=MANUAL` 인데 테넌트가 붙은 주문). 시드 README 의 "도메인 규칙을
  우회하지 마라" 에 정면으로 걸린다

---

# ✅ AC-0 재측정 (2026-08-07) — 전제 유지, 그리고 **티켓보다 나쁘다**

## DB 실측과 대조한 원소 수 (200 을 근거로 쓰지 않았다)

```
outbound_db.outbound_order          1행   (SO-DEMO-0001 | PICKING | tenant_id = NULL)
GET /api/v1/outbound/orders?size=100
  토큰 tenant_id = demo-corp   →   HTTP 200   totalElements = 0
```

**DB 1행 ↔ API 0원소.** 200 이므로 엣지·헬스·가드는 전부 초록이다(티켓 그대로).

## 인가 규칙 코드도 티켓 인용 그대로다

`SecurityContextCallerScopeProvider:50-56` — `tenantId == null || isBlank ||
requiredTenantId.equals(tenantId) || PLATFORM_WILDCARD.equals(tenantId)` → `unrestricted()`,
그 외 → `restrictedTo(tenantId)`. 변경 없음.

## 🔴🔴 신규 — `unrestricted` 를 주는 테넌트는 **assume 자체가 불가능**하다

티켓은 *"`tenant_id ∈ {null,"",wms,*}` 면 무제한"* 이라고 적어 두고 넘어갔는데,
그 집합에 **도달할 수 있는지**는 재지 않았다. 재 보니:

```
assume wms        → 토큰 발급 실패
assume demo-corp  → 성공 (대조군)
```

⇒ 콘솔이 얻을 수 있는 토큰은 **전부 `restrictedTo`** 다. 즉 이건 "데모 운영자만 못 본다"
가 아니라 **콘솔에서 도달 가능한 어떤 신원도 이 행을 볼 수 없다**는 뜻이다.
`unrestricted` 는 사실상 **Kafka 컨슈머·스케줄러 전용**(`CallerScopeProvider` javadoc 이
그렇게 적고 있다: *"Internal flows with no security context … resolve to unrestricted"*).
AC-1 의 A/B/C 는 이 사실 위에서 다시 읽혀야 한다.

## ⚠️ "생성 201" 은 이 슬라이스에서 재현하지 못했다 — 이유를 적는다

`POST /api/v1/outbound/orders` 가 `422 PARTNER_INVALID_TYPE (not found in read model)`
로 막혔다. outbound 의 파트너/SKU **읽기 모델**은 master-service 이벤트로 채워지는데
이 측정 슬라이스에는 master-service 를 띄우지 않았다(메모리 한계 — wms 7앱 ≈ 5.6GiB).
**제품 사실이 아니라 슬라이스의 한계**이므로 "생성이 깨졌다" 로 적지 않는다.
기존 `SO-DEMO-0001` 행이 그 경로로 만들어진 산물이고 `tenant_id` 가 NULL 이라는 것이
이 AC 가 실제로 필요로 하는 증거다.

🔴 그리고 이 재측정에서 **내 계측기가 두 번 틀렸다** — 페이로드 필드명(`quantity` →
`qtyOrdered`)과 `Idempotency-Key` 누락. 둘 다 400 을 냈고, 그것을 제품 결함으로 적었다면
거짓 보고였다. **수정 후 응답만 제품 사실로 기록했다.**

🔴 세 번째: 처음 기동에서 전건 **401** 이 나왔는데, 이는 내가 compose 를 손으로 부르며
`demo.env` 를 소스하지 않아 `OIDC_ALLOWED_ISSUERS` 가 컨테이너 기본값
(`iam-gateway-service:8080`)으로 굳은 탓이었다(토큰의 `iss` 는 `iam.local`).
**401 은 도메인 판정이 아니다** — "물어보지도 못했다" 이고, 그대로 적었으면 이 티켓에
없는 결함을 하나 더 만들어 낼 뻔했다.

---

# Acceptance Criteria

- [x] **AC-0 (재측정) — 완료 2026-08-07.** DB 1행 ↔ API 0원소(HTTP 200) 대조 ✅ ·
      `tenant_id` NULL ✅ · 인가 규칙 코드 불변 ✅ · 🔴 신규: `unrestricted` 테넌트는
      **assume 불가** ⇒ 콘솔 도달 가능한 신원 전부가 `restrictedTo` ·
      ⚠️ "생성 201" 은 master-service 미기동으로 미재현(사유 기록). 상세는 위 §
- [ ] **AC-1 (선택)** — A/B/C 중 하나를 고르고 근거를 적는다. **B 를 고르면 ADR 이
      선행**한다(`TASK-MONO-304` 의 격리 규칙을 바꾸는 일이므로)
- [ ] **AC-2 (형제 확인)** — 같은 "쓰기는 되는데 읽기에서 안 보인다" 가 wms 의 다른
      엔드포인트에도 있는지 센다. `tenant_id` 를 가진 테이블은 이 하나뿐이므로 범위는
      좁지만, **`CallerScope` 를 쓰는 호출처 전수**로 확인한다. 0건이면 "0건" 이라고 적는다
- [ ] **AC-3 (라이브)** — 콘솔 `/wms/outbound` 에서 브라우저로 목록이 찬다
- [ ] **AC-4 (회귀)** — 테넌트 격리가 **약해지지 않았음**을 테스트로 고정한다.
      다른 테넌트의 주문이 보이면 안 된다 — 이 티켓이 그 반대로 가는 것을 막는다

---

# Related Specs

- `projects/wms-platform/specs/services/outbound-service/architecture.md`
- `TASK-MONO-304` — 테넌트 격리 규칙의 출처
- `TASK-BE-576` — ecommerce 에서 같은 증상을 "운영자에게 데이터가 사는 테넌트를 준다"로 푼 선례

# Related Contracts

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` §"`GET /orders`
  is forced to `tenantId = <caller tenant>`"

# Edge Cases

- unrestricted 로 바꾸면 데모 운영자가 **모든 테넌트의 주문**을 본다. 데모에서는
  무해할 수 있으나 그 성질을 문서화하지 않으면 다음 사람이 격리가 있다고 착각한다
- `required-tenant-id` 기본값이 `wms` 다 — 환경별로 다르면 같은 토큰이 환경마다
  restricted/unrestricted 로 갈린다

# Failure Scenarios

- **`dbexec` 로 `tenant_id` 만 박아 화면을 채움** → `source=MANUAL` 인데 테넌트가 붙은,
  제품이 만들 수 없는 행이 생긴다. 그 위에서 하는 모든 검증이 무효다
- **조회 조건에서 `source` 만 풀고 ADR 없이 머지** → 격리 규칙이 조용히 바뀐다.
  AC-1 이 막는다
- **"200 이니 통과" 로 판정** → 이 결함이 정확히 그 모양이다. AC-0 이 막는다

# Definition of Done

- [ ] A/B/C 결정 + 근거(B 면 ADR ACCEPTED 선행)
- [ ] 콘솔 목록 브라우저 증거
- [ ] 격리 회귀 테스트
- [ ] Ready for review
