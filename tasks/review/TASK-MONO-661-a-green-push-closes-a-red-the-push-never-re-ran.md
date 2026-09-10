# Task ID

TASK-MONO-661

# Title

🔴🔴 nightly 빨강을 **그 빨강을 다시 돌리지도 않은 초록**이 닫는다 — 감시자가 존재하는 이유를 감시자가 지운다

# Status

review

# Owner

monorepo

# Task Tags

- ci
- code
- test

---

# Goal

`TASK-MONO-655` 가 만든 nightly 자동 감시자(`A red nightly arrives (issue, not just a
badge)`)가 **아직 살아 있는 빨강을 닫는다.** 닫는 술어가 *"나중 런이 success 인가"* 인데,
그 나중 런에서 **빨갰던 잡이 아예 안 돌았을 수 있다.**

## 실측 (2026-09-10 UTC)

이슈 [#3724](https://github.com/kanggle/monorepo-lab/issues/3724) 의 자기 기록:

```
빨간 잡: ami-generation-watch, fan-surface-watch
  런 34403347798  (schedule, 2026-09-09)
…
🟢 다시 초록이다 — 1bfc0c5a9…  (push)
  런 34436788683
```

🔴 그런데 그 두 잡은 `nightly-e2e.yml` 에서 **`if: github.event_name != 'push'`** 다.
⇒ **push 런에서 항상 `skipped`** 이고, GitHub 은 `skipped` 를 실패로 안 친다
⇒ 워크플로 결론은 `success` ⇒ 감시자가 *"다시 초록"* 으로 읽고 이슈를 **닫았다.**

**그 빨강은 아직 빨갛다.** close chore 가 같은 날 `check-fan-guard-live.sh` 를
`https://fan.hubwang.com` 에 대고 로컬에서 돌렸고 **rc=1** 이었다(§ 근거).

## 🔴 655 는 이 축을 «안 다뤘다», 그리고 `done/` 이라 고칠 수 없다

`tasks/done/TASK-MONO-655-…md` 는 **탐지** 축에서 skipped 를 정확히 처리했다 —
그 파일의 표가 *"🔵 **대조군** `skipped` | 빨강 **아님** (watch 잡들은 push 런에서 정상
skip)"* 이라고 적어 뒀고 **그것은 옳다**(새 빨강을 찾을 때 skipped 를 빨강으로 세면
매 push 마다 거짓 이슈가 열린다).

🔴🔴 **틀린 것은 그 판단이 아니라, 같은 술어를 «닫을 때» 도 썼다는 것이다.**
탐지와 종료는 **서로 다른 질문**이다:

| | 질문 | skipped 를 어떻게 봐야 하나 |
|---|---|---|
| 탐지 | *"이 런에 새 빨강이 있나"* | **빨강 아님** (655 가 맞다) |
| 종료 | *"아까 그 빨강이 회수됐나"* | 🔴 **«안 잼» 이지 «초록» 이 아니다** |

⇒ 종료 술어는 *"나중 런이 success"* 가 아니라
**"빨갰던 잡들이 그 뒤 실제로 돌았고 전부 success"** 여야 한다.

🔵 이것은 이 저장소가 이미 이름 붙인 함정의 **이벤트 필터 판**이다:
*"main tip 초록 ≠ 그 사이 빨강이 회수됨 — 경로필터 잡은 다음 머지가 그 경로를 안
건드리면 skipped"*. 여기서는 경로가 아니라 **이벤트**로 갈리고, 대가가 더 크다 —
**감시자 자신이** 그 빨강을 지운다.

---

# Scope

## In Scope

- `.github/workflows/nightly-e2e.yml` 의 감시 잡 — **종료 술어**를 고친다.
- 그 술어의 회귀 테스트(빨갰던 잡이 skipped 인 런으로는 안 닫힌다).

## Out of Scope

