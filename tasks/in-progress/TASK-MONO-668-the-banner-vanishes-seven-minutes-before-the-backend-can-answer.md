# Task ID

TASK-MONO-668

# Title

🔴 **배너가 백엔드보다 7분 먼저 사라진다** — 판정이 인스턴스 상태만 보고 묶음 헬스를 안 본다. `TASK-MONO-636` 축의 **세 번째 값**이 없다

# Status

in-progress

# Owner

monorepo

# Task Tags

- demo
- ux
- shared-lib

---

# Goal

「데모 서버가 켜지는 중」을 **「켜졌다」와 다른 화면**으로 만든다 — 또는 **그러지 않기로
결정하고 그 근거를 적는다.**

🔴 이 티켓은 «배너를 하나 더 만든다» 가 아니라 **`TASK-MONO-636` 이 정한 축에 값이 하나
빠져 있다** 는 것이다. 그 티켓과 `TASK-MONO-644` 가 **두 번** 고친 결함이 같은 모양이다.

---

# 🔴 실측 (2026-09-11 UTC · `TASK-MONO-654` 재촬영 창)

창을 열어 `store` 묶음 하나만 기동하면서 탐침과 묶음 상태를 같이 읽었다:

| 시각 | 인스턴스 | `store` 묶음 | `/api/demo/backend-state` | 화면의 배너 |
|---|---|---|---|---|
| `16:10:54Z` | `stopped` | `selected` | `unavailable` | 🟢 있다 (옳다) |
| `16:10:57Z` | `pending` | `requested` | — | |
| **`16:12:xx Z`** | `running` | **`booting`** | 🔴 **`running`** | 🔴 **없다** |
| `16:19:51Z` | `running` | **`ready`** | `running` | 🟢 없다 (옳다) |

⇒ 🔴🔴 **배너가 사라진 뒤에도 백엔드는 7분 더 못 쓴다.**

## 기전 — 판정이 «인스턴스» 만 본다

`infra/demo/backend-resolver/src/index.ts`:

```ts
if (status && status.state === 'running' && isPlausibleIpv4(status.ip)) { … }   // ← 여기까지다
```

`/status` 는 **인스턴스** 상태만 준다. 묶음별 헬스는 **`/bundles`** 에 있고(`ready` ·
`booting` · `partial` · `requested` · `waiting`), 해석기는 그것을 **안 본다.**

🔵 그리고 그것은 원래 옳았다 — 해석기의 일은 «주소를 만들 수 있는가» 이고, 그 답은
인스턴스가 running 이면 참이다. **새로 생긴 요구는 «그 주소가 지금 대답하는가» 다.**

---

# 🔴 왜 이것이 «작은 UX» 가 아닌가

`TASK-MONO-636` 이 이름 붙인 축은 **「모른다」 ≠ 「없다」** 였고, `TASK-MONO-644` 가
*"두 상태를 갈라야지 한쪽으로 통일하면 안 된다"* 로 같은 것을 두 번째로 고쳤다.
`TASK-MONO-654` 는 그 축의 **탐침 전 상태**를 갈랐다(아무 말도 안 한다).

지금 빠진 것은 **네 번째 값**이다:

| 상태 | 화면 | 현재 |
|---|---|---|
| 아직 안 물어봤다 | 아무 말 없음 | 🟢 654 가 갈랐다 |
| 물어봤는데 꺼져 있다 | 「꺼져 있어…」 배너 | 🟢 |
| **켜지는 중** | — | 🔴 **「켜졌다」와 같은 화면** |
| 켜졌다 | 배너 없음 | 🟢 |

🔴 방문자 관점의 피해: 안내문이 *"서버를 켠 뒤(약 10분) 다시 열어 주세요"* 라고 했는데,
켜자마자 배너가 사라지므로 방문자는 **다 됐다고 믿고** 들어왔다가 빈 화면을 본다.
⇒ 「켰는데도 안 된다」가 되고, 그것은 `TASK-MONO-654` 가 고친 것과 **같은 종류의 나쁨**이다.

---

# Scope

## 포함

- 「켜지는 중」을 판정할 수 있게 한다(`/bundles` 를 보거나, `/status` 에 그 사실을 싣거나).
- 그 상태를 **다른 화면**으로 만든다.
- 🔴 **또는 안 하기로 결정하고 근거를 적는다** — 아래 § 갈래 ⓒ.

## 제외

- 🔴 **배너 문구 재설계** — `TASK-MONO-642` 가 그 문장을 정했다. 새 값에 필요한 문장만 더한다.
- 🔴 **부팅을 빠르게 만드는 것** — 다른 축이다(`TASK-MONO-645` 창 회계: 묶음을 8→5 로 줄여도
  부팅은 8분 19초 → 8분 3초였다. **묶음 수에 비례하지 않는다**).

