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
