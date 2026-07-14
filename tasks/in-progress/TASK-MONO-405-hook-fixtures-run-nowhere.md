# Task ID

TASK-MONO-405

# Title

**훅 픽스처 스위트를 아무도 돌리지 않는다** — 훅 한 줄을 고치면 CI 가 Java·프런트·Testcontainers **20여 잡**을 20분간 돌리는데, 그 중 **훅을 시험하는 잡은 0개**다. `MONO-402` 가 방금 심은 회귀 가드도 **아무도 안 본다**

# Status

in-progress

# Owner

monorepo

# Task Tags

- code
- test
- adr

---

# Dependency Markers

- **발굴**: `TASK-MONO-402` 종결 직후. **가드를 고치면서 그 가드를 지키는 가드를 안 붙였다**는 것을 깨달음.
- **선행 (머지됨)**: `TASK-MONO-402`(훅 배선 + HARDSTOP-05 `done/`), `TASK-MONO-396`(커맨드 정합).
- **연관**: `TASK-MONO-360`(가드 도달성 — *꺼진 잡의 skip 은 초록으로 보고된다*), `TASK-MONO-326`(CI DRY).

---

# Goal

**`.claude/hooks/__tests__/run-all.ps1` 을 CI 게이트로 만든다.** 그리고 훅 변경이 **훅과 무관한 20여 잡**을 끌고 오는 낭비를 함께 끊는다.

---

# 실측 (2026-07-14, `origin/main` `cb8d28983` — 착수 시 **다시 확인할 것**)

## 실측 1 — 픽스처 스위트는 CI 어디에도 없다

`run-all.ps1` **1행이 스스로 적어 두었다**:

```
# Diagnostic runner for the hook fixtures. Developer-run only — not CI-gated.
```

**`.github/` 전체에서 `run-all` · `__tests__` · `hooks/` 참조 = 0건.** (술어 도달성 확인: 같은 디렉터리에 `runs-on` 은 20+건 잡힌다 — **빈 결과가 부재임을 먼저 증명했다.**)

⇒ **훅 12개 · 픽스처 13개 · 단언 60여 개가 사람이 손으로 돌릴 때만 실행된다.**

## 실측 2 — 🔴 그래서 `MONO-402` 의 회귀 가드는 태어나자마자 눈이 없다

`MONO-402` 가 심은 픽스처 4개:

| 픽스처 | 지키는 성질 |
|---|---|
| `HARDSTOP-05 positive-2` | `done/` 임의 편집 차단 |
| `HARDSTOP-05 negative-3` | **close-chore 의 `review→done` 은 통과**(이게 깨지면 저장소의 모든 task close 가 막힌다) |
| `protect-main positive-6` | PowerShell 페이로드의 push 차단 |
| `protect-main positive-7` | `HEAD=main` 암묵 push 차단(2026-05-25 누출의 모양) |

**누가 정규식을 되돌리거나 matcher 를 다시 좁혀도 CI 는 초록이다.** *관측할 수 없는 성질은 아무도 지키고 있지 않은 성질* — `MONO-355`·`387`·`392` 가 세 번 만난 그 문장이 **이번엔 내가 방금 만든 가드에 적용된다.**

## 실측 3 — 🔴 비용은 이미 내고 있다. 커버리지만 0이다

`ci.yml:242` — `.claude/**` 가 **`libs` 필터**에 들어 있고, 그 필터는 주석대로 *"activates **ALL** downstream jobs (cross-project impact)"* 다.

