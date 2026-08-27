# Task ID

TASK-MONO-594

# Title

🔴 **수동 Redeploy 가 빌드되지 않는다** — `usable_base()` 가 «HEAD 자신» 을 기준점으로
받아들여 판정 창이 **0 커밋**이 된다. 🔵 **바로 아래 갈래에는 그 가드가 이미 있다.**

# Status

review

# Owner

monorepo

# Task Tags

- vercel
- ci
- scripts
- adr-mono-067

---

# Goal

`scripts/vercel-should-build.sh` 의 기준점 선택이 `VERCEL_GIT_PREVIOUS_SHA == HEAD` 인
경우를 걸러내지 못한다. 그러면 `BASE..HEAD` 가 **빈 범위**라 `git diff --quiet` 가 항상
0(=차이 없음)을 내고, Vercel 규약상 그것은 **건너뜀**이다. 즉 그 배포는 **무엇이 바뀌었든
반드시 취소된다.**

🔴 고칠 것은 «무시 규칙이 너무 넓다» 가 아니라 **판정 창이 비었는데도 자신 있게 답을 냈다**
는 것이다. `TASK-MONO-572` 가 고친 것과 **같은 클래스**이고(창의 크기), 파일 헤더가
*"고장은 반드시 「더 굽는」 쪽으로 나야 한다"* 고 적어 둔 방향의 **반대**로 고장 나 있다.

---

# Context — 실측 (2026-08-27 UTC)

## ① 라이브 증거 — 취소된 배포의 빌드 로그가 원인을 그대로 적는다

`vercel inspect https://kanggle-ez5e07ur9-khakiman-projects.vercel.app --logs`
(deployment `dpl_9AZiTx4H6a7mtR7wEChGvm1Uw9Bq`, `kanggle-fan`, Production):

```
2026-08-27T10:08:40.411Z  Cloning github.com/kanggle/monorepo-lab (Branch: main, Commit: 8e43a4d)
2026-08-27T10:08:40.412Z  Skipping build cache, deployment was triggered without cache.
2026-08-27T10:08:46.882Z  Running "bash "$(git rev-parse --show-toplevel)/…/vercel-ignore.sh""
2026-08-27T10:08:46.970Z  [vercel-ignore] · 판정 창 = VERCEL_GIT_PREVIOUS_SHA (직전 배포) · 0 커밋
2026-08-27T10:08:46.971Z  [vercel-ignore] ↷ 건너뜀 — 8e43a4db98857be8f036cdb18fdde90bc8c614cf..HEAD 에
                          다음 경로의 변경이 없습니다: :/projects/fan-platform/web …
2026-08-27T10:08:46.973Z  The Deployment has been canceled as a result of running the command
                          defined in the "Ignored Build Step" setting.
status  ● Canceled
```

🔵 **로그가 스스로 «0 커밋» 이라고 말한다.** `VERCEL_GIT_PREVIOUS_SHA` = `8e43a4db9…` 이고
클론된 커밋도 `8e43a4d` — **같은 커밋**이다.

🔵 **이것이 수동 Redeploy 라는 근거 둘**: ⑴ `Skipping build cache, deployment was triggered
without cache` 는 대시보드 Redeploy 의 지문이다. ⑵ 그 시각 `main` 의 tip 은 `8e43a4d` 가
아니라 훨씬 앞이었다 — **push 로 생긴 배포라면 tip 을 클론했을 것**이다.

## ② 기전을 이 저장소에서 재현했다 (추론이 아니라 실행)

```
$ H=$(git rev-parse HEAD)
$ git merge-base --is-ancestor "$H" HEAD ; echo $?
0                                    ← 🔴 커밋은 자기 자신의 조상이다
$ git cat-file -e "${H}^{commit}" ; echo $?
0                                    ← 객체도 실재한다
$ git diff --quiet "$H" HEAD -- ':/projects/fan-platform/web' ; echo $?
0                                    ← 빈 범위 ⇒ 차이 없음 ⇒ **건너뜀**
```

`usable_base()` 가 검사하는 것은 **세 가지뿐**이다 — (a) 비었나 (b) 객체가 있나
(c) HEAD 의 **조상**인가. `cand == HEAD` 는 **셋 다 통과한다.** (c) 는 force-push 를 막으려고
넣은 것인데, 자기 자신은 조상이므로 이 경우를 못 거른다.

