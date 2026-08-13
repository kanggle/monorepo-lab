# ADR-MONO-064 — wms 출고 주문의 테넌트 가시성 평면

**Status:** ACCEPTED
**Date:** 2026-08-13
**주관 티켓:** `TASK-BE-581` (AC-1)
**선행:** `TASK-MONO-304` · [`ADR-MONO-022`](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) § D9 (격리 규칙의 출처) · [`ADR-MONO-048`](ADR-MONO-048-shared-reactive-gateway-library.md) § D5 (네 게이트 정책 · wms 만 `*` 를 거부) · [`ADR-MONO-019`](ADR-MONO-019-platform-console-customer-tenant-model.md) § D5/D6 (그 네 게이트의 권위)
**선례:** [`projects/erp-platform/docs/adr/ADR-001`](../../projects/erp-platform/docs/adr/ADR-001-erp-event-plane-tenant-axis.md) — **같은 결함 클래스**를 erp 에서 이미 판정했다

## History

- 2026-08-13 — PROPOSED. 선택지 A~E 제시. 소유자 결정 대기.
- 2026-08-13 — **ACCEPTED — B** (+ R1 = 스코핑 축의 `*` 분기 제거 · R2 = 별도 티켓 분리).
  § 실측(M1~M6) · § 선택지 · § 추천 은 **byte-unchanged** — finalise 이지 re-decide 가 아니다.

  🔴 **게이트가 실제로 물었다.** 직전 턴의 소유자 응답은 **`"추천대로 진행"`** 이었고,
  넘기지 않았다. `B` 라는 글자의 출처가 **이 문서의 § 추천**, 즉 에이전트 선호이므로
  그대로 ACCEPT 하면 규정이 금지하는 *"launders an agent's own preference into an accepted
  decision"* 과 형태가 구별되지 않는다. 선택지를 명시 제시해 다시 물었고 **B · R1-제거 ·
  R2-분리** 가 소유자로부터 지정됐다. (같은 결과가 나왔다는 사실은 판단의 정당성과 무관하다.)

---

## Context

### 증상

데모 운영자가 `POST /api/v1/outbound/orders` 로 만든 주문을 콘솔 `/wms/outbound` 에서
못 본다. 200 이므로 엣지·헬스·가드는 전부 초록이다.

```
outbound_db.outbound_order          1행  (SO-DEMO-0001 | PICKING | MANUAL | tenant_id = NULL)
GET /api/v1/outbound/orders?size=100   토큰 tenant_id=demo-corp  →  200  totalElements = 0
```

### 기계

| 축 | 코드 | `wms.oauth2.required-tenant-id` 를 무엇으로 읽나 |
|---|---|---|
| 입장 | `OAuth2ResourceServerConfig` → `TenantClaimValidator.forTenant(...).trustEntitledDomains()` | **도메인 키** (`entitled_domains ∋ wms` 면 통과) |
| 행 스코핑 | `SecurityContextCallerScopeProvider` → `requiredTenantId.equals(tenantId)` | **테넌트 값** (등호 비교) |

`demo-corp` 는 1번 축을 `entitled_domains` 로 통과하고 2번 축에서 `restrictedTo("demo-corp")`
가 된다. 생성 경로는 `tenant_id` 를 안 박으므로(`OrderController` 는 8-arg 편의 생성자 →
`source="MANUAL"`, `tenantId=null`) 만든 주체의 조회 조건에 자기 주문이 절대 안 걸린다.

> 🔵 erp §7 이 이 결함 클래스를 이미 명명했다: *"이 프로퍼티는 **도메인 키다.** 영속 계층이나
> 이벤트 평면에서 테넌트 **값**으로 읽지 말 것."* wms 의 스코핑 축이 정확히 그렇게 읽고 있다.

---

## 이 ADR 이 서 있는 실측 (2026-08-13, 손대지 않은 데모 볼륨 `wms_postgres-data`)

### M1 — wms 전체에서 테넌트를 지닌 컬럼은 **하나**다

`information_schema` 전수(손으로 적지 않음), 6개 DB **91개 테이블**:

```
admin_db(22) inbound_db(20) inventory_db(13) master_db(10) notification_db(5) outbound_db(21)
⇒ tenant 컬럼:  outbound_order.tenant_id   단 하나
⇒ 그 컬럼의 값 분포:  <NULL> · MANUAL · 1행     (non-null 0건)
```

**wms 의 테넌트 축은 지금까지 단 한 번도 값을 가진 적이 없다.**

### M2 — HTTP 호출자는 **항상 restricted** 다. `unrestricted` 로 가는 문 세 개가 모두 닫혀 있다