- **`fan-surface-watch` 빨강 자체의 수리** — 별건이고 **거짓 빨강**으로 판정됐다
  (`TASK-FAN-FE-021`). 🔴 이 티켓에서 같이 고치면 「감시자를 고쳤다」와 「빨강을 껐다」가
  한 PR 에 섞이고, 그러면 **감시자 수정이 실제로 무는지** 증명할 대상이 사라진다.
- 🔴 **`ami-generation-watch` 빨강의 원인** — **안 쟀다**(§ 안 잰 것). 이 티켓은 그것을
  진단하지 않는다. 다만 그 빨강도 같은 구멍의 피해자이므로 종료 술어가 고쳐지면
  **다시 보이게 된다**, 그것이 이 티켓의 성공 조건이다.
- 감시 잡을 push 런에서도 돌리기 — 🔴 **오답이다.** 그 잡들이 push 를 피하는 데는 이유가
  있다(머지 직후엔 Vercel 배포가 안 끝나 **거짓 빨강**이 난다). 고칠 것은 **언제 도느냐**가
  아니라 **무엇을 초록으로 읽느냐**다.

---

# Acceptance Criteria

- [x] **AC-0 (실측 고정)** — 착수 시점에 다시 재서, *"빨갰던 잡이 skipped 인 런이
      이슈를 닫았다"* 는 사례를 **이슈 코멘트 기록으로** 하나 이상 제시한다.
      🔵 #3724 가 이미 그 형태이지만, 착수 시점 기록으로 다시 확인하라
      (그 사이 감시자가 바뀌었을 수 있다).
- [x] **AC-1** — 종료 술어가 **«빨갰던 잡이 그 뒤 실제로 돌았는가»** 를 본다.
      🔴 `conclusion == 'success'` 만 보지 마라 — 잡별 `conclusion` 을 읽어
      **`skipped` 를 «미측정»으로** 분류해야 한다.
- [x] **AC-2 (bite)** — 빨갰던 잡이 `skipped` 인 런을 먹이면 **안 닫는다**.
      🔴 이것이 본체다. AC-1 만으로는 「술어를 바꿨다」는 알아도 「이제 안 닫는다」는 모른다.
- [x] **AC-3 (대조군)** — 빨갰던 잡이 **실제로 돌아서 success** 인 런을 먹이면 **닫는다.**
      🔴 이 칸이 없으면 「구멍을 막았다」와 「영영 안 닫게 만들었다」가 같은 초록이다 —
      후자면 이슈가 **영구히 열려** 있고, 영구히 열린 경보는 꺼진 경보와 같다.
- [x] **AC-4** — 탐지 축은 **안 바뀐다**. 655 의 대조군(push 런의 skipped 는 빨강 아님)이
      **그대로 초록**이다. 🔴 종료를 고치면서 탐지를 깨면 매 push 마다 거짓 이슈가 열린다.
- [x] **AC-5** — 게이트가 **각각 독립 statement + 명시 `rc=$?`**. 감시자에 테스트가 있으면
      그것, 없으면 이 티켓이 만든다. 🔵 판정은 rc 가 아니라 **몇 개가 돌았나**.

---

# Related Specs

- `.github/workflows/nightly-e2e.yml` — 감시 잡 + 세 watch 잡의 `if:` 조건
- `tasks/done/TASK-MONO-655-nightly-e2e-has-been-red-for-three-days-so-it-guards-nothing.md`
  (frozen — **이 티켓이 그 설계의 빈 축을 잇는다**)
- `docs/guides/monorepo-workflow.md`

# Related Contracts

- 없음.

---

# Implementation Notes

- 🔵 **감시자가 이미 잡 «이름» 을 알고 있다** — 이슈 본문이 `빨간 잡: a, b` 로 적고
  코멘트가 `빨간 잡이 바뀌었다: … → …` 로 추적한다. 즉 «무엇이 빨갰나» 는 이미 상태로
  들고 있으므로, 종료 시 **그 이름들의 잡 conclusion 을 조회**하면 된다.