## ③ 🔴🔴 비대칭 — 같은 사실이 두 갈래에 있고, 가드는 **한쪽에만** 있다

`scripts/vercel-should-build.sh` 의 기준점 선택은 세 후보를 순서대로 본다. 두 번째 갈래에는
이 가드가 **이미 있다**:

```bash
elif [ "$MB" = "$(git rev-parse HEAD 2>/dev/null)" ]; then
  log "· merge-base 가 HEAD 자신입니다 (브랜치가 기본과 같음) — HEAD^ 로 판정합니다."
```

그리고 그 위 주석이 **이유까지 정확히 적어 뒀다**:

> 🔵 2번은 production 브랜치에서는 쓰면 안 된다. 거기서 merge-base 는 HEAD 자신이라
> **창이 비고 모든 것이 건너뛰어진다** — 정확히 반대 방향의 고장이다.

🔴 **첫 번째 갈래(`VERCEL_GIT_PREVIOUS_SHA`)는 `usable_base()` 를 쓰고, 거기엔 그 검사가
없다.** 저자는 «창이 비면 전부 건너뛴다» 를 **알고 있었고 한 곳에만 적용했다.**
[[feedback_one_fact_in_two_sections_only_one_gets_fixed]]
[[feedback_the_unguarded_operation_is_where_the_invariant_breaks]]

## ④ 가드의 모집단이 **N-1** 이었다

`scripts/check-vercel-build-triggers.sh` 에는 `PREVIOUS_SHA` 축의 칸이 셋 있다:

| 칸 | 무엇을 넣나 | 기대 |
|---|---|---|
| (6) | 유효한 PREVIOUS_SHA, [자기경로, 무관] 배치 | 빌드 |
| (6b) | PREVIOUS_SHA **없음** | 건너뜀(옛 동작) |
| (8) | **존재하지 않는** PREVIOUS_SHA | HEAD^ 로 폴백 |
| — | 🔴 **PREVIOUS_SHA == HEAD** | **칸이 없다** |

«나쁜 PREVIOUS_SHA» 의 모집단을 «없다 / 가짜다» 둘로 봤고, **«문법적으로 완벽한데 창이
비는 값»** 이라는 셋째를 못 봤다. [[feedback_why_a_guard_does_not_bite]]
[[feedback_recount_population_dont_inherit_scope]]

## ⑤ 🔴🔴 왜 지금 중요한가 — **다른 티켓의 처방이 정확히 이것에 막힌다**

`TASK-FAN-FE-018` 이 `fan.hubwang.com` 에서 실측한 것: `/api/auth/providers` ·
`/api/auth/session` · `/api/auth/csrf` 가 전부 **500** `There was a problem with the server
configuration` ⇒ 진단은 *"`AUTH_SECRET`/`NEXTAUTH_SECRET` 계열 env 가 Vercel 프로젝트에 안
들어가 있다"*. 그 처방은 **소유자가 대시보드에서 env 를 넣는 것**이다.

🔴 **Vercel 의 env 변경은 새 배포에서만 반영된다.** 소유자가 취할 자연스러운 다음 행동은
**Settings → Redeploy** 이고, 그 배포는 위 ①의 기전으로 **7초 만에 Canceled** 가 된다.

⇒ 관측되는 것: *"env 를 넣고 재배포했는데 그대로다."* **틀린 결론이 남의 티켓에 기록된다.**
같은 함정이 `TASK-MONO-586`(fan 게이트웨이 env) · `ADR-MONO-067` 단계 3·4 의 env 항목
전부에 걸린다. [[feedback_if_the_symptom_survives_the_fix_it_was_not_the_cause]]

## ⑥ 🔵 과대주장하지 않는다 — 이 티켓이 **모르는** 것

- **기전은 확정**(②에서 실행으로 증명), **라이브 관측은 1건**(①). *"Vercel 이 모든 수동
  Redeploy 에서 `VERCEL_GIT_PREVIOUS_SHA` 를 HEAD 와 같게 준다"* 는 **관측 1건의 일반화**다
  ⇒ AC-0 이 둘째 관측을 만든다.
