# Task ID

TASK-PC-FE-342

# Title

`ADR-MONO-083` P5 콘솔 후속 — SCM 메뉴 «업무 권한 없음» · 쓰기 버튼을 SCM 세분 역할로 (`permission-map.ts` `serverRequires` / `writeRequires` · 계약 § 2.4.3.4) · WMS 출고 «발송 재시도» 버튼은 `SCM_LOGISTICS_WRITE`

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (데이터 행 · 계약 표 — 규칙 신설 없음)
> ⏳ **머지 게이트 — «`TASK-MONO-792` 를 실은 AMI 굽기가 라이브 확인된 뒤에만 머지»** (소유자 결정 2026-10-15 UTC, `TASK-MONO-792` § 소유자 결정 «순서»). 콘솔은 머지 즉시 Vercel 로 나가고 백엔드는 굽기에서만 나간다. 굽기 전에 머지하면 라이브 `GET /api/admin/me/domain-access` 가 아직 `SCM_OPERATOR` 만 내므로 **모든 운영자의 SCM 메뉴에 거짓 «업무 권한 없음» + 승인 · 기각 · 정책/매핑 저장 버튼 숨김** — 서버는 열어 주는데 화면이 닫는다. 그 반대(굽기 뒤 · 이 PR 전)는 조회 세트 운영자에게 쓰기 버튼이 보이고 누르면 서버 403(화면 안 «권한 없음») — 안전 쪽 실패.

# Dependency Markers

- **선행**: `TASK-MONO-792`(머지 + 그 커밋을 담은 굽기 + 라이브 확인 — `TASK-MONO-672` 다음 창 목록의 792 항목). 이 티켓의 AC-0 이 그 라이브 확인을 다시 잰다.
- 형제: `TASK-PC-FE-338`(c-2 — 메뉴 단위 `serverRequires` · `writeRequires` 규칙을 만든 티켓, 이 티켓은 **데이터만** 더한다 — 계약 § 2.4.3.4 «Scales with P5–P8»).

# Goal

`TASK-MONO-792` 이 SCM 4 서비스에 세분 서버 인가를 넣었다(scm `iam-integration.md` § 업무 역할). 콘솔의 SCM 메뉴 · 버튼 신호를 서버 판정과 같게 만든다 — 계약이 «그 단계가 `serverRequires`(·`writeRequires`) 를 더한다 — 데이터만» 이라고 미뤄 둔 몫.

# Scope

## In Scope

- 계약 먼저: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4
  - `:281` «발송 재시도 … `authenticated()` only. It carries no requirement and stays visible» → `SCM_LOGISTICS_WRITE` 를 요구, 없으면 숨김(WMS 출고 화면 — 소유자 결정 Q3 «엄격»).
  - `:304` «SCM · ERP · Finance leaves | — | SCM services are `authenticated()` only» → SCM 행을 떼어 아래 표로.
  - `:307` «Scales with P5–P8» 문장에 P5 완료 표시.
- `apps/console-web/src/shared/guide/permission-map.ts` SCM 행(`/scm` · `/scm/procurement` · `/scm/inventory` · `/scm/replenishment` · `/scm/config`, `/scm/guide` 제외):
  - `gate.roles` = SCM 카탈로그 역할(상한) — `SCM_OPERATOR` 단독이 아니라 8개 업무 역할.
  - `serverRequires`(기본 조회) · `writeRequires`(변경 버튼) — 아래 «서버 판정 대조표» 대로.
- WMS 출고 화면의 «발송 재시도» 버튼: `writeRequires: ['SCM_LOGISTICS_WRITE']` 에 해당하는 숨김(배차 조회 칸은 `SCM_LOGISTICS_READ ∨ _WRITE` 가 없으면 화면 안 «권한 없음» — 숨기지 않는다).
- 시험: `permission-map-drift` · 메뉴 신호 · 버튼 숨김 단위 시험 — 조회 세트 → 쓰기 버튼 없음 / 수요계획 세트만 → `/scm/procurement` 배지 · `/scm/replenishment` 승인 버튼 보임.

## Out of Scope

- 서버 판정 변경(792 에서 끝났다) · ERP · Finance · E-commerce(P6 ~ P8 의 콘솔 후속).
- 새 신호 규칙(계약 § 2.4.3.4 의 규칙은 그대로 — 데이터만).

# 서버 판정 대조표 (`TASK-MONO-792` 기준 — AC-0 에서 다시 잰다)

