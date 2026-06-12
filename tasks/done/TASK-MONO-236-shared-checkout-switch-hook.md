# Task ID

TASK-MONO-236

# Title

Add a `PreToolUse[Bash]` advisory hook (`warn-shared-checkout-switch.ps1`) that surfaces an `ask` confirmation when a HEAD-moving `git checkout`/`git switch` runs in the MAIN checkout with a dirty working tree — the enforcement counterpart to the TASK-MONO-235 "Concurrent-session worktree isolation" rule.

# Status

done

# Owner

backend

# Task Tags

- hooks
- monorepo
- governance

---

# Dependency Markers

- **선행**: `TASK-MONO-235` (done, PR #1406 + close #1408) promoted the "Concurrent-session worktree isolation" rule to `CLAUDE.md § Cross-Project Changes`. That task noted hook hardening as an explicit out-of-scope follow-up. This is that follow-up.
- **source incident**: 2026-06-13 — pc-fe-070 session ran `git checkout` in the shared main `monorepo-lab` checkout while an increment-C session held uncommitted WIP there; the checkout carried increment-C's WIP onto the pc-fe-070 branch (recovered by explicit-path commit + HEAD restore). Memory: `env_concurrent_git_branch_switch_hazard`.
- **⚠️ classifier constraint**: `.claude/` (hooks/agents/commands) edit+commit is **hard-blocked by the auto-mode classifier even with explicit approval** (memory `env_classifier_claude_self_mod_block`). Therefore **the hook + settings + README changes in §Scope MUST be applied and committed by the human operator** — the AI agent authored this task file (which lives outside `.claude/`, so it is committable) but cannot land the `.claude/` payload. The full ready-to-apply patch is embedded verbatim below.
- **model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (single PowerShell hook + JSON registration; mechanical, well-specified).

---

# Goal

Make the TASK-MONO-235 worktree-isolation rule enforceable (not just documented). A `PreToolUse[Bash]` hook intercepts the precise footgun — a HEAD-moving `git checkout`/`git switch` in the MAIN checkout (`git-dir == git-common-dir`) while the working tree is **dirty** — and emits a non-blocking `ask` confirmation. `ask` (not `block`) because solo work in the main checkout is legitimate; the dirty-tree gate suppresses the common clean-start `checkout -b` case, keeping false-positives near zero.

# Scope

## In scope (`.claude/` — human-applied per classifier constraint)

### (a) New hook `.claude/hooks/warn-shared-checkout-switch.ps1`

```powershell
# PreToolUse[Bash] advisory guard for the concurrent-session shared-checkout
# hazard (CLAUDE.md > Cross-Project Changes > "Concurrent-session worktree
# isolation"; project memory env_concurrent_git_branch_switch_hazard;
# TASK-MONO-235 promoted the rule, this hook is its TASK-MONO-236 enforcement).
#
# When a `git checkout <branch>` / `git switch <branch>` that MOVES HEAD runs
# in the MAIN checkout (git-dir == git-common-dir) while the working tree is
# DIRTY, the uncommitted changes are carried onto the target branch. If a
# concurrent session shares that checkout, its WIP is stranded on the wrong
# branch and its next commit lands there (2026-06-13 pc-fe-070 x increment-C
# incident). This surfaces an `ask` confirmation so the operator can re-route
# the work into a dedicated `git worktree add` directory.
#
# `ask` (not `block`): solo work in the main checkout is legitimate; this is a
# safety prompt, not a prohibition. The dirty-tree gate suppresses the common
# clean-start case (`git checkout -b task/...` on a clean main checkout is
# allowed silently).
#
# Safety-rail (best-effort) — exempt from the platform 4-block remediation
# standard (same policy as protect-main-branch.ps1 / verify-worktree-isolation.ps1;
# see .claude/hooks/README.md).
#
# Silent-allow cases:
#   - input parse failure / no command / no cwd
#   - no HEAD-moving `git checkout|switch` segment in the command
#   - pathspec/file checkout (`git checkout -- <path>`, `git checkout <ref> -- <path>`,
#     or `git checkout <name>` where <name> is not a resolvable branch/commit)
#   - command routes git elsewhere via `git -C <dir>` (cwd heuristic invalid)
#   - cwd not a git dir / is a LINKED worktree (the sanctioned place to work)
#   - clean working tree (the switch carries nothing)
#   - switching to the SAME branch already checked out (no-op)

$ErrorActionPreference = 'Stop'

$reader    = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$inputJson = $reader.ReadToEnd()

function Exit-Allow { exit 0 }
function Exit-Ask {
    param([Parameter(Mandatory)][string]$Reason)
    $payload = @{ decision = 'ask'; reason = $Reason } | ConvertTo-Json -Compress
    Write-Output $payload
    exit 0
}

try {
    $data = $inputJson | ConvertFrom-Json
} catch { Exit-Allow }

$command = ''
$cwd     = ''
if ($data.tool_input -and $data.tool_input.command) { $command = [string]$data.tool_input.command }
if ($data.cwd)                                       { $cwd     = [string]$data.cwd }

if (-not $command -or -not $cwd) { Exit-Allow }
if (-not (Test-Path -LiteralPath $cwd -PathType Container)) { Exit-Allow }

function Invoke-Git {
    param([Parameter(Mandatory)][string[]]$Args, [Parameter(Mandatory)][string]$WorkDir)
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName               = 'git'
    $psi.WorkingDirectory       = $WorkDir
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError  = $true
    $psi.UseShellExecute        = $false
    $psi.CreateNoWindow         = $true
    $psi.Arguments = (@('-C', "`"$WorkDir`"") + $Args) -join ' '
    try { $p = [System.Diagnostics.Process]::Start($psi) } catch { return $null }
    $stdout = $p.StandardOutput.ReadToEnd()
    [void]$p.StandardError.ReadToEnd()
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) { return $null }
    if ([string]::IsNullOrEmpty($stdout)) { return '' }
    return ($stdout -replace "`r`n", "`n").TrimEnd("`n")
}

