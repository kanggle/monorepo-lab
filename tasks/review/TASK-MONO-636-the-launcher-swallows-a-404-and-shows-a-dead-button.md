# Task ID

TASK-MONO-636

# Title

론처가 404 를 삼키고 **영원히 잠긴 버튼**을 보여준다 — 배포 안 된 기능과 고장난 기능이 구별되지 않는다

# Status

review

# Owner

monorepo

# Task Tags

- launcher
- demo
- bug

---

# Goal

`TASK-MONO-634` 가 넣은 카드의 「실시간 기능 시작」 버튼이 **회색으로 잠긴 채, 이유를 한 마디도
말하지 않는다.** 방문자에게는 «고장난 버튼» 으로 읽히는데 실제로는 «아직 배포 안 된 기능» 이다.

🔴🔴 **이것은 배포 지연이 아니라 코드 결함이다.** 배포가 끝나면 증상은 사라지지만, 같은
모양은 제어 API 가 잠깐 죽거나 라우트 이름이 바뀌는 날 **똑같이 재발한다.** 그때도 화면은
아무 말도 안 한다.

---

# Context — 실측 (2026-09-08 UTC, `main` = `d82d5c9bf`)

서빙 중인 론처는 `9f0fcd2d6` 판이 맞다(`build-info.json` 확인). 제어 API 를 직접 찌른 결과:

```
GET  /status        → 200  {"state":"stopped","ip":null,"used_minutes":379,"budget_minutes":600}
GET  /domains       → 200  {"state":"stopped","domains":{}, ...}
GET  /bundles       → 404  {"message":"Not Found"}
POST /bundle/start  → 404  {"message":"Not Found"}
```

`TASK-MONO-634` 가 만든 라우트 3개가 **API Gateway 에 없다** — `terraform apply` 미실행.
(그 자체는 예정된 미완이고 이 티켓의 대상이 아니다. § Scope 참조.)

## 결함의 사슬

```js
// infra/demo/aws/site/index.html — pollBundles()
const { ok, body } = await api("/bundles");
if (!ok) return;            // 🔴 404 를 조용히 삼킨다
```

1. `/bundles` 404 → `ok=false` → **early return**
2. `lastBundles` 가 `null` 로 남는다
3. `renderCards()` 에서 `info === null` ⇒ `startable=false` ⇒ 버튼 `disabled`
4. 배지도 `info ? text : "… 확인 중"` 이라 **초기 문구에 고정**

⇒ 방문자가 보는 것: **"… 확인 중" 배지 + 잠긴 회색 버튼**, 영원히. 안내문(`#bmsg`)은
`bundleStart()` 안에서만 채워지는데 그 함수는 **버튼을 눌러야** 돌고, 버튼은 잠겨 있다.

🔴 이 실패 모드는 같은 파일이 **이미 금지해 둔 것**이다 — `api()` 헬퍼 주석이
*"상태코드를 버리면 429 가 조용히 무시된다 — 사용자는 영원히 «기동 중» 을 보게 된다"* 라고
적어 두고 `ok` 를 돌려주게 만들었는데, `pollBundles` 가 그 `ok` 를 **받아서 버렸다.**

🔵 그리고 «아직 모른다»(폴링 전)와 «물어봤는데 없다»(404)가 **같은 화면**이 된다. 이 저장소가
반복해서 이름 붙인 축이다: 부재 판정과 미측정을 구별할 것.

---

# Scope

## 포함

- `infra/demo/aws/site/index.html` — `pollBundles()` / `renderCards()` / 배지·안내 문구
- `infra/demo/verify-demo-wrapper.sh` — 이 축을 무는 가드(아래 AC-3)

## 제외

- 🔴 **`terraform apply` 는 이 티켓이 아니다.** 그건 소유자 승인이 필요한 인프라 작업이고
  (`TASK-MONO-634` § 남은 것), 이 티켓은 **그것이 안 됐을 때 화면이 정직한가**를 고친다.
  둘을 섞으면 «배포하면 사라지는 증상» 이라는 이유로 코드 결함이 안 고쳐진다.
- 카드 썸네일(`TASK-MONO-634` AC-6) · Vercel Blob 배선(`TASK-MONO-635`)

---

# Acceptance Criteria

## AC-0 — 착수 전 재측정 (verify-then-act)

