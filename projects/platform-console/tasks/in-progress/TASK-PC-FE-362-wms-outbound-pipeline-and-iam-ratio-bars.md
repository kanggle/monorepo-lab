# Task ID

TASK-PC-FE-362

# Title

그래프 4차 묶음 — WMS 출고 화면(`/wms/outbound`) 맨 위에 출고 상태 파이프라인 막대(상태별 총 건수) · IAM 개요에 운영자 활성 · 정지 비율과 계정 잠김 비율 막대

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (360 의 상태 분포 막대 · 357 의 `InlineBar` 와 같은 모양 · 서버 변경 없음)
> 기안 2026-10-22 UTC — 콘솔 그래프 남은 후보 중 추천 둘(소유자 «진행», 2026-10-22 KST). 1~3차 = `TASK-PC-FE-357` · `359` · `360` · `361`(done) · `358`(review).
> 🔴 **2026-10-22 KST 소유자 결정으로 WMS 막대의 자리가 «개요» 에서 «출고 화면» 으로 바뀌었다** — 아래 `### 소유자 결정`.

# Dependency Markers

- 선행 없음. 재사용: `shared/ui/InlineBar.tsx` · 360 의 `ShippingStatusBar` / `ApprovalStatusBar` 규칙(칸 하나라도 비ok → 막대 없음 · 전부 0 → 빈 문구).

# Goal

| # | 화면 | 더하는 것 | 데이터 |
|---|---|---|---|
| 1 | WMS 출고 `/wms/outbound` (맨 위) | 출고 주문의 **상태 파이프라인 누적 막대** — 정상 흐름 `PICKING → PICKED → PACKING → PACKED → SHIPPED`(`outbound-ops-helpers.ts` 의 `OUTBOUND_FLOW`, 361 이 `order-status.md` 에서 가져온 순서) + 곁가지(`CANCELLED` · `BACKORDERED`)는 막대 밖 숫자로 | outbound-service `GET /orders?status=<S>&page=0&size=1`.`totalElements` 7번(op 1, § 2.4.5.1). 출고 화면 서버 상태(`getOutboundSectionState`)에서 **페이지 로드당 1회**, 같은 자격 · 역할(`OUTBOUND_READ`) · 테넌트 범위. 목록 필터 · 페이지 이동으로 다시 읽지 않는다. 테넌트 범위(`outbound-service-api.md` § Tenant scoping)는 화면에 한 문장으로 밝힌다 |
| 2 | IAM 개요 `/iam` | 운영자 **활성 · 정지 비율 막대** · 계정 **잠김 비율 막대**(잠김 / 전체) | 이미 받는 `{total, active, suspended}` · `{total, locked}`(`features/iam-overview/api/overview-state.ts`) — 새 호출 0 |

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 출고 주문 읽기(엔드포인트 · `status` 필터 · `totalElements` · 테넌트 범위) · 개요가 `RECEIVED` 를 세는 읽기가 같은 읽기인가 ② 출고 상태 목록 · 순서를 계약에서(`RECEIVED` 는 같은 TX 에서 `PICKING` 으로 넘어가 쉬는 단계가 아니다 — 361 AC-0 ④) ③ IAM 개요의 세 숫자가 전체 모집단인가(대시보드 색인 `/dashboards` 의 `activeCount` 는 **제한된 조각**이었다 — 같은 실수를 하지 않는다) ④ 셀 저하 규칙(403 · 401) ⑤ 샘플 고정값에 상태별 값이 있는가.
- 계약 먼저: `console-integration-contract.md` § 2.4.5.1(출고 화면)과 § 2.4.3.1(IAM 개요)에 새 읽기 · 집계 범위를 적은 뒤 구현. § 2.4.5.2(WMS 개요)는 바꾸지 않는다.
- 구현: 상태별 독립 셀 · 하나라도 비ok → 막대 없음 · 전부 0 → 빈 문구 · 숫자 글자 · 색 견본 · 기존 testid 유지 · 클라이언트 컴포넌트는 서버 전용 모듈을 임포트하지 않는다.
- 시험: 비율 · 하나 저하 · 전부 0 · 403 · 401(세션 전체 재로그인) · 호출 수(출고 = 정상 흐름 5 + 곁가지 2 + 페이지 0 목록 1) · 필터 변경 시 재조회 없음 · IAM 은 새 호출 0.

## Out of Scope

- 서버 · BFF 변경 · 입고(ASN) 상태 분포(필요하면 다음 묶음).
- **WMS 개요(`/wms`, `features/wms-ops/api/overview-state.ts`)와 admin 읽기 모델은 건드리지 않는다.**
- 그 밖의 «효과 낮음» 후보 · ERP 트리맵 · 재주문점 게이지 · 감사 로그(소유자: 추천 둘만).