# --- 1. Find a HEAD-moving checkout/switch segment ---------------------------
$sub       = $null
$refToken  = $null
$newBranch = $false
foreach ($seg in ($command -split '\s*(?:&&|\|\||;|\|)\s*')) {
    if ($seg -match '\bgit\s+-C\b')                   { continue }   # routed elsewhere
    if ($seg -notmatch '\bgit\s+(checkout|switch)\b') { continue }
    $candidateSub = $matches[1]
    # pathspec / file-restore checkout -> not a HEAD move
    if ($candidateSub -eq 'checkout' -and $seg -match '\s--(\s|$)') { continue }
    $candidateNew = [bool]($seg -match '\s(-b|-B|-c|-C)(\s|$)')
    $candidateRef = $null
    if ($seg -match '\bgit\s+(?:checkout|switch)\s+((?:-\S+\s+)*)(\S+)') {
        $candidateRef = $matches[2]
    }
    $sub = $candidateSub; $refToken = $candidateRef; $newBranch = $candidateNew
    break
}
if ($null -eq $sub) { Exit-Allow }

# --- 2. Confirm it actually moves HEAD ---------------------------------------
$headMoving = $false
if ($sub -eq 'switch') {
    $headMoving = $true                          # `git switch` targets branches only
} elseif ($newBranch) {
    $headMoving = $true                          # checkout -b/-B creates + moves
} elseif ($refToken) {
    $isBranch = Invoke-Git -Args @('rev-parse','--verify','--quiet',"refs/heads/$refToken") -WorkDir $cwd
    if (-not [string]::IsNullOrEmpty($isBranch)) {
        $headMoving = $true
    } else {
        $isCommit = Invoke-Git -Args @('rev-parse','--verify','--quiet',"$refToken^{commit}") -WorkDir $cwd
        if (-not [string]::IsNullOrEmpty($isCommit)) { $headMoving = $true }
    }
}
if (-not $headMoving) { Exit-Allow }

