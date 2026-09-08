# Task ID

TASK-MONO-640

# Title

🔴 `.gitignore` 의 `bin/` 이 **README 가 «필수» 라고 적은 생성기**를 삼켰다 — 선언된 스크립트 셋이 없는 파일을 가리킨다

# Status

done

# Owner

monorepo

# Task Tags

- demo
- public-data
- tooling
- bug

---

# Goal

`infra/demo/public-data/` 의 **생성기와 발행자가 저장소에 없다.** 루트 `.gitignore` 의
경로 제한 없는 `bin/` 패턴(Gradle/Eclipse 산출물용)이 그 디렉터리를 통째로 먹었다.

그래서 새로 클론한 사람은 **번들 시드를 재생성할 수 없고**, `package.json` 이 선언한 세
스크립트가 전부 없는 파일을 가리킨다. `ADR-MONO-070` 이 «발행 절차를 로컬에서 실제로
실행해 시험한다» 를 설계 근거로 삼았는데, 그 절차를 실행하는 CLI 가 커밋돼 있지 않다.

🔴🔴 **지금 그 파일들은 한 사람의 작업 디렉터리에만 있다.** 그 트리가 사라지면 생성기가
사라진다 — 그리고 그 사실은 «스냅샷을 다시 만들어야 하는 날» 에야 발견된다.

---

# Context — 실측 (2026-09-08 UTC, `main` = `04b177e30`)

## 무엇이 없는가

```
$ git ls-files infra/demo/public-data/ | wc -l
19                      ← README · fixtures 3 · package.json · snapshots 3 · src 8 · tests 1 · tsconfig
$ git ls-files infra/demo/public-data/bin/ | wc -l
0                       ← 🔴 하나도 없다
$ ls infra/demo/public-data/bin/
build-bundled-snapshots.mjs   (266줄)
publish-public-data.mjs       (383줄)
$ git check-ignore -v infra/demo/public-data/bin/build-bundled-snapshots.mjs
.gitignore:14:bin/      infra/demo/public-data/bin/build-bundled-snapshots.mjs
```

## 그 없는 파일을 가리키는 선언들

| 자리 | 무엇을 말하는가 |
|---|---|
| `package.json` `scripts` | `build-snapshots` · `check-snapshots` · `publish-snapshot` — **셋 다** `node bin/…` |
| `README.md` (82·83·97·101·105행) | 스냅샷 생성·드리프트 검사·발행 절차의 **정본 사용법** |
| `README.md` 「파일이 이 모양인 이유」 표 | *"`snapshots/` … **손으로 쓰지 않는다**"* — 손으로 안 쓰면 무엇이 쓰는지가 이 파일이다 |
| `src/index.ts` | *"발행자(`bin/publish-public-data.mjs`)는 여기서 재수출하지 않는다"* |
| `fixtures/console-sample.mjs` | *"`bin/publish-public-data.mjs` 는 fan·store 만 …"* |

⇒ 저장소는 그 파일들의 **존재를 전제로 쓰여 있다.**

## 범위는 정확히 하나다

무시된 `bin/` 디렉터리는 **59개**인데, 그중 **소스를 담은 것은 하나뿐**이다:

```
$ (무시된 bin/ 중 .mjs|.ts 를 가진 것)
infra/demo/public-data/bin/ → 2
```

나머지 58개는 전부 Gradle/Eclipse 산출물(`default` · `generated-sources` · `main` · `test`)이다.
🔴 **그러므로 `bin/` 패턴 자체를 지우면 안 된다** — 58개 디렉터리의 컴파일 산출물이 추적
대상이 된다. 고칠 것은 **그 한 경로의 예외**다.

## CI 는 이 패키지를 한 번도 안 돈다

```
$ grep -rn "public-data" .github/workflows/*.yml
(0건)
```

패키지 시험(`tests/public-data.test.mjs`)도, 스냅샷 드리프트 검사도 **러너가 없다.**
🔴 이 저장소가 이름 붙인 함정이다 — *러너 없는 스위트는 썩는다.*