### 소유자 결정 (2026-10-22 KST)

- **결정**: 출고 상태 파이프라인 막대는 WMS 개요가 아니라 **`/wms/outbound` 화면 맨 위**에 둔다. 개요는 그대로.
- **이유**: 개요가 `RECEIVED` 를 세는 읽기는 admin-service 읽기 모델(`GET /dashboard/orders`)인데, 이 모델의 `status` 는 `RECEIVED` · `SHIPPED` · `CANCELLED` 셋뿐이다(AC-0 ①). `status=PICKING` 등은 항상 0 이라 정상 흐름 5단계를 이 읽기로 그리면 «파이프라인이 비었다» 로 잘못 읽힌다. 진짜 주문 상태 필터가 있는 곳은 outbound-service `GET /orders?status=`(§ 2.4.5.1 op 1)이고, 그 읽기를 이미 쓰는 화면이 출고 화면이다.
- 구현자의 STOP 보고(2026-10-22)가 이 결정을 이끌었다.

### 관찰 (고치지 않음)

- `projects/wms-platform/specs/services/admin-service/domain-model.md:379` 는 OrderSummary `status` 를 «Mirrors Order status» 라 적지만, 코드(`OrderSummaryEntity.java:123/128/133` `applyReceived` · `applyCancelled` · `applyShipped`)는 세 값만 쓴다. 스펙 문구와 구현이 어긋난다 — 이 티켓에서는 고치지 않는다(wms 쪽 별도 정정 후보).

# Acceptance Criteria

- [x] **AC-0** — 위 ①~⑤. (2026-10-22 UTC, 아래 `## AC-0 측정`)
- [ ] **AC-1** — 계약 먼저 → 구현 + **사용자에게 보이는 새 문구 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [ ] **AC-2** — 시험 위 목록 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` · `node scripts/check-client-graph-server-only.mjs` · `check-client-graph-backend-origins.mjs`(스테이지 뒤) rc=0. SSR 이 바뀌므로 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5.1 (WMS 출고 — 새 읽기 7) · § 2.4.3.1 (IAM 개요 — 비율 막대)
- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` § 1.3 · § Tenant scoping (읽기만)
- `projects/wms-platform/specs/services/outbound-service/state-machines/order-status.md` (읽기만)

# Edge Cases

- 고객사 테넌트 운영자는 자기 테넌트 주문만 센다. `tenant_id` 없는 B2B 주문은 보이지 않는다(`outbound-service-api.md` § Tenant scoping) — 화면 문장으로 밝히고, 건수 0 은 빈 문구로(오류처럼 보이지 않게).
- `RECEIVED` 가 남아 있는 주문(같은 TX 전환이 실패한 경우)은 정상 흐름에 넣지 않는다 — 읽지도 않는다(출고 화면 목록에서만 보인다).
- IAM 운영자 0 · 계정 0 → 막대 없음.

# Failure Scenarios

1. 제한된 조각(첫 페이지 · 샘플)을 전체 비율처럼 그린다 → AC-0 ③ · 시험.
2. 출고 상태 하나의 실패가 화면 전체를 저하시킨다 → 셀 독립 시험.
3. 정상 흐름 순서를 손으로 만든다 → 계약 인용 + 시험(`OUTBOUND_FLOW` 순서 고정).
4. 필터를 바꿀 때마다 7개 총계를 다시 읽는다 → 서버 상태 1회 + 화면 시험(호출 수 1).

---

## AC-0 측정 (2026-10-22 UTC)

경로는 `projects/platform-console/apps/console-web/src/` 기준(별도 표기 없으면).

