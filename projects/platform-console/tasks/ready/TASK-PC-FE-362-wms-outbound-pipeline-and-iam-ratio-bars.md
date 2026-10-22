# Task ID

TASK-PC-FE-362

# Title

그래프 4차 묶음 — WMS 개요에 출고 상태 파이프라인 막대(상태별 총 건수) · IAM 개요에 운영자 활성 · 정지 비율과 계정 잠김 비율 막대

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (360 의 상태 분포 막대 · 357 의 `InlineBar` 와 같은 모양 · 서버 변경 없음)
> 기안 2026-10-22 UTC — 콘솔 그래프 남은 후보 중 추천 둘(소유자 «진행», 2026-10-22 KST). 1~3차 = `TASK-PC-FE-357` · `359` · `360` · `361`(done) · `358`(review).

# Dependency Markers

- 선행 없음. 재사용: `shared/ui/InlineBar.tsx` · 360 의 `ShippingStatusBar` / `ApprovalStatusBar` 규칙(칸 하나라도 비ok → 막대 없음 · 전부 0 → 빈 문구).

# Goal

| # | 화면 | 더하는 것 | 데이터 |
|---|---|---|---|
| 1 | WMS 개요 `/wms` | 출고 주문의 **상태 파이프라인 누적 막대** — 정상 흐름 `PICKING → PICKED → PACKING → PACKED → SHIPPED`(361 이 인용한 `projects/wms-platform/specs/services/outbound-service/state-machines/order-status.md` 순서) + 곁가지(`CANCELLED` · `BACKORDERED`)는 막대 밖 숫자로 | 상태마다 `listOrders({status, size:1}).totalElements` — 개요가 이미 `RECEIVED` 를 이렇게 센다(`features/wms-ops/api/overview-state.ts` — AC-0 에서 줄 번호). 출고 주문은 **테넌트 격리** 표면(WMS admin 계약 § 1.0 · § 1.3) ⇒ 내 회사 주문만 센다 — 화면에 밝힌다 |
| 2 | IAM 개요 `/iam` | 운영자 **활성 · 정지 비율 막대** · 계정 **잠김 비율 막대**(잠김 / 전체) | 이미 받는 `{total, active, suspended}` · `{total, locked}`(`features/iam-overview/api/overview-state.ts`) — 상태 필터 `size:1` 의 전체 모집단 값인지 AC-0 이 확인 |

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 개요의 출고 주문 읽기(엔드포인트 · `status` 필터 · `totalElements` 가 테넌트 필터 뒤 전체인가) · `RECEIVED` 를 이미 세는 셀 ② 출고 상태 목록 · 순서를 계약에서(`RECEIVED` 는 같은 TX 에서 `PICKING` 으로 넘어가 쉬는 단계가 아니다 — 361 AC-0 ④) ③ IAM 개요의 세 숫자가 전체 모집단인가(대시보드 색인 `/dashboards` 의 `activeCount` 는 **제한된 조각**이었다 — 같은 실수를 하지 않는다) · 계정 상태 `ACTIVE` · `DORMANT` · `DELETED` 를 따로 세지 않는다면 잠김 비율만 ④ 셀 저하 규칙(403 · 401) ⑤ 샘플 고정값에 상태별 값이 있는가.
- 계약 먼저: `console-integration-contract.md` 의 WMS 개요 절(§ 2.4.5.2)과 IAM 개요 절에 새 읽기 · 집계 범위를 적은 뒤 구현.
- 구현: 상태별 독립 셀 · 하나라도 비ok → 막대 없음 · 전부 0 → 빈 문구 · 숫자 글자 · 색 견본 · 기존 testid 유지 · 클라이언트 컴포넌트는 서버 전용 모듈을 임포트하지 않는다.
- 시험: 비율 · 하나 저하 · 전부 0 · 403 · 401(세션 전체 재로그인) · 호출 수(출고 = 정상 흐름 5 + 곁가지 2, `RECEIVED` 셀은 기존 그대로) · IAM 은 새 호출 0.

## Out of Scope

- 서버 · BFF 변경 · 입고(ASN) 상태 분포(필요하면 다음 묶음).
- 그 밖의 «효과 낮음» 후보 · ERP 트리맵 · 재주문점 게이지 · 감사 로그(소유자: 추천 둘만).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~⑤.
- [ ] **AC-1** — 계약 먼저 → 구현 + **사용자에게 보이는 새 문구 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [ ] **AC-2** — 시험 위 목록 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` · `node scripts/check-client-graph-server-only.mjs` · `check-client-graph-backend-origins.mjs`(스테이지 뒤) rc=0. SSR 이 바뀌므로 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5.2 (WMS 개요) · IAM 개요 절(AC-0 에서 이름 확인)
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.0 · § 1.3 (읽기만)
- `projects/wms-platform/specs/services/outbound-service/state-machines/order-status.md` (읽기만)

# Edge Cases

- 테넌트 축이 없는 운영자(네이티브 `wms` 평면)는 출고 주문이 비어 보일 수 있다(계약 § 1.0) — 빈 문구로, 오류처럼 보이지 않게.
- `RECEIVED` 가 남아 있는 주문(같은 TX 전환이 실패한 경우)은 정상 흐름에 넣지 않고 기존 셀 숫자로만.
- IAM 운영자 0 · 계정 0 → 막대 없음.

# Failure Scenarios

1. 제한된 조각(첫 페이지 · 샘플)을 전체 비율처럼 그린다 → AC-0 ③ · 시험.
2. 출고 상태 하나의 실패가 개요 전체를 저하시킨다 → 셀 독립 시험.
3. 정상 흐름 순서를 손으로 만든다 → 계약 인용 + 시험.