| `unrestricted` 분기 | HTTP 도달 가능? | 근거 |
|---|---|---|
| JWT 없음 (Kafka 컨슈머·스케줄러) | — HTTP 아님 | `CallerScopeProvider` javadoc |
| `tenant_id` null / blank | ❌ | 입장에서 `tenant_mismatch` (`WmsTenantGatePolicyTest.missingTenantRejected` / `blankTenantRejected`) |
| `tenant_id = "wms"` | ⚠️ 입장은 통과하나 **그 신원이 없다** | `admin_operators` 6명 중 home tenant `wms` 0명 · `operator_tenant_assignment` 에 `wms` 행 0건 |
| `tenant_id = "*"` | ❌ (오늘) | 아래 M4 |

⇒ 오늘 wms outbound REST 를 부를 수 있는 **모든** 신원은 restricted 이고,
`outbound_order` 의 유일한 행은 `tenant_id=NULL` 이다. 즉 **이 엔드포인트는 지금 아무에게도
아무 주문도 보여주지 않는다.** 데모 운영자 한정 결함이 아니다.

### M3 — 🔴🔴 결함은 "안 보인다" 가 아니라 **"만들고 나면 아무것도 못 한다"** 이다

`CallerScope` 호출처 전수(outbound-service, 8개 지점):

| 지점 | 가드 | restricted 호출자 × `tenant_id=NULL` 주문 |
|---|---|---|
| `ReceiveOrderService` (생성) | **없음** | **201 ✅** |
| `OrderQueryService.list` | `scopeListQuery` | 200 · 0원소 |
| `OrderQueryService.findById` | `requireOrderAccess` | **403** |
| `ConfirmPickingService:92` | `requireOrderAccess` | **403** |
| `PackingService:102 / :184 / :263` | `requireOrderAccess` | **403** ×3 |
| `ConfirmShippingService:104` | `requireOrderAccess` | **403** |
| `CancelOrderService:86` | `requireOrderAccess` | **403** |

정적 추정이 아니다 — **이미 통과 중인 단위 테스트가 이 판정을 핀하고 있다**:
`CallerScopeTest.restricted_requireOrderAccess_deniesNullTenant`
(restricted 호출자 + `orderTenantId == null` → `TenantScopeDeniedException`).

생성만 가드가 없는 이유는 **검사할 기존 주문이 없기 때문**이다. 그래서 데모 운영자는
자기 주문을 만들 수는 있고, 읽지도 · 피킹 확정하지도 · 패킹하지도 · 출하하지도 · **취소하지도**
못한다. 티켓의 Goal("비어 있지 않은 목록")은 결함을 과소평가하고 있다.

> 🔵 이것이 "데모가 `RESERVED` 에서 멈춘다"의 진짜 원인이기도 하다. 시드가
> 피킹 확정을 안 부르는 것으로 기록돼 있으나(`picking_confirmation` 0건 — 실측 재확인),
> **불렀더라도 403 이었다.**

### M4 — 🔴🔴 `*` 분기는 죽은 코드가 아니다. **데이터 우연으로만 닫혀 있다**

`SecurityContextCallerScopeProvider` 는 `tenant_id="*"` 를 `unrestricted()` 로 열고,
`CallerScope` javadoc 은 *"a platform-scope operator (`tenant_id=*`)"* 를 지원 호출자로 적는다.
그런데 wms 입장 게이트는 `allowSuperAdminWildcard()` 를 **의도적으로 호출하지 않는다** —
*"wms is the only platform that rejects the SUPER_ADMIN `*` wildcard (ADR-MONO-048 § D5)"*.

**두 축이 같은 신원을 정반대로 판정한다.** 그리고 `WmsTenantGatePolicyTest` 가 직접 핀하는
대로, `*` 토큰은 `entitled_domains ∋ wms` 를 달면 **입장한다**(와일드카드가 아니라 entitlement 가
연다). 그 토큰은 스코핑 축에서 즉시 `unrestricted` 다 — ADR-048 § D5 가 막으려던 바로 그
신원이 **모든 테넌트의 주문**을 보게 된다.

오늘 안 터지는 이유는 **오직** `tenant_domain_subscription` 에 `*` 행이 없어서다(실측:
18행 전수, `*` 0건) ⇒ `*` 토큰엔 `entitled_domains` 가 안 붙어 입장에서 걸린다.
**설계가 아니라 데이터가 막고 있다.** 구독 행 하나면 조용히 열린다.

> 이 항목은 A~E 어느 선택지와도 독립이다. **어느 쪽을 고르든 별도로 닫아야 한다** (§ 라이더).