**① 출고 주문 읽기**
- 개요가 `RECEIVED` 를 세는 읽기: `features/wms-ops/api/overview-state.ts:200` `listOrders({ status: 'RECEIVED', page: 0, size: 1 })` → 구현 `features/wms-ops/api/wms-inventory-api.ts:80-91` = `GET /dashboard/orders`(admin-service 읽기 모델). 같은 파일 `:195-199` 주석이 «read model collapses order status to RECEIVED/SHIPPED/CANCELLED (picking/packing events are swallowed)» 라고 적고, 코드도 그렇다: `projects/wms-platform/apps/admin-service/src/main/java/com/wms/admin/readmodel/outbound/OrderSummaryEntity.java:123`(`"RECEIVED"`) · `:128`(`applyCancelled`) · `:133`(`applyShipped`) 외에 status 를 바꾸는 곳이 없다. 샘플도 같다(`shared/sample/fixtures/wms.ts:651-657` `collapseStatus`). ⇒ **개요의 읽기로는 `PICKING`~`PACKED` 를 셀 수 없다(항상 0)** → 소유자 결정으로 출고 화면으로 이동.
- 출고 화면의 읽기: `features/wms-outbound-ops/api/outbound-order-api.ts:32-44` `listOrders({status, size})` → outbound-service `GET /orders?status=…&page=…&size=…`. 필터 enum `PICKING|PICKED|PACKING|PACKED|SHIPPED|CANCELLED|BACKORDERED`(`outbound-service-api.md:306`, `features/wms-outbound-ops/components/outbound-ops-helpers.ts:13-22`). `totalElements` 는 응답 `page.totalElements`(`api/types.ts:48-69` 정규화가 평평한 `total` 도 받는다). 테넌트 필터 뒤 전체: `outbound-service-api.md:52-95` — `tenant_id=wms` 는 무제한, 다른 인증 호출자는 `GET /orders` 가 자기 `tenantId` 로 강제되고(다른 필터는 없다), `tenant_id` 없는 B2B 주문은 보이지 않는다. 콘솔 계약 § 2.4.5(`console-integration-contract.md:585-598`)도 같은 서술.
- 자격: 출고 화면 서버 상태 `features/wms-outbound-ops/api/outbound-state.ts` 가 이미 `listOrders` 를 도메인 대면 IAM OIDC 토큰으로 호출(`callOutbound`) — 새 7읽기도 같은 함수를 지나므로 자격 · 역할 · 테넌트가 같다.

**② 상태 목록과 순서(계약에서)**
- `projects/wms-platform/specs/services/outbound-service/state-machines/order-status.md` § States `:27-36` · § Transitions `:42-91`: 정상 흐름 `RECEIVED →(같은 TX) PICKING → PICKED → PACKING → PACKED → SHIPPED`, `RECEIVED` 는 `:29` «callers never observe an Order resting in RECEIVED». 곁가지 `CANCELLED`(`:35`) · `BACKORDERED`(`:36`). 코드 상수는 361 이 이미 둔 `outbound-ops-helpers.ts:55`(`OUTBOUND_FLOW`) · `:59`(`OUTBOUND_SIDE_BRANCHES`)를 그대로 쓴다. 시험이 순서를 고정한다.

**③ IAM 숫자는 전체 모집단인가**
- `features/iam-overview/api/overview-state.ts:177-183`: 운영자 `listOperators({page:0,size:1})` · `{status:'ACTIVE'}` · `{status:'SUSPENDED'}`, 계정 `searchAccounts({page:0,size:1})` · `{status:'LOCKED'}` — 모두 **상태 필터 `size:1` 의 `totalElements`**(`:187-198`). 첫 페이지 조각이나 `/dashboards` 의 `activeCount` 같은 제한 값이 아니다 ⇒ 비율로 그려도 된다. 계정은 `LOCKED` 만 세므로(`ACTIVE`/`DORMANT`/`DELETED` 따로 읽지 않음) 계정 막대는 «잠금 vs 나머지» 이고 활성/휴면 분할이 아니다 — 계약 § 2.4.3.1 에 명시. 운영자는 활성 · 정지 외 상태(있다면)가 막대 끝의 빈 칸으로 남는다.

**④ 셀 저하 규칙**
- 출고: 7읽기는 셀마다 `countCell`(`outbound-state.ts`)이 잡는다 — `401` 은 그대로 던져 기존 catch 가 `redirect('/login?error=session_expired')`(전체 재로그인), `403` → `forbidden`, 그 밖(`WmsOutboundUnavailableError` 포함) → `degraded`. 한 셀이라도 비ok 면 막대 없음(숫자 «—»). 페이지 0 목록의 403/degrade 는 기존 규칙 그대로(페이지 전체 상태).
- IAM: 새 호출 없음 — 기존 셀 상태(`ok`/`forbidden`/`degraded`)와 `null` 숫자 규칙을 그대로 따라 숫자가 하나라도 `null` 이면 막대 없음.

**⑤ 샘플 고정값**
- 출고: `shared/sample/fixtures/wms.ts:1012-1040` `ordersListFixture` 가 **출고 쪽 `GET /orders` 를 서빙**하고 `status` 필터를 적용(`:1031,1035`), 실제 상태 값(`PICKING` · `PICKED` · `SHIPPED` · `CANCELLED` 각 1건, `:557,580,603,626`)을 쓴다. 개요 쪽 admin 샘플은 `collapseStatus` 로 접힌다(위 ①). ⇒ 샘플 모드에서 7읽기가 각각 0/1 로 합쳐져 `PICKING 1 · PICKED 1 · SHIPPED 1 · CANCELLED 1` 막대가 나온다(`PACKING`/`PACKED`/`BACKORDERED` 0). 코드 읽기로 확인한 것이고 화면 실행은 AC-3 에서.
- IAM: 샘플 운영자/계정에 `SUSPENDED`(`fixtures/iam.ts:296,525,765`) · `LOCKED`(`:101,107`) 행이 있다.

