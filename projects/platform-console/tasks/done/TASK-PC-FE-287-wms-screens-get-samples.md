# Task ID

TASK-PC-FE-287

# Title

WMS 화면이 샘플로 선다 — 개요·입고·재고·마스터·작업·출고 (`ADR-MONO-074` 실행 6/8)

# Status

done

# Owner

platform-console

# Task Tags

- code
- test
- demo

---

# Goal

`TASK-PC-FE-282` 샘플 모드 위에서 **wms GET 을 전부 `ready`** 로 만든다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 283~288 직렬 머지.

화면: `/wms` · `/wms/guide`(정적) · `/wms/inbound` · `/wms/inventory` · `/wms/master` · `/wms/operations` · `/wms/outbound`

코어: `callWmsGateway` (admin read-model · outbound). ADR 인벤토리: GET **11** · 쓰기 **6**.

---

# Scope

## In Scope

- 위 화면의 wms GET 픽스처 + 원장 `ready`
- 🔴 wms 는 **NESTED 에러 봉투**(`{ error: { code } }`)다 — 샘플 라우터의 거부/준비 중 응답이 이 도메인에서는 그 모양이어야 코드가 보존된다

## Out of Scope

- 피킹·패킹·출고 확정 — `SAMPLE_READ_ONLY`

---

# Acceptance Criteria

- [x] **AC-0** 282 AC-0 표·원장에서 wms `pending` GET 목록을 이 파일에 적는다. — § Implementation notes "AC-0 — 재인벤토리" (3 surface; ADR 11 은 다른 단위).
- [x] **AC-1** 전부 `ready` + 실제 파서 통과 테스트. — `tests/unit/sample-fixtures-schema-wms.test.ts` (3 surface, 19 GET 리터럴 전부, 실제 프로덕션 zod 스키마로 파싱).
- [x] **AC-2** «(샘플)» 규칙 테스트 초록(창고명·SKU 이름·거래처명 끝). 🔴 `locationCode`·`skuCode`·`lotNo`·`warehouseCode`·수량·상태 enum 제외. — `sample-label-rule.test.ts` 의 `it.each(SAMPLE_FIXTURE_DOCUMENTS)` 가 wms 문서 3개를 순회, 초록. 새 키 분류는 `label-rule.ts` TASK-PC-FE-287 절.
- [x] **AC-3** 🔴 **코드 칸이 `null` 이 아니다** — `TASK-MONO-675` 가 라이브에서 본 «필드는 있는데 값이 전부 null» 을 샘플이 재현하지 않는다. 재고 행의 코드가 마스터 픽스처 안에서 해석된다. — § Implementation notes "AC-3" 절 + 코드 해석 표, bite B1.
- [x] **AC-4** `SAMPLE_READ_ONLY` · `SAMPLE_NOT_READY` 가 wms 코어(`parseWmsError`)를 거쳐 **코드가 보존되는지** 테스트(평면 봉투로 주면 `HTTP_403` 으로 뭉개진다). — `sample-fixtures-schema-wms.test.ts` "AC-4" 절, NESTED(wms/wms_outbound) + FLAT(wms_outbound_logistics) 양쪽. bite B4/B5.
- [x] **AC-5** `X-Read-Model-Lag-Seconds` 는 샘플에서 **안 싣는다**(지연 힌트는 사실이 아니므로) — 헤더 부재 시 `lagSeconds=null` 이 화면에 거짓 경고를 안 띄우는지 확인. — `sample-fixtures-schema-wms.test.ts` "AC-5" 절.
- [x] **AC-6** 대표 쓰기 1개(출고 확정) → «샘플 화면에서는 실행되지 않습니다». — `sample-fixtures-schema-wms.test.ts` "AC-6" 절: POST `/orders/{id}/shipments` → 403 `SAMPLE_READ_ONLY` → `messageForCode` 문구 정확히 일치.
- [x] **AC-7** `e2e-smoke` 익명 `/wms/inventory` 렌더 1칸. — `e2e-smoke/sample-visitor-wms.spec.ts`, `pnpm e2e:smoke` rc=0 (24 passed, 신규 1건 포함). 행 수는 단언하지 않음.
- [x] **AC-8** 🔴 **첫 화면 개요의 WMS 카드가 이 픽스처와 맞는다** (`TASK-PC-FE-285` CORRECTION 에서 추가). 지금 `fixtures/dashboards.ts` 의 wms 카드는
      손으로 적힌 값(«총 재고» `48210` · «알림» `3`)이다. console-bff 가 wms 카드에 부르는 조회를 **어댑터 코드에서** 확인하고, 같은 경로를 이 티켓의
      wms 픽스처 핸들러에 물어 카드 값을 **파생**한다 — 총 재고는 `/wms/inventory` 의 수량과, 알림은 그 화면이 보이는 알림과 같아야 한다.
      `tests/unit/sample-overview-cards-match-lists.test.ts` 에 칸을 더하고 bite. 🔴 wms 는 NESTED 에러 봉투다.
      — `WmsInventoryReadAdapter.read()`(console-bff)가 부르는 정확히 그 경로(`GET /api/v1/admin/dashboard/inventory`, 파라미터 없음)를
      wms 픽스처에 물어 총 재고(Σ`onHandQty`)와 알림(`lowStockFlag` 행 수)을 파생. 카드 스키마(`WmsDataSchema`)가 실제 어댑터 응답 모양과
      다른 기존 결함(286 D2 와 같은 부류)은 재현하지 않되 고치지도 않음 — § Implementation notes 참고. bite B3.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `TASK-PC-FE-282` (기반)
