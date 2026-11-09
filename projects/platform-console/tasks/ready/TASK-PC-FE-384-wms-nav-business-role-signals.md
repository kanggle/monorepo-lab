# Task ID

TASK-PC-FE-384

# Title

`ADR-MONO-083` WMS 콘솔 후속 — WMS 메뉴 «업무 권한 없음» 신호를 WMS 업무 역할로 (`permission-map.ts` `serverRequires` · 계약 § 2.4.3.4) · WMS 개요 · 운영 개요 WMS 카드 — 서버 = `TASK-MONO-827`

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (데이터 행 · 계약 표 — 규칙 신설 없음, `TASK-PC-FE-344` 와 같은 모양)
> 기안 2026-11-09 UTC. 출처: `TASK-MONO-827` Out of Scope «콘솔 반영은 별도 콘솔 티켓, 굽기 라이브 뒤(793 · 794 와 같음)» · 827 § 구현 메모.
> ⏳ **머지 게이트 — «`TASK-MONO-827` 을 실은 AMI 굽기가 라이브 확인된 뒤에만 머지»** (`TASK-PC-FE-342` · `343` · `344` 와 같은 순서). 콘솔은 머지 즉시 Vercel 로 나가고 백엔드는 굽기에서만 나간다. 굽기 전에 머지하면 라이브 admin-service 가 아직 합성으로 **모든 구독 운영자에게 대시보드를 열어 주는데** 화면이 조회 세트 운영자의 WMS 메뉴에 «업무 권한 없음» 을 그린다(거짓 배지). 반대(굽기 뒤 · 이 PR 전)는 세트 없는 운영자에게 배지 없이 메뉴가 열리고 들어가면 서버 403(화면 안 «권한 없음») — 안전 쪽 실패.

# Dependency Markers