## 🔴🔴 그리고 «드리프트» 는 이 호스트에서 **오진이다**

```
$ node bin/build-bundled-snapshots.mjs --check
[build-snapshots] ✗ 드리프트: fan.json 이 픽스처와 다릅니다.
[build-snapshots] ✗ 드리프트: store.json 이 …
[build-snapshots] ✗ 드리프트: console-sample.json 이 …
rc=1
```

**세 파일 다 «다르다» 고 나오지만 내용은 같다.** 사본에서 재생성해 정규화 md5 로 대조했다:

| 데이터셋 | 재생성본 md5(정규화) | 커밋본 md5(정규화) | |
|---|---|---|---|
| fan | `064b1c21…` | `064b1c21…` | 동일 |
| store | `bc2ac388…` | `bc2ac388…` | 동일 |
| console-sample | `8a60128d…` | `8a60128d…` | 동일 |

원인은 **줄바꿈**이다: 작업트리 `CR=125 LF=125`(CRLF) · 재생성본 `CR=0 LF=125`(LF) ·
git blob `CR=0`(LF). 이 호스트의 `core.autocrlf=true` 가 체크아웃에서 CRLF 로 바꾸는데
빌더는 LF 로 쓰고, `--check` 는 **바이트로 비교**한다.

🔵 그리고 빌더는 `generatedAt` 을 **상수로 고정**해 두었다(파일 49~51행). 즉 비결정성을
이미 한 번 막아 뒀는데, 줄바꿈 축은 안 막혀 있다.

🔴 **이 오진을 「손으로 편집됐다」로 읽으면 세 파일을 통째로 다시 써서 거대한 가짜 diff 를
만든다.** `TASK-MONO-638` AC-0 이 정확히 그 문장을 담고 있으므로, 이 티켓이 먼저 간다.

---

# Scope

## 포함

- 루트 `.gitignore` — `bin/` 의 **경로 한정 예외**
- `infra/demo/public-data/bin/build-bundled-snapshots.mjs` · `publish-public-data.mjs` — 커밋
- `infra/demo/public-data/` — 필요한 경우 `.gitattributes` 또는 `--check` 비교 방식
- 가드 — 선언된 스크립트가 **실재하고 추적되는** 파일을 가리키는가
- `.github/workflows/ci.yml` — AC-5 의 판단에 따라

## 제외

- 🔴 다른 58개 `bin/` — 전부 빌드 산출물이다. **건드리지 마라.**
- 데이터 확충(`TASK-MONO-638`) · 캡처(`TASK-MONO-639`)
- Blob 발행 배선(소유자 승인 대기)

---

# Acceptance Criteria

## AC-0 — 착수 전 실측 (verify-then-act)

- [x] 🔴🔴 **먼저 그 두 파일을 백업한다.** 저장소 어디에도 없는 유일본이고, `.gitignore`
      를 만지다 실수하면 되돌릴 곳이 없다. 백업 위치와 md5 를 적는다.
- [x] `git check-ignore -v` 로 **지금도 무시되는지** 다시 확인한다. 그 사이 누가 고쳤으면
      이 티켓의 재현 조건이 사라진다 — 그때는 «무시되는가» 가 아니라 **«추적되는가»** 로
      판정하라(파일이 `git ls-files` 에 있으면 닫힌다).
- [x] 무시된 `bin/` 중 **소스를 담은 것이 여전히 하나뿐인지** 다시 센다. 늘었으면 이
      티켓의 예외 하나로는 부족하다.

## AC-1 — `.gitignore` 를 **경로 한정**으로 고친다

- [x] `bin/` 패턴은 **그대로 둔다.** 58개 Gradle 산출물이 그것에 걸려 있다.
- [x] `infra/demo/public-data/bin/` 만 예외로 되살린다.
- [x] 🔴 **양방향으로 확인한다** — 한쪽만 보면 절반이 조용히 깨진다:
      · `git check-ignore` 가 Gradle `bin` 경로를 **여전히 무시**하는가
      · `infra/demo/public-data/bin/*.mjs` 를 **더는 무시하지 않는가**