- [x] `GET /bundles` 를 **다시 찌른다.** 🔴 그 사이 `terraform apply` 가 돌았으면 200 이고,
      그러면 **이 티켓의 재현 조건이 사라진다** — 그때는 증상이 아니라 **코드**로 판정해야
      한다(`if (!ok) return;` 이 그대로면 결함은 그대로다). 라우트 유무로 티켓을 닫지 마라.
- [x] 서빙 중인 `build-info.json` 의 commit 이 무엇인지 적는다. 다른 판을 보고 고치면
      «안 고쳐지는 결함» 이 된다.

## AC-1 — 화면이 **이유를 말한다**

- [x] `/bundles` 가 404/5xx/타임아웃일 때 배지가 **"… 확인 중" 에 머물지 않는다.**
      상태를 «확인 불가» 로 바꾸고, 그 옆(또는 `#bmsg`)에 사유를 쓴다.
- [x] 404 와 5xx 를 **다른 문구**로 구별한다: 404 = «이 기능이 제어 API 에 아직 배포되지
      않았습니다», 5xx/타임아웃 = «제어 API 가 응답하지 않습니다».
      🔴 둘을 뭉치면 «배포하면 되는 것» 과 «장애» 가 같은 화면이 된다.
- [x] 그 문구가 **버튼을 안 눌러도** 보인다(잠긴 버튼은 클릭 이벤트가 안 난다).

## AC-2 — 폴백: 있는 정보라도 보여준다

- [x] `/bundles` 가 없어도 `/status`·`/domains` 는 **200 이다**(실측). 그 둘로 EC2 상태와
      도메인 헬스를 카드에 **최소한 표시**한다.
- [x] 🔴 그렇다고 「기동 버튼」을 열지 마라 — 누르면 404 다. 버튼은 잠긴 채로 두되
      **왜 잠겼는지가 보이면** 된다. 「보이게 하는 것」과 「눌리게 하는 것」은 다른 축이다.
- [x] 고급 영역(`/domain/start`)은 그대로 동작한다 — 그 경로는 배포돼 있다(실측 200).

## AC-3 — 가드

- [x] `(z14)` 의 노드 드라이버가 **`renderCards()` 를 실행하지 않는다**(대역 밖). 이 축을
      무는 가드를 **어디에 둘지 먼저 정하고 근거를 적는다** — (z14) 대역을 넓힐지, 새 칸을
      만들지. 🔴 대역을 넓히면 **실물보다 관대해지기 쉽다**(이 저장소의 반복 함정).
- [x] 가드는 «404 를 받았을 때 화면이 사유를 말하는가» 를 **실행해서** 본다. 문자열 grep 은
      자기 문서에 걸린다(이 저장소가 (z12)·그리고 `TASK-MONO-634` 자신에서 두 번 밟았다).
- [x] **bite**: `if (!ok) return;` 를 되살리면 그 가드가 **빨개지는지** 확인한다.

---

# Related Specs / Contracts

- [`ADR-MONO-071`](../../docs/adr/ADR-MONO-071-boot-the-bundle-the-visitor-chose.md) § D5 —
  「EC2 running ≠ 이 기능 준비됨」 7단계. 이 티켓은 그 표에 **«제어 API 가 이 기능을 모른다»**
  라는 상태가 빠져 있었음을 드러낸다
- `TASK-MONO-634` — 이 결함을 만든 티켓(`review/`)
- `TASK-MONO-551` — 「stale 을 정상으로 그리지 마라」의 선례. 같은 축이다

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| `/bundles` 404 | «아직 배포되지 않음» + 버튼 잠김 + **사유 표시** |
| `/bundles` 5xx·타임아웃 | «응답 없음» + 버튼 잠김 + 사유 표시(404 와 **다른 문구**) |
| `/bundles` 200 인데 `bundles` 키 없음 | 모르는 모양 = 확인 불가(조용히 빈 객체로 읽지 않는다) |
| `config.js` 부재 | 이미 처리됨 — 둘러보기는 살고 기동만 잠긴다(회귀 금지) |
| 배포 후 정상 | 배지가 실제 상태로 바뀌고 버튼이 열린다 |

---

# Failure Scenarios

