# Task ID

TASK-MONO-663

# Title

🔴 **포트폴리오 동기화가 하필 가장 못하는 기계에서 돈다** — 리눅스면 몇 분인 일이 Windows-Docker 에서 사본 하나당 수십 분이고, 그래서 「전부 돌린다」가 소유자의 반나절이 된다

# Status

in-progress

# Owner

monorepo

# Task Tags

- ci
- ops
- portfolio

---

# Goal

`scripts/sync-portfolio.sh` 를 **호스트에서 손으로 돌리는 것** 말고 **CI(ubuntu) 에서 돌릴 수
있게** 만든다. 🔴 **「자동으로 돌게 한다」가 아니다** — 트리거를 무엇으로 할지는 이 티켓의
AC-2 가 소유자에게 묻는 별도 결정이고, 기본 제안은 **수동 실행(`workflow_dispatch`)** 이다.

🔴🔴 **이 티켓은 `sync-portfolio.sh` 의 로직을 고치지 않는다.** 스크립트는 오늘 동작한다
(`TASK-MONO-657` 이 `scm`·`erp`·`finance` 로 실측). 바꾸는 것은 **어디서 도는가** 하나다.

---

# 🔴 왜 이 티켓이 있나 — 「범위를 좁힌다」는 결정이 성능 때문에 내려졌다

`TASK-MONO-657` AC-0 에서 소유자에게 사본 6개를 어떻게 할지 물었고, 답은 **「erp·finance
둘만」** 이었다. 🔴 **그 결정의 입력은 「나머지 넷은 안 낡았다」가 아니라 「전부 돌리면
반나절이 걸린다」였다.** 넷은 여전히 **2026-08-04 에 멈춰 있다.**

⇒ 즉 **실행 비용이 범위 결정을 왜곡했다.** 비용이 몇 분이었으면 물어볼 것도 없이 일곱 개를
다 돌렸을 일이다. 🔵 이 티켓은 그 왜곡을 없애는 것이 목적이고, **낡은 사본 넷을 지금
미는 것이 아니다**(그것은 이 티켓이 끝난 뒤 `TASK-MONO-657` 이 다시 열리거나 후속 티켓이 한다).

## 🔴 왜 Windows 에서 느린가 — 기전을 적어 둔다 (설계상 그렇다)

스크립트 § Strategy 가 스스로 적는 대로, 사본 하나를 만드는 데 이 순서를 돈다:

1. `git clone --no-local` — 🔴 `--no-local` 이라 **하드링크 최적화를 끈다**(345–349행).
   모노레포 전체(12,630 파일)를 **실제로 복사**한다.
2. `docker run --rm python:3-alpine` 안에서 `pip install git-filter-repo` + 필터 실행 —
   워크디렉터리를 **볼륨 마운트**한다(404행, `MSYS_NO_PATHCONV=1`).
3. 후처리(gradle 경로 재작성) → `git push --force`

🔴 **Windows 에서 비싼 것은 ①과 ②의 «파일» 축이다**: NTFS 의 파일 생성 비용 + Docker
Desktop 의 **호스트↔리눅스VM 바인드 마운트 번역**. 둘 다 ubuntu 러너에는 없다 —
거기서는 clone 이 같은 파일시스템이고 filter-repo 는 **컨테이너 없이 그냥 pip 로** 깔린다.

🔵 **CPU 나 네트워크가 아니다.** 그래서 「기계를 더 좋은 걸로」가 아니라 **「OS 를 바꾼다」**
가 처방이다.

---

# 🔴🔴 진짜 블로커는 성능이 아니라 «자격증명» 이다 — 실측했다

CI 에서 돌리려면 워크플로가 **다른 리포 7개에 write** 할 수 있어야 한다. 그런데:

```
$ gh secret list                       # 2026-09-10 UTC, monorepo-lab
VERCEL_DEPLOY_HOOK_AUTH        2026-09-02T10:19:56Z
VERCEL_DEPLOY_HOOK_CONSOLE     2026-09-05T13:10:59Z
VERCEL_DEPLOY_HOOK_FAN         2026-08-30T09:58:43Z
VERCEL_DEPLOY_HOOK_PORTFOLIO   2026-08-30T09:57:58Z
VERCEL_DEPLOY_HOOK_STORE       2026-08-30T09:59:41Z
$ gh variable list
(비어 있다)
```

