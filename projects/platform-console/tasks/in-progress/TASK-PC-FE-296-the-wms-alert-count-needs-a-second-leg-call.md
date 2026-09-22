# Task ID

TASK-PC-FE-296

# Title

운영자 개요의 wms 「알림」 수는 **두 번째 레그 호출**이 있어야 정직해진다 — 그 비용을 지불할지 결정한다
(🔴 진짜 비용은 지연이 아니라 `TASK-PC-FE-295` 가 세운 **«`data` = producer 본문 그대로»** 불변식이다)

# Status

in-progress (2026-09-22 UTC — AC-0 실측 끝 · AC-1 은 소유자 결정 대기)

# Owner

platform-console

# Task Tags

- contract
- code
- test
- decision

---

# Goal

`TASK-PC-FE-295` 가 wms 카드의 「알림」 타일을 **제거**했다. 그 타일은 `WmsDataSchema.inventorySnapshot.alertCount` 를 읽었는데 그 키를 producer 가 **보낸 적이 없어** 운영에서 늘 `—` 였다. 295 는 «영구히 빈 타일은 «알림 0건» 으로 읽혀 «묻지 않았다» 를 숨긴다» 를 이유로 지웠고, **복원 방법과 그 선행을 계약서에 적어 뒀다**.

이 티켓은 그 복원을 **집행하는** 것이 아니라, **지불할지 결정하고 결정대로 집행**하는 것이다.

## 왜 한 번의 호출로는 안 되나 (295 의 실측)

`WmsInventoryReadAdapter.read()` 는 `GET /api/v1/admin/dashboard/inventory` 를 **쿼리 없이** 부른다. producer(`InventoryDashboardController#list` → `PageResponse<InventorySnapshotResponse>`)는 **한 페이지**의 행 + `page.totalElements` 를 준다.

- 그 페이지에서 `lowStockFlag` 를 세면 **페이지 지역 수**다. 전체 알림 수가 아니다.
- 전체를 알려면 `?lowStockOnly=true&size=1` 로 **한 번 더** 부르고 그 응답의 `page.totalElements` 를 읽어야 한다. producer 는 이 파라미터를 **이미 지원한다**(`InventoryDashboardController` 의 `lowStockOnly` 파라미터 · 샘플 픽스처도 같은 필터를 구현하고 있다).

## 🔴🔴 그래서 이 티켓의 비용은 지연이 아니다

`TASK-PC-FE-295` 가 § 2.4.9.1 에 **«`ok` 카드의 `data` 는 producer 의 응답 본문, verbatim»** 을 계약으로 못박았다. 그 불변식이 이번 결함을 막는 장치다 — bff 가 가공을 시작하면 producer 가 문서화한 모양 위에 **bff 전용 모양이 하나 더** 생기고, 둘이 갈라지는 날 아무도 모른다(295 의 결함이 정확히 그 모양이었다).

**두 호출의 결과를 한 카드에 실으려면 그 불변식을 깨야 한다.** 그러므로 이 티켓은 «필드 하나 추가» 가 아니라 **계약 개정**이고, 그 점이 AC-1 의 결정 대상이다.

🔵 295 가 남긴 안전장치: 양쪽 스위트가 함께 읽는 `specs/contracts/fixtures/operator-overview-leg-bodies.json` 이 있으므로, 모양이 바뀌면 **양쪽이 동시에** 움직이거나 빨개진다.

---

# Scope

## In Scope

- AC-0 실측(타일이 아직 없는가 · producer 가 아직 그 필터를 지원하는가 · **두 번째 호출이 실제로 무엇을 더 사는가**)
- 갈래 판단 + 소유자 결정
- 결정이 «지불한다» 면: 계약 § 2.4.9.1 개정(producer 표 + 레그 `data` 모양) → 포트·어댑터·유스케이스 → 공유 픽스처 → 양쪽 테스트 → 샘플 카드

