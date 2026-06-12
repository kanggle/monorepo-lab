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
