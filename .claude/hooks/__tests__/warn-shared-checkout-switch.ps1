# warn-shared-checkout-switch fixture — 1 positive (ask) + 5 negative (allow).
#
# Safety-rail PreToolUse[Bash] guard, exempt from the 4-block remediation
# standard. Asserts raw decision shape via Assert-PlainAsk / Assert-Allowed.
# Positive case reproduces the 2026-06-13 pc-fe-070 x increment-C shape:
# a HEAD-moving checkout in the MAIN checkout with a dirty working tree.
. (Join-Path $PSScriptRoot '_helpers.ps1')

function Assert-PlainAsk {
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string]$Output,
        [Parameter(Mandatory)][string]$ExpectedReasonSubstring
    )
    if (-not $Output) { throw "Expected hook ask, got empty (silently allowed)" }
    $parsed = ConvertFrom-HookOutput -Output $Output
    if (-not $parsed) { throw "Hook output is not valid JSON: $Output" }
    if ($parsed.decision -ne 'ask') { throw "Expected decision='ask', got '$($parsed.decision)'" }
    if ($parsed.reason -notmatch [regex]::Escape($ExpectedReasonSubstring)) {
        throw "Expected reason to contain '$ExpectedReasonSubstring', got: $($parsed.reason)"
    }
}

$tmpRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("hook-fx-mono236-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $tmpRoot -Force | Out-Null
try {
    $mainRepo  = Join-Path $tmpRoot 'main-repo'
    $wt        = Join-Path $tmpRoot 'wt'
    $elsewhere = Join-Path $tmpRoot 'elsewhere'
    New-Item -ItemType Directory -Path $mainRepo  -Force | Out-Null
    New-Item -ItemType Directory -Path $elsewhere -Force | Out-Null

    & git -C $mainRepo init -q -b main *>$null
    & git -C $mainRepo -c user.email=t@t -c user.name=t commit --allow-empty -q -m init *>$null

    # Negative 1: main + CLEAN tree + checkout -b = allowed (clean-start).
    $n1 = Invoke-Hook -HookName 'warn-shared-checkout-switch.ps1' -Payload @{
        tool_name = 'Bash'; tool_input = @{ command = 'git checkout -b task/foo' }; cwd = $mainRepo
    }
    Assert-Allowed -Output $n1
    "PASS: negative-1 (main + clean + checkout -b allowed)"

    # Negative 2: same-branch no-op switch = allowed.
    $n2 = Invoke-Hook -HookName 'warn-shared-checkout-switch.ps1' -Payload @{
        tool_name = 'Bash'; tool_input = @{ command = 'git switch main' }; cwd = $mainRepo
    }
    Assert-Allowed -Output $n2
    "PASS: negative-2 (same-branch no-op switch allowed)"

    # Dirty the main checkout (untracked file -> non-empty porcelain).
    Set-Content -LiteralPath (Join-Path $mainRepo 'wip.txt') -Value 'wip' -NoNewline

    # Positive: main + DIRTY + HEAD-moving checkout -b = ask.
    $p1 = Invoke-Hook -HookName 'warn-shared-checkout-switch.ps1' -Payload @{
        tool_name = 'Bash'; tool_input = @{ command = 'git checkout -b task/bar' }; cwd = $mainRepo
    }
    Assert-PlainAsk -Output $p1 -ExpectedReasonSubstring 'Concurrent-session shared-checkout hazard'
    "PASS: positive-1 (main + dirty + checkout -b -> ask)"

    # Negative 3: pathspec/file checkout = allowed (not a HEAD move).
    $n3 = Invoke-Hook -HookName 'warn-shared-checkout-switch.ps1' -Payload @{
        tool_name = 'Bash'; tool_input = @{ command = 'git checkout -- wip.txt' }; cwd = $mainRepo
    }
    Assert-Allowed -Output $n3
    "PASS: negative-3 (pathspec checkout allowed)"

    # Negative 4: git routed elsewhere via -C = allowed (cwd heuristic invalid).
    $n4 = Invoke-Hook -HookName 'warn-shared-checkout-switch.ps1' -Payload @{
        tool_name = 'Bash'; tool_input = @{ command = "git -C $elsewhere checkout -b z" }; cwd = $mainRepo
    }
    Assert-Allowed -Output $n4
    "PASS: negative-4 (git -C redirect allowed)"

    # Negative 5: dirty LINKED worktree + HEAD-moving checkout = allowed (sanctioned).
    & git -C $mainRepo worktree add --detach -q $wt HEAD *>$null
    Set-Content -LiteralPath (Join-Path $wt 'wip2.txt') -Value 'wip' -NoNewline
    $n5 = Invoke-Hook -HookName 'warn-shared-checkout-switch.ps1' -Payload @{
        tool_name = 'Bash'; tool_input = @{ command = 'git checkout -b z' }; cwd = $wt
    }
    Assert-Allowed -Output $n5
    "PASS: negative-5 (linked worktree checkout allowed)"
}
finally {
    & git -C $mainRepo worktree remove --force $wt 2>$null
    Remove-Item -Path $tmpRoot -Recurse -Force -ErrorAction SilentlyContinue
}