- `tasks/ready/TASK-MONO-675-the-denormalised-codes-are-null-because-the-ref-tables-are-empty.md` (라이브 결함 — 샘플이 그것을 **가리지도 재현하지도** 않는다)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5 (wms)

# Edge Cases

- 출고 경로는 per-call baseUrl override 를 쓴다 — 샘플 라우터 매칭이 base 가 아니라 **경로**로 되는지 확인.

# Failure Scenarios

- 평면 봉투 거부 응답 → 코드 소실 → 거부 문구 대신 일반 에러 ⇒ AC-4.

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [x] 원장의 wms `pending` 0 — `coverage.ts` `SURFACE_COVERAGE`/`SCREEN_COVERAGE` 의 wms 행 전부 `ready` (3 surface + 6 screen; `/wms/guide` 는 이미 282 소유의 `static`).

분석=Opus 5 / 구현 권장=Sonnet 5.

---

# Implementation notes (구현 에이전트, 2026-09-17 UTC)

## AC-0 — 재인벤토리

`TASK-PC-FE-282`~`286` 은 원장 granularity 를 엔드포인트가 아니라 **surface**(게이트웨이 프로필
`logPrefix`)로 잡았다(282 D1) — 이 티켓도 그대로 따른다. `coverage.ts` 의 wms `pending` 3 surface 를
그대로 인용한다:

| surface | 코드상 정의 | 화면 | GET (`method: 'GET'` 리터럴 스윕) |
|---|---|---|---:|
| `wms` (`callWmsAdmin`) | `wms-client.ts` (admin-service 읽기 전용 read-model) | `/wms` · `/wms/inbound` · `/wms/inventory` · `/wms/master` · `/wms/operations` · `/wms/outbound`(택배/출고 read 섹션) | alerts(1) + refs+projection-status(2) + inventory·by-key·throughput·orders(4) + settings·setting(2) + shipments·asns·inspection·adjustments(4) = **13** |
| `wms_outbound` (`callOutbound`) | `outbound-client.ts` (outbound-service 주문 라이프사이클) | `/wms/outbound`(주문 오퍼레이션 본체) | orders·order·saga·picking-requests(4) + admin-shaped shipment resolver(1, `outbound-shipment-api.ts` — **경로가 `/api/v1/admin/...`** 인데 surface 는 여전히 `wms_outbound`, Edge Case) = **5** |
| `wms_outbound_logistics` (`callScmGateway`→`callFlatEnvelopeGateway`) | `outbound-logistics-api.ts` | `/wms/outbound`(발송 재시도 액션의 조회 절반) | dispatches/by-shipment(1) = **1** |

