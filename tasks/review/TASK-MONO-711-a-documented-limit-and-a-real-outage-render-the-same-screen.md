# Task ID

TASK-MONO-711

# Title

🔴 **기록된 한계와 진짜 장애가 같은 화면으로 나온다** — 촬영 사전점검은 그 화면을 통과시키고, 저하 판정은 원인을 지운다

# Status

review (2026-09-18 UTC — AC-0 ~ AC-4 닫힘)

# Owner

monorepo

# Task Tags

- console
- capture
- observability
- portfolio

---

# Goal

## 🔴 이 티켓은 한 번 틀리게 기안됐다 — 그 정정이 Goal 의 절반이다

첫 판(2026-09-18 UTC, 같은 날 아침)은 제목이 *"묶음 여섯이 전부 `ready` 인데 콘솔 랜딩이
저하다"* 였고, 2026-09-18 데모 창에서 `/` · `/dashboards/overview` · `/onboarding` 이
`operator-overview-bff-unavailable`, `/dashboards/health` 가 `domain-health-bff-unavailable`
로 찍힌 것을 **새 결함**으로 읽었다.

**그 프레임은 무관했다.** 콘솔은 그 묶음에서 뜨지 않는다 — `ADR-MONO-067` 단계 3
(`TASK-MONO-627`)이 방문자 경로를 **Vercel** 로 옮겼고, `console-bff` 는 `TASK-MONO-362` 가
엣지 노출 금지(`api-gateway-policy.md` L14)에 따라 **Traefik 라우터를 일부러 없앤** 서비스다.
그래서 Vercel 콘솔은 BFF 를 부를 수 없고, 그 사실과 **영향 레그 목록까지**
[`infra/demo/console-vercel.override.yml`](../../infra/demo/console-vercel.override.yml) § «이
억제가 영구히 열화시키는 것» 과 `TASK-MONO-585` § 알려진 한계가 이미 적어 두었다.
고치려면 BFF 에 공개 경로를 주거나 합성을 콘솔 서버로 옮겨야 하고 **둘 다 ADR 사안**이다.

🔵 잡아 준 것은 첫 판 자신의 AC-0(«착수 게이트: 모집단부터 다시 재라»)이었다. 🔴 그러나
**기안 시점에 했어야 할 일**이다 — 이 저장소의 «내 티켓의 『스펙에 적혀 있다』를 열어라» 축.

## 그래서 이 티켓이 실제로 드는 것 — 어디에도 없는 셋

기전은 기록돼 있는데, **그 기전이 남기는 자국**은 아무도 안 들고 있다.

### ① 촬영 사전점검이 저하 화면을 통과시킨다

`scripts/capture-portfolio.mjs` 는 찍기 전에 `/dashboards/overview` 한 장을 열어 «운영자
화면이 맞는지» 본다. 2026-09-18 실행 로그:

```
[portfolio] ✔ 사전 확인 /dashboards/overview (본문 220자)
```

**그 220자가 저하 문구 자체다.** 사전점검은 거부·빈값만 보고 **저하를 안 본다**.
🔵 `TASK-MONO-707` 이 고친 저하 술어(`judgeDegraded()`)는 **이미 있다** — `probe` 가 안 부를 뿐이다.

### ② `bffUnavailable` 은 catch-all 이라 «영구 한계» 와 «진짜 장애» 가 구별되지 않는다

`features/operator-overview/api/operator-overview-state.ts` 는 `400 NO_ACTIVE_TENANT` 와
`401` 만 갈라내고 **나머지 전부**를 하나로 떨군다:

```ts
return { overview: null, noTenant: false, unauthorized: false, bffUnavailable: true };
```

여기 떨어지는 것: 502 · 500 · 타임아웃 · DNS 실패 · `ApiError` 가 아닌 예외 전부.
⇒ **Vercel 에서는 이 화면이 상시 그 상태다.** 그래서 «일시적으로 불러올 수 없습니다» 라는
문구가 상시 거짓말이 되고, 언젠가 **진짜로** BFF 가 죽어도 아무도 구별하지 못한다.
🔴 신호가 상시 켜져 있으면 그 신호는 꺼진 것과 같다.

