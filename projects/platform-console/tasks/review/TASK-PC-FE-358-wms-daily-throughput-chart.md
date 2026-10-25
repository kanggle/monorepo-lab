# Task ID

TASK-PC-FE-358

# Title

WMS 개요에 일별 입고 · 출고 처리량 그래프 — 계약에 있고 API 클라이언트까지 있지만 BFF · 화면이 없는 `GET /dashboard/throughput` 를 잇는다

# Status

review

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

- [x] **AC-0** — ①~④ 를 file:line 과 함께 기록. ② 에서 콘솔 토큰이 통과하지 못하면 STOP → 서버 · 카탈로그 티켓 먼저(이 티켓은 그 뒤). → `## AC-0 측정` (② 통과, STOP 아님).
- [x] **AC-1** — 소유자 결정(기본 창고 · 기간 · 막대/선 · 수량/건수)을 선택창으로 받는다(추천과 함께). → `### 소유자 결정 (2026-10-22 UTC)`.
- [x] **AC-2** — 계약 먼저 갱신 → 구현. **사용자에게 보이는 새 문구(제목 · 범례 · 단위 · 빈 상태 · 오류 · 창고 선택 라벨) 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [x] **AC-3** — 시험 위 목록 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` rc=0. SSR 이 바뀌므로 **머지 전 브랜치에서 nightly 콘솔 잡** 실행 · 확인.
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

---

## AC-0 측정 (2026-10-22 UTC · 창 없음 · 분석=Opus 5.5)

> 경로 접두: 콘솔 = `projects/platform-console/apps/console-web/src/`, WMS = `projects/wms-platform/apps/`. 모두 이 브랜치(`origin/main` `7b068bf71` 기준)에서 다시 열어 확인했다.

**① `/wms` 개요가 지금 그리는 것 · 창고를 아는 방법**

- `app/(console)/wms/page.tsx:96-100` 이 `getWmsOverviewState` · `getWmsSectionState` · `getWmsBusinessRoles` 셋을 `Promise.all` 로 부른다.
- `features/wms-ops/api/overview-state.ts:161-204` = 독립 `cell()` 11개. `cell()` 은 `:116-128` — 403 → `forbidden`, 그 밖 → `degraded`, 401 은 다시 던져 `:258-263` 에서 `redirect('/login?error=session_expired')`. **어느 칸도 `warehouseId` 를 받지 않는다** — 개요는 창고 구분 없이 전체 합이다.
- 창고 목록은 SSR 이 받지 않는다. 클라이언트 `WmsRefPicker` → BFF `app/api/wms/master/refs/[type]/route.ts` → `GET /dashboard/refs/warehouses` 경로뿐이다.
- 창고 수: 샘플 = **1개**(`shared/sample/fixtures/wms.ts:142-150`, `wh-sample-0001` · `WH01`), 데모 시드 = **2개**(`wms-platform/apps/master-service/src/main/resources/db/seed/R__01_seed_dev_warehouse.sql:37-66`, `WH01` · `WH-MAIN`, 둘 다 `ACTIVE`).
- 🔵 **보정 1(이번 확인에서 새로 나옴)**: 생산자 refs 응답의 창고 코드는 **`code`** 필드다(`admin-service/.../api/dashboard/dto/MasterRefResponse.java:21-35`, `e.getWarehouseCode()` → `code`). 샘플 고정값은 `warehouseCode` 라는 다른 이름을 쓴다(`fixtures/wms.ts:144`). 콘솔은 `code ?? warehouseCode ?? id` 순서로 읽는다(`WmsRefPicker.tsx:134-143` 은 `code ?? id`).
- 🔵 **보정 2**: 샘플 창고 id `wh-sample-0001` 은 UUID 가 아니다. BFF 가 `warehouseId` 를 UUID 로 검사하므로 샘플에서는 BFF 를 부를 수 없다 — 샘플은 창고가 하나라 선택 칸이 없어 BFF 를 부르지 않는다(SSR 이 바로 그린다). 그래서 문제가 되지 않지만 기록해 둔다.
- refs 는 `status` 거르기를 받는다(`MasterRefController.java:61-77`, TASK-BE-630).

**② 권한 — 통과, STOP 아님**

- `admin-service/.../api/dashboard/ThroughputDashboardController.java:25` `@PreAuthorize("hasRole('WMS_VIEWER')")` — 콘솔이 이미 쓰는 다른 대시보드 컨트롤러와 같은 문턱이다.
- `admin-service/.../config/SecurityConfig.java:54-58` 계층 `SUPERADMIN > ADMIN > OPERATOR > VIEWER`, `:147-149` 에서 `entitled_domains ∋ wms` 이면 `ROLE_WMS_VIEWER` 를 합성 ⇒ **오늘 `/wms` 를 보는 운영자는 누구나 통과한다.**
- `shared/guide/permission-map.ts:719-737` 의 `/wms` 행(`gate: domain wms`, `writeRequires: WMS_OPERATOR`)은 바꿀 것이 없다 — 새 읽기는 같은 `WMS_VIEWER` 문턱이고, 행 설명 «재고·처리량·주문·알림 요약» 이 이미 처리량을 말한다.
- 판정: **통과**. 서버 · 카탈로그 선행 티켓 불필요.

**③ 데모 시드에 처리량 데이터가 있는가 — 없다(추론)**

- 데이터 = `admin_throughput_inbound_daily` / `admin_throughput_outbound_daily`(`admin-service/src/main/resources/db/migration/V2__init_readmodel.sql:369-398`).
- Kafka 로만 채워진다: `InboundProjectionService.java:240-261`(putaway 완료), `OutboundProjectionService.java:224-229`(출고 확정). SQL 시드는 없다.
- 데모 `infra/demo/seed/seed-wms.sh`(적치 확정 `:254-267` · `:346-354`, 출하 `:478-545`)는 시드를 돌린 **그 UTC 날짜**에 putaway · 출고를 만들고, 이미 있으면 건너뛴다(`:489`) — 다시 돌려도 새 날짜가 생기지 않는다 ⇒ 오래전에 구운 라이브 데모에서 최근 14일 창은 **비어 있을 가능성이 높다**. ⚪ 이것은 **추론이지 측정이 아니다**(라이브 창 없이 판정). 그래서 빈 기간 문구가 실제로 보일 화면이고, AC-4 의 판정 조건(«빈 그래프 문구가 보이는 것까지»)이 그대로 맞다.

**④ 날짜 기준 — 서버는 UTC 로 하루를 자른다, 빠진 날은 «없다»**

- `InboundProjectionService.java:246`, `OutboundProjectionService.java:228` — `effectiveAt.atZone(ZoneOffset.UTC).toLocalDate()`. 서버의 «하루» = **UTC 날짜**.
- `ThroughputDashboardController.java:69-92` — 행이 있는 날짜의 `TreeSet` 만 돈다 ⇒ **행이 없는 날은 `days[]` 에서 빠진다**(0 으로 채우지 않는다). 한쪽 표에만 행이 있는 날은 다른 쪽을 0 으로 채워 둘 다 싣는다(`:81-84`). `totals` 는 그 합. 범위는 `from..to` 양끝 포함 ≤ 90일(`:55-58`), `to < from` 은 400(`:52-54`).
- ⇒ 콘솔이 `from..to` 를 **0 으로 채워야** 가로축이 날짜를 건너뛰지 않는다.
- 콘솔 KST 도우미 = `features/wms-ops/api/kst-period.ts`(배송 오늘/주/월), 표시 = `shared/lib/datetime.ts`. 처리량은 서버가 UTC 로 자르므로 **KST 도우미를 쓰면 안 된다** — KST 자정~09시에 «오늘» 이 서버보다 하루 앞선다(Failure Scenario 1 의 반대 방향).
- 샘플 고정값 `fixtures/wms.ts:423-447` 은 질의를 무시하고 고정 `2026-09-01..15` · 2일치만 돌려준다.
- `ThroughputSchema`(`features/wms-ops/api/types.ts:82-104`)는 `inbound`/`outbound` 를 `z.record` 로 받아 이름 있는 필드가 아니다. `getThroughput`(`wms-inventory-api.ts:60-74`)은 소비자 0곳(시험 `wms-api.test.ts:353` 만).

**곁가지 발견 (범위 밖)** — 증가형 카운터의 upsert 가드 `WHERE last_event_at < EXCLUDED.last_event_at`(`ThroughputInboundDailyRepository.java:36`, `ThroughputOutboundDailyRepository.java:36`)는 순서가 뒤바뀌거나 같은 시각인 이벤트를 **버린다** ⇒ 과소 집계. 서버 결함이고 별도 WMS 티켓(메인 세션이 기안 중)으로 간다. 이 티켓은 서버를 바꾸지 않는다.

### 소유자 결정 (2026-10-22 UTC)

모두 추천안이고, 소유자가 선택창에서 골랐다.

- **(a) 기본 창고** — `ACTIVE` 창고가 2개 이상이면 선택 칸을 보이고, 기본값 = `warehouseCode` 오름차순 첫 `ACTIVE` 창고. 1개면 자동 선택 · 선택 칸 없음. 0개면 안내 문구.
- **(b) 기본 기간** — **UTC 오늘**로 끝나는 최근 14일. 화면에 «UTC 기준» 이라고 적는다.
- **(c) 모양 · 지표** — 막대. 기본 지표 = **건수**(입고 `putawayCount` 와 출고 `shipmentCount` 를 날마다 나란히 놓은 막대 쌍). 빠진 날은 0 으로 채운다. 수량(`qtyReceived`/`qtyShipped`)은 합계 글자로만.
- **(d) 서버 과소 집계** — 별도 WMS 티켓. 여기서는 다루지 않는다.

## 구현 (2026-10-22 UTC · Opus)

경로 접두 `projects/platform-console/apps/console-web/` 는 생략.

**커밋 1 — 계약 먼저** (`specs/contracts/console-integration-contract.md`, 코드보다 앞선 별도 커밋)
- § 2.4.5 에 라우팅 노트: op #3 을 `/wms` 개요 카드로 잇는다 · 새 같은 출처 읽기 프록시 `GET /api/wms/throughput` · upstream 전 400 조건 넷 · `mapWmsError` · 창고 후보는 op #11.
- § 2.4.5.2 에 «Daily throughput card»: 독립 저하 · 배치 · 두 읽기 순서 · UTC 창(KST 아님, 이유) · **0 채우기(규범)** · 무엇을 그리나 · 카드 단위 저하 · 범위 밖.

**커밋 2 — 코드 · 시험**
- `src/features/wms-ops/api/types.ts` — `ThroughputSchema` 를 `z.record` 에서 이름 있는 필드로 좁혔다. 그리는 두 건수(`putawayCount` · `shipmentCount`)는 필수, 수량은 선택, 나머지는 passthrough.
- `src/features/wms-ops/lib/throughput.ts`(새) — 순수 함수: `utcThroughputWindow(now)` · `enumerateIsoDates` · `zeroFillThroughput` · `sumThroughput` · `validateThroughputQuery`(프록시 400) · `activeWarehouseOptions`(ACTIVE · 코드 오름차순 · `code ?? warehouseCode ?? id`) · `barPercent`(최댓값 비율 · 2% 조각 · 0 나눗셈 방지).
- `src/features/wms-ops/api/throughput-state.ts`(새) — `getWmsThroughputState(eligible, now?)`. 창고 목록 → 0개면 처리량을 읽지 않음 → 첫 창고의 14일 UTC 창 → 0 채움. 403 → `forbidden`, 그 밖 → `degraded`, 두 읽기 어느 쪽이든 401 → `redirect('/login?error=session_expired')`. 처리량만 실패하면 창고 목록은 남겨 선택 칸이 계속 쓸 수 있다.
- `src/app/api/wms/throughput/route.ts`(새) — `master/refs/[type]/route.ts` 모양. 검증 실패 → `400 VALIDATION_ERROR`(upstream 호출 없음), 통과 → `getThroughput` → 생산자 본문 그대로, 오류 → `mapWmsError`.
- `src/features/wms-ops/hooks/use-wms-throughput.ts`(새) — 기본 창고가 아닐 때만 켜지는 `useQuery`(기본 창고는 SSR 값을 쓰므로 두 번 읽지 않는다), `retry: false`, 재조회 간격 없음.
- `src/features/wms-ops/components/WmsThroughputChart.tsx`(새) — CSS 세로 막대 쌍. 막대 `aria-hidden`, 날마다 화면낭독 문장 하나 + 보이는 숫자. 날짜 라벨은 ISO 문자열에서 바로 만든다(시간대 변환 없음 — KST 로 바꾸면 하루가 밀린다). 357 의 공용 원자에 기대지 않는다.
- `src/features/wms-ops/components/WmsThroughputCard.tsx`(새, `'use client'`) — 제목 · UTC 창 라벨 · 창고 2개 이상이면 `<select>` · 1개면 창고 이름 글자 · 본문(불러오는 중 / 창고 없음 / 권한 없음 · 점검 필요 / 빈 기간 / 막대 + 합계).
- `src/features/wms-ops/index.ts` — `WmsThroughputCard` · `getWmsThroughputState` · `WmsThroughputState` 내보냄.
- `src/app/(console)/wms/page.tsx` — `Promise.all` 에 넷째 읽기로 추가. 카드는 **`recentActivity` 칸의 맨 앞**에 그린다. 🔵 지시는 «개요 영역»이었지만, `overview` 칸에 넣으면 알림 분포와 알림 표 사이에 끼어 계약 § 2.4.5.2 Order 규칙(PC-FE-177, «알림 분포 + 표는 붙어 있다»)을 깬다. 추세는 «활동» 이라 활동 띠 맨 앞으로 옮겼고, 계약에도 같은 배치를 적었다. 기존 testid 는 하나도 바뀌지 않았다.
- `src/shared/sample/fixtures/wms.ts` — 🔵 **샘플 결정**: 고정값이 질의의 `from`/`to` 를 따르게 했다(0 채우기만으로는 고정 9월 창이 늘 비어 익명 샘플 화면이 영원히 빈 그래프가 된다). 유효한 ≤90일 창이면 그 창의 날마다 날짜 번호로 정해지는 결정적 합성 건수(같은 날짜 = 같은 숫자), **5일에 하루는 일부러 빼서** 실제 생산자처럼 빠진 날이 생기고 콘솔의 0 채우기가 화면에서 보인다. 모르는 `warehouseId` → 빈 `days`. 창이 없거나 틀리면 옛 고정 9월 답(라벨 가드 문서용).
- 🔵 데모의 기본 창고: 코드 오름차순은 **문자 코드 순서**라 `WH-MAIN`(`-` = 0x2D)이 `WH01`(`0` = 0x30)보다 앞선다 ⇒ 라이브 데모 기본값 = `WH-MAIN`(ecommerce 출고 창고). 시험이 이 순서를 고정한다(`wms-throughput-lib.test.ts`).

**시험 (새 파일 5개 · 68개 — lib 22 · state 15 · proxy 13 · card/chart 13 · sample 5)**
- `tests/unit/wms-throughput-lib.test.ts` — UTC 창(2026-10-22T23:30Z → 10-09..10-22 · 2026-10-22T00:30+09:00 → 10-08..10-21 · 해 넘김 · 14개), 날짜 나열, 0 채우기(빠진 날 · 창 밖 무시 · 한쪽 없음), 검증 400 여덟 경우 + 90일 통과, 창고 거르기 · 정렬 · 코드 대체, 막대 비율.
- `tests/unit/wms-throughput-state.test.ts` — 비자격(호출 0) · 창 전달 · KST 새벽 · 창고 0/1/2 · 200 0 채움 · 빈 `days` · 목록 403 · 목록 503 · 처리량 403(목록 유지) · 처리량 timeout · 처리량 400 · 401 둘 → redirect.
- `tests/unit/wms-throughput-proxy.test.ts` — 통과 시 IAM 토큰 · 변이 머리글 없음 · 질의 그대로, 400 여덟 경우(upstream 0회), 401 · 반쪽 세션 401 · 403 · 503.
- `tests/unit/WmsThroughputCard.test.tsx` — 막대 비율(100% · 50% · 2% · 0%) · 모두 0 · `aria-hidden` · 화면낭독 문장 · 날짜 라벨 · UTC 창 라벨 · 합계 글자 · 14칸 · 창고 1개(선택 칸 없음) · 빈 기간 · 창고 0개 · 권한 없음/점검 필요 · 비자격 · 2개 이상 선택 칸(기본은 재조회 안 함 → 바꾸면 프록시 재조회 · 0 채움) · 바꾼 뒤 403 은 본문만 교체.
- `tests/unit/sample-wms-throughput-window.test.ts` — 샘플이 창을 따른다 · 결정적 · 모르는 창고 → 빈 값 · 창 없음 → 옛 답 · 샘플 창고 1개.

**결과** (각 명령 로그 파일 · rc 직접 확인, 파이프 없음)
- `npx vitest run --maxWorkers=4 --minWorkers=2` → rc=0 · **417 files / 5030 tests 전부 통과**.
- `npx tsc --noEmit` → rc=0.
- `npx next lint` → rc=0(새 파일 경고 0 — 남은 경고는 기존 파일).
- e2e: `/wms` 를 여는 스펙은 `tests/federation-hardening-e2e/specs/wms-golden-path.spec.ts` 하나 — URL 과 첫 heading(`h1` «WMS 개요»)만 본다. 카드는 `h3` 이고 뒤에 오므로 깨지지 않는다. `e2e-smoke/sample-visitor-wms.spec.ts` 는 `/wms/inventory` 만. e2e 추가 없음.

## 새 문구 (소유자 승인 2026-10-22 UTC)

경로 접두 `projects/platform-console/apps/console-web/src/features/wms-ops/components/`. `{…}` 는 값이 들어가는 자리.

| file:line | 문구 | 언제 보이나 |
|---|---|---|
| `WmsThroughputCard.tsx:140` | `WMS 일별 입고 · 출고 처리량` (`aria-label`) | 카드 영역 이름(화면낭독) — 늘 |
| `WmsThroughputCard.tsx:145` | `일별 입고 · 출고` | 카드 제목 — 늘 |
| `WmsThroughputCard.tsx:150` | `최근 14일 · UTC 기준 ({from} ~ {to})` | 제목 아래 — 늘 (예: `최근 14일 · UTC 기준 (2026-10-09 ~ 2026-10-22)`) |
| `WmsThroughputCard.tsx:156` | `창고` | 선택 칸 라벨 — 활성 창고 2개 이상 |
| `WmsThroughputCard.tsx:177` | `창고 {코드} · {이름}` (이름이 없으면 `창고 {코드}`) | 활성 창고가 1개일 때 선택 칸 대신 |
| `WmsThroughputCard.tsx:77` | `불러오는 중…` | 창고를 바꾼 직후 응답 전 |
| `WmsThroughputCard.tsx:82` | `활성 창고가 없어 처리량을 표시할 수 없습니다.` | 활성 창고 0개 |
| `WmsThroughputCard.tsx:96` | `이 기간에 입고 · 출고 기록이 없습니다.` | 14일 모두 0 (③ 에 따라 라이브 데모에서 보일 가능성이 높음) |
| `WmsThroughputCard.tsx:52` | `합계 — 입고 {n}건 (수량 {q}) · 출고 {m}건 (수량 {q})` | 막대 아래 — 데이터가 있을 때 |
| `WmsThroughputChart.tsx:53` | `입고(적치 완료) 건수` | 범례 — 막대가 보일 때 |
| `WmsThroughputChart.tsx:57` | `출고(출하 확정) 건수` | 범례 — 막대가 보일 때 |
| `WmsThroughputChart.tsx:61` | `일별 입고 · 출고 건수` (`aria-label`) | 막대 목록 이름(화면낭독) |
| `WmsThroughputChart.tsx:71` (+ `:30`) | `{M}월 {D}일: 입고 {n}건, 출고 {m}건` | 날마다 하나, 화면낭독 전용(`sr-only`) |

새 문구가 아닌 것(참고): `권한 없음` · `점검 필요` 는 기존 공용 어휘 `src/shared/lib/overview-cell.ts:19` 를 그대로 쓴다(403 / 그 밖 실패). 막대 밑 날짜 `{M}/{D}` 와 숫자는 값이다. 선택 칸의 항목 글자 `{코드} · {이름}` 은 생산자 데이터다.

## 소유자 승인 · 머지 전 확인 (2026-10-22 UTC · 분석=Opus 5.5)

- 새 문구 13개 · 카드 위치(«최근 활동» 칸 맨 앞) = **소유자 승인**(선택창, 머지 전) → AC-2 닫힘.
- `TASK-PC-FE-357`(#4450 `d10f95e74`) 머지 뒤 rebase — `INDEX.md` 충돌 1곳(ready 칸: 357 은 review · 358 은 in-progress 로 갔으므로 `_(없음)_`). rebase 트리에서 vitest 418 files · 5061 tests · `tsc --noEmit` · `next lint` rc=0.
- 브랜치 nightly run `39636295024`(head `c04b575e5`) **success** — `Package platform-console e2e boot jars (nightly)` · `Platform Console E2E full-stack (Playwright + docker compose)` 둘 다 success → AC-3 닫힘.
- 🔴 PR 체크 2개 빨강(같은 head): `Client graph server-only imports` · `Client graph backend origins`. 원인 = 카드(`'use client'`)가 서버 전용 `api/throughput-state.ts` 를 **여러 줄 `import type { … }`** 로 가리켜, 가드의 그래프 걷기가 그 모듈 → `shared/lib/session.ts` → `next/headers` · `shared/config/env.ts` 의 백엔드 주소까지 닿았다. 수정 = 카드가 쓰는 두 타입(`ThroughputCardStatus` · `WmsThroughputState`)을 `lib/throughput.ts` 로 옮기고(`OverviewCellStatus` 기반) 상태 모듈은 다시 내보내기만. 로컬 두 가드 hits=0 · tsc · lint · vitest 위와 같음. 타입만 옮겼으므로 내보내는 JS 는 같다 — nightly 는 다시 돌리지 않았다.
- 🔵 가드 쪽 관찰(이 티켓 밖): 옛 그래프도 nightly 의 `next build` 는 통과했다 — `import type` 은 번들에 안 들어간다. `scripts/check-client-graph-server-only.mjs:69-70` 의 `TYPE_ONLY_RE` 는 **마지막 대상**(`next/headers`) 을 가리키는 타입 임포트만 거르고, 로컬 모듈 사이의 `import type` 연결은 따라간다 ⇒ 오탐. 루트 티켓 후보(소유자에게 보고).
- 열림: **AC-4**(Vercel Production 기록 · 익명 샘플 화면 · 로그인 운영자 라이브 = 다음 데모 창).

## 라이브 확인 (2026-10-23 UTC · 33차 창)

- 백엔드: 굽기 `283fd39cf` → AMI `ami-02b3ba00a3b2ff622`(51분 36초 · 태그 RepoCommit 일치) · 핀 PR #4498(`c5e3ddad3`) · 인스턴스 `i-0f39aec49ddbaa899`.
- **AC-4 🟡** — 로그인 WMS 개요의 «일별 입고·출고» 그래프가 14일 창(2026-10-10~23, UTC)으로 그려지고 빈 날은 0 — 10/23 입고 86.
- ⚪ «빈 14일 창 = 빈 문구» 경우는 이 데이터로는 잴 수 없다(창에 값이 있다). Vercel Production 기록 · 익명 샘플 화면은 이 창 결과에 따로 적히지 않았다.
- ⇒ 이 티켓은 `review` 에 남는다(체크 칸은 동결 규칙으로 고치지 않음).

---

## CORRECTION — 34차 창 라이브 결과 (2026-10-24 UTC)

- **AC-4 — 34차 창 결과 (열린 채 유지).** 34차 창(2026-10-24 UTC): 측정 안 함.

---

## CORRECTION — 35차 창 라이브 결과 (2026-10-25 UTC)

- **AC-4 ✅ — 로그인 · 익명 두 절반 모두.** 35차 창(소유자, Vercel production):
  - 로그인 `/wms`: «일별 입고 · 출고» 가 14 일(10/12 ~ 10/25)로 그려지고 빈 날은 0 이다. 10/25 입고 86 · 출고 2.
  - 익명 샘플: 같은 카드가 샘플 창고 WH01 값으로 그려진다.
  - 「빈 14일 창 문구」 경우는 ⚪ — 이 볼륨에는 오늘 데이터가 있다. AC-4 는 이 문구를 «시드가 비면» 이라는 조건으로만 판정에 넣었고(시드가 비지 않았으므로 그 조건이 성립하지 않았다), 따라서 이 ⚪ 는 AC 를 열어 두지 않는다고 읽는다.
- **관찰 — 위 수치의 측정 조건.** wms 알림 행이 1건 있을 때 로그인 `/wms` 개요 전체가 «wms 운영 정보를 일시적으로 불러올 수 없습니다» 로 저하됐다. 알림 행을 지운 뒤(소유자 결정, `dbexec --why`) 0행에서 카드가 그려졌고 위 수치는 그 상태에서 잰 것이다. 원인(콘솔 알림 스키마의 `alertId` 대 생산자 `id`)은 별도 PR 로 기안되는 새 결함이다.
- 배포: 원장은 소유자가 Vercel production 에서 화면을 확인했음을 적는다. 배포 id 는 원장에 없다 — 닫기 chore 가 Vercel Production 기록을 확인한다.

⇒ 판정: 모든 AC 닫힘 — 닫기 chore 대상 (AC-4 의 «Vercel Production 기록» 은 닫기 chore 에서 확인)