---

# 🔴 갈래 — 착수 전에 고른다

| | 갈래 | 대가 |
|---|---|---|
| **ⓐ** | 해석기가 `/bundles` 도 본다 | 🔴 **해석기가 «어느 묶음이 내 것인가» 를 알아야 한다** — 지금은 `servicePrefix` 만 안다. 앱마다 묶음 이름이 필요해지고, 그것은 `ADR-MONO-068 § D5.1` 이 «설정 셋» 으로 좁혀 둔 인터페이스를 **넷째 축으로 늘리는 것**이다 ⇒ 그 ADR 을 다시 열어야 한다 |
| **ⓑ** | 컨트롤 플레인이 `/status` 에 «전부 ready 인가» 를 싣는다 | 🔵 해석기 인터페이스가 안 바뀐다. 🔴 대신 «전부» 의 정의를 람다가 정해야 한다(선택된 묶음 기준? 앱별?) |
| **ⓒ** | 안 고친다 | 🔵 구간이 8분이고 방문자가 그 사이에 올 확률이 낮다면 합리적이다. 🔴 **그 확률을 잰 적이 없다** |

🔴🔴 **ⓐ 를 반사적으로 고르지 마라** — `ADR-MONO-068` 이 해석기 설정을 **정확히 셋**으로
좁혀 두었고, 그 파일이 *"넷째 축이 생기면 그것은 «설정» 이 아니라 **동작 분기**다 — 그때는
이 인터페이스를 늘리기 전에 `ADR-MONO-068` 을 다시 열어라"* 라고 적어 두었다.

---

# Acceptance Criteria

## AC-0 — 착수 게이트

- [x] 🔴 **위 실측을 다시 재라.** 창이 열리는 김에 읽으면 되고(비용 0), 그 사이
      람다나 해석기가 바뀌었을 수 있다. 🔵 술어: 기동 직후 `/bundles` 와
      `/api/demo/backend-state` 를 **같이** 읽는다.
      → 🟢 **다시 쟀다** (2026-09-12 창). 술어 그대로 `/bundles` 와
      `/api/demo/backend-state` 를 **같이** 읽었다:

      ```
      07:13:41Z  backend-state={"state":"running"}   store=booting  console=booting
      07:14:25Z  backend-state={"state":"running"}   store=booting  console=booting
      07:15:18Z  backend-state={"state":"running"}   store=booting  console=booting
      07:15:40Z  backend-state={"state":"running"}   store=ready    console=ready
      ```

      🔴 **결함이 살아 있다** — 배너가 `running` 이라고 말하는 동안 `store` 묶음은 `booting` 이다.
- [x] 🔴 **구간 길이를 재라.** 이번 관측은 «인스턴스 running → 묶음 ready» 가 **약 7분**
      이었다(`16:12` → `16:19:51`). 🔴 단일 표본이다 — 갈래 ⓒ 의 근거가 되려면 더 필요하다.

## AC-1 — 갈래를 고른다

      → 🟢 **두 번째 표본이 생겼다.** 인스턴스 기동(`07:05:18Z`) → **선택 묶음 전부 ready
      `+661초 = 11분 1초`**. `store` 단독은 `+610~622초`.
      🔴🔴 **직전 표본(≈7분)과 그냥 비교하면 안 된다 — 모집단이 다르다.** 이번 창은
      저장된 `boot-selection` 이 **8묶음**이었다(내가 요청한 4묶음이
      *"이미 요청된 묶음입니다"* 로 흡수됐다). 🔵 묶음이 많을수록 길어지므로
      «7분 → 11분» 은 **악화가 아니라 다른 실험**이다.
      🔴 내가 **실제로 증명한 하한**은 «배너 running + store booting» 을 관측한
      `07:13:41 → 07:15:40` = **119초**다. 그 전에도 배너는 running 이었을 것이나
      **관측 시작이 늦어서 시작점을 못 봤다** — 추정을 판정으로 적지 않는다.
