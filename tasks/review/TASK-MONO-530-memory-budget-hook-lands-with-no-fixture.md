# Task ID

TASK-MONO-530

# Title

`memory-budget-check.ps1` 훅이 **fixture 없이** 랜딩한다 — `hook-fixtures` 잡은 초록인데 이 훅을 한 줄도 안 걸친다

# Status

review

# Owner

monorepo

# Task Tags

- tooling
- ci

---

# 배경 — `TASK-MONO-529` 가 밀어낸 절반

`TASK-MONO-529` 는 `/audit-memory` 에 Phase 2-6(인덱스 예산)을 넣으면서 훅을 **명시적으로
Out of Scope 로 선언**했다:

> 🔴 `.claude/hooks/` · `.claude/settings.json` — **분류기 하드블록**. 자동 강제를 원하면
> 패치를 사람에게 넘겨야 하며 이 티켓 밖이다

그 패치가 실제로 넘어왔다 — 로컬 커밋 `7ee64d87d`(`chore(hooks): add MEMORY.md index budget
Stop hook`)가 `.claude/hooks/memory-budget-check.ps1`(108줄) + `settings.json` Stop 배선
4줄을 추가한다. 이 티켓은 **그 커밋을 머지 가능한 상태로 만드는 일**이다.

## 훅 자체는 문제가 없다 (2026-08-14 실측)

| 축 | 결과 |
|---|---|
| 포인터 계수 술어 | `\]\([a-z0-9_]+\.md\)` — `audit-memory.md` Phase 2-6 스니펫(L73·75·117·119)과 **문자 그대로 동일** |
| 라이브 실행 | `exit 0`, 출력 없음 (실측 22,369B / **170 포인터**, 정원 180 ⇒ WITHIN) |
| 루프 안전 | 하루 1회 마커 디바운스 있음 |
| 후보 선정 | 허용리스트 `^[CDFG][\.\d]` — 블록리스트가 아니다 |

훅과 명령이 서로 다른 숫자를 내는 것이 이 축의 전형적 고장인데(같은 것을 두 곳에서 재면
반드시 갈라진다), 여기선 술어가 같아서 그럴 수 없다.

## 🔴 막는 것 — fixture 가 없고, 그래서 CI 가 **아무것도 안 본다**

```
.claude/hooks/__tests__/run-all.ps1  L22-33  = 하드코딩된 12개 목록 (열거 아님)
  hardstop-01/03/05/09(x2)/10(x2) · hardstop-body-canonical-sync ·
  format-alignment · protect-main-branch · verify-worktree-isolation ·
  warn-shared-checkout-switch
                                              ← memory-budget-check 없음
```

`hook-fixtures` 잡은 `.claude/hooks/**` + `.claude/settings.json` 경로 필터로 게이트돼
있으므로 이 훅이 랜딩하면 **잡은 돈다**. 그런데 도는 것은 저 12개고, 새 훅은 한 줄도 안
걸친다 ⇒ **초록인데 커버리지 0.** `TASK-MONO-405` 가 이 러너를 CI 에 물린 이유가 정확히
*"MONO-402 의 회귀 fixture 가 아무것도 지키고 있지 않았다"* 였는데, 같은 모양이 새 훅에서
반복된다.

## 🔴🔴 그리고 block 분기는 **한 번도 발화한 적이 없다**

현재 인덱스가 170/180 이라 훅은 L66 에서 나간다. 즉 지금까지 실행된 것은 WITHIN 분기뿐이고
`[VIOLATION]` JSON · 후보 섹션 계산 · 마커 기록은 **전부 미발화**다. 훅 자신의 L69-73 주석이
그 코드가 이미 한 번 틀렸다고 적고 있다:

> 처음엔 "함정 섹션을 제외" 로 짰다가 `^(A2|B)[\.\s]` 가 `B5.` 를 못 잡아 **함정 5개 섹션이
> 전부 후보로 새어 나갔다**

⇒ 저자 본인이 이 분기가 미끄럽다고 기록해 뒀는데 그 분기를 지키는 것이 아무것도 없다.
이건 `TASK-MONO-529` 가 자기 AC-3 에 쓴 문장의 재현이다 — *"AC-3 없이 닫으면 술어만 있고
발화하지 않는 단계가 남는다."* 명령 쪽은 양쪽 분기를 확인했고, 훅 쪽은 안 했다.

