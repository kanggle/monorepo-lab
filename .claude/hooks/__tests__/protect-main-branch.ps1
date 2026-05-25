# protect-main-branch fixture — 4 positive + 3 negative cases.
#
# This hook is a safety-rail (Bash-tool guard), intentionally exempt from the
# 4-block remediation message standard (see .claude/hooks/README.md). We
# assert raw decision/reason shape via Assert-PlainBlock / Assert-Allowed.
#
# Positive case 4 (HEAD-on-main implicit push) is the regression fixture for
# TASK-MONO-135 — the 2026-05-25 fan-platform agent leak that force-rolled
# back origin/main to 11a3d9b0.
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

# --- Test scaffolding: build two throwaway git repos, one on main HEAD, one on a feature branch.
$tmpRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("hook-fx-mono135-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $tmpRoot -Force | Out-Null
try {
    $repoMain = Join-Path $tmpRoot 'repo-on-main'
    $repoFeat = Join-Path $tmpRoot 'repo-on-feature'

    foreach ($r in @($repoMain, $repoFeat)) {
        New-Item -ItemType Directory -Path $r -Force | Out-Null
        & git -C $r init -q -b main *>$null
        # Need at least one commit for symbolic-ref to resolve.
        & git -C $r -c user.email=t@t -c user.name=t commit --allow-empty -q -m init *>$null
    }
    & git -C $repoFeat checkout -q -b task/foo *>$null

    # --- Positive 1: literal `main` in command (legacy regex path).
    $p1 = Invoke-Hook -HookName 'protect-main-branch.ps1' -Payload @{
        tool_name  = 'Bash'
        tool_input = @{ command = 'git push origin main' }
        cwd        = $repoFeat   # cwd irrelevant for legacy regex
    }
    Assert-PlainBlock -Output $p1 -ExpectedReasonSubstring 'direct push, force push, or hard reset'
    "PASS: positive-1 (literal `git push origin main`)"

    # --- Positive 2: raw `--force` (legacy regex path).
    $p2 = Invoke-Hook -HookName 'protect-main-branch.ps1' -Payload @{
        tool_name  = 'Bash'
        tool_input = @{ command = 'git push --force origin some-branch' }
        cwd        = $repoFeat
    }
    Assert-PlainBlock -Output $p2 -ExpectedReasonSubstring 'direct push, force push, or hard reset'
    "PASS: positive-2 (raw --force push)"

    # --- Positive 3: hard reset to origin/main (legacy regex path).
    $p3 = Invoke-Hook -HookName 'protect-main-branch.ps1' -Payload @{
        tool_name  = 'Bash'
        tool_input = @{ command = 'git reset --hard origin/main' }
        cwd        = $repoFeat
    }
    Assert-PlainBlock -Output $p3 -ExpectedReasonSubstring 'direct push, force push, or hard reset'
    "PASS: positive-3 (hard reset to origin/main)"

    # --- Positive 4 (TASK-MONO-135 regression): HEAD-on-main implicit push.
    #
    # 2026-05-25 fan-platform sweep leak fixture: agent worktree HEAD = main,
    # `git push -u origin HEAD` defaults to origin/main. Pre-MONO-135 regex
    # missed this because the command string had no literal "main".
    $p4a = Invoke-Hook -HookName 'protect-main-branch.ps1' -Payload @{
        tool_name  = 'Bash'
        tool_input = @{ command = 'git push -u origin HEAD' }
        cwd        = $repoMain
    }
    Assert-PlainBlock -Output $p4a -ExpectedReasonSubstring "cwd HEAD is 'main'"
    "PASS: positive-4a (HEAD=main + `git push -u origin HEAD`)"

    $p4b = Invoke-Hook -HookName 'protect-main-branch.ps1' -Payload @{
        tool_name  = 'Bash'
        tool_input = @{ command = 'git push' }
        cwd        = $repoMain
    }
    Assert-PlainBlock -Output $p4b -ExpectedReasonSubstring "cwd HEAD is 'main'"
    "PASS: positive-4b (HEAD=main + bare `git push`)"

    $p4c = Invoke-Hook -HookName 'protect-main-branch.ps1' -Payload @{
        tool_name  = 'Bash'
        tool_input = @{ command = 'git push origin' }
        cwd        = $repoMain
    }
    Assert-PlainBlock -Output $p4c -ExpectedReasonSubstring "cwd HEAD is 'main'"
    "PASS: positive-4c (HEAD=main + `git push origin`)"

    # --- Negative 1: feature branch HEAD + implicit push — allowed.
    $n1 = Invoke-Hook -HookName 'protect-main-branch.ps1' -Payload @{
        tool_name  = 'Bash'
        tool_input = @{ command = 'git push -u origin HEAD' }
        cwd        = $repoFeat
    }
    Assert-Allowed -Output $n1
    "PASS: negative-1 (HEAD=feature + `git push -u origin HEAD` allowed)"

    # --- Negative 2: portfolio-sync cwd allowlist.
    $n2 = Invoke-Hook -HookName 'protect-main-branch.ps1' -Payload @{
        tool_name  = 'Bash'
        tool_input = @{ command = 'git push --force origin main' }
        cwd        = '/tmp/portfolio-sync/wms-platform'
    }
    Assert-Allowed -Output $n2
    "PASS: negative-2 (portfolio-sync allowlist)"

    # --- Negative 3: project-template cwd allowlist.
    $n3 = Invoke-Hook -HookName 'protect-main-branch.ps1' -Payload @{
        tool_name  = 'Bash'
        tool_input = @{ command = 'git push --force origin main' }
        cwd        = '/tmp/project-template-extract-001'
    }
    Assert-Allowed -Output $n3
    "PASS: negative-3 (project-template allowlist)"

    # --- Negative 4: explicit non-main refspec from HEAD=main — allowed.
    # `git push origin HEAD:feature-x` explicitly targets feature-x, not main.
    # The legacy `\b(main|master)\b` regex doesn't match (the only "main" is
    # the HEAD branch, not in the command string), and the new MONO-135 check
    # sees `:` in the command (refspec form) so the implicit-target patterns
    # don't match either → allow.
    $n4 = Invoke-Hook -HookName 'protect-main-branch.ps1' -Payload @{
        tool_name  = 'Bash'
        tool_input = @{ command = 'git push origin HEAD:feature-x' }
        cwd        = $repoMain
    }
    Assert-Allowed -Output $n4
    "PASS: negative-4 (HEAD=main + explicit `HEAD:feature-x` refspec allowed)"
}
finally {
    Remove-Item -Path $tmpRoot -Recurse -Force -ErrorAction SilentlyContinue
}