### M5 — 🔴🔴🔴 격리 불변식은 **이미 형제 표면이 매일 깨고 있다**

`TASK-MONO-304` 가 세운 "테넌트 스코프 호출자는 자기 테넌트 주문만 본다" 는
**정확히 한 표면**(outbound 원시 API)에만 있다. 같은 주문을 내주는 형제 읽기 표면:

```java
// admin-service — OrderDashboardController
@RequestMapping("/api/v1/admin/dashboard/orders")
@PreAuthorize("hasRole('WMS_VIEWER')")          // ← 테넌트 필터 없음. CallerScope 안 씀
```

`admin_db` 는 **22개 테이블 전부에 테넌트 컬럼이 없다**(M1). 구조적으로 테넌트를 모른다.
그리고 그 표면은 지금 **그 행을 보여주고 있다**:

```
admin_order_summary                                     SO-DEMO-0001  1행
GET /api/v1/admin/dashboard/orders  (운영자 토큰)  →  totalElements = 1   (TASK-BE-581 § 파급 실측)
GET /api/v1/outbound/orders         (같은 토큰)   →  totalElements = 0
```

⇒ **현재 배치는 격리의 비용을 전부 치르고(데모 파손 + 운영자 전면 잠금) 편익은 0 이다.**
관문 있는 표면은 굶고, 관문 없는 형제가 같은 데이터를 무필터로 내준다.

> 🔵 erp §3 와 글자 그대로 같다: *"불변식은 관문 없는 형제 5개가 16행으로 매일 깨고 있었고,
> 관문이 있는 하나만 굶었다."* 이 관측이 "MONO-304 의 규칙을 건드리면 안 된다" 는 전제를
> **무효화**한다 — 그 규칙은 이미 보안 성질로서 공허하다.

### M6 — 부수 관측 (판정에 안 씀, 기록만)

- 시드 `seed-wms.sh:245` 의 존재 확인이 `GET /outbound/orders` + orderNo grep 인데 그 GET 은
  이 결함으로 영원히 0을 낸다 ⇒ **"존재" 빠른 경로는 구조적으로 죽은 분기**이고 매번 POST 로
  떨어진다(409 로 흡수되어 실패로는 안 보인다).
- `outbound_order.status = PICKING` 인데 `admin_order_summary.status = RECEIVED` — 프로젝션이
  한 이벤트 뒤쳐져 있다. **이 티켓 밖**이다(BE-582 계열). 판정 근거로 쓰지 않았다.

---

## 선택지

> A · B · C 는 티켓이 세운 것이고, D · E 는 위 실측이 새로 연 것이다.

### A — 데모 운영자에게 wms 네이티브 스코프를 준다

`operator_tenant_assignment` 에 `(operator 5, 'wms')` 한 행 + 운영자가 `wms` 를 assume.
`TASK-BE-576` 이 ecommerce 에서 택한 계열("데이터가 사는 테넌트를 준다"). 제품 코드 변경 0.

- 🔴 **티켓이 적은 것보다 나쁘다.** assume 은 토큰의 `tenant_id` 를 **치환**하고
  `entitled_domains` 는 **선택된 테넌트의 구독만**이다(`TenantClaimTokenCustomizer`
  D3 least-privilege, union 아님). 테넌트 `wms` 의 구독은 `[wms]` 하나뿐 ⇒ 그 세션은
  **ecommerce · erp · finance · scm 섹션을 전부 잃는다.**
  단일 계정 올-도메인 데모(`demo-corp` 1테넌트 → 콘솔 5섹션)가 깨진다.
- 🔴 M3 의 403 잠금은 **안 풀린다** — 주문의 `tenant_id` 는 여전히 NULL 이고, 운영자가
  `demo-corp` 로 돌아오는 순간 다시 전부 403 이다.

### B — 생성 경로가 호출자 테넌트를 `tenant_id` 에 박는다

restricted 호출자가 만든 주문에 그 테넌트를 stamp. 조회 조건의
`source=FULFILLMENT_ECOMMERCE` pin 은 "자기 테넌트 · 모든 source" 로 완화.

- ✅ M3 의 8개 지점이 **한 번에** 풀린다(자기 주문이므로 `requireOrderAccess` 통과) —
  읽기·피킹·패킹·출하·취소가 전부 데모 운영자에게 열린다.
- ✅ **AC-4 를 만족한다.** 넓히는 것은 *자기 테넌트에 대해 보는 범위*이고, 다른 테넌트의
  주문은 여전히 안 보인다(`acme-corp` 는 `demo-corp` 주문을 못 본다).