합계 GET 리터럴 **19** — 🔴 ADR 본문의 **11** 과 단위가 다르다(ADR 은 "호출 함수 이름" 코드 읽기
스윕, 위 표는 "이 티켓이 실제로 만든 핸들러 분기" 세기 — 282~286 이 이미 기록한 "단위가 다르다"
원칙을 그대로 따른다). 이 티켓의 **실제 범위는 원장의 wms `pending` 3 surface**이고, `SCREEN_COVERAGE`
의 wms `pending` 화면은 6개(`/wms` · `/wms/inbound` · `/wms/inventory` · `/wms/master` ·
`/wms/operations` · `/wms/outbound`) — 작업지시서 나열과 일치(`/wms/guide` 는 이미 282 소유의
`static`).

## 🔴🔴 발견한 결함 — `wms_outbound_logistics` 의 `core` 가 282 부터 틀려 있었다

`coverage.ts` 의 이 행은 `{ core: 'wms', surface: 'wms_outbound_logistics', … }` 로 정의돼 있었다.
그런데 `outbound-logistics-api.ts`(발송 재시도 액션)의 `LOGISTICS_PROFILE` 은 `callScmGateway` →
`callFlatEnvelopeGateway` 를 거치고, 그 코어는 `sampleGate({core: 'flat', …})` 로 묻는다 —
`callWmsGateway` 는 이 호출 경로에 아예 없다. `findSurfaceCoverage` 는 `(core, surface)` **두 필드
모두** 비교하므로, `core: 'wms'` 행은 실제 요청(`core: 'flat'`)과 **영원히** 매치되지 않는다 —
`status` 를 무엇으로 적어도 이 표면은 구조적으로 `ready` 가 될 수 없었다. `pending` 상태에서는
"매치 안 됨"과 "진짜 pending" 이 똑같이 503 을 내서 드러나지 않았다. 이 티켓에서 `core: 'flat'` 로
고쳤다 — `fixtures/wms.ts` 모듈 헤더 + `coverage.ts` 인라인 주석에 근거를 남겼고,
`sample-fixtures-schema-wms.test.ts` "AC-0" 절 + bite B4 로 회귀를 잡는다. `sample-coverage-ledger.test.ts`
의 기존 범용 가드("ledger promises what the router does")는 **이 결함을 못 잡는다** — 그 테스트는
항상 행 자신의 `core` 필드로 `sampleResponse` 를 부르므로, 자기 자신과 일관되게 틀린 행은
"라우터가 원장이 약속한 대로 답한다"를 통과한다(스스로에게는 정직하다).

## 스크린 ↔ 서페이스 매핑 (구현 중 실측)

- `/wms` → `getWmsSectionState`(alerts, `wms`) + `getWmsOverviewState`(inventory·orders·shipments·alerts
  fan-out, 전부 `wms`) — 이미 위 3 surface 로 커버.
- `/wms/inbound` → `wms`(asns list, `getWmsInboundState`).
- `/wms/inventory` → `wms`(inventory list + by-key, `getWmsInventoryState` + client 훅).
- `/wms/master` → `wms`(refs/{type}, `getWmsMasterState` — 서버는 `locations` 탭만 시드, 나머지
  5종은 클라이언트 훅 `useWmsRefs` 가 탭 전환 시 요청).
- `/wms/operations` → `wms`(settings + projection-status, `getWmsOperationsState` — 두 독립 fan-out
  cell).
- `/wms/outbound` → **두 surface**: `wms_outbound`(주문 오퍼레이션 본체, `getOutboundSectionState`) +
  `wms`(택배/출고 read 섹션, `getWmsShipmentsState`) — 클라이언트의 "발송 재시도" 액션(쓰기, out of
  scope)만 `wms_outbound_logistics` 를 만진다.

## 픽스처 세계 (하나로 엮은 세 read-model)

창고 1(`WH01`) · 존 2(A동 상온·B동 냉장) · 로케이션 3 · SKU 3(사과=LOT 추적·바나나=NONE 추적·
생수=LOT 추적) · 랏 2 · 거래처 2(SUPPLIER·CUSTOMER) — 전부 `admin-service-api.md` § 1.7 /
`domain-model.md` § 5 의 필드 이름을 그대로 썼다. 재고 3행(사과 100·바나나 5[저재고 유일]·생수
52) · 처리량 1건 · 주문 **4건**(PICKING·PICKED·SHIPPED·CANCELLED, `outbound-service-api.md` 실제
enum) — 관리자 read-model 의 `dashboard/orders`(RECEIVED/SHIPPED/CANCELLED 로 collapse, `overview-
state.ts` 자신의 주석)와 배송 1건 · 디스패치 1건은 **이 4주문에서 파생**된다(손으로 두 번 안 적음 —
아래 § 편차 D1). ASN 2건(RECEIVED·CLOSED, 후자만 검수 완료) · 재고조정 2건 · 알림 2건(바나나 저재고,
미확인·확인 각 1) · 설정 2건 · 프로젝션 상태 2개 토픽.

