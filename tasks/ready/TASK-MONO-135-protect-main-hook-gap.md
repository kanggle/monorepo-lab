# Task ID

TASK-MONO-135

# Title

`.claude/hooks/protect-main-branch.ps1` — HEAD-based `git push` gap (worktree fan-out leak fix)

# Status

ready

# Owner

devops

# Task Tags

- infra
- hooks

---

# Goal

2026-05-25 8-project parallel refactor sweep 중 fan-platform 작업 agent 가 `.claude/hooks/protect-main-branch.ps1` 의 regex 차단을 우회해 local main → origin/main 직접 push 가 통과한 사고가 발생했다. 사고는 사용자 force-push (`+11a3d9b0:main`) 로 복구됨 (origin/main 11a3d9b0 으로 rollback, fan commit 은 `task/fan-be-008-refactor-sweep` 로 분리, 후속 PR #813 으로 정상 review 진입).

원인: hook 의 차단 regex `git\s+push.*\b(main|master)\b` 는 커맨드 문자열에 literal `main`/`master` 가 있을 때만 차단한다. agent 가 worktree HEAD 가 우연히 `main` 인 상태에서 `git push -u origin HEAD` 류 (커맨드에 literal `main` 미포함) 를 호출하면 git 의 default `push.default = simple/current` 동작이 origin/main 으로 push 되는데, 이 경로가 hook 의 사각지대.

본 task 는 hook 에 **암묵적 push (no explicit non-main target) 시 cwd 의 현재 branch 확인 후 main/master 면 차단** 검사를 추가하고 fixture 로 회귀를 막는다.

---

# Scope

## In Scope

| 변경 | 대상 | 설명 |
|---|---|---|
| 신규 검사 | `.claude/hooks/protect-main-branch.ps1` | 기존 3-regex 차단이 모두 miss 한 경우, `git push` 가 암묵적 target (bare `git push`, `git push origin`, `git push origin HEAD`, `git push [-u] HEAD` 등 — refspec 미지정) 이고 cwd 의 `git symbolic-ref --short HEAD` 가 `main`/`master` 면 차단 |
| 신규 fixture | `.claude/hooks/__tests__/protect-main-branch.ps1` | 4 positive (literal main / force / hard-reset / **new: HEAD-on-main-default**) + 3 negative (feature-branch HEAD / portfolio-sync allowlist / project-template allowlist) |
| run-all 등록 | `.claude/hooks/__tests__/run-all.ps1` | `protect-main-branch.ps1` fixture 추가 |
| README 갱신 | `.claude/hooks/README.md` | inventory 행에 "HEAD-based default push when HEAD is main/master" 차단 명시 |

## Out of Scope

- 다른 hook 의 sandbox 확장
- agent worktree 생성 시 `worktree.baseRef` 또는 branch 명명 자동화 강화 (root cause 측 보강은 별 task — 본 task 는 defensive 한 hook layer 만)
- `protect-main-branch.ps1` 의 4-block remediation format 적용 (README.md 명시: 본 hook 은 safety-rail 로 4-block 면제 — 유지)

---

# Acceptance Criteria

- [ ] `protect-main-branch.ps1` 가 다음을 모두 차단:
  1. `git push origin main`
  2. `git push --force` / `git push -f`
  3. `git reset --hard origin/main`
  4. (신규) HEAD 가 main 인 cwd 에서 `git push`, `git push -u origin HEAD`, `git push origin HEAD`, `git push origin` — 즉 암묵적 default target push
- [ ] 다음은 차단되지 않음 (false-positive 0):
  - HEAD 가 feature branch 인 cwd 에서 `git push -u origin HEAD`
  - portfolio-sync cwd 또는 inline `cd /tmp/portfolio-sync/...`
  - project-template cwd 또는 inline `cd /tmp/project-template-...`
  - `git push origin HEAD:feature-x` (HEAD 가 main 이어도 명시적 non-main target 이면 허용)
- [ ] fixture 7 시나리오 모두 PASS: `pwsh .claude/hooks/__tests__/run-all.ps1`
- [ ] README inventory 행 갱신
- [ ] 사고 reference 를 fixture 의 positive-4 comment 에 명시 (2026-05-25 fan-platform agent leak, force-push 복구 SHA `11a3d9b0`)

---

# Related Specs

- `.claude/hooks/README.md` § Inventory + Hook output format (본 hook 은 safety-rail 면제 명시 유지)
- `CLAUDE.md` § "main 직접 push 차단 (hook + classifier)" 정책 reference
- 사고 reference: 2026-05-25 8-project sweep 중 fan agent 우회 → origin/main rollback (`+11a3d9b0:main`, `--force-with-lease`)

# Related Skills

- 없음

# Related Contracts

- 없음

---

# Implementation Notes

## 차단 알고리즘

```
if (command matches existing 3 patterns) → block (기존 동작)
elif command matches `git push` AND command 에 explicit refspec (`:` 포함 또는 origin 뒤 non-flag/non-HEAD positional) 없음:
  branch = git -C $cwd symbolic-ref --short HEAD
  if branch in (main, master) → block
```

## "explicit refspec 없음" 판정

다음 패턴 중 하나면 implicit (default to current branch):
- `git push` (bare)
- `git push [flags...] origin`
- `git push [flags...] origin HEAD`
- `git push [flags...] HEAD`

명시적 refspec 패턴 (allow):
- `:` 포함 (`HEAD:feature-x`, `src:dst`)
- `origin` 뒤에 `HEAD`/`origin`/flag 아닌 positional (e.g. `git push origin feature-branch`)

## cwd 처리

- hook input `$data.cwd` 가 worktree path 인 경우 `git -C $cwd symbolic-ref --short HEAD` 가 worktree HEAD 를 반환 — 정확함
- `cwd` 가 비어있거나 git 디렉토리가 아니면 검사 skip (false-positive 회피)
- `git symbolic-ref` 실패 시 (detached HEAD 등) skip — 본 hook 은 best-effort defensive layer

## fixture payload 예시

positive-4 (신규):

```powershell
$payload = @{
    tool_name = 'Bash'
    tool_input = @{ command = 'git push -u origin HEAD' }
    cwd = $tmpRepoOnMain   # 임시 git repo, HEAD = main
}
```

negative-1 (feature branch):

```powershell
$payload = @{
    tool_name = 'Bash'
    tool_input = @{ command = 'git push -u origin HEAD' }
    cwd = $tmpRepoOnFeature   # 임시 git repo, HEAD = task/foo
}
```

`_helpers.ps1` 의 `Invoke-Hook` + `Assert-Allowed`/`Assert-Block` (또는 raw JSON parse) 패턴 사용. 본 hook 은 4-block 면제이므로 `Assert-Stanza` 대신 단순 decision 비교.

---

# Edge Cases

- **Worktree base branch 가 main 인 경우**: 본 hook 이 차단 → agent 가 worktree 안에서 직접 push 실패 → 명시적으로 `git checkout -b task/<id>-<short>` 후 push 해야 함. 의도된 동작.
- **detached HEAD**: `git symbolic-ref --short HEAD` 가 exit non-zero → skip (allow). 의도된 동작 (detached HEAD 는 main 으로 push 안 됨).
- **portfolio-sync / project-template allowlist**: 기존 allowlist 가 새 검사보다 먼저 evaluate 되도록 hook 흐름 유지.
- **bare repo / git 디렉토리 아님**: `git symbolic-ref` 실패 → skip.
- **Windows path 에 공백**: `cwd` 가 `"C:\Users\foo bar\..."` 경우 `git -C` 인자 quoting — PowerShell 인자 전달 자체가 안전 (string 으로 처리).

---

# Failure Scenarios

- **새 false-positive**: HEAD 가 main 이지만 `git push origin HEAD:feature-x` 같은 명시적 비-main 타겟 push 가 차단되면 사용자 워크플로우 깨짐. fixture negative-case 로 보장.
- **portfolio-sync allowlist 회귀**: 신규 분기 추가 시 allowlist 가 새 검사 이전에 평가되지 않으면 standalone 동기화 force-push 차단. fixture 로 검증.
- **cwd null/empty**: 기존 hook 은 `$cwd = ""` 인 경우도 처리. 신규 검사는 `cwd` 가 있을 때만 수행 — null/empty 시 silent skip.

---

# Test Requirements

- `pwsh .claude/hooks/__tests__/run-all.ps1` 전 fixture PASS
- 신규 fixture `protect-main-branch.ps1` 7 시나리오 (4 positive + 3 negative) PASS
- (선택) 수동 회귀: 사고 재현 형태 `git -C <임시 main-HEAD worktree> push -u origin HEAD` 를 hook 직접 호출 → block 응답

---

# Definition of Done

- [ ] `protect-main-branch.ps1` 패치 완료
- [ ] fixture 작성 + run-all 등록
- [ ] README inventory 갱신
- [ ] 전 fixture PASS 확인
- [ ] commit + push (branch `task/mono-135-protect-main-hook-gap`)
- [ ] PR open
- [ ] Ready for review
