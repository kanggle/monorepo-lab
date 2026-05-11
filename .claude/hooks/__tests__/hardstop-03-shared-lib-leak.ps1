# HARDSTOP-03 fixture — shared file references a project name as path token.
# Includes positive (genuine violation) and negative (annotated allow) cases.
. (Join-Path $PSScriptRoot '_helpers.ps1')

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..') | Select-Object -ExpandProperty Path

# Positive case: platform/foo.md references `projects/wms-platform/` as path token
$positivePath = Join-Path $repoRoot "platform\example-shared.md"
$positiveOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Write'
    tool_input = @{
        file_path = $positivePath
        content   = @"
# Example shared spec

This file documents the boundary by example. Reference to
projects/wms-platform/apps/outbound-service/ violates the rule.
"@
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $positiveOutput -ExpectedId 'HARDSTOP-03' -ExpectedDecision 'block'
"PASS: HARDSTOP-03 positive (path-token reference)"

# Negative case: same content but with the hardstop-allow annotation
$negativeOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Write'
    tool_input = @{
        file_path = $positivePath
        content   = @"
# Example shared spec

<!-- hardstop-allow: documentation example, not a code reference -->
Reference to projects/wms-platform/apps/outbound-service/ is intentional here.
"@
    }
    cwd = $repoRoot
}
Assert-Allowed -Output $negativeOutput
"PASS: HARDSTOP-03 negative (annotated allow)"
