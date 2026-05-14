# Test helpers for .claude/hooks/__tests__/ fixtures.
#
# Provides Invoke-Hook + Assert-Stanza so each fixture is a short, declarative
# script that pipes synthesized stdin JSON to the target hook and asserts the
# JSON output shape.

$script:HooksDir = Split-Path -Parent $PSScriptRoot

function Invoke-Hook {
    param(
        [Parameter(Mandatory)][string]$HookName,
        [Parameter(Mandatory)][hashtable]$Payload
    )
    $hookPath = Join-Path $script:HooksDir $HookName
    if (-not (Test-Path $hookPath -PathType Leaf)) {
        throw "Hook not found: $hookPath"
    }
    $json = $Payload | ConvertTo-Json -Depth 8 -Compress

    # Pipe via cmd to a fresh powershell child — matches the harness invocation pattern.
    $tmp = [System.IO.Path]::GetTempFileName()
    try {
        Set-Content -Path $tmp -Value $json -Encoding UTF8 -NoNewline
        $output = & cmd /c "type `"$tmp`" | powershell -NoProfile -ExecutionPolicy Bypass -File `"$hookPath`""
    }
    finally {
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
    if (-not $output) { return $null }
    return ($output | Out-String).Trim()
}

function ConvertFrom-HookOutput {
    param([string]$Output)
    if (-not $Output) { return $null }
    try { return $Output | ConvertFrom-Json } catch { return $null }
}

function Assert-Stanza {
    param(
        [Parameter(Mandatory)][string]$Output,
        [Parameter(Mandatory)][string]$ExpectedId,
        [Parameter(Mandatory)][string]$ExpectedDecision
    )
    if (-not $Output) {
        throw "Expected hook output, got empty (silently allowed)"
    }
    $parsed = ConvertFrom-HookOutput -Output $Output
    if (-not $parsed) {
        throw "Hook output is not valid JSON: $Output"
    }
    if ($parsed.decision -ne $ExpectedDecision) {
        throw "Expected decision='$ExpectedDecision', got '$($parsed.decision)'"
    }
    $reason = $parsed.reason
    if ($reason -notmatch [regex]::Escape("[VIOLATION] $ExpectedId")) {
        throw "Wrong stanza ID. Expected [VIOLATION] $ExpectedId, got: $($reason.Substring(0, [Math]::Min(120, $reason.Length)))"
    }
    foreach ($block in @('[VIOLATION]', '[WHY]', '[REMEDIATION] Choose one:', '[REFERENCE]')) {
        if ($reason -notmatch [regex]::Escape($block)) {
            throw "Missing required block '$block' in stanza"
        }
    }
}

function Assert-Allowed {
    param([string]$Output)
    if ($Output) {
        $parsed = ConvertFrom-HookOutput -Output $Output
        if ($parsed -and $parsed.decision -eq 'block') {
            throw "Expected hook to allow (silent), but got block: $($parsed.reason.Substring(0, [Math]::Min(120, $parsed.reason.Length)))"
        }
    }
    # Empty output or non-block decision = allowed
}

# ===== Canonical stanza drift detection (TASK-MONO-100) =====
#
# Helpers below let a fixture verify that the hook's hardcoded stanza body has
# not drifted from the canonical body in `platform/hardstop-rules.md`.
#
# Scope: only [WHY] and [REFERENCE] blocks are byte-compared. [VIOLATION] and
# [REMEDIATION] contain design-intent dynamic injection (relFromRoot / lineNo /
# taskId / project / service / catalog enumeration) that legitimately differs
# from the canonical placeholder body (`<cwd>`, `<path>`, `<project>`,
# `<service>`, etc.). The static-block compare is sufficient because the [WHY]
# invariant and the [REFERENCE] pointer are the core sync contract — changes to
# them in either source should always land in both.

function Get-CanonicalStanza {
    param(
        [Parameter(Mandatory)][string]$RuleId,
        [Parameter()][string]$RulesPath
    )
    if (-not $RulesPath) {
        $repoRoot = Resolve-Path (Join-Path $script:HooksDir '..\..\') | Select-Object -ExpandProperty Path
        $RulesPath = Join-Path $repoRoot 'platform\hardstop-rules.md'
    }
    if (-not (Test-Path $RulesPath -PathType Leaf)) {
        throw "Canonical rules file not found: $RulesPath"
    }
    $content = (Get-Content -Path $RulesPath -Raw) -replace "`r`n", "`n"
    $idEsc = [regex]::Escape($RuleId)
    # Match: heading "## HARDSTOP-NN — ..." then anything up to ```...``` code block; capture inner body.
    $pattern = "(?ms)^## $idEsc — [^\n]+\n.*?\n``````\n(.+?)\n``````"
    if ($content -match $pattern) {
        return $Matches[1].Trim()
    }
    throw "Canonical stanza for '$RuleId' not found in $RulesPath"
}

function Get-StaticStanzaBlocks {
    param([Parameter(Mandatory)][string]$Stanza)
    # Return [WHY] block content and [REFERENCE] block content (both as trimmed strings).
    # Skips [VIOLATION] and [REMEDIATION] (design-intent dynamic injection — see TASK-MONO-100 spec).
    $norm  = ($Stanza -replace "`r`n", "`n").TrimEnd()
    $lines = $norm -split "`n"
    $whyLines = @()
    $refLines = @()
    $state = 'none'
    foreach ($line in $lines) {
        if     ($line -match '^\[VIOLATION\]')   { $state = 'violation' }
        elseif ($line -match '^\[WHY\]')         { $state = 'why' }
        elseif ($line -match '^\[REMEDIATION\]') { $state = 'remediation' }
        elseif ($line -match '^\[REFERENCE\]')   { $state = 'reference' }
        if ($state -eq 'why')        { $whyLines += $line }
        elseif ($state -eq 'reference') { $refLines += $line }
    }
    return @{
        why       = ($whyLines -join "`n").TrimEnd()
        reference = ($refLines -join "`n").TrimEnd()
    }
}

function Assert-StanzaBodyMatchesCanonical {
    param(
        [Parameter(Mandatory)][string]$HookOutput,
        [Parameter(Mandatory)][string]$RuleId
    )
    $parsed = ConvertFrom-HookOutput -Output $HookOutput
    if (-not $parsed) {
        throw "Hook output is not valid JSON: $HookOutput"
    }
    if (-not $parsed.reason) {
        throw "Hook output has no 'reason' field for $RuleId"
    }
    $hookBlocks  = Get-StaticStanzaBlocks -Stanza $parsed.reason
    $canonStanza = Get-CanonicalStanza -RuleId $RuleId
    $canonBlocks = Get-StaticStanzaBlocks -Stanza $canonStanza
    if ($hookBlocks.why -cne $canonBlocks.why) {
        throw ("[$RuleId] [WHY] block drift between hook and platform/hardstop-rules.md:`n  hook  : $($hookBlocks.why)`n  canon : $($canonBlocks.why)")
    }
    if ($hookBlocks.reference -cne $canonBlocks.reference) {
        throw ("[$RuleId] [REFERENCE] block drift between hook and platform/hardstop-rules.md:`n  hook  : $($hookBlocks.reference)`n  canon : $($canonBlocks.reference)")
    }
}