- [x] 예외를 둔 **이유를 `.gitignore` 에 적는다.** 안 적으면 다음 사람이 «왜 여기만 예외지»
      하고 지운다(그리고 그 지움은 아무 에러도 안 낸다).

## AC-2 — 생성기와 발행자를 커밋한다

- [x] 두 파일이 `git ls-files` 에 뜬다.
- [x] 🔴 **깨끗한 트리에서 검증한다** — 새 worktree(또는 `git archive`)에서
      `node bin/build-bundled-snapshots.mjs --check` 가 **돈다**. 「내 트리에서 됐다」는
      이 티켓의 증거가 아니다. 그 트리에 파일이 있다는 것이 바로 재현 조건이었다.
- [x] 커밋 전에 두 파일에 «로컬 전용/커밋 금지» 마커가 없는지 확인한다(실측: 없음).

## AC-3 — 가드: 선언된 스크립트가 실재하는 파일을 가리키는가

- [x] `package.json` 의 `scripts` 가 가리키는 로컬 경로가 **존재하고 추적되는지** 무는 칸을
      둔다. 🔴 «존재» 만 물으면 이 결함을 **못 잡는다** — 결함의 정의가 «있는데 추적 안 됨»
      이다. `git ls-files` 로 물어라.
- [x] 자리를 정하고 근거를 적는다(`verify-demo-wrapper.sh` 의 칸으로 둘지, `scripts/` 아래
      독립 검사로 둘지). 🔵 이 패키지 밖의 `package.json` 들도 같은 부류의 결함을 가질 수
      있으므로, 모집단을 **어디까지 볼지 먼저 세고** 정하라.
- [x] **bite**: 스크립트가 없는 파일을 가리키게 만들면 그 가드가 빨개진다. 그리고
      **추적 안 되는 파일**을 가리키게 만들어도 빨개진다(두 축은 다르다).

## AC-4 — `--check` 의 줄바꿈 오진

- [x] 처방을 **정하고 근거를 적는다.** 후보: ① 비교 전에 줄바꿈 정규화 ② `.gitattributes`
      로 `snapshots/*.json` 을 `eol=lf` 고정 ③ 그대로 두고 문서화.
- [x] 🔴 어느 쪽을 고르든 **이 호스트에서 실제로 돌려서** 확인한다. CI(Linux, LF)에서만
      맞는 처방은 이 결함을 못 고친다 — 결함이 **Windows 체크아웃에서만** 나타나기 때문이다.
- [x] 🔴 정규화를 고른다면 **내용 비교가 느슨해지지 않았는지** 확인한다. 진짜 드리프트
      (픽스처를 고치고 스냅샷을 안 만든 상태)는 여전히 빨개져야 한다 — bite 로 보여라.

## AC-5 — 러너 없는 스위트

- [x] CI 에서 `public-data` 를 도는 잡이 **0개**임을 다시 확인한다.
- [x] 배선할지 말지 **정하고 근거를 적는다.** 배선한다면 최소한 패키지 시험과 스냅샷
      드리프트 검사가 대상이다. 🔴 안 배선하기로 정한다면 **그 이유와 그때까지 무엇이
      미측정인지**를 적어라 — 「나중에」는 근거가 아니다.
- [x] 배선한다면 그 잡이 **실제로 돌았는지**를 PR 에서 로그로 확인한다(경로 필터에 걸려
      SKIPPED 되면 배선한 적이 없는 것과 같다).

---

# Related Specs / Contracts