**실측 (`MONO-402` impl PR #2545)**: `.claude/{settings.json,hooks/*}` + `tasks/*` **6개 파일**만 바꿨는데 —

- **22 SUCCESS / 9 SKIPPED**, Java 빌드 · 프런트 lint·unit·E2E · **Testcontainers 통합 7개 레인** 전부 기동, **~20분**
- **그 중 훅을 시험한 잡: 0개**

**⇒ 훅 변경은 CI 비용을 최대로 내면서 커버리지는 0이다.** 필터가 `.claude/` 에 **이미 닿고 있다** — 잘못된 잡을 깨울 뿐이다.

## 실측 4 — 픽스처는 Windows 전용이다 (이 task 의 유일한 설계 판단)

`_helpers.ps1:24`:

```powershell
$output = & cmd /c "type `"$tmp`" | powershell -NoProfile -ExecutionPolicy Bypass -File `"$hookPath`""
```

**`cmd` 와 `powershell`(Windows PowerShell 5.1)은 Linux 러너에 없다.** 그리고 이 저장소의 러너는 **전부 `ubuntu-latest`**(windows 잡 0개, `pwsh` 사용처 0곳).

**그리고 이건 픽스처의 결함이 아니다** — `settings.json` 이 훅을 **`powershell -NoProfile -File …`** 로 띄운다. **훅은 프로덕션에서 Windows PowerShell 로 실행된다.**

## 실측 5 — 사소한 것

`run-all.ps1:22–23` 이 `verify-worktree-isolation.ps1` 을 **두 번** 나열한다(실행 로그에 두 번 찍힌다). 무해하지만 거짓 신호다.

---

# Scope

**포함:**

1. `run-all.ps1` 을 실행하는 **CI 잡** 신설 + `run-all.ps1` 1행의 *"not CI-gated"* 주석 철회.
2. `paths-filter` 에 **`hooks` 필터** 신설(`.claude/hooks/**`, `.claude/settings.json`) → 이 잡의 게이트.
3. **훅-only 변경이 Java/FE/Testcontainers 20여 잡을 깨우지 않게** 한다(§ 결정 지점 D2).
4. `run-all.ps1` 의 중복 항목 제거(실측 5).

**제외:**

- **훅 로직 변경** — 이 task 는 **감시만** 붙인다. 픽스처가 RED 를 내면 **그건 별개 티켓**이다.
- **F3**(Edit/Write 훅의 셸 우회) — `MONO-402` 가 사람 결정으로 남긴 항목. 여기서 다루지 않는다.

---

# 🔴 결정 지점

## D1 — 어느 러너에서 돌릴 것인가 (**권고: `windows-latest`**)

| | (A) `windows-latest` — **권고** | (B) `ubuntu-latest` + `_helpers.ps1` 을 `pwsh` 로 재작성 |
|---|---|---|
| 시험하는 것 | **프로덕션이 실제로 쓰는 셸**(Windows PowerShell 5.1, `settings.json` 이 그렇게 띄운다) | pwsh(Core) — **프로덕션과 다른 셸** |
| 비용 | windows 러너 ~2배 단가, 스위트는 1분 미만 | 저렴 |
| 위험 | 없음 | **pwsh 에서 초록인데 Windows PowerShell 에서 깨지는 훅을 통과시킨다** |

**(A) 를 권고하는 이유**: 이 저장소는 *"내 노트북에서 보정한 임계가 러너에서 안 문다"* 를 한 티켓에서 **두 번** 겪었다(`MONO-397`). **다른 환경에서 초록인 가드는 가드가 아니다.** 훅이 Windows PowerShell 로 실행된다면 **거기서 시험해야 한다.** 스위트가 1분 미만이라 비용 논거가 약하다.

**⚠️ (B) 를 고르면 반드시 실측할 것**: 픽스처가 `pwsh` 에서 **정말로 통과하는지** — `ConvertTo-Json`·`-match`·here-string 동작이 5.1↔Core 에서 갈린다. **"통과할 것이다" 는 가설이다.**

## D2 — 훅 변경이 전체 스위트를 깨우는 것을 어떻게 끊을 것인가

`.claude/**` 는 지금 `libs` 필터에 있고 그건 **의도된 것**이다(`rules/`·`platform/` 변경은 실제로 전 프로젝트에 영향). 문제는 **`.claude/hooks/` 와 `.claude/settings.json` 은 그렇지 않다**는 것 — **에이전트 하네스일 뿐 빌드 산출물에 안 닿는다.**

- **(A) `libs` 필터에서 `.claude/hooks/**` · `.claude/settings.json` 을 빼고 새 `hooks` 필터로 옮긴다** — **권고**. 다만 `.claude/` 의 나머지(`skills/`·`agents/`·`commands/`·`config/`)는 **`libs` 에 남긴다**(규칙 라우팅이라 영향 반경이 다르다).
- **(B) 그대로 두고 `hooks` 잡만 추가** — 안전하지만 훅 한 줄에 20분을 계속 낸다.

> **⚠️ `paths-filter` negation 금지**(`MONO-074/075` quirk, `project_ci_path_filter_074_075_quirk`). **pure-positive 열거로만** 쓸 것.

---

# Acceptance Criteria

- **AC-1 (실측 재확인)** — 착수 시 § 실측 1·3·4 를 다시 측정한다. 특히 *"`.github/` 에 픽스처 참조 0건"* 은 **아는 답에 먼저 돌려 자기검증**할 것(같은 술어가 `runs-on` 은 20+건 잡아야 한다 — **빈 결과는 부재가 아니다**).
- **AC-2 (가드가 문다)** — **mutation 으로 증명.** `hardstop-detect.ps1` 의 정규식에서 `done` 을 빼면(= `MONO-402` 를 되돌리면) **새 CI 잡이 RED** 여야 한다. `settings.json` 에서 `PowerShell` 블록을 빼면 → 픽스처는 로직만 보므로 **안 물 수 있다**; 그렇다면 **그렇게 적어라**(배선은 픽스처가 못 잡는다 — `MONO-402` 가 이미 그렇게 기록했다).
- **AC-3 (오탐 0)** — 훅과 무관한 변경(예: `tasks/` 문서만)에서 이 잡이 **발화하지 않거나, 발화해도 통과**해야 한다. **첫날 RED 인 가드는 꺼지고, 꺼진 잡의 skip 은 초록으로 보고된다**(`MONO-360`).
- **AC-4 (도달성 — 이 task 의 요점)** — 새 잡이 **`.claude/hooks/**` 와 `.claude/settings.json` 변경에서 *실제로 실행*되는지** PR 에서 확인한다. **"잡을 추가했다" 는 "잡이 돈다" 가 아니다.** 잡이 SKIP 되면 이 task 는 아무것도 안 한 것이다.
- **AC-5 (비용 — D2)** — 훅-only PR 에서 Java/FE/Testcontainers 잡이 **더 이상 깨어나지 않음**을 확인(D2-A 선택 시). **실제 PR 의 check 목록으로 확인**할 것.
- **AC-6 (D1 실측)** — 고른 러너에서 **60여 단언이 전부 통과**함을 CI 로그로 확인. **(B) 선택 시 `pwsh` 호환성은 가설이 아니라 관측으로 확정.**
- **AC-7 (`run-all.ps1` 정직해진다)** — 1행의 *"Developer-run only — not CI-gated"* 를 사실에 맞게 고치고, 중복 항목(L22–23)을 제거한다.

---

# Related Specs

- `.claude/hooks/__tests__/run-all.ps1` — 대상(L1 주석 · L22–23 중복)
- `.claude/hooks/__tests__/_helpers.ps1` L24 — `cmd /c … | powershell` (**D1 의 근거**)
- `.claude/settings.json` — 훅을 `powershell -NoProfile -File` 로 띄운다(**D1-A 의 근거**)
- `.github/workflows/ci.yml` L47–61(필터 카탈로그 주석) · L231–244(필터 정의) · L242(`.claude/**` → `libs`)
- `docs/adr/` — 해당 없음(CI 배선)

# Related Contracts

**없다** — CI/에이전트 하네스. 런타임 표면 없음.

---

# Edge Cases

- **⚠️ `.claude/hooks/` 와 `settings.json` 은 classifier 하드블록**(auto mode) — `MONO-402` 실측. **`.github/workflows/` 는 자유.** 이 task 는 대부분 워크플로 작업이지만 `run-all.ps1`·`_helpers.ps1` 을 손대면 **auto mode 를 벗어나야 한다**(`Shift+Tab`). **우회 금지.**
- **`paths-filter` negation 금지** — pure-positive 열거만(`MONO-074/075`).
- **`libs` 필터에서 경로를 빼는 것은 행동 변경이다** — `.claude/skills|agents|commands|config` 는 **남겨야 한다**(규칙 라우팅 = 전 프로젝트 영향). **`hooks/` 와 `settings.json` 만 옮긴다.**
- **windows 러너는 이 저장소에 처음이다**(현재 전부 `ubuntu-latest`) — `actions/checkout` 외 의존 없음, 스위트는 PowerShell 내장만 쓴다. **단 CRLF**: `.gitattributes`/체크아웃 개행이 픽스처의 here-string 비교에 영향 줄 수 있다(`MONO-100` 의 CRLF/LF 픽스처가 이미 그 축을 안다).
- **`hardstop-body-canonical-sync.ps1` 은 `platform/hardstop-rules.md` 를 읽는다** — 러너 체크아웃에 그 파일이 있어야 한다(전체 체크아웃이면 무해).

# Failure Scenarios

- **잡을 추가했는데 SKIP 된다** → 아무것도 안 지킨다. **skip 은 초록으로 보고된다.** Guard: **AC-4**(실제 PR 에서 실행 확인).
- **`pwsh` 로 재작성했는데 5.1 과 동작이 갈린다** → **프로덕션에서 깨지는 훅이 CI 초록으로 통과한다.** Guard: D1 표 + AC-6.
- **첫날 RED** → 가드가 꺼진다. Guard: AC-3.
- **`libs` 필터에서 `.claude/**` 를 통째로 빼버린다** → `skills/`·`rules` 라우팅 변경이 프로젝트 잡을 안 깨운다(**커버리지 손실**). Guard: Edge Case 3 — **`hooks/` 와 `settings.json` 만** 옮긴다.
- **mutation 없이 "가드가 붙었다" 고 선언한다** → 이 저장소가 **세 번** 대가를 치른 실패. Guard: AC-2.

---

# Provenance

`TASK-MONO-402`(main 보호 훅 3개가 `matcher:"Bash"` 뿐이라 PowerShell 도구로 그냥 새어 나갔다)를 종결하고 **바로 다음 질문**에서 나왔다: *"내가 방금 심은 회귀 픽스처는 누가 돌리지?"*

**아무도 안 돌린다.**

**이 task 의 발견은 커버리지 부재가 아니라 그 비대칭이다**: 훅을 한 줄 고치면 CI 가 **Java 빌드 · 프런트 3종 · Testcontainers 7개 레인 · E2E** 를 20분간 돌린다(`MONO-402` PR 실측: 22 SUCCESS / 9 SKIPPED). **그 중 훅을 시험한 잡은 0개다.** 필터는 `.claude/` 에 **이미 닿고 있다** — 잘못된 잡을 깨울 뿐이다. **최대 비용, 0 커버리지.**

**그리고 이건 `MONO-360` 이 가르친 문장의 다음 장이다.** 거기서는 *가드가 물 기회를 얻는가*(paths-filter 도달성)를 물었고, `MONO-402` 에서는 *모든 문에서 물 기회를 얻는가*(matcher 배선)를 물었다. **여기서는 *가드를 지키는 가드가 있는가* 를 묻는다** — 픽스처는 훅의 성질을 못박지만, **픽스처 자신을 못박는 것은 아무것도 없다.**

분석=Opus 4.8 / 구현 권장=**Sonnet** (CI 배선. 다만 **D1 은 판단**이고, `run-all.ps1`·`_helpers.ps1` 편집은 **auto mode 탈출 필요** — 막히면 우회 말고 사용자에게.)
