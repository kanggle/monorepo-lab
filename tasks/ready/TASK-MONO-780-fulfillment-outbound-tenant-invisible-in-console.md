# Task ID

TASK-MONO-780

# Title

스토어 주문의 wms 출고 주문은 `tenant_id=ecommerce` 인데 콘솔 WMS 화면은 로그인 토큰(demo-corp)으로만 조회한다 — **풀필먼트 출고가 콘솔에 영영 안 보인다** (소유자 결정)

# Status

ready

# Owner

monorepo

# Task Tags

- wms
- ecommerce
- platform-console
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — «풀필먼트 출고는 어느 테넌트 소속인가» 는 멀티테넌시 결정이다. 고칠 자리가 셋(wms 소비자 · 콘솔 wms 프록시 · 데모 시드) 중 어디인지가 결정의 결과다.

---

# Dependency Markers

- 출처: 24차 데모 창(2026-10-08 UTC) — `TASK-MONO-765` AC-4 의 WMS 콘솔 칸이 이 공백으로 닫히지 않는다(765 는 `review/` 에 남아 이 티켓을 가리킨다).
- 관련: `TASK-MONO-304`(wms 교차 테넌트 범위를 서명된 JWT 에서) · `TASK-BE-576`(콘솔 테넌트 축 — 같은 URL 이 테넌트에 따라 0/8) · `TASK-MONO-760`(scm 가시성 노드는 demo-corp).

# Goal

24차 창 실측:

| 사실 | 값 | 출처 |
|---|---|---|
| 스토어 주문 → wms 출고 주문 | `source=FULFILLMENT_ECOMMERCE` · **`tenant_id=ecommerce`** · 4건(시드 3 + 소유자 주문 1, PICKING) | `outbound_db.outbound_order` |
| 시드 수동 출고 | `SO-DEMO-0001` · `tenant_id=demo-corp` | 같은 표 |
| 콘솔 WMS 출고 목록 | demo@demo.com 로 **1건(SO-DEMO-0001)만** — 콘솔 상단 테넌트를 ecommerce 로 바꿔도 같다(소유자 브라우저) | |
| 왜 바꿔도 같은가 | 콘솔 wms 프록시는 **IAM OIDC 토큰을 그대로** 붙인다(테넌트 교환 토큰 아님) | `console-web/src/app/api/wms/_proxy.ts:10-13` |
| wms 가 거르는 값 | 클라이언트 필터가 아니라 **서명된 JWT 의 tenant** | `OrderQueryController.java:88-90` · `OrderJpaRepository.java:35` |
| scm 재고 가시성 WH-MAIN 노드 | `tenant_id=demo-corp` | `scm_inventory_visibility.inventory_nodes` |

⇒ 같은 창고(WH-MAIN)의 같은 흐름이 wms 출고는 ecommerce, scm 노드는 demo-corp 에 있다. 콘솔 운영자는 출고를 볼 길이 없다.

## 🔵 소유자 결정 (2026-10-08 UTC) — ⓑ, 그리고 그것은 **이미 ADR 이 정한 것**이다

> OWNER DECISION: «추천대로 진행» — 갈래 ⓑ(콘솔이 활성 테넌트의 토큰으로 wms 를 조회). 근거는 아래 ADR 대조.

기안 때 ⓐⓑⓒ 를 같은 무게의 선택지로 적었는데, 재측정해 보니 **`ADR-MONO-022` D9 (TASK-MONO-304) 가 이미 답을 갖고 있었다**:

| ADR-022 D9 가 정한 것 | 지금 코드 | 일치 |
|---|---|---|
| 풀필먼트 출고의 `tenant_id` = **주문을 낸 고객 테넌트**(facet d — 이벤트 봉투의 tenant) | `FulfillmentRequestedConsumer.java:113,170` `envelope.tenantId()` → `ReceiveOrderCommand` | ✅ |
| 고객 테넌트 운영자는 **서명된 `tenant_id`** 로 자기 테넌트의 `FULFILLMENT_ECOMMERCE` 주문만 본다 · wms 본래 운영자(`tenant_id=wms`)는 전부 | `SecurityContextCallerScopeProvider.java:68-87`(`wms.oauth2.required-tenant-id`, 기본 `wms` · 데모 `docker-compose.e2e.yml:64` 도 `wms`) | ✅ |
| «**콘솔은 이미 assume 한 테넌트 토큰을 넘긴다** — no console change» | 콘솔 wms 프록시는 **로그인 IAM OIDC 토큰**을 붙인다(`_proxy.ts:9-13` «NOT the GAP exchanged operator token — the wms gateway requires the IAM OIDC token») | ❌ **어긋난 곳은 여기 하나** |