- 🔴 **`vercel ls` 의 7초 Canceled 다수는 이 결함이 아니다.** 최근 `main` 커밋
  (`docs(tasks)` · `fix(rules)` 등)은 `projects/fan-platform/web/**` 를 안 건드리므로
  건너뛰는 것이 **정상 동작**이다. `TASK-MONO-590` §③ 이 잰 «73건이 아무것도 안 굽는다» 는
  그대로 유효하고, 이 티켓은 **그 73건의 설명이 아니다.**
  [[feedback_a_verifiable_mechanism_is_not_the_cause]]

---

# Scope

**In:**

- `scripts/vercel-should-build.sh` — `usable_base()` 의 기준점 검사
- `scripts/check-vercel-build-triggers.sh` — 새 칸 + `--self-test` 주입 칸

**Out:**

- 🔴 **판정을 배포 «생성 전» 으로 옮기는 설계** → `TASK-MONO-590`. **다른 축이다** —
  590 은 *배포가 만들어지는 것 자체*를 줄이고, 이 티켓은 *만들어진 배포의 판정이 틀린 것*을
  고친다. 🔵 **590 이 랜딩해도 이 결함은 남는다**: 590 AC-3 이 훅 없는 프로젝트에서
  `ignoreCommand` 를 살려 두기로 했고, **수동 Redeploy 는 훅 경로를 안 지나간다.**
- fan 의 Vercel env 설정 → 소유자 (`TASK-FAN-FE-018`)
- `fan.hubwang.com` 의 500 자체 → `TASK-FAN-FE-018`
- 이미지 할당량 → `TASK-MONO-587`

---

# Acceptance Criteria

## AC-0 — 착수 시 **다시 잰다**, 그리고 관측을 **둘로** 만든다

1. 🔴 **오늘 값을 상속하지 마라.** 그 사이 다른 티켓이 이 파일을 고쳤을 수 있다 —
   `usable_base()` 를 **읽고** 시작한다.
2. **둘째 관측을 만든다**: 임의의 배포 하나를 대시보드에서 Redeploy 하고
   `vercel inspect <url> --logs` 로 **`· 판정 창 = … · 0 커밋`** 줄이 다시 나오는지 본다.
   🔵 **양성 대조군**: 같은 프로젝트에 fan 경로를 건드리는 push 를 하나 넣어 그 배포는
   `▶ 빌드` 로 나오는지 확인한다 — 로그 술어 자체가 작동한다는 증거.
   🔴 관측이 **재현되지 않으면** ①은 일회성이었다는 뜻이다. 그래도 ②의 기전은 참이므로
   AC-2 는 유효하지만, **티켓의 서술을 그 사실로 고쳐 적는다.**

## AC-1 — 🔴 **빈 창을 무엇으로 번역할지 정하고, 근거를 적는다**

두 갈래가 있고 **결과가 다르다.** 고르고, 왜 골랐는지를 코드 옆에 남긴다.

| 선택지 | 동작 | 근거 / 대가 |
|---|---|---|
| **(A) `exit 1` — 빌드** ⭐ | 빈 창 = **판정 불가** ⇒ 파일의 명시 규약 *"판정 불가는 빌드다"* 를 그대로 적용 | 🔵 수동 Redeploy 는 **사람이 명시적으로 빌드를 요청한 행위**다. fail-open 방향과도 일치. 대가: Redeploy 1건이 항상 슬롯을 쓴다(사람이 누른 것이므로 의도된 비용) |
| **(B) 다음 후보로 내려가 `HEAD^`** | 두 번째 갈래(`MB == HEAD`)가 하는 것과 같은 처리 | 🔵 일관성. 🔴 **그러나 «env 를 반영하려는 Redeploy» 는 여전히 안 굽는다** — 그 커밋이 앱 경로를 안 건드렸으면 `HEAD^..HEAD` 도 «건너뜀» 이다 ⇒ ⑤의 함정이 **안 고쳐진다** |

🔴 **(B) 를 고르면 ⑤ 를 «해결» 로 적지 마라.** 그때는 «Redeploy 로는 env 를 반영할 수
없다» 를 `VERCEL.md` 에 **함정으로 명시**하는 것이 이 티켓의 산출물이 된다.
[[feedback_a_partial_deletion_reads_as_a_total_one]]

🔵 이 티켓의 권고는 **(A)** 다 — 파일이 이미 자기 규약을 적어 뒀고, 이 경우가 정확히 그
규약이 다루려던 «판정할 수 없는 상황» 이기 때문이다.