---

# Goal

`memory-budget-check.ps1` 이 `run-all.ps1` 에 물린 fixture 로 **양쪽 분기 전부** 커버되고,
`hook-fixtures` 잡의 초록이 "이 훅을 봤다" 를 의미하게 된다.

---

# Scope

## In Scope

- `.claude/hooks/__tests__/memory-budget-check.ps1` 신설
- `.claude/hooks/__tests__/run-all.ps1` L22-33 목록에 그 파일명 추가 (**이 한 줄이 없으면
  fixture 를 써도 안 돌아간다** — 러너는 열거가 아니라 하드코딩이다)
- 로컬 커밋 `7ee64d87d` 를 `main` 에서 떼어 브랜치로 옮기고 위 둘과 함께 한 PR 로 랜딩

## Out of Scope

- 훅의 **동작 변경** — 실측상 술어가 옳다. 이 티켓은 계측기를 붙이는 일이지 훅을 고치는
  일이 아니다. AC-0 이 반증하면 그때 별도로 연다
- 정원 상수(180) 조정 — `MEMORY.md` 헤더와 `audit-memory.md` 가 소유한다
- `/audit-memory` Phase 2-6 자체 — `TASK-MONO-529` 에서 닫혔다

---

# Acceptance Criteria

> **✅ AC-0~AC-4 전부 완료 2026-08-14.** AC-4 는 CI 가 나온 뒤에 닫았다 — 그 전에 체크하면
> 그건 측정이 아니라 선언이다. 실행 기록은 각 AC 아래 `→` 줄. 훅 자체는 **한 바이트도 안
> 고쳤다**(`git diff` 로 확인) — 이 티켓은 계측기를 붙이는 일이었고 그대로 끝났다.

- [x] **AC-0 (재측정)** — 착수 시점에 다시 잰다: ① `run-all.ps1` 목록이 여전히 하드코딩인가
      (열거로 바뀌었으면 이 티켓의 절반이 사라진다) ② 훅의 포인터 술어가 여전히
      `audit-memory.md` 스니펫과 같은가 ③ 현재 포인터/정원. 🔴 **표의 숫자를 인용하지 말고
      다시 돌려라** — MONO-529 가 같은 자리에서 자기 상수(120B)를 착수 당일 반증했다(→130B)
      → **전제 3개 전부 유지.** ① `run-all.ps1` 은 여전히 하드코딩(`Get-ChildItem` **0건**,
      목록 12개) ② 술어 `\]\([a-z0-9_]+\.md\)` 가 훅 **2회** · `audit-memory.md` **4회**로
      문자 그대로 같다 ③ **22,369B / 170 포인터**, 정원 180 ⇒ WITHIN
- [x] **AC-1 (fixture)** — 최소 6칸. WITHIN 무출력 · 메모리 디렉터리 부재 시 무출력 ·
      OVER 시 `decision=block` + `MEMORY-BUDGET-01` 4-block 스탠자 · 스탠자가 **포인터 수**를
      보고할 것(바이트만이 아니라) · **허용리스트: C/F 는 후보에 있고 A2/B5 는 없을 것** ·
      디바운스(같은 날 2회차는 조용)
      → **6칸 신설, 전부 PASS.** 🔴 `_helpers.ps1` 의 `Invoke-Hook` 은 **쓸 수 없었다** —
      훅이 메모리 경로를 `(Get-Location).Path` 로 유도하는데 그 헬퍼는 자식의 작업 디렉터리를
      정할 수단이 없다(그리고 `Push-Location` 은 이 호스트에서 프로세스 cwd 와 갈린다).
      `cmd /c "cd /d <dir> && …"` 로 프로세스 cwd 를 명시하는 로컬 invoker 를 뒀다.
      🔵 `$Home` 은 읽기 전용 자동 변수라 파라미터명을 `$HomeDir` 로 바꿔야 했다(실측 1회 실패)