1. **버튼을 그냥 열어 준다** → 누르면 404 이고 방문자는 «눌렀는데 아무 일도 안 남» 을 본다.
   지금보다 나쁘다(지금은 최소한 못 누른다).
2. **404 와 5xx 를 한 문구로 합친다** → 「apply 하면 되는 것」이 「장애」로 보여 엉뚱한 곳을 판다.
3. **가드를 문자열 grep 으로 짠다** → 이 티켓의 설명 문구 자체에 걸린다(같은 파일에서
   `TASK-MONO-634` 가 이미 한 번 당했다 — loose 대조 5≠3).

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** (단일 파일 UI 분기 + 가드 한 칸. 다만 가드를 어디에 둘지는
설계 판단이므로 그 자리에서 막히면 Opus).

---

# Verification — 구현 세션 (2026-09-08 UTC)

## AC-0 — 착수 전 재측정 (verify-then-act)

**서빙 판**: `GET https://hubwang.com/build-info.json` →

```json
{"commit":"9f0fcd2d6df70b3dee847163c82d4159cbe9d125","ref":"main","index_md5":"15deba8ffd93865a6983449db43d75d9"}
```

⇒ 기안이 본 판(`9f0fcd2d6`)과 **같은 판**을 보고 고쳤다. `main` 은 그 뒤 `e61847f5f`(이 티켓의
기안 커밋)까지 갔지만 그 커밋은 `tasks/` 만 건드렸으므로 사이트 산출물은 그대로다.

**제어 API 재측** (`https://r1tljg51qa.execute-api.ap-northeast-2.amazonaws.com`, 주소는 서빙 중인
`config.js` 의 `window.DEMO_API_BASE` 에서 읽었다 — 내 기억이 아니라 페이지가 쓰는 값이다):

| 요청 | 코드 | 본문 |
|---|---|---|
| `GET /status` | **200** | `{"state":"stopped","ip":null,"used_minutes":379,"budget_minutes":600}` |
| `GET /domains` | **200** | `{"state":"stopped","ip":null,"domains":{},"health_age_seconds":null,"health_stale":false}` |
| `GET /bundles` | **404** | `{"message":"Not Found"}` |
| `POST /bundle/start` | **404** | `{"message":"Not Found"}` |

⇒ `terraform apply` 는 아직 안 돌았다. **판정은 라우트가 아니라 코드로 했다** — `if (!ok)
return;` 이 그대로 있었고, 그것이 이 티켓의 대상이다. 라우트가 200 이 됐더라도 이 결함은
그대로였을 것이다(아래 bite 가 그것을 실행으로 보인다).

🔵 `used_minutes` = **379** 로 기안 때와 같다. 인스턴스가 `stopped` 라 누적이 없어서이고,
**같은 계량기를 두 시점에 읽은 두 표본**이다(같은 값이 나왔다는 것이 곧 재측정의 결과다).

## AC-1 · AC-2 — 화면이 이유를 말하는가 / 있는 정보라도 보여주는가

`infra/demo/aws/site/index.html`:

- `pollBundles()` 가 `{ok, status}` 를 **받아서 쓴다.** 404 → `bundlesErr="absent"`,
  그 밖의 `!ok` → `"down"`, `fetch` 자체 실패(네트워크·타임아웃·CORS) → `"down"`,
  200 인데 `bundles` 키가 없거나 객체가 아니면 → `"shape"`. 넷 다 `renderCards()` 를 부른다.
- 「아직 안 물어봤다」(`bundlesErr === null && info === null`)와 「물어봤는데 못 받았다」가
  **다른 값**이 됐다. 배지는 전자에서만 `… 확인 중` 이고, 후자에서는 `🔴 확인 불가` 다.
- 사유는 카드마다 새로 만든 `<div class="bnote" data-bundle-note>` 에 쓴다 —
  `#bmsg` 가 아니라 **거기**인 이유: `#bmsg` 는 `bundleStart()` 안에서만 채워지고 그 함수는
  버튼을 눌러야 도는데, 안내가 필요한 바로 그 상황에서 버튼은 잠겨 있다. 그리고 `#bmsg` 는
  기동 요청의 응답을 들고 있어서 여기서 쓰면 1.5초 뒤 폴링이 그 답을 지운다.
