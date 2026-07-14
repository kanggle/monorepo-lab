# verify-worktree-isolation fixture — 4 positive + 5 negative cases.
#
# This hook is a safety-rail (PreToolUse Edit/Write guard), intentionally
# exempt from the 4-block remediation message standard (same policy as
# protect-main-branch.ps1; see .claude/hooks/README.md). We assert raw
# decision shape via Assert-PlainBlock / Assert-Allowed.
#
# Positive cases reproduce the 2026-05-25 PC-BE-005 dispatch leak shape
# (linked worktree cwd + file_path resolving outside that worktree) —
# ~2.8% leak rate, 4 leaks / 144 tool_uses; agent self-recovered via
# `git diff` -> Move-Item -> `git apply` -> `git restore`. Defensive
# hook closes the next-dispatch repeat path.
. (Join-Path $PSScriptRoot '_helpers.ps1')

function Assert-PlainBlock {
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string]$Output,
        [Parameter(Mandatory)][string]$ExpectedReasonSubstring
    )
    if (-not $Output) {
        throw "Expected hook block, got empty (silently allowed)"
    }
    $parsed = ConvertFrom-HookOutput -Output $Output
    if (-not $parsed) {
        throw "Hook output is not valid JSON: $Output"
    }
    if ($parsed.decision -ne 'block') {
        throw "Expected decision='block', got '$($parsed.decision)'"
    }
    if ($parsed.reason -notmatch [regex]::Escape($ExpectedReasonSubstring)) {
        throw "Expected reason to contain '$ExpectedReasonSubstring', got: $($parsed.reason)"
    }
}