## Out of Scope

- **다른 다섯 레그** — 이 티켓은 wms 한 레그의 호출 수만 다룬다. 다섯 레그의 verbatim 불변식은 그대로다.
- **`dashboard/alerts` producer 표** — wms 에는 별도의 알림 테이블이 있지만 이 레그가 부르는 곳이 아니다. 그것으로 카드를 만들면 «같은 질의» 가 아니게 되고, 샘플 카드와 `/wms/inventory` 화면이 갈라진다(`TASK-PC-FE-287` AC-8 이 지킨 성질).
- **카드 레이아웃 재설계** — 타일 하나를 되살리는 것이지 카드를 다시 그리는 것이 아니다.

---

# Acceptance Criteria

- [x] **AC-0 착수 게이트 (verify-then-act) — 🔴 아래 넷을 재기 전에는 한 줄도 쓰지 않는다.**
      ① **타일이 아직 없는가** — `DomainCardSummaries.tsx` 의 `WmsSummary` 에 `operator-overview-card-wms-alerts` 가 없는지. 🔴 있으면 누군가 이미 되살린 것이므로 **STOP** 하고 그 경위를 적는다.
      ② **producer 가 아직 그 필터를 지원하는가** — `InventoryDashboardController#list` 의 `lowStockOnly` 파라미터를 **파일을 열어** 확인한다(계약서 산문이 아니라 컨트롤러 코드).
      ③ **분모가 비어 있지 않은가** — `lowStockOnly=true` 가 0 을 돌려주는 세계에서는 «타일이 살아났다» 와 «여전히 아무것도 없다» 를 구별할 수 없다. 샘플 픽스처는 저재고 행을 갖고 있다(287 이 non-vacuity 를 보장); **데모/운영 쪽도 0 이 아닌지**는 창이 필요하면 ⚪ + 이유.
      ④ 🔴 **두 번째 호출이 무엇을 더 사는지 «잰다»** — 추정하지 말고: 레그 하나가 더 늘면 이 라우트의 **fan-out 폭·타임아웃 예산·서킷 브레이커 키**가 어떻게 되는지 `OperatorOverviewCompositionUseCase` 와 `LegResilience` 배선을 읽고 적는다. (🔵 이 저장소의 규율: 한 번의 로컬 측정을 성질로 승격하지 않는다 — 지연 수치를 적으려면 그것이 **단일 표본**임을 함께 적는다.)
- [ ] **AC-1 — 소유자 결정: 지불하는가.** 갈래를 **비용과 함께** 적어 올린다(내 추천은 이 파일에 적되, 추천은 결정이 아니다):
      **ⓐ 지불한다** — 두 번째 호출을 추가하고 카드 `data` 를 **합성 객체**로 만든다. 🔴 대가 = § 2.4.9.1 의 verbatim 불변식이 wms 에 대해 깨지고, 그 예외가 다음 레그의 선례가 된다.
      **ⓑ 지불하지 않는다** — 타일은 영구히 없다. 🔵 대가 = 운영자가 저재고 건수를 개요에서 못 본다(`/wms/inventory` 의 `lowStockOnly` 필터로는 볼 수 있다). 이 경우 **계약서의 «의도적 부재» 문단을 «결정됨» 으로 승격**하고 이 티켓을 닫는다 — 🔴 «미결» 로 남기면 다음 사람이 같은 질문을 다시 연다.
      **ⓒ 다른 축으로 답한다** — 예: 타일을 「저재고 행 수」가 아니라 **`/wms/inventory?lowStockOnly=true` 로 가는 링크**로 만든다(숫자 없이). 호출 0, 불변식 유지, 운영자는 한 클릭으로 도달.