🔴 **다섯 개 전부 Vercel 배포 훅이고, 다른 리포에 쓸 수 있는 토큰은 없다.**

그리고 기본 제공되는 `GITHUB_TOKEN` 은 **이 리포에만** 유효하다 — `wms-platform` 등에는
`403` 이다. ⇒ **새 자격증명이 필요하고, 그것을 만드는 것은 소유자만 할 수 있다.**
🔴 AC-0 이 그 게이트다. 자격증명 없이 워크플로만 만들면 **머지되고, 초록이고, 첫 실행에서
403 으로 죽는다.**

---

# Scope

## 포함

- `.github/workflows/portfolio-sync.yml` 신설 — `workflow_dispatch`,
  입력으로 **프로젝트 이름 하나**(기본 = 없음, 즉 고르지 않으면 안 돈다)
- `scripts/sync-portfolio.sh` 를 **리눅스에서도 돌게** 만드는 최소 변경:
  - 🔴 docker 가 없으면 **호스트의 `git-filter-repo` 를 쓰도록** 폴백(현재는 pre-flight 가
    `command -v docker` 로 **무조건 fail** 한다, 471행) — 🔵 로직이 아니라 **실행 경로**다
  - `MONOREPO_DIR` 이 CI 체크아웃일 때 `main`/`origin/main` 비교 가드(326–343행)가 통과하는지
    확인 — 🔴 `actions/checkout` 의 기본은 **detached HEAD 이고 `main` ref 가 없다**
    ⇒ 그 가드가 *"could not resolve main / origin/main"* 으로 죽는다. `fetch-depth: 0` +
    ref 를 어떻게 줄지 정해야 한다
- 실제로 한 번 돌려서 **소요 시간을 잰다**(그것이 이 티켓의 성립 근거다)

## 제외

- 🔴 **낡은 사본 넷(`wms`·`iam`·`ecommerce`·`fan`)을 미는 일.** 그것은 `TASK-MONO-657` 의
  축이고, 소유자가 **「erp·finance 둘만」** 이라고 이미 결정했다. 이 티켓이 그 결정을
  뒤집으면 안 된다 — **도구를 만드는 것과 쓰는 것은 다른 결정**이다.
- 🔴 **`sync-portfolio.sh` 의 추출 로직·경로 목록·후처리를 고치는 일.** 오늘 동작한다.
- 🔴 **자동 트리거(`on: push` / `on: schedule`)를 다는 일.** AC-2 가 묻는다 — 🔴🔴
  force-push 를 **무인으로** 만드는 것은 `CLAUDE.md` § Git 규율이 명시적 승인 축으로
  못 박은 것을 **워크플로 파일 하나로 영구 승인**하는 것과 같다.
- 낡음을 무는 가드 — `TASK-MONO-657` 의 § 제외가 이미 「사본을 유지한다는 결정이 선행」이라고
  적었고, 그 결정은 아직 없다.

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (verify-then-act)

- [ ] 🔴🔴 **자격증명이 있는가.** `gh secret list` 로 **재라**(위 실측은 낡는다). 다른 리포에
      write 가능한 토큰이 **없으면 STOP** — no-op 이 올바른 구현이다. 🔴 워크플로만 만들고
      머지하면 **초록으로 머지되고 첫 실행에서 403 으로 죽는다**(이 저장소에 그 부류의 선례가
      있다: 게이트 없는 「했다」는 거짓인 채 머지된다).
- [ ] 🔴 토큰을 만드는 것은 **소유자만 할 수 있다.** 스코프를 티켓에 적어서 요청하라 —
      대상 리포 7개에 `contents: write`. 🔵 GitHub App 이든 PAT 든 이 티켓은 안 고른다.

## AC-1 — 리눅스에서 돈다

- [ ] `command -v docker` **무조건 fail**(471행)을 폴백으로 바꾼다: docker 가 있으면 지금대로,
      없으면 `git filter-repo` 를 직접 호출. 🔴 **두 경로가 같은 결과를 내는지**를 재라 —
      술어는 「rc=0」이 아니라 **«같은 파일 목록이 나오는가»** 다.
- [ ] `actions/checkout` 아래에서 가드(326–343행)가 통과하는지 확인. 🔴 detached HEAD 에서
      `rev-parse main` 은 **빈 문자열**을 내고 스크립트는 그것을 `fail` 로 잡는다 —
      즉 **가드는 옳고 환경이 다르다.** 가드를 지우지 말고 **환경을 맞춰라**.
