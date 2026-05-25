# Task ID

TASK-MONO-136

# Title

`.claude/hooks/verify-worktree-isolation.ps1` — Edit/Write 가 linked worktree 밖으로 leak 시 차단 (2.8% leak rate fix)

# Status

review

# Owner

devops

# Task Tags

- infra
- hooks

---

# Goal

2026-05-25 PC-BE-005 agent (Opus, `isolation: "worktree"`) 가 worktree 안 144 tool_uses 중 **4건의 Write/Edit 호출이 worktree subfolder 대신 main repo path 로 자동 변환되는 leak** 을 일으켰다 (≈2.8% rate). agent 가 자체 복구 패턴 (`git diff` 추출 → `Move-Item` → `git apply` → `git restore`) 으로 사고 0 으로 종결했으나, 다음 dispatch 가 이 패턴을 모르면 main repo 가 의도외 변경된 상태에서 commit 될 위험 잔존.

본 task 는 PreToolUse Edit / Write hook 에 **linked worktree cwd 에서 file_path 가 그 worktree 의 subfolder 아닌 경로 → block** 검사를 추가하고 fixture 로 회귀를 막는다. defensive layer 만 — root cause (worktree path leak 의 race / Windows path resolver 추정) 조사는 별 task.

근거 메모: `project_2026_05_25_cross_project_sweep_and_recovery` § "NEW env hazard (env_git_worktree_verify_windows variant)" — 후속이 *"별 MONO task 후보 (defensive hook): PreToolUse Edit/Write hook 에서 file_path 가 cwd 기반 worktree subfolder 인지 검증, mismatch 시 block/warn"* 로 명시 작성됨.

---

# Scope

## In Scope

| 변경 | 대상 | 설명 |
|---|---|---|
| 신규 hook | `.claude/hooks/verify-worktree-isolation.ps1` | cwd 가 **linked** git worktree (Agent isolation) 일 때 `tool_input.file_path` 절대경로가 그 worktree 의 toplevel 안인지 검증. mismatch → `decision=block`. main worktree cwd / non-git cwd / detached HEAD / 상대경로 / cwd 비어있음 → silent allow (best-effort defensive) |
| 신규 fixture | `.claude/hooks/__tests__/verify-worktree-isolation.ps1` | 4 positive + 5 negative = 9 시나리오 (아래 참조) |
| `.claude/settings.json` | PreToolUse Edit + PreToolUse Write 매처 | 기존 3 hook (hardstop-detect / spec-check / rule-consistency-check) 와 동일 매처 array 에 `verify-worktree-isolation.ps1` entry 추가 |
| run-all 등록 | `.claude/hooks/__tests__/run-all.ps1` | fixture 추가 |
| README 갱신 | `.claude/hooks/README.md` | inventory 행 신규 + 본 hook 은 safety-rail (4-block remediation 면제) 명시 |

## Out of Scope

- Worktree path leak 의 **root cause** 조사 (controlled reproduction multi-run dispatch, Windows path resolver 분석) — 별 MONO task. 본 task 는 defensive layer 한정.
- `worktree.baseRef` 또는 Agent tool isolation 옵션 조정
- Bash tool 의 redirect (`> file`) write leak 검증 (Bash 는 hook 자체가 PreToolUse Bash → protect-main-branch / hardstop-detect 매처라 본 검사 외)
- protect-main-branch 와 통합 (별 hook 으로 분리 유지 — single-responsibility)

---

# Acceptance Criteria

- [ ] `verify-worktree-isolation.ps1` 가 다음을 모두 차단:
  1. cwd=linked worktree A, `tool_input.file_path` = main repo path → block
  2. cwd=linked worktree A, file_path = 다른 linked worktree B path → block
  3. cwd=linked worktree A, file_path = worktree 와 무관한 절대경로 (e.g. `C:\Users\...\elsewhere\foo.txt`) → block
  4. positive-1 의 실 사고 재현 (PC-BE-005 dispatch 의 leak shape: linked worktree cwd + main repo 의 use-case 파일) → block
