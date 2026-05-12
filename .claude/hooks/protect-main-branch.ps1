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
    if ($command -match 'git\s+push.*\b(main|master)\b' -or
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
}
catch {}
