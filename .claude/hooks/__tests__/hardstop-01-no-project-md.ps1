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
    "PASS: HARDSTOP-01 positive (no PROJECT.md walking up from projects/orphan/)"

    # Negative case: file path entirely outside any monorepo (no .git/CLAUDE.md+tasks INDEX walking up).
    # The hook should fail-open silently — the monorepo's Hard Stop rules have no jurisdiction here.
    $outsideRoot = Join-Path $env:TEMP ("hardstop-outside-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
    New-Item -Path $outsideRoot -ItemType Directory -Force | Out-Null
    try {
        $outsideFile = Join-Path $outsideRoot "scratch.md"
        $outsideOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
            tool_name  = 'Write'
            tool_input = @{
                file_path = $outsideFile
                content   = "some scratch content"
            }
            cwd = $outsideRoot
        }
        Assert-Allowed -Output $outsideOutput
        "PASS: HARDSTOP-01 negative (file outside any monorepo, fail-open)"
    }
    finally {
        Remove-Item -Recurse -Force $outsideRoot -ErrorAction SilentlyContinue
    }
}
finally {
    Remove-Item -Recurse -Force $tempRoot -ErrorAction SilentlyContinue
}