- [ ] 다음은 차단되지 않음 (false-positive 0):
  - cwd=main worktree, file_path=main repo subfolder → allow
  - cwd=linked worktree A, file_path=cwd 의 subfolder → allow
  - cwd 가 git 디렉토리 아님 (e.g. `/tmp/...`) → allow
  - cwd 가 비어있거나 `tool_input.file_path` 가 비어있음 → allow
  - `tool_input.file_path` 가 상대경로 (Edit/Write 가 absolute 요구하지만 안전 차원) → allow
- [ ] fixture 9 시나리오 모두 PASS: `pwsh .claude/hooks/__tests__/run-all.ps1`
- [ ] `.claude/settings.json` PreToolUse Edit + Write 매처에 hook entry 추가
- [ ] README inventory 행 갱신 + safety-rail 면제 명시
- [ ] 사고 reference 를 fixture positive-4 comment 에 명시 (2026-05-25 PC-BE-005 leak, 144 tool_uses 중 4 leak ≈2.8%)

---

# Related Specs

- `.claude/hooks/README.md` § Inventory + Hook output format (본 hook 은 safety-rail 면제 명시 추가)
- 사고 reference: 2026-05-25 PC-BE-005 dispatch worktree leak (commit chain `395b3cd1` PR #826) — agent 자체 복구 패턴 5-step 정착
- 관련 메모: `project_2026_05_25_cross_project_sweep_and_recovery` § "NEW env hazard", `env_git_worktree_verify_windows` (별건이지만 cousin Windows hazard)

# Related Skills

- 없음

# Related Contracts

- 없음

---

# Implementation Notes

## linked worktree 판정

```
$gitDir       = git -C $cwd rev-parse --git-dir
$gitCommonDir = git -C $cwd rev-parse --git-common-dir
# main worktree:   $gitDir == $gitCommonDir
# linked worktree: $gitDir != $gitCommonDir (e.g. .git/worktrees/<id>)
```

이 비교가 가장 robust 한 linked worktree 판정. `worktree-agent-` branch 명 매칭은 fragile (Agent tool 명명이 바뀔 수 있음).

## toplevel containment 판정

```
$toplevel = git -C $cwd rev-parse --show-toplevel  # forward-slash
$normalizedFilePath = [System.IO.Path]::GetFullPath($file_path)
$normalizedToplevel = [System.IO.Path]::GetFullPath($toplevel) # backslash, drive letter resolved
# Windows case-insensitive
if (-not $normalizedFilePath.StartsWith($normalizedToplevel + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) `
    -and $normalizedFilePath -ne $normalizedToplevel) {
  → block
}
```

- `[System.IO.Path]::GetFullPath` 는 missing-file 에도 안전 (Resolve-Path 와 달리).
- `git rev-parse --show-toplevel` 의 forward-slash 출력은 `GetFullPath` 가 평준화.
- DirectorySeparatorChar 를 prefix 에 추가해 `C:\repo\a` 가 `C:\repo\abc` 의 prefix 로 false-positive 매칭되는 것 회피.

## skip 조건 (silent allow)

| 조건 | 이유 |
|---|---|
| `$cwd` 빈 문자열 | hook input 누락 — best-effort defensive |
| `$file_path` 빈 문자열 또는 절대경로 아님 | Edit/Write 가 absolute 요구하나 안전 차원 |
| `git -C $cwd rev-parse --git-dir` exit non-zero | cwd 가 git 디렉토리 아님 |
| `$gitDir -eq $gitCommonDir` | main worktree — 검사 대상 외 |
| `git symbolic-ref` 실패 | detached HEAD 등 — best-effort skip |

## hook output format

본 hook 은 **safety-rail** (Bash protect-main-branch 와 동급의 defensive layer) — 4-block remediation 면제. `decision=block` + 간단한 `reason` 문자열로 충분. README inventory 에 면제 명시.

block reason 예시:
```
Worktree isolation breach: cwd is a linked worktree (<toplevel>) but file_path <file_path> resolves outside it. Likely Agent dispatch path leak (PC-BE-005 2026-05-25 pattern); rebase Edit/Write target into worktree subfolder, or use `git diff` + Move-Item + git apply recovery if leak already happened.
```

## fixture payload 예시

positive-1 (linked worktree cwd + main repo file_path):

```powershell
# 임시 main repo + 그 위의 linked worktree 두 개 (A, B) 셋업
$mainRepo  = ...; & git init -q $mainRepo; & git commit --allow-empty -m init
$wtA       = ...; & git -C $mainRepo worktree add --detach $wtA HEAD
$wtB       = ...; & git -C $mainRepo worktree add --detach $wtB HEAD