### ③ 저하가 **마커가 아니라 부재**로 렌더되는 자리가 있다 — `/console`

위 override 는 영향 레그에 `/console` 을 **명시**한다(`getDomainHealthState()` 를 부른다).
그런데 2026-09-18 촬영은 `/console` 을 **저하 아님**으로 판정했다(본문 436자, 마커 0개).
이미지를 열어 보니 서비스 카탈로그 6장이 멀쩡히 뜨고 **도메인 상태 카드가 아예 안 보인다**
— 실패가 문구로도 마커로도 남지 않고 **원소가 사라지는 것**으로 표현됐다.
⇒ 저하 술어(문구 ∪ 마커)의 **구조적 사각**이다. 🔵 `TASK-MONO-707` 이 고른 합집합은 옳았지만
«아무것도 안 그린다» 는 두 날개 어느 쪽에도 안 걸린다.

## 포트폴리오에 대한 결론 (`TASK-MONO-648` · `667` 의 입력)

**콘솔 랜딩(`/` · `/dashboards/overview`)은 스크린샷이 될 수 없다 — 일시적이 아니라
아키텍처 결정이 바뀌기 전까지 영구히.** `648` AC-2 §336 의 «`/dashboards/overview` 를 대체할
4번째 장을 소유자가 고른다» 가 **취향이 아니라 요건**이 된다. 🔵 `/console` 은 보기에는
후보지만 ③ 때문에 **빠진 절이 있는 장**이므로, 싣는다면 그 사실을 알고 싣어야 한다.

---

# Scope

## 포함

- ① 사전점검이 `judgeDegraded()` 를 부르고, 저하면 **경고**한다.
- ② `bffUnavailable` 이 **사유를 들고 다니게** 한다(상태코드/예외종류 수준). 화면 문구도
  «일시적» 이 상시 거짓인 환경에서 무엇을 말할지 다시 정한다.
- ③ `/console` 의 도메인 상태 카드가 **실패를 표현**하게 한다(사라지지 않는다), 또는 그것이
  의도라면 그 의도를 술어가 아는 형태로 적는다.

## 제외

- 🔴 **`console-bff` 를 Vercel 에서 닿게 만드는 것** — 엣지 노출 금지에 정면으로 걸리거나
  합성을 옮기는 일이고 **ADR 사안**이다. 이 티켓은 그 결정을 하지 않는다.
- 저하 술어의 문구·마커 목록 자체(`TASK-MONO-707` 에서 끝났다). ③ 은 그 목록의 문제가
  아니라 «아무것도 안 그린다» 는 **세 번째 모양**이다.
- `/wms/operations` 의 `wms-operations-settings-degraded` — 같은 창에서 같이 저하였지만
  BFF 레그가 아니다. 🔴 섞지 마라(«컨테이너 블로커 ≠ 내용물 블로커»).

---

# Acceptance Criteria

- [x] **AC-0 — 착수 게이트: 이 티켓의 전제부터 다시 읽어라.** 🔴 첫 판이 «기록된 한계» 를
      «새 결함» 으로 읽고 무너졌다. 착수 시점에 ⓐ `console-vercel.override.yml` § 영향 레그와
      ⓑ `TASK-MONO-585` § 알려진 한계를 **열어서** 위 ①②③ 이 거기 **없는지** 다시 확인해라.
      있으면 그 칸을 지워라 — 남은 것으로 티켓을 다시 좁히는 것이 착수다.
- [x] **AC-1 — ① 사전점검.** `probe` 가 `judgeDegraded()` 를 부르고 저하면 경고를 찍는다.
      🔴 **막지는 마라** — 저하를 **일부러** 찍는 측정이 있다(`TASK-MONO-707` AC-3 이 그랬다).
      경고 + 매니페스트 기록이지 중단이 아니다. **bite**: 저하 픽스처에서 경고가 안 나오면
      `--self-test` 가 빨개진다.
