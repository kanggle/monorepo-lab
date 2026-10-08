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

# Scope

## In Scope

- **AC-0 = 소유자 결정**: 풀필먼트 출고 주문의 테넌트.
  - ⓐ 창고 운영사(demo-corp)로 — wms 소비자가 창고/거래처의 테넌트로 적는다. 콘솔·scm 과 한 축이 된다.
  - ⓑ ecommerce 그대로 두고 콘솔이 볼 수 있게 — 콘솔 wms 프록시가 활성 테넌트로 교환한 토큰을 쓰고, demo@ 에게 ecommerce wms 접근을 준다.
  - ⓒ 데모 시드만 맞춘다 — 운영자를 ecommerce 에도 배정(코드 무변경, 데모 한정).
- 결정된 갈래의 구현 + 시험 + 계약 행.

## Out of Scope

- scm 가시성 노드 이름이 비어 콘솔에 UUID 로만 보이는 것(`inventory_nodes.name` 공백, `warehouse_code=WH-MAIN` 은 있음) — 작은 표시 개선, 결정 뒤 같은 축이면 함께, 아니면 별도.

# Acceptance Criteria

- [ ] **AC-0** — 위 ⓐⓑⓒ 중 소유자 결정(정확형 기록). 각 갈래의 영향 표(바뀌는 서비스 · 계약 · 기존 데이터).
- [ ] **AC-1** — 결정 갈래 구현 · 단위/IT.
- [ ] **AC-2** — ⚪ 재굽기 창: 스토어 주문 1건이 콘솔 WMS 출고 목록에 보인다 → `TASK-MONO-765` AC-4 의 WMS 칸을 닫는다.

# Related Specs

- `projects/wms-platform/specs/services/outbound-service/` · `projects/platform-console/specs/` § wms 연동 · `rules/` 멀티테넌시 규칙

# Related Contracts

- wms `outbound-service-api.md` § 목록(테넌트 범위) · ecommerce `fulfillment.requested` 이벤트

# Edge Cases

- 이미 `tenant_id=ecommerce` 로 쌓인 출고 행 — ⓐ 이면 이전 데이터 질문이 먼저다(마이그 vs 데모 재시드).

# Failure Scenarios

1. 콘솔에서 테넌트 필터를 클라이언트 파라미터로 열어 준다 — `TASK-MONO-304` 가 막은 교차 테넌트 읽기를 되살린다.
2. scm 쪽만 맞추고 wms 를 안 맞춘다 — 축이 다시 갈라진다.