## AC-2 — 수정

`usable_base()` 에 «후보가 HEAD 자신인가» 검사를 더한다. 🔴 **문자열 비교가 아니라
`git rev-parse` 로 정규화해서 비교한다** — `VERCEL_GIT_PREVIOUS_SHA` 는 40자 full SHA 로
오고 HEAD 는 ref 다. 축약형/ref 표기가 섞이면 같은 커밋인데 다르다고 읽는다.

🔴 **로그 문구를 남긴다.** 이 결함이 3주 동안 안 보인 이유는 로그가 «0 커밋» 이라고
말하는데도 **그것이 이상하다고 아무도 안 읽었기** 때문이다. 새 경로는 무엇을 왜 했는지
한 줄로 적어야 한다.

## AC-3 — 🔴 **칸을 신설하고, 그 칸이 실제로 무는지 bite 로 증명한다**

- `check-vercel-build-triggers.sh` 에 칸 **(13)** 추가: `CELL_PREV_SHA` 를 **HEAD 와 같게**
  세우고 **자기 경로를 건드린 커밋**을 넣는다 → 기대 `rc=1`(빌드).
  🔵 **자기 경로를 건드린 커밋**이어야 한다 — 무관한 커밋으로 만들면 (A)든 (B)든 통과해서
  **무엇을 증명했는지 알 수 없다.**
- 🔴 **bite**: 수정을 되돌린 사본으로 그 칸을 돌려 **`rc=0` 으로 실패하는 것**을 확인한다.
  주입이 실제로 착지했는지부터 단언한다(`CELL_PREV_SHA` 가 로그의 «판정 창» 줄에 나타나는가).
  [[feedback_assert_the_injection_before_reading_the_bite]]
- `--self-test` 칸 수를 갱신하고, **몇 칸인지 티켓에 적는다**(숫자 없는 «통과» 금지).

## AC-4 — 🔴 `TASK-MONO-590` 과의 관계를 **한 줄로 못박는다**

590 이 이 파일을 «거의 죽은 기전» 으로 만들 계획이므로, 이 수정이 왜 그 뒤에도 필요한지를
`vercel-should-build.sh` 헤더에 적는다: **수동 Redeploy 는 Deploy Hook 경로를 지나가지
않는다.** 🔴 안 적으면 590 착수자가 이 칸을 «죽은 기전의 테스트» 로 보고 지운다.
[[feedback_a_pin_can_freeze_the_defect_it_was_written_to_guard]]

## AC-5 — 검증

- `bash -n` (수정한 스크립트 전부)
- `bash scripts/check-vercel-build-triggers.sh` rc=0
- `bash scripts/check-vercel-build-triggers.sh --self-test` — **칸 수를 적는다**
- 🔴 **CI 가 권위다.** 이 가드가 어느 워크플로 잡에서 도는지 **확인하고 적는다** — 러너
  없는 스위트는 썩는다. [[feedback_two_correct_exclusions_compose_into_a_hole]]

---

# Related Specs