- [x] **AC-1b — 🔴 그 bite 를 CI 가 돌린다 (착수 중에 추가한 칸).** AC-1 을 구현하면서
      `capture-portfolio.mjs --self-test` 를 **어떤 워크플로도 부르지 않는다**는 것을 발견했다.
      즉 `TASK-MONO-707` 이 만든 16칸은 **사람이 손으로 돌릴 때만** 빨개졌다. AC-1 의 «bite» 는
      그 상태로는 요건을 만족하지 못한다 — 아무도 안 돌리는 가드는 없는 가드보다 나쁘다.
      🔵 이 칸은 원래 Scope 에 없었다. 범위를 조용히 넓히지 않으려고 **칸으로 적고** 닫는다.
- [x] **AC-2 — ② 사유를 들고 다닌다.** `bffUnavailable` 옆에 원인 구분(최소:
      `transport` / `status:<code>` / `timeout`)을 남긴다. 🔴 **상시 켜진 신호를 구별 가능하게
      만드는 것이 목적**이지 화면을 예쁘게 하는 것이 아니다. 화면 문구는 그 다음 문제다.
      **bite**: 서로 다른 실패를 주입하면 서로 다른 사유가 나온다(같은 값이면 빨강).
- [x] **AC-3 — ③ 부재 대신 표현.** `/console` 의 도메인 상태 카드가 실패를 **보이게** 한다.
      판정은 촬영 매니페스트다 — 그 장이 `degraded` 로 잡히거나, 잡히지 않는 것이 의도라면
      **왜 그것이 옳은지**를 이 티켓에 적고 칸을 ⚪ 로 닫는다.
- [x] **AC-4 — 648 에 넘긴다.** 「콘솔 랜딩은 영구히 후보가 아니다」를 `TASK-MONO-648`
      AC-2 §336 옆에 **한 줄로** 적는다. 🔴 이 티켓에서 큐레이션을 고르지 마라 — 소유자 결정이다.

---

# Related Specs

- [`infra/demo/console-vercel.override.yml`](../../infra/demo/console-vercel.override.yml) § «이 억제가 영구히 열화시키는 것»
- `tasks/done/TASK-MONO-585-*.md` § 알려진 한계 — `console-bff` 는 Vercel 에서 닿지 않는다
- `TASK-MONO-362` — BFF 의 Traefik 라우터 제거 (`platform/api-gateway-policy.md` L14)
- `scripts/capture-portfolio.mjs` § 사전 확인(`probe`) · `judgeDegraded()`
- `projects/platform-console/apps/console-web/src/features/operator-overview/api/operator-overview-state.ts`
- `tasks/done/TASK-MONO-707-*.md` § 창 실측 — 저하 술어가 문구 ∪ 마커인 이유
- `tasks/in-progress/TASK-MONO-648-*.md` § AC-2 §336

# Related Contracts

- `console-integration-contract.md` § 2.4.9.1 (운영 개요 합성). 🔴 AC-2 가 봉투를 바꾸면 계약 먼저.

---

# Edge Cases

| 경우 | 다룸 |
|---|---|
| 로컬 도커에서는 BFF 가 닿는다 | 🔴 그래서 로컬 초록은 이 축의 증거가 아니다. 판정 트리를 명시해라 |
| 사유를 화면에 노출 | 🔴 방문자에게 내부 상태코드를 보이면 안 된다 — AC-2 의 사유는 **로그·매니페스트용**이 기본이다 |
| 경고가 시끄러워진다 | Vercel 에서 상시 3레그가 저하다 ⇒ 경고가 매 실행 3줄이다. 그것이 **정상 상태**라면 그렇게 적어라(억제하지 말고) |
| `/console` 카드가 원래 «없으면 안 그린다» 설계 | AC-3 이 ⚪ 로 닫히는 경로. 🔴 단 그 이유를 적어야 닫힌다 |