- [x] 🔴 ⓐ/ⓑ/ⓒ 중 하나를 고르고 **왜 나머지를 안 골랐는지** 적는다.
      → 2026-09-15 선택창으로 물었다(🔵 추천 표지는 **내 것**, 선택은 소유자).
      소유자 선택(라벨 원문): **「ⓑ 람다 /status 에 준비여부 (Recommended)」**
      — 선택지 설명(내가 쓴 것): *컨트롤 플레인이 `/status` 에 «선택된 묶음이 전부 ready 인가» 를 싣고 해석기가 그것으로 «켜지는 중» 을 가른다. 해석기 설정 셋은 그대로라 ADR-068 재개봉 불필요. 대가: 자기 묶음이 ready 여도 다른 묶음이 booting 이면 «켜지는 중» 이 더 오래 뜬다 — 보수적인 쪽의 오차.*
      질문 시점의 저장소 사실(2026-09-15 재측정): 해석기 `infra/demo/backend-resolver/src/index.ts:191-199` 는 여전히 `state === 'running' && isPlausibleIpv4(ip)` 만 보고 `/bundles` 를 **안 부른다** · `handler.py:676-686` `/status` = `state, ip, used_minutes, budget_minutes`(준비 필드 **없음**) · `/bundles`(`:462-494`)에도 집계 필드 **없음**, 묶음별 `ready` 는 `:431-432`.
      안 고른 이유: **ⓐ** = 해석기에 넷째 축(앱별 묶음 이름)이 생긴다 — `ADR-MONO-068:472-474` *"`DemoBackendResolverConfig` 의 필드가 **정확히 그 셋**이다"* 와 `index.ts:74-78` 이 재개봉을 요구한다 · **ⓒ** = 잰 값(하한 119초 · 전체 661초)은 있으나 «그 구간에 방문자가 올 확률» 은 **여전히 안 쟀다**.
      🔴 **ⓑ 가 넘겨받는 미결 하나**: «전부» 의 정의 — Edge Case 첫 행(«그 앱이 쓰는 묶음이 기준») 과 부딪친다. 선택지 설명대로 **선택된 묶음 전부**로 가면 그 Edge Case 는 «보수 쪽 오차로 수용» 으로 닫아야 하고, 구현 PR 이 그것을 본문에 적는다.
- [x] ⓐ 라면 🔴 **`ADR-MONO-068` 을 먼저 열어라**(그 파일이 그렇게 요구한다).
      → **해당 없음** — ⓑ 를 골랐다. 🔴 단 구현이 해석기에 **새 설정 필드**를 더하게 되면 그 순간 ⓐ 와 같은 규율이 걸린다(«읽는 응답 필드» 는 설정이 아니지만 «앱별 묶음 이름» 은 설정이다).
- [x] ⓒ 라면 🔴 근거는 «확률이 낮다» 가 아니라 **잰 값**이어야 한다.
      → **해당 없음** — ⓒ 를 고르지 않았다.

## AC-2 — 세 앱을 같이 본다

- [x] 🔴 해석기는 **세 앱이 공유한다**(store · fan · console). 한 앱만 고치면 나머지 둘이
      다른 말을 한다 — 이 저장소가 이미 «사본이 갈린다» 로 데인 축이다(`ADR-MONO-068 § D6`).
      → 🟢 (2026-09-15 구현) 판정은 **해석기 한 곳**(`starting`)이고 세 앱 전부 반영: store 배너 ·
      fan `(main)` 배너 + `/login` · console 배너. 세 배너가 **같은 첫 문장**. 소비자별 표는 § 구현.
      🔴 **라이브에서 세 앱이 같은 순간 같은 말을 하는지는 아직 안 쟀다** — § 창이 잴 것.
- [x] 🔵 `console` 은 배너 대신 `SampleDataBanner` 를 쓴다 — **같은 값이 필요한지** 확인하라.

## AC-3 — 가드

      → 🟢 **같은 값이 필요하지 않다 — 애초에 안 읽는다.** 같은 순간에 세 앱을 물었다:

      | 앱 | `/api/demo/backend-state` |
      |---|---|
      | store | 🟢 `{"state":"running"}` |
      | fan | 🔴 `Redirecting...` — **그 라우트가 없다** |
      | console | 🔴 HTML 앱 셸 — **없다** |

      그리고 `git grep backend-state\|backendState -- console-web/src` = **0건**.
      `SampleDataBanner` 는 `(demo)/layout.tsx` 에 있고 **«샘플 데이터»** 축이다.
      🔵 ⇒ **세 앱이 공유하는 것은 «해석기(라이브러리)» 이지 이 엔드포인트가 아니다.**
      AC-2 의 첫 칸(«한 앱만 고치면 나머지 둘이 다른 말을 한다»)은 **해석기 층**에서 지켜야 한다.
- [x] 🔴 **실행 비교**: 「인스턴스 running + 묶음 booting」 상태를 주고 화면이
      «켜졌다» 와 **다른지** 비교한다. 🔴 선언 grep 으로는 못 잰다.
      → 🟢 **단위 층에서 실행 비교** (렌더된 DOM): fan 은 **진짜 해석기**에 `/status` stub
      (`selection_ready:false` ↔ `true`)을 먹여 배너 유무를 비교 · console 은 `starting` → 엘리먼트 /
      `running` → `null` · store 는 같은 하네스로 두 탐침 결과의 `innerHTML` 이 **다른지** 단언.
      🔴 store 의 JSX 스위트는 **로컬에서 못 돌렸다**(§ 검증 ⚪) — CI 가 권위.
      🔴 라이브(«인스턴스 running + 묶음 booting» 의 실제 화면)는 **창이 잰다**.