$payload = @{
    tool_name  = 'Edit'
    tool_input = @{ file_path = (Join-Path $mainRepo 'projects/foo/Bar.java') }
    cwd        = $wtA
}
```

`_helpers.ps1` 의 `Invoke-Hook` + `Assert-Allowed` / `Assert-PlainBlock` 패턴 (protect-main-branch fixture 가 정의한 `Assert-PlainBlock` 동형).

---

# Edge Cases

- **새 파일 Write (file_path 미존재)**: `Resolve-Path` 는 실패하지만 `[System.IO.Path]::GetFullPath` 는 성공 — toplevel containment 검사는 raw path normalization 으로 충분.
- **Symlinked worktree path**: Windows 에서 worktree path 자체가 symlink 인 경우 거의 없으나, `GetFullPath` 는 symlink 를 resolve 하지 않음 → false-positive 가능. realpath 까지 따지지 않음 (best-effort, README 명시).
- **case-sensitive comparison**: Windows path 는 `OrdinalIgnoreCase` 비교. fixture 에 mixed-case 시나리오 1개 권장 (negative).
- **forward-slash vs backslash**: `git rev-parse --show-toplevel` 는 forward-slash 반환, `file_path` 는 backslash 일 수 있음 → `GetFullPath` 양쪽 적용으로 정규화.
- **non-Windows host** (CI Linux): 같은 hook 이 CI 에서 실행될 일 없음 (Claude Code session 한정 hook). fixture 는 Windows 가정.
- **Bash tool 의 redirect (`> file`) write**: 본 hook 미적용 (Bash matcher 아님). Out of Scope.
- **Empty file_path**: Edit tool 의 file_path 는 required 라 비어있을 일 없으나 hook 입력 robustness 차원에서 skip allow.

---

# Failure Scenarios

- **새 false-positive**: main worktree cwd 인데 file_path 가 main repo 밖 절대경로 (예: `C:\temp\foo.log`) 에 write — 본 hook 은 main worktree 면 skip 이므로 차단 안 함 (의도). fixture negative-3 으로 보장.
- **linked worktree cwd 에서 worktree 내부 write 가 차단**: containment 비교 버그 시 발생. fixture positive-1~4 + negative-2 로 동시 검증.
- **`git -C` 실패 시 spurious block**: git exit non-zero → silent allow. exit code 검사 누락이 가장 위험한 회귀 — fixture negative-3 (cwd=non-git) 로 보장.
- **prefix false-positive** (`C:\repo\a` vs `C:\repo\abc`): DirectorySeparatorChar suffix 검사로 회피. fixture negative 1개 권장.

---

# Test Requirements

- `pwsh .claude/hooks/__tests__/run-all.ps1` 전 fixture PASS (기존 28 assert + 신규 9 = 37)
- 신규 fixture 9 시나리오 (4 positive + 5 negative) PASS
- (선택) 수동 회귀: 임시 worktree 두 개 셋업 + raw hook 직접 호출 → block / allow 응답 확인

---

# Definition of Done

- [ ] `verify-worktree-isolation.ps1` 작성 완료
- [ ] fixture 작성 + run-all 등록
- [ ] `.claude/settings.json` PreToolUse Edit + Write 매처에 hook entry 추가
- [ ] README inventory 갱신 (safety-rail 면제 명시)
- [ ] 전 fixture PASS 확인
- [ ] commit + push (branch `task/mono-136-worktree-isolation-hook` — `main`/`master` substring 회피 확인)
- [ ] PR open (사용자 요청 시)
- [ ] Ready for review