# Failure Scenarios

1. **문구만 고친다**(«일시적» 삭제) → ②의 구별 불가가 그대로 남는다. 신호는 여전히 상시 켜져 있다.
2. **사전점검을 «저하면 중단»으로 만든다** → 저하를 일부러 찍는 측정이 불가능해진다(AC-1 이 못박은 이유).
3. **①②③ 을 한 덩어리로 고치려 한다** → 셋은 다른 파일·다른 층이다. 쪼개서 머지해라.
4. **또 «기록된 한계» 를 결함으로 읽는다** → AC-0 이 그것을 막는다. 🔴 이 티켓이 이미 한 번 그랬다.

---

# 분석 / 구현 권장

(분석=Opus 5 / 구현 권장=Sonnet — ①③ 은 한 파일씩이고 ②는 타입 하나 넓히는 일이다.
🔴 단 AC-0 의 재확인은 **읽는 일이지 고치는 일이 아니다** — 거기서 티켓이 또 줄어들 수 있다)

---

# 🟢 AC-0 — 착수 게이트 결과 (2026-09-18 UTC)

이 티켓 자신이 요구한 «그 두 문서를 열어서 ①②③ 이 거기 **없는지** 다시 확인해라» 를 돌렸다.

| 문서 | ①②③ 이 있나 |
|---|---|
| `infra/demo/console-vercel.override.yml` (§ 영향 레그 · § 영구 열화) | **없다** (`probe`·`judgeDegraded`·catch-all·«부재» 어느 것도 0건) |
| `TASK-MONO-585` § 알려진 한계 (26줄) | **없다** (같은 질의) |
| 저장소 전체 «사전 확인» 소유자 | 이 티켓과 스크립트 자신뿐 |

⇒ 셋 다 살아남았다. 🔵 그 문서들이 드는 것은 **기전**(BFF 가 닿지 않는다)이고, 이 티켓이 드는
것은 그 기전이 남기는 **자국**이다 — 겹치지 않는다.

# 🟢 AC-1 · AC-1b — ① 사전점검이 저하를 본다 (분석·구현=Opus 5)

## 고친 것

| 무엇 | 어떻게 |
|---|---|
| 판정부 분리 | `sanityCheck()` 에서 **판정만** `judgeProbe(page, probePath)` 로 뗐다. 🔵 붙여 두면 self-test 가 픽스처로 이 배선을 못 문다 — «판정기가 있나» 가 아니라 «프로브가 그것을 부르나» 가 이 티켓의 질문이다 |
| 저하 판정 | `judgeProbe` 가 거부·문구 검사 뒤 `judgeDegraded(page, text)` 를 부르고 `degraded`·`degradedBy` 를 **결과에 얹는다** |
| 🔴 막지 않는다 | `ok` 는 건드리지 않는다. 저하를 `ok:false` 로 만들면 «저하를 일부러 찍는» 측정이 불가능해진다 — `TASK-MONO-707` AC-3 이 정확히 그것을 했다(재무를 내리고 `/ledger` 가 degraded 로 잡히는지 봤다) |
| 경고 | 호출부가 `⚠ 사전 확인 화면이 **저하 상태**입니다 — <경로> (<마커들>)` 를 찍고 «찍기는 계속합니다» 를 명시한다 |
| 기록 | 매니페스트에 `probes: [{app, path, chars, degraded, degradedBy}]` — 로그는 흘러가고, «그때 프로브가 저하였나» 는 **나중에 큐레이션할 때** 묻게 되는 질문이다 |

## self-test — **20/20** (기존 16 + 프로브 4), rc=0

| 칸 | 기대 | 왜 |
|---|---|---|
| `probe-degraded-marker` | `ok:true · degraded:true` | 2026-09-18 창의 그 화면 |
| `probe-degraded-copy-only` | `ok:true · degraded:true` | 마커 없이 문구만 (합집합의 다른 날개) |
| `probe-healthy` | `ok:true · degraded:false` | 🔵 대조군 — 없으면 «항상 저하» 가 통과한다 |
| `probe-denied-still-blocks` | `ok:false` | 🔴 **거부는 여전히 막는다** — 저하를 통과시키는 것과 거부를 통과시키는 것은 다른 일이다 |