- `scripts/vercel-should-build.sh` — 결함 위치 (`usable_base()`)
- `scripts/check-vercel-build-triggers.sh` — 칸 (6)(6b)(8) 이 사는 곳, (13) 이 갈 곳
- `projects/fan-platform/web/fan-platform-web/VERCEL.md` — AC-1 (B) 를 고르면 함정 기록처
- `tasks/ready/TASK-MONO-590-stop-paying-quota-for-deployments-that-build-nothing.md` — 다른 축
- `projects/fan-platform/tasks/ready/TASK-FAN-FE-018-the-route-guard-does-not-run-in-production.md` — ⑤ 의 피해자
- `tasks/done/TASK-MONO-572-vercel-ignore-window-is-one-commit-wide.md` — **같은 클래스의 선례**
- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| `VERCEL_GIT_PREVIOUS_SHA` 가 **축약형**으로 온다 | AC-2 — `git rev-parse` 로 정규화 후 비교. 문자열 비교는 같은 커밋을 다르다고 읽는다 |
| 최초 커밋의 Redeploy (부모가 없다) | 이미 위쪽 `HEAD^` 존재 검사에서 `exit 1`(빌드)로 빠진다 — 이 수정보다 앞이다 |
| (A) 를 골라 Redeploy 가 항상 빌드된다 | 🔵 의도된 비용. 다만 **자동 재시도**가 Redeploy 를 반복하면 슬롯을 먹는다 ⇒ AC-0 에서 «누가 Redeploy 를 누르는가» 가 사람뿐인지 확인 |
| 590 이 먼저 랜딩한다 | 🔵 충돌 없음 — 590 은 워크플로/훅 축, 이 티켓은 스크립트 내부. AC-4 가 그 사실을 파일에 남긴다 |
| 다른 세션이 같은 파일을 고치고 있다 | 🔴 `TASK-MONO-590` 도 이 스크립트를 **호출**만 하고 수정하지는 않는다. 그래도 착수 전 `git log --all -- scripts/vercel-should-build.sh` 로 확인 |
| 얕은 clone 이라 `HEAD^` 가 없다 | 기존 fail-open 경로 유지 — 이 수정이 그 순서를 바꾸면 안 된다 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방어 |
|---|---|---|
| «칸 (8) 이 이미 있으니 커버된다» 로 닫는다 | 가짜 SHA 는 걸러지고 **HEAD 와 같은 SHA** 는 안 걸러진 채로 초록 | ④ 표 + AC-3 의 새 칸 |
| 새 칸을 **무관한 커밋**으로 만든다 | (A)(B) 어느 쪽이든 통과 ⇒ 무엇을 증명했는지 모름 | AC-3 — 자기 경로 커밋 |
| bite 없이 «칸을 넣었다» 로 끝낸다 | 칸이 죽은 채 초록 (이 저장소가 칸 (12)에서 이미 겪었다) | AC-3 — 되돌린 사본으로 rc=0 확인 + 주입 단언 |
| (B) 를 고르고 ⑤ 를 «고쳤다» 로 적는다 | 소유자가 여전히 env 반영에 실패하고, 티켓엔 «해결» | AC-1 — (B) 는 `VERCEL.md` 함정 기록이 산출물 |
| 라이브 관측 1건을 «항상 그렇다» 로 적는다 | 재현 안 되면 티켓 서술 전체가 의심받는다 | ⑥ + AC-0 2 |
| `vercel ls` 의 7초 Canceled 20건을 전부 이 결함으로 귀속 | 590 의 실측(73건 정상 동작)과 모순되고, 없는 문제를 고친다 | ⑥ 둘째 항목 |
| 590 착수자가 칸 (13) 을 «죽은 기전» 으로 지운다 | 결함이 조용히 돌아온다 | AC-4 — 헤더에 이유를 박는다 |

---

# ✅ 구현 (2026-08-27 UTC) — **(A) 를 골랐다. 빈 창은 «변경 없음» 이 아니라 판정 불가다.**

## AC-0 ① — `usable_base()` 를 **읽고** 시작했다. 티켓 서술 그대로였다.

`scripts/vercel-should-build.sh:102-114` 의 검사는 **정확히 셋**이었고 `cand == HEAD` 검사는
없었다. 그 사이 다른 티켓이 이 파일을 고치지 않았다는 것도 확인했다 —
`git log --all -- scripts/vercel-should-build.sh` 의 최신 커밋은 `c75ffb06d`(web-store 배선,
`TASK-MONO-582`)이고, 이 파일을 건드리는 **열린 PR 은 없다**.

②의 기전도 이 트리에서 다시 실행했다: `is-ancestor` rc=0 · `cat-file -e` rc=0 ·
빈 범위 `git diff --quiet` rc=0(=«차이 없음»=건너뜀). **셋 다 통과한다**는 서술이 재현됐다.

## AC-0 ② — 🔴 **둘째 «라이브» 관측은 못 만들었다. 정직하게 적는다.**

AC-0 ②는 대시보드에서 Redeploy 를 눌러 `vercel inspect --logs` 로 `· 0 커밋` 줄이 다시
나오는지 보라고 했다. **못 했고, 이유는 둘이며 둘 다 이 세션이 넘을 수 없다:**