- [x] **AC-2 (배선)** — `run-all.ps1` 목록에 추가하고, 러너를 통째로 돌려 새 칸이 실제로
      실행됨을 확인한다. 🔴 fixture 파일만 추가하고 목록을 빼먹으면 **정확히 이 티켓이
      고치려는 상태**가 재생산된다
      → 목록에 추가 후 러너 전체 실행: `--- Running memory-budget-check.ps1 ---` 출력 +
      6칸 PASS + `All fixtures PASS`, 러너 **exit 0**
- [x] **AC-3 (무는지 확인)** — 훅을 **돌연변이**시켜 fixture 가 RED 가 되는지 본다. 최소 4종:
      허용리스트를 넓힘 · 정원을 도달 불가하게 올림 · 마커 기록 제거 · 포인터 정규식 무력화.
      전부 복원 후 GREEN 확인. 🔴 **판정은 러너의 종료 상태로 하고 stdout 정규식으로 하지
      말 것**(§ Failure Scenarios 2번)
      → **4/4 RED**(M1 허용리스트 `^[CDFG]`→`^[A-G]` · M2 정원 180→9999 · M3 마커 기록 제거 ·
      M4 포인터 정규식 무력화), 복원 후 GREEN, 훅 **바이트 동일** 확인.
      🔴 **하네스가 한 번 훅을 오염시켰다** — 러너 전체(5회 × ~25초)를 한 호출에 돌렸다가
      2분 타임아웃에 걸렸고, 프로세스가 죽으면서 `finally` 복원이 **실행되지 않아** 훅이 M1
      상태로 남았다. `git status` 로 발견해 커밋된 blob 에서 복원했다. ⇒ 돌연변이 하네스는
      **1회 호출 = 1 돌연변이 + 즉시 복원**으로 쪼갰다. 타임아웃이 있는 실행기에서 `finally`
      는 복원을 보장하지 않는다