- [x] **대조군**: 「전부 ready」에서는 그 문구가 **안 나와야** 한다.
      → 🟢 fan 배너·fan `/login`(`Configuration` 이 다시 설정 결함 문구로 돌아온다) · store 비교칸의
      `running` 쪽 · 람다 `test_all_selected_bundles_ready_is_true`.
- [x] 🔴 **bite**: 판정을 인스턴스 상태만 보게 되돌리면 빨강.
      → 🟢 **두 층에서 따로 물렸다** (§ 검증): 해석기가 필드를 무시(`starting = false`) → fan 4 · console 1 ·
      store 3 빨강 / 람다가 인스턴스만 보고 `True` → 11 빨강. 둘 다 복원 후 초록.

---

# Related Specs / Contracts

- `TASK-MONO-636` — 「아직 모른다」와 「물어봤는데 없다」는 다른 값이다 (이 축의 출처)
- `TASK-MONO-644` — 두 상태를 갈라야지 한쪽으로 통일하면 안 된다 (같은 축, 두 번째)
- `TASK-MONO-654` — 탐침 전 상태를 갈랐다. **이 티켓은 그 다음 값**이다
- `ADR-MONO-068` § D5.1 / § D6 — 🔴 해석기 설정은 **정확히 셋**. 넷째 축 = 동작 분기 = ADR 재개봉
- `TASK-MONO-653` — `pending` 을 `stopped` 와 같이 묶지 마라 (람다 쪽의 같은 축)
- `infra/demo/backend-resolver/src/index.ts` · `infra/demo/aws/terraform/lambda/handler.py`

---

# Edge Cases

| 상황 | 기대 | 🟢 구현(2026-09-15) |
|---|---|---|
| 인스턴스는 running 인데 **일부 묶음만** ready | 🔴 그 앱이 쓰는 묶음이 기준이다. 「전부」로 뭉치면 관계없는 묶음이 그 앱을 막는다 | 🔵 **닫음 — 기대를 뒤집고 «보수 쪽 오차로 수용»** (AC-1 소유자 결정 ⓑ). «그 앱의 묶음» 을 기준으로 하려면 해석기가 앱별 묶음 이름을 알아야 하고 그것은 `ADR-MONO-068` 의 넷째 설정 = 재개봉이다. 🔴 **수용한 대가를 이름으로 적는다**: 스토어가 다 뜬 뒤 누군가 `console-wms` 를 더하면 그 묶음이 `booting` 인 동안 스토어·팬·콘솔 **셋 다** «켜지는 중» 을 말한다(스토어는 멀쩡히 된다). 틀리는 방향이 «아직» 쪽이고, 문구가 «동작하지 않을 **수** 있습니다» 라 그 경우에도 거짓이 아니다. 테스트: `test_one_ready_and_one_booting_is_false_the_accepted_conservative_cost` |
| 방문자가 **고르지 않은** 묶음 | `waiting` 이다. 「켜지는 중」이 **아니다** — 영영 안 켜진다 | 🟢 판정 모집단이 **저장된 선택**이라 선택 밖 묶음은 아예 안 센다. 🔴 곁발견: 선택 밖 묶음의 상태는 `waiting` 만이 아니다 — 공유 의존 iam 이 up 이면 `partial` 이다(첫 테스트가 `waiting` 을 단언했다가 틀렸다). 테스트: `test_unselected_bundles_never_hold_it_back` |
| `/bundles` 조회 실패 | 🔴 「켜지는 중」으로도 「켜졌다」로도 번역하지 마라. 해석기의 기존 규칙(판정 불가 → 기존 동작)을 따른다 | 🟢 ⓑ 에선 해석기가 `/bundles` 를 **안 부른다**. 같은 자리의 실패 둘: ① 람다 안의 SSM 읽기 실패 → `selection_ready=null` 이고 `/status` 는 **200 그대로**(덧붙인 필드가 본체를 죽이면 세 앱에 「꺼짐」 배너가 뜬다) ② `/status` 자체 실패 → 옛 동작 그대로 `unavailable`. 헬스 stale 도 `null` → `running`(옛 동작) |
| 부팅이 실패해 영영 ready 가 안 된다 | 🔴 「켜지는 중」이 **영구히** 걸린다. 상한이 필요한가? | 🔵 **상한을 두지 않았다 — 기존 인스턴스 가드가 상한이다.** 시간 상한은 «아직 아님» 을 «됐다»(이 티켓의 결함) 나 «꺼졌다»(거짓 — 인스턴스는 running) 중 하나로 **바꿔 말하는 것**뿐이다. 인스턴스는 유휴 20분 / 최대 가동 180분 / 월 예산 가드가 끄고(`handler.py` `idle_check`), 꺼지면 `state≠running` → `unavailable` 로 옳게 넘어간다. 🔴 그 사이 방문자가 보는 문장은 «몇 분 뒤 다시 열어 주세요» 이고 실패한 부팅에선 **틀린 약속**이 된다 — 진단은 론처 카드(`/bundles` 묶음별 상태)의 몫이다. 이 한계는 알고 남긴다 |

