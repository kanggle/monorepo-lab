# Task ID

TASK-PC-FE-373

# Title

로그인 `/wms` 개요가 경보 행이 1건만 있어도 통째로 «일시적으로 불러올 수 없습니다» — 콘솔 `AlertRowSchema` 는 `alertId` 를 요구하는데 wms admin-service 는 `id` 로 내보낸다

# Status

ready

# Owner

frontend

# Task Tags

- code
- bug
- console
- wms
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (스키마 키 교체 + 계약 필드 목록 고정 + 테스트; 생산자는 안 건드림)
> Drafted 2026-10-25 UTC. 출처: 35차 데모 창(`TASK-MONO-672` 후속 원장) 라이브 발견. 사실은 origin/main `1ccccdb88` 에서 읽었다.

# Dependency Markers

- 출처: 35차 창 — 경보 행 1건(`LOW_STOCK`, `TASK-BE-647` AC-2 가 만든 행)이 생기자 로그인 `/wms` 가 degraded. 행을 지우자(소유자 결정) 0건이 되어 화면이 렌더됨 — 대조 쌍으로 원인이 갈렸다.
- 관련: `TASK-PC-FE-358` AC-4 (로그인 `/wms` 일별 입고·출고 카드 — 같은 개요 화면이 경보 때문에 가려졌었다), `TASK-BE-647` (low-stock 경보 emit — 이 티켓이 드러낸 경로의 생산자).
- 선행 없음. 계약 보강은 이 티켓 안에서 먼저 한다(Scope 1).

# Goal

wms 경보가 1건 이상 있는 운영 상태에서도 로그인 `/wms` 개요가 정상 렌더되고, 경보 표의 «확인 처리» 버튼이 실제 경보 id 로 acknowledge 를 호출한다. 콘솔이 생산자의 실제 키(`id`)를 읽는다.

# Scope

## In Scope

