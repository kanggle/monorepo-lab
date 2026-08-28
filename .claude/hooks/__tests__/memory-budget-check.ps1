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
    [void]$sb.AppendLine('## A. Standing rules')
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('### A2. Measurement discipline')
    [void]$sb.AppendLine()
    $half = [math]::Max(1, [int][math]::Floor($TrapPointers / 2))
    for ($i = 0; $i -lt $half; $i++) { [void]$sb.AppendLine("- [t$i](trap_a2_$i.md) trap") }
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('### B5. Frontend hazards')
    [void]$sb.AppendLine()
    for ($i = $half; $i -lt $TrapPointers; $i++) { [void]$sb.AppendLine("- [t$i](trap_b5_$i.md) trap") }
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('## C. Procedures')
    [void]$sb.AppendLine()
    $mhalf = [math]::Max(1, [int][math]::Floor($MovablePointers / 2))
    for ($i = 0; $i -lt $mhalf; $i++) { [void]$sb.AppendLine("- [m$i](proc_c_$i.md) procedure") }
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('## F. Strategy')
    [void]$sb.AppendLine()
    for ($i = $mhalf; $i -lt $MovablePointers; $i++) { [void]$sb.AppendLine("- [m$i](strat_f_$i.md) strategy") }
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

    # --- Negative 2: WITHIN the line limit -> silent allow ------------------------
    New-FakeIndex -Path $index -TrapPointers 6 -MovablePointers 4
    $n2 = Invoke-BudgetHook -Cwd $fakeCwd -HomeDir $fakeHome
    Assert-Allowed -Output $n2
    if (Test-Path -LiteralPath $marker) { throw "WITHIN must not write the debounce marker" }
    "PASS: negative-2 (small index, within the line limit -> silent allow, no marker)"

    # --- Positive 1: OVER the line limit -> block with the 4-block stanza ---------
    # 240 pointers is one per line plus section overhead, comfortably past the hook's
    # 140-line tunable. The fixture does not hardcode 140 here, so changing the
    # tunable does not silently un-bite this test.
    New-FakeIndex -Path $index -TrapPointers 120 -MovablePointers 120
    $p1 = Invoke-BudgetHook -Cwd $fakeCwd -HomeDir $fakeHome
    Assert-Blocked -Output $p1 -Case 'positive-1 (large index)'
    Assert-Stanza -Output $p1 -ExpectedId 'MEMORY-BUDGET-01' -ExpectedDecision 'block'
    "PASS: positive-1 (large index, over the line limit -> block + MEMORY-BUDGET-01 stanza)"

    $reason = (ConvertFrom-HookOutput -Output $p1).reason

    # --- Positive 2: the stanza names WHICH axis judged, and labels the rest -------
    # TASK-MONO-596. The defect this replaces was a verdict on a fabricated pointer
    # quota while the real numbers were reported alongside it, so a reader could not
    # tell which one decided. Now exactly one axis judges (lines) and the others must
    # be marked as reporting-only — otherwise the next reader mistakes one for a
    # threshold, which is how 180 and 24576 both happened.
    $measuredLines = (Get-Content -LiteralPath $index -Raw -Encoding UTF8) -split "`r?`n"
    if ($reason -notmatch ("{0}\s*줄\s*/" -f $measuredLines.Count)) {
        throw "Stanza does not report the measured LINE count as the verdict axis (expected '$($measuredLines.Count) 줄 /'): $reason"
    }
    if ($reason -notmatch '보고 전용') {
        throw "Stanza does not mark the non-judging numbers as reporting-only: $reason"
    }
    if ($reason -notmatch '240\s*포인터') {
        throw "Stanza stopped reporting the pointer count entirely — it is context the remediation needs: $reason"
    }
    "PASS: positive-2 (stanza names the judging axis and labels the rest reporting-only) — MONO-596"

    # --- Negative 3: THE ONE THAT MATTERS — traps are not movable candidates ------
    # Candidate list lives on the "후보:" line of [REMEDIATION] item 2.
    $candLine = ($reason -split "`r?`n" | Where-Object { $_ -match 'PLAYBOOKS\.md' })
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

    # ===== TASK-MONO-596 AC-4: bite at the BOUNDARY, both directions ==============
    #
    # 🔴 One direction proves nothing. A hook that is always silent and a hook that is
    #    correctly silent look identical from the WITHIN side; a hook that always fires
    #    and one that correctly fires look identical from the OVER side. Only the pair,
    #    one line apart, separates them.
    #
    # 🔴 The threshold is READ FROM THE HOOK, not retyped. Retyping it makes the fixture
    #    a second home for the same constant, and this ticket exists because a constant
    #    with two homes drifted.
    $limit = [int](Select-String -LiteralPath $hook -Pattern '^\s*\$LineLimit\s*=\s*(\d+)' |
                   ForEach-Object { $_.Matches[0].Groups[1].Value } | Select-Object -First 1)
    if (-not $limit) { throw "Could not read `$LineLimit from the hook — the fixture cannot test a boundary it cannot locate." }
    "  (boundary cells read LineLimit = $limit from the hook source)"

    # Build an index with an EXACT line count. Content shape is irrelevant here; what is
    # under test is the line predicate.
    function New-IndexWithLines {
        param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][int]$Lines)
        $sb = [System.Text.StringBuilder]::new()
        [void]$sb.AppendLine('> index header')
        [void]$sb.AppendLine('## C. Procedures')
        for ($i = 0; $i -lt ($Lines - 3); $i++) { [void]$sb.AppendLine("- [p$i](proc_c_$i.md) filler") }
        [void]$sb.Append('- [last](proc_c_last.md) filler')
        Set-Content -LiteralPath $Path -Value $sb.ToString() -Encoding UTF8 -NoNewline
    }

    # 🔴🔴 ASSERT THE INJECTION BEFORE READING THE BITE. If the builder is off by one,
    #    "did not fire" and "was never at the boundary" are indistinguishable — and the
    #    first reads as a passing test.
    function Assert-IndexLines {
        param([Parameter(Mandatory)][int]$Want, [Parameter(Mandatory)][string]$Case)
        $got = ((Get-Content -LiteralPath $index -Raw -Encoding UTF8) -split "`r?`n").Count
        if ($got -ne $Want) { throw "$Case`: injection did not land — built $got lines, wanted $Want. This cell tested nothing." }
        return $got
    }

    # 🔴 The daily debounce marker silently converts "fired" into "allowed". Every
    #    boundary cell must clear it first, or the second of the pair is a false green.
    function Clear-Debounce { Remove-Item -LiteralPath $marker -Force -ErrorAction SilentlyContinue }

    # --- boundary A: exactly one line UNDER the limit -> silent -------------------
    Clear-Debounce
    New-IndexWithLines -Path $index -Lines ($limit - 1)
    $got = Assert-IndexLines -Want ($limit - 1) -Case 'boundary-under'
    $bU = Invoke-BudgetHook -Cwd $fakeCwd -HomeDir $fakeHome
    Assert-Allowed -Output $bU
    if (Test-Path -LiteralPath $marker) { throw "boundary-under wrote the debounce marker — it must not have fired at all" }
    "PASS: boundary-under ($got lines = limit-1 -> silent, no marker) — MONO-596"

    # --- boundary B: exactly AT the limit -> fires --------------------------------
    # The hook's predicate is `lines -lt limit`, mirroring the harness wording "under
    # 140". So `= limit` is already over.
    Clear-Debounce
    New-IndexWithLines -Path $index -Lines $limit
    $got = Assert-IndexLines -Want $limit -Case 'boundary-at'
    $bA = Invoke-BudgetHook -Cwd $fakeCwd -HomeDir $fakeHome
    Assert-Blocked -Output $bA -Case "boundary-at ($limit lines)"
    Assert-Stanza -Output $bA -ExpectedId 'MEMORY-BUDGET-01' -ExpectedDecision 'block'
    "PASS: boundary-at ($got lines = limit -> fires) — MONO-596"

    # --- AC-3: when the movable inventory is empty, SAY SO -----------------------
    # Measured 2026-08-28 on the real MEMORY.md: §C and §F hold 0 pointers, so this
    # fallback is the live path, not a theoretical one. A hook that instead printed an
    # empty candidate list would be prescribing a lever that has no stock.
    #
    # 🔴 `New-FakeIndex` CANNOT build this case: its `[math]::Max(1, …)` floors the
    #    movable count at 1, so asking for 0 still emits one movable pointer. The first
    #    version of this cell used it and the candidate list came back
    #    "C. Procedures = 1 포인터" — the cell would have been testing the opposite of
    #    what it claims. The injection assertion below is what caught that.
    Clear-Debounce
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.AppendLine('> index header')
    [void]$sb.AppendLine('## A2. Measurement discipline')
    for ($i = 0; $i -lt 200; $i++) { [void]$sb.AppendLine("- [t$i](trap_a2_$i.md) trap") }
    [void]$sb.AppendLine('## C. Procedures')
    [void]$sb.AppendLine('(전부 이관 완료 — 포인터 없음)')
    Set-Content -LiteralPath $index -Value $sb.ToString() -Encoding UTF8

    # Assert the injection: the movable sections must hold ZERO pointers, or this cell
    # exercises the populated path and silently proves nothing.
    $movablePtrs = 0; $sec = ''
    foreach ($l in ((Get-Content -LiteralPath $index -Raw -Encoding UTF8) -split "`r?`n")) {
        if ($l -match '^##+\s*(.+)$') { $sec = $Matches[1].Trim() }
        if ($l -match '^\-\s' -and $sec -match '^[CDFG][\.\d]') {
            $movablePtrs += ([regex]::Matches($l, '\]\([a-z0-9_]+\.md\)')).Count
        }
    }
    if ($movablePtrs -ne 0) { throw "empty-movable-inventory: injection did not land — $movablePtrs movable pointers present, wanted 0. This cell tested the populated path." }

    $e1 = Invoke-BudgetHook -Cwd $fakeCwd -HomeDir $fakeHome
    Assert-Blocked -Output $e1 -Case 'empty-movable-inventory'
    $eReason = (ConvertFrom-HookOutput -Output $e1).reason
    if ($eReason -notmatch '없음') {
        throw "With zero movable pointers the stanza must say the lever is empty, not print a blank list: $eReason"
    }
    if ($eReason -notmatch '소유자 결정') {
        throw "The remaining lever (sibling consolidation) must be marked an owner decision: $eReason"
    }
    "PASS: empty-movable-inventory (says '없음' + flags the owner decision) — MONO-596 AC-3"
}
finally {
    Remove-Item -LiteralPath $tmpRoot -Recurse -Force -ErrorAction SilentlyContinue
}
