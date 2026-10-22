# Task ID

TASK-SCM-BE-062

# Title

보충 제안이 «발동 당시 재주문점»을 싣는다 — 콘솔이 «재주문점 · 발동 재고 · 제안 수량» 을 한 줄 막대로 그려 «이 제안이 얼마나 급한가» 를 보이게 하는 데이터 (SKU 마다 정책을 따로 조회하지 않고)

# Status

review

# Owner

backend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (계약 변경 · ADR-MONO-017 범위 판정 · 스냅샷 의미)
> 기안 2026-10-22 UTC — 콘솔 그래프 조사(`TASK-PC-FE-357`~`362`)의 «데이터가 더 필요한 후보» 중 명분이 커지는 유일한 것(소유자 «어», 2026-10-22 KST). **ready 만 — 착수는 소유자 지시 때.** 서버 변경 ⇒ 배포는 **굽기**.

# Dependency Markers

- 출처: 콘솔 그래프 조사 — 보충 제안 행은 `suggestedQty` · `triggerAvailableQty` 만 있어 «막대의 절반» 만 그릴 수 있었다(재주문점 없음).
- 후속(이 티켓 머지 · 굽기 뒤): 콘솔 티켓 — `/scm/replenishment` 표에 «재주문점 · 발동 재고 · 제안 수량» 한 줄 막대. 이 티켓이 닫힐 때 기안한다.
- 선행 없음.

# Goal

지금 `GET /api/v1/demand-planning/suggestions` 의 한 행(`specs/contracts/http/demand-planning-api.md:60-85`)은 `suggestedQty` · `triggerAvailableQty` 를 갖지만 **그 제안을 발동시킨 재주문점**은 없다. 재주문점은 `GET /policies/{skuCode}`(`:137-145`)에 따로 있어, 콘솔이 막대를 그리려면 **SKU 마다 한 번씩** 정책을 불러야 한다(20행이면 20호출). 제안이 발동될 때의 정책 값(`reorderPoint` · 필요하면 `safetyStock`)을 제안 행에 **함께 저장하고 응답에 싣는다**.

# Scope

## In Scope

- **AC-0 측정** (file:line):
  - ① 제안이 만들어지는 경로(`source=ALERT` 등) — 그 시점에 정책 값을 이미 읽는가(읽는다면 저장만 더하면 된다).
  - ② `ADR-MONO-017` 의 기각 갈래 B(«도메인마다 BFF 용 `/summary` · `/dashboard-card` 집계 엔드포인트», `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md:65`)에 이 변경이 해당하는가 — 이 티켓은 **새 엔드포인트도 집계도 아니고**, 제안 한 건이 자기 발동 조건을 기록하는 것이다. 해당하면 STOP(ADR 개정 먼저).
  - ③ 같은 제안 응답을 쓰는 소비자 전수(콘솔 · 다른 서비스 · 시험) — 필드 **추가**가 깨는 곳이 있는가(zod `strict` · 계약 시험).
  - ④ 기존 제안 행(마이그레이션 전 데이터)에 값이 없을 때의 표현(`null`) · 데모 시드.
- **소유자 결정 (AC-0 뒤 선택창)**: 실을 값의 의미 — **(가) 발동 당시 스냅샷**(추천: 정책이 나중에 바뀌어도 «재고 5 ≤ 재주문점 10 이라 발동» 이 정직하다) / (나) 응답 시점의 현재 정책(조인 — 바뀐 정책이 옛 제안을 거짓으로 설명할 수 있다).
- 계약 먼저: `demand-planning-api.md` 의 제안 모양에 필드 추가(`reorderPointAtTrigger` 같은 이름 — 의미가 이름에 드러나게) · 이벤트 계약에 제안 이벤트가 있으면 함께 판정.
- 구현: 스키마 마이그레이션(nullable 열) · 발동 시 저장 · 응답 매핑 · 시험(발동 → 값 저장 · 정책 변경 뒤에도 제안 값 불변(가 일 때) · 옛 행 `null` · 계약 시험).

## Out of Scope