- 폴백 `bundleFallback()` 이 `/status`(EC2 state)와 `/domains`(그 카드가 여는 화면의 도메인
  헬스)를 읽는다. 🔴 «준비됨» 은 유추하지 않는다 — 묶음이 필요로 하는 나머지 도메인(iam 등)은
  `/bundles` 만 안다(ADR-MONO-071 § D5).
- **버튼은 잠긴 채로 둔다.** 사유를 말하는 것과 누를 수 있게 하는 것은 다른 축이고, 열면
  눌러서 404 를 받는다(§ Failure Scenarios 1).
- 고급 영역(`/domain/start`)은 **한 줄도 안 건드렸다** — `git diff` 로 확인.

가드 (z34) 의 실행 대조 출력 (카드 3장 × 7시나리오, `배지 / 버튼 / 사유` 발췌):

| 시나리오 | 배지 | 버튼 | 사유 |
|---|---|---|---|
| `PRE` (폴링 전) | `… 확인 중` | 잠김 | (없음) |
| `E404` | `🔴 확인 불가` | 잠김 | 이 기능이 제어 API 에 아직 배포되지 않았습니다 (/bundles → 404). … (EC2 대기 중 · 도메인 console 확인 중) |
| `E500` (503) | `🔴 확인 불가` | 잠김 | 제어 API 가 응답하지 않습니다 — 잠시 후 자동으로 다시 시도합니다. … |
| `ENET` (fetch 실패) | `🔴 확인 불가` | 잠김 | (E500 과 **같은** 문구 — 둘 다 «응답이 없다») |
| `SHAPE` (200, 키 없음) | `🔴 확인 불가` | 잠김 | 제어 API 응답을 해석할 수 없습니다 — 묶음 목록이 들어 있지 않습니다. |
| `E404R` (같은 404, EC2 running) | `🔴 확인 불가` | 잠김 | … (EC2 **실행 중** · 도메인 console **실행 중**) ← 폴백이 `/status` 를 실제로 읽는다 |
| `OK` | `⚪ 꺼짐` / `🟢 사용 가능` / `🟡 기동 중…` | `waiting` 만 열림 | (없음) |

## AC-3 — 가드를 어디에 둘 것인가

**결정: (z14) 를 넓히지 않고 새 칸 (z34) 를 만든다.** 근거(가드 머리말에 그대로 적혀 있다):

- (z14) 의 최소 DOM 대역은 `querySelectorAll()` 이 **셀렉터와 무관하게** 표면 앵커 행을
  돌려주고, 원소에 `querySelector` 가 없다. 카드 렌더는 **다른 모집단**(`[data-bundle]`)을
  훑고 `card.querySelector()` 로 자식 넷을 꺼내며 `className` 을 쓴다.
- 한 대역이 둘 다 흉내 내려면 `querySelectorAll` 이 셀렉터로 분기해야 하는데, 그렇게
  똑똑해진 스텁이 바로 이 저장소가 반복해서 밟은 «실물보다 관대한 대역» 이다.
- 부작용 하나 더: 합쳤다면 (z14) 의 빨강이 «링크 판정이 틀렸다» 인지 «카드 대역이 좁다» 인지
  구별되지 않는다. 지금은 **어느 칸이 빨간지가 곧 사유**다.

가드가 지키는 것들 — 전부 **실행 대조**이고 문자열 grep 이 아니다:

- 구간 앵커(`GUARD-Z34-BEGIN/END`)가 `renderCards` · `pollBundles` · `bundleFallback` 을
  포함하는지 단언한다 — 구간이 좁아지면 이 칸이 조용히 공허해지는 것을 막는다
  (TASK-MONO-603 이 (z14) 에서 당한 그것).
- 카드 모집단을 **마크업에서 뽑는다**(`data-bundle` ↔ 그 카드의 `data-surface` 도메인).
  loose 개수(`data-bundle="` 3건) ↔ 짝지어진 개수(3장)를 대조하고, 0장이면 실패한다.
- 「404 와 5xx 가 다른 문구인가」는 소스가 아니라 **두 실행 출력을 서로 비교**해서 본다 —
  이 축을 grep 으로 짜면 이 티켓 자신의 설명 문구에 걸린다(§ Failure Scenarios 3).
- 대역이 **모르는 셀렉터를 만나면 `null` 이 아니라 죽는다.** 조용히 `null` 을 주면 코드가
  새 원소를 읽어도 가드는 «없어서 안 그렸다» 를 통과시킨다.