1. 이 호스트에 `vercel` CLI 가 없고(`command -v vercel` → 부재), 대시보드는 소유자 접근이다.
2. 🔴🔴 **더 결정적인 것** — 계정이 `Deployments Created per Day` 천장에 걸려 있어
   **지금은 Redeploy 를 눌러도 배포가 「생성」되지 않는다.** 08-27 08:37Z 이후 `main` 의 모든
   커밋이 두 프로젝트 모두에서 `Deployment rate limited — retry in 24 hours` 를 받았다
   (`b6b5d4174` · `7465c8499`(fan) · `4e750c183` · `a7d790815`). 배포가 생성되지 않으면
   `ignoreCommand` 는 **아예 실행되지 않으므로**, 지금 누른 Redeploy 는 «0 커밋» 로그를
   만들지도 못하고 «재현 안 됨» 으로 읽히기만 한다. ⇒ **판정 불가 구간이다.**

🔵 **대신 ②보다 강한 «스크립트 쪽» 재현을 만들었다 — 그러나 그것은 AC-0 ②가 아니다.**
고치기 **전** 판정자(`origin/main:scripts/vercel-should-build.sh`)를 이 트리에서
`VERCEL_GIT_PREVIOUS_SHA=$(git rev-parse HEAD)` 로 직접 돌렸더니 라이브 로그의 두 줄이
**문자 그대로** 나왔다:

```
[vercel-ignore] · 판정 창 = VERCEL_GIT_PREVIOUS_SHA (직전 배포) · 0 커밋
[vercel-ignore] ↷ 건너뜀 — a7d7908155…..HEAD 에 다음 경로의 변경이 없습니다: :/projects/fan-platform/web
rc=0   ← 건너뜀
```

🔴 **이것이 무엇을 증명하고 무엇을 증명하지 않는지 갈라 적는다.** 증명한 것은 *"`PREVIOUS_SHA`
가 HEAD 와 같게 들어오면 이 스크립트는 반드시 건너뛴다"* — **기전의 스크립트 쪽**이다.
증명하지 **않은** 것은 *"Vercel 이 수동 Redeploy 에서 항상 `PREVIOUS_SHA` 를 HEAD 와 같게
준다"* — **Vercel 쪽**이고, 그것은 여전히 **관측 1건**이다. ⑥의 단서는 **그대로 유효하며
이 재현으로 격상되지 않는다.** 티켓 서술을 고쳐 적을 사유(관측이 반증됨)는 발생하지 않았다.

⇒ **AC-0 ②는 남는다.** 한도가 풀린 뒤 소유자가 Redeploy 1회 + `vercel inspect --logs` 로
닫아야 하고, 그때 양성 대조군(팬 경로를 건드리는 push 가 `▶ 빌드` 로 나오는지)도 같이 본다.

## AC-1 — **(A) 를 골랐다**. 근거는 코드 옆에 있다.

`scripts/vercel-should-build.sh` 헤더의 새 절
(*"후보가 HEAD 자신이면 창은 0 커밋이고, 그것은 «변경 없음» 이 아니다"*)에 선택지 둘과
고른 이유를 적었다. 요지:

- **(B) 다음 후보(`HEAD^`)로 폴백**은 아래 merge-base 갈래와의 «일관성» 만 얻고,
  🔴 **env 반영용 Redeploy 는 여전히 안 굽는다** — 그 커밋이 앱 경로를 안 건드렸으면
  `HEAD^..HEAD` 도 «건너뜀» 이기 때문이다. ⑤의 함정이 **안 고쳐진다.**