## 편차 / 구현자 선택

- **D1 — admin `dashboard/orders` 는 outbound 주문에서 파생, 손으로 안 적는다.** 285/286 이 이미
  쓴 "손 대신 파생" 원칙의 이 도메인 적용. `ADMIN_ORDERS = OUTBOUND_ORDERS.map(...)`, 상태는
  `collapseStatus()` 함수로 계산(`overview-state.ts` 자신이 문서화한 RECEIVED/SHIPPED/CANCELLED
  collapse). `SAMPLE_WMS_SHIPMENTS`(admin `dashboard/shipments`) 도 SHIPPED 주문에서 파생 —
  `totalQty` 는 그 주문 라인의 실제 합. `SAMPLE_WMS_DISPATCH.shipmentId` 도 그 배송 id 를 그대로
  참조. bite B2(collapseStatus 파괴) 로 이 파생이 실제로 지켜지는지 확인.
- **D2 — 관리자 화면과 출고 화면이 SKU 코드를 공유.** `OutboundOrderLineSchema.skuCode` 는
  producer 상 nullable(백필 전 주문은 null — `TASK-MONO-659` 주석)이지만, 이 샘플 세계엔 "백필 전"
  이 없으므로 전부 `SKUS[i].skuCode` 에서 직접 읽어 채웠다(AC-3 의 정신을 재고 행 밖으로 확장 —
  `sample-fixtures-schema-wms.test.ts` "outbound order lines also carry a resolved skuCode").
- **D3 — outbound 의 admin-shaped shipment 조회는 `wms_outbound` surface 가 직접 답한다(Edge
  Case).** `resolveShipmentIdForOrder`(구 `outbound-tms-api.ts`, 현 `outbound-shipment-api.ts`)는
  `callOutbound`(profile `wms_outbound`)를 **admin base URL 로 override** 해서 부른다 — `logPrefix`
  는 그대로 `wms_outbound`. 그래서 `/api/v1/admin/dashboard/shipments?orderId=…` 라는 admin 모양의
  경로가 `wms:wms_outbound` 키 아래서 응답돼야 한다. 라우터는 **경로**로만 매치하므로(코어/서페이스는
  요청이 실어온 값), 이 픽스처는 `shipmentsQueryFixture()` 를 `wms` 와 `wms_outbound` 두 디스패처가
  **공유**하도록 짜서 두 진입점이 같은 데이터를 본다(중복 정의 없음). bite 로는 안 넣었지만
  `sample-fixtures-schema-wms.test.ts` "Edge Case" 절이 두 경로의 응답이 `toEqual` 인지 직접 비교한다.
- **D4 — `master-state.ts` 는 `locations` 탭만 서버 시드.** 나머지 5개 ref 타입(warehouses·zones·
  skus·lots·partners)은 픽스처에 전부 있지만(AC-1 이 6종 전부를 테스트), 서버 컴포넌트는
  `DEFAULT_REF_TYPE='locations'` 만 최초 렌더에 요청한다 — 실제 화면 동작 그대로 재현했을 뿐, 픽스처
  범위를 좁히지 않았다.
- **D5 — 결재/위임 같은 "내가 만든 caller" 관례가 이 도메인엔 없다.** wms 의 읽기 표면 중 JWT 의
  caller 를 구별해 필터링하는 것은 없다(alerts/inventory/orders 전부 warehouse-global 이거나 쓰기
  전용 필터만 받는다 — `admin-service-api.md` § 1.0). 그래서 285 의 D1(「나」 상수) 같은 장치가
  필요 없다.
- **D6 — `message`/`customerName`/`supplierName`/`reasonNote` 를 새로 분류.** `label-rule.ts` 에
  이유와 함께 추가(§ Related 참고). `locationCode`/`skuCode`/`lotNo`/`warehouseCode` 를 포함해 새
  MACHINE 키 29개, HUMAN_READABLE 키 4개 — 이 두 표(§ label-rule.ts TASK-PC-FE-287 절)가 근거다.