- 🔴 `TASK-MONO-304` 의 격리 규칙 변경이다 ⇒ **이 ADR 이 그 승인이다.**
  다만 M5 가 그 규칙이 이미 공허함을 보였으므로 비용이 티켓의 가정보다 작다.
- 🔴 **데이터 문제가 코드 문제보다 앞선다**: 기존 `SO-DEMO-0001` 은 NULL 로 남아 계속
  안 보인다. 소급 stamp 는 금지(제품이 만들 수 없는 행). ⇒ 볼륨 초기화 + 재시드가 복구 경로.

### C — 데모에서 ecommerce 슬라이스를 함께 띄운다

진짜 `FULFILLMENT_ECOMMERCE` 주문이 흘러 `tenant_id=demo-corp` 가 자연히 박힌다. 제품 변경 0.

- 🔴 **Goal 의 절반만 닫는다.** 화면은 차지만 `MANUAL` 주문은 여전히 안 보이고 **M3 의 403
  잠금이 그대로 남는다**(그 주문에 대해선 운영자가 아무것도 못 한다).
- 🔴 메모리 예산이 늘고 `TASK-MONO-399` AC-2 와 얽힌다.

### D — 콘솔을 admin 프로젝션으로 재배선한다 *(신규)*

콘솔 출고 화면이 원시 API 대신 `/api/v1/admin/dashboard/orders` 를 읽게 한다.
데이터는 **이미 거기 있고 이미 보인다**(M5). 백엔드 변경 0, 즉시 화면이 찬다.

- 🔴 **테넌트를 모르는 형제 표면을 제품으로 승격**시킨다. M5 의 누수를 고치는 대신 공식화한다.
- 🔴 M3 의 403 잠금은 그대로 — 목록만 보이고 아무 동작도 못 한다. **표면적 수리**다.
- 🔵 M6 의 프로젝션 지연(`RECEIVED` vs `PICKING`)이 사용자에게 노출된다.

### E — 스코핑 축이 `required-tenant-id` 를 **테넌트 값으로 읽는 것을 중단**한다 *(신규 · erp Option D 유사)*

erp §7 의 처방을 그대로 적용: 이 프로퍼티는 도메인 키이므로 등호 비교에 쓰지 않는다.
wms 는 `PROJECT.md` 가 **multi-tenant 를 명시적 out-of-scope** 로 선언한 프로젝트이고
("단일 물류 센터 가정"), M1 은 그 선언이 데이터에서도 참임을 보인다.

- 🔴 **AC-4 와 정면 충돌한다.** wms-entitled 테넌트가 4개 있고(`ecommerce` · `acme-corp` ·
  `initech-corp` · `demo-corp`), 행 스코핑을 걷으면 이들이 서로의 풀필먼트 주문을 본다.
  AC-4 는 "격리가 약해지지 않았음" 을 요구한다.
- 🔵 M5 를 정직하게 인정하는 유일한 선택지이긴 하다 — 다만 그 정직함의 값이 격리 포기다.
  **M5 를 고치려면 E 가 아니라 형제 표면에 스코핑을 더하는 별도 작업**이 맞다.

---

## 추천 (소유자 결정 아님)

**B** `(분석=Opus 5 / 구현 권장=Opus — 격리 규칙 변경 + 8개 호출처 파급)`

근거 세 줄:

1. **Goal 을 전부 닫는 유일한 선택지다.** A·C·D 는 M3 의 403 잠금을 남긴다.
2. **AC-4 를 만족한다.** 넓히는 축이 "자기 테넌트 안" 이지 "테넌트 사이" 가 아니다.
3. **주된 반론이 실측으로 무너졌다.** "MONO-304 의 격리를 건드리는 값이 크다" 는 전제는
   M5(형제 표면이 이미 무필터) 앞에서 성립하지 않는다.

## 라이더 — 어느 선택지를 고르든 함께 답해야 하는 것

- **R1 (M4)**: `SecurityContextCallerScopeProvider` 의 `*` → `unrestricted` 분기를
  제거할 것인가, 아니면 입장 게이트의 와일드카드 거부를 철회할 것인가?
  **두 축이 같은 신원을 반대로 판정하는 상태를 유지하는 것은 선택지가 아니다.**

  🔵 **새 질문이 아니다 — `ADR-MONO-048` § D5 가 이미 유예해 둔 것이다**:
  > *"Recorded for a future decision, deliberately not taken here: `wms` rejects the `*`
  > SUPER_ADMIN wildcard that `scm`/`fan` accept. … But "can a platform operator reach the
  > wms edge during an incident?" is a real operational question with an inconsistent answer
  > across domains. It is **not** in scope."*

  M4 가 그 유예에 더하는 새 사실은 **wms 내부의 두 번째 축이 그 질문에 이미 "예" 라고
  답하고 있다**는 것이다 — 엣지가 거부하는 신원을 애플리케이션 계층이 무제한으로 대접한다.
  유예는 "아직 안 정했다" 였지, "한쪽이 몰래 정해도 된다" 가 아니었다.
