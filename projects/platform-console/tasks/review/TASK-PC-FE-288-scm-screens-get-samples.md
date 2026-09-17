# Task ID

TASK-PC-FE-288

# Title

SCM 화면이 샘플로 선다 — 개요·조달·재고·보충 계획·보충 설정 (`ADR-MONO-074` 실행 7/8)

# Status

review

# Owner

platform-console

# Task Tags

- code
- test
- demo

---

# Goal

`TASK-PC-FE-282` 샘플 모드 위에서 **scm GET 을 전부 `ready`** 로 만든다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 283~288 직렬 머지.

화면: `/scm` · `/scm/guide`(정적) · `/scm/procurement` · `/scm/inventory` · `/scm/replenishment` · `/scm/config`

코어: `callScmGateway`(평면 봉투 코어 심 — 429 백오프 · 404-as-empty 센티널 · `X-Cache`). ADR 인벤토리: GET **10** · 쓰기 **4**.

---

# Scope

## In Scope

- 위 화면의 scm GET 픽스처 + 원장 `ready`

## Out of Scope

- 보충 정책 저장·제안 승인/기각 — `SAMPLE_READ_ONLY`

---

# Acceptance Criteria

- [x] **AC-0** 282 AC-0 표·원장에서 scm `pending` GET 목록을 이 파일에 적는다. — § Implementation notes "AC-0 — 재인벤토리" (3 surface; ADR 10 은 다른 단위).
- [x] **AC-1** 전부 `ready` + 실제 파서 통과 테스트. — `tests/unit/sample-fixtures-schema-scm.test.ts` (3 surface 전부, 실제 프로덕션 zod 스키마로 파싱).
- [x] **AC-2** «(샘플)» 규칙 테스트 초록(공급사명·품목명 끝). 🔴 SKU·공급사 코드·수량·상태 enum 제외. — `sample-label-rule.test.ts` 의 `it.each(SAMPLE_FIXTURE_DOCUMENTS)` 가 scm 문서 3개를 순회, 초록. § Implementation notes "이 도메인은 사람이 읽는 필드가 거의 없다" 참조 — 품목명 필드는 scm 스키마 어디에도 없다.
- [x] **AC-3** 🔴 발주의 공급사가 **UUID 로 보이지 않는다** — `TASK-MONO-677` 이 라이브에서 본 결함을 샘플이 재현하지 않되, 계약에 아직 없는 필드를 픽스처가 **지어내지도 않는다**(677 이 계약을 바꾸기 전이면 현재 계약 모양 + 공급사 목록 픽스처로 해석 가능한 범위만). — 677 이 이미 계약 + 콘솔 스키마 + `ScmPoTable`/`PoDetailDialog` 를 병합했음을 코드에서 확인, 그 위에서 id 매치·code 매치·미해석 3케이스 픽스처 + `masterRefLabel` 로 검증. bite B1.
- [x] **AC-4** `/scm/config` 의 404-as-empty: «설정 없음» 칸 하나를 픽스처로 **일부러** 두어 empty-state 가 보이는지 확인(센티널 경로가 샘플에서도 산다). — `SKU-SAMPLE-003` 을 policy/map 양쪽에서 의도적으로 비움 → `POLICY_NOT_FOUND`/`MAPPING_NOT_FOUND`. bite B3.
- [x] **AC-5** `X-Cache` 헤더는 샘플에서 싣지 않는다 — 부재 시 화면이 거짓 캐시 표시를 안 하는지. — 라우터의 `jsonResponse()` 가 애초에 그 헤더를 안 실음(구조적) + `readCacheHeader(res)` 가 `null` 을 반환함을 단언.
- [x] **AC-6** 대표 쓰기 1개(제안 승인) → «샘플 화면에서는 실행되지 않습니다». — `sample-fixtures-schema-scm.test.ts` "AC-6" 절: POST 제안 승인 → 403 `SAMPLE_READ_ONLY` → `messageForCode` 매핑 문구 정확히 일치.
- [x] **AC-7** `e2e-smoke` 익명 `/scm/procurement` 렌더 1칸. — `e2e-smoke/sample-visitor-scm.spec.ts`, `pnpm e2e:smoke` rc=0 (24 passed).
- [x] **AC-8** 🔴 **첫 화면 개요의 SCM 카드가 이 픽스처와 맞는다** (`TASK-PC-FE-285` CORRECTION 에서 추가). 지금 `fixtures/dashboards.ts` 의 scm 카드는
      손으로 적힌 노드 셋(`sample-node-01..03`, 평택·이천·부산)이다. console-bff 가 scm 카드에 부르는 조회를 **어댑터 코드에서** 확인하고, 같은 경로를
      이 티켓의 scm 픽스처 핸들러에 물어 카드 값을 **파생**한다 — 노드 수·노드 id·이름이 `/scm/inventory` 의 노드와 같아야 한다.
      `tests/unit/sample-overview-cards-match-lists.test.ts` 에 칸을 더하고 bite. — § Implementation notes "AC-8" 절 참조(console-bff 어댑터가 실제로는 scm 게이트웨이가
      아니라 producer 를 직접 부르는 경로/셰이프 불일치를 발견 — 재현하지 않고 295 로 넘김). `sample-overview-cards-match-lists.test.ts` 신규 scm 셀, bite B2.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `TASK-PC-FE-282` (기반)