# --- 3. cwd must be the MAIN checkout (not a linked worktree) -----------------
$gitDir = Invoke-Git -Args @('rev-parse','--git-dir') -WorkDir $cwd
if ($null -eq $gitDir) { Exit-Allow }
$gitCommonDir = Invoke-Git -Args @('rev-parse','--git-common-dir') -WorkDir $cwd
if ($null -eq $gitCommonDir) { Exit-Allow }

function Resolve-Absolute {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$BaseDir)
    if ([string]::IsNullOrEmpty($Path)) { return $null }
    if ([System.IO.Path]::IsPathRooted($Path)) { return [System.IO.Path]::GetFullPath($Path) }
    return [System.IO.Path]::GetFullPath((Join-Path $BaseDir $Path))
}

$gitDirAbs       = Resolve-Absolute -Path $gitDir       -BaseDir $cwd
$gitCommonDirAbs = Resolve-Absolute -Path $gitCommonDir -BaseDir $cwd
if (-not $gitDirAbs -or -not $gitCommonDirAbs) { Exit-Allow }
if (-not [string]::Equals($gitDirAbs, $gitCommonDirAbs, [System.StringComparison]::OrdinalIgnoreCase)) {
    Exit-Allow   # LINKED worktree — the sanctioned place to do task work
}

# --- 4. no-op switch (already on target) -> allow ----------------------------
if ($refToken -and -not $newBranch) {
    $current = Invoke-Git -Args @('symbolic-ref','--short','HEAD') -WorkDir $cwd
    if (-not [string]::IsNullOrEmpty($current) -and
        [string]::Equals($current, $refToken, [System.StringComparison]::Ordinal)) {
        Exit-Allow
    }
}

# --- 5. dirty working tree is the danger -------------------------------------
$porcelain = Invoke-Git -Args @('status','--porcelain') -WorkDir $cwd
if ([string]::IsNullOrEmpty($porcelain)) { Exit-Allow }   # clean — nothing carried

# --- 6. surface the confirmation ---------------------------------------------
$toplevel = Invoke-Git -Args @('rev-parse','--show-toplevel') -WorkDir $cwd
if ([string]::IsNullOrEmpty($toplevel)) { $toplevel = $cwd }

$target = '(another branch)'
if ($refToken)                 { $target = $refToken }
if ($newBranch -and $refToken) { $target = "$refToken (new branch)" }

$reason = "Concurrent-session shared-checkout hazard: cwd is the MAIN checkout ($toplevel) with a DIRTY working tree, and this 'git $sub' moves HEAD to '$target'. The uncommitted changes are carried onto that branch — if a concurrent session shares this checkout, its WIP is stranded on the wrong branch and its next commit lands there (CLAUDE.md > Cross-Project Changes > Concurrent-session worktree isolation; 2026-06-13 incident). Prefer an isolated 'git worktree add <dir> <branch>' for concurrent task work and keep the main checkout parked on a stable branch. Proceed only if no other live session shares this directory."
Exit-Ask -Reason $reason
```

### (b) `.claude/settings.json` — register under the existing `PreToolUse` `Bash` matcher (append after `protect-main-branch.ps1`)

```json
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/protect-main-branch.ps1"
          },
          {
            "type": "command",
            "command": "powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/warn-shared-checkout-switch.ps1"
          }
        ]
      },