🔴 **각 칸이 `ok` 와 `degraded` 를 둘 다 단언한다.** 하나만 보면 한쪽 결함이 샌다:
`degraded` 만 보면 누가 저하를 차단으로 바꿔도 초록이고, `ok` 만 보면 배선을 떼어내도 초록이다.
🔵 공허성 하한도 넓혔다 — 프로브 칸은 «저하 ≥1 · 정상 ≥1 · 차단 ≥1» 이 아니면 멈춘다.

## bite — 두 방향, **다른 모양으로** 물린다

| 주입 (1건 단언 후) | 결과 |
|---|---|
| A `judgeProbe` 에서 `judgeDegraded` 호출 제거(= 결함 원상복구) | **18/20**, ✗ 두 칸이 `degraded:false` 로 |
| B 저하면 `ok:false` 로 막게 함(= AC-1 이 금지한 방향) | **18/20**, ✗ 같은 두 칸이 `ok:false` 로 |

🔵 A 와 B 가 **같은 칸을 다른 값으로** 틀리게 만든다 — 그래서 이 칸들은 «배선 없음» 과
«과잉 차단» 을 구별한다. `probe-healthy`·`probe-denied-still-blocks` 는 양쪽에서 초록이다.

## AC-1b — 🔴 그 bite 를 **아무도 안 돌리고 있었다**

`grep -rn capture-portfolio .github/` → **0건**. `TASK-MONO-707` 은 CI 가 돈다고 주장한 적이
없고(`TASK-MONO-648` 도 «손으로 돌린 증거» 로 적었다) — 즉 **거짓 주장은 없었지만 가드도 없었다.**
⇒ `ci.yml` 의 `Frontend E2E smoke` 잡에 스텝을 더했다. 🔵 그 잡이 바로 위에서 **chromium 을
설치**한다(self-test 는 실제 브라우저에 `setContent` 로 픽스처를 띄운다). 창도 백엔드도 필요 없다.
🔴 Playwright 미설치면 스크립트가 `rc=3` 을 내는데, CI 에서 그것은 «고장이 아님» 이 아니라
**이 잡의 배선이 깨진 것**이므로 빨간 것이 맞다 — 그 구별을 스텝 주석에 적었다.
🔵 `scripts/check-required-check-names.sh` rc=0 — **잡이 아니라 스텝**을 더했으므로 등록된
체크 이름 넷은 그대로다(잡 이름을 바꾸면 조용히 BLOCKED 가 된다는 그 함정을 건드리지 않았다).

## ⚪ 안 한 것

- **②③④ 는 안 건드렸다** — 다른 파일·다른 층이고, 이 티켓의 Failure 3 이 «한 덩어리로 고치려
  한다» 를 막고 있다. 티켓은 `in-progress` 로 남는다.
- self-test 는 **술어와 배선**을 재지 실제 콘솔이 그 마커를 다는지는 재지 않는다(그건 창이다).
- 🔴 **새 CI 스텝이 실제로 도는 것은 이 PR 의 CI 가 처음이다** — 그 초록이 이 칸의 실전 판정이다.

---

# 🟢 AC-2 — ② `bffUnavailable` 이 **사유를 들고 다닌다** (2026-09-18 UTC · 분석·구현=Opus 5)

## 🔴 대상이 **두 파일**이었다

착수해서 처음 안 것: 같은 catch-all 모양을 **두 기능이 복제**하고 있다 —
`features/operator-overview/api/operator-overview-state.ts` 와
`features/domain-health/api/domain-health-state.ts`. 한쪽만 고치면 이 저장소의
«한 사실이 두 집을 갖는다» 축을 그대로 밟는다. ⇒ 분류기를 **공유 모듈 하나**로 두고
둘이 같이 쓰게 했다: `shared/api/unavailable-cause.ts`.