---

# Failure Scenarios

1. 🔴🔴 **ⓐ 를 고르면서 `ADR-MONO-068` 을 안 연다** → 그 파일이 명시적으로 금지한 경로다.
2. 🔴 **한 앱만 고친다** → 세 앱이 다른 말을 하고, 그 갈라짐은 조용하다.
3. 🔴 **「켜지는 중」과 「조회 실패」를 같이 묶는다** → `TASK-MONO-636`·`644` 가 두 번 고친
   그 결함을 **세 번째로** 만든다.
4. **구간이 짧다고 추정하고 ⓒ 를 고른다** → 이번 실측은 **7분**이고 부팅 전체는 약 9분이다.
   추정이 아니라 재라.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus** — 갈래 ⓐ 는 `ADR-MONO-068` 재개봉이고, ⓑ 는 컨트롤 플레인의
계약 변경이다. 🔵 고른 뒤의 **구현 자체**는 작아서 Sonnet 으로 충분하다.


---

# 🟢 2026-09-12 데모 창 — AC-0 과 AC-2 한 칸이 실측으로 닫혔다

🔵 **남은 칸은 «고르고 고치는» 것들**이다(AC-1 갈래 선택 · AC-2 세 앱 반영 · AC-3 가드).
이 티켓은 여전히 `ready/` 다 — 측정은 끝났고 **결정과 구현이 남았다.**

🔴 이 창이 준 가장 쓸모 있는 입력: **`store` 묶음이 `ready` 가 되는 시점과 배너가 «떴다» 고
말하는 시점이 다르다**는 것을 **쌍 관측으로** 봤다. ⓒ 갈래(«확률이 낮다»)를 고르려면
근거가 «잰 값» 이어야 한다고 AC-1 이 요구하는데, 이제 그 값이 있다.

---

# 🔵 구현 (2026-09-15 UTC · `in-progress`) — 갈래 ⓑ

🔴 **아직 닫지 않는다.** 코드·계약·단위 시험은 들어갔지만 이 티켓의 판정은 **라이브 쌍 관측**이고,
그것은 람다 apply + 앱 배포 + 데모 창이 있어야 난다(아래 § 창이 잴 것).

## 설계 답 1 — `/status` 필드: `selection_ready`

계약의 집: [`ADR-MONO-071 § D5.1`](../../docs/adr/ADR-MONO-071-boot-the-bundle-the-visitor-chose.md)
(묶음 상태 8단계가 사는 곳 옆). `infra/demo/aws/README.md` 는 그리로 가리킨다.

| 값 | 조건 |
|---|---|
| `true` | running · 헬스 신선(≤90초) · 선택 비어 있지 않음 · 선택 묶음 **전부** `_bundle_state == ready` |
| `false` | 위와 같은데 하나라도 `ready` 아님 ⇒ **켜지는 중** |
| `null` | 판정 불가 — running 아님(`state` 가 이미 말한다) · 헬스 stale · `published_at` 없음 · 선택 빔 · 어느 묶음 `unknown` · **SSM 읽기 예외** |

- 🔴 **판정은 `/bundles` 와 같은 함수(`_bundle_state`)** — 두 엔드포인트가 따로 계산하면 론처 카드와
  앱이 같은 순간 다른 말을 한다. `test_status_and_bundles_never_disagree` 가 여섯 세계로 대조한다.
- 🔴 **stale → `null`**, `false` 도 `true` 도 아니다(티켓의 요구 그대로).
- 🔴 **SSM 읽기가 던져도 `/status` 는 200** 이고 `selection_ready=null` — 덧붙인 필드가 본체를
  죽이면 세 앱에 「꺼짐」 배너가 뜬다. IAM 변경 없음(람다가 이미 `/bundles` 로 두 파라미터를 읽는다).
- 🔵 선택이 빈 경우(= 「전체 시작」 경로로만 켰다)는 `null` ⇒ 그 경로에선 **옛 동작(결함 그대로)**이다.
  론처의 기본 행동은 묶음 기동이고(`ADR-MONO-071 § D6`) 「전체 시작」은 `<details>` 안이다. 알고 남긴다.

## 설계 답 2 — 하위 호환 · 배포 순서