- **(A) `exit 1`(빌드)** 는 이 파일이 맨 위에 이미 선언해 둔 규약(*"판정할 수 없는 상황은
  전부 빌드 진행"*)을 그대로 적용한다. 수동 Redeploy 는 **사람이 명시적으로 빌드를 요청한
  행위**라 fail-open 방향과도 맞고, 대가(Redeploy 1건이 항상 슬롯을 쓴다)는 사람이 누른
  비용이다.

🔵 **(B) 를 안 골랐으므로 `VERCEL.md` 함정 기록은 산출물이 아니다.** 대신 ⑤의 함정은
**해소되는 쪽**으로 처리됐다 — 다만 그 해소는 «이 수정이 담긴 커밋의 배포» 에만 적용되고,
**이미 배포돼 있는 낡은 커밋을 Redeploy 하면 그 커밋에 담긴 옛 판정자가 돈다.**
`ignoreCommand` 는 **클론된 커밋의 트리에서** 실행되기 때문이다. 이 단서는 아래 §부수 발견과
직결된다.

## AC-2 — 수정

`usable_base()` 에 검사 (d) 를 넣었다. `>>> MONO-594-EMPTY-WINDOW-GUARD` /
`<<< MONO-594-EMPTY-WINDOW-GUARD` 두 ASCII 표식 사이가 그 블록이다(표식의 용도는 AC-3 참조).

- 🔴 **문자열 비교가 아니라 `git rev-parse "<cand>^{commit}"` 로 정규화해서 비교한다.**
  실측으로 확인: full SHA(`a7d79081552887293f9b6db32ec642ed24d7fd98`)와
  **축약형**(`a7d790815`) **둘 다** 잡혀 `rc=1`(빌드)이 나온다. 문자열 비교였으면 축약형이
  조용히 빠져나갔을 것이고, 그 실패는 고치기 전과 **구별되지 않는다.**
- 🔴 **로그를 남긴다.** 이 결함이 안 보인 이유가 «0 커밋» 을 아무도 이상하게 안 읽은 것이므로,
  새 경로는 무엇을 왜 했는지 두 줄로 적는다:

```
[vercel-ignore] · VERCEL_GIT_PREVIOUS_SHA=a7d7908155… 가 HEAD 자신입니다 — 판정 창이 **0 커밋**입니다 (수동 Redeploy 의 지문).
[vercel-ignore] ✖ 빈 창으로는 판정할 수 없습니다 — 빌드를 진행합니다 (TASK-MONO-594).
rc=1   ← 빌드
```

🔵 **2번 갈래(merge-base)의 동작은 안 건드렸다.** 그쪽엔 `MB = HEAD` elif 가 **먼저** 있어
`usable_base` 에 닿지 않는다 — 칸 (10)(11)이 그 축을 계속 지킨다(둘 다 통과).

## AC-3 — 칸 (13)·(13b) 신설 + **bite 를 CI 에 상주시켰다**

- **칸 (13)** — `CELL_PREV_SHA` 를 HEAD 와 같게 세우고 **자기 경로를 건드린 커밋**을 HEAD 에
  둔다 → 기대 `rc=1`(빌드). 고장 난 판은 여기서 `rc=0`(건너뜀)을 낸다.
- **칸 (13b) — 픽스처 대조군.** 🔴 고친 판에서 (13)은 **커밋 내용과 무관하게** `rc=1` 이다
  (빈 창이면 즉시 빌드). 그러니 (13) 혼자서는 *"HEAD 가 정말 자기 경로를 건드렸는가"* 를
  증명하지 못하고, 픽스처가 조용히 무관한 커밋으로 바뀌어도 초록으로 남는다. (13b)는 **같은
  HEAD 를 정상 창(1 커밋)으로** 재서 여전히 `rc=1` 임을 본다 — 둘 사이에서 달라지는 것이
  창의 크기뿐이므로, (13)이 재는 것이 **창** 이라는 뜻이 된다.
- 🔴🔴 **bite 는 일회성 수동 확인이 아니라 `--self-test` 칸 (h) 로 만들었다.** `_mk` 가 진짜
  트리의 `scripts/vercel-should-build.sh` 를 사본에 복제하고 `_run` 이 그 사본의 판정자를
  쓰므로, **표식 사이를 sed 로 지운 «되돌린 판»** 으로 가드를 돌릴 수 있다. 실측 결과:
  `ok: (h) 빈 창 가드(MONO-594)를 되돌림 -> 칸 (13)이 문다 (rc=1)`. 이것이 티켓이 요구한
  *"되돌린 사본에서 실패"* 를 **영구히** 확인한다 — 한 번 손으로 보고 지나가면 칸 (12)가
  그랬듯 죽은 채 초록이 된다.
- 🔴 **주입 착지부터 단언한다**((e)(g)가 세운 규율): 표식이 삭제 **전 정확히 2개**,
  삭제 **후 0개** 여야 한다. 아니면 «안 물었다» 와 «시험한 적이 없다» 가 구별되지 않는다.
  추가로 되돌린 사본에 `bash -n` 을 건다 — 문법이 깨지면 가드가 아니라 **sed 를** 시험하게
  되고, 그때도 rc=1 이 나와 **통과처럼 보인다.**

## AC-4 — `TASK-MONO-590` 과의 관계를 파일에 못박았다

`vercel-should-build.sh` 헤더와 칸 (13) 주석 **양쪽에** 적었다:
**수동 Redeploy 는 Deploy Hook 경로를 지나가지 않는다.** 590 은 배포가 *만들어지는 것 자체*를
훅 축에서 줄이는 티켓이고 그 AC-3 이 훅 없는 프로젝트에서 `ignoreCommand` 를 살려 두기로
했으므로, 590 이 이 파일을 «거의 죽은 기전» 으로 만들어도 이 갈래와 칸 (13)은 남아야 한다.

## AC-5 — 검증 (숫자 없는 «통과» 없음)

| 게이트 | 결과 |
|---|---|
| `bash -n scripts/vercel-should-build.sh` | rc=0 |
| `bash -n scripts/check-vercel-build-triggers.sh` | rc=0 |
| `bash scripts/check-vercel-build-triggers.sh` | **rc=0 · ok 칸 36개** (설정 3개 × 12칸; 수정 전 30 = 3 × 10) |
| 칸 (13)·(13b) 발화 | 3개 설정 **전부**에서 각각 1회 = 6줄 (론처 · web-store · `kanggle-fan`) |
| `bash scripts/check-vercel-build-triggers.sh --self-test` | **rc=0 · 칸 9개** (무망가 + (a)~(h); 수정 전 8) |
| 그중 새 칸 (h) | `ok: (h) 빈 창 가드(MONO-594)를 되돌림 -> 칸 (13)이 문다 (rc=1)` |
| A/B — 고치기 **전** 판정자 + `PREVIOUS_SHA=HEAD` | **rc=0 (건너뜀)** — 라이브 로그 두 줄을 문자 그대로 재현 |
| A/B — 고친 판정자 + `PREVIOUS_SHA=HEAD` | **rc=1 (빌드)** |
| 정규화 — **축약형** `PREVIOUS_SHA` | **rc=1 (빌드)** — 문자열 비교였으면 빠져나갔을 경로 |

**🔴 CI 러너를 확인하고 적는다 (러너 없는 스위트는 썩는다).**
잡 = `.github/workflows/ci.yml` 의 **`vercel-build-triggers`**
(*"Vercel build triggers (an unrelated commit must not bake a deploy)"*), `ubuntu-latest`,
`timeout-minutes: 5`. 스텝 셋: `bash -n` → `--self-test` → 가드 본 실행.
🔵 **도달성도 확인했다** — `changes` 잡의 `vercel-build-triggers` path-filter 에
`scripts/vercel-should-build.sh` 와 `scripts/check-vercel-build-triggers.sh` 가 **둘 다** 있고
`code-changed` 와 AND 되어 있지 않다. 이 PR 은 두 파일을 모두 건드리므로 잡이 **돈다.**

---

# 🔵 부수 발견 — 이 수정이 랜딩하는 것 자체가 **팬의 «준비된 트리거»** 다

`projects/fan-platform/web/fan-platform-web/vercel-ignore.sh` 의 `SPECS` 에
`':/scripts/vercel-should-build.sh'` 가 **들어 있다**(*"판정자와 이 래퍼. 고쳤으면 한 번은
행사돼야 한다"*). 즉 이 PR 이 `main` 에 스쿼시되면 그 커밋은 `kanggle-fan` 의 트리거 목록을
건드리므로 **팬 프로덕션 빌드가 하나 구워지고**, 그 빌드가 소유자가 이미 넣어 둔
`AUTH_SECRET` 계열 env 를 **반영한다**(`TASK-FAN-FE-018` · `TASK-MONO-574` 선행 2).

🔴 **다만 «머지하면 팬이 산다» 로 읽지 마라. 조건이 하나 남는다** — 위 AC-0 ②가 잰 대로
계정이 배포 생성 한도에 걸려 있는 동안에는 **배포가 생성조차 되지 않는다.** 이 커밋은
슬롯이 있을 때 팬을 굽는 트리거이지, 슬롯 자체를 만들지 않는다.

🔵 그리고 이것이 AC-1 에 적은 단서와 짝을 이룬다: `ignoreCommand` 는 **클론된 커밋의 트리**
에서 돌기 때문에, 이 수정이 담기지 **않은** 옛 커밋을 Redeploy 하면 옛 판정자가 돌아 여전히
취소된다. ⇒ 팬을 살리는 경로는 «옛 배포를 Redeploy» 가 아니라 **«이 커밋을 배포»** 다.
