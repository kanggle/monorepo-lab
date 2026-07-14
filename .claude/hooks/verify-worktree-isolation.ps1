# Defensive PreToolUse Edit/Write guard for the 2026-05-25 PC-BE-005 dispatch
# worktree path-leak (~2.8% rate, 4 leaks / 144 tool_uses). When an Agent runs
# in `isolation: "worktree"`, the cwd is a *linked* git worktree; Write/Edit
# tool calls whose `file_path` resolves OUTSIDE that worktree's toplevel are
# almost certainly path-leak symptoms (main repo or sibling worktree path
# instead of the intended worktree subfolder). This hook blocks those.
#
# Safety-rail (best-effort) — not a rule-surface violation. Exempt from the
# platform/lint-remediation-message-standard 4-block format (same policy as
# protect-main-branch.ps1; see .claude/hooks/README.md).
#
# Silent-allow cases:
#   - cwd empty / not a git directory
#   - main worktree (git-dir == git-common-dir)
#   - file_path empty / not absolute
#   - file_path is the toplevel itself or a subfolder of it
#   - git symbolic-ref fails (detached HEAD etc.)

$ErrorActionPreference = 'Stop'

$reader    = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$inputJson = $reader.ReadToEnd()

function Exit-Allow {
    # Silent allow = empty stdout, exit 0. Matches existing hook chain convention.
    exit 0
}

function Exit-Block {
    param([Parameter(Mandatory)][string]$Reason)
    $payload = @{ decision = 'block'; reason = $Reason } | ConvertTo-Json -Compress
    Write-Output $payload
    exit 0
}

try {
    $data = $inputJson | ConvertFrom-Json
} catch {
    Exit-Allow
}

$cwd      = ''
$filePath = ''
if ($data.cwd)                                  { $cwd      = [string]$data.cwd }
if ($data.tool_input -and $data.tool_input.file_path) {
    $filePath = [string]$data.tool_input.file_path
}

if (-not $cwd -or -not $filePath) { Exit-Allow }

# file_path must be absolute. Relative paths are resolved against cwd by the
# tool itself — by definition they cannot leak outside cwd, so safe to allow.
if (-not [System.IO.Path]::IsPathRooted($filePath)) { Exit-Allow }

# cwd must exist as a directory before we ask git anything about it.
if (-not (Test-Path -LiteralPath $cwd -PathType Container)) { Exit-Allow }

function Invoke-Git {
    param([Parameter(Mandatory)][string[]]$Args, [Parameter(Mandatory)][string]$WorkDir)
    # Use ProcessStartInfo so stderr is captured and discarded inside the
    # hook process rather than leaking to the parent harness's console.
    # PowerShell 5.1's `2>$null` wraps native stderr lines as ErrorRecords
    # that show up at the call site (CLAUDE.md user-level shell note).
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName               = 'git'
    $psi.WorkingDirectory       = $WorkDir
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError  = $true
    $psi.UseShellExecute        = $false
    $psi.CreateNoWindow         = $true
    $psi.Arguments = (@('-C', "`"$WorkDir`"") + $Args) -join ' '
    try {
        $p = [System.Diagnostics.Process]::Start($psi)
    } catch {
        return $null
    }
    $stdout = $p.StandardOutput.ReadToEnd()
    [void]$p.StandardError.ReadToEnd()
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) { return $null }
    if ([string]::IsNullOrEmpty($stdout)) { return '' }
    return ($stdout -replace "`r`n", "`n").TrimEnd("`n")
}

$gitDir       = Invoke-Git -Args @('rev-parse', '--git-dir')        -WorkDir $cwd
if ($null -eq $gitDir) { Exit-Allow }   # not a git directory
$gitCommonDir = Invoke-Git -Args @('rev-parse', '--git-common-dir') -WorkDir $cwd
if ($null -eq $gitCommonDir) { Exit-Allow }

# Normalize both to absolute paths and compare. Main worktree has
# git-dir == git-common-dir (both point at <repo>/.git). Linked worktrees
# have git-dir = <main>/.git/worktrees/<id> while git-common-dir = <main>/.git.
#
# git's output may be either relative-to-cwd (`.git`) or absolute
# (`C:/.../.git/worktrees/<id>`); a Join-Path of cwd + absolute would
# embed a drive letter mid-path and trip [System.IO.Path]::GetFullPath
# with "given path's format is not supported". Guard with IsPathRooted.
function Resolve-Absolute {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$BaseDir)
    if ([string]::IsNullOrEmpty($Path)) { return $null }
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $BaseDir $Path))
}

$gitDirAbs       = Resolve-Absolute -Path $gitDir       -BaseDir $cwd
$gitCommonDirAbs = Resolve-Absolute -Path $gitCommonDir -BaseDir $cwd
if (-not $gitDirAbs -or -not $gitCommonDirAbs) { Exit-Allow }
if ([string]::Equals($gitDirAbs, $gitCommonDirAbs, [System.StringComparison]::OrdinalIgnoreCase)) {
    Exit-Allow   # main worktree — out of scope
}

$toplevel = Invoke-Git -Args @('rev-parse', '--show-toplevel') -WorkDir $cwd
if ([string]::IsNullOrEmpty($toplevel)) { Exit-Allow }

$normalizedTopRaw = Resolve-Absolute -Path $toplevel -BaseDir $cwd
if (-not $normalizedTopRaw) { Exit-Allow }
$normalizedTop  = $normalizedTopRaw.TrimEnd([System.IO.Path]::DirectorySeparatorChar)
$normalizedFile = [System.IO.Path]::GetFullPath($filePath)

# Toplevel itself = allowed; subfolder = allowed.
# Use DirectorySeparatorChar suffix so `C:\repo\a` does NOT prefix-match
# `C:\repo\abc`.
$topWithSep = $normalizedTop + [System.IO.Path]::DirectorySeparatorChar
$isInsideToplevel = (
    [string]::Equals($normalizedFile, $normalizedTop, [System.StringComparison]::OrdinalIgnoreCase) -or
    $normalizedFile.StartsWith($topWithSep, [System.StringComparison]::OrdinalIgnoreCase)
)

if ($isInsideToplevel) { Exit-Allow }

$reason = "Worktree isolation breach: cwd is a linked worktree ($normalizedTop) but file_path $normalizedFile resolves outside it. Likely Agent dispatch path leak (PC-BE-005 2026-05-25 pattern, TASK-MONO-136). Re-target Edit/Write inside the worktree subfolder; if the leak already wrote to main, recover with: git diff (capture patch in worktree) -> Move-Item (new files into worktree) -> git apply (in worktree) -> git restore (clean main)."
Exit-Block -Reason $reason