## AC-3 — TASK-MONO-675 재현하지도 가리지도 않는다

`InventoryRowSchema` 의 4개 코드 필드는 전부 `LOCATIONS`/`SKUS`/`LOTS`/`WAREHOUSE` **참조 배열에서
직접 읽어** 채웠다(리터럴 이중 기입 없음 — `fixtures/wms.ts` INVENTORY 배열 헤더 주석). 라이브
결함(다섯 ref 테이블이 전부 0건이라 코드가 전부 null)을 **재현하지 않는다**(코드가 실제로 있다) —
그렇다고 "이름 확인 불가" 상태를 **가리지도** 않는다(그 문구가 뜨는 조건 자체가 이 세계에 없다 —
결함이 아니라 결함의 부재를 정확히 표현). `sample-fixtures-schema-wms.test.ts` "AC-3" 절이 재고 4
필드 + 출고 주문 라인의 skuCode 까지 router 를 통해 마스터 픽스처와 대조한다. bite B1(locationCode
리터럴 파괴)로 발화 증명.

## ⚪ 측정하지 못한 것 / 넘길 의무

- **`/wms/master` 의 5개 비-`locations` ref 탭**(warehouses/zones/skus/lots/partners)은 픽스처가
  있고 AC-1 이 6종 전부를 직접 검증하지만, 클라이언트 훅 `useWmsRefs` 를 통한 실제 탭-전환 렌더는
  `sampleResponse()` 직접 호출로만 검증했다 — Playwright 로 그 클릭 경로까지 구동하는 e2e 는 AC-7
  ("1 칸")을 넘는 범위라 추가하지 않았다(283/284 의 동일 선례).
- **`/wms/outbound` 의 발송 재시도(retry-dispatch) 성공 경로**는 쓰기라 범위 밖 — AC-6 이 확인 대화상자
  까지가 아니라 최종 제출 거부만 요구하므로, 그 거부(403 `SAMPLE_READ_ONLY`)만 확인했다.
- **실제 Vercel 배포에서 샘플 방문자** — 로컬 production build + smoke 까지만 쟀다(283~286 과 동일
  한계).
- 넘길 의무 **0건** — 이 티켓이 임시로 들고 있던 남의 미해결 작업 없음.

## 다음 티켓(TASK-PC-FE-288)에 남기는 메모 — pending e2e 예시 소진

`e2e-smoke/sample-visitor.spec.ts` 의 "🔴 pending 화면 → 셸은 서고 «준비 중»" 칸을 `/wms` → `/scm`
으로 재조준했다(이 티켓이 `/wms` 를 `ready` 로 만들었으므로). **288 이 scm 의 GET 을 전부 `ready` 로
만들면, 이 칸이 가리킬 실제 pending 화면이 하나도 안 남는다.** 이 자리에서 288 의 문제를 풀지
않았다 — 스펙이 명시한 대로, 다음 사람이 할 일만 스펙 파일(`e2e-smoke/sample-visitor.spec.ts`)의
주석에 적어 두었다: **실제 라우트가 아니라 `SampleScreenNotice` 컴포넌트의 유닛 테스트**(합성
`pending` 원장 행을 그 컴포넌트에 직접 주입)로 재홈해야 한다는 처방까지 남겼다.

## 게이트 밖 검색 — 이 부류가 다시 조용히 지나가는 걸 막는 다른 wms/outbound 테스트 무영향 확인

`wms-api.test.ts`/`outbound-api.test.ts`/`wms-proxy.test.ts`/`outbound-proxy.test.ts` 는 **282 가
이미** 헤더 주석("ADR-MONO-074 (TASK-PC-FE-282) changed an expectation …")과 함께 "빈 쿠키 병=세션
없음"을 반쪽 세션(운영자 쿠키만)으로 바꿔 뒀다 — 이 티켓이 wms 표면을 `ready` 로 뒤집어도 그
파일들은 **무수정 초록**이다(grep 으로 확인, `## 측정` 절 참고). `wms-*-state.test.ts`/
`outbound-*.test.ts` 계열은 `getWms*State(eligible)` 를 직접 목으로 불러 세션/쿠키를 아예 거치지
않으므로 원천적으로 무관하다.