- [ ] **AC-2 (ⓐ 선택 시) 계약 먼저** — § 2.4.9.1 의 **producer 표에 행을 먼저 추가**하고(295 가 계약서에 적어 둔 순서), `ok` 카드 `data` 모양 표의 wms 행을 합성 모양으로 고친다. 🔴 **왜 이 레그만 예외인지**를 그 자리에 적는다 — 적지 않으면 다음 레그가 «wms 도 했으니» 를 근거로 삼는다.
- [ ] **AC-3 (ⓐ) 구현** — `WmsInventoryReadPort` 에 두 번째 읽기, 어댑터에 `?lowStockOnly=true&size=1`, 유스케이스가 둘을 합성. 🔴 **두 번째 호출의 실패가 카드 전체를 죽이면 안 된다** — 재고 행 수는 살아 있는데 알림만 못 읽은 상태가 표현돼야 한다(빈 값과 «못 물었다» 를 구별).
- [ ] **AC-4 (ⓐ) 공유 픽스처 + 양쪽 테스트** — `operator-overview-leg-bodies.json` 의 wms 항목을 합성 모양으로 옮기고, `leg-body-contract.test.tsx`(그린다) 와 `OperatorOverviewLegBodyContractTest`(그대로 간다) **양쪽이 같이** 움직인다. 🔴 한쪽만 고치면 다른 쪽이 빨개져야 하고, **그것이 295 가 만든 장치가 동작한다는 증거**다 — bite 로 확인한다.
- [ ] **AC-5 (ⓐ) 샘플 동반 이동** — `dashboards.ts` 의 wms 카드와 `sample-overview-cards-match-lists.test.ts` 의 wms 칸. 🔴 그 칸은 **동일성 비교**(295)이므로 합성 모양이면 «어느 질의의 본문과 같은가» 를 두 질의로 나눠 적어야 한다.
- [ ] **AC-6 대조군** — 나머지 다섯 레그의 `data` 는 **여전히 producer 본문 그대로**다. 전수 칸(`leg-body-contract`)이 그것을 지킨다.

---

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 — **Composed producers 표** · **`ok` 카드 `data` 모양 표**(295 신설) · **「wms 알림 수는 의도적으로 부재」 문단**(이 티켓이 해소하거나 확정한다)
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.1 `GET /api/v1/admin/dashboard/inventory`
- `projects/platform-console/specs/services/console-bff/architecture.md`
- `projects/platform-console/tasks/done/TASK-PC-FE-295-*` — 타일을 제거한 티켓(제거 이유·복원 방법 전문)
- `projects/platform-console/tasks/done/TASK-PC-FE-287-*` — wms 샘플 카드가 저재고 행을 세던 원래 방식과 그 non-vacuity 규율

# Related Contracts

- `console-integration-contract.md` § 2.4.9.1 — 🔴 ⓐ 를 고르면 이 티켓이 **verbatim 불변식의 예외를 신설**한다
- `admin-service-api.md` § 1.1 — producer 는 **불변**(`lowStockOnly` 는 이미 있다)

# Edge Cases

- **저재고 행이 0** — 「알림 0」이 **정직한** 값이 되는 경우. 🔴 «못 물었다» 와 화면에서 구별돼야 한다(295 가 지운 이유가 바로 이 구별의 부재였다).
- **두 번째 호출만 실패** — 재고 행 수는 있고 알림만 없다. 카드 전체가 `degraded` 로 떨어지면 **덜 정직해진다**(멀쩡한 숫자를 숨긴다).
- **두 호출 사이에 데이터가 바뀐다** — 두 수가 같은 순간의 것이 아니다. 개요 카드의 `asOf` 는 **합성 요청 시각**이지 레그 응답 시각이 아니므로(§ 2.4.9.1), 이 비동기를 카드가 주장하지 않도록 한다.
- **`size=1` 인데 producer 가 size 를 무시** — `page.totalElements` 만 읽으므로 무해하지만, 무시한다면 본문이 커진다. 실측으로 확인한다.

# Failure Scenarios

