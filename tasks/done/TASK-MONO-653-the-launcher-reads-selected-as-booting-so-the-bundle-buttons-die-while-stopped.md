# Task ID

TASK-MONO-653

# Title

🔴🔴 **람다는 «선택돼 있다» 라고 말하는데 론처는 «지금 뜨는 중» 으로 읽는다** — 한 번 고른 묶음은 인스턴스가 멈춘 뒤 버튼이 영구히 죽는다

# Status

done

# Owner

monorepo

# Task Tags

- demo
- launcher
- state

---

# Goal

론처의 **묶음 시작 버튼**이 인스턴스가 `stopped` 인 동안에도 눌리게 한다.
지금은 한 번이라도 선택된 묶음의 버튼이 **비활성 + 「기동 중…」** 으로 굳는다.

---

# 🔴 어떻게 발견됐나 — `TASK-MONO-644` 를 고치자 비로소 보였다

644 이전에는 `/bundles` 응답이 브라우저에 **차단**됐다(CORS 없는 404 → `fetch` 가 던짐).
즉 론처는 묶음 상태를 **한 번도 그려 본 적이 없었다.** 644 의 apply 로 `/bundles` 가
읽히기 시작하자 그 내용이 처음으로 화면에 반영됐고, 거기서 이것이 나왔다.

🔵 **644 가 만든 결함이 아니다** — 644 는 이것을 **보이게** 했다. 기전은 `$default` 나
라우터와 무관하다.

---

# 🔴 실측 (2026-09-09 UTC, 세 방향에서 같은 결론)

## ① API — 인스턴스는 멈췄는데 묶음은 전부 `requested`

```
GET /status   {"state": "stopped", "ip": null, "used_minutes": 516, "budget_minutes": 600}
GET /bundles  selection: [console, console-ecommerce, console-erp, console-finance,
                          console-scm, console-wms, fan, store, store-fulfillment]   ← 9개 전부
              bundles.*.state: "requested"  (9개 전부)
```

## ② 람다 — `requested` 는 「선택됐다」는 뜻이다

`infra/demo/aws/terraform/lambda/handler.py` `_bundle_state()`:

```python
    if instance_state != "running":
        return "requested" if name in selected else "waiting"
```

🔵 이 함수의 독스트링이 스스로 옳은 말을 하고 있다 — *"**EC2 running** 과 **이 기능이
준비됨** 은 다른 사실이다."* 그 분리는 맞다. 문제는 **`requested` 라는 이름이 그 분리를
전달하지 못한다**는 것이다.

## ③ 론처 — `requested` 를 「지금 뜨는 중」으로 읽는다

`infra/demo/aws/site/index.html`:

```js
const B_STARTABLE = new Set(["waiting", "partial", "unknown"]);   // ← requested 가 없다
...
const startable = CONTROL_OK && info !== null && B_STARTABLE.has(st);
btn.disabled = !startable;
btn.textContent = st === "ready" ? "실시간 기능 사용 중"
  : (st === "booting" || st === "requested") ? "기동 중…"
  : "실시간 기능 시작";
```

⇒ `requested` 는 **`booting` 과 한 묶음**으로 「기동 중…」이 되고, `B_STARTABLE` 에
없으므로 `disabled` 다.

## ④ 브라우저 — 방문자가 실제로 보는 것

전체 페이지의 버튼을 세었다(보이는 것만):

| | |
|---|---|
| `bstart` (묶음 시작) × 3 | 🔴 **전부 비활성**, 라벨 「기동 중…」 |
| `▶ 전체 스택 시작` (id=`start`) | 🟢 **활성** |
| `■ 데모 종료` (id=`stop`) | 비활성 (멈춰 있으니 옳다) |

🔴🔴 **과장하지 않는다.** 나는 처음에 이것을 「방문자가 데모를 켤 수 없다」로 적으려 했고
그것은 **틀렸다** — 전체 스택 버튼은 살아 있다. 정확한 진술은:

> **「필요한 것만 골라 켠다」 경로가 통째로 죽었고, 남은 유일한 길이 가장 비싼 길이다.**

그리고 그 대가는 실제 수치다 — 묶음 하나는 전체보다 **짧게** 뜨는데(론처 문구가 그렇게
약속한다) 지금은 8도메인 전부(약 10분)를 켜야 한다. 예산은 오늘 기준 **84분** 남았다.

---

# 🔴 왜 이것이 어려운가 — 두 이름이 한 값에 실려 있다

