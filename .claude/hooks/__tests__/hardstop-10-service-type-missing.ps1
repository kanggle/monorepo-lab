# HARDSTOP-10 fixture — architecture.md Write without a recognised Service Type declaration.
. (Join-Path $PSScriptRoot '_helpers.ps1')

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..') | Select-Object -ExpandProperty Path

# Synthetic project: projects/test-svctype-fixture/{PROJECT.md, specs/services/synth-svc/architecture.md}
$tempProj = "projects\test-svctype-fixture-" + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$projDir = Join-Path $repoRoot $tempProj
$archDir = Join-Path $projDir "specs\services\synth-svc"
New-Item -Path $archDir -ItemType Directory -Force | Out-Null
New-Item -Path (Join-Path $projDir "PROJECT.md") -ItemType File -Force | Out-Null
Set-Content -Path (Join-Path $projDir "PROJECT.md") -Value "---`ndomain: test`n---" -Encoding UTF8

try {
    $archFile = Join-Path $archDir "architecture.md"

    # Positive case: Write without Service Type
    $positiveContent = @"
# synth-svc Architecture

## Style

Hexagonal.

## Notes

No service type declared here intentionally for the test.
"@
    $positiveOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{
            file_path = $archFile
            content   = $positiveContent
        }
        cwd = $repoRoot
    }
    Assert-Stanza -Output $positiveOutput -ExpectedId 'HARDSTOP-10' -ExpectedDecision 'block'
    "PASS: HARDSTOP-10 positive (no Service Type header)"

    # Negative case: Write WITH valid Service Type
    $negativeContent = @"
# synth-svc Architecture

## Service Type

``rest-api``

## Style

Hexagonal.
"@
    $negativeOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{
            file_path = $archFile
            content   = $negativeContent
        }
        cwd = $repoRoot
    }
    Assert-Allowed -Output $negativeOutput
    "PASS: HARDSTOP-10 negative (valid rest-api declared)"
}
finally {
    Remove-Item -Recurse -Force $projDir -ErrorAction SilentlyContinue
}