- **AC-0 사실 (초안 작성 중 코드로 확인):**
  - 생산자: `projects/wms-platform/apps/admin-service/src/main/java/com/wms/admin/api/dashboard/dto/AlertLogResponse.java:7-19` — record 컴포넌트 `id`(UUID) · `alertType` · `warehouseId` · `locationId` · `skuId` · `lotId` · `thresholdQty` · `actualQty` · `detectedAt` · `acknowledgedAt` · `acknowledgedBy` · `projectedAt`. **`alertId` 도 `message` 도 `acknowledged`(boolean) 도 없다.**
  - 소비자: `features/wms-ops/api/types.ts:236-247` `AlertRowSchema` 는 `alertId: z.string()`(필수). `.passthrough()` 라 모르는 키는 통과하지만 **필수 키 부재는 던진다**. `message`·`acknowledged` 는 optional 이라 안 던지지만 항상 비어 있다.
  - 파싱 위치: `features/wms-ops/api/wms-alerts-api.ts:26` `AlertPageSchema.parse(json)` → 던짐 → `features/wms-ops/api/wms-state.ts:107-109` 의 catch-all 이 `degraded: true` 로 접는다 → 개요 전체가 «wms 운영 정보를 일시적으로 불러올 수 없습니다».
  - `alertId` 를 쓰는 소비 자리 전부: `features/wms-ops/components/WmsOpsScreen.tsx:112`(ack 대상) · `features/wms-ops/hooks/use-wms-ops.ts:440,449,453`(ack 경로) · `features/wms-ops/components/WmsAlertsTable.tsx:73`(행 `key`) · `features/wms-ops/components/wms-ops-helpers.ts:46`(라벨) · `features/wms-ops/api/wms-alerts-api.ts:38,44`(ack 호출). ack 의 URL 경로 파라미터 이름 `{alertId}` 는 **계약상 이름이고 그대로 둔다** — 바꿀 것은 행에서 읽는 키다.
  - 표본 픽스처 `shared/sample/fixtures/wms.ts:963,973` 이 `alertId` 를 쓴다 — 익명 표본은 표를 멀쩡히 그렸고, **픽스처와 생산자의 키가 갈라져 있다는 사실을 가렸다.**
  - 계약: `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.6 `GET /api/v1/admin/dashboard/alerts` (`:489-496`)는 «Response `200`: paginated list of `AlertLog` rows» 뿐 **필드 이름이 없다.** ack 응답(`:506`)도 «the updated `AlertLog` row».
- **1. 계약 먼저 (같은 PR, 순서상 첫 커밋):** `admin-service-api.md` § 1.6 에 `AlertLog` 행의 필드 목록을 고정한다 — 위 12개 필드의 이름·타입·nullable 여부(`AlertLogResponse` 와 `AlertLogEntity` 를 읽어 확정; `warehouseId` 는 35차 창에서 `null` 이고 `locationId` 가 채워진 행이 관측됨). `acknowledged` boolean 과 `message` 는 **없다**고 명시한다(상태 = `acknowledgedAt` 유무). ack 응답이 같은 행임을 명시. 원자 크로스 프로젝트 변경(wms 계약 + 콘솔)이 한 PR 이다 — 루트 `CLAUDE.md` § Cross-Project Changes.
- **2. 콘솔이 생산자에 맞춘다 (생산자는 안 바꾼다):** `AlertRowSchema` 의 필수 키를 `id` 로, `alertId` 를 읽던 자리 전부를 `id` 로. 계약에 없는 `message` 는 표의 «메시지» 열을 계약 필드(`thresholdQty`/`actualQty` 등)로 만든 문장으로 대체하거나 열을 정리한다(구현 시 결정, 화면에 `—` 만 나오는 열을 남기지 않는다). **«확인됨/미확인» 은 `acknowledgedAt` 유무로 파생**한다 — 지금은 생산자가 `acknowledged` 를 안 보내서 확인된 경보에도 «미확인» + «확인 처리» 버튼이 뜬다(같은 뿌리의 2차 결함, 초안 작성 중 발견).
- **3. 픽스처를 생산자 모양으로:** `shared/sample/fixtures/wms.ts` 의 경보 두 행을 `id` + 계약 필드로 바꾼다. `tests/unit/sample-fixtures-schema-wms.test.ts` 가 픽스처를 스키마로 검증하는 구조라면 그 테스트가 «생산자 모양» 을 지키도록 한다.
- **4. 행 하나가 개요를 지우지 않게 (결정: 범위 안, 경보 목록 한정):** 경보 행 한 줄이 파싱 불가여도(`id` 부재 등) **그 행만** 표에서 빼거나 «확인 불가» 로 그리고 개요·다른 행은 렌더한다. 구현은 경보 목록 파서에 국한한다(행 단위 `safeParse` 후 실패 행 제외 + 제외 건수 표기/로그). 근거: 이번 결함의 폭발 반경이 «행 1건 → 화면 전체» 였고, 계약이 아직 필드를 고정하지 않았던 동안 같은 부류의 드리프트가 다시 일어날 수 있다. **범위 밖:** `wmsPage()` 공용 래퍼를 12개 읽기 전부에서 행 단위 관용으로 바꾸는 일 — 영향이 넓고(재고·출하 등 필수 키 판정이 의도된 곳이 섞여 있음) 별도 결정이 필요하다.

## Out of Scope

- **생산자(wms admin-service) 변경 일체** — 키를 `alertId` 로 바꾸거나 `acknowledged`/`message` 를 추가하지 않는다. 콘솔이 생산자에 맞춘다(소유자 방향).
- `wmsPage()` 공용 래퍼의 전면 행 단위 관용화 (위 4번 근거).
- SCM `ReplenishmentSuggestion.warehouseId` 가 LOCATION id 를 담고 wms 경보는 `warehouseId:null` + `locationId` 라는 35차 창 관찰 — 별도 사안(이 티켓은 콘솔 읽기 키만).
- 경보 acknowledge 의 서버 측 의미(권한·멱등) — 변경 없음.

# Acceptance Criteria

- [ ] **AC-0** — 위 «사실» 의 파일:줄을 착수 시점 코드로 다시 읽어 맞는지 확인하고 어긋난 줄은 이 절을 고쳐 기록한다. 추가로 `AlertLogEntity` 를 읽어 `AlertLogResponse` 의 nullable 여부를 확정한다(계약 필드 목록의 근거).
- [ ] **AC-1 (계약)** — `admin-service-api.md` § 1.6 가 `AlertLog` 행 필드 목록(이름·타입·nullable)을 명시하고, `id` 가 행 식별자이며 `acknowledged`/`message` 는 응답에 없음을 적는다. 이 변경이 콘솔 수정과 **같은 PR 의 앞선 커밋**이다.
- [ ] **AC-2 (실제 모양 입력, 수정 전 빨강)** — 콘솔 단위 테스트가 **생산자의 실제 모양 행**(키 `id`, `alertId` 없음, `acknowledged`/`message` 없음, `warehouseId:null` + `locationId`)을 `AlertPageSchema`/`listAlerts` 경로와 `getWmsSectionState` 에 먹이고 «degraded 가 아님 + 행이 표에 나옴» 을 단언한다. **수정을 되돌리면(스키마·소비 자리 원복) 이 테스트가 빨강**임을 mutation/revert 로 보이고 구현 보고에 기록한다. 대조군: 표본 픽스처를 쓴 기존 통과 테스트는 이 결함을 못 잡았다는 점을 적는다.
- [ ] **AC-3 (ack 가 실제 id 를 쓴다)** — 실제 모양 행에서 «확인 처리» → 확인 → 요청 경로가 `/api/wms/alerts/<행의 id>/acknowledge` 이고 `undefined` 가 아니다(테스트로 경로 문자열 단언). 수정 전 빨강.
- [ ] **AC-4 (상태 파생)** — `acknowledgedAt` 이 있는 행은 «확인됨» 이고 버튼이 없고, 없는 행은 «미확인» + 버튼. 수정 전 빨강(확인된 행이 «미확인» 으로 나옴).
- [ ] **AC-5 (한 행이 전체를 지우지 않는다)** — 경보 응답에 정상 행 1 + 파싱 불가 행 1(예: `id` 부재)이 섞여 있을 때 개요가 degraded 가 아니고 정상 행이 표에 나온다. 수정 전 빨강.
- [ ] **AC-6 (픽스처)** — 표본 경보 행이 생산자 모양(`id`)이고, 익명 `/wms` 표본 화면이 여전히 경보 표를 그린다(기존 sample 스위트 초록).
- [ ] **AC-7 (회귀)** — console 전체 vitest(`--maxWorkers=2 --minWorkers=1`) · `tsc --noEmit` · `next lint` rc=0. 모든 e2e 스펙을 grep 해서 `alertId`/`wms-alert-row` 를 읽는 스펙이 있으면 함께 고친다.
- [ ] **AC-8 (라이브, 다음 데모 창)** — Vercel Production 배포 후 다음 창에서 로그인 `/wms` 가 **경보 ≥ 1건** 상태로 개요를 렌더하고(경보 행 생성은 `TASK-BE-647` 재현 — 재고 조정으로 임계 이하), 표에 경보가 보이며 «확인 처리» 가 성공해 행이 «확인됨» 이 된다. 못 재면 ⚪ + 사유.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § WMS
- `projects/wms-platform/specs/services/admin-service/architecture.md` (AlertLog 투영)

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.6 Alerts — **필드 목록을 이 티켓이 고정한다(변경 대상).**

# Edge Cases

- `warehouseId` 가 `null` 이고 `locationId` 만 있는 행(35차 창 관측) — 파싱·표시에서 던지지 않는다.
- 경보 0건 — 기존대로 빈 상태(이 티켓 이전에도 렌더되던 경로, 대조군).
- `acknowledgedAt` 은 있으나 `acknowledgedBy` 가 null — 확인됨으로 그린다.
- ack 응답(`AckResultSchema = AlertRowSchema`)도 같은 모양이라 같은 수정이 덮는다 — 응답 파싱 실패가 «성공한 ack 를 실패로 보이게» 하지 않는지 AC-3 테스트에서 함께 본다.
- 페이지 2 이상 — 행 `key` 가 `id` 라 중복 없음.

# Failure Scenarios

1. 스키마만 `id` 로 바꾸고 `WmsOpsScreen.tsx:112`·`use-wms-ops.ts` 를 안 바꾼다 — 화면은 뜨지만 ack 가 `/alerts/undefined/acknowledge` 를 친다(AC-3).
2. 픽스처를 그대로 두고 테스트도 표본 픽스처로만 돌린다 — 이번 결함이 숨었던 바로 그 경로가 또 초록으로 지나간다(AC-2 가 실제 모양을 요구하는 이유).
3. 계약 필드 목록 없이 콘솔만 고친다 — 다음에 누가 생산자 DTO 를 바꿔도 같은 드리프트가 조용히 재발한다(AC-1).
4. `acknowledged` 파생을 빼먹는다 — 화면은 뜨지만 확인된 경보에 «확인 처리» 가 남는다(AC-4).
5. 행 단위 관용(4번)을 `wmsPage()` 전체로 넓혀 다른 읽기의 의도된 필수 키 판정까지 약화한다 — 범위 밖.
