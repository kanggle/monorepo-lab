# Task ID

TASK-PC-FE-384

# Title

`ADR-MONO-083` WMS 콘솔 후속 — WMS 메뉴 «업무 권한 없음» 신호를 WMS 업무 역할로 (`permission-map.ts` `serverRequires` · 계약 § 2.4.3.4) · WMS 개요 · 운영 개요 WMS 카드 — 서버 = `TASK-MONO-827`

# Status

done

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

- [x] **AC-0** — 🔴 머지 게이트 실측: `TASK-MONO-827` 을 담은 AMI 가 라이브인가(굽기 기록 · 창에서 세트 0 운영자의 `/api/v1/admin/dashboard/orders` 가 403 인가 — 827 AC-4). 아니면 **이 티켓은 시작하지 않는다**. 위 대조표의 «AC-0» 칸을 콘솔 코드(`features/wms-ops/**` · `shared/composition/console-composition.ts` WMS 갈래)로 확정한다.
- [x] **AC-1** — 계약 § 2.4.3.4(와 필요하면 § 2.4.9.1) 개정이 코드보다 먼저.
- [x] **AC-2** — 🔴 대조군: `wms-inventory-read` → `/wms/inventory` 배지 없음 · `/wms/inbound` · `/wms/master` · `/wms/outbound` 배지 / `wms-outbound-operate` → `/wms/outbound` 배지 없음 · `/wms/inventory` 배지 / `wms-master-read` → `/wms/master` 배지 없음 / `wms-ops-actions` · `legacy-compat` → WMS 메뉴 배지 0 / WMS 세트 0(다른 도메인 세트만) → WMS 메뉴 전부 배지.
- [x] **AC-3** — 실패 경로: `/me/domain-access` 실패 · 시간 초과 → 배지 없음(지금 규칙 그대로).
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

## CORRECTION (구현 착수 중, 2026-11-24 UTC)

1. **AC-0 완료** — 38차 창(2026-11-24 UTC), AMI `ami-0f3dc38ba84b6d232`(`3277f0473`): 세트 0 운영자(토큰 `entitled_domains ∋ wms`) 의 `/api/v1/admin/dashboard/orders` · `/inventory` = 403 · 403. `wms-outbound-operate` → orders 200 / inventory 403. `wms-inventory-read` → orders 403 / inventory 200. `scm-read` → 403 / 403. `legacy-compat` → 200 / 200. 기록: `tasks/done/TASK-MONO-827-*.md` § CORRECTION — AC-4.
2. **운영 개요 WMS 카드의 출처는 `/dashboard/inventory` 가 아니라 `/dashboard/orders?status=RECEIVED`** (계약 § 2.4.9.1 행 2). 메뉴 신호 대상이 아니고 403 은 이미 카드별 `forbidden` 으로 매핑된다 — 코드 변경 없음. 계약 § 2.4.9.1 에 «구독만으로 열린다» 류 문장은 없어 문구 수정도 없다.
3. **AC-2 의 «`wms-ops-actions` → WMS 메뉴 배지 0» 은 `/wms/outbound` 에서 틀리다** — outbound-service GET = `OUTBOUND_READ ∨ OUTBOUND_WRITE ∨ OUTBOUND_ADMIN`, `WMS_OPERATOR` 는 통과하지 못한다. 시험은 `/wms/outbound` 에 배지가 있음을 단언한다(나머지 WMS 잎 배지 0). `/wms/outbound` 행은 변경 없음.
4. **`/wms/operations` 결정** — `/wms` 와 같은 합집합(8역할). 설정 GET = «업무 역할 아무거나»; projection-status(`WMS_ADMIN`) 는 독립 셀(`operations-state.ts`)이고 페이지 수준 forbidden 분기가 없으며 콘솔 운영자 토큰에 ADMIN 이 없으므로 배지를 움직이지 않는다. `/wms` 개요도 같은 합집합 — 유일한 화면 전체 forbidden 은 `getWmsSectionState` → `listAlerts`(알림 목록 = 영역 역할 아무거나), 나머지 카드는 `cell()` 403 → `forbidden` 으로 독립.

## CORRECTION — AC-4 라이브 화면 (2026-11-24 UTC · 분석=Opus 5.5)

**AC-4 ✅**(체크박스는 review 동결이라 그대로, 이 절이 닫힘 기록). 배포: #4649 squash `627ed8a25` → Vercel `Production – kanggle-console` success(10:37:53Z, GitHub deployment `8025324475`). 백엔드: 38차 AMI `ami-0f3dc38ba84b6d232`(`3277f0473`). 창: `console` + `console-wms`(10:39:15Z → 10:41:56Z ready → 10:47:51Z stop, 예산 51 → 56 / 2400).

창에서 `badge-11241038@example.com` 을 `TASK-MONO-672` 항목 22 절차로 만들고(수락 직후 세트 0), 로컬 Playwright(system Chrome)로 `https://console.hubwang.com` 에 실제 로그인 → `/wms` 에서 사이드바 각 WMS 링크 안의 «업무 권한 없음» 유무를 읽었다(본문 덤프 없음). 단일 테넌트 운영자라 셀렉트 대신 `tenant-single` «테넌트: demo-corp».

| 메뉴 | `wms-inventory-read` | 세트 0 |
|---|---|---|
| `/wms` 개요 | 배지 없음 | **배지** |
| `/wms/inventory` | 배지 없음 | **배지** |
| `/wms/inbound` | **배지** | **배지** |
| `/wms/outbound` | **배지** | **배지** |
| `/wms/master` | **배지** | **배지** |
| `/wms/operations` | 배지 없음 | **배지** |
| WMS 상위 메뉴 | 배지 없음 | **배지** |
| `/wms` 화면 `wms-forbidden` | 0 | 1 (서버 403 과 일치) |

- 대조군 둘(배지 있음 · 없음 양쪽 + 세트 0)이 계약 § 2.4.3.4 새 행 그대로 — 상위 메뉴는 «하위 전부 배지일 때만» 규칙대로.
- 정리: 세트 회수 → 운영자 `01a20d26-473f-71e9-817d-a4308972d32a` `SUSPENDED` · 호스트 · 로컬 임시 파일(비밀번호) 삭제.
- ⇒ done(4차원: #4649 MERGED · `627ed8a25` ⊂ main · 머지 시점 실패 0 · AC 절 — AC-0~3 체크 · AC-4 = 이 절).