## 측정 (이 워크트리 · Windows 호스트 · 각 게이트 독립 실행 + 명시 rc)

BEFORE 는 이 워크트리의 분기점(`origin/main` = `c129d936f`, `TASK-PC-FE-286` 의 머지 커밋 그 자체)
에서, 어떤 편집도 하기 전에 쟀다.

| 게이트 | 트리 | 결과 |
|---|---|---|
| `pnpm test` | BEFORE(워크트리, 편집 전) | rc=1 · **312 files(2 failed) / 3439 tests(5 failed)** — 전부
  기지 Windows 타이밍 flake: `LedgerOpsScreen.test.tsx`(4 셀) · `OperatorsScreen.test.tsx`(1 셀,
  "a valid create is reason+confirm-gated") — 이 티켓과 무관한 화면(finance/ledger·iam) |
| `npx tsc --noEmit` | AFTER(최종) | rc=0 (1차 시도부터 통과 — 재작업 없음) |
| `pnpm lint` | AFTER(최종) | rc=0 · «No ESLint warnings or errors» (1차 시도부터 통과) |
| `pnpm test` (1차 — 구현 직후) | AFTER | rc=0 · **313 files / 3479 tests 전부 통과** — 기존 테스트
  수정 0건 필요(282 의 D8 대량 패치가 이미 wms/outbound 전 도메인 테스트를 선제 커버해 둔 덕분;
  `sample-mode-cores.test.ts` 의 pending-GET 제네릭 셀 1개만 선제적으로 `no-such-surface` 패턴으로
  교체) |
| `pnpm test` (최종 — bite 복원 후) | AFTER | rc=0 · **313 files / 3479 tests 전부 통과**
  (Duration 153.24s) — 기지 flake 도 이 실행에선 재현 안 됨(비결정적) |
| `pnpm build` | AFTER(최종) | rc=0 |
| `pnpm e2e:smoke` | AFTER(최종, 프로덕션 빌드, 백엔드 전부 loopback 127.0.0.1:1) | rc=0 ·
  **24 passed** — 기존 23개(기존 pending 칸을 `/scm` 로 재조준, 값 불변) + 신규
  `sample-visitor-wms.spec.ts` 1개(AC-7) |

🔵 **BEFORE 실행에서 관측된 flake 는 "재실행 1회"를 별도로 요구하는 게 아니다** — AFTER 두 번의
전체 스위트 실행(구현 직후 · bite 복원 후) 모두 0 실패였으므로, flake 가 실제로 재발했다면 그
경로에서 잡혔을 것이다. 재현되지 않아 개별 파일 재실행은 생략했다(메모리에 이미 기록된 기지
flake — `OperatorsScreen`/`LedgerOpsScreen`/`DelegationScreen`).

### AC-3 — 재고 코드 해석 표 (router 가 실제로 돌려주는 값)

| 재고 행 | locationCode | skuCode | lotNo | warehouseCode | 해석 근거 |
|---|---|---|---|---|---|
| loc-1/sku-1/lot-1 | WH01-A-01-01-01 | SKU-APPLE-001 | L-20260501-A | WH01 | `/dashboard/refs/{locations,skus,lots,warehouses}` 각각과 일치 |
| loc-2/sku-2/(lot 없음) | WH01-A-01-01-02 | SKU-BANANA-002 | — (SKU 가 NONE 추적) | WH01 | 동일 |
| loc-3/sku-3/lot-2 | WH01-B-01-01-01 | SKU-WATER-003 | L-20260601-B | WH01 | 동일 |

### 교차뷰 불변식 표 (router 가 실제로 돌려주는 값)

| 사실 | admin read-model (`wms`) | outbound-service (`wms_outbound`) | logistics (`wms_outbound_logistics`) |
|---|---|---|---|
| 주문 4건의 상태 | RECEIVED·RECEIVED·SHIPPED·CANCELLED (`dashboard/orders`) | PICKING·PICKED·SHIPPED·CANCELLED (`/orders`) | — |
| 미출고 주문(status=RECEIVED) | **2**(`?status=RECEIVED` totalElements) | (PICKING+PICKED = 2, 같은 부분집합) | — |
| SHIPPED 주문의 배송 | `ship-sample-0001`, orderId=`ord-sample-0003`, totalQty=20 | 같은 주문의 라인 합 = 20 | 디스패치 `shipmentId`=`ship-sample-0001` (일치) |
| admin-shaped shipment 조회(`?orderId=ord-sample-0003`) | 200, 1건(`wms` 서페이스로도) | 200, 동일 1건(`wms_outbound` 서페이스, Edge Case) | — |