**둘 다 추가라서 순서가 자유롭다.** 선택과 이유:
- 옛 해석기 + 새 람다: 모르는 키 무시 → 오늘과 같다.
- 새 해석기 + 옛 람다(필드 없음): `selection_ready === false` 만 켜지는 중이므로 → **오늘과 같다**
  (`running`). 🔴 `!selection_ready` 로 썼다면 람다 apply 전의 **모든 방문이 영구히** 「켜지는 중」이었다 —
  그래서 `null`·없음·`"false"`·`0` 을 전부 옛 동작으로 두는 칸을 시험에 박았다.
- ⇒ 앱(Vercel)을 먼저 내도, 람다를 먼저 apply 해도 중간 상태가 **오늘보다 나빠지지 않는다.**

## 설계 답 3 — 소비자별 동작 (`ADR-MONO-068 § D6`: 세 앱이 다른 말을 하지 않는다)

해석기의 새 값: **`starting`** (`'not-demo' | 'starting' | 'running' | 'unavailable'`).
🔴 `starting` 에서도 `resolveDemoBackend()` 는 **주소를 준다** — «말하기» 용이지 «막기» 용이 아니다.
설정 필드는 **셋 그대로**(ADR-068 재개봉 없음). 읽는 것은 응답 필드 하나다.

| 소비자 | `starting` 에서 | 왜 |
|---|---|---|
| **web-store** `/api/demo/backend-state` | `{"state":"starting"}` 그대로 | 판정 위임만 한다 |
| **web-store** 배너(클라이언트 탐침) | 🟦 **새 배너** `demo-backend-starting`: *데모 서버가 켜지는 중입니다. 준비가 끝나기 전에는 장바구니·로그인 같은 실시간 기능이 동작하지 않을 수 있습니다. 몇 분 뒤 다시 열어 주세요.* | 642 문장은 안 건드렸다. 「샘플」·「켠 뒤」를 **안 넣었다**(켜지는 중에 무엇이 그려지는지 안 쟀다 / 이미 켜졌다 — 론처로 돌려보내면 중복 기동) |
| web-store BFF · `api.ts` | 데모 주소로 간다(변화 없음) | 주소를 거두면 폴백 사슬로 떨어져 준비된 묶음까지 끊긴다 |
| **fan** `(main)` 배너(서버 컴포넌트) | 🟦 새 배너, 같은 첫 문장 · «로그인·멤버십» | 같은 값 → 같은 말 |
| **fan** `/login` | 🟦 `login-demo-starting`: *…로그인이 실패할 수 있습니다. 몇 분 뒤 다시 시도해주세요.* **폼은 안 막는다** · 코드 문구(`Configuration` → «관리자 문의»)보다 **먼저** | 꺼짐 문구의 «다시 시도해도 같은 결과» 는 켜지는 중엔 **거짓**이다 |
| **console** `DemoBackendNotice` | 🟦 새 배너, 같은 첫 문장 · «로그인과 운영 데이터가 일부만 동작할 수 있습니다» · 도메인 이름 안 댐 | 이 위젯의 기존 규칙(도메인을 주장하지 않는다) 유지 |
| console `DemoLoginCredentials` | 렌더(변화 없음 — 술어가 `!== 'not-demo'`) | 켜지기를 기다리는 방문자에게 가장 필요하다 |
| console `SampleDataBanner` | 변화 없음 | 다른 축(AC-2 에서 실측으로 닫힘) |
| **auth-forwarder** | **포워드**(변화 없음, 주석만) | iam 은 이미 대답할 수 있다. 503 을 내면 될 로그인을 끊는다 |
| 론처 `index.html` | 변화 없음(`used_minutes`·`state` 만 읽음) | 묶음별 상태는 이미 `/bundles` 로 그린다 |

🔵 세 앱의 새 배너는 **같은 첫 문장**(«데모 서버가 켜지는 중입니다»)이고 색은 셋 다 같은 하늘색 —
꺼짐의 amber 와 **다른 사실을 다른 색**으로. 뒷문장은 각 앱이 잠기는 기능을 이름댄다(642 가 세 앱을
통일하지 않은 이유와 같다).

## 설계 답 4 — 영영 ready 가 안 되는 부팅

상한을 **두지 않았다** — Edge Cases 넷째 행에 근거. 요약: 시간 상한은 「아직」을 「됐다」나 「꺼졌다」로
바꿔 말할 뿐이고, 인스턴스 가드(유휴 20분·최대 180분·월 예산)가 이미 상한이다.

## 검증 (2026-09-15 UTC, 이 워크트리 · Windows 호스트 · Node v24.14.0 · Python 3.12.10)