⇒ 갈래 재평가:
- **ⓐ 창고 운영사 테넌트로 저장** — D9 의 격리 키를 뒤집는다(ecommerce 운영자가 자기 주문을 못 보게 된다). ADR 개정 없이는 불가 → **기각**.
- **ⓒ 데모에서만 운영자를 ecommerce 에 배정** — 단독으로는 **효과 없음**: 콘솔이 여전히 demo-corp 로그인 토큰을 보낸다. ⓑ 의 데모 측 보조(배정)로만 의미가 있다.
- **ⓑ 콘솔이 활성 테넌트 토큰으로 wms 조회** — D9 가 전제한 동작을 코드가 따라가게 한다. 데이터 모델·wms 코드 무변경이 목표.

# Scope

## In Scope

- **AC-0 = 재측정(구현 위치 판정)** — 🔴 프록시 주석은 «wms 게이트웨이는 IAM OIDC 토큰을 요구한다» 고 한다. 다음을 잰다:
  1. 콘솔 테넌트 전환(`POST /api/tenant`) 뒤 쿠키에 있는 토큰 중 무엇이 `tenant_id=<활성 테넌트>` 를 싣는가(IAM assume-tenant 교환 토큰인가, GAP 교환 토큰인가) — 발급자(`iss`) · `aud` · `tenant_id` · `entitled_domains` 표.
  2. wms 게이트웨이가 그 토큰을 받는가(발급자 · audience 검증 file:line). 받으면 구현 = 콘솔 `wms-api.ts` 의 토큰 선택만. 안 받으면 구현 위치가 게이트웨이(허용 발급자/audience) 쪽으로 바뀐다 — 그 경우 보안 영향 표를 먼저 쓰고 소유자에게 다시 묻는다.
  3. demo@ 의 ecommerce 테넌트 토큰이 `entitled_domains` 에 `wms` 를 싣는가(D9 의 dual-accept 전제). 안 실으면 데모 측 구독/배정(ⓒ 보조)이 필요하다.
- 판정된 위치의 구현 + 시험(콘솔: 활성 테넌트 토큰이 wms 호출에 실린다 · 활성 테넌트 없음 = 지금 동작 / wms: D9 시험이 이미 있으면 재사용) + 계약 행(`console-integration-contract` § 2.4.5 의 토큰 문장 정정).

## Out of Scope

- scm 가시성 노드 이름이 비어 콘솔에 UUID 로만 보이는 것(`inventory_nodes.name` 공백, `warehouse_code=WH-MAIN` 은 있음) — 작은 표시 개선, 결정 뒤 같은 축이면 함께, 아니면 별도.

# Acceptance Criteria

- [ ] **AC-0** — 위 In Scope 의 재측정 1·2·3 표 + 구현 위치 판정(콘솔만 / 게이트웨이까지). 게이트웨이까지면 보안 영향 표를 쓰고 소유자 재확인 뒤 AC-1 로. (갈래 결정 자체는 위 «소유자 결정» 절에 기록됨 — ⓑ.)
- [ ] **AC-1** — 판정 위치 구현 · 단위/IT · bite(토큰 선택을 로그인 토큰으로 되돌리면 시험 빨강).
- [ ] **AC-2** — ⚪ 재굽기 창: 스토어 주문 1건이 콘솔 WMS 출고 목록에 보인다 → `TASK-MONO-765` AC-4 의 WMS 칸을 닫는다.

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` § D9 (+ facet d)
- `projects/wms-platform/specs/services/outbound-service/` · `projects/platform-console/specs/` § wms 연동 · `rules/` 멀티테넌시 규칙

# Related Contracts

- wms `outbound-service-api.md` § 목록(테넌트 범위) · ecommerce `fulfillment.requested` 이벤트

# Edge Cases

- demo-corp 로 돌아오면 wms 화면은 지금처럼 demo-corp 출고(`SO-DEMO-0001`)를 보여야 한다 — 활성 테넌트를 따라가는 것이지 «ecommerce 고정» 이 아니다.
- 활성 테넌트가 wms 구독이 없는 테넌트 — 게이트웨이의 dual-accept 가 거절(403)하면 콘솔은 «이 테넌트는 WMS 를 구독하지 않습니다» 계열로(지금 403 매핑 재사용).
- scm 노드(demo-corp)와 wms 풀필먼트 출고(ecommerce)의 테넌트가 다른 것은 **D9 상 정상**이다(창고 재고 vs 고객 주문). 데모 동선 문구로만 안내한다.

# Failure Scenarios

1. 콘솔에서 테넌트 필터를 클라이언트 파라미터로 열어 준다 — `TASK-MONO-304` 가 막은 교차 테넌트 읽기를 되살린다.
2. 게이트웨이를 «아무 발급자나» 받게 넓힌다 — 프록시 주석이 지키던 불변식(#569)을 확인 없이 깬다. AC-0 의 2 가 «안 받는다» 면 멈추고 묻는다.
3. ⓐ 로 우회한다 — ADR-022 D9 의 격리 키를 조용히 뒤집는다.