### Bites (금지/결함 코드를 넣어 빨강 → 되돌려 복원 트리에서 초록)

| # | 가드 | 주입 | 빨강 | 복원 후 |
|---|---|---|---|---|
| B1 | AC-3 코드 해석(재고↔ref 대조) | `INVENTORY[0].locationCode` 를 `'BITE-B1-WRONG-CODE'` 로 | rc=1 · 1 file / 1 failed(AC-3 "codes resolve" 셀) | rc=0 · 36/36 |
| B2 | 교차뷰 상태 collapse(admin↔outbound) | `collapseStatus()` 를 항상 `'RECEIVED'` 반환하도록 파괴 | rc=1 · 2 files 중 1 failed / 2 cells 실패(collapse 셀 + 미출고 부분집합 셀) | rc=0 · 41/41 |
| B3 | 개요 WMS 카드 = `/wms/inventory` 파생(AC-8) | `dashboards.ts` wms 카드를 옛 하드코딩 값(`48210`)으로 되돌림 | rc=1 · 1 file / 1 failed | rc=0 · 5/5 |
| B4 | `wms_outbound_logistics` 의 `core` 가 `flat`(발견한 결함의 회귀 가드) | `coverage.ts` 행을 `core: 'wms'` 로 되돌림 | rc=1 · 2 files / **9 cells** 실패(AC-0·AC-1·Cross-view·coverage-ledger 전부) | rc=0 · 81/81 |
| B5 | FLAT 봉투가 NESTED 로 뭉개지면 코드 소실(Failure Scenario) | `router.ts` `errorBody()` 를 항상 NESTED 반환하도록 파괴 | rc=1 · 1 file / 2 failed(logistics 404 + write 코드 위치) | rc=0 · 36/36 |

B1–B5 모두 단독 주입 → 실행 → 복원 순으로 개별 확인했다. `grep -rn "BITE-" src tests e2e-smoke` =
0건(복원 확인). 전체 스위트 기준 최종 복원 확인은 § 측정의 "최종" 행.

### 병합 트리 관점 — DoD 대조군 (로그인 운영자 경로)

이 티켓이 건드린 파일은 `coverage.ts`(3행 + core 수정 1건) · `label-rule.ts`(키 분류 추가만) ·
`fixtures/index.ts`(배럴 추가만) · `fixtures/wms.ts`(신규) · `fixtures/dashboards.ts`(wms 카드
파생) · `sample-mode-cores.test.ts`(wms pending-GET 제네릭 셀 1개, `logPrefix` 만 교체·단언 불변) ·
`sample-overview-cards-match-lists.test.ts`(신규 WMS 셀 추가) · `e2e-smoke/sample-visitor.spec.ts`
(pending 예시를 `/wms`→`/scm`, 단언 문자열 불변) 뿐 — wms/outbound 화면 컴포넌트·hooks·api
클라이언트는 **한 줄도 안 바뀌었다**. 인증 운영자 경로 테스트(wms-api/wms-proxy/outbound-api/
outbound-proxy/wms-*-state/outbound-* 전부)는 무수정 초록이며, `pnpm test` 최종 실행이 그 안에
포함된 전체 스위트다.

## CORRECTION — 닫기 판정 (조정자, 2026-09-17 UTC)