🔵 `domain-health-api.ts:55` 의 맨 `catch {}` 는 **건드리지 않았다** — JSON 파싱 방어이고
기본값이 명시적이며 주석이 이유를 적어 뒀다. catch-all 문제가 아니다(모집단을 잘못 넓히지 않았다).

## 분류

| 갈래 | 언제 | 무엇을 싣나 |
|---|---|---|
| `status` | 응답은 왔는데 상태코드가 나쁘다 | `status` + `code` |
| `transport` | **응답에 닿지도 못했다**(`fetch` 가 `TypeError`) | `name` |
| `timeout` | `AbortError` / `TimeoutError` | `name` |
| `unknown` | 위 어느 것도 아님 | `name` |

🔴 **순서가 의미를 갖는다** — `ApiError` 를 먼저 본다. 그것만이 «응답을 받은» 경우이고,
뒤집으면 `ApiError` 가 `unknown` 으로 샌다.
🔵 `AbortError`/`TimeoutError` 는 환경마다 클래스가 달라 **이름**으로 가른다.
🔵 성공하면 `unavailableCause` 는 `undefined` 다 — **없는 사유를 지어내지 않는다.**
🔴 방문자에게 보이는 값이 아니다(서버 로그·진단용). 두 모듈이 `logger.warn` 한 줄을 남긴다.

## 🔴 결함의 자리가 **테스트 안에** 그대로 있었다

두 파일의 스위트가 각각 «502» 와 «네트워크 실패» 를 **이미 주입하고 있었는데**, 둘 다
`expect(state.bffUnavailable).toBe(true)` 하나만 단언했다 — **서로 다른 세계가 같은 단언을
만족**한다. 그것이 이 AC 가 말하는 «구별되지 않는다» 의 정확한 모양이고, 스위트가 그 상태를
**증명하고 있었다.**

| 넓힌/더한 칸 | 무엇을 문다 |
|---|---|
| 502 칸(개요·헬스) | 사유가 `{kind:'status', status:502, code:'BAD_GATEWAY'}` |
| 네트워크 칸(개요·헬스) | 사유가 `transport` — «나빴다» 가 아니라 **«닿지도 못했다»** |
| 🔴 **뭉개짐 칸(개요)** | 502 · transport · timeout 셋을 한 테스트에서 돌려 `kinds` 가 **셋 다 다른지** |
| 🔴 뭉개짐 칸(헬스) | 같은 질문 — 공유 분류기가 **이쪽에도** 걸려 있는지 |
| 🔵 대조군(개요) | 성공하면 `unavailableCause` 가 `undefined` |

## bite

`classifyUnavailable()` 맨 앞에 `return {kind:'unknown'}` 을 넣어 **사유를 하나로 뭉갰다**
(주입 1건 단언 후) → 개요 스위트 **3칸 빨강**(502 · transport · 뭉개짐), 🔵 대조군은 **초록**.
복원 후 전부 초록.

## 검증

| 게이트 | 결과 |
|---|---|
| `operator-overview-api.test.ts` | 🟢 18칸 |
| `domain-health-api.test.ts` | 🟢 20칸 |
| `console-web` 전체 (`pnpm test`) | 🟢 **313 파일 / 3505 칸** |
| `npx tsc --noEmit` | 🟢 rc=0 |

## ⚪ 안 한 것

- **화면 문구는 그대로 두었다.** AC-2 가 «사유를 구별 가능하게 만드는 것이 목적이고 문구는
  그 다음» 이라고 적어 뒀다. «일시적으로» 가 Vercel 에서 상시 거짓인 것은 여전하다.
