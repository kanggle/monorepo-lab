# Runner for the hook fixtures.
#
# CI-gated since TASK-MONO-405 — the `hook-fixtures` job in .github/workflows/ci.yml
# runs this on windows-latest under Windows PowerShell, gated on the `hooks` paths
# filter (.claude/hooks/** + .claude/settings.json). Before that it was developer-run
# only, which meant MONO-402's regression fixtures were watching nothing: the regex
# could be reverted or the matcher narrowed again and CI stayed green.
#
# Run under Windows PowerShell (5.1), NOT pwsh — settings.json launches the hooks with
# `powershell -NoProfile -File …`, and the fixtures shell out the same way. That is the
# interpreter whose behaviour we need to be asserting.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/__tests__/run-all.ps1
#
# Each fixture is self-contained and emits one "PASS:" line per assertion.
# A failure throws, and the runner exits non-zero with the failing fixture name.

$ErrorActionPreference = 'Stop'

$fixtures = @(
    'hardstop-01-no-project-md.ps1',
    'hardstop-03-shared-lib-leak.ps1',
    'hardstop-05-task-not-ready.ps1',
    'hardstop-09-architecture-missing.ps1',
    'hardstop-09-deferred-skeleton-option3.ps1',
    'hardstop-10-service-type-missing.ps1',
    'hardstop-10-crlf-lf-simulation.ps1',
    'hardstop-body-canonical-sync.ps1',
    'format-alignment.ps1',
    'protect-main-branch.ps1',
    'verify-worktree-isolation.ps1',
    'warn-shared-checkout-switch.ps1'
)

$failed = @()
foreach ($fx in $fixtures) {
    $fxPath = Join-Path $PSScriptRoot $fx
    Write-Host "--- Running $fx ---"
    try {
        & $fxPath
    }
    catch {
        Write-Host ("FAIL: {0} -> {1}" -f $fx, $_.Exception.Message)
        $failed += $fx
    }
}

Write-Host ""
if ($failed.Count -gt 0) {
    Write-Host ("{0} fixture(s) FAILED: {1}" -f $failed.Count, ($failed -join ', '))
    exit 1
}
Write-Host "All fixtures PASS"
exit 0