- [ ] ⚪ **한 프로젝트로 실제 실행해서 소요 시간을 적어라.** 🔴 호스트 실측과 **같은
      프로젝트**로 재라(다른 프로젝트로 재면 두 수가 크기가 달라서 비교가 안 된다 —
      비교표의 두 열은 술어와 모집단을 둘 다 맞춰야 한다).

## AC-2 — 트리거는 소유자 결정

- [ ] 🔴🔴 **소유자에게 묻는다**: ⓐ `workflow_dispatch` 만(사람이 누를 때만 돈다) /
      ⓑ + 주기 실행(`schedule`) / ⓒ + `main` 푸시마다. **내 추천은 ⓐ** 이고 사유는
      *force-push 를 무인으로 만드는 것이 `CLAUDE.md` 가 명시적 승인 축으로 못 박은 것*
      이기 때문이다. 🔴 **추천을 결정으로 적지 마라.**
- [ ] 답을 이 티켓에 **소유자의 말 그대로** 적는다.

## AC-3 — 「어디서 도는가」를 사람이 읽는 곳에 적는다

- [ ] `docs/guides/monorepo-workflow.md` § 5(사용법)에 **CI 경로를 추가**한다. 🔴 지금 그
      문서는 호스트 실행만 적고 있고, 그대로 두면 **다음 사람이 또 Windows 에서 반나절을 쓴다.**
- [ ] 🔴 호스트 실행법을 **지우지 마라** — 자격증명이 없거나 CI 가 안 도는 날에 유일한 경로다.

---

# Related Specs / Contracts

- `scripts/sync-portfolio.sh` — 대상. 특히 326–343행(main 비교 가드) · 349행(`--no-local`
  clone) · 404행(docker 마운트) · 437행(force-push) · 471행(docker pre-flight)
- `docs/guides/monorepo-workflow.md` § 5 — 사용법(AC-3 이 고칠 곳)
- `TASK-MONO-657` — **이 티켓을 낳은 곳.** 「erp·finance 둘만」이라는 범위 결정의 입력이
  실행 비용이었다
- `TEMPLATE.md` § Discovery → Distribution — 왜 사본이 존재하는가
- `ADR-MONO-008` 17단계 · `ADR-MONO-016` — `PROJECT_REMOTES` 등록 결정

---

# Edge Cases

- **`git-filter-repo` 를 pip 로 깔면 버전이 다르다** — 컨테이너는 그때그때 최신을 깐다
  (스크립트 368행 `pip install --quiet git-filter-repo`, **핀이 없다**). 🔵 즉 지금도
  버전은 안 고정돼 있으므로 CI 로 옮긴다고 **새로 생기는 위험은 아니다.** ⚪ 핀을 박는 것은
  별도 판단이고 이 티켓의 축이 아니다 — 다만 **핀이 없다는 사실은 적어 둔다.**
- **`--no-local` 을 CI 에서도 유지할 것인가** — 리눅스에서는 하드링크가 되므로 끄면 더
  빠르다. 🔴 그러나 `--no-local` 은 **filter-repo 가 원본을 건드리지 않게** 하는 안전장치일
  수 있다(주석이 사유를 안 적는다) ⇒ **사유를 모른 채 끄지 마라.** ⚪ 켠 채로도 리눅스는
  충분히 빠를 가능성이 높다 — AC-1 의 실측이 답한다.
- **워크플로가 `paths` 필터에 걸린다** — 🔴 `workflow_dispatch` 는 경로 필터가 없지만,
  누군가 나중에 `on: push` 를 더하면 **`projects/**` 를 안 건드린 커밋에서 skipped 가 된다.
  이 저장소는 그 부류(`skipped` 를 초록으로 읽는 것)를 하루에 두 번 밟은 적이 있다.
- **7개 중 하나만 실패하면** — 스크립트는 `set -euo pipefail` 이라 첫 실패에서 멈춘다.
  🔵 워크플로 입력을 **프로젝트 하나**로 잡으면 그 문제가 애초에 안 생긴다(§ Scope).

---

# Failure Scenarios

1. 🔴🔴 **자격증명 없이 워크플로만 머지한다.** 초록으로 머지되고 **첫 실행에서 403**
   이다. AC-0 이 그것을 막는다.