- 콘솔 화면(후속 티켓).
- 정책 API · 제안 상태 기계 변경.
- 현재 재고(inventory-visibility) 를 제안에 싣는 것 — «발동 재고» 와 다른 값이고 실시간성 문제가 있다.

# Acceptance Criteria

- [x] **AC-0** — 위 ①~④. ② 가 «해당» 이면 STOP. → 해당 안 함(아래 § AC-0 ②). ③(b) 는 정정 있음(§ 구현 «AC-0 정정»).
- [x] **AC-1** — 소유자 결정(가/나) 선택창 · 계약 먼저. → (가) + (가-1) + (가-2), 계약 커밋 `7726cda04` 가 구현 커밋보다 먼저.
- [x] **AC-2** — 구현 + 시험(위 목록) · CI 의 scm 잡 초록. → 구현 · 단위/슬라이스/계약 시험은 로컬 초록(118/118). **Testcontainers IT 6건(신규 3 · 기존 시험에 단언 추가 3)은 로컬에서 못 돌렸다**(Docker 데몬 없음) — CI `demand-planning-service:integrationTest` 와 scm 잡 초록을 보기 전에는 닫지 않는다.
- [ ] **AC-3** — 굽기 뒤 라이브: 데모에서 새로 발동된 제안 응답에 값이 실리고, 옛 제안은 `null`(다음 데모 창). 닫을 때 콘솔 후속 티켓 기안.

# Related Specs