| 화면 | 기본 조회가 부르는 것 | `serverRequires` | 변경 버튼 | `writeRequires` |
|---|---|---|---|---|
| `/scm` 개요 | procurement PO · inventory-visibility snapshot | 둘 중 하나라도 — `SCM_PROCUREMENT_READ/WRITE` · `SCM_INVENTORY_VISIBILITY_READ/WRITE` (카드별 저하는 지금 규칙) | — | — |
| `/scm/procurement` | `GET /api/v1/procurement/po` | `SCM_PROCUREMENT_READ` · `SCM_PROCUREMENT_WRITE` | (READ-ONLY 화면) | — |
| `/scm/inventory` | `GET /api/v1/inventory-visibility/snapshot` | `SCM_INVENTORY_VISIBILITY_READ` · `_WRITE` | (READ-ONLY) | — |
| `/scm/replenishment` | `GET /api/v1/demand-planning/suggestions` | `SCM_DEMAND_PLANNING_READ` · `_WRITE` | 승인 · 기각 | `SCM_DEMAND_PLANNING_WRITE` (승인의 발주 초안 = from-suggestion 이 이 역할을 받는다, Q2) |
| `/scm/config` | `GET …/policies` · `…/sku-supplier-map` | `SCM_DEMAND_PLANNING_READ` · `_WRITE` | 정책 · 매핑 저장 | `SCM_DEMAND_PLANNING_WRITE` — 🔵 공급사 선택 목록은 발주 조회(`GET /procurement/suppliers`)라 수요계획 세트만이면 그 칸만 «권한 없음» |
| `/wms/outbound` «발송 재시도» | `GET /api/v1/logistics/dispatches/by-shipment/{id}` | (화면 전체 = 출고 — 지금 행 유지) | 발송 재시도 | `SCM_LOGISTICS_WRITE` |

# Acceptance Criteria

- [ ] **AC-0** — 🔴 머지 게이트 실측: `TASK-MONO-792` 를 담은 AMI 가 라이브인가(굽기 기록 · `GET /api/admin/domain-roles` 가 `SCM_PROCUREMENT_READ` 를 내는가 · 라이브 `/me/domain-access` 가 데모 운영자에게 SCM 8 역할을 내는가). 아니면 **이 티켓은 시작하지 않는다**. 위 대조표를 서버 코드(`SecurityConfig` 4개)와 다시 대조.
- [ ] **AC-1** — 계약 § 2.4.3.4 개정(`:281` · `:304` · `:307`) 이 코드보다 먼저.
- [ ] **AC-2** — 🔴 대조군: 조회 세트(`scm-read`) → SCM 메뉴 배지 없음 · 승인/기각/저장 버튼 없음 / 수요계획 세트만 → `/scm/procurement` · `/scm/inventory` 배지, `/scm/replenishment` 버튼 보임 / SCM 세트 0 → SCM 메뉴 전부 배지 / WMS 출고 세트만 → 출고 화면의 «발송 재시도» 없음.
- [ ] **AC-3** — 실패 경로: `/me/domain-access` 실패 · 시간 초과 → 배지 없음 · 버튼 보임(지금 규칙 그대로).
- [ ] **AC-4** — 배포 상태(Vercel · 라이브 화면) 를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9 · P5
- `projects/scm-platform/specs/integration/iam-integration.md` § 업무 역할 (서버 판정 정본)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4
- `projects/iam-platform/specs/contracts/http/admin-api.md` § 카탈로그 (row 12 `/me/domain-access`)

# Edge Cases

- `SCM_OPERATOR` 는 `/me/domain-access` 에 **나오지 않는다**(카탈로그 밖 · auth 파생) — 신호 입력으로 쓰지 않는다.
- 개요 카드 둘은 서로 다른 서비스 — 한쪽 역할만 있으면 그 카드만 «권한 없음»(카드 단위 저하는 지금 규칙).

# Failure Scenarios

1. 굽기 전에 머지 — 위 머지 게이트의 거짓 배지 · 버튼 숨김.
2. `SCM_OPERATOR` 를 `serverRequires` 에 넣는다 — 진입 역할은 업무 API 를 열지 않으므로 신호가 서버보다 넓어진다.
3. 발송 재시도 버튼을 WMS 역할로 판정 — 서버(물류)는 SCM 역할만 본다(Q3).

# Merge gate — 28th window

- 2026-10-16 UTC — gate met: the bake carrying 792/793/794/795 was live-verified in the 28th window (2026-10-16) — may now be implemented/merged. (AMI `ami-00ef8cc1057cca59b` @ `8905d2c29`; this ticket's source `TASK-MONO-792` → `done/`; results in `TASK-MONO-672` § 28th window — harvest.)
