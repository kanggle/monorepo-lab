# Task ID

TASK-PC-FE-377

# Title

`/scm/replenishment` 표가 «재주문점 · 발동 재고 · 제안 수량» 을 한 줄 막대로 보인다 — `reorderPointAtTrigger` 소비 (정책을 SKU 마다 따로 부르지 않고)

# Status

review

# Owner

frontend

# Task Tags

- code
- console
- scm
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (스키마 필드 1개 + 표 칸 1개 + 순수 계산 함수 + 샘플 시드 + 계약 문장 + 테스트 — 새 호출 없음)
> Drafted 2026-10-26 UTC. 출처: `TASK-SCM-BE-062` 가 «닫을 때 콘솔 후속 티켓 기안» 을 요구(`Dependency Markers`·AC-3 마지막 절) → 소유자 결정 2026-10-26 UTC: AC-3 «옛 제안은 key 없음» 절반 ⚪ 로 닫고 후속 기안.

# Dependency Markers

- 선행: `TASK-SCM-BE-062`(생산자 `reorderPointAtTrigger` — impl PR #4477 머지 `f23bec8e9`). 33차 창 굽기 `283fd39cf`(AMI `ami-02b3ba00a3b2ff622`)에 그 머지가 조상으로 들어 있어(SCM 티켓 «라이브 확인 (2026-10-23 UTC)»), 35차 창 라이브에서 새 제안이 `reorderPointAtTrigger: 10` 을 실었다(같은 티켓 35차 CORRECTION). **서버 선행 조건은 충족** — 굽기 전 머지로 빈 막대가 나갈 위험(SCM 티켓 Failure Scenario 3)은 지났다.
- 관련: `TASK-PC-FE-077`(보충 운영자 게이트 — 이 화면의 출처 · `types.ts` 머리 주석), `TASK-PC-FE-277`(`warehouseCode` 를 스키마에 선언한 선례 — «데이터는 도착해 있었고 없던 것은 한 줄»), `TASK-PC-FE-342`(`canWrite` 로 작업 열 제거).

# Goal

로그인 콘솔 `/scm/replenishment` 의 제안 표에서 운영자가 «이 제안이 얼마나 급한가» 를 한눈에 본다 — 발동 순간의 **재주문점** 대비 **발동 재고** 와 **제안 수량** 을 한 줄 막대(+ 숫자 텍스트)로. 정책을 SKU 마다 따로 조회하지 않는다(응답 행에 이미 실려 온다).

# Scope

## In Scope

- **AC-0 사실 (기안 중 확인, 2026-10-26 UTC):**
  - 생산자 계약 `projects/scm-platform/specs/contracts/http/demand-planning-api.md:103` — `reorderPointAtTrigger` (integer): 제안이 발동될 때 SKU 에 `reorder_policy` 가 있었을 때만. 발동 순간 정책값의 **스냅샷**(`triggerAvailableQty <= reorderPointAtTrigger` 의 비교 대상), 이후 정책 PUT · 승인 · 기각으로 **바뀌지 않는다**. 정책 없이 발동한 제안(ALERT 의 wms 경보 임계값 · BATCH 합성값)과 필드 도입 전 행은 **키 자체가 없다**(`:88-91` «Absent keys, not `null` values» — `@JsonInclude(NON_NULL)`). `safetyStock` 은 싣지 않는다. 예시 `:80` `"reorderPointAtTrigger": 10`.
  - 콘솔 스키마 `projects/platform-console/apps/console-web/src/features/scm-replenishment/api/types.ts:32-57` — `SuggestionSchema` 는 `.passthrough()`(`:57`), 모든 필드 optional, 필드 선언은 `triggerAvailableQty: z.number().nullable().optional()`(`:52`) 까지. `reorderPointAtTrigger` 는 선언이 없다 — 값은 `passthrough` 로 이미 도착해 있고, 없던 것은 선언·렌더다(`TASK-PC-FE-277` 과 같은 모양).
  - 표 `.../components/ReplenishmentTable.tsx:79-93` 헤더는 `SKU · 창고 · 공급사 · 추천 수량 · 트리거 가용재고 · 상태 · PO(· 작업)`, `:136-139` 이 `suggestedQty` · `triggerAvailableQty` 를 숫자로 그린다(`repl-row-trigger-${i}` testid). 재주문점 칸·막대 없음.
  - 데이터 경로: `.../api/demand-planning-api.ts:177`(`SuggestionPageSchema.parse`)·`:204`(`SuggestionSchema.parse`) — 새 호출 불필요, 기존 응답 행을 그대로 쓴다.
  - 샘플 세계: `src/shared/sample/fixtures/scm.ts:917-929` `SuggestionSeed` 에 `reorderPointAtTrigger` 없음, 시드 4행 `:942-995`(0001 · 0002 · 0004 = ALERT, 0003 = BATCH `warehouseCode: null`·UUID 모양 `supplierId`). 0003 은 «정책 없이 발동» 에 해당하므로 **키 없음** 표본으로 두는 것이 생산자 의미와 맞다.
  - 소비자 계약 `projects/platform-console/specs/contracts/console-integration-contract.md:1266-1267` — «Each suggestion row shows the `triggerAvailableQty` that explains **why** it was suggested.» 이 문장이 새 동작을 말하도록 바뀌어야 한다(계약 먼저).
  - `projects/platform-console/specs/services/console-web/architecture.md:182` 가 `triggerAvailableQty` 를 언급한다 — 착수 시 읽어 갱신이 필요한지 판단.
- **1. 계약 먼저 (같은 PR 첫 커밋):** `console-integration-contract.md` § 보충 운영자 게이트(`:1266-1267`)에 «행은 `reorderPointAtTrigger`(있을 때) 를 `triggerAvailableQty` · `suggestedQty` 와 함께 한 줄 막대로 보인다 · 키가 없으면(정책 없이 발동했거나 필드 도입 전 행) 막대 없이 `—`» 를 적는다. 생산자 계약은 바꾸지 않는다(소비만).
- **2. 스키마:** `SuggestionSchema` 에 `reorderPointAtTrigger: z.number().nullable().optional()` 한 줄 + 의미 주석(스냅샷 · 키 없음 = 값 없음 · 현재 정책이 아님). `passthrough`·tolerance 불변식은 건드리지 않는다.
- **3. 표:** 「재주문점 · 발동 재고 · 제안」 칸 하나(기존 `트리거 가용재고` 칸을 대체하거나 옆에 추가 — 착수 시 열 폭·모바일 가로 스크롤을 보고 결정, 결정 근거를 PR 에 적는다). 막대는 **숫자 텍스트가 항상 함께** 있다(색·길이만으로 읽히지 않게 — `재주문점 10 · 발동 5 · 제안 50` 류), `role="img"` + `aria-label` 또는 `meter` 의미 중 접근성 규칙(`StatusBadge` 와 같은 수준)에 맞는 쪽. 막대 눈금 계산은 **순수 함수**(`shared/lib` 또는 feature `lib/`)로 분리해 단위 시험 가능하게.
- **4. 샘플 시드:** `SuggestionSeed` 에 `reorderPointAtTrigger: number | null` 추가, 0001 · 0002 · 0004 에 값(발동 재고 ≤ 재주문점 이 되게 — 예: 0001 `triggerAvailableQty: 5` 이면 재주문점 ≥ 5), 0003 은 키 생략(= BATCH 정책 없는 발동). 직렬화 때 `null` 이 아니라 **키를 생략**해 생산자 응답 모양(`NON_NULL`)과 같게 한다.
- **5. 시험:** 순수 함수 단위(정상 · 재주문점 없음 · 재주문점 0 · 발동 재고 > 재주문점인 이상 입력 · 제안 수량 0) · `ReplenishmentScreen.test.tsx`(`:48` 근처 픽스처에 필드 추가 — 값 있음 → 막대 + 텍스트, 키 없음 → `—`·막대 없음) · `demand-planning-api.test.ts`(`:114` 근처 — 새 필드가 파싱을 통과하고 유지됨) · 샘플 스키마 시험(`sample-fixtures-schema-scm.test.ts`, SCM-BE-062 § AC-0 ③ 이 인용) 통과.

## Out of Scope

- 생산자(scm demand-planning-service) 변경 — 계약·구현 모두 `TASK-SCM-BE-062` 로 끝났다. `safetyStock` 을 싣는 것도 아니다(`demand-planning-api.md:103` «intentionally not carried»).
- 현재 정책 조회(`GET /policies/{skuCode}`) — 막대는 **발동 당시 스냅샷**만 그린다. 현재 정책과의 비교·«정책이 바뀌었음» 표시는 하지 않는다.
- 현재 재고(inventory-visibility)를 막대에 섞는 것(SCM 티켓 Out of Scope 와 같은 이유: «발동 재고» 와 다른 값 · 실시간성).
- 승인·기각 동작, 필터, 페이지네이션, 권한(`canWrite`) 변경.
- console-bff — 이 응답을 읽지 않는다(SCM-BE-062 § AC-0 ② 3 의 grep 0).

# Acceptance Criteria

- **AC-1** 계약 `console-integration-contract.md` 의 보충 게이트 절이 «`reorderPointAtTrigger` 한 줄 막대 · 키 없으면 `—`» 를 적고, 이 변경은 그 계약 커밋이 구현 커밋보다 먼저다.
- **AC-2** `SuggestionSchema` 가 `reorderPointAtTrigger` 를 선언하고, 값이 있는 응답은 유지되고 키가 없는 응답(옛 행 · 정책 없이 발동)은 파싱을 깨지 않는다(`.passthrough()` · tolerance 불변식 유지).
- **AC-3** 값이 있는 행은 막대와 함께 **숫자 텍스트**(재주문점 · 발동 재고 · 제안 수량)를 보인다. 키가 없는 행은 막대 없이 `—` 이며 지어낸 숫자(0, `triggerAvailableQty` 로 대체한 재주문점 등)를 그리지 않는다.
- **AC-4** 막대 눈금 계산이 순수 함수로 분리돼 단위 시험이 있고, 재주문점 없음 · 0 · 발동 재고 > 재주문점 · 제안 수량 0 에서 NaN/Infinity/음수 폭을 내지 않는다.
- **AC-5** 샘플 시드가 생산자 모양을 따른다 — 값 있는 행 3, 키 생략 행 1(0003), `null` 로 직렬화하지 않는다. 샘플 스키마 시험 · `sample-coverage-ledger` 초록.
- **AC-6** console-web `lint` · `tsc` · vitest(대상 + 기존 `ReplenishmentScreen` · `demand-planning-api` · `sample-fixtures-schema-scm`) 초록, CI 프런트 단위 초록.
- **AC-7 ⚪ 라이브** 로그인 콘솔 `/scm/replenishment` 에서 새로 발동된 제안 행이 `reorderPointAtTrigger` 값(데모 정책 10)을 막대로 보인다 — 다음 창 측정(서버는 이미 굽혔고 Vercel 배포만 필요). ✅ 2026-10-28 UTC 36차 창 — 아래 CORRECTION

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 보충 운영자 게이트(`:1266-1267`)
- `projects/platform-console/specs/services/console-web/architecture.md:182`
- `projects/scm-platform/tasks/done/TASK-SCM-BE-062-suggestion-carries-reorder-point-at-trigger.md` (후속의 출처)

# Related Contracts

- `projects/scm-platform/specs/contracts/http/demand-planning-api.md` § `GET /api/v1/demand-planning/suggestions` (`:60-104`) — **읽기 전용 소비**, 변경 없음.
- `projects/platform-console/specs/contracts/console-integration-contract.md` (갱신 대상 — 위 AC-1).

# Edge Cases

- **키 없음 = 값 없음** — 정책 없이 발동한 제안(ALERT 경보 임계값 · BATCH 합성값은 재주문점이 아님)과 필드 도입 전 행. 막대 대신 `—`. `null` 도 같은 처리(`nullable().optional()`).
- **발동 재고 > 재주문점인 행** — 생산자 규칙상 `triggerAvailableQty <= reorderPointAtTrigger` 이지만 BATCH 경로 등 예외를 가정해 막대가 넘치지 않게 클램프하고 텍스트는 실제 숫자를 그대로 보인다.
- **재주문점 0** — 0 으로 나누지 않는다(막대 없이 텍스트만 또는 빈 막대).
- **같은 SKU 의 제안마다 재주문점이 다를 수 있다** — 스냅샷이라 정상(`demand-planning-api.md:103`). 행 사이 값이 달라도 «오류» 로 보이지 않게 한다.
- **샘플 방문자** — 같은 표를 샘플 시드로 본다(AC-5). 0003 은 키 없음 표본이다.
- **읽기 전용 운영자**(`canWrite=false`) — 작업 열만 빠지고 막대는 동일.

# Failure Scenarios

- **`triggerAvailableQty` 나 현재 정책으로 재주문점을 대신 채운다** — 지어낸 숫자 → 막대가 거짓말(SCM-BE-062 가 (가-1) 로 막은 것). 키 없음은 `—`.
- **`.strict()` 로 바꾸거나 필드를 required 로 선언한다** — 옛 행·정책 없는 발동 행이 파싱 실패 → 보충 화면 전체 degraded(tolerance 불변식 위반).
- **막대를 색·길이만으로 그린다** — 접근성 실패. 숫자 텍스트가 항상 함께.
- **샘플 시드를 `null` 로 직렬화한다** — 생산자 `NON_NULL` 모양과 갈라져 샘플이 «null 도 온다» 는 잘못된 인상을 굳힌다.
- **계약보다 코드를 먼저 바꾼다** — Change Rule 위반(AC-1).

# Implementation Notes (2026-10-26 UTC)

- **AC-1** 커밋 순서: 계약 `b11c3815a`(`console-integration-contract.md` § 보충 게이트 + `architecture.md:182` 괄호 한 구절) → 구현 `76f860c1d`.
- **AC-2** `types.ts` `SuggestionSchema.reorderPointAtTrigger: z.number().nullable().optional()` + 의미 주석, `.passthrough()` 불변. `demand-planning-api.test.ts`: 값 10 유지 · 키 없는 행도 파싱되고 `'reorderPointAtTrigger' in row === false`.
- **AC-3 · 열 결정 = 대체**: 「트리거 가용재고」 칸을 「재주문점 · 발동 재고 · 제안」 칸으로 바꿨다(열 수 불변, 발동 재고 숫자는 새 칸 텍스트에 남는다). testid `repl-row-trigger-${i}` 는 그 `<td>` 에 유지, 새 `repl-row-bar-${i}`(막대, 키 없으면 렌더 안 함) · `repl-row-bar-text-${i}`. 막대는 `aria-hidden`(`InlineBar` 규칙), 텍스트 항상 동반 `재주문점 10 · 발동 5 · 제안 50`. 키 없음 → 막대 없이 `재주문점 — · 발동 2 · 제안 15`(발동·제안은 실제 값이라 남긴다 — 티켓의 «`—`» 를 «재주문점 자리에 `—`» 로 해석, 지어낸 숫자 없음).
- **AC-4** `features/scm-replenishment/lib/reorder-bar.ts` `reorderBarGeometry` / `reorderBarText` — 눈금 `max(재주문점, 발동) + 제안`, 재주문점은 마커. 재주문점 null/NaN/Infinity/음수 → `null`(막대 없음) · 0 → 마커 생략(0 나눗셈 없음) · 발동 > 재주문점 → 눈금 확장 + `triggerAboveReorderPoint` · 제안 0/null/음수 → 0%. `tests/unit/reorder-bar.test.ts`.
- **AC-5** 샘플 `scm.ts` 0001=10(발동 5) · 0002=12(발동 8) · 0004=10(발동 6), 0003 은 `suggestionBody()` 가 키 생략(목록·상세 둘 다). `sample-fixtures-schema-scm.test.ts` 가 숫자 · `≤ 재주문점` · 0003 키 없음을 단언.
- **AC-6** `tsc` rc=0 · `lint` rc=0 · vitest 대상 4파일 93건 + `tests/unit/sample` 전체(30파일 595건, 구현 에이전트 실행) rc=0.
- **브라우저 실측(로컬 `next dev`, 샘플 방문자, Playwright)**: 1280px — 4행 중 막대 3 · 0003 은 `재주문점 —`, 페이지 가로 넘침 없음. 🔴 **390px 모바일 — 표가 페이지를 넘친다. 단 main 에서도 넘친다**(같은 화면 main `scrollWidth` 554 / 표 536, 감싸는 `overflow-x` 요소 없음). 처음 구현(`min-w-40`)은 +146px 를 더했고 `min-w-24` + 줄바꿈 텍스트로 +82px(636)까지 줄였다 — 남은 증가는 정보가 는 칸의 자연 폭. **표에 가로 스크롤 감싸개가 없는 것은 이 티켓 이전부터의 결함이고 범위 밖**(별도 티켓 후보).
- **AC-7 ⚪ 열림** — 로그인 콘솔 라이브(다음 창, Vercel 배포만 필요).

---

## CORRECTION — 36차 창 라이브 결과 (2026-10-28 UTC)

- **AC-7 ✅.** 36차 창: 굽기 `0777e56ea` → `ami-0bb9778ca4a73e75c`, 02:54:31Z 개설 · 04:46Z `/stop`. 로그인 `/scm/replenishment`: 행 SKU-APPLE-001 · WH01 · SUP-001 · 50 이 「재주문점 10 · 발동 5 · 제안 50」 을 보이고, 막대가 그려진 것을 소유자가 확인했다.
- 이 티켓의 AC 는 체크박스가 없는 목록이라 AC-7 줄 끝에 ✅ 를 덧붙였다.