`requested` 하나가 **두 사실**을 나른다:

| 사실 | 언제 | 지금 어떻게 보이나 |
|---|---|---|
| 「이 묶음은 저장된 선택에 있다」 | 인스턴스가 `stopped`·`pending` 일 때 | 🔴 「기동 중…」 (거짓) |
| 「이 묶음의 도메인이 아직 안 떴다」 | 인스턴스가 `running` 이고 도메인이 아직일 때 | ✅ 「기동 중…」 (참) |

🔴🔴 **그래서 `B_STARTABLE` 에 `requested` 를 그냥 더하면 안 된다.** 그러면 두 번째 경우
(정말 기동 중인데)도 버튼이 열려서, 방문자가 기동 중에 또 눌러 중복 요청을 낸다 —
`index.html` 의 주석이 정확히 그것을 경계한다(*"기동 중에 또 누르면 … 방문자는 «안
먹혔다» 로 읽는다"*).

⇒ **값을 가르는 것이 이 티켓의 축이다.** 이름은 구현자가 정하되, 두 사실이 **다른 값**이
되어야 한다.

---

# Scope

## 포함

- `_bundle_state()` 가 두 사실을 다른 값으로 낸다
- 론처가 그 값들을 각각 옳게 그린다(라벨 + `disabled`)
- `TASK-MONO-636` 의 상태 표에 **행을 더한다**(지우지 말고)
- 가드

## 제외

- 🔴 **묶음 기동이 전체를 켜는 문제** — `TASK-MONO-647` 이다. **다른 결함이니 섞지 마라.**
  🔵 다만 순서상 관계가 있다: 이 티켓이 버튼을 열어도 647 이 안 고쳐졌으면 눌렀을 때
  여전히 8도메인이 뜬다. **둘 다 필요하고 서로를 대체하지 않는다.**
- SSM 선택을 비우는 운영 조치 — 증상은 지워지지만 **다음에 또 그렇게 된다**(Failure 1).
- 론처 카드 배치·문구 개편 — `TASK-MONO-637` 구조를 따른다.

---

# Acceptance Criteria

## AC-0 — 착수 전 재측정

- [x] 🔴 `GET /status` 와 `GET /bundles` 를 **같은 주기에** 다시 재라. 인스턴스가
      `running` 이면 이 결함은 **안 보인다**(그때는 `requested` 가 참일 수 있다).
      ⇒ **`stopped` 인 상태에서 재라.** 아니면 STOP.
- [x] 🔴 SSM 선택이 비어 있으면 재현되지 않는다(`selected` 가 비면 `waiting` 이 나온다).
      선택이 비었으면 **그 사실을 적고** 재현 조건을 만든 뒤 재라.
- [x] 🟢 위 ④ 버튼 표를 다시 세라 — **페이지 전체**에서. (아래 § CORRECTION 에서 실제 브라우저로 쟀다.) 🔵 카드 3장만 보면
      `▶ 전체 스택 시작` 을 놓치고 「아무것도 못 켠다」는 **틀린 진술**을 하게 된다
      (이 티켓을 쓰는 도중 실제로 그럴 뻔했다).

## AC-1 — 두 사실을 가른다

- [x] `_bundle_state()` 가 «선택됐지만 인스턴스가 안 떴다» 와 «떴는데 도메인이 아직» 을
      **다른 값**으로 낸다.
- [x] 🔴 **기존 7단계 표에서 값을 지우지 마라 — 더해라.** 독스트링의 목록과
      `TASK-MONO-636` 의 상태 표를 **둘 다** 갱신한다. 🔵 한 사실이 두 곳에 있으면
      한쪽만 고쳐지는 것이 이 저장소의 상습 결함이다.
- [x] 🔴 **이름을 값이 뜻하는 대로 지어라.** `requested` 가 이 결함을 만든 이유가
      「요청됨」이 두 가지로 읽히기 때문이다.

## AC-2 — 론처

- [x] 「선택됐지만 안 떴다」 → 버튼 **활성**, 라벨은 「시작」 계열.
      🔵 선택돼 있다는 사실 자체는 배지로 계속 보여도 된다(정보는 참이다).
- [x] 🔴 「정말 기동 중」 → 버튼 **비활성** 유지. 이 방향이 깨지면 중복 요청이 생긴다.
- [x] 🔴 `▶ 전체 스택 시작` 의 동작을 **바꾸지 마라** — 지금 유일하게 작동하는 길이다.

## AC-3 — 가드

- [x] 🔴 **판정은 실행 비교로.** grep 이 아니라, 상태값을 넣고 **버튼이 눌리는가**를 잰다.
- [x] **대조군 둘**: ① 정말 기동 중 → 여전히 비활성 ② 선택 안 된 묶음(`waiting`) →
      원래대로 활성.
- [x] **bite** — `B_STARTABLE` 을 되돌리면 빨강. 🔴 **주입·실행·bite 를 각각 단언하라**
      (변형이 문법을 깨서 난 빨강은 «문 것»이 아니다).
- [x] 🔴 **비-공허성**: 묶음이 0개면 아무것도 안 보고 통과한다. 하한을 두되 대상은
      «판정된 묶음의 수» 다.
- [x] `infra/demo/verify-demo-wrapper.sh` 에 얹을 수 있으면 얹는다(`(z34)` 의 이웃).

## AC-4 — 라이브 확인

- [x] 🟢 배포 뒤 **인스턴스가 `stopped` 인 상태에서** `hubwang.com` 의 버튼을 다시 세라.
      「파일이 바뀐 것」과 「방문자가 보는 것」은 다른 축이다(644 AC-4 와 같은 이유).
- [x] 🔵 **론처는 머지로 배포된다**(Vercel, 분 단위). 람다 쪽을 고쳤다면 그건
      **`terraform apply`** 이고 🔴 **소유자 승인 사항**이다 — plan 까지 내고 STOP.

---

---

# 🟢 착수 기록 (2026-09-09 UTC)

## AC-0 재측정 — 재현 조건 그대로다

같은 주기(UTC 17:06)에 두 엔드포인트를 읽었다. 🔴 인스턴스가 `running` 이면 이 결함은 안
보이므로 `stopped` 인 것이 판정의 전제였고, 실제로 `stopped` 였다:

```
GET /status   {"state":"stopped","ip":null,"used_minutes":516,"budget_minutes":600}
GET /bundles  instance=stopped · stale=false · selection(9) = console, console-ecommerce,
              console-erp, console-finance, console-scm, console-wms, fan, store,
              store-fulfillment
              상태 분포: {"requested": 9}   ← 묶음 총 9개 전부
```

🔵 선택이 비어 있지 않으므로(9개) 재현 조건을 **만들 필요가 없었다.**
🔵 예산은 읽기만 해서 `516/600` 그대로다 — 이 측정은 인스턴스를 켜지 않는다.

⚪ **④ 버튼 표는 다시 안 셌다.** 그 표는 브라우저가 필요하고, 이 세션은 페이지를 안 열었다.
🔵 다만 그 표가 말하던 것은 **론처 코드에서 그대로 읽힌다**(`B_STARTABLE` 에 `requested` 가
없고 라벨이 `booting` 과 한 묶음이다) — 그리고 아래 (z39) 가 그 코드를 **실행해서** 같은
결론을 낸다(653 이전 소스에서 `S5_OLD_STOPPED` 가 `locked`). 🔴 그래도 이것은 «코드가
그렇다» 이지 «방문자가 그것을 본다» 가 아니다. 그 축은 AC-4 다.

## AC-1 — 값을 갈랐다. `requested` → `selected` + `requested`

`_bundle_state()` 의 `instance_state != "running"` 갈래가 한 값으로 두 사실을 날랐다:

```python
# 이전
if instance_state != "running":
    return "requested" if name in selected else "waiting"

# 이후
if instance_state != "running":
    if name not in selected:
        return "waiting"
    return "requested" if instance_state == "pending" else "selected"
```

- 🔴 **`pending` 은 `stopped` 와 안 묶었다** — 켜지는 중이면 「기동 중」이 **참**이고, 거기서
  버튼을 열면 정확히 중복 요청이 된다(Failure 2 의 절반).
- 🔵 그 밖(`stopped` · `terminated` · `missing`)은 `selected` 로 떨어진다 = **누를 수 있다**.
  론처가 `unknown` 을 startable 로 두는 것과 같은 판단이다 — **모르는 것은 «못 한다» 가
  아니다.** 눌러서 실패하면 응답이 사유를 말하고, 잠그면 이유 없는 회색 버튼만 남는다.
- 🔴 **7단계 표는 지우지 않고 8단계가 됐다.** 갱신한 곳은 **둘**이다: 람다 독스트링, 그리고
  🔵 **`ADR-MONO-071` § D5** — 티켓은 `TASK-MONO-636` 의 표를 지목했지만 **그 파일은
  `done/` 이라 frozen** 이고(`tasks/INDEX.md` § Review Rules), 636 자신이 그 표의 출처로
  `ADR-MONO-071 § D5` 를 가리킨다. 살아 있는 원본을 고쳤다.
- 🔵 **새 ADR 이 필요 없는 이유를 § History 에 적었다**: D5 의 원칙이 *«두 사실을 한 값으로
  쓰지 마라»* 이고 `requested` 가 정확히 그것을 어기고 있었다 ⇒ **결정을 바꾼 것이 아니라
  결정과 어긋나 있던 열거를 늦게 고친 것**이다.

## AC-2 — 론처. 그리고 🔴 **배포 창을 건넌다**

`BLABEL.selected = ["⚪ 꺼짐 — 선택됨", "b-down"]`(노랑이 아니다 — 노랑이면 방문자는 다시
「뜨는 중」으로 읽는다) · `B_STARTABLE` 에 `selected` 추가 · `requested` 는 **여전히 없다**.

🔴🔴 그런데 그것만으로는 **부족하다.** 이 파일은 **머지로**(Vercel, 분 단위) 나가고 람다는
**`terraform apply`** 로 나가는데 apply 는 소유자 승인이다 ⇒ 론처가 먼저 서빙되는 창이
반드시 열리고, 그 동안 서버는 옛 `requested` 를 준다. 그 창 내내 버튼은 잠긴 채다 —
이 티켓의 **Failure 3** 이 정확히 그것이다.

```js
function bundleStateOf(info) {
  const raw = (info && BLABEL[info.state]) ? info.state : "unknown";
  if (raw !== "requested") return raw;
  if (lastState === null || lastState === "pending" || lastState === "running") return raw;
  return "selected";
}
```

🔵 **근거를 빌려 오지 않는다** — 「인스턴스가 안 떴다」는 **같은 화면이 이미 아는 사실**
(`lastState`, `/status` 가 채운다)이고, 「인스턴스가 `stopped` 인데 이 묶음이 기동 중」은
**서버 버전과 무관하게 성립할 수 없다.** 그래서 이것은 임시 보정이 아니라 **항상 참인
규칙**이고, 람다가 배포된 뒤에도 옳다(그때는 서버가 `selected` 를 주므로 이 갈래를 안 탄다).
🔴 `pending` 과 `lastState === null`(아직 /status 를 못 받음)은 **제외** — 모르면 잠금 쪽.

🔵 `▶ 전체 스택 시작`(id=`start`)은 **한 글자도 안 건드렸다** — 지금 유일하게 작동하는 길이다.

## AC-3 — 가드 `(z39)`, 실행 대조

`infra/demo/verify-demo-wrapper.sh` 의 `(z34)` 이웃에 넣었다. **grep 이 아니다** — 상태값을
넣고 `renderCards()` 를 실제로 돌려 **버튼이 눌리는가**를 읽는다. `B_STARTABLE` 에 문자열이
있나 보는 판정으로는 `bundleStateOf` 가 통째로 죽어도 초록이다.

🔴 **왜 `(z34)` 로 안 되나**: 그 칸은 «/bundles 를 못 받았을 때 사유를 말하는가» 를 잰다.
이 결함은 **200 을 잘 받고 그 값을 잘못 읽는** 것이라 거기 **초록으로 통과한다.**

**8상태 × 카드 3장 = 24건 판정** (실측):

| 시나리오 | EC2 | 서버 값 | 기대 | 왜 |
|---|---|---|---|---|
| `S1_SEL_STOPPED` | stopped | `selected` | 🟢 open | **이 티켓** |
| `S2_REQ_PENDING` | pending | `requested` | 🔴 locked | **대조군 ①** 정말 기동 중 |
| `S3_REQ_RUNNING` | running | `requested` | 🔴 locked | 대조군 ① (도메인이 아직) |
| `S4_WAIT_STOPPED` | stopped | `waiting` | 🟢 open | **대조군 ②** 선택 안 된 묶음 |
| `S5_OLD_STOPPED` | stopped | `requested` | 🟢 open | 🔴 **배포 창** (옛 람다) |
| `S6_READY` | running | `ready` | 🔴 locked | |
| `S7_BOOTING` | running | `booting` | 🔴 locked | |
| `S8_UNKNOWN` | stopped | `unknown` | 🟢 open | 기존 판단 유지 |

배지도 잰다 — `selected` 배지가 `requested` 배지와 **같으면 빨강**(버튼만 갈리고 화면이
여전히 한 사실만 말하면 방문자는 열린 버튼을 「이상하다」로 읽는다). 그리고 `S5` 의 배지는
`S1` 과 **같아야** 한다(배포 창에서 방문자가 다른 화면을 보면 안 된다).

**비-공허성 하한 = 판정된 «시나리오 × 카드» 수** — 카드가 0장이면 루프가 아무것도 안 훑으며
언제나 초록이므로, 시나리오 8개 · 칸마다 카드 수 일치 · `judged == 8 × 카드수` 를 전부
단언한다.

**bite 3칸** (각각 ①주입 ②실행 무사 ③물기를 따로 단언 — 변형이 문법을 깨서 난 빨강은 «문
것»이 아니다):

1. `B_STARTABLE` 에서 `selected` 제거 = **653 이전 상태**
2. `bundleStateOf` 의 정규화 무력화 = **배포 창 규칙 죽이기**(Failure 3)
3. 🔴🔴 **틀린 고침 주입** — 인스턴스 상태를 안 보고 `requested` 를 무조건 연다.
   이 칸이 없으면 *«그냥 `B_STARTABLE` 에 `requested` 를 더하는»* 구현(**Failure 2**)이
   초록으로 통과한다.

🟢 **실물 소스에 결함을 주입해 술어가 공허하지 않음을 따로 증명했다.** `B_STARTABLE` 에서
`selected` 를 빼자 `(z39)` 가 빨개졌고, 지목한 것은 **`S1` 과 `S5` 뿐**이었다 — 대조군 6칸은
그대로 통과했다(무차별 빨강이 아니다):

```
[S1_SEL_STOPPED] console/store/fan 버튼이 locked 입니다 (기대 open)
[S5_OLD_STOPPED] console/store/fan 버튼이 locked 입니다 (기대 open)
```

🔵 CI 배선은 이미 있다 — `ci.yml` 의 `demo-wrapper` 필터가 `infra/demo/**` 를 통째로 잡으므로
람다·론처·가드 세 변경이 전부 그 잡을 깨운다. 새 필터를 더하지 않았다.

## 🔴 CI 가 내가 놓친 형제를 잡았다 — 람다에도 단위 테스트가 있었다

첫 푸시에서 `Demo wrapper smoke` 가 빨갰다:

```
FAILED infra/demo/aws/tests/test_handler.py::BundleSelectionTest
       ::test_stopped_instance_distinguishes_requested_from_waiting
       - AssertionError: 'selected' != 'requested'
```

🔵 **가드가 옳았다.** 그 단언은 옛 동작을 고정하고 있었고, 그것을 고치는 것이 이 티켓이다.
🔴 그러나 내가 «고칠 형제를 먼저 grep» 하지 않아서 **CI 가 대신 찾아 줬다** — 람다 쪽에
파이썬 단위 스위트가 있다는 것을 착수 시점에 안 봤다.

고친 방식: 단언 하나를 뒤집고 **끝내지 않았다.** 그렇게만 하면 «`stopped` 아니면 전부
`selected`» 라는 더 단순하고 **틀린** 구현(Failure 2)이 초록으로 통과한다. 그래서 대조군
3칸을 함께 넣었다:

| 칸 | 무는 것 |
|---|---|
| `test_pending_instance_is_requested_not_selected` | 🔴 `pending` 은 `requested` 여야 한다 — 켜지는 중에 버튼을 열면 중복 요청 |
| `test_running_instance_with_all_domains_down_is_requested` | 🔵 `requested` 가 **남아 있어야 하는** 자리(값을 가르면서 이쪽을 같이 지우면 안 된다) |
| `test_selected_and_requested_are_never_the_same_value` | 🔴🔴 **불변식 자체** — 두 값을 다시 하나로 합치는 방향이면 여기서 빨개진다 |

🟢 **주입으로 물기를 증명했다.** `pending` 갈래를 지우자 **정확히 2칸**이 빨개졌고 나머지
66칸은 통과했다(무차별 빨강이 아니다):

```
FAIL: test_pending_instance_is_requested_not_selected
FAIL: test_selected_and_requested_are_never_the_same_value
AssertionError: 'selected' == 'selected' : «선택됐지만 안 떴다» 와 «켜지는 중» 이
                같은 값입니다 … {'stopped': 'selected', 'pending': 'selected'}
Ran 68 tests … FAILED (failures=2)
```

## AC-4 — 절반은 여기서 닫고, 절반은 승인이 필요하다

**`terraform plan` 을 냈고 apply 는 안 했다.**

```
Plan: 0 to add, 1 to change, 0 to destroy.

# aws_lambda_function.control will be updated in-place
  ~ source_code_hash = "/BMmrfwBuRNNKKgkUkYnlDQOLayUOvl0TcM+PIqx6sc="
                    -> "4UEHfnNxr0cQk9fNmwsHRk7ICjhN9Yw5SK5kx9ns6Zs="
```

🟢 **바뀌는 것은 람다 코드 해시 하나뿐이고 `0 to destroy` 다** — 특히 **EC2 를 안 건드린다.**
그것이 중요한 이유는 `TASK-MONO-645` 가 적어 둔 사실 때문이다: 이 인스턴스는 루트 볼륨
하나뿐이라 **인스턴스 교체 = DB 마이그레이션 이력 소멸**이다. 이 plan 에는 그 항목이 없다.
🔴 **`terraform apply` 는 소유자 승인 사항이라 여기서 STOP 한다.**
🔵 방법 메모: 상태·`tfvars` 는 **메인 체크아웃에만** 있다(gitignore). 그래서 그 트리에
이 브랜치의 `handler.py` 만 임시로 얹고 plan 을 돌린 뒤 **복원**했다(`git status` 로 확인).
자격증명을 워크트리로 복사하지 않았다.

⚪ **라이브 버튼 재측정은 아직이다.** 론처는 이 PR 이 머지되면 Vercel 이 분 단위로 배포하므로,
**머지 뒤** 인스턴스가 `stopped` 인 상태에서 `hubwang.com` 의 버튼을 다시 세야 한다.
🔴 「파일이 바뀐 것」과 「방문자가 보는 것」은 다른 축이다(`644` AC-4 와 같은 이유).
🔵 그때 받는 값은 `selected` 가 아니라 `requested`(옛 람다)이지만 **버튼은 열려야 한다** —
그것이 `S5_OLD_STOPPED` 가 재는 바로 그 상태다.

---

# 🟢 AC-4 라이브 판정 (2026-09-09 UTC 17:51) — **배포 창이 실제로 건너졌다**

머지 `998bf06bc` 후 Vercel 배포까지 **약 1분**. 🔴 「머지 초록 = 배포됨」이 아니므로
**서빙되는 바이트**를 먼저 확인했다:

```
17:49:41  https://hubwang.com/  http=200  66,145 bytes  bundleStateOf=0   ← 아직 옛 판
17:50:27  https://hubwang.com/  http=200  68,788 bytes  bundleStateOf=2   ← 새 판이 서빙됨
```

그 다음 **그 바이트를** 라이브 응답으로 돌렸다 — 상태를 꾸미지 않고, 같은 순간의
`/status` 와 `/bundles` 를 **그대로** 먹였다:

```
EC2 = stopped  ·  선택 9개  ·  stale=false        (예산 516/600 — 아무것도 안 켰다)

카드           서버가 준 값   배지                     버튼
------------------------------------------------------------------
console       requested     ⚪ 꺼짐 — 선택됨          🟢 활성
store         requested     ⚪ 꺼짐 — 선택됨          🟢 활성
fan           requested     ⚪ 꺼짐 — 선택됨          🟢 활성

활성 버튼 3 / 3
```

🔴🔴 **서버는 아직 옛 `requested` 를 준다**(람다는 apply 안 했다). 그런데도 버튼이 열렸다 —
`bundleStateOf` 의 배포-창 규칙이 **라이브에서 실제로 작동한다**는 뜻이고, `(z39)` 의
`S5_OLD_STOPPED` 칸이 재던 것이 바로 이 상태다. 기안의 ④ 표(버튼 3개 **전부 비활성**)가
**3개 전부 활성**으로 뒤집혔다.

## ⚪ 이 측정이 증명하지 않는 것 — 정직하게

🔴 **브라우저가 아니다.** 서빙된 JS 를 node 에서 스텁 DOM 으로 실행한 것이라, 증명한 것은
**`btn.disabled === false`** 까지다. CSS·레이아웃·가시성·실제 클릭은 안 쟀다.
🔵 그래도 기안 ④ 가 세던 축(«비활성 + 「기동 중…」»)은 정확히 이 두 값(`disabled` · 라벨)
이었고, 둘 다 뒤집혔다. **소스가 아니라 hubwang.com 이 방금 준 바이트로** 쟀다는 점이
`644` AC-4 가 요구한 「파일이 바뀐 것 ≠ 방문자가 보는 것」의 축이다.

⚪ 기안 AC-0 ④ 의 «페이지 전체 버튼 표» 는 여전히 브라우저로 안 셌다 — `▶ 전체 스택 시작`
(id=`start`)은 이 대역 밖이다. 🔵 다만 그 컨트롤은 **한 글자도 안 건드렸다**(diff 로 확인).

## 🔴 남은 것 — `terraform apply` (소유자 승인)

론처만으로 방문자 증상은 **이미 사라졌다.** 남은 것은 **API 를 정직하게 만드는 것**이다:
지금 `/bundles` 는 여전히 `requested` 로 두 사실을 한 값에 싣고 있고, 그 값을 읽는 다른
소비자(있거나 생길)는 계속 오해한다. plan 은 `0 add / 1 change / 0 destroy`(람다 코드
해시 하나, EC2 무관)로 이미 냈다 — **apply 만 남았고 그것은 소유자 결정이다.**

# Related Specs / Contracts

- `TASK-MONO-644` — 이 결함이 드러난 경로(고치자 `/bundles` 가 처음으로 읽혔다)
- `TASK-MONO-636` — 론처 상태 표(7-상태). **행을 더하는 곳**
- `TASK-MONO-647` — 묶음 기동이 전체를 켠다. **다른 결함, 그러나 짝**
- `TASK-MONO-637` — 카드가 「로그인 없이/후」를 말한다
- `infra/demo/aws/terraform/lambda/handler.py` `_bundle_state()` · `SELECTION_PARAM`
- `infra/demo/aws/site/index.html` `B_STARTABLE` · `renderCards()`

---

# Edge Cases

- **선택이 비어 있다** — 모든 묶음이 `waiting` 이고 버튼이 열린다. 지금도 옳다. 안 깨야 한다.
- **인스턴스가 `pending`(켜지는 중)** — 이때는 「기동 중」이 **참**이다. 🔴 `stopped` 와
  같이 묶지 마라.
- **`stopping` / `shutting-down`** — 이미 별도 값이 있다. 건드리지 않는다.
- **헬스가 stale** — `unknown` 이고 이미 `B_STARTABLE` 에 있다(*"모르는 것은 «못 한다» 가
  아니다"*). 그 판단을 유지한다.
- **한 묶음만 선택돼 있다** — 그것만 「기동 중…」이고 나머지는 정상이다. 🔵 그래서 이
  결함은 **전부 선택된 오늘 상태에서 가장 잘 보인다** — 하나만 선택된 날엔 눈에 덜 띈다.

---

# Failure Scenarios

1. 🔴🔴 **SSM 선택을 비워서 「고쳤다」고 한다.** 증상은 사라지지만 **다음에 누가 묶음을
   고르고 인스턴스가 멈추는 순간 똑같이 돌아온다.** 그리고 그때는 아무도 안 본다.
2. 🔴🔴 **`B_STARTABLE` 에 `requested` 를 그냥 더한다.** 「선택됨」은 고쳐지지만
   **정말 기동 중일 때도 버튼이 열려** 중복 요청이 된다. 두 사실이 한 값에 실려 있는 한
   어느 쪽으로 고쳐도 한쪽이 깨진다.
3. 🔴 **람다만 고치고 론처를 안 고친다(또는 그 반대).** 배포 경로가 다르다 —
   람다는 `terraform apply`(승인), 론처는 머지(분 단위). **둘 사이에 창이 열린다.**
4. 🔴 **`▶ 전체 스택 시작` 을 건드린다.** 지금 유일하게 작동하는 길이다.
5. **`TASK-MONO-647` 과 섞는다.** 647 은 «눌렀을 때 무엇이 뜨나», 이것은 «누를 수 있나» 다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus** — 상태값을 가르는 일이고, `B_STARTABLE` 에 한 줄 더하는
것이 **틀린 답**인 부류다(Failure 2). 가드도 실행 비교라 설계가 필요하다.

---

# ✅ CORRECTION (2026-09-10 UTC 04:07) — AC-0 ③ 을 **실제 브라우저로** 닫았다

`review/` 로 옮길 때 이 칸은 ⚪ 였고, 사유는 *"그 표는 브라우저가 필요하고 이 세션은 페이지를
안 열었다"* 였다. 🔴 **그것은 정당한 닫음이 아니었다** — 그 AC 는 ⚪ 대체를 허용하지 않았고
*"페이지 전체에서 다시 세라"* 라고만 적었다. `done/` 은 frozen 이므로 그 상태로 옮기면 이 칸은
**영영 안 읽힌다.** 그래서 닫기 전에 쟀다.

## 어떻게 쟀나

`hubwang.com` 을 **실제 chromium** 으로 열고(Playwright), 배지가 «… 확인 중» 을 벗어날 때까지
기다린 뒤 **페이지의 모든 `<button>`** 을 라벨·`disabled`·가시성과 함께 셌다.
🔴 **아무것도 클릭하지 않았다** — 클릭은 EC2 를 켜고 요금이 걸린다.

같은 시각의 재현 조건(별도 curl 로 확인):

```
UTC 2026-09-10T04:07:29
GET /status   {"state":"stopped","ip":null,"used_minutes":516,"budget_minutes":600}
GET /bundles  instance=stopped · 선택 9개 · 상태분포 {"requested": 9}   ← 옛 람다 그대로
```

브라우저가 실제로 부른 것: `200 /status  |  200 /bundles`.

## 페이지 전체 버튼 표 — **보이는 21개** (전체 `<button>` 31개)

| id/class | 묶음 | 라벨 | 버튼 |
|---|---|---|---|
| `.bstart` | console | 실시간 기능 시작 | 🟢 **활성** |
| `.bstart` | store | 실시간 기능 시작 | 🟢 **활성** |
| `.bstart` | fan | 실시간 기능 시작 | 🟢 **활성** |
| `#start` | — | ▶ 전체 스택 시작 | 🟢 활성 |
| `#stop` | — | ■ 데모 종료 (EC2 정지) | 🔴 비활성 |
| `.shots-nav ‹` × 3 | 카드 3장 | ‹ | 🔴 비활성 (첫 장이라 옳다) |
| `.shots-nav ›` × 3 · `.shots-dot` × 8 · `.copy` × 2 | | | 🟢 활성 |

**활성 17 / 비활성 4.**

## 기안 ④ 표와 나란히

| | 기안 (2026-09-09, 결함 상태) | 지금 |
|---|---|---|
| `.bstart` × 3 | 🔴 **전부 비활성**, 라벨 「기동 중…」 | 🟢 **전부 활성**, 라벨 「실시간 기능 시작」 |
| `#start` | 🟢 활성 | 🟢 활성 — **안 바뀌었다** |
| `#stop` | 🔴 비활성 | 🔴 비활성 — 멈춰 있으니 옳다 |

🔵 **AC-0 ③ 이 경고한 그것을 실제로 피했다**: 카드 3장만 봤으면 `#start` 가 살아 있다는 것을
못 보고 「방문자가 아무것도 못 켠다」는 **틀린 진술**을 했을 것이다. 페이지 전체를 세니 그
버튼은 **처음부터 활성이었고 지금도 활성**이다 — AC-2 의 *"`▶ 전체 스택 시작` 을 바꾸지 마라"*
가 지켜졌다는 증거이기도 하다.

## 🔵 이 측정이 AC-4 의 ⚪ 도 덮는다

`review/` 판에서 AC-4 는 *"브라우저가 아니라 서빙된 JS 를 node 스텁 DOM 으로 실행한 것이므로
증명한 것은 `btn.disabled === false` 까지"* 라고 한계를 적었다. **이번 것은 실제 브라우저다** —
`disabled` 와 **라벨**과 **가시성**이 전부 실물이다. 그 ⚪ 는 여기서 닫힌다.

⚪ 여전히 안 잰 것 하나: **클릭했을 때 무엇이 뜨는가.** 그 축은 이 티켓이 아니라
`TASK-MONO-647`(묶음 기동이 전체를 켠다)이고, 재려면 EC2 를 켜야 한다.

## 🔴 남은 의무는 이 티켓이 안 들고 간다 — `TASK-MONO-656`

`terraform apply`(= `/bundles` 가 스스로 `selected` 를 주게 하는 것)는 소유자 승인 사항이라
AC-4 가 *"plan 까지 내고 STOP"* 으로 **스코프 밖에 뒀다.** 🔴 그러나 그 의무를 `done/` 과 함께
얼리면 저장소 어디에도 안 남는다 ⇒ **`TASK-MONO-656` 이 넘겨받았다**(같은 이유로 `645` 가
`635`·`638` 의 항목을 넘겨받은 선례).