2. 🔴 **가드(326–343행)가 CI 에서 죽으니까 가드를 지운다.** 그 가드는 *"local main 이
   origin/main 과 다르면 잘못된 상태를 사본에 발행한다"* 를 막는 것이다 — 🔴 **CI 에서야말로
   그것이 더 중요하다**(사람이 눈으로 안 본다). 환경을 맞춰라, 가드를 지우지 마라.
3. 🔴 **「리눅스가 빠를 것이다」를 재지 않고 적는다.** 그러면 이 티켓의 근거가 **추정**이다.
   AC-1 의 마지막 칸이 그것을 막는다.
4. 🔴 **트리거를 내가 정한다.** force-push 를 무인화하는 것은 소유자 결정이다.
5. 🔴 **도구를 만든 김에 낡은 사본 넷을 민다.** 소유자는 **「erp·finance 둘만」** 이라고
   말했다. 도구를 만드는 것과 쓰는 것은 **다른 결정**이다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** — 워크플로 한 장 + 스크립트의 실행 경로 폴백이 본체다.
🔴 단, **AC-0(자격증명) 과 AC-2(트리거) 는 소유자**이고 대리 판단이 불가능하다.

---

# 🔵 실행 순서 — 소유자 결정 (2026-09-10 UTC)

소유자의 말 그대로: **「662 를 먼저 해결한 뒤」**

⇒ 이 티켓은 **`TASK-MONO-662` 뒤에 선다.** 🔴 사유: **자동화는 「사본을 더 자주 민다」는
뜻**이고, 662 가 열려 있는 동안은 **밀 때마다 사본 배지가 빨개진다** — 순서를 바꾸면
자동화가 그 결함을 **더 빨리 복제**한다.

🔴 **그러므로 AC-0(자격증명)은 아직 열려 있고, 여는 시점도 지금이 아니다.** 소유자가
토큰을 만드는 것은 662 가 닫힌 뒤다. 지금 만들어 두면 «준비됐다» 는 신호가 되어
이 순서를 흐린다.

## 🔴 이 티켓의 ID 가 한 번 바뀌었다