- **로그가 실제로 어디에 쌓이는지는 안 쟀다** — `logger` 는 stdout 으로 나가고, Vercel 쪽
  수집 여부는 이 저장소에서 확인할 수 없다. 🔴 그래서 «이제 사유를 볼 수 있다» 고 적지 않는다.
  볼 수 있게 된 것은 **상태 객체**이고, 그것을 읽는 첫 소비자는 ③ 이나 촬영 매니페스트다.
- ③④ 는 다음이다.

# 🟢 AC-3 — ③ 부재 대신 표현 (2026-09-18 UTC · 분석·구현=Opus 5)

## 두 갈래 중 **«보이게 한다»** 를 골랐다 — ⚪ 가 아니다

AC-3 은 «보이게 하거나, 의도라면 왜 옳은지 적고 ⚪ 로 닫아라» 였다. ⚪ 쪽 근거가 하나 있었다:
`console/page.tsx` 주석이 *"Best-effort: a null/degraded health simply yields no dots (the catalog
never blanks)"* 라고 **의도를 이미 적어 두었다.** 🔴 그러나 그 의도는 «점을 안 그린다» 까지만
옳고 «아무 말도 안 한다» 까지 옳지는 않다. 카탈로그를 안 비우는 것과 실패를 숨기는 것은 다른
일이고, 같은 컴포넌트가 **카탈로그 레그**의 실패에는 이미 `catalog-degraded` 배너를 그리고
있었다 — 두 레그를 다르게 대하는 근거가 없다. ⇒ 보이게 했다.

## 고친 것

| 무엇 | 어떻게 |
|---|---|
| 사유가 화면까지 내려간다 | `ServiceCatalog` 에 `healthState: 'ok' \| 'no-tenant' \| 'unavailable'`. 🔵 불리언이 아니다 — 두 «점 0개» 를 구별해야 한다 |
| 실패 = **마커** | `unavailable` → `data-testid="catalog-health-unavailable"` 배너. 접미사 `-unavailable` 은 `DEGRADED_SUFFIXES` 에 있다 |
| 🔴 «일시적» 을 안 쓴다 | 방문자 배포에서 이 레그는 **상시** 끊겨 있다. 상시 참인 «일시적» 은 거짓말이고, 거짓말하는 신호는 꺼진 신호와 같다(이 티켓 ②의 축) |
| 🔵 테넌트 미선택은 저하가 **아니다** | 이 페이지에서는 정상 상태다(카탈로그 자신이 테넌트 선택기다). `catalog-health-no-tenant` — 저하 접미사 **아님**. 그래도 **말은 한다** |
| 🔴 사유를 화면에 안 낸다 | 상태코드·전송 구분은 서버 로그(`domain_health_bff_unavailable`, 이 티켓 ②). Edge Case 표의 그 칸 |

🔴 **두 번째 갈래(`no-tenant`)를 왜 침묵으로 두지 않았나** — «정상이니 조용해도 된다» 는 각각
옳은 제외가 구멍을 만드는 그 모양이다. 세션/프록시 결함이 `noTenant` 로 표현되면 `/console` 은
다시 **설명 없는 공백**이 된다. ⇒ 두 경우 모두 무언가를 그리되, 저하로 **세는** 것은 하나뿐이다.

## 비공허성 — 사슬 세 마디를 각각 쟀다

「마커를 그린다」만으로는 공허하다: 아무도 안 읽는 `data-testid` 를 그려도 초록이기 때문이다.

| 마디 | 재는 칸 |
|---|---|
| ① 실패 → 페이지가 사유를 **내려보낸다** | `console-home-parallel.test.tsx` 4칸 (`data-health-state`) |
| ② 사유 → 컴포넌트가 **마커를 그린다** | `catalog-health-notice.test.tsx` 5칸 |
| ③ 마커 → **촬영 술어가 읽는** 접미사다 | 같은 파일 3칸 — `scripts/capture-portfolio.mjs` 를 읽어 `DEGRADED_SUFFIXES` 를 뽑는다 |