- **AC-0 없이 착수** → 타일이 이미 복원돼 있거나 필터가 사라진 세계에서 없는 일을 한다.
- **계약을 안 고치고 구현** → 295 가 막 세운 불변식이 **코드에서만** 깨지고 계약서는 여전히 verbatim 이라고 말한다. 다음 사람이 계약을 믿고 또 틀린다(이번 결함의 원인 그대로).
- **예외의 이유를 안 적음** → 다음 레그가 «wms 도 두 번 부른다» 를 선례로 삼아 fan-out 이 조용히 자란다.
- **공유 픽스처를 한쪽만 고침** → 295 의 장치가 무력화되고, 결함이 다시 두 스위트 사이로 숨는다.
- **ⓑ 를 고르고 계약서를 안 고침** → «미결» 로 남아 다음 사람이 같은 질문을 다시 연다(이 저장소가 이름 붙인 «게이트 없는 미결은 반드시 다시 열린다»).

# Test Requirements

- console-web: `pnpm lint` · `npx tsc --noEmit` · `pnpm test` — 각각 **독립 실행** + `rc=$?` 명시(파이프 금지)
- console-bff: `./gradlew :projects:platform-console:apps:console-bff:test` — 🔴 `rc=0` 을 증거로 쓰지 말고 **실행된 칸 수**를 결과 XML 에서 셀 것

# Definition of Done

- AC-0 의 넷이 적혀 있고, AC-1 의 갈래가 **소유자 결정**으로 닫혔다.
- ⓐ 면 AC-2~AC-6 이 전부 닫혔고, ⓑ·ⓒ 면 **계약서가 그 결정을 말하고** 이 티켓이 닫힌다(둘 다 «아무것도 안 함» 이 아니다).

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus** — 코드량은 작지만 **계약 불변식의 예외를 신설할지**가 본체이고, 그 판단이 이 티켓의 전부다.

---

# 🟢 AC-0 착수 게이트 — 넷을 다 쟀다 (2026-09-22 UTC · 분석=Opus 5)

🔴 **창도 SSM 도 쓰지 않았다.** 넷 다 저장소의 코드·설정에서 읽었고, ④ 는 «추정하지 말고 배선을 읽어라» 는 이 AC 의 지시 그대로 **구성된 예산으로 산술**을 했다(측정된 지연이 아니다 — 아래 § 한계).

## ① 타일이 아직 없는가 — 🟢 **없다** (STOP 조건 아님)

`operator-overview-card-wms-alerts` 를 `console-web/src` 전수에서 grep: **0건**. `WmsSummary` 는 「재고 행 수」 한 타일만 그린다(`page.totalElements`). ⇒ 누군가 먼저 되살린 상태가 아니므로 이 티켓은 유효하다.

## ② producer 가 아직 그 필터를 지원하는가 — 🟢 **지원한다, 그리고 질의까지 도달한다**

계약서 산문이 아니라 **컨트롤러와 리포지터리 코드**로 확인했다:

- `InventoryDashboardController:37` — `@RequestParam(required = false, defaultValue = "false") boolean lowStockOnly`
- `:43` — 그 값이 `repository.search(...)` 로 **전달된다**
- `InventorySnapshotRepository:18` — JPQL 이 **실제로 쓴다**: `AND (:lowStockOnly = FALSE OR i.lowStockFlag = TRUE)`

🔵 **파라미터가 있다는 것보다 강한 확인이다** — 이름만 받고 버리는 파라미터였다면 `page.totalElements` 가 전체 행 수로 나와 **조용히 틀린 알림 수**가 됐을 것이다. 질의가 거르므로 `lowStockOnly=true` 의 `page.totalElements` = **저재고 행의 전체 수**가 맞다.

## ③ 분모가 비어 있지 않은가 — 🟢 **샘플은 양방향으로 비어 있지 않다** / ⚪ **운영·데모는 못 쟀다**

