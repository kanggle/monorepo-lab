# Task ID

TASK-MONO-711

# Title

🔴 **기록된 한계와 진짜 장애가 같은 화면으로 나온다** — 촬영 사전점검은 그 화면을 통과시키고, 저하 판정은 원인을 지운다

# Status

ready

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

- [ ] **AC-0 — 착수 게이트: 이 티켓의 전제부터 다시 읽어라.** 🔴 첫 판이 «기록된 한계» 를
      «새 결함» 으로 읽고 무너졌다. 착수 시점에 ⓐ `console-vercel.override.yml` § 영향 레그와
      ⓑ `TASK-MONO-585` § 알려진 한계를 **열어서** 위 ①②③ 이 거기 **없는지** 다시 확인해라.
      있으면 그 칸을 지워라 — 남은 것으로 티켓을 다시 좁히는 것이 착수다.
- [ ] **AC-1 — ① 사전점검.** `probe` 가 `judgeDegraded()` 를 부르고 저하면 경고를 찍는다.
      🔴 **막지는 마라** — 저하를 **일부러** 찍는 측정이 있다(`TASK-MONO-707` AC-3 이 그랬다).
      경고 + 매니페스트 기록이지 중단이 아니다. **bite**: 저하 픽스처에서 경고가 안 나오면
      `--self-test` 가 빨개진다.
- [ ] **AC-2 — ② 사유를 들고 다닌다.** `bffUnavailable` 옆에 원인 구분(최소:
      `transport` / `status:<code>` / `timeout`)을 남긴다. 🔴 **상시 켜진 신호를 구별 가능하게
      만드는 것이 목적**이지 화면을 예쁘게 하는 것이 아니다. 화면 문구는 그 다음 문제다.
      **bite**: 서로 다른 실패를 주입하면 서로 다른 사유가 나온다(같은 값이면 빨강).
- [ ] **AC-3 — ③ 부재 대신 표현.** `/console` 의 도메인 상태 카드가 실패를 **보이게** 한다.
      판정은 촬영 매니페스트다 — 그 장이 `degraded` 로 잡히거나, 잡히지 않는 것이 의도라면
      **왜 그것이 옳은지**를 이 티켓에 적고 칸을 ⚪ 로 닫는다.
- [ ] **AC-4 — 648 에 넘긴다.** 「콘솔 랜딩은 영구히 후보가 아니다」를 `TASK-MONO-648`
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