- **R2 (M5)**: `admin-service` 의 무필터 읽기 표면을 이 티켓에서 다룰 것인가,
  아니면 별도 티켓으로 분리할 것인가? (분리를 권장 — 범위가 dashboard 6개 컨트롤러다.)

---

## 결정 — B (ACCEPTED 2026-08-13)

**B 를 binding 으로 고정하고 A · C · D · E 를 배제한다.**

### D1 — 생성 경로가 호출자의 검증된 테넌트를 `tenant_id` 에 stamp 한다

restricted 호출자가 만든 주문에 **그 호출자의 서명된 `tenant_id`** 를 박는다.
클라이언트가 보낸 값이 아니라 `CallerScope` 가 JWT 에서 해석한 값이다(스푸핑 불가).

- unrestricted 호출자(Kafka 컨슈머 · 스케줄러 · 보안 컨텍스트 없음)는 **stamp 하지 않는다** —
  `FulfillmentRequestedConsumer` 가 명시적으로 싣는 ecommerce 테넌트가 그대로 보존된다.
- 명령이 이미 `tenantId` 를 지니면 **덮어쓰지 않는다**(이벤트 평면이 권위다).

### D2 — 목록 조회의 `source` pin 을 걷는다. 테넌트 pin 은 유지한다

`CallerScope.scopeListQuery` 는 `tenantId` 만 고정하고 `source` 는 클라이언트 필터를
그대로 둔다. **격리 축은 `tenant_id` 하나**이며, `source=FULFILLMENT_ECOMMERCE` pin 은
D1 이전에 "테넌트를 가진 주문 = ecommerce 주문" 이었기에 중복이었던 것이고, D1 이후에는
정확히 **이 결정이 보이게 하려는 주문을 배제**한다.

🔴 **`requireOrderAccess` 는 byte-unchanged 다.** 그 술어(테넌트 등호, null → 거부)는
옳았다 — 틀린 것은 주문에 테넌트가 안 박히던 쪽이다.

### D3 (R1) — 스코핑 축에서 `*` → `unrestricted` 분기를 제거한다

`SecurityContextCallerScopeProvider` 는 `PLATFORM_WILDCARD` 를 더 이상 특별 취급하지 않는다.
`CallerScope` · `CallerScopeProvider` javadoc 의 *"a platform-scope operator (`tenant_id=*`)"*
문장도 **회수한다** — wms 입장 게이트가 거부하기로 한 신원을 애플리케이션 계층이 무제한으로
대접하던 모순이 이로써 닫힌다(`ADR-MONO-048` § D5 의 유예는 **거부 쪽으로** 정산됐다).

fail-closed 방향이다: `*` 토큰이 entitlement 로 입장하더라도 이제 `restrictedTo("*")` 이고,
그 문자열을 `tenant_id` 로 가진 주문은 없으므로 아무것도 보지 못한다.

### D4 (R2) — admin-service 의 무필터 읽기 표면은 **별도 티켓**이다

M5 는 이 ADR 의 근거로 쓰였을 뿐 여기서 고치지 않는다. 범위가 dashboard 컨트롤러 6개 +
`admin_db` 스키마(22 테이블 전부 테넌트 컬럼 없음)라 성격이 다르다.
**M5 가 사실이라는 것은 이 ADR 이 확정한다** — 후속 티켓이 이 문서를 근거로 쓸 수 있다.

### 이 결정이 **하지 않는** 것

- `admin-service` 에 테넌트 축을 넣지 않는다 (D4).
- 기존 `tenant_id IS NULL` 행에 **소급 stamp 하지 않는다** — `source=MANUAL` 인데 테넌트가
  붙은, 제품이 만들 수 없는 행이 된다(티켓 § Out of Scope). 기존 데모 볼륨의
  `SO-DEMO-0001` 은 계속 안 보인다. **복구 경로는 볼륨 초기화 + 재시드**다.
- `ADR-MONO-048` § D5 의 입장-게이트 거부를 건드리지 않는다 — D3 은 **애플리케이션 축을
  그 거부에 맞추는** 방향이다. `WmsTenantGatePolicyTest` 의 거절 단언은 그대로 유지된다.