머지 검증 4차원: (a) PR [#3890](https://github.com/kanggle/monorepo-lab/pull/3890) `state=MERGED` 2026-09-17T07:36:16Z ·
(b) squash `367976d37` 가 `origin/main` 조상(머지 시점의 끝) · (c) 머지 전 롤업 **62 체크 · FAILURE 0**(`f7ff6dbf5` 기준), 필수 4 + 프런트
unit · E2E smoke · console-bff IT **실제 실행** · (d) 아래 표.

| AC | 닫힘 | 증거 |
|---|---|---|
| AC-0 | ✅ | 표면 3 · GET 메서드 리터럴 19(13+5+1). ADR 의 11 은 단위가 달라 비교하지 않음 |
| AC-1 | ✅ | 19 GET 을 router 경유로 실제 zod 스키마에 파싱 |
| AC-2 | ✅ | 라벨 가드가 wms 문서 3개 순회 · 기계 키 29 · 사람 키 4 분류와 사유 |
| AC-3 | ✅ | 재고 행 코드 4종이 참조 표 배열에서 **읽혀** 들어가고(복제 리터럴 없음) router 로 `/dashboard/refs/{type}` 와 대조 · bite B1 |
| AC-4 | ✅ | `wms`·`wms_outbound` NESTED / `wms_outbound_logistics` FLAT — 코드 보존을 코어까지 · bite B4 · B5 |
| AC-5 | ✅ | 지연 헤더 부재 → `readWmsLagHeader()` null → `WmsLagHint` 미렌더 |
| AC-6 | ✅ | 출고 확정 POST → 403 `SAMPLE_READ_ONLY` → `messageForCode` |
| AC-7 | ✅ | `e2e-smoke/sample-visitor-wms.spec.ts` · 로컬 24 passed · PR CI E2E smoke SUCCESS |
| AC-8 | ✅ | WMS 카드 `totalStockUnits`·`alertCount` 를 `WmsInventoryReadAdapter` 경로로 파생 · «카드 = 재고» 가드 칸 · bite B3 |
| Edge | ✅ | outbound per-call baseUrl override — 같은 출하 조회가 `wms` · `wms_outbound` 두 표면에서 경로로 매칭(바이트 동일 행) |
| DoD | ✅ | 원장 wms `pending` 0. 기존 테스트: `sample-mode-cores` 1칸 설정만(`expect(` ±0) · smoke 경로 리터럴 1줄(`'/wms'`→`'/scm'`, 주제 불변) |

🔵 **조정자 확인 1 — 282 부터의 원장 버그는 실재했다**: `main`(`c129d936f`) 의 `coverage.ts` 는 `{ core: 'wms', surface: 'wms_outbound_logistics' }` 였고,
`features/wms-outbound-ops/api/outbound-logistics-api.ts` 는 `callScmGateway`(flat 코어)를 쓴다 ⇒ `sampleGate({ core: 'flat', … })` 가 그 행을 영영 못 찾았다.
282 의 원장 가드는 «행 자신의 core 로» router 를 부르므로 이 부류를 구조적으로 못 문다 — 에이전트가 전용 가드를 더했다(되돌리면 9칸 빨강).

🔴 **조정자 확인 2 — 리뷰가 찾은 로그인 운영자 경로의 결함(이 티켓 범위 밖)**: 에이전트가 AC-8 을 하며 «WMS 카드도 286 D2 와 같은 모양 불일치» 라고
적었다. 조정자가 대조했다 — `WmsInventoryReadAdapter` 는 `GET /api/v1/admin/dashboard/inventory` 본문(`{ content: [...], page, sort }`,
`wms-platform/specs/contracts/http/admin-service-api.md` § 1.1)을 그대로 싣고, web `WmsDataSchema` 는 `inventorySnapshot.{totalStockUnits, alertCount}` 를 읽는다
⇒ 로그인 운영자의 «총 재고»·«알림» 은 항상 `—` 일 것. **같은 방식으로 SCM 카드도 확인했다**(`{ data: { content }, meta }` ↔ `nodes`). IAM · ERP ·
E-Commerce 셋은 키가 맞는다. ⇒ **`TASK-PC-FE-295` 범위를 finance · WMS · SCM 으로 넓혔다**(이 종료 PR 에서 — 그 티켓 § 범위 확장 · AC-6 · AC-7).
🔴 WMS 는 모양뿐 아니라 **집계 의미**도 없다: producer 는 행 목록 첫 페이지를 주지 «총 재고 합» 을 주지 않는다 — 그 티켓에 적었다.

🔵 에이전트가 첫 실행에서 자기가 띄운 백그라운드 테스트를 기다리다 턴을 끝냈다(커밋·푸시 전) — 조정자가 재개시켜 완료. 결과물에는 영향 없음.

넘길 의무: WMS · SCM 카드의 운영 경로 결함 → `TASK-PC-FE-295`(티켓 안에 기록). «pending 화면» smoke 칸의 이사 → `TASK-PC-FE-288` 지시서에 반영. 그 밖 0건.