- [`ADR-MONO-070`](../../docs/adr/ADR-MONO-070-public-browsing-without-the-backend.md) — «발행 절차를 로컬에서 실제로 실행해 시험한다» 가 이 티켓이 고치는 전제다
- [`infra/demo/public-data/README.md`](../../infra/demo/public-data/README.md) — § 시드 · § 왜 저장소 어댑터가 둘인가
- `TASK-MONO-635` — 이 패키지를 만든 티켓(`review/`, frozen). 🔴 **Review Rules 에 따라 이 티켓이 새로 기안됐다** — 그 티켓을 고쳐 쓰지 않는다
- `TASK-MONO-638` — **이 티켓이 선행이다.** 638 은 픽스처를 늘린 뒤 스냅샷을 재생성해야 하는데, 지금은 새 트리에 생성기가 없다. 그리고 638 AC-0 의 「다르면 손으로 편집된 것」 문장은 이 호스트에서 **오진**이다

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 다른 `bin/` 이 예외에 휩쓸림 | AC-1 의 양방향 확인이 잡는다 |
| 예외를 뒀는데 파일이 여전히 스테이지 안 됨 | `git ls-files` 로 확인(존재 ≠ 추적) |
| CRLF 체크아웃에서 `--check` | 내용이 같으면 통과해야 한다 |
| 픽스처를 고치고 스냅샷을 안 만듦 | `--check` 가 **여전히** 빨개져야 한다(AC-4 bite) |
| Linux CI 에서 `--check` | 지금도 통과할 것이다 — 그러므로 CI 만으로는 이 결함이 안 보인다 |

---

# Failure Scenarios

1. **`bin/` 패턴을 지운다** → 58개 디렉터리의 Gradle 산출물이 추적 대상이 되고, 그 diff 는
   수천 파일이다. 그리고 그것은 «되돌리기» 가 아니라 새 결함이다.
2. **파일만 `git add -f` 로 넣고 `.gitignore` 는 안 고친다** → 다음에 그 디렉터리에 파일이
   하나 늘면 **그것만 조용히 안 들어간다.** 같은 결함이 한 파일 단위로 재발한다.
3. **`--check` 를 «항상 통과» 로 느슨하게 만든다** → 진짜 드리프트를 못 잡는다. 이 저장소가
   반복해서 밟은 «가드를 고치는 대신 술어를 지우는» 실패다.
4. **가드를 «파일이 존재하는가» 로 짠다** → 이 결함의 정의는 «존재하는데 추적 안 됨» 이므로
   그 가드는 오늘 상태에서도 **초록**이다.
5. **백업 없이 `.gitignore` 를 만진다** → 실수로 그 트리를 정리하면 생성기가 영구히 사라진다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** (파일 커밋 + `.gitignore` 한 줄 + 가드 한 칸. 다만 AC-4 의
처방 선택과 AC-5 의 배선 여부는 판단이므로 막히면 Opus).

---

# Verification — 구현 세션 (2026-09-08 UTC)

## AC-0 — 착수 전 실측 (verify-then-act)

**① 백업 먼저.** 두 파일은 저장소 어디에도 없는 유일본이었다. 스크래치패드에 복사하고
원본과 md5 를 대조했다(동일):

| 파일 | md5 |
|---|---|
| `build-bundled-snapshots.mjs` | `947e305e8ad9a205d4a1d8751544da4b` |
| `publish-public-data.mjs` | `50dbd70ff8ae4c4dd36bb456cba60355` |

**② 여전히 무시되는가** — 그렇다:

```
$ git check-ignore -v infra/demo/public-data/bin/x.mjs
.gitignore:14:bin/      infra/demo/public-data/bin/x.mjs
$ git ls-files infra/demo/public-data/bin/ | wc -l
0
```

**③ 소스를 담은 무시 `bin/` 이 여전히 하나뿐인가** — 그렇다(`infra/demo/public-data/bin/` → 2건,
합계 **1**). 늘었으면 예외 하나로는 부족했을 것이다.

## AC-1 — `.gitignore` 를 경로 한정으로 고친다

패턴 `bin/` 은 **그대로 두고** 한 경로만 되살렸다.

🔴 **문법을 실측으로 정했다.** 두 후보를 실제로 넣어 보고 골랐다:

| 예외 문법 | `public-data/bin/*.mjs` | Gradle `bin/main` |
|---|---|---|
| `!infra/demo/public-data/bin/` | ✅ **추적 가능** | ✅ 여전히 무시 |
| `!infra/demo/public-data/bin/**` | 🔴 **여전히 무시** | ✅ 여전히 무시 |