- 🔴 **이슈를 영구히 열어 두는 쪽으로 도망가지 마라.** AC-3 이 그것을 막는다.
- 🔵 `schedule` 런은 하루 1회다. 종료가 **cron 을 기다려야** 한다는 뜻이므로, 이슈가
  최대 하루 더 열려 있는 것은 **정상**이고 그것을 결함으로 읽지 마라.

---

# Edge Cases

- 빨갰던 잡이 **이름이 바뀌었다** → 조회가 못 찾는다. 🔴 그때는 **닫지 마라**(미측정).
- 런이 `cancelled` → 미측정이다. 초록으로 읽지 마라.
- 빨간 잡이 여럿이고 그중 일부만 회수됐다 → **안 닫는다**(부분 회수는 회수가 아니다).

---

# Failure Scenarios

- **`skipped` 를 초록으로 읽는다** → 지금 결함 그대로.
- **`skipped` 를 빨강으로 읽는다** → 매 push 마다 거짓 이슈(AC-4 가 막는다).
- **안 닫히게만 만든다** → 영구 열린 경보 = 꺼진 경보(AC-3 이 막는다).

---

# Test Requirements

- 종료 술어 단위 테스트: skipped 런 → 안 닫음 / 실제 success 런 → 닫음
- 탐지 축 회귀(655 의 대조군)

---

# 🔴 안 잰 것

- **`ami-generation-watch` 빨강의 원인.** 로그가 셋업 노이즈에 묻혀 판정 못 했다.
  이 티켓은 그것을 **진단하지 않는다** — 종료 술어가 고쳐지면 그 빨강이 다시 보이게
  되고, 그때 별도로 진단하면 된다.
- **다른 워크플로에 같은 감시자가 있는지** — 안 셌다.

---

# 분석 / 구현 권장

분석=**Opus 5** / 구현 권장=**Opus** (술어의 «탐지 ↔ 종료» 분리가 이 티켓의 전부이고,
AC-3 의 대조군을 빠뜨리면 경보를 꺼 버린다)

---

# 구현 기록 (ready → review, 2026-09-10 UTC)

## 무엇을 바꿨나

| 파일 | 내용 |
|---|---|
| `scripts/nightly-close-predicate.mjs` | **신규** — 종료 술어 + `--self-test` 7칸 + 양방향 대조 |
| `.github/workflows/nightly-e2e.yml` | 닫기 전에 그 술어를 부른다 · 종료 self-test 스텝 추가 |
| `CLAUDE.md` · `platform/git-workflow-policy.md` | `scripts/` 분모 **55 → 56** (아래 § 분모) |

## § 왜 인라인이 아니라 `scripts/` 인가

옛 판의 종료 로직은 워크플로 인라인 bash 였다. 거기 두면 **로컬 검증본이 «사본»** 이
된다 — `TASK-PC-FE-279` 가 방금 고친 그 결함이다. 🔵 그리고 이 호스트에는 외부 `jq` 가
없어서 bash+jq 판은 **로컬에서 self-test 를 못 돌린다** ⇒ `.mjs`(선례 6건).

## § AC 판정

| AC | 판정 | 근거 |
|---|---|---|
| AC-0 | ✅ | 이슈 #3724 의 자기 기록 재확인 (§ 아래) |
| AC-1 | ✅ | 술어가 `prev` 의 잡별 `result` 를 읽고 `skipped`/부재를 **«안 잼»** 으로 분류 |
| AC-2 | ✅ | self-test (2) + **실제 #3724 입력 재현** → `hold` |
| AC-3 | ✅ | self-test (5)(7) + **실제 대조군 입력** → `close` |
| AC-4 | ✅ | 탐지 축 diff **0줄** (§ 아래) |
| AC-5 | ✅ | § 게이트 |

## § AC-0 — 실측 고정