기안 당시 `TASK-MONO-661` 로 냈는데, **같은 시각 다른 흐름이 그 번호를 먼저 썼다**
(`tasks/review/TASK-MONO-661-a-green-push-closes-a-red-the-push-never-re-ran.md`, #3752).
`Task ID collision` **required 체크가 그것을 잡았고** — 🔵 로컬에서는 스테이지 뒤에 돌려도
`rc=0` 이었다, 내 트리에 그 파일이 없었으므로 — **663 으로 옮겼다.**

⇒ 🔴 **ID 를 고를 때는 `origin/main` 을 먼저 fetch 해서 세라.** 이 저장소의 규칙에 그
문장이 이미 있지만 *"다음 작업을 추천하기 전"* 단계로만 적혀 있어서 **기안 시점에는 안
읽힌다.** `git log --oneline -1 origin/main` 만으로는 부족하고 `git ls-tree origin/main`
으로 **파일 목록**을 세야 한다.

---

# 🟢 AC-0 소유자 결정 (2026-09-11 UTC) — **fine-grained PAT 발급**

## 답 (소유자의 말 그대로)

> **「fine-grained PAT 발급」**

세 갈래(fine-grained PAT / GitHub App / 보류하고 수동 실행)를 올렸고 소유자가 **PAT** 를
골랐다. 🔵 순서 게이트였던 **`TASK-MONO-662` 는 `done/` 으로 닫혔다**(`894f6dc35`)
⇒ 이 질문을 열 시점이 됐다는 조건이 충족됐다.

## 🔴 그러나 AC-0 은 **아직 닫히지 않는다** — 결정 ≠ 자격증명의 존재

이 AC 의 게이트는 *"대상 7개 리포에 `contents: write` 인 토큰이 **있는가**"* 이지
*"어떤 방식으로 만들 것인가"* 가 아니다. 🔴 **토큰 없이 워크플로만 머지하면 초록으로
머지되고 첫 실행에서 죽는다** — 그것이 이 AC 가 존재하는 이유다.

실측(재확인 필요): `gh secret list` 다섯 개가 **전부 Vercel 훅**이고 다른 리포에 write
가능한 토큰이 **없다**. 기본 `GITHUB_TOKEN` 은 대상 리포에 **403**.

## ⇒ 소유자가 해야 할 것 (에이전트가 대신할 수 없다)

1. GitHub 에서 **fine-grained PAT** 발급 — 대상 리포 **7개**에만, 권한은 **`Contents: Read and write`** 하나.
2. 이 저장소 시크릿에 등록. 🔴 **값을 대화창에 붙여넣지 말 것** — GitHub UI 에서 바로 넣는다.
3. 🔴 **만료일을 이 티켓에 적는다.** fine-grained PAT 는 만료가 있고, **만료되면
   워크플로가 조용히 죽는다**(그때 증상은 «동기화가 안 됐다» 이고 원인은 안 보인다).
   ⇒ 만료일 + 갱신 책임을 § 에 남기는 것이 **이 선택이 떠안은 비용**이다.

🔵 그 셋이 끝나면 AC-0 이 닫히고 AC-1(워크플로) 이하가 열린다.

🔴 **그 전까지 워크플로를 머지하지 마라.** 이 티켓 § Background 가 그 실패 모양을 이미
적어 뒀다.

---

# 🟢 구현 (2026-09-11 UTC)

## 🟢 AC-0 — 자격증명. **결정이 아니라 «존재» 를 쟀다**

소유자가 **「fine-grained PAT 발급」** 을 고르고 실제로 넣었다. 🔴 **「넣었다」를 그대로
믿지 않고 쟀다** — 이름 오타가 가장 흔한 실패다:

```
$ gh secret list --repo kanggle/monorepo-lab
PORTFOLIO_SYNC_TOKEN        2026-09-11T09:35:09Z     ← 🟢 정확 일치
VERCEL_DEPLOY_HOOK_AUTH     …  (기존 5개)
```

**만료일: 2027-12-10** (소유자 보고). 🔴 fine-grained PAT 는 만료되면 **워크플로가 조용히
죽고** 그때 증상은 *"동기화가 안 됐다"* 라 원인이 안 보인다 ⇒ 이 줄이 그 기록이다.

### 🔴 그러나 «시크릿이 있다» 는 «토큰이 동작한다» 가 아니다

`gh secret list` 는 **이름만** 보여 준다 — 스코프도, 어느 리포를 포함하는지도 말해 주지
않는다. 이 AC 가 막으려던 것이 정확히 *"초록으로 머지되고 첫 실행에서 403 으로 죽는다"*
이므로, **워크플로 첫 잡을 프리플라이트로** 만들었다(아래 AC-1). ⇒ 첫 실행이 **자격을
먼저 판정**한다.

## 🟢 AC-1 — 리눅스에서 돈다

### ① `command -v docker || fail` → 백엔드 해결

🔴 그 한 줄이 이 티켓의 원인이었다. 이제 **native → docker → 둘 다 이름을 대며 실패** 다.

🔴🔴 **첫 구현이 틀렸고 그 자리에서 잡았다**: `command -v docker` 로 판정했더니 이 호스트
(Docker Desktop **정지** 상태)에서 **docker 를 골랐다.** CLI 가 PATH 에 있다는 것은
**데몬이 살아 있다는 뜻이 아니다** — 그대로 뒀으면 추출을 다 한 뒤 `docker run` 에서
*"failed to connect to the docker API at npipe:…"* 로 죽었을 것이다.
⇒ **`docker info`** 로 바꿨다. 🔵 **쓸 것을 찔러라, 그것을 부르는 이름 말고.**

**실측(이 호스트 = 네이티브도 docker 데몬도 없음):**

```
$ bash scripts/sync-portfolio.sh --dry-run scm-platform          rc=1
[fail] no filter-repo backend available. Install EITHER:
    · git-filter-repo on PATH   →  pip install git-filter-repo   (preferred; no container)
    · docker                    →  the script then runs filter-repo in python:3.11-alpine

$ FILTER_BACKEND=native  …    → "filter-repo backend: native (forced via FILTER_BACKEND)"  🟢
$ FILTER_BACKEND=bogus   …    → "[fail] FILTER_BACKEND must be 'native' or 'docker'"       🟢
```

### 🔵 «두 경로가 같은 결과» 를 **구조로** 보장했다

AC 의 술어는 *"rc=0 이 아니라 «같은 파일 목록이 나오는가»"* 다. 🔵 가장 강한 방법은
**필터 명령을 한 벌만 두는 것**이라, 생성되는 `_filter_repo_run.sh` 에서
**프리앰블만 백엔드별로 가르고 필터 명령 본문은 동일**하게 뒀다.
🔴 그리고 `--global` 은 **컨테이너 경로에만** 남겼다 — 네이티브는 남의 기계(또는 다른
스텝과 공유하는 러너)에서 도므로 리포-로컬 config 를 쓴다.

### ② `rev-parse main` — 🔴 가드를 안 지우고 **환경을 맞췄다**

`actions/checkout` 기본값은 detached HEAD 라 `rev-parse main` 이 **빈 문자열**을 내고
스크립트가 그것을 `fail` 로 잡는다. AC 가 *"가드는 옳고 환경이 다르다"* 라고 적었다.
⇒ 워크플로의 두 잡 모두 **`ref: main` + `fetch-depth: 0`** 으로 체크아웃한다.

### ⚪ ③ 소요 시간 — **못 쟀다**

🔴 이 호스트에 **네이티브도 docker 데몬도 없다** — 어느 경로도 못 돌린다. 🔵 AC 가
*"호스트 실측과 **같은 프로젝트**로 재라"* 고 못 박았으므로 **아무 수나 적지 않는다**
(두 열의 술어와 모집단을 맞춰야 비교가 성립한다).
⇒ **집**: 워크플로를 `dry_run=false` 로 **한 프로젝트(`scm-platform`)에 처음 돌리는 그
실행**이 이 칸을 닫는다. 러너 로그의 잡 소요가 곧 그 수다.

## 🟢 AC-2 — 트리거. 소유자 결정: **「ⓐ workflow_dispatch 만」**

세 갈래(ⓐ 수동만 / ⓑ + 주기 / ⓒ + push)를 각각이 포기하는 것과 함께 올렸고 소유자가
**ⓐ** 를 골랐다. 🔵 내 추천도 ⓐ 였지만 **물어서 받았다.**

🔴 **포기한 것을 적는다**: 사본이 낡는 것을 **아무도 알려 주지 않는다.**
`TASK-MONO-657` 이 37일/114일 낡은 것을 **사람이 눈치채서** 발견했다. 그 감시는 별도
축이고 이 워크플로가 하지 않는다.

🔵 ⓑ 를 안 고른 데는 부수 사유도 있다 — `TASK-MONO-662` 가 방금 **사본에서 `schedule:`
을 떼어낸** 티켓이라, 모노레포에 주기 실행을 다시 들이는 것이 모순으로 읽힐 수 있다
(다른 축이지만 적어 둔다).

## 🟢 AC-3 — 「어디서 도는가」를 사람이 읽는 곳에

`docs/guides/monorepo-workflow.md` § 5 에 **CI 경로를 먼저** 놓는 표를 넣고, 왜 Windows
호스트가 느린지(NTFS 파일 생성 + Docker 번역)를 적었다.
🔴 **호스트 실행법은 안 지웠다** — 자격증명이 없거나 CI 가 안 도는 날의 유일한 경로다.
🔵 백엔드 자동 선택과 `FILTER_BACKEND` 강제 지정도 같이 적었다.

## 워크플로의 모양

- **트리거**: `workflow_dispatch` 만. 입력 `project`(비우면 전부) · `dry_run`(**기본 true**)
- **`permissions: contents: read`** — 🔵 기본 토큰의 권한을 최소로. 대상 리포로 가는
  권한은 `PORTFOLIO_SYNC_TOKEN` 에서만 온다
- **`concurrency: portfolio-sync`** — 🔴 두 런이 같은 사본에 force-push 하면 나중 것이
  앞 것을 덮고 **어느 쪽이 남았는지는 타이밍이 정한다**
- **잡 ① 프리플라이트**: 시크릿 존재 → **7개 리포 `push` 권한** → 목록이 스크립트와 같은가
  - 🔴 술어가 **«리포가 보이는가» 가 아니라 «push 권한이 있는가»** 다. read-only 토큰도
    `GET /repos/…` 에 200 을 낸다 — 그것을 통과로 읽으면 **첫 push 에서 403** 이다
  - 🔴 프리플라이트의 리포 목록은 손으로 적었으므로 **`PROJECT_REMOTES` 에서 유도해
    대조**한다. 안 하면 리포가 늘어난 날 프리플라이트만 모른다
- **잡 ② 동기화**: `needs: preflight`. `FILTER_BACKEND=native` 를 **명시**해 «우연히
  native» 가 되지 않게 하고, 🔴 파이프를 안 써서 **종료코드가 tail 것이 되지 않게** 한다