## 새 문구 (소유자 승인 대기)

| file:line | 문구 | 언제 보이나 |
|---|---|---|
| `features/wms-outbound-ops/components/OutboundStatusPipeline.tsx:66` | 출고 상태 | `/wms/outbound` 맨 위, 상태 총계가 서버에서 내려왔을 때 항상(소제목 h2) |
| `features/wms-outbound-ops/components/OutboundStatusPipeline.tsx:71-72` | 건수는 로그인한 운영자의 테넌트 범위 주문만 집계합니다 (wms 운영자는 전체, 고객사 운영자는 자기 회사 주문). | 위 소제목 아래 항상(테넌트 범위 안내 한 문장) |
| `features/wms-outbound-ops/components/OutboundStatusPipeline.tsx:79` | 출고 주문이 없습니다. | 일곱 상태가 모두 `ok` 이고 모두 0 일 때(막대 대신) |

> 상태 이름(`PICKING` · `PICKED` · `PACKING` · `PACKED` · `SHIPPED` · `CANCELLED` · `BACKORDERED`)은 출고 목록 · 상태 필터가 이미 쓰는 원문 그대로이고, 색 견본은 글자가 없다(`aria-hidden`). IAM 막대에는 새 글자가 없다 — 기존 «활성» · «정지» · «잠금» 라벨 앞에 색 견본만 붙는다.

## 구현 (2026-10-22 UTC · Sonnet)

**파일**
- 계약(별도 커밋): `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5.1 · § 2.4.3.1.
- 출고: `features/wms-outbound-ops/api/types.ts`(+`OutboundStatusCount` · 셀 상태 — 클라이언트 안전) · `api/outbound-state.ts`(`statusCounts` · `countCell` · 8읽기 병렬) · `components/OutboundStatusPipeline.tsx`(신규, `'use client'` 없음 · 서버 전용 임포트 없음) · `components/OutboundOpsScreen.tsx`(`statusCounts` prop, 헤더 아래 · 표 위) · `index.ts`(타입 export) · `app/(console)/wms/outbound/page.tsx`(prop 전달).
- IAM: `features/iam-overview/components/IamOverviewSummaryCards.tsx`(운영자 활성/정지 `StackedBar` · 계정 잠금 `StackedBar` · 라벨 앞 `BarSwatch`).
- `shared/ui/InlineBar.tsx` — **변경 없음**(기존 `StackedBar` · `BarSwatch` 그대로 재사용, `expected` 옵션 이미 존재).
- 시험: `tests/unit/outbound-pipeline-and-iam-ratio-bars.test.tsx`(16) · `tests/unit/outbound-state-counts.test.ts`(5).
- 건드리지 않음: `features/wms-ops/api/overview-state.ts` · admin 읽기 모델 · § 2.4.5.2.

**SSR vs 클라이언트**
- `/wms/outbound`: 7총계 + 페이지 0 목록은 **서버(SSR)** `getOutboundSectionState` 에서 한 번(`Promise.all` 8읽기). 총계는 prop 으로 내려가 클라이언트 `OutboundOpsScreen` 안에서 렌더만 되고(컴포넌트 자체는 훅 · 상태 없음) **필터/페이지 변경은 목록 한 번만 다시 읽는다**(시험으로 고정: 호출 1). 출고 SSR 이 바뀌므로 머지 전 브랜치 nightly 콘솔 잡 필요(AC-2).
- `/iam`: `IamOverviewSummaryCards` 는 서버 컴포넌트 그대로, 새 호출 0.

**검증 (2026-10-22 UTC, 로컬)** — `vitest run --maxWorkers=4 --minWorkers=2` 425 파일 · 5176 시험 rc=0 · `tsc --noEmit` rc=0 · `next lint` rc=0 · `check-client-graph-server-only.mjs` rc=0(console-web hits=0) · `check-client-graph-backend-origins.mjs` rc=0(console-web hits=0) · `check-index-queue-drift.sh` · `check-task-id-collision.sh` · `check-walkthrough-ledger-drift.sh` 각 rc=0(스테이지 뒤). AC-1(소유자 문구 승인) · AC-2(브랜치 nightly) · AC-3(배포 · 샘플 화면)은 열려 있다.
