# memory-budget-check fixture — 2 negative (allow) + 2 positive (block) + 1 debounce.
#
# TASK-MONO-530. The hook this covers (`memory-budget-check.ps1`) reads a file that
# lives OUTSIDE the repo (`~/.claude/projects/<slug>/memory/MEMORY.md`), so the
# fixture fabricates a whole fake home and a fake cwd and points the hook at them.
#
# 🔴 WHY NOT `Invoke-Hook` FROM _helpers.ps1: the hook derives the memory directory
#    from `(Get-Location).Path`, and `Invoke-Hook` has no way to set the child's
#    working directory. `Push-Location` is NOT a substitute — PowerShell's location
#    and the PROCESS working directory are different things and are known to diverge
#    on this host. So the invoker below hands `cmd` an explicit `cd /d`, which is the
#    process cwd the hook will actually observe. Everything else (stdin piping, the
#    `powershell -NoProfile -File` shape) matches _helpers.ps1 deliberately — that is
#    the interpreter settings.json launches.
#
# 🔴 THE ASSERTION THAT MATTERS IS NEGATIVE-3, not the block. The hook's remediation
#    picks movable sections with an ALLOWLIST (`^[CDFG][\.\d]`). Its own comment records
#    that the first version used a blocklist and `^(A2|B)[\.\s]` failed to match `B5.`,
#    leaking all five trap sections into the "move these to lazy-load" candidates.
#    Demoting a trap section is not a size win, it is an outage — an unloaded trap gets
#    stepped on again. So this fixture asserts the trap sections are ABSENT from the
#    candidate list, which is the property that regex exists to hold.
. (Join-Path $PSScriptRoot '_helpers.ps1')

$hook = Join-Path (Split-Path -Parent $PSScriptRoot) 'memory-budget-check.ps1'
if (-not (Test-Path -LiteralPath $hook -PathType Leaf)) { throw "Hook not found: $hook" }

# Assert-Stanza takes a [string] that is Mandatory, so handing it the $null a silent
# hook returns fails with "Cannot bind argument to parameter 'Output' because it is an
# empty string" — which reads like a bug in this fixture rather than "the hook did not
# block". Measured: raising the hook's quota so it can never fire produced exactly that
# message, and a first pass at grading the mutation run scored it as "did not bite".
# The fixture bit; the message hid it. So name the failure before delegating.
function Assert-Blocked {
    param([AllowEmptyString()][AllowNull()][string]$Output, [string]$Case)
    if ([string]::IsNullOrWhiteSpace($Output)) {
        throw "$Case`: expected the hook to BLOCK, but it produced no output (allowed silently). The budget predicate did not fire."
    }
}

# Invoke the hook with an explicit process cwd and a faked $env:USERPROFILE.
function Invoke-BudgetHook {
    param(
        [Parameter(Mandatory)][string]$Cwd,
        [Parameter(Mandatory)][string]$HomeDir
    )
    $tmp = [System.IO.Path]::GetTempFileName()
    $saved = $env:USERPROFILE
    try {
        Set-Content -LiteralPath $tmp -Value '{}' -Encoding UTF8 -NoNewline
        $env:USERPROFILE = $HomeDir
        $out = & cmd /c "cd /d `"$Cwd`" && type `"$tmp`" | powershell -NoProfile -ExecutionPolicy Bypass -File `"$hook`""
    }
    finally {
        $env:USERPROFILE = $saved
        Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    }
    if (-not $out) { return $null }
    return ($out | Out-String).Trim()
}

# Build a MEMORY.md with a known pointer count, split across trap and movable sections.
#   $TrapPointers   -> "## A2. ..." and "## B5. ..."   (must NEVER be offered as movable)
#   $MovablePointers-> "## C. ..."  and "## F. ..."    (the only legitimate candidates)
function New-FakeIndex {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][int]$TrapPointers,
        [Parameter(Mandatory)][int]$MovablePointers
    )
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.AppendLine('> index header')
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('## A. 지속 규칙')
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('### A2. 측정·검증 규율')
    [void]$sb.AppendLine()
    $half = [math]::Max(1, [int][math]::Floor($TrapPointers / 2))
    for ($i = 0; $i -lt $half; $i++) { [void]$sb.AppendLine("- [t$i](trap_a2_$i.md) 함정") }
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('### B5. 프런트/콘솔')
    [void]$sb.AppendLine()
    for ($i = $half; $i -lt $TrapPointers; $i++) { [void]$sb.AppendLine("- [t$i](trap_b5_$i.md) 함정") }
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('## C. 절차·플레이북')
    [void]$sb.AppendLine()
    $mhalf = [math]::Max(1, [int][math]::Floor($MovablePointers / 2))
    for ($i = 0; $i -lt $mhalf; $i++) { [void]$sb.AppendLine("- [m$i](proc_c_$i.md) 절차") }
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('## F. 전략/판정')
    [void]$sb.AppendLine()
    for ($i = $mhalf; $i -lt $MovablePointers; $i++) { [void]$sb.AppendLine("- [m$i](strat_f_$i.md) 전략") }
    Set-Content -LiteralPath $Path -Value $sb.ToString() -Encoding UTF8
}

$tmpRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("hook-fx-mono530-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $tmpRoot -Force | Out-Null
try {
    $fakeCwd  = Join-Path $tmpRoot 'repo'
    $fakeHome = Join-Path $tmpRoot 'home'
    New-Item -ItemType Directory -Path $fakeCwd -Force | Out-Null

    # The hook's slug: cwd with ':' and both separators collapsed to '-'.
    $slug   = $fakeCwd -replace ':', '-' -replace '[\\/]', '-'
    $memDir = Join-Path $fakeHome ".claude\projects\$slug\memory"
    $index  = Join-Path $memDir 'MEMORY.md'
    $marker = Join-Path $memDir '.budget-warned'

    # --- Negative 1: no memory directory at all -> silent allow ------------------
    # A hook that fired here would block every session on any machine that has no
    # memory dir for the current project.
    $n1 = Invoke-BudgetHook -Cwd $fakeCwd -HomeDir $fakeHome
    Assert-Allowed -Output $n1
    "PASS: negative-1 (no memory dir -> silent allow)"

    New-Item -ItemType Directory -Path $memDir -Force | Out-Null

    # --- Negative 2: WITHIN quota -> silent allow --------------------------------
    New-FakeIndex -Path $index -TrapPointers 6 -MovablePointers 4
    $n2 = Invoke-BudgetHook -Cwd $fakeCwd -HomeDir $fakeHome
    Assert-Allowed -Output $n2
    if (Test-Path -LiteralPath $marker) { throw "WITHIN must not write the debounce marker" }
    "PASS: negative-2 (10 pointers, within quota -> silent allow, no marker)"

    # --- Positive 1: OVER quota -> block with the 4-block stanza ------------------
    # 240 pointers is comfortably over the hook's 180 tunable; the fixture does not
    # hardcode 180 itself, so raising the quota does not silently un-bite this test.
    New-FakeIndex -Path $index -TrapPointers 120 -MovablePointers 120
    $p1 = Invoke-BudgetHook -Cwd $fakeCwd -HomeDir $fakeHome
    Assert-Blocked -Output $p1 -Case 'positive-1 (240 pointers)'
    Assert-Stanza -Output $p1 -ExpectedId 'MEMORY-BUDGET-01' -ExpectedDecision 'block'
    "PASS: positive-1 (240 pointers, over quota -> block + MEMORY-BUDGET-01 stanza)"

    $reason = (ConvertFrom-HookOutput -Output $p1).reason

    # --- Positive 2: the measurement is reported, and it is POINTERS --------------
    # The whole point of this hook is that bytes are the wrong primary metric
    # (measured: merging 6 lines saved 188B / 0.8%). If the stanza stopped naming the
    # pointer count, the remediation would read as "compress harder" again.
    if ($reason -notmatch '포인터 240') {
        throw "Stanza does not report the measured pointer count (expected '포인터 240'): $reason"
    }
    "PASS: positive-2 (stanza reports the measured pointer count, not just bytes)"

    # --- Negative 3: THE ONE THAT MATTERS — traps are not movable candidates ------
    # Candidate list lives on the "후보:" line of [REMEDIATION] item 2.
    $candLine = ($reason -split "`r?`n" | Where-Object { $_ -match '후보:' })
    if (-not $candLine) { throw "No candidate line in the remediation block: $reason" }
    $candLine = ($candLine | Out-String).Trim()
    foreach ($trap in @('A2', 'B5')) {
        if ($candLine -match [regex]::Escape($trap)) {
            throw "Trap section '$trap' leaked into the movable candidates — an unloaded trap gets stepped on again. Line: $candLine"
        }
    }
    foreach ($movable in @('C.', 'F.')) {
        if ($candLine -notmatch [regex]::Escape($movable)) {
            throw "Movable section '$movable' missing from candidates (allowlist too narrow). Line: $candLine"
        }
    }
    "PASS: negative-3 (allowlist: C/F offered as movable, A2/B5 never offered)"

    # --- Debounce: a second OVER run on the same day is silent --------------------
    # A Stop hook that blocks on every single turn cannot be satisfied — the agent
    # never gets to finish. The marker is what makes this hook survivable.
    if (-not (Test-Path -LiteralPath $marker)) { throw "OVER must write the debounce marker" }
    $p2 = Invoke-BudgetHook -Cwd $fakeCwd -HomeDir $fakeHome
    Assert-Allowed -Output $p2
    "PASS: debounce (second over-quota run same day -> silent allow)"
}
finally {
    Remove-Item -LiteralPath $tmpRoot -Recurse -Force -ErrorAction SilentlyContinue
}