| 무엇 | 명령 | 결과 |
|---|---|---|
| 람다 단위 | `python infra/demo/aws/tests/test_handler.py` (CI 는 pytest — 이 호스트엔 pytest 가 없어 unittest 로 같은 파일을 돌렸다) | **rc=0 · 86 tests**(새 `SelectionReadyOnStatusTest` 11) |
| 해석기 × fan | `vitest run` demo-backend · login-page · DemoBackendNotice | **rc=0 · 40/40** |
| 해석기 × console | `vitest run` demo-backend · demo-backend-notice · demo-login-credentials | **rc=0 · 31/31** |
| 해석기 × store (`.ts` 둘) | fan 의 vitest 3 바이너리로 web-store 의 demo-backend · backend-state-route | **rc=0 · 31/31** |
| 타입 | `npx tsc --noEmit` web-store · fan-platform-web · console-web | **rc=0 · rc=0 · rc=0** |
| CI «Demo resolver copies» | `bash -n` · `--self-test` · 가드 | **rc=0 · 0 · 0** (앱 안 구현 0건 · 선언 앱 4개) |
| CI «Client graph backend origins» | `node --check` · `--self-test` · 가드 | **rc=0 · 0 · 0** (오리진 리터럴 0건) |
| CI «Backend fetch resolution» | `node --check` · `--self-test` · 가드 | **rc=0 · 0 · 0** (미분류 0) |
| CI «Demo wrapper smoke» 일부 | `bash -n` (`git ls-files 'infra/demo/*.sh'`) · public-data `--check` · public-data `node --test` | **33개 실패 0 · rc=0 · rc=0** |

**bite (주입 먼저 단언 — 마커 grep 1건 확인 후 실행, 복원 후 마커 0건)**

| 층 | 되돌림 | 결과 | 복원 후 |
|---|---|---|---|
| 해석기 | `starting = false` (응답 필드 무시 = 인스턴스만 본다) | fan **4** · console **1** · store(.ts) **3** 빨강 — 전부 668 칸 | 40/40 · 31/31 · 31/31 |
| 람다 | running 이면 무조건 `True` | **11** 빨강 (`SelectionReadyOnStatusTest` 전부 계열) | 86 OK |

🔵 fan bite 런의 다섯째 빨강(`running + ip → sslip.io 조립` **5090ms**)은 bite 와 무관한 **5초 타임아웃 지문**이다 —
복원 후 같은 스위트가 40/40 이었다.

**⚪ 로컬에서 못 잰 것 — 이유와 권위**
- ⚪ **web-store `DemoBackendNotice.test.tsx`(JSX)** — vitest 4 가 Node 24 에서 기동 불가(`#module-evaluator`),
  vitest 3 로 돌리면 `React is not defined` 로 **변경 전부터 있던 칸까지 11/11 전부** 죽는다(도구 불일치의 지문).
  ⇒ 새 칸 셋(실행 비교 · 꺼짐 문구 아님 · 뭉치지 않음)의 권위는 **CI `frontend-unit-tests`(Node 20)**.
- ⚪ **`verify-demo-wrapper.sh`** — `(h) 참조 이미지가 레지스트리에 실재하는가`(Docker Hub 조회)에서 590초
  타임아웃 **rc=124**. 이 스크립트엔 칸을 건너뛰는 옵션이 없어 핸들러를 읽는 `(z)`·`(z32)`·`(z33)`·`(z40)` 까지
  **도달하지 못했다.** 🔵 이 변경은 그 칸들이 읽는 것(묶음 표 · 파라미터 이름 · `bundle_start` 문장 순서)을
  **안 건드렸다** — 그러나 «안 건드렸으니 초록» 은 판정이 아니므로 권위는 CI «Demo wrapper smoke» 다.
- ⚪ `check-prerendered-demo-verdict.sh` — `next build` 가 필요하다. 스토어 껍데기는 안 건드렸다.

## 배포 — 🔴 이 커밋만으로는 아무것도 안 바뀐다

1. **람다**: 🔴 **소유자 승인 `terraform apply`** 가 필요하다(`archive_file` 이 `lambda/` 를 굽는다). 이 작업에서
   terraform·aws 는 **한 번도 부르지 않았다.** IAM 변경 없음.
2. **앱**: store · fan · console 은 Vercel 빌드(머지 후 `vercel-deploy.yml`). auth-forwarder 는 주석만 바뀌었다.
3. **AMI 재굽기는 필요 없다** — 인스턴스 쪽 파일을 하나도 안 건드렸다(헬스 발행·선택 파라미터 그대로).
4. 🔵 순서는 자유다(§ 설계 답 2) — 어느 한쪽만 나간 상태는 **오늘과 같다.**

## 창이 잴 것 (이 티켓을 닫는 판정)