- [x] **AC-4 (CI)** — PR 에서 `Hook fixtures (Windows PowerShell)` 잡이 **SKIPPED 가 아니라
      실행**되고 통과함을 확인한다. 스킵된 잡은 이 변경에 대해 아무 말도 하지 않는다
      → **완료 — 그리고 첫 실행에서 진짜 결함을 잡았다.** 1회차 **FAIL**: 이 fixture 가 BOM 없는 UTF-8 이라 windows-latest 의 PowerShell 5.1 이 ANSI 코드페이지로 파싱해 `전략` 이 `ì „ëžµ` 로 깨졌고 **파서가 죽었다**(`Missing ')' in method call`). 단언 실패가 아니라 파스 실패다. 전수 결과 **훅 스크립트 27개 중 26개가 BOM 보유**, 없는 둘이 이 fixture 와 **훅 자신**(non-ASCII 3,201B)이었다 → 둘 다 BOM 추가(훅은 본문 sha256 바이트 동일). 2회차 **PASS** — 러너 로그에 `--- Running memory-budget-check.ps1 ---` + 6칸 PASS + `All fixtures PASS` 확인(= 잡의 초록이 아니라 **칸이 실제로 돌았다**는 증거). 🔴 ⇒ 로컬 전건 초록은 이것을 볼 수 없었다 — 이 호스트의 코드페이지가 받아줬기 때문이고, 그게 AC-4 가 존재하는 이유다. 🔵 대조군은 이미 있다 — `TASK-MONO-530` 티켓만 담은 PR
      [#3320](https://github.com/kanggle/monorepo-lab/pull/3320) 에서 이 잡은 **SKIPPED** 였고
      (`tasks/` 만 건드렸으므로 정상), 이 PR 은 `.claude/hooks/**` 를 건드리므로 **실행돼야**
      한다. 두 PR 이 경로 필터의 양쪽 칸을 이룬다

---

# Related Specs

- `.claude/hooks/__tests__/run-all.ps1` — 러너(하드코딩 목록)
- `.claude/hooks/__tests__/_helpers.ps1` — `Invoke-Hook` / `Assert-Stanza` / `Assert-Allowed`
- `.claude/commands/audit-memory.md` § Phase 2-6 — 같은 술어의 다른 집
- `TASK-MONO-529` (done) — 이 훅을 Out of Scope 로 밀어낸 티켓
- `TASK-MONO-405` — 이 러너를 CI 에 물린 티켓(같은 실패 모드의 선례)
- `platform/lint-remediation-message-standard.md` — 4-block 스탠자 형식

# Related Contracts

- 없음 (훅/테스트 하네스, API·이벤트 계약 무관)

# Edge Cases

- **훅이 읽는 파일이 리포 밖이다** (`~/.claude/projects/<slug>/memory/MEMORY.md`) ⇒ fixture 는
  가짜 `$env:USERPROFILE` + 가짜 cwd 를 만들어 주입해야 한다. 실제 사용자 메모리를 건드리면
  안 된다
- 🔴 **`_helpers.ps1` 의 `Invoke-Hook` 을 그대로 쓸 수 없다** — 훅이 메모리 경로를
  `(Get-Location).Path` 에서 유도하는데 `Invoke-Hook` 은 자식의 작업 디렉터리를 정할 수단이
  없다. `Push-Location` 도 대체재가 아니다(이 호스트에서 PowerShell 의 location 과 **프로세스
  cwd** 가 갈린다 — `env_powershell_dotnet_io_ignores_setlocation`). `cmd /c "cd /d <dir> && …"`
  로 프로세스 cwd 를 명시할 것
- 🔴 `$Home` 은 PowerShell **읽기 전용 자동 변수**다 — fixture 파라미터 이름으로 쓰면
  `Cannot overwrite variable Home` 로 죽는다. `$HomeDir` 같은 다른 이름을 쓸 것
- 훅은 OVER 분기에서만 마커를 쓴다 ⇒ WITHIN 칸은 "마커가 **없을** 것"까지 단언해야 의미가 있다
- Windows PowerShell 5.1 로 돌린다(pwsh 아님) — `settings.json` 이 훅을 그렇게 띄운다

# Failure Scenarios

- **fixture 만 추가하고 `run-all.ps1` 목록을 빼먹는다** → 파일은 리포에 있고 CI 는 초록이며
  아무것도 실행되지 않는다. 이 티켓이 존재하는 이유 그 자체가 재생산된다
- 🔴🔴 **AC-3 의 판정을 stdout 정규식으로 한다** → 2026-08-14 에 실제로 밟았다. 정원을
  도달 불가하게 올린 돌연변이에서 fixture 는 **정상적으로 RED 였는데**, 채점용 정규식이
  그 에러 문구(`Cannot bind argument to parameter 'Output' because it is an empty string`)를
  포함하지 않아 **"안 물었다"로 오채점**됐다. 무는 것을 안 문다고 읽는 쪽이 더 위험하다 —
  멀쩡한 가드를 고치러 들어간다. 판정은 러너의 **종료 상태**로 한다
- **block 분기의 실패 메시지가 훅이 아니라 fixture 를 지목한다** → 위와 같은 뿌리. 빈 출력을
  `Assert-Stanza` 에 그대로 넘기면 파라미터 바인딩 에러가 나서 *"이 fixture 가 깨졌다"* 로
  읽힌다. 넘기기 전에 `expected BLOCK, got silence` 로 **먼저 이름 붙일 것**
- **함정 섹션이 후보로 새는 것을 안 잰다** → 훅의 가장 미끄러운 코드가 무방비로 남는다.
  크기는 줄지만 안 로드된 함정은 다시 밟히므로 이건 개선이 아니라 사고다
- **`hook-fixtures` 잡이 SKIPPED 인 채로 머지한다** → 경로 필터가 걸려 있으므로 훅을
  건드리면 돌아야 정상이다. 스킵됐다면 필터가 잘못된 것이고 그것도 이 티켓의 발견이다

# Definition of Done

- [x] AC-0 재측정 기록 (훅 술어 · 러너 형태 · 현재 포인터/정원)
- [x] fixture 6칸 + `run-all.ps1` 배선
- [x] AC-3 돌연변이 4종 RED → 복원 GREEN, 판정은 종료 상태로
- [x] PR 에서 `Hook fixtures` 잡 실행(SKIPPED 아님) + 통과
- [x] Ready for review