- `tasks/ready/TASK-MONO-677-the-po-screen-shows-a-supplier-uuid-although-the-master-has-a-code-and-a-name.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.6 (scm)

# Edge Cases

- 샘플 라우터가 429 를 내지 않는다(백오프 경로 미진입) — 그 경로는 인증 테스트가 계속 지킨다.

# Failure Scenarios

- 677 머지 후 계약이 바뀌면 픽스처가 옛 모양 ⇒ AC-1 파서 테스트가 빨개져 알려 준다(의도된 결합).

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [x] 원장의 scm `pending` 0 — `coverage.ts` `SURFACE_COVERAGE`/`SCREEN_COVERAGE` 의 scm 행 전부 `ready` (3 surface + 5 screen; `/scm/guide` 는 이미 282 소유의 `static`). **이 티켓 이후 원장 전체에 `pending` 표면·화면이 0.**

분석=Opus 5 / 구현 권장=Sonnet 5.

---

# Implementation notes (구현 에이전트, 2026-09-17 UTC)

## AC-0 — 재인벤토리

`TASK-PC-FE-282`~`287` 은 원장 granularity 를 엔드포인트가 아니라 **surface**(게이트웨이 프로필
`logPrefix`)로 잡았다(282 D1) — 이 티켓도 그대로 따른다. `coverage.ts` 의 scm `pending` 3 surface 를
그대로 인용한다:

| surface | 코드상 정의 | 화면 | GET (`method: 'GET'` 리터럴 스윕) |
|---|---|---|---:|
| `scm` (`callScm`, `scm-client.ts`) | procurement PO read + inventory-visibility | `/scm`(개요 PO+스냅샷 fan-out) · `/scm/procurement` · `/scm/inventory` | po(list+detail)=2 + snapshot(cross-node+single-node)=1 + sku=1 + staleness=1 + nodes=1 = **6** |
| `scm_replenishment` (`callDemandPlanning`, `demand-planning-api.ts`) | demand-planning 제안 read(+ approve/dismiss 는 쓰기) | `/scm/replenishment` | suggestions(list+detail)=2 = **2** |
| `scm_config` (`callSeed`, `demand-planning-seed-api.ts`) | demand-planning 재주문정책+공급사매핑 seed GET(+PUT 은 쓰기) | `/scm/config` | policies+sku-supplier-map=2 = **2** |

합계 GET 리터럴 **10** — 🔴 ADR 본문의 **10**(procurement 2 + inventory-visibility 4 + demand-planning
suggestions 2 + seed 2)과 우연히 같은 숫자로 수렴하지만, 단위는 여전히 다르다(ADR 은 "호출 함수 이름"
코드 읽기 스윕, 위 표는 "이 티켓이 실제로 만든 핸들러 분기" 세기 — 282~287 이 이미 기록한 "단위가
다르다" 원칙을 그대로 따른다. 우연의 일치를 근거로 쓰지 않는다). 이 티켓의 **실제 범위는 원장의 scm
`pending` 3 surface**이고, `SCREEN_COVERAGE` 의 scm `pending` 화면은 5개(`/scm` · `/scm/config` ·
`/scm/inventory` · `/scm/procurement` · `/scm/replenishment`) — 작업지시서 나열과 일치(`/scm/guide` 는
이미 282 소유의 `static`).

## 스크린 ↔ 서페이스 매핑 (구현 중 실측)

- `/scm`(개요) → `getScmOverviewState` 가 `listPurchaseOrders`(`scm`, `?page=0&size=1` + 9종 상태별
  1건 + 최근 5건) + `getSnapshot`(`scm`, `?page=0&size=1`) 를 fan-out. 이미 `scm` surface 로 커버.
- `/scm/procurement` → `scm`(PO list+detail, `?page=0&size=20`).
- `/scm/inventory` → `scm`(snapshot cross-node list + staleness, `getSnapshot({page:0,size:20})` +
  `getStaleness()`). per-SKU breakdown(`getSkuBreakdown`)과 node list(`getNodes`, `/api/scm/nodes`
  프록시)는 이 화면에서 도달 가능한 클라이언트 훅/프록시로만 존재 — 실제 SSR 렌더는 스냅샷+신선도뿐이다
  (§ ⚪ 참조).
- `/scm/replenishment` → `scm_replenishment`(제안 list, `getReplenishmentSectionState`).
- `/scm/config` → `scm_config`(SKU 코드 입력 시에만 클라이언트가 policy+map 을 GET — 서버 사전 시드
  없음, `SeedConfigScreen` 이 완전히 클라이언트 구동).

## 픽스처 세계 (procurement + inventory-visibility + demand-planning, 하나로 엮은 scm 세계)

공급사 마스터(내부 해석 표, 콘솔이 직접 조회하는 GET 은 없다) 2 — 대한물산(`SUP-SAMPLE-01`)·한빛무역
(`SUP-SAMPLE-02`). 발주(PO) 4 — `po-sample-0001`(DRAFT, id 로 해석)·`po-sample-0002`(SUBMITTED,
from-suggestion 모양이라 **code** 로 해석)·`po-sample-0003`(CONFIRMED, UUID 모양인데 아무 공급사에도
안 걸림 → 미해석)·`po-sample-0004`(CANCELED, `po-sample-0001` 과 같은 공급사 재사용). 재고-가시성 노드
3(`node-sample-01/02/03` = 평택·이천·부산, 기존 dashboards.ts 하드코딩 이름 그대로 계승) · 스냅샷 행
4(3개 노드에 걸침, FRESH·FRESH·STALE·UNREACHABLE 각 1 이상) · 재주문 제안 4(SUGGESTED·APPROVED·
MATERIALIZED·DISMISSED 각 1, MATERIALIZED 건의 `materializedPoId` 는 실제 `po-sample-0002` 를
가리킨다) · 재주문정책+공급사매핑은 SKU 2종만 완비, `SKU-SAMPLE-003` 은 **의도적으로** 둘 다 비움(AC-4).

## 편차 / 구현자 선택

- **D1 — `TASK-MONO-677` 이 이미 프로덕션 계약·스키마·컴포넌트를 바꿔 놨다.** 이 워크트리를 읽어보니
  `procurement-api.md` 의 `PurchaseOrderResponse` supplier reference fields 절, 콘솔
  `PurchaseOrderSchema` 의 `supplierCode`/`supplierName`, `ScmPoTable`/`PoDetailDialog` 의
  `masterRefLabel` 호출이 이미 병합되어 있었다(677 은 AC-3 라이브 판정만 남기고 in-progress) — 그래서
  이 티켓은 그 프로덕션 코드를 **재현**만 하면 됐고, 새 필드를 지어낼 필요가 없었다(AC-3 문구가 경고한
  함정을 피함). 픽스처의 resolution 규칙(id 우선, 안 되면 code, 안 되면 둘 다 null — 절대 raw id 로
  안 돌아감)은 677 계약 § `PurchaseOrderResponse` — supplier reference fields 의 규칙 1~3 을 그대로
  옮겼다.
- **D2 — 이 도메인은 사람이 읽는 필드가 거의 없다(286 finance/ledger D1 과 같은 부류).** 작업지시서
  AC-2 는 "공급사명·품목명 끝"을 언급하지만, `PoLineSchema.sku` 는 코드이고 `SuggestionSchema` 에는
  이름 필드가 전혀 없다 — 실제로 사람이 읽는 값은 `PurchaseOrderResponse.supplierName`(677 이 추가한
  해석된 공급사 이름)과 inventory-visibility `NodeRow.name`(창고/3PL 시설 이름) **둘뿐**이다. 둘 다
  `label-rule.ts` 에 이미 등록된 키(`supplierName`/`name`)라 새 분류가 필요 없었다.
- **D3 — SKU breakdown 의 미매칭 SKU 는 404 가 아니라 진짜 "데이터 없음" 200.** per-SKU breakdown 은
  집계 쿼리이지 id 조회가 아니고, `inventory-visibility-api.md` § Error Codes 에 `SKU_NOT_FOUND` 류
  코드가 없다 — 그래서 미매칭 SKU 는 `{nodes:[], totalQuantity:0}` 의 200 으로 답했다(실제 프로듀서
  동작을 흉내). node-scoped snapshot(`?nodeId=`)은 반대로 **모르는 nodeId** 를 `404 NODE_NOT_FOUND` 로
  답한다 — Error Codes 표에 그 코드가 명시돼 있고, node 존재는 실제로 검증 가능한 참조이기 때문
  (procurement PO 상세의 `PO_NOT_FOUND` 와 같은 패턴).
- **D4 — `wms_outbound_logistics`/`scm-gateway.ts` 순환 초기화 함정(286 D3, 287 지시서에 반영)을
  scm.ts 에도 그대로 적용.** `fixtures/scm.ts` 는 `dashboards.ts`/`registry.ts`/`fixtures/index.ts`
  를 import 하지 않는다(파일 헤더에 명시). `dashboards.ts` 가 `scm.ts` 를 import 하는 단방향 edge만
  존재 — 286 이 겪은 순환 크래시가 구조적으로 발생할 수 없다.
- **D5 — `sugg-sample-0003` 이 BATCH 출처 + 미해석 공급사 + 미설정 SKU 를 하나로 묶는다.** 프로덕션
  `ReplenishmentTable.tsx`(TASK-MONO-683)는 이미 `warehouseCode` 부재(BATCH 출처 스키마 주석)와
  UUID-모양 `supplierId`(코드가 아님 → `이름 확인 불가`)를 각각 처리하는 코드를 갖고 있었다 — 이 픽스처는
  그 두 실제 분기를 **같은 행**에서 발화시키고, 그 행의 SKU(`SKU-SAMPLE-003`)를 AC-4 의 "의도적으로
  미설정" SKU 와 **같은 SKU** 로 만들어 "이 SKU 는 아직 준비가 덜 됐다"는 하나의 일관된 이야기로
  묶었다(손으로 두 번 안 적음).

## AC-8 — 개요 SCM 카드의 파생과 알려진 셰이프 불일치(295 로 이관)

`ScmInventoryReadAdapter.read()`(console-bff)는 scm 게이트웨이를 거치지 않고 producer 를 **직접**
`GET /api/inventory-visibility/snapshot`(`/v1` 없음, TASK-MONO-162 토폴로지 주석)로 부르고, 그 원시
응답(`{data:{content,...}|array, meta}`)을 그대로 leg 의 `data` 로 흘려보낸다. 콘솔 자신의
`ScmDataSchema` 는 `{meta:{warning}, nodes:[...]}` 라는 **다른** 모양을 기대한다 — top-level `nodes`
키가 producer 응답에 아예 없다. 이것은 286 D2(finance)·287 AC-8(wms)이 찾은 것과 **같은 부류**의
BFF-레그/FE-카드 셰이프 불일치이고, 코디네이터가 이미 `TASK-PC-FE-295` 로 scm 까지 스코프를 넓혔다(이
티켓 범위 밖 — 도메인 픽스처로는 실제 프로덕션 코드의 셰이프 버그를 고칠 수 없다). 그래서 이 픽스처는
`ScmDataSchema` 가 실제로 파싱하는 모양을 유지하고, **값**만 이 티켓의 scm 픽스처가 답하는 (자기
게이트웨이 관례상의) 스냅샷 쿼리에서 파생했다 — `dashboards.ts` 의 `SCM_OVERVIEW_NODES` 주석에 근거를
남겼다. 노드 id 집합은 `/scm/inventory` 가 실제로 그리는 스냅샷 행의 `nodeId` 들과 정확히 같고, 이름은
같은 픽스처의 `/nodes` 레지스트리에서 조회했다(둘 다 손으로 재입력하지 않음).

## ⚪ 측정하지 못한 것 / 넘길 의무

- **`/scm/inventory` 의 per-SKU breakdown·node 목록은 SSR 로 렌더되지 않는다** — per-SKU breakdown 은
  클라이언트가 SKU 를 입력해야 요청되고(`useScmSkuBreakdown`), node 목록(`getNodes`/`/api/scm/nodes`)은
  프록시 라우트만 있고 현재 어떤 클라이언트 훅도 그것을 부르지 않는다(고아 라우트로 보인다 — 이 티켓
  범위 밖). 두 경로 모두 `sampleResponse()` 직접 호출로 AC-1 테스트가 검증했다.
- **개요 SCM 카드 ↔ console-bff 셰이프 불일치**(§ AC-8) → `TASK-PC-FE-295`(코디네이터가 이미 scm 까지
  확장). 이 티켓의 픽스처/카드는 그 불일치를 재현도 은폐도 하지 않는다.
- **실제 Vercel 배포에서 샘플 방문자** — 로컬 production build + smoke 까지만 쟀다(283~287 과 동일
  한계).
- 넘길 의무 **0건** — 이 티켓이 임시로 들고 있던 남의 미해결 작업 없음.

## 다음(마지막) — 원장에 pending 이 0 이 된 뒤 소진된 예시들

이 티켓 이후 `SURFACE_COVERAGE`/`SCREEN_COVERAGE` 어디에도 `pending` 이 없다. 287 이 이 자리에 남긴
메모대로:

- `e2e-smoke/sample-visitor.spec.ts` 의 "pending 화면" 셀(`/scm` 을 가리키던 것)을 **제거**하고,
  `tests/unit/SampleScreenNotice.test.tsx`(신규)로 재홈했다 — `usePathname`/`screenStatusFor` 를
  모킹해 합성 `pending`/`ready`/`static` 응답을 직접 주입, 실제 라우트에 의존하지 않는다. bite B4.
- `tests/unit/sample-mode-cores.test.ts` 의 "① scm shim — pending GET" 셀은 `logPrefix: 'scm'`(이제
  ready)에서 283 D8 패턴의 `logPrefix: 'no-such-surface'` 로 교체(단언 불변).
- `tests/unit/sample-coverage-ledger.test.ts` 의 "the ledger promises what the router does" `it.each`
  는 이제 모든 행이 `ready` 라 `pending`(else) 분기가 이 스위트 안에서는 죽은 코드다 — 그 promise 는
  `sample-mode-cores.test.ts` 의 여러 "no-such-surface" 셀들이 계속 지킨다는 주석을 남겼다(새 가드
  추가 없음 — 이미 충분한 population 이 있었다).

## 게이트 (이 워크트리 · Windows 호스트 · 각 게이트 독립 실행 + 명시 rc)

BEFORE 는 이 워크트리의 분기점(`origin/main` = `367976d37`, `TASK-PC-FE-287` 의 머지 커밋 그 자체)에서,
`git stash push -u` 로 이 티켓의 편집을 걷어낸 뒤 쟀다.

| 게이트 | 트리 | 결과 |
|---|---|---|
| `pnpm test` | BEFORE(`git stash`로 되돌린 이 워크트리) | rc=0 · **313 files / 3479 tests passed**, 실패 0 |
| `pnpm lint` | AFTER(최종) | rc=0 · «No ESLint warnings or errors» |
| `npx tsc --noEmit` | AFTER(최종) | rc=0 (1차 시도부터 통과) |
| `pnpm test` (1차 — 구현 직후) | AFTER | rc=0 · **315 files / 3518 tests passed** — 기존 테스트 회귀 0건(구현 중 잡힌 회귀 없음) |
| `pnpm build` | AFTER(최종) | rc=0 |
| `pnpm e2e:smoke` | AFTER(최종, 프로덕션 빌드, 백엔드 전부 loopback 127.0.0.1:1) | rc=0 · **24 passed** — 기존 24개 중 pending 셀 1개 제거 + 신규 `sample-visitor-scm.spec.ts` 1개(AC-7) = 순증 0, 총 24 |
| `pnpm test` (bite 복원 후, 최종) | AFTER | rc=0 · **315 files / 3518 tests passed**, 실패 0 |
| `pnpm lint` / `npx tsc --noEmit` (bite 복원 후, 최종 재확인) | AFTER | rc=0 / rc=0 |

🔵 이번 실행들에서는 memory 에 기록된 기지 Windows 타이밍 flake(`OperatorsScreen`/`LedgerOpsScreen`/
`DelegationScreen`)가 재현되지 않았다(전부 실패 0).

### Bites (금지/결함 코드를 넣어 빨강 → 되돌려 복원 트리에서 초록)

| # | 가드 | 주입 | 빨강 | 복원 후 |
|---|---|---|---|---|
| B1 | AC-3 공급사 해석(id→code→null) | `resolveSupplier()` 를 항상 `null` 반환하도록 파괴 | rc=1 · 1 file / 3 failed(id 해석·code 폴백·list 해석 셀) | rc=0 · 31/31 |
| B2 | AC-8 개요 SCM 카드 = `/scm/inventory` 노드 집합(cross-view) | 스냅샷 행 하나의 `nodeId` 를 존재하지 않는 노드로 변경(`dashboards.ts` 의 파생이 모듈 로드 시점에 throw) | rc=1 · 2 files 단언 실패(로드 자체가 던져 그 트리를 거치는 모든 스위트가 실패) | rc=0 · 37/37 |
| B3 | AC-4 config 404-as-empty | `POLICY_NOT_FOUND` 대신 항상 기본 정책을 반환하도록 파괴 | rc=1 · 1 file / 1 failed | rc=0 · 31/31 |
| B4 | `SampleScreenNotice` pending/ready 분기(재홈된 유닛 테스트) | 컴포넌트의 조건을 반전(`=== 'pending'` → `return null`) | rc=1 · 1 file / 4 failed(전 셀) | rc=0 · 4/4 |
| B5 | coverage.ts 의 scm 행 core 오분류(287 이 찾은 결함 부류의 회귀 가드) | `scm_config` 행을 `core: 'wms'` 로 변경 | rc=1 · 2 files / 9 cells 실패(AC-0·AC-1·AC-4·coverage-ledger 전부) | rc=0 · 76/76 |

B1–B5 모두 단독 주입 → 실행 → 복원 순으로 개별 확인했다. `grep -rn "BITE-" src tests e2e-smoke` = 0건
(복원 확인). 전체 스위트 기준 최종 복원 확인은 § 게이트의 "bite 복원 후" 행.

### 병합 트리 관점 — DoD 대조군 (로그인 운영자 경로)

이 티켓이 건드린 파일은 `coverage.ts`(scm 3행 상태만 변경 + `SURFACE_SAMPLE_PATH` 3행 추가) ·
`label-rule.ts`(키 12개 추가만) · `fixtures/index.ts`(scm 배럴 추가만) · `fixtures/scm.ts`(신규) ·
`fixtures/dashboards.ts`(scm 카드 파생, 하드코딩 리터럴 제거) · `sample-mode-cores.test.ts`(scm shim
1 셀의 `logPrefix` 만 no-such-surface 로 교체, 단언 불변) · `sample-coverage-ledger.test.ts`(주석
추가만, `expect(` 불변) · `sample-overview-cards-match-lists.test.ts`(신규 SCM 셀 추가) ·
`e2e-smoke/sample-visitor.spec.ts`(pending 셀 제거, 다른 셀 단언 불변) 뿐 — scm/replenishment/config
화면 컴포넌트·hooks·api 클라이언트는 **한 줄도 안 바뀌었다**(`TASK-MONO-677`/`683` 이 이미 병합해 둔
프로덕션 코드를 그대로 재현했을 뿐). 인증 운영자 경로 테스트(scm-api/scm-proxy/scm-state/
scm-overview-state/demand-planning-api/demand-planning-proxy/demand-planning-seed-api/
demand-planning-seed-proxy 전부)는 무수정 초록이다 — `scm-api.test.ts`/`scm-proxy.test.ts`/
`demand-planning-*.test.ts` 는 282 의 D8 로 "빈 쿠키 병=세션 없음"이 반쪽 세션으로 이미 바뀌어 있었고
(각 파일 헤더 `ADR-MONO-074` 인용 확인), `scm-state.test.ts`/`scm-overview-state.test.ts`/
`replenishment-state.test.ts` 는 grep 으로 모든 `it(` 셀이 항상 `ACCESS_COOKIE` 를 세팅함을 확인해
애초에 빈 병(샘플) 경로에 도달하지 않는다(282 가 고칠 것이 없었던 파일) — `pnpm test` 최종 실행이 그
안에 포함된 전체 스위트다.
