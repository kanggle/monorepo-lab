# HARDSTOP-09 fixture — edit under projects/<name>/apps/<service>/src/main/ without architecture.md.
. (Join-Path $PSScriptRoot '_helpers.ps1')

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..') | Select-Object -ExpandProperty Path

# Synthetic project structure under repo root: projects/test-arch-fixture/{PROJECT.md, apps/synth-svc/src/main/java/Foo.java}
# But no specs/services/synth-svc/architecture.md.
$tempProj = "projects\test-arch-fixture-" + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$projDir = Join-Path $repoRoot $tempProj
$srcMain = Join-Path $projDir "apps\synth-svc\src\main\java"
New-Item -Path $srcMain -ItemType Directory -Force | Out-Null
New-Item -Path (Join-Path $projDir "PROJECT.md") -ItemType File -Force | Out-Null
Set-Content -Path (Join-Path $projDir "PROJECT.md") -Value "---`ndomain: test`n---" -Encoding UTF8

try {
    $synthFile = Join-Path $srcMain "Foo.java"
    $output = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{
            file_path = $synthFile
            content   = "public class Foo {}"
        }
        cwd = $repoRoot
    }
    Assert-Stanza -Output $output -ExpectedId 'HARDSTOP-09' -ExpectedDecision 'block'
    "PASS: HARDSTOP-09 (architecture.md missing for synth-svc)"
}
finally {
    Remove-Item -Recurse -Force $projDir -ErrorAction SilentlyContinue
}
