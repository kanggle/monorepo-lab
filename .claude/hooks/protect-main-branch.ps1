$reader    = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$inputJson = $reader.ReadToEnd()

try {
    $data = $inputJson | ConvertFrom-Json
    $command = ""

    if ($data.tool_input -and $data.tool_input.command) {
        $command = $data.tool_input.command
    }

    $cwd = ""
    if ($data.cwd) { $cwd = $data.cwd }

    # Allowlist: portfolio-sync workdirs.
    # scripts/sync-portfolio.sh extracts each project into /tmp/portfolio-sync/<name>/
    # and force-pushes to its standalone remote (kanggle/wms-platform,
    # kanggle/ecommerce-microservices-platform). That force-push is intentional —
    # the standalone repos are derived artifacts re-generated from monorepo HEAD.
    # Detect via cwd OR via an inline `cd /tmp/portfolio-sync/...` in the command.
    if ($cwd -match 'portfolio-sync' -or $command -match 'portfolio-sync') {
        exit 0
    }
    
    # Allowlist: Template repo extraction workdirs.
    # scripts/extract-template.sh produces an extraction tree at /tmp/project-template-*
    # (or any name the operator passes). Per ADR-MONO-003b § D2.3, the first push of
    # that tree to kanggle/project-template IS the Phase 5 launch artifact. The same
    # allowlist also covers future scripts/sync-template.sh runs that force-push
    # subsequent rebuilds. Detect via cwd OR via inline `cd /tmp/project-template-...`.
    if ($cwd -match 'project-template' -or $command -match 'project-template') {
        exit 0
    }


    # Block git push to main/master, raw force push, or hard reset to origin's
    # main/master. `--force-with-lease` (the safe alternative used to update
    # feature branches after rebase — git only updates the remote when its
    # current tip matches what we last fetched) is NOT blocked on non-protected
    # branches; the first regex still catches main/master targets regardless of
    # the force flavor. TASK-MONO-043 (A) fixed this false-positive.
    # TASK-MONO-150: scope the main/master target match to an actual `git push`
    # segment + ref position. Previous `git\s+push.*\b(main|master)\b` matched
    # across shell-operator chains (`git push origin feat && gh pr create
    # --base main`) and hyphen-token branch names (`feature-main-fix`).
    $pushTargetsMainMaster = $false
    foreach ($seg in ($command -split '\s*(?:&&|\|\||;|\|)\s*')) {
        if ($seg -match 'git\s+push\b' -and $seg -match '(?:\s|:)(main|master)(?:\s|$)') {
            $pushTargetsMainMaster = $true
            break
        }
    }
    if ($pushTargetsMainMaster -or
        $command -match 'git\s+push\s+--force(?!-with-lease)' -or
        $command -match 'git\s+push\s+-f\b' -or
        $command -match 'git\s+reset\s+--hard\s+origin/(main|master)') {

        $result = @{
            decision = "block"
            reason   = "main/master branch protection: direct push, force push, or hard reset to main/master is blocked."
        }
        $result | ConvertTo-Json -Compress
        exit 0
    }

    # TASK-MONO-135: HEAD-based implicit-target push gap.
    #
    # The 4 patterns above all rely on the command STRING containing literal
    # "main"/"master". They miss the case where the agent's cwd (typically a
    # worktree) has HEAD pointing at main/master and the command is bare
    # `git push` / `git push origin` / `git push [-u] origin HEAD` — git's
    # default `push.default = simple/current` then pushes the local branch
    # to its same-name upstream (origin/main).
    #
    # The 2026-05-25 fan-platform 8-project sweep had exactly this leak:
    # one agent's worktree ended up on main HEAD, `git push -u origin HEAD`
    # passed the regex, and origin/main was force-rolled back to 11a3d9b0
    # to recover. This block prevents recurrence.
    #
    # Allow rule: if the command has an EXPLICIT non-main target (contains `:`
    # for refspec, or `origin <branch>` for non-HEAD positional), let the
    # earlier regex's verdict stand. Only intercept implicit-default forms.
    if ($command -match 'git\s+push\b' -and $cwd) {
        $isImplicitTarget =
            $command -match '^\s*git\s+push\s*$' -or
            $command -match '^\s*git\s+push\s+(?:--?\S+(?:[=\s]\S+)?\s+)*origin\s*$' -or
            $command -match '^\s*git\s+push\s+(?:--?\S+(?:[=\s]\S+)?\s+)*origin\s+HEAD\s*$' -or
            $command -match '^\s*git\s+push\s+(?:--?\S+(?:[=\s]\S+)?\s+)*HEAD\s*$'

        if ($isImplicitTarget) {
            $branch = $null
            try {
                $branch = (& git -C $cwd symbolic-ref --short HEAD 2>$null)
                if ($branch) { $branch = "$branch".Trim() }
            } catch {}

            if ($branch -eq 'main' -or $branch -eq 'master') {
                $result = @{
                    decision = "block"
                    reason   = "main/master branch protection: cwd HEAD is '$branch' and the bare/implicit `git push` would default to origin/$branch. Switch to a feature branch first (git checkout -b task/<id>-<short>) or use an explicit target (git push origin HEAD:<feature-branch>)."
                }
                $result | ConvertTo-Json -Compress
                exit 0
            }
        }
    }
}
catch {}