# --- Test scaffolding: build a throwaway main repo + two linked worktrees.
$tmpRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("hook-fx-mono136-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $tmpRoot -Force | Out-Null
try {
    $mainRepo = Join-Path $tmpRoot 'main-repo'
    $wtA      = Join-Path $tmpRoot 'wt-A'
    $wtB      = Join-Path $tmpRoot 'wt-B'
    $nonGit   = Join-Path $tmpRoot 'not-a-git'

    New-Item -ItemType Directory -Path $mainRepo -Force | Out-Null
    New-Item -ItemType Directory -Path $nonGit   -Force | Out-Null

    & git -C $mainRepo init -q -b main *>$null
    & git -C $mainRepo -c user.email=t@t -c user.name=t commit --allow-empty -q -m init *>$null
    & git -C $mainRepo worktree add --detach -q $wtA HEAD *>$null
    & git -C $mainRepo worktree add --detach -q $wtB HEAD *>$null

    # Throwaway files to make file_path resolution realistic. Containment
    # is the only thing being tested — file existence is not required by
    # GetFullPath but we create some anyway so the fixture is closer to
    # real Edit/Write payloads (which would target existing files).
    $mainSubdir = Join-Path $mainRepo 'projects/foo'
    New-Item -ItemType Directory -Path $mainSubdir -Force | Out-Null
    $mainFile = Join-Path $mainSubdir 'Bar.java'
    Set-Content -LiteralPath $mainFile -Value 'class Bar {}' -NoNewline

    $wtASubdir = Join-Path $wtA 'projects/foo'
    New-Item -ItemType Directory -Path $wtASubdir -Force | Out-Null
    $wtAFile = Join-Path $wtASubdir 'Bar.java'
    Set-Content -LiteralPath $wtAFile -Value 'class Bar {}' -NoNewline

    $wtBFile = Join-Path $wtB 'README.md'
    Set-Content -LiteralPath $wtBFile -Value '# wt B' -NoNewline

    # --- Positive 1: linked worktree cwd + main repo file_path (PC-BE-005 leak shape).
    $p1 = Invoke-Hook -HookName 'verify-worktree-isolation.ps1' -Payload @{
        tool_name  = 'Edit'
        tool_input = @{ file_path = $mainFile }
        cwd        = $wtA
    }
    Assert-PlainBlock -Output $p1 -ExpectedReasonSubstring 'Worktree isolation breach'
    "PASS: positive-1 (linked worktree cwd + main repo file_path)"

    # --- Positive 2: linked worktree cwd + sibling linked worktree file_path.
    $p2 = Invoke-Hook -HookName 'verify-worktree-isolation.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{ file_path = $wtBFile }
        cwd        = $wtA
    }
    Assert-PlainBlock -Output $p2 -ExpectedReasonSubstring 'Worktree isolation breach'
    "PASS: positive-2 (linked worktree A cwd + sibling worktree B file_path)"

    # --- Positive 3: linked worktree cwd + unrelated absolute path.
    $unrelated = Join-Path $tmpRoot 'elsewhere\foo.txt'
    New-Item -ItemType Directory -Path (Split-Path $unrelated -Parent) -Force | Out-Null
    Set-Content -LiteralPath $unrelated -Value 'x' -NoNewline
    $p3 = Invoke-Hook -HookName 'verify-worktree-isolation.ps1' -Payload @{
        tool_name  = 'Edit'
        tool_input = @{ file_path = $unrelated }
        cwd        = $wtA
    }
    Assert-PlainBlock -Output $p3 -ExpectedReasonSubstring 'Worktree isolation breach'
    "PASS: positive-3 (linked worktree cwd + unrelated absolute path)"

    # --- Positive 4: prefix false-positive guard.
    # Toplevel = $wtA. A sibling directory named "$wtA-extra" would prefix-
    # match without the DirectorySeparatorChar suffix guard — assert that
    # this case still blocks (i.e. the guard works the other way too: a
    # sibling whose prefix matches the toplevel is correctly classified
    # as OUTSIDE the toplevel).
    $sibling = "$wtA-extra"
    New-Item -ItemType Directory -Path $sibling -Force | Out-Null
    $siblingFile = Join-Path $sibling 'leak.txt'
    Set-Content -LiteralPath $siblingFile -Value 'x' -NoNewline
    $p4 = Invoke-Hook -HookName 'verify-worktree-isolation.ps1' -Payload @{
        tool_name  = 'Edit'
        tool_input = @{ file_path = $siblingFile }
        cwd        = $wtA
    }
    Assert-PlainBlock -Output $p4 -ExpectedReasonSubstring 'Worktree isolation breach'
    "PASS: positive-4 (prefix-similar sibling dir correctly classified as outside)"

    # --- Negative 1: main worktree cwd + main repo file_path = allowed.
    $n1 = Invoke-Hook -HookName 'verify-worktree-isolation.ps1' -Payload @{
        tool_name  = 'Edit'
        tool_input = @{ file_path = $mainFile }
        cwd        = $mainRepo
    }
    Assert-Allowed -Output $n1
    "PASS: negative-1 (main worktree cwd + main repo file_path allowed)"

    # --- Negative 2: linked worktree cwd + file_path inside the SAME worktree = allowed.
    $n2 = Invoke-Hook -HookName 'verify-worktree-isolation.ps1' -Payload @{
        tool_name  = 'Edit'
        tool_input = @{ file_path = $wtAFile }
        cwd        = $wtA
    }
    Assert-Allowed -Output $n2
    "PASS: negative-2 (linked worktree cwd + same-worktree file_path allowed)"

    # --- Negative 3: cwd not a git directory = allowed (silent skip).
    $n3 = Invoke-Hook -HookName 'verify-worktree-isolation.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{ file_path = (Join-Path $nonGit 'foo.txt') }
        cwd        = $nonGit
    }
    Assert-Allowed -Output $n3
    "PASS: negative-3 (non-git cwd silently allowed)"

    # --- Negative 4: empty cwd or empty file_path = allowed.
    $n4a = Invoke-Hook -HookName 'verify-worktree-isolation.ps1' -Payload @{
        tool_name  = 'Edit'
        tool_input = @{ file_path = $mainFile }
        cwd        = ''
    }
    Assert-Allowed -Output $n4a
    "PASS: negative-4a (empty cwd allowed)"

    $n4b = Invoke-Hook -HookName 'verify-worktree-isolation.ps1' -Payload @{
        tool_name  = 'Edit'
        tool_input = @{ file_path = '' }
        cwd        = $wtA
    }
    Assert-Allowed -Output $n4b
    "PASS: negative-4b (empty file_path allowed)"

    # --- Negative 5: relative file_path = allowed.
    # Edit/Write tools require absolute paths, but the hook should
    # defensively silent-allow relative inputs rather than synthesise a
    # path relative to cwd (which could falsely flag).
    $n5 = Invoke-Hook -HookName 'verify-worktree-isolation.ps1' -Payload @{
        tool_name  = 'Edit'
        tool_input = @{ file_path = 'relative\path\foo.txt' }
        cwd        = $wtA
    }
    Assert-Allowed -Output $n5
    "PASS: negative-5 (relative file_path allowed)"
}
finally {
    # Clean up linked worktrees before deleting the dir tree (Windows pnpm
    # long-path style failure not expected for a throwaway empty wt, but
    # `git worktree remove --force` is the documented order).
    & git -C $mainRepo worktree remove --force $wtA 2>$null
    & git -C $mainRepo worktree remove --force $wtB 2>$null
    Remove-Item -Path $tmpRoot -Recurse -Force -ErrorAction SilentlyContinue
}