샘플 픽스처(`fixtures/wms.ts`)의 `INVENTORY`: **전체 3행 · `lowStockFlag: true` 1행.**

- `1 > 0` ⇒ 「살아났다」와 「여전히 아무것도 없다」를 **구별할 수 있다**.
- `3 ≠ 1` ⇒ 「행 수」와 「알림 수」가 **다른 숫자**다. `TASK-PC-FE-295` 가 이 두 개념을 갈라놓은 것이 요점이므로, 같은 값이면 그 구별을 증명할 수 없다.

⚪ **데모/운영 쪽 분모는 이 세션이 못 쟀다** — 창이 필요하고, 잔여 예산이 **30분**(`/status` 실측 `used 1170 / budget 1200`, 2026-09-22 UTC)이라 열지 않았다. 🔵 다만 이 항목은 «구현이 가능한가» 가 아니라 «판정이 가능한가» 에 걸리는 조건이고, 샘플 세계가 비어 있지 않으므로 **단위 칸으로는 판정이 선다.**

## ④ 🔴🔴 두 번째 호출이 **무엇을 더 사는가** — 배선을 읽었다

### (a) fan-out 폭은 **안 늘어난다** — 늘릴 수가 없다

`OperatorOverviewCompositionUseCase.compose` 는 레그를 `EnumMap<DomainTarget, Supplier<CompositionLeg>>` 로 만들어 `engine.fanOut(tenantId, legBodies)` 에 넘긴다. **키가 도메인이라 도메인당 슬롯이 하나**다.

⇒ 두 번째 호출은 **7번째 병렬 레그가 될 수 없고**, WMS 레그 **본문 안에서 순차**로 돌아야 한다. 즉 «병렬이라 공짜» 가 아니라 **그 레그의 지연에 그대로 더해진다.**

### (b) 타임아웃 예산은 **안 늘어난다** — 벽이 하나다

| 상수 | 값 | 자리 |
|---|---|---|
| `RestClientConfig.PER_LEG_TIMEOUT` | **2초** | connect · read, **호출당** |
| `CompositionEngine.COMPOSITION_TIMEOUT` | **5초** | **합성 전체**. 초과한 레그는 `degraded / TIMEOUT` |
| `resilience.retry.max-attempts` | **2**(재시도 1회) | `backoff-base-ms: 150`, 지터 ±50% |

🔴 **재시도가 레그 본문 «전체» 를 감싼다** — `Resilience4jLegResilienceAdapter:102` 의 `Retry.decorateSupplier(gate.retry(), call)` 에서 `call` 은 `resilience.execute(domain, routeLabel, call)` 이 받은 **레그 본문 통째**다. ⇒ 재시도는 **두 호출을 다시 돈다.**

**그래서 최악 지연 산술:**

| | 최악 | 5초 벽 |
|---|---|---|
| **오늘** (1호출) | `2 + 0.15 + 2` = **4.15초** | 🟢 **자기 최악을 견딘다** |
| **ⓐ 순차 2호출** | `(2+2) + 0.15 + (2+2)` = **8.15초** | 🔴 **못 견딘다** |

🔴🔴 **이것이 이 AC 의 답이다.** 두 번째 호출은 WMS 레그를 «자기 최악을 견디는» 상태에서 «구조적으로 못 견디는» 상태로 옮긴다. 그리고 초과하면 카드가 `TIMEOUT` 으로 degrade 되므로 — **지금 잘 나오는 「재고 행 수」까지 함께 잃는다.**

🔵 **완화를 시도해도 안 맞는다**: 두 번째 호출을 재시도 **밖**으로 빼도 `4.15 + 2 = 6.15초` 로 여전히 5초를 넘는다. 5초 안에 넣으려면 두 번째 호출에 **1초 미만의 별도 상한**을 주거나, `COMPOSITION_TIMEOUT` 을 올려야 하는데 **그 벽은 여섯 레그가 공유**한다(한 카드를 위해 전체 라우트의 예산을 바꾸는 일이다).