⇒ `bin/` 이 **디렉터리**를 제외하면 git 은 그 안으로 내려가지 않으므로 `**` 형태는 아무
효과가 없다. 디렉터리 형태만 동작한다. 이 사실을 `.gitignore` 주석에 적었다.

**양방향 확인**(변경 후):

| 경로 | 결과 |
|---|---|
| `infra/demo/public-data/bin/build-bundled-snapshots.mjs` | ✅ 추적 가능 |
| `libs/java-common/bin/main` | ✅ 여전히 무시 |
| `projects/ecommerce-microservices-platform/apps/product-service/bin/main` | ✅ 여전히 무시 |
| `libs/bin/default` · `projects/bin/default` | ✅ 여전히 무시 |

## AC-2 — 생성기와 발행자를 커밋한다

- `git ls-files` 에 두 파일이 뜬다.
- 🔴 **깨끗한 트리에서 확인했다.** 「내 트리에서 됐다」는 이 티켓의 증거가 아니다 —
  그 트리에 파일이 있다는 것이 바로 재현 조건이었다. `git archive HEAD` 로 **커밋된 것만**
  뽑아 그 안에서 돌렸다:

```
$ ls <archive>/infra/demo/public-data/bin/
build-bundled-snapshots.mjs   publish-public-data.mjs
$ node bin/build-bundled-snapshots.mjs --check     → rc=0 (세 데이터셋 드리프트 없음)
$ node --test tests/public-data.test.mjs           → rc=0
```

- 커밋 전 «로컬 전용/커밋 금지» 마커를 grep 했다 — **없음**(실측).

## AC-3 — 가드: 선언된 스크립트가 실재하는 파일을 가리키는가

**자리: `scripts/check-package-script-targets.sh`**(독립 검사). 근거 — 모집단이 이 패키지가
아니라 **저장소 전체의 `package.json`** 이고(16개), `verify-demo-wrapper.sh` 는 `infra/demo`
축의 파일이라 그 모집단을 담기에 자리가 안 맞는다. 형제 선례가 `scripts/` 에 여럿 있다
(`check-message-backticks.sh` 등).

🔴 **술어는 «존재하는가» 가 아니라 «추적되는가» 다**(`git ls-files --error-unmatch`).
이 결함의 정의가 «작성자 트리에는 있는데 커밋은 안 됨» 이므로, 존재 검사는 결함이 있는
상태에서도 **초록**이다.

**모집단**: `package.json` **16개** · 로컬 스크립트 대상 **10건**. 둘 중 하나라도 0이면
`FAIL` 한다(0건을 훑고 «위반 없음» 으로 통과하는 것이 이 부류의 표준 고장이다).
🔵 위반 수에는 **하한을 두지 않는다** — 이 모집단은 비어 있는 것이 정상이고, 하한을 두면
다 고쳤을 때 성공이 고장난다.

### self-test — 실트리를 변형해서 증명한다

| 칸 | 무엇 | 기대 | 결과 |
|---|---|---|---|
| (1) 대조군 | 손대지 않은 트리 | 위반 0 | ✅ |
| (2) bite | 없는 파일을 가리키는 스크립트 | 위반 1 | ✅ |
| (3) bite | 🔴 **있는데 추적 안 되는** 파일 | 위반 1 | ✅ |
| (4) 대조군 | 추적되는 파일을 가리킴 | 위반 0 | ✅ |

🔴 (3)이 이 가드의 존재 이유다. (2)만 있으면 «존재하는가» 가드와 구별되지 않는다.

🔵 (4)를 짜다 한 번 틀렸다: 대상 경로가 **그 `package.json` 의 디렉터리 기준**으로 풀리는데
리포 루트 기준으로 적어서 «없는 파일» 로 물렸다. **가드가 옳았고 픽스처가 틀렸다** — 그
사실을 스크립트 주석에 남겼다.

## AC-4 — `--check` 의 줄바꿈 오진

**처방: 비교 전에 CRLF→LF 정규화**(후보 ①). 근거:

