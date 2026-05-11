# HARDSTOP-01 fixture — file under projects/<name>/ without PROJECT.md ancestor.
. (Join-Path $PSScriptRoot '_helpers.ps1')

# Use a synthetic projects/<orphan>/foo.md path under a temp tree that mimics the repo layout
# without containing a PROJECT.md anywhere on the walk-up path.
$tempRoot = Join-Path $env:TEMP ("hardstop-test-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -Path (Join-Path $tempRoot "projects\orphan-no-projectmd\apps") -ItemType Directory -Force | Out-Null
# Touch a .git so Get-RepoRoot resolves
New-Item -Path (Join-Path $tempRoot ".git") -ItemType Directory -Force | Out-Null

try {
    $tempFile = Join-Path $tempRoot "projects\orphan-no-projectmd\apps\foo.md"
    $output = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Edit'
        tool_input = @{
            file_path  = $tempFile
            old_string = 'foo'
            new_string = 'bar baz qux quux corge grault garply waldo fred plugh xyzzy thud'
        }
        cwd        = $tempRoot
    }
    Assert-Stanza -Output $output -ExpectedId 'HARDSTOP-01' -ExpectedDecision 'block'
    "PASS: HARDSTOP-01 (no PROJECT.md walking up from projects/orphan/)"
}
finally {
    Remove-Item -Recurse -Force $tempRoot -ErrorAction SilentlyContinue
}