### (c) 서킷 키는 **안 늘어난다** — 그래서 더 나쁘다

`CompositionEngine:288` 이 `resilience.execute(domain, routeLabel, call)` 로 **레그 본문 전체**를 감싸고, 키는 `(domain, routeLabel)` = `(WMS, operator-overview)` **하나**다.

⇒ 두 호출이 **같은 차단기를 공유한다**. 알림 호출이 반복 실패하면 차단기가 열리고, 그 다음부터 **레그 본문이 아예 호출되지 않아** 행 수도 `CIRCUIT_OPEN` 으로 degrade 된다.

🔴 **그래서 AC-3 이 요구한 «두 번째 호출의 실패가 카드 전체를 죽이면 안 된다» 는 기본 배선으로 충족되지 않는다.** 충족하려면 두 번째 호출을 레그 본문 안에서 `try/catch` 로 감싸 «알림만 미상» 으로 낮춰야 하고, **그러면 그 호출은 차단기·재시도 보호 밖**에 놓인다 ⇒ 교환은 **«보호받되 전체를 죽인다» vs «전체를 안 죽이되 보호 밖»** 이다.

---

# 🔵 AC-1 로 올리는 것 — 갈래가 셋에서 «사실상 둘» 로 좁혀졌다

AC-0 이 **ⓐ 를 있는 그대로는 성립시키지 못한다.** 소유자가 ⓐ 를 고르려면 **딸린 결정**이 하나 더 붙는다:

| 갈래 | AC-0 이후 상태 |
|---|---|
| **ⓐ 지불** | 🔴 **그대로는 불가** — 5초 벽을 넘는다. 고르려면 ⓐ-1(두 번째 호출에 1초 미만 상한 + 보호 밖 `try/catch`) 또는 ⓐ-2(`COMPOSITION_TIMEOUT` 인상 — **여섯 레그 공유 예산 변경**) 중 하나를 **함께** 골라야 한다. 그리고 verbatim 불변식 예외 신설은 그대로 남는다 |
| **ⓑ 미지불** | 변함없이 성립. 계약서의 «의도적 부재» 를 **«결정됨» 으로 승격**하고 닫는다 |
| **ⓒ 링크** | 🔵 **AC-0 이 오히려 강화했다** — 호출 0이라 타임아웃·서킷·불변식 **셋 다 안 건드린다**. 운영자는 `/wms/inventory?lowStockOnly=true` 한 클릭으로 **정확한 수**를 본다(개요가 근사치를 주는 것보다 정직하다) |

🔵 **내 추천은 ⓒ 다.** 근거는 취향이 아니라 위 산술이다 — ⓐ 는 «알림 수 하나» 를 위해 **지금 정상인 타일을 잃을 경로**를 만들고, 그것을 막으려면 보호 밖으로 빼거나 여섯 레그의 공유 예산을 바꿔야 한다. 🔴 **그래도 이것은 추천이지 결정이 아니다** — 개요에 숫자가 보이는 것의 값어치는 소유자가 정한다.

# ⚪ 이 측정이 **안 잰 것** (축소하지 않는다)

- **실제 지연** — 위 표는 **구성된 상한으로 만든 최악 산술**이지 측정이 아니다. 실제 p99 는 훨씬 작을 수 있고, 그러면 ⓐ 가 «평소엔 멀쩡하고 나쁜 날에만 카드를 잃는» 모양이 된다. 🔴 이 저장소의 규율상 **단일 측정을 성질로 승격하지 않듯, 최악 산술도 평상 동작으로 읽으면 안 된다** — 둘은 다른 질문이다.
- **데모/운영의 저재고 분모**(③) — 창 필요. 잔여 30분이라 열지 않았다.
- **`dashboard/alerts` 쪽 경로** — 범위 밖(Out of Scope). 이 레그가 부르는 곳이 아니다.