🔴 **AC-0 과 같은 술어를 쓴다 — 쌍으로 읽는다.** 묶음 하나를 기동하면서 같은 순간에:
1. `GET /status` 의 `selection_ready` 와 `GET /bundles` 의 선택 묶음 state — **둘이 어긋나는 순간이 0**이어야 한다.
2. store `/api/demo/backend-state` — `booting` 인 동안 **`starting`**, 전부 `ready` 뒤 `running`.
3. 세 앱 화면 — 같은 순간 셋 다 «데모 서버가 켜지는 중입니다» (fan `(main)` · console `/login` · store 배너).
   🔴 fan `(main)` 은 서버 컴포넌트다 — `TASK-MONO-654` 가 스토어에서 본 «프리렌더에 판정이 구워짐» 이
   거기서 재발하는지도 **같이 본다**(이 티켓이 만든 결함은 아니지만 `starting` 이 그 증상을 더 자주 드러낸다).
4. 대조군: 전부 `ready` 가 된 뒤 15초(해석기 캐시 TTL) 안에 세 배너가 **사라진다.**
5. 🔵 곁측정: `starting` 이 떠 있던 구간 길이 — AC-1 의 두 표본(≈7분 · 661초)에 셋째 표본을 더한다.

## 🔴 곁발견

- 선택 **밖** 묶음은 `waiting` 만이 아니다 — iam 이 up 이면 `partial` 이다. 내 첫 시험이 `waiting` 을
  단언해 빨갛게 떴고, 핸들러가 아니라 **시험의 전제**가 틀렸다.
- `scripts/check-prerendered-demo-verdict.sh` 의 `VERDICT_RE` 는 새 배너 문구를 **안 넣었다** — 그 가드의
  주입(`DEMO_API_BASE=http://127.0.0.1:9`)은 언제나 `unavailable` 을 만들어서 `starting` 이 구워지는 세계를
  **만들 수 없다**. 넣으면 한 번도 물 기회가 없는 칸이 된다. 스토어 껍데기가 무조건 렌더이므로 어느 판정이든
  구워지려면 조건이 되돌아와야 하고, 그것은 기존 지문(`demo-backend-notice`)이 문다.

---

# 🔵 창 실측 — 2026-09-17 UTC · AMI `ami-0d30513151d07e163`(RepoCommit `b54296645`) · 인스턴스 `i-09fb4c2cb734cae4c` · 창 08:50:37Z~10:08:16Z · 9묶음 전부 명시 기동(`/bundle/start`, TASK-MONO-685 이후 꺼진 인스턴스는 요청한 묶음만 뜬다) · 소유자 승인 «b54296645, 상한 150분»

표본 26개(약 15~35초 간격) — 매 표본마다 `GET /status` · `GET /bundles` · store `/api/demo/backend-state` 를 같은 순간에, 네 번째마다 세 앱 화면(store `/` · fan `/` · console `/login`)을 함께 읽었다.

| 구간 (UTC) | `selection_ready` | 선택 묶음 | store backend-state | 세 앱 배너 |
|---|---|---|---|---|
| 08:50:58 ~ 08:51:34 | **`null`** | 전부 `unknown` (헬스 첫 발행 전) | **`running`** | 🔴 **셋 다 없음** |
| 08:51:52 ~ 09:00:xx | `false` | `booting` → 순차 `ready` | `starting` | 🟢 08:52:27 · 08:54:39 · 08:56:38 · 08:58:06 · 08:59:34 에 **셋 다 «데모 서버가 켜지는 중입니다»**(`demo-backend-starting`) |
| 09:01:02 | `true` | 9개 전부 `ready` | `running` | 🟢 셋 다 사라짐 |
| 09:01:56 (대조군, +54초) | `true` | — | `running` | 🟢 셋 다 없음 |

1. 🟢 **쌍 불일치 0** — `selection_ready=true` 와 «선택 묶음 전부 ready» 가 어긋난 표본이 없다.
2. 🟢 backend-state: booting 동안 `starting`, 전부 ready 뒤 `running`.
3. 🟢 세 앱이 같은 순간 같은 첫 문장. fan `(main)` 도 기동 중에 배너를 그렸다(프리렌더에 판정이 구워지는 증상은 관측 안 됨).
4. 🟢 대조군: ready 뒤 첫 표본(09:01:02)에서 이미 세 배너가 사라졌다(15초 캐시 TTL 안).
5. 🔵 `starting` 구간 셋째 표본 ≈ **9분 10초**(08:51:52 → 09:01:02).
6. 🔴 **새로 본 틈** — 인스턴스 `running` 직후, 헬스가 **처음 발행되기 전 약 36~54초** 동안 `selection_ready=null` → 해석기가 옛 동작(`running`)으로 떨어져 **세 앱 모두 배너가 없었다.** 설계 표(§ Edge Cases «헬스 stale 도 null → running(옛 동작)»)가 **의도한** 동작이지만, 그 결과는 이 티켓이 없애려던 «켜졌다고 믿고 들어와 빈 화면» 과 같은 모양이다(짧을 뿐). 고칠지는 소유자 판단 — 이 티켓의 판정 술어(1~4)는 모두 통과했다.
