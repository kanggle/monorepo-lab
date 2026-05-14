# Canonical-body sync fixture (TASK-MONO-100).
#
# Verifies that the [WHY] and [REFERENCE] blocks emitted by the hook for each
# mechanically-detectable HARDSTOP trigger match the canonical stanza in
# platform/hardstop-rules.md byte-for-byte. [VIOLATION] and [REMEDIATION] are
# design-intent dynamic (file path / project / service / line number / catalog
# enumeration) and excluded from this compare — see _helpers.ps1 §
# "Canonical stanza drift detection" for the rationale.
#
# Catches drift introduced when a maintainer edits the hook hardcoded body or
# the canonical platform body without syncing the other.

. (Join-Path $PSScriptRoot '_helpers.ps1')

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..') | Select-Object -ExpandProperty Path

# ===== HARDSTOP-01 =====
$h01Root = Join-Path $env:TEMP ("hardstop-body-01-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -Path (Join-Path $h01Root "projects\orphan-body\apps") -ItemType Directory -Force | Out-Null
New-Item -Path (Join-Path $h01Root ".git") -ItemType Directory -Force | Out-Null
try {
    $h01File = Join-Path $h01Root "projects\orphan-body\apps\foo.md"
    $h01Out = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Edit'
        tool_input = @{
            file_path  = $h01File
            old_string = 'foo'
            new_string = 'bar baz qux quux corge grault garply waldo fred plugh xyzzy thud'
        }
        cwd = $h01Root
    }
    Assert-StanzaBodyMatchesCanonical -HookOutput $h01Out -RuleId 'HARDSTOP-01'
    "PASS: HARDSTOP-01 [WHY]+[REFERENCE] match canonical platform/hardstop-rules.md"
}
finally {
    Remove-Item -Recurse -Force $h01Root -ErrorAction SilentlyContinue
}

# ===== HARDSTOP-03 =====
$h03Path = Join-Path $repoRoot "platform\example-shared-body.md"
$h03Out = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Write'
    tool_input = @{
        file_path = $h03Path
        content   = @"
# Example shared spec — drift fixture

Reference to projects/wms-platform/apps/outbound-service/ violates the rule.
"@
    }
    cwd = $repoRoot
}
Assert-StanzaBodyMatchesCanonical -HookOutput $h03Out -RuleId 'HARDSTOP-03'
"PASS: HARDSTOP-03 [WHY]+[REFERENCE] match canonical platform/hardstop-rules.md"

# ===== HARDSTOP-05 =====
$h05File = Join-Path $repoRoot "tasks\review\TASK-MONO-BODY-FIXTURE.md"
$h05Out = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $h05File
        old_string = "# Goal`n`nOriginal goal."
        new_string = "# Goal`n`nRevised goal — adding scope post-review."
    }
    cwd = $repoRoot
}
Assert-StanzaBodyMatchesCanonical -HookOutput $h05Out -RuleId 'HARDSTOP-05'
"PASS: HARDSTOP-05 [WHY]+[REFERENCE] match canonical platform/hardstop-rules.md"

# ===== HARDSTOP-09 =====
$h09TempProj = "projects\test-arch-body-" + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$h09ProjDir  = Join-Path $repoRoot $h09TempProj
$h09SrcMain  = Join-Path $h09ProjDir "apps\synth-svc\src\main\java"
New-Item -Path $h09SrcMain -ItemType Directory -Force | Out-Null
New-Item -Path (Join-Path $h09ProjDir "PROJECT.md") -ItemType File -Force | Out-Null
Set-Content -Path (Join-Path $h09ProjDir "PROJECT.md") -Value "---`ndomain: test`n---" -Encoding UTF8
try {
    $h09File = Join-Path $h09SrcMain "Foo.java"
    $h09Out = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{
            file_path = $h09File
            content   = "public class Foo {}"
        }
        cwd = $repoRoot
    }
    Assert-StanzaBodyMatchesCanonical -HookOutput $h09Out -RuleId 'HARDSTOP-09'
    "PASS: HARDSTOP-09 [WHY]+[REFERENCE] match canonical platform/hardstop-rules.md"
}
finally {
    Remove-Item -Recurse -Force $h09ProjDir -ErrorAction SilentlyContinue
}

# ===== HARDSTOP-10 =====
$h10TempProj = "projects\test-svctype-body-" + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$h10ProjDir  = Join-Path $repoRoot $h10TempProj
$h10ArchDir  = Join-Path $h10ProjDir "specs\services\synth-svc"
New-Item -Path $h10ArchDir -ItemType Directory -Force | Out-Null
New-Item -Path (Join-Path $h10ProjDir "PROJECT.md") -ItemType File -Force | Out-Null
Set-Content -Path (Join-Path $h10ProjDir "PROJECT.md") -Value "---`ndomain: test`n---" -Encoding UTF8
try {
    $h10File = Join-Path $h10ArchDir "architecture.md"
    $h10Content = @"
# synth-svc Architecture

## Style

Hexagonal — no Service Type declared (drift fixture).
"@
    $h10Out = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{
            file_path = $h10File
            content   = $h10Content
        }
        cwd = $repoRoot
    }
    Assert-StanzaBodyMatchesCanonical -HookOutput $h10Out -RuleId 'HARDSTOP-10'
    "PASS: HARDSTOP-10 [WHY]+[REFERENCE] match canonical platform/hardstop-rules.md"
}
finally {
    Remove-Item -Recurse -Force $h10ProjDir -ErrorAction SilentlyContinue
}