- 후보 ②(`.gitattributes` 로 `eol=lf` 고정)는 **오늘 고쳐 주지 못한다.** 이미 CRLF 로
  체크아웃된 작업 트리는 재체크아웃/`--renormalize` 전까지 그대로이고, 그때까지 이 가드는
  계속 거짓 빨강을 낸다. 결함이 **Windows 체크아웃에서만** 나타나므로 그 지연이 곧 결함이다.
- 후보 ③(문서화)은 «가드를 읽고 무시하라» 는 뜻이고, 무시되는 가드는 없는 가드다.
- 🔵 이것은 빌더가 이미 한 처방(`generatedAt` 을 상수로 고정)과 **같은 부류**다: 비교가
  재려던 것만 재게 만든다. JSON 의 의미에 줄바꿈 표현은 들어 있지 않다.

**이 호스트에서 실제로 돌려서** 확인했다:

| | 전 | 후 |
|---|---|---|
| `--check` (정상 트리) | 🔴 `rc=1` · 세 데이터셋 전부 «드리프트» | ✅ `rc=0` · 드리프트 없음 |
| `--check` (픽스처 변경 후) | — | ✅ `rc=1` · `fan.json` 드리프트 |

🔴 **느슨해지지 않았다** — 아래 bite 가 그것을 보인다: `membership-plans.mjs` 의 가격을
바꾸니 `fan.json` 이 여전히 빨개졌고(`rc=1`), 나머지 둘은 정상으로 남았다(즉 **바뀐 것만**
잡는다). 복원 후 diff 0.

## AC-5 — 러너 없는 스위트

**재확인**: `grep -rn "public-data" .github/workflows/*.yml` → **0건**. 패키지 시험도 스냅샷
드리프트 검사도 러너가 없었다. 🔵 그리고 이 티켓 전에는 **배선했어도 돌 수 없었다** —
생성기 자체가 커밋돼 있지 않았기 때문이다.

**결정: 배선한다.** 두 자리로 나눴다.

| 무엇 | 어디 | 근거 |
|---|---|---|
| 스냅샷 드리프트 검사 + 패키지 시험 | 기존 `demo-wrapper-smoke` 잡의 스텝 | 그 잡의 트리거가 이미 `infra/demo/**` 라 이 패키지의 어떤 변경도 깨운다. 잡을 새로 만들면 **같은 트리거를 두 벌**로 유지해야 하고, 두 벌은 갈라진다 |
| 새 가드 | 새 잡 `package-script-targets` | 모집단이 `infra/demo` 가 아니라 저장소 전체다. `message-backticks` 와 같은 모양(문법 → self-test → 실행) |

🔴 드리프트 검사를 **패키지 시험보다 먼저** 뒀다: 픽스처를 고치고 스냅샷을 안 만든 상태에서
시험만 통과하는 것을 «정상» 으로 보고하면 안 된다.

🔴🔴 **배선 사슬 네 단계를 다 연결했고, 네 단계임을 확인했다**:

```
① paths-filter 정의  package-script-targets: ['**/package.json', '.gitignore', 'scripts/…']
② changes 의 outputs 선언   package-script-targets: ${{ steps.filter.outputs.package-script-targets }}
③ 잡의 if            needs.changes.outputs.package-script-targets == 'true'
④ 스텝               bash -n → --self-test → 실행
```

②가 빠지면 그 잡은 **한 번도 안 돌면서 PR 은 초록**이다 — 그것이 바로 이 티켓이 고치는
«선언은 있는데 실제로는 없다» 와 같은 부류라, `ci.yml` 주석에 못 박았다.
🔴 트리거에 **`.gitignore` 를 넣었다** — 원인이 그쪽에 있기 때문이다. `package.json` 만
보면 이 결함의 도착 경로를 놓친다.