- **선행**: `TASK-MONO-827`(#4632 머지 `aaaad2dfa` + 그 커밋을 담은 굽기 + 라이브 확인 — 827 AC-4 · `TASK-MONO-672` 세트별 표 WMS 행). 이 티켓의 AC-0 이 그 라이브 확인을 다시 잰다.
- 형제: `TASK-PC-FE-342`(SCM) · `TASK-PC-FE-343`(ERP) · `TASK-PC-FE-344`(Finance) — 같은 모양 · `TASK-PC-FE-338`(c-2 — `serverRequires` · `writeRequires` 규칙. 이 티켓은 **데이터만** 더한다).

# Goal

`TASK-MONO-827` 이 WMS admin-service 의 구독 합성(`entitled_domains ∋ wms` → `ROLE_WMS_VIEWER`)을 지우고 대시보드 · 설정 조회를 업무 역할로 판정하게 했다(`projects/wms-platform/specs/contracts/http/admin-service-api.md` § Read gates — 정본). 콘솔의 WMS 메뉴 신호와 WMS 개요 · 운영 개요 WMS 카드를 그 서버 판정과 같게 만든다. 지금 `permission-map.ts` 의 `/wms` 행 주석(`:722-724`)은 «구독만으로 열린다» 를 전제로 `serverRequires` 를 비워 두었다 — 827 뒤에는 틀린 전제다.

# Scope

## In Scope

- 계약 먼저: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 — WMS 행을 아래 대조표로(Finance 를 떼어 낸 344 와 같은 방식) · 운영 개요 WMS 카드(§ 2.4.9.1)의 forbidden 조건.
- `apps/console-web/src/shared/guide/permission-map.ts` WMS 행(`/wms` · `/wms/inbound` · `/wms/inventory` · `/wms/master` · `/wms/operations` — `/wms/outbound` 은 이미 `OUTBOUND_*`, `/wms/guide` 제외): 대조표 대로 `serverRequires`. `/wms` 행의 합성 전제 주석을 고친다.
- `/wms` 개요가 실제로 부르는 갈래와 «한 갈래 403 이면 무엇이 되나»(화면 전체 forbidden 인지 갈래별 저하인지)를 AC-0 에서 재고 대조표 칸을 확정한다 — 344 의 `/finance` 개요가 «데이터 없이 렌더» 라고 잘못 기안됐던 전례.
- 시험: `permission-map-drift` · `console-nav-business-permission.test.ts` 의 WMS 대조군.

## Out of Scope

- 서버 판정 변경(827 에서 끝났다).
- 새 신호 규칙 · 화면 행동 변경(예: 개요의 갈래별 저하로 바꾸기 — 필요하면 후속 티켓으로 기록만).
- 가이드 문안(`features/wms-guide/data.ts`) — `TASK-PC-FE-348` 계열.

# 서버 판정 대조표 (`TASK-MONO-827` 기준 — AC-0 에서 다시 잰다)

| 화면 | 기본 조회가 부르는 것 | `serverRequires` | 변경 버튼 | `writeRequires` |
|---|---|---|---|---|
| `/wms` 개요 | `/dashboard/inventory` · `/throughput` · `/orders` · `/alerts`(AC-0 에서 확정) | AC-0 — 갈래들의 교집합/합집합 중 화면 동작에 맞는 쪽 | 알림 확인 | `WMS_OPERATOR`(그대로) |
| `/wms/inbound` | `/dashboard/asns` · `/inspection` | `INBOUND_READ` · `INBOUND_WRITE` | — | — |
| `/wms/inventory` | `/dashboard/inventory` · `/by-key` | `INVENTORY_READ` · `INVENTORY_WRITE` | — | — |
| `/wms/outbound` | outbound-service · `/dashboard/shipments` | `OUTBOUND_READ` · `OUTBOUND_WRITE`(이미 있음 — 827 로 `/shipments` 도 같은 역할) | (그대로) | (그대로) |
| `/wms/master` | `/dashboard/refs/{type}` | `MASTER_READ` | — | — |
| `/wms/operations` | `/settings` · `/operations/projection-status` | AC-0 — 설정 조회 = WMS 업무 역할 아무거나 · projection-status = `WMS_ADMIN`(827 전부터, 콘솔 운영자 토큰에는 없음) | — | — |
| 운영 개요 WMS 카드 | `/dashboard/inventory`(`tests/federation-hardening-e2e` 가 부르는 출처) | (메뉴 신호 대상 아님 — 카드가 `INVENTORY_*` 없는 운영자에게 `forbidden` 으로 정직한지 AC-0) | — | — |

모든 행: `WMS_OPERATOR` 보유(`wms-ops-actions` · `wms-full` · `legacy-compat`)는 서버 역할 계층으로 전부 통과 — `serverRequires` 에 `WMS_OPERATOR` 를 함께 넣는다(아래 Edge Case 1).

# Acceptance Criteria

- [ ] **AC-0** — 🔴 머지 게이트 실측: `TASK-MONO-827` 을 담은 AMI 가 라이브인가(굽기 기록 · 창에서 세트 0 운영자의 `/api/v1/admin/dashboard/orders` 가 403 인가 — 827 AC-4). 아니면 **이 티켓은 시작하지 않는다**. 위 대조표의 «AC-0» 칸을 콘솔 코드(`features/wms-ops/**` · `shared/composition/console-composition.ts` WMS 갈래)로 확정한다.
- [ ] **AC-1** — 계약 § 2.4.3.4(와 필요하면 § 2.4.9.1) 개정이 코드보다 먼저.
- [ ] **AC-2** — 🔴 대조군: `wms-inventory-read` → `/wms/inventory` 배지 없음 · `/wms/inbound` · `/wms/master` · `/wms/outbound` 배지 / `wms-outbound-operate` → `/wms/outbound` 배지 없음 · `/wms/inventory` 배지 / `wms-master-read` → `/wms/master` 배지 없음 / `wms-ops-actions` · `legacy-compat` → WMS 메뉴 배지 0 / WMS 세트 0(다른 도메인 세트만) → WMS 메뉴 전부 배지.
- [ ] **AC-3** — 실패 경로: `/me/domain-access` 실패 · 시간 초과 → 배지 없음(지금 규칙 그대로).
- [ ] **AC-4** — 배포 상태(Vercel · 라이브 화면)를 따로 적는다. 라이브 = 창에서 단일 세트 운영자로 위 대조군 중 둘 이상(배지 있음 · 없음 양쪽).

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9 · § WMS 행 정정 (`TASK-MONO-827`)
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § Read gates (서버 판정 정본)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 · § 2.4.9.1
- `projects/iam-platform/specs/contracts/http/admin-api.md` § 카탈로그 (`/me/domain-access`)

# Edge Cases

- **`WMS_OPERATOR` 는 `/me/domain-access` 에 나온다**(Finance 의 `FINANCE_OPERATOR` 와 다르다 — iam `DomainRoleCatalog.java:46` 의 카탈로그 행이고 `wms-ops-actions` 가 준다). 서버는 계층(`OPERATOR > VIEWER`)으로 모든 대시보드를 열어 주므로 `serverRequires` 에서 빠지면 `wms-ops-actions` 운영자에게 거짓 배지가 생긴다.
- `WMS_VIEWER` 는 어떤 세트에도 없다(카탈로그 밖) — 신호 입력으로 쓰지 않는다.
- 테넌트를 고르지 않은 운영자(기본 토큰 — 역할 0)는 WMS 전 화면이 서버 403 이다(827) — 신호도 «업무 권한 없음» 이 맞다.
- `/wms/operations` 의 projection-status 는 827 전부터 `WMS_ADMIN` 이고 콘솔 운영자 토큰에는 ADMIN 등급이 없다 — 이 칸 때문에 화면 전체를 배지로 만들면 지금보다 넓게 닫는다. AC-0 이 화면이 그 갈래 실패를 어떻게 그리는지 잰다.

# Failure Scenarios

1. 굽기 전에 머지 — 위 머지 게이트의 거짓 배지.
2. `serverRequires` 에 `WMS_OPERATOR` 를 빠뜨린다 — `wms-ops-actions` 운영자의 WMS 메뉴 전부 거짓 배지(Edge Case 1).
3. `/wms` 개요 칸을 갈래 하나로 잡는다 — 344 `/finance` 처럼 «한 갈래 403 = 화면 전체 forbidden» 이면 배지가 화면보다 좁아진다(덜 알림). AC-0 의 화면 동작 측정이 이것을 막는다.
