# =============================================================================
# console-demo-down.ps1 — TASK-MONO-170
# Remove the per-domain ops DEMO overlay from the federation-hardening-e2e stack.
# Stops + removes the scm-gateway overlay container. Does NOT tear down the
# fed-e2e BASE harness (that is the harness's own lifecycle). console-web reverts
# to its base env the next time the base is brought up without the demo overlay.
#
# Usage:  pnpm console-demo:down   (or)   .\scripts\console-demo-down.ps1
# =============================================================================
[CmdletBinding()]
param()
$ErrorActionPreference = 'Continue'
$Proj = 'federation-hardening-e2e'

Write-Host "=== removing scm-gateway demo overlay container ===" -ForegroundColor Cyan
docker rm -f "$Proj-scm-gateway-1" 2>&1 | Out-Host

Write-Host "[OK] demo overlay removed (scm-gateway)." -ForegroundColor Green
Write-Host "     The fed-e2e base harness is untouched. console-web keeps its overlay env"
Write-Host "     until the base is recreated without the demo overlay (or the harness restarts it)."