이슈 [#3724](https://github.com/kanggle/monorepo-lab/issues/3724) 본문·코멘트:

```
빨간 잡: ami-generation-watch, fan-surface-watch   (런 34403347798, schedule)
…
🟢 다시 초록이다 — 1bfc0c5a9… (push)               (런 34436788683)
```

그 두 잡은 `if: github.event_name != 'push'` ⇒ 그 push 런에서 **돌지 않았다**.

## § AC-2 / AC-3 — bite 와 대조군, **실제 입력으로**

```
# AC-2 — 실제 #3724 시나리오
--needs '{"ami-generation-watch":{"result":"skipped"},
          "fan-surface-watch":{"result":"skipped"},
          "platform-console-e2e-fullstack":{"result":"success"}}'
--prev  'ami-generation-watch, fan-surface-watch'
→ hold
  ✖ ami-generation-watch — **skipped** ⇒ «초록» 이 아니라 «안 잼»
  ✖ fan-surface-watch    — **skipped** ⇒ «초록» 이 아니라 «안 잼»

# AC-3 — 같은 둘이 실제로 돌아 success 인 cron 런
→ close
  ✔ ami-generation-watch — 이번 런에서 실제로 돌았고 success
  ✔ fan-surface-watch    — 이번 런에서 실제로 돌았고 success
```

🔴🔴 **AC-3 이 없었으면 «영구 hold» 를 «고쳤다» 로 착각할 수 있었다.** 그래서 술어는
**`prev` 에 있는 잡만** 본다 — self-test (5)가 그 대조군이다(`prev` 밖의 잡이 skip 된
것은 붙잡지 않는다). self-test 는 *"close 가 최소 한 번, hold 가 최소 한 번 나온다"* 도
따로 단언한다 — 전부 hold 를 내는 술어는 판정기가 아니라 상수다.

## § AC-4 — 탐지 축은 손대지 않았다

```
git diff .github/workflows/nightly-e2e.yml | grep -E "^[-+].*(red=|self-test \(4\)|to_entries)"
→ (없음)
```

⇒ `red` 계산과 655 의 self-test 4칸(그중 (4) = *"skipped 는 빨강이 아니다"*)은 **한 글자도
안 바뀌었다.** 종료를 고치면서 탐지를 깨면 매 push 마다 거짓 이슈가 열린다.

## § 게이트

```
node scripts/nightly-close-predicate.mjs --self-test    rc=0   7칸 + 양방향 대조
python -c "yaml.safe_load(...)"                          rc=0   잡 15개, 새 스텝 자리 확인
bash scripts/check-ls-files-guard-count.sh               rc=0   20/56, 2 homes 합의
```

## § 분모 — `scripts/` 에 파일을 더하면 다른 가드의 **입력**이 바뀐다

`check-ls-files-guard-count.sh` 의 `TOTAL` 은 `find scripts -maxdepth 1 -type f` 다.
파일 하나를 더했으므로 **55 → 56** 이고, 산문 홈 **2곳**(`CLAUDE.md` · 
`platform/git-workflow-policy.md`)을 같이 고쳤다. `READERS` 는 `ls-files` 문자열 기준이라
**20 유지**(이 스크립트는 그 문자열을 안 쓴다).
🔵 `TASK-MONO-650` 이 정확히 이 자리에서 main 을 빨갛게 만들었다 — **분자가 아니라
분모가 움직인 것**이었다. 그래서 `git status` 에 `scripts/` 추가가 보이면 **가드를 전부**
돌린다(§ 아래 sweep).

## 🔴 안 잰 것

- **`ami-generation-watch` 빨강의 원인.** 이 티켓은 진단하지 않는다(범위 밖).
  🔵 그러나 이 수정의 성공 조건이 곧 그것이다 — 종료 술어가 고쳐졌으므로 그 빨강은
  이제 **다음 cron 에서 다시 보이게 된다.**
- **라이브에서 이 술어가 실제로 이슈를 붙잡는 것은 못 봤다.** 그러려면 빨간 이슈가
  열려 있는 상태에서 push 런이 돌아야 한다. 지금은 열린 이슈가 **0건**이다.
  ⇒ 다음에 nightly 가 빨개진 뒤 push 가 나면 그때 확인된다.
- **다른 워크플로에 같은 감시자가 있는지** — 안 셌다(661 § 안 잰 것 그대로).