③ 은 **두 방향**을 단언한다: 실패 마커는 그 목록 중 하나로 끝나고, 테넌트 미선택 마커는
**어느 것으로도 끝나지 않는다**(정상 화면이 저하로 집계되면 촬영이 멀쩡한 장을 후보에서 뺀다).
🔴 정규식이 못 찾으면 **던진다** — 빈 목록이면 세 칸이 전부 공허하게 통과한다.

## bite — 주입 셋, **서로 다른 칸**이 물린다

| 주입 | 결과 |
|---|---|
| A `page.tsx` 에서 `healthState` 전달 제거(= 결함 원상복구) | `console-home-parallel` **4칸 ✗**, `catalog-health-notice` 는 초록 |
| C 실패 마커를 `catalog-health-note` 로 개명(= 술어가 안 읽는 이름) | `catalog-health-notice` **3칸 ✗** (렌더 2 + 드리프트 1) |
| D 테넌트 미선택 마커를 `…-no-tenant-degraded` 로 개명 | **1칸 ✗** — 역방향 핀만. 나머지 7칸 초록 |

🔵 A 와 C 가 **다른 파일의 칸**을 물고, D 는 **정확히 한 칸**만 문다 — 세 마디가 서로의
사본이 아니라는 증거다. 원상복구 후 재실행 15/15 초록(rc=0).

## rc 로그 (파이프 없음)

| 명령 | rc |
|---|---|
| `pnpm install --frozen-lockfile` (worktree) | 0 |
| `pnpm test` (착수 직후, 내 새 파일의 경로 버그 포함) | 1 — `313 passed / 1 failed`, 실패는 **내 새 파일 2칸뿐** ⇒ 베이스라인 초록 확인 |
| `npx vitest run` (해당 3파일) | 0 — **21칸** |
| `npx tsc --noEmit` | 0 |
| `pnpm lint` | 0 |
| bite A / C / D | 1 / 1 / 1 (위 표) |
| 복구 후 `npx vitest run` (2파일) | 0 — **15칸** |
| `pnpm test` (전량, 최종) | ↓ 아래 |

🔵 판정은 rc 가 아니라 **몇 칸이 돌았나**로 했다(일을 하나도 안 하고 끝나는 러너 함정).

## ⚪ 남는 측정 하나 — 그리고 그것이 이 칸을 막지 않는 이유

AC-3 의 문자 그대로의 판정은 **촬영 매니페스트**다. 이 세션에는 데모 창이 없으므로
«다음 촬영에서 `/console` 이 `degraded` 로 잡힌다» 는 **아직 안 쟀다.** 🔵 그러나 그 문장을
이루는 세 마디는 위에서 각각 쟀고, 네 번째 마디(«방문자 배포에서 이 레그가 실제로 죽는다»)는
2026-09-18 창이 **이미 쟀다**(`/dashboards/health` = `domain-health-bff-unavailable`).
⇒ 남은 것은 사슬의 확인 사살이지 미지수가 아니다. 그 한 줄을 `TASK-MONO-672` § 다음 창
런북에 **가드가 있는 자리로** 옮겨 적었다 — 창이 열리면 누군가 그 줄을 읽는다.

# 🟢 AC-4 — 648 에 넘겼다

`TASK-MONO-648` § «랜딩은 영구히 후보가 아니다» 절에 갱신 한 문단을 붙였다:
`/console` 은 이제 저하를 **말하므로** 다음 촬영부터 «저하» 로 집계된다 — 화면이 나빠진 것이
아니라 **이전의 «저하 아님» 이 오보였다**. 🔴 큐레이션은 고르지 않았다(소유자 결정).

---

# 🟢 종결 (2026-09-18 UTC)

①②③④ 넷 다 닫혔다. Scope 의 «제외» 는 지켰다 — `console-bff` 를 Vercel 에서 닿게 하는
결정(ADR 사안)은 건드리지 않았고, 저하 술어의 문구·마커 **목록**도 안 바꿨다(③은 그 목록의
문제가 아니라 «아무것도 안 그린다» 는 세 번째 모양이었고, 고친 것은 화면 쪽이다).
