# Task ID

TASK-PC-FE-358

# Title

WMS 개요에 일별 입고 · 출고 처리량 그래프 — 계약에 있고 API 클라이언트까지 있지만 BFF · 화면이 없는 `GET /dashboard/throughput` 를 잇는다

# Status

ready

# Owner

frontend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (BFF 라우트 · SSR 상태 함수 · 계약 갱신 · 권한 판정이 함께 바뀜) — AC-0 은 Opus, 결정 뒤 화면 막대는 Sonnet 으로 나눠도 된다.
> 기안 2026-10-22 UTC — 소유자 요청 «콘솔에서 그래프로 더 직관적으로» 조사(이 세션)의 1차 묶음 B. 묶음 A 는 `TASK-PC-FE-357`.

# Dependency Markers

- 선행 없음. 같은 1차 묶음: `TASK-PC-FE-357`(독립).
- 🔴 계약 먼저: BFF 라우트를 더하므로 `console-integration-contract.md` § WMS 를 구현 전에 고친다(CLAUDE.md Layer Rules).

# Goal

콘솔 화면 가운데 **일별 시계열을 받을 수 있는 곳은 이것 하나**다. WMS admin-service 계약 § 1.2(`projects/wms-platform/specs/contracts/http/admin-service-api.md:301-339`)는 창고 하나 · 최대 90일의 `days[] {date, inbound{putawayCount, qtyReceived}, outbound{shipmentCount, qtyShipped}}` + `totals` 를 낸다(인가 `WMS_VIEWER` 이상 · 창고 단위 데이터 = 테넌트 구분 없음, ADR-MONO-065 § 1.0 설계). 콘솔에는 클라이언트 `getThroughput()`(`features/wms-ops/api/wms-inventory-api.ts:60`) · `ThroughputSchema`(`types.ts:82`) · 샘플 고정값(`shared/sample/fixtures/wms.ts:420`)까지 있는데 **BFF 라우트 · 상태 함수 · 화면이 없다**. 이것을 이어 WMS 개요(`/wms`)에서 «요즘 입고와 출고가 늘었나 줄었나» 를 한눈에 보이게 한다.

# Scope

## In Scope

- **AC-0 측정** (코드 · 데이터, 창 불필요):
  - ① `/wms` 개요가 지금 무엇을 그리고 창고를 어떻게 아는가 — 창고 목록(`master` refs `warehouses`) 을 이미 받는가, 창고 수(샘플 · 데모 시드).
  - ② 권한: 콘솔 운영자 토큰의 WMS 역할(`WMS_OPERATOR` · `INVENTORY_READ` …)로 `WMS_VIEWER 이상` 이 통과하는가 — admin-service 의 실제 인가 코드(`@PreAuthorize` 등)를 읽어 file:line. 업무 권한 세트(ADR-MONO-083)의 어떤 세트가 이 화면을 볼 수 있어야 하는가(`permission-map.ts` 의 `/wms` 행).
  - ③ 데모 시드에 처리량 데이터가 있는가(투영 표 · 시드 스크립트) — 없으면 라이브에서 빈 그래프다.
  - ④ 날짜 기준: `from`/`to` 는 `LocalDate` — 서버가 어느 시간대로 «하루» 를 자르는가, 콘솔은 KST 로 «오늘» 을 정하는가(KST/UTC 어긋남 = 이 호스트 함정).
- **소유자 결정 (AC-0 뒤 선택창)**: 기본 창고(창고 하나면 자동 · 여럿이면 선택 칸 vs 첫 창고) · 기본 기간(예 14일 · 30일) · 막대 vs 선 · 수량(`qty*`) vs 건수(`*Count`) 중 무엇을 기본으로.
- BFF 라우트(형제 `app/api/wms/*` 모양 · degrade 규약 그대로) · SSR 상태 함수(독립 degrade — 이 칸이 실패해도 개요 나머지는 그대로) · CSS 막대 그래프(라이브러리 없음 — `RankingBarChart` 원칙, 357 의 공용 원자를 쓸 수 있으면 쓴다) · 합계 숫자는 글자로.
- 계약 `console-integration-contract.md` § WMS 갱신(라우트 · 응답 · degrade).
- 시험: 상태 함수(200 · 403 · 5xx · 빈 `days`) · BFF 프록시 · 화면(막대 비율 · 0 · 빈 기간 · 날짜 라벨) · 샘플 고정값 경로.

## Out of Scope

- 서버(admin-service) 변경 · 새 집계 API(ADR-MONO-017 D3.B — 생산자 `/summary` 금지).
- 창고 여러 개를 합친 그래프(API 가 창고 하나 단위 — 합치려면 창고 수만큼 호출, 별도 결정).

# Acceptance Criteria

- [ ] **AC-0** — ①~④ 를 file:line 과 함께 기록. ② 에서 콘솔 토큰이 통과하지 못하면 STOP → 서버 · 카탈로그 티켓 먼저(이 티켓은 그 뒤).
- [ ] **AC-1** — 소유자 결정(기본 창고 · 기간 · 막대/선 · 수량/건수)을 선택창으로 받는다(추천과 함께).
- [ ] **AC-2** — 계약 먼저 갱신 → 구현. **사용자에게 보이는 새 문구(제목 · 범례 · 단위 · 빈 상태 · 오류 · 창고 선택 라벨) 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [ ] **AC-3** — 시험 위 목록 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` rc=0. SSR 이 바뀌므로 **머지 전 브랜치에서 nightly 콘솔 잡** 실행 · 확인.
- [ ] **AC-4** — 배포(Vercel Production 기록) · 익명 샘플 화면에서 그래프 확인 · 로그인 운영자 라이브 = 다음 데모 창(③ 에서 시드가 비면 «빈 그래프 문구» 가 보이는 것까지가 판정).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.0 · § 1.2

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § WMS (갱신 대상)
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.2 (읽기만)

# Edge Cases

- 범위 > 90일 · `to < from` → 서버 400 — 콘솔이 보내지 않게 막고, 받으면 degrade.
- 데이터가 없는 날(`days` 에 빠진 날짜 vs 0 으로 채운 날짜) — AC-0 에서 서버 동작을 읽고, 빠진 날은 0 으로 그릴지 결정(그래프 가로축이 날짜를 건너뛰면 추세가 거짓이 된다).
- 창고 0개(테넌트에 창고가 없음) → 그래프 대신 안내.
- 업무 권한 세트가 WMS 재고만 있는 운영자 — ② 의 판정대로 숨김 또는 «권한 없음».

# Failure Scenarios

1. 날짜를 UTC 로 잘라 KST 운영자에게 «오늘» 이 하루 밀린다 — ④ 와 시험이 막는다.
2. 이 칸의 실패가 개요 전체를 저하시킨다 — 독립 degrade 시험.
3. 빈 날을 건너뛴 축이 추세를 왜곡한다 — Edge Case 2 의 결정 + 시험.