### bite — 결함을 되살리면 빨개지는가

가드 **안에** 두 칸이 상주한다. 각 칸은 ① 주입됐는가 ② 그래도 실행되는가(문법을 안 깼는가)
③ 술어가 무는가 를 **따로** 단언한다 — 변형이 node 를 죽이면 그 빨강은 가드가 문 것이 아니다.

| bite | 변형 | 결과 |
|---|---|---|
| bite-1 | `// GUARD-Z34-BITE` 줄을 `if (!r.ok) return;` 으로 되돌린다 | **물었다** |
| bite-2 | `absent:` 문구를 `down:` 의 값으로 덮어 404·5xx 를 뭉갠다 | **물었다** |

그리고 **파일 수준으로도** 한 번 더 확증했다(구간이 아니라 `index.html` 자체를 변형한 사본에
`ROOT` 를 걸어 셀을 돌렸다) — `rc=1`, 사유가 이 티켓의 증상 그대로였다:

```
[E404] console 배지가 미측정 문구에 머물렀습니다 — «물어봤는데 없다» 와 «아직 모른다» 가 같은 화면입니다
[E404] console 가 사유를 한 마디도 말하지 않습니다 — 방문자는 이유 없는 회색 버튼을 봅니다
[console] 404 와 5xx 의 문구가 같습니다 — «배포하면 되는 것» 과 «장애» 가 한 화면이 되어 …
```

## 실행한 게이트

| 게이트 | 결과 |
|---|---|
| `bash infra/demo/verify-demo-wrapper.sh` (정적 전량) | ✅ **rc=0 · 83 ok · FAIL 0** — (z14) 초록 유지 · (z34) 신규 초록 |
| `bash -n infra/demo/verify-demo-wrapper.sh` | ✅ rc=0 (ci.yml:1489 이 같은 검사를 한다) |
| INDEX·task 가드 5종 (`check-index-queue-drift` · `check-task-id-collision` · `check-walkthrough-ledger-drift` · `check-adr-index-drift` · `check-project-adr-index-drift`) | ✅ 전부 rc=0 |
| `bash scripts/check-message-backticks.sh` | ✅ 131개 파일, 위반 0건 |
| `index.html` 인라인 스크립트 파싱(`new Function`) | ✅ 파싱 성공 |
| (z34) 단독 하네스(`set -uo pipefail` 복제) | ✅ 카드 3장 × 7시나리오 · bite 2칸 |
| (z34) 파일수준 bite (변형 사본 + `ROOT` 교체) | ✅ `rc=1` 로 빨개짐 |

🔴 **첫 실행은 `rc=124`(타임아웃) 였고 그것은 성공이 아니다.** (z13) 이 30초짜리 매달림
shim 을 프로젝트마다 돌려서 `timeout 900` 안에 안 끝났다. 그 셀을 자르거나 건너뛰지 않고
**`timeout 3000` 으로 다시 재서** `rc=0` 을 얻었다 — 막다른 길이면 그 길을 막은 관측을 다시
재라는 쪽이다.

🔴 **미측정**: 라이브 화면 확인은 못 했다. 이 변경은 `hubwang.com` 에 배포돼야 눈으로 보이고,
그 배포는 `main` 머지 → Vercel 이 처리한다. 머지 뒤 `build-info.json` 의 commit 이 이 PR 의
스쿼시 커밋으로 바뀌는지 한 번 확인해야 한다(그때까지 이 축은 «안 쟀다»).

---

# 이 티켓이 드러낸 것 (닫지 않고 기록만)

`ADR-MONO-071 § D5` 의 7단계(`waiting`·`requested`·`booting`·`ready`·`partial`·`stopping`·
`unknown`)에는 **«제어 API 가 이 기능을 아예 모른다»** 라는 상태가 없다. 그 상태는 묶음의
상태가 아니라 **제어 평면의 상태**이므로 7단계에 끼워 넣을 값이 아니고, 이 티켓은 그것을
`bundlesErr` 라는 **별도 축**으로 두었다. ADR 본문 수정은 이 티켓의 § Scope 밖이라 하지
않았다 — 필요하면 별도 티켓으로 D5 에 각주를 다는 것이 맞다.
