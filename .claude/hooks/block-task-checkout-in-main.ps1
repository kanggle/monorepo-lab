# PreToolUse[Bash] HARD-BLOCK guard: prohibit doing task work directly in the
# MAIN checkout. Complements warn-shared-checkout-switch.ps1, which only `ask`s
# and ONLY when the tree is DIRTY — that left a gap: a CLEAN-start
# `git checkout -b task/...` in the main checkout was allowed silently, which is
# exactly how the 2026-06-13 TASK-PC-FE-079 violation happened (clean ff from
# origin/main -> `checkout -b task/pc-fe-079` in the main checkout -> commit ->
# re-park step forgotten -> HEAD stranded on the feature branch overnight).
#
# Policy (feedback_auto_worktree_per_task): task implementation must run in a
# dedicated `git worktree add` directory; the main checkout stays parked on a
# stable branch and is never used for task work. This hook BLOCKS a HEAD-moving
# `git checkout|switch` to a `task/*` branch when cwd is the MAIN checkout.
#
# Naturally exempt (the sanctioned paths):
#   - `git worktree add <dir> -b task/...`  (segment is `git worktree`, not
#     `git checkout|switch` -> never matched). /start-task uses this.
#   - checkout/switch run INSIDE a linked worktree (git-dir != git-common-dir)
#   - `git checkout main` / any non-`task/` ref (re-parking is allowed)
#   - pathspec/file checkout (`git checkout ... -- <path>`)
#   - `chore/*` branches (close-chores stay lightweight; add `|chore` below to include)
#
# Safety-rail (best-effort) — exempt from the platform 4-block remediation
# standard (same policy as protect-main-branch.ps1 / warn-shared-checkout-switch.ps1;
# see .claude/hooks/README.md).

$ErrorActionPreference = 'Stop'

$reader    = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$inputJson = $reader.ReadToEnd()

function Exit-Allow { exit 0 }
function Exit-Block {
    param([Parameter(Mandatory)][string]$Reason)
    $payload = @{ decision = 'block'; reason = $Reason } | ConvertTo-Json -Compress
    Write-Output $payload
    exit 0
}

try { $data = $inputJson | ConvertFrom-Json } catch { Exit-Allow }

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

# --- 1. Find a checkout/switch segment targeting a task/* branch -------------
$refToken = $null
foreach ($seg in ($command -split '\s*(?:&&|\|\||;|\|)\s*')) {
    if ($seg -match '\bgit\s+-C\b')                   { continue }   # routed elsewhere
    if ($seg -match '\bgit\s+worktree\b')             { continue }   # worktree add is sanctioned
    if ($seg -notmatch '\bgit\s+(checkout|switch)\b') { continue }
    $candidateSub = $matches[1]
    # pathspec / file-restore checkout -> not a HEAD move
    if ($candidateSub -eq 'checkout' -and $seg -match '\s--(\s|$)') { continue }
    if ($seg -match '\bgit\s+(?:checkout|switch)\s+((?:-\S+\s+)*)(\S+)') {
        $candidateRef = $matches[2]
        # strip surrounding quotes git itself would not see
        $candidateRef = $candidateRef.Trim('"').Trim("'")
        if ($candidateRef -match '^task/') { $refToken = $candidateRef; break }
    }
}
if ($null -eq $refToken) { Exit-Allow }

# --- 2. cwd must be the MAIN checkout (not a linked worktree) ----------------
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

# --- 3. block --------------------------------------------------------------
$toplevel = Invoke-Git -Args @('rev-parse','--show-toplevel') -WorkDir $cwd
if ([string]::IsNullOrEmpty($toplevel)) { $toplevel = $cwd }

$reason = "Task work in the MAIN checkout is blocked: cwd is the main checkout ($toplevel) and this 'git' command moves HEAD to task branch '$refToken'. Per feedback_auto_worktree_per_task, task implementation must run in a dedicated worktree so the main checkout stays parked on a stable branch (the 2026-06-13 PC-FE-079 violation came from a clean-start checkout here whose re-park step was forgotten). Use 'git worktree add <dir> -b $refToken' (or /start-task) and work in that directory instead. Re-parking with 'git checkout main' is allowed."
Exit-Block -Reason $reason