```

### (c) `.claude/hooks/README.md` — inventory row (after the `protect-main-branch.ps1` row) + exempt-list update

Inventory row:

```markdown
| [`warn-shared-checkout-switch.ps1`](warn-shared-checkout-switch.ps1) | `PreToolUse[Bash]` | `ask` | Surface a confirmation when a HEAD-moving `git checkout`/`git switch` runs in the MAIN checkout (`git-dir == git-common-dir`) with a **dirty** working tree — the concurrent-session shared-checkout hazard (CLAUDE.md § Concurrent-session worktree isolation; 2026-06-13 pc-fe-070 × increment-C incident; TASK-MONO-236). Dirty-gate suppresses clean-start `checkout -b`; linked worktrees / clean tree / no-op same-branch switch / `git -C` redirects silently allowed. |
```

Exempt-list sentence (§ Hook output format) — add this hook to the existing exemption:

> `protect-main-branch.ps1`, `verify-worktree-isolation.ps1`, **and `warn-shared-checkout-switch.ps1`** are intentionally exempt — their messages are safety-rail enforcements (Bash- and Edit/Write-tool guards), not rule-surface violations, so the standard does not apply.

### (d) Fixtures `.claude/hooks/__tests__/` (per README § "Adding a new detector" step 4)

One PASS case + negative cases:

1. main checkout + dirty + `git checkout -b task/foo` → `{"decision":"ask",...}`
2. main checkout + **clean** + `git checkout -b task/foo` → allow (empty output)
3. linked worktree + dirty + `git checkout other` → allow
4. `git checkout -- src/file.txt` (pathspec) → allow
5. same-branch no-op `git switch <current>` → allow
6. `git -C <dir> checkout x` (routed elsewhere) → allow

## Out of scope
- Any `block` escalation — `ask` is deliberate (solo main-checkout work is valid).
- Detecting the `git -C <maindir> checkout` cross-dir vector (v1 limitation; the incident form is the no-`-C` shared-cwd case). Note as a known gap in the hook header if revisited.
- Auto-detecting whether another session is actually live (impossible from a single PreToolUse payload) — the dirty-tree heuristic is the proxy.

# Acceptance Criteria

- **AC-1**: `.claude/hooks/warn-shared-checkout-switch.ps1` exists with the §(a) content and is registered under `PreToolUse[Bash]` in `.claude/settings.json` after `protect-main-branch.ps1`.
- **AC-2**: A HEAD-moving `git checkout`/`git switch` in the main checkout with a dirty tree emits `{"decision":"ask",...}`; the same on a clean tree, inside a linked worktree, as a pathspec checkout, as a same-branch no-op, or via `git -C` emits nothing (allow).
- **AC-3**: `.claude/hooks/README.md` inventory + exempt list updated per §(c).
- **AC-4**: Fixtures per §(d) added under `__tests__/`; `pwsh .claude/hooks/__tests__/run-all.ps1` passes (existing + new).
- **AC-5**: Applied + committed by the human operator (classifier blocks AI `.claude/` commit). Suggested commit: `feat(hooks): TASK-MONO-236 — warn on dirty HEAD-moving checkout in shared main checkout`.

# Related Specs / Code

- `CLAUDE.md § Cross-Project Changes` → "Concurrent-session worktree isolation" (TASK-MONO-235, the rule this hook enforces).
- `.claude/hooks/protect-main-branch.ps1` (sibling Bash guard — push-time; this hook is checkout-time), `.claude/hooks/verify-worktree-isolation.ps1` (shared `Invoke-Git` / `Resolve-Absolute` / main-vs-linked-worktree detection pattern reused here).
- Memory: `env_concurrent_git_branch_switch_hazard`, `env_classifier_claude_self_mod_block`.

# Related Contracts

- None (harness hook + governance — no API or event contract touched).

# Edge Cases / Failure Scenarios

- **Classifier hard-block** — AI cannot edit/commit any `.claude/` file; the §Scope payload is human-applied. This task file (outside `.claude/`) is the committable record.
- **False-positive control** — the dirty-tree gate is what keeps noise low; without it every solo `checkout -b` in the main repo would prompt. Verify AC-2's clean-tree negative case before merge.
- **PowerShell 5.1** — hook mirrors the `ProcessStartInfo` + stderr-discard pattern of `verify-worktree-isolation.ps1` to avoid the native-stderr ErrorRecord trap on this Windows host.
- **HARDSTOP-03 N/A** — `.claude/` is shared/project-agnostic; this is a repo-wide harness guard (correct for a shared file), no project-specific content.
- **Self-dogfooding** — task authored in a dedicated `mlab-mono236` worktree off `origin/main`, leaving the contested main checkout untouched.

# Notes

- Enforcement counterpart to TASK-MONO-235. Hook payload embedded verbatim so the operator can apply without reconstructing it. After the human lands the `.claude/` commit, move this task `ready → done`.