- `projects/scm-platform/specs/services/demand-planning-service/` (architecture · data-model — AC-0 에서 이름 확인)
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` (§ 갈래 B — 범위 판정)

# Related Contracts

- `projects/scm-platform/specs/contracts/http/demand-planning-api.md` (갱신 대상)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § SCM 보충(읽기 — 소비자 영향)

# Edge Cases

- 정책이 없는 SKU 로 발동된 제안(가능한가 — AC-0 ①) → `null`.
- 같은 SKU 의 정책이 제안 사이에 바뀜 → (가) 면 제안마다 다른 값이 정상.
- 콘솔 zod 가 응답 필드에 `strict` 면 필드 추가가 콘솔을 깬다 → AC-0 ③.

# Failure Scenarios

1. 현재 정책을 조인해 «발동 당시» 처럼 보인다 → 이름 · 시험으로 의미를 고정.
2. 필드 추가가 콘솔 파서를 깨 보충 화면이 저하된다 → AC-0 ③ + 콘솔 계약 시험.
3. 굽기 전에 콘솔 후속을 머지 → 운영 콘솔이 없는 필드를 기다린다(빈 막대) — 후속은 이 티켓의 AC-3 뒤.

---

## AC-0 측정 (2026-10-22 UTC)

> 경로 약어: `dp/` = `projects/scm-platform/apps/demand-planning-service/src/main/java/com/example/scmplatform/demandplanning/`,
> `cw/` = `projects/platform-console/apps/console-web/`. 측정 트리 = 워크트리 `task/scm-be-062-impl`(base `origin/main` `02329254b`).
> Service Type = `event-consumer + batch-job + rest-api`(`specs/services/demand-planning-service/architecture.md:11`) — 이 변경이 닿는 면은 응답(rest-api) 이라 `platform/service-types/rest-api.md` 를 읽었다(생성 경로는 consumer · batch 쪽이지만 그 두 파일이 정하는 것 — 구독 그룹 · 스케줄 — 은 바뀌지 않는다).

### ① 제안이 만들어지는 곳 · 그 순간 정책을 이미 읽는가 — **읽는다(저장만 더하면 된다). 단 «정책 없음» 대체 경로 둘이 서로 다른 가짜 값을 쓴다**

- 생성 지점은 **정확히 둘**(`raiseFromAlert` · `raiseFromBatch` 의 `src/main` 호출처 grep = 2):
  - ALERT: `dp/application/usecase/EvaluateReorderUseCase.java:149-151` — 같은 `@Transactional`(`:96`) 안에서 `:116` 이 정책을 읽고 `:119-122` 가 `reorderPoint` 를 지역 변수로 이미 들고 있다.
  - BATCH: `dp/application/usecase/SweepReorderUseCase.java:159-161` — 같은 `@Transactional`(`:72`) 안 `:134` 가 정책을 읽고 `:137-140` 이 `reorderPoint` 를 들고 있다.
  - ⇒ 스냅샷은 **추가 조회 0** — 이미 손에 든 값을 팩터리에 넘기기만 하면 된다.
- 🔴 **정책이 없을 때 두 경로의 `reorderPoint` 는 정책 값이 아니다:**
  - ALERT: `EvaluateReorderUseCase.java:123-128` — wms 경보 임계값(`alertThreshold`) 을 재주문점으로 쓴다(`data-model.md:24-26` 이 적은 대체 경로).
  - BATCH: `SweepReorderUseCase.java:141-145` — **`reorderPoint = availableQty`**, 즉 «언제나 발동» 을 위한 합성값. 이 대체 경로는 `data-model.md:24-26` 에 **적혀 있지 않다**(스펙 공백 — 이 티켓 범위 밖, 기록만).
  - ⇒ 이 지역 변수를 그대로 저장하면 BATCH 대체 제안은 막대가 «재주문점 = 발동 재고» 로 그려진다 — **지어낸 숫자**. 정책이 없으면 `null` 로 둬야 한다(티켓 Edge Case 첫 줄과 같음). 아래 (가-1).
- `safetyStock` 도 같은 정책 객체에서 읽힌다(`dp/domain/model/ReorderPolicy.java:15,48`) — 하지만 v1 판단 규칙은 쓰지 않는다(`ReorderPolicy.java:42-44` 는 `reorderPoint` 만, `data-model.md:17,113` «v1 informational»). 아래 (가-2).
- 🔴 **구현 함정(시험으로 막을 것):** `dp/adapter/outbound/persistence/adapter/ReorderSuggestionAdapter.java:40-59` 의 `save` 는 승인 · 기각 · 발주화 때도 **모든 열을 도메인 값으로 다시 쓴다.** 새 필드를 `reconstitute`(`:84-91`, `dp/domain/model/ReorderSuggestion.java:140-151`) 에 빠뜨리면 첫 승인이 스냅샷을 `null` 로 덮는다. 시험: 발동 → 승인 → 값 그대로.
- 팩터리 시그니처를 넓히면 호출처 **13곳 / 6파일**(main 3 · test 3: `ReorderSuggestionStateMachineTest` 1 · `SuggestionApprovalTxnTest` 6 · `SuggestionControllerSliceTest` 3) 이 함께 바뀐다.

### ② `ADR-MONO-017` 갈래 B 판정 — **해당하지 않는다(STOP 아님)**

- 갈래 B 원문(`docs/adr/ADR-MONO-017-platform-console-bff-architecture.md:65`): *"Each domain adds a `/summary` or `/dashboard-card` **endpoint** optimised for BFF consumption"*. 그것이 속한 결정은 **D3 «Cross-domain dashboards composition»**(`:60`, 질문 정의 `:26`) — 교차 도메인 대시보드를 무엇으로 조립하느냐다. 기각 사유는 «5 도메인 생산자 스펙 + 5 구현 재작업»(`:65`).
- 이 변경과 대조:
  1. **새 엔드포인트가 아니다** — 기존 `GET /suggestions` · `GET /suggestions/{id}` 행 모양(`specs/contracts/http/demand-planning-api.md:60-85,102-104`) 에 필드 하나.
  2. **집계가 아니다** — 행 한 건이 자기 발동 조건을 기록한다. 같은 성격의 필드가 이미 있다: `triggerAvailableQty` = «provenance»(`data-model.md:59`).
  3. **BFF 소비가 아니다** — 소비자는 단일 도메인 화면 `/scm/replenishment` 이고, D2 갈래 A(`:56`) 가 단일 도메인 화면은 도메인 엔드포인트를 **직접** 부른다고 정했다. `projects/platform-console/apps/` 에서 `console-web` 을 뺀 곳의 `demand-planning|suggestion` grep = **0** — console-bff 는 이 응답을 읽지 않는다.
  4. **기각 사유(5 도메인 재작업) 가 성립하지 않는다** — 생산자 하나, 계약 파일 하나.
- 🔵 겹치는 한 점: 동기가 «콘솔이 그리기 편하게» 라는 것. 하지만 B 를 가르는 것은 동기가 아니라 «소비자 전용 집계 엔드포인트» 라는 **모양**이고, 이 필드는 그 모양이 아니다. (나) 도 새 엔드포인트를 만들지 않으므로 어느 쪽을 골라도 판정은 같다.

### ③ 같은 응답의 소비자 전수 — **필드 추가가 깨는 곳 0. 단 계약 시험 · 영속 IT 가 아예 없다**

| 소비자 | 위치 | 추가 필드에 대한 반응 |
|---|---|---|
| 콘솔 파서 | `cw/src/features/scm-replenishment/api/types.ts:32-57` | `.passthrough()`(`:57`) — **`strict` 아님**, 모든 필드 optional. 모르는 키는 통과(`:22-26` 관용 불변식). 안 깨짐. |
| 콘솔 표 | `cw/src/features/scm-replenishment/components/ReplenishmentTable.tsx:138` | `triggerAvailableQty` 만 그린다. 영향 없음(그리는 것은 후속 티켓). |
| 콘솔 소비자 계약 | `projects/platform-console/specs/contracts/console-integration-contract.md:1216-1217` | «행은 `triggerAvailableQty` 를 보인다» — 후속 티켓이 갱신. |
| 콘솔 샘플 세계 | `cw/src/shared/sample/fixtures/scm.ts:614-688`(4행) · `cw/tests/unit/sample-fixtures-schema-scm.test.ts:162-205` | 같은 스키마로 파싱 — 필드 없어도 통과. 후속 티켓이 시드에 값을 넣는다. |
| console-bff | — | 참조 0(위 ② 3). |
| scm e2e | `projects/scm-platform/tests/e2e/.../ReplenishmentLoopE2ETest.java:235-239` · `InboundExpectedLoopE2ETest.java:371-377` | `JsonNode` 로 키를 골라 읽음 — 관용. |
| 컨트롤러 슬라이스 | `.../adapter/inbound/web/SuggestionControllerSliceTest.java:55-109` | `jsonPath` 가 특정 키만 본다(키 집합 단언 없음). 팩터리 시그니처 변경으로 **컴파일만** 손봐야 함. |
| 이벤트 | — | demand-planning 은 이벤트를 **내지 않는다**(`src/main` 의 outbox · `KafkaTemplate` 0, 이벤트 계약의 suggestion 언급은 전부 소비 측 `replenishment-subscriptions.md:35,62-63`). procurement `from-suggestion` 요청 본문은 별도 계약이고 건드리지 않는다. ⇒ **이벤트 계약 영향 없음.** |

- 🔴 **없는 것 둘:** (a) `demand-planning-api.md` 에 대한 **계약 시험이 없다** — `src/test` 에서 `suggestedQty|triggerAvailable|SuggestionResponse` grep = 0. `rest-api.md:82` 가 요구하는 것인데 지금까지 없었으니, AC-2 의 «계약 시험» 은 **첫 계약 시험을 새로 만드는 일**이다. (b) `demand-planning-service/src/test` 에 Testcontainers IT(`*IT*.java`) 가 **0** — 마이그레이션 V4 를 실제 Postgres 에서 돌리는 시험도 새로 만들어야 한다(H2 금지). 둘 다 (가) 의 비용에 들어간다.

### ④ 옛 행 · 데모 시드 · 테넌트

- 🔴 **«옛 행 = `null`» 은 전선에서 «키 없음» 이다.** `dp/adapter/inbound/web/dto/SuggestionResponse.java:18` 이 `@JsonInclude(NON_NULL)` — null 필드는 **생략**된다. AC-3 의 «옛 제안은 `null`» 은 «키가 없다(= null 로 취급)» 로 읽어야 하고, 계약도 그렇게 적어야 한다. 콘솔 `.nullable().optional()` 은 둘 다 받는다. (곁: 같은 이유로 계약 예시 `demand-planning-api.md:80` 의 `"materializedPoId": null` 도 실제로는 나가지 않는다 — 기존 불일치, 이 티켓에서 함께 바로잡을지는 구현 때 판단.)
- 마이그레이션: 기존 V1~V3(`src/main/resources/db/migration/demand-planning/`) → 다음은 **V4**, V3 과 같은 «additive + nullable» 모양. 🔴 **백필하지 않는다** — 현재 정책으로 채우면 그것이 곧 (나) 의 거짓을 과거 행에 굳히는 일이다.
- 데모 시드: `infra/demo/seed/seed-scm.sh:162-171` 은 정책(`reorderPoint=10`) 을 PUT 할 뿐, 제안은 **넣지 않고** 서비스가 재고 신호에 반응해 만들기를 기다린다(`:177-178`, 대기 `:184-208`). ⇒ 굽기 뒤 새로 발동된 제안은 자동으로 `10` 을 싣고, **시드 변경 불필요.** 이미 남아 있는 데모 DB 행은 키 없음.
- 테넌트 `"scm"` 고정: `SuggestionController.java:39` 와 같은 상수가 `EvaluateReorderUseCase.java:45` · `SweepReorderUseCase.java:36` 에 있고, 정책 조회도 같은 상수로 한다(`EvaluateReorderUseCase.java:116` · `SweepReorderUseCase.java:134`, 정책 행 `tenant_id = "scm"` `data-model.md:19`). ⇒ 스냅샷 값과 정책은 **같은 테넌트 키** — 이 변경이 교차 테넌트 위험을 만들지 않는다. (나) 의 읽기 시점 조인도 제안 행의 `tenantId` 로 하면 같다. 이 고정이 문제였던 곳은 `TASK-PC-FE-364`(통합 개요 카드 탈락) 이지 여기가 아니다. 곁: `getSuggestion`(`SuggestionController.java:64-68`) 은 테넌트를 아예 거르지 않는다 — 단일 테넌트라 지금은 무해, 이 티켓 범위 밖.

### 소유자 결정 대기

**(가) 발동 당시 스냅샷 — 추천**

- 계약 먼저: `demand-planning-api.md` 행 모양에 `reorderPointAtTrigger`(integer, **정책이 없던 발동 · V4 이전 행은 키 없음**) + 의미 주석 «발동 순간의 scm 재주문점. 정책이 나중에 바뀌어도 변하지 않는다». `data-model.md` 의 `reorder_suggestion` 표에 `reorder_point_at_trigger INT null` 한 줄. 이벤트 계약 변경 없음(③).
- 구현: `V4__reorder_suggestion_reorder_point_at_trigger.sql`(`ADD COLUMN reorder_point_at_trigger INT`, 백필 없음) · 도메인 `Integer reorderPointAtTrigger`(두 팩터리 + `reconstitute`) · 두 use case 가 `policyOpt.map(ReorderPolicy::getReorderPoint).orElse(null)` 을 넘김(대체 경로의 지역 변수는 **넘기지 않음**) · JPA 열 · 어댑터 `save`/`toDomain` · `SuggestionResponse` 필드.
- 시험: 도메인 단위(팩터리가 값을 싣는다) · use case 단위 ×2 경로(정책 있음 → 값 / 정책 없음 → null, 특히 BATCH 의 합성값이 새지 않음) · Testcontainers 영속 IT(V4 왕복 · **승인 뒤에도 값 유지** · 정책 PUT 뒤에도 옛 제안 값 불변) · 컨트롤러 슬라이스(값 있음 → 키 있음 / null → 키 없음) · 첫 계약 시험.
- 비용: main 약 9파일 + 호출처 13곳 갱신 + 새 IT · 새 계약 시험 · 마이그레이션 1. 굽기 필요.
- 이득: 막대가 «그때 왜 발동했나» 를 정직하게 보인다. 정책이 바뀌어도, 정책이 없었어도(null) 옛 제안이 거짓이 되지 않는다.
- 하위 결정 둘(추천 먼저):
  - **(가-1) 정책 없이 발동한 제안 → `null`**(추천) / 대신 실제로 쓰인 임계값을 저장. 후자는 BATCH 대체가 `availableQty` 자체라 막대가 «재주문점 = 발동 재고» 로 지어진다(①). 저장하려면 ALERT 의 wms 임계값만 별도 출처 표시와 함께 — 그건 필드가 둘이 되는 일.
  - **(가-2) `safetyStock` 은 싣지 않는다**(추천) / `safetyStockAtTrigger` 도 함께. v1 판단 규칙이 쓰지 않는 값이고(`data-model.md:17,113`) 막대(재주문점 · 발동 재고 · 제안 수량) 에 자리가 없다. 필요해지면 같은 모양으로 V5.

**(나) 응답 시점의 현재 정책 조인**

- 계약: 필드 이름은 **`currentReorderPoint`** 여야 한다(«AtTrigger» 를 쓰면 이름이 거짓말 — Failure Scenario 1). 의미 주석 «응답 순간의 정책. 이 제안을 발동시킨 값과 다를 수 있다».
- 구현: 마이그레이션 없음. `SuggestionQueryUseCase` 가 한 페이지의 SKU 집합으로 정책을 한 번에 읽는 포트 메서드(`findBySkuCodes(tenantId, Set<String>)`) 신설 → 목록 · 상세 · 기각 응답에 매핑. 페이지당 쿼리 +1.
- 시험: 슬라이스 · use case 단위 · 영속 IT(일괄 조회) · 계약 시험.
- 비용: (가) 보다 작다(마이그레이션 · 도메인 · 13곳 시그니처 변경 없음). 굽기 필요는 같다.
- 손해: 정책이 바뀌면 옛 제안 설명이 바뀐다 — 예: 발동 재고 5 · 그때 재주문점 10 → 나중에 재주문점 3 으로 내리면 막대는 «5 > 3, 발동하지 말았어야 할 제안» 을 그린다. `MATERIALIZED`/`DISMISSED` 이력 행일수록 틀린다. 정책 없이 발동한 뒤 정책이 생긴 SKU 는 발동과 무관한 값을 보인다.

**추천: (가) + (가-1) null + (가-2) 재주문점만.** 근거: 생성 순간 값이 이미 손에 있어 추가 조회가 없고(①), 이 막대의 질문(«이 제안이 얼마나 급했나») 은 발동 순간의 사실이며, 그 사실은 나중에 다시 만들 수 없다 — (나) 를 먼저 했다가 (가) 로 바꾸면 그 사이 발동한 행은 영영 값이 없다.

> 소유자 결정 전까지 이 티켓은 AC-1 에서 멈춘다. 결정이 나면 계약(`demand-planning-api.md` · `data-model.md`) 을 먼저 바꾸고 구현한다.

### 소유자 결정 (2026-10-22 KST)

- **(가) 발동 당시 스냅샷**, 필드 이름 **`reorderPointAtTrigger`**.
- **(가-1) 포함** — 정책 없이 발동한 제안은 값을 **갖지 않는다**(wms 경보 임계값도, BATCH 합성값도 아님).
- **(가-2) 포함** — `safetyStock` 은 싣지 않는다.
- 옛 행 · 값 없음의 전선 표현 = **키 없음**(`SuggestionResponse` 가 `@JsonInclude(NON_NULL)`).

## 구현 (2026-10-22 UTC · Opus)

### 🔴 AC-0 정정 — ③(b) «Testcontainers IT 가 0» 은 **틀렸다**

AC-0 은 `*IT*.java` 로 Glob 했고 0 을 받았다. 이 서비스의 IT 이름은 `*IntegrationTest.java` 라 그 패턴에 안 걸린다 — **패턴이 0 이었지 IT 가 0 이 아니었다.** 실제로는 `src/test/java/.../integration/` 에 `AbstractDemandPlanningIntegrationTest`(Postgres 16 + Kafka, `ddl-auto=validate`, Flyway) 와 IT 4 클래스가 있고, `build.gradle:107-125` 의 `integrationTest` 태스크가 `@Tag("integration")` 만 돌리며, CI 가 그것을 부른다(`.github/workflows/ci.yml:3959`). 그래서 새 IT 는 «처음 만드는 일» 이 아니라 기존 기반에 얹는 일이었다. ③(a) «suggestion 행 계약 시험 0» 은 맞았다(있던 계약 시험은 오류 봉투 `GlobalExceptionHandlerEnvelopeContractTest` 하나).

### 커밋

| 커밋 | 내용 |
|---|---|
| `6afeb9151` | AC-0 측정 · ready → in-progress |
| `7726cda04` | **계약 먼저** — `demand-planning-api.md` · `data-model.md` · 소유자 결정 기록 |
| (이 커밋) | 구현 + 시험 |

### 계약 차이 (`7726cda04`)

- `demand-planning-api.md` § GET /suggestions: 예시에 `"reorderPointAtTrigger": 10` 추가. **«값 없음 = 키 없음»** 단락(NON_NULL). 필드 표 신설 — `reorderPointAtTrigger` 는 «발동 순간 정책이 있었을 때만», 정책 PUT · 승인 · 기각으로 바뀌지 않음, 정책 없이 발동(ALERT 경보 임계값 · BATCH 합성값) 과 V4 이전 행은 키 없음, `safetyStock` 안 실음. 예시의 `"materializedPoId": null` 은 전선에 나간 적 없는 값이라 지우고 정정 주석.
- `data-model.md`: `reorder_point_at_trigger INT null` 행(V4 · 백필 없음 · 이유). `reorder_policy` 대체 경로 주석에 **BATCH 쪽 대체(availableQty 합성 임계값)** 를 처음 적음 — AC-0 ① 에서 찾은 스펙 공백.
- 이벤트 계약: 변경 없음(demand-planning 은 이벤트를 내지 않는다, AC-0 ③).

### 코드 (`projects/scm-platform/apps/demand-planning-service/src/main/`)

- `resources/db/migration/demand-planning/V4__reorder_suggestion_reorder_point_at_trigger.sql` — `ADD COLUMN reorder_point_at_trigger INT`, nullable, **백필 없음**.
- `domain/model/ReorderSuggestion.java` — `final Integer reorderPointAtTrigger`(어떤 전이도 안 건드림), `raiseFromAlert` · `raiseFromBatch` · `reconstitute` 세 팩터리 모두에 인자 추가, getter.
- `application/usecase/EvaluateReorderUseCase.java` · `SweepReorderUseCase.java` — `policyOpt.map(ReorderPolicy::getReorderPoint).orElse(null)` 를 넘김. 지역 변수 `reorderPoint`(대체 경로 값이 들어 있을 수 있음) 는 **넘기지 않는다**.
- `adapter/outbound/persistence/jpa/ReorderSuggestionJpaEntity.java` — 열 매핑. `adapter/.../ReorderSuggestionAdapter.java` — `save` · `toDomain` 양쪽.
- `adapter/inbound/web/dto/SuggestionResponse.java` — `Integer reorderPointAtTrigger`(NON_NULL 그대로).
- 테넌트 고정 · 상태 기계 · 정책 API · 이벤트: 변경 없음.

### 시험

| 층 | 파일 | 무엇을 | 로컬 |
|---|---|---|---|
| 도메인 단위 | `domain/ReorderSuggestionStateMachineTest` (+4) | 두 팩터리가 값/`null` 을 싣는다 · 승인→발주화 · 기각 뒤에도 불변 · `reconstitute` 가 값과 `null` 을 보존 | ✅ |
| use case 단위 | `application/EvaluateReorderUseCaseTest` (+2) | 정책 있음 → 10(경보 임계값 8 과 구별되게) · **정책 없음 → `null`(임계값 8 이 새지 않음)** | ✅ |
| use case 단위 | `application/SweepReorderUseCaseTest` (+2) | 정책 있음 → 10 · **정책 없음 → `null`(합성값 3 이 새지 않음)** | ✅ |
| 슬라이스 | `adapter/inbound/web/SuggestionControllerSliceTest` (+1, 단언 +3) | 목록 · 상세 · 기각 응답에 값 · **`null` 이면 키 자체가 없다**(`doesNotExist` + `not(hasKey)`) | ✅ |
| **계약(첫)** | `adapter/inbound/web/SuggestionResponseContractTest` (신규 2) | 실 MVC+Jackson 으로 — 다 찬 행의 키 집합 = 계약 필드 집합(내부 `destinationNodeType` · `tenantId` · `version` 누출 없음) · 최소 행에 `null` 값 키 0 | ✅ |
| Testcontainers IT | `integration/AlertConsumerIntegrationTest` (+2, 단언 +1) | 실 Kafka→Postgres 로 발동 → 10 · **정책 10→3 변경 뒤에도 10 · 기각 뒤에도 10** · 정책 없는 SKU → `null` | ⚪ CI 전용 |
| Testcontainers IT | `integration/ApproveMaterializationIntegrationTest` (단언 +1) | 승인→발주화(저장 2회, 매번 모든 열 재기록) 뒤 10 유지 | ⚪ CI 전용 |
| Testcontainers IT | `integration/SweepSchedulerIntegrationTest` (+1, 단언 +1) | BATCH 정책 있음 → 20 · 정책 없음 → `null`(합성값 7 이 새지 않음) | ⚪ CI 전용 |

- 갱신만 한 호출처: `application/SuggestionApprovalTxnTest`(시그니처 6곳). 시그니처는 인자 **개수**가 바뀌어 옛 호출이 조용히 자리를 밀리지 않고 컴파일 오류가 난다(`Integer`·`UUID` 둘 다 nullable 이라 개수가 같았다면 위험했을 자리).
- **로컬 실행**: `./gradlew :projects:scm-platform:apps:demand-planning-service:test` → **rc=0, 20 스위트 118 시험, 실패 0 · 건너뜀 0**(`build/test-results/test/*.xml` 합산). `test` 태스크가 `integration` 태그를 빼므로 IT 는 여기서 **컴파일만** 되었다(같은 source set).
- **bite**: 두 use case 에서 `policyOpt.map(...)` 을 지역 변수 `reorderPoint` 로 바꿔 넣고 두 단위 시험 클래스를 돌림 → rc=1, **정확히 두 «새지 않음» 시험만 실패**(17 중 2) → 되돌림. 즉 그 두 시험이 (가-1) 을 실제로 문다.
- **⚪ CI 전용 — 왜**: 이 호스트에 Docker 데몬이 없다(`docker info` → `npipe:////./pipe/docker_engine` 없음). IT 는 `DockerAvailableCondition` 이 있어 로컬에서 돌렸어도 «건너뜀» 이었을 것이고, 그것을 통과로 세지 않는다. CI `ci.yml:3959` 가 `demand-planning-service:integrationTest` 를 돌린다 — 그 결과가 V4 마이그레이션 + `ddl-auto=validate` 의 첫 실측이다.

## 머지 전 확인 (2026-10-22 UTC · 분석=Opus 5.5)

- PR #4477(head `20f75411e`) 체크 pending 0 · fail 0. 로컬에서 못 돈 Testcontainers IT 를 CI 에서 확인: run `39814660754` 의 `Integration (scm-platform, Testcontainers)` **pass** — `demand-planning-service:integrationTest` 가 `AlertConsumerIntegrationTest` · `ApproveMaterializationIntegrationTest` · `SweepSchedulerIntegrationTest` 를 실제 Postgres 로 돌렸다(V4 마이그레이션 · `ddl-auto=validate` 포함). `E2E (scm-platform v1 cross-service smoke)` pass. → AC-2 닫힘.
- 열림: **AC-3**(굽기 뒤 라이브 — 새로 발동한 제안에 `reorderPointAtTrigger`, 옛 제안은 키 없음 · 닫을 때 콘솔 후속 티켓 기안).