- [x] 🔴 **PR 에서 두 잡이 SKIPPED 가 아니라 실제로 돌았는지 로그로 확인한다.**
      (배선했는데 경로 필터에 걸려 안 돌면 배선한 적이 없는 것과 같다.)

      **측정(PR #3688, run 34224907741)** — 잡의 «결론» 이 아니라 **스텝**을 읽었다:

      | 잡 | 스텝 | 결과 |
      |---|---|---|
      | `package.json scripts point at files that are in the repo` | `bash -n` | success |
      | | `Self-test the predicate (bites on the real tree, mutated)` | success |
      | | `Guard — every referenced local script is tracked` | success |
      | `Demo wrapper smoke (infra/demo)` | `Public-data bundled seed is regenerable and not drifted` | success |
      | | `Public-data package tests (transform allowlist + leak controls)` | success |

      ⇒ 배선 사슬 네 단계가 **실제로** 이어졌다. 전체 체크 59개 중 SUCCESS 38 · SKIPPED 21 ·
      실패 0.

## 실행한 게이트

| 게이트 | 결과 |
|---|---|
| `scripts/check-package-script-targets.sh` | ✅ rc=0 — `PKG=16 TARGETS=10` · 위반 0건 |
| 같은 스크립트 `--self-test` | ✅ rc=0 — 대조군 2칸 · bite 2칸 |
| `bash -n scripts/check-package-script-targets.sh` | ✅ rc=0 |
| `check-message-backticks` (+ `--self-test`) | ✅ rc=0 / rc=0 |
| `check-index-queue-drift` · `check-task-id-collision` · `check-walkthrough-ledger-drift` | ✅ 전부 rc=0 |
| `node bin/build-bundled-snapshots.mjs --check` | ✅ rc=0 (수정 전 🔴 rc=1) |
| `node --test tests/public-data.test.mjs` | ✅ rc=0 — **pass 31 · fail 0** |
| 깨끗한 트리(`git archive HEAD`)에서 위 둘 | ✅ 둘 다 rc=0 |
| `ci.yml` YAML 파싱 + 배선 사슬 4단계 | ✅ 잡 56개 · 필터·출력·if·스텝 전부 확인 |

🔵 한 번 «실패» 로 보고된 것이 있었는데 **게이트가 아니라 내 명령이 틀린 것**이었다:
`node --test … | grep -E "^# (pass|fail)"` 의 종료코드는 `grep` 것이고, node 의 출력 접두사가
`#` 가 아니라 `ℹ` 라 매치가 0건이었다. 실제 테스트는 `rc=0`(31/31)이다. 파이프 끝의
종료코드를 보면 안 된다는, 이 저장소가 이름 붙인 함정을 그대로 밟았다.

🔴 **미측정**: 이 PR 이 만든 두 CI 잡이 실제로 도는지는 **PR 이 열려야** 확인된다(위 AC-5
마지막 칸). 그 확인 전까지 「배선했다」는 선언이지 측정이 아니다.

## CORRECTION — § 미측정이었던 「두 CI 추가가 실제로 도는가」를 쟀다 (2026-09-08 UTC)

이 티켓은 «PR 이 열려야 확인된다 … 그 확인 전까지 「배선했다」는 선언이지 측정이 아니다»
라고 적었다. 이제 쟀다 — 그리고 **잡의 결론이 아니라 스텝**을 읽었다.

| 추가 | 어디 | 실측 (impl PR `#3688`) |
|---|---|---|
| 새 잡 `package-script-targets` | 독립 잡 | **SUCCESS** — 「package.json scripts point at files that are in the repo」 |
| 스텝 「Public-data bundled seed is regenerable and not drifted」 | `Demo wrapper smoke (infra/demo)` 안 | **success** |
| 스텝 「Public-data package tests (transform allowlist + leak controls)」 | 같은 잡 | **success** |

🔵 **경로 게이트도 함께 확인했다**: 그 뒤 PR 4건(`#3689`·`#3691`·`#3692`·`#3693`)에서 새 잡은
`SKIPPED` 다. `package.json` 을 안 건드린 PR 이므로 **정확한 게이팅**이다 — 「안 돈다」가
아니라 「그 도착 경로가 아니다」.

🔴 그래서 이제 「배선했다」가 선언이 아니라 측정이다.
