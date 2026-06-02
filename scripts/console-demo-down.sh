#!/usr/bin/env bash
# =============================================================================
# console-demo-down.sh — TASK-MONO-170
# Remove the per-domain ops DEMO overlay from the federation-hardening-e2e stack.
# Stops + removes the scm-gateway overlay container. Does NOT tear down the
# fed-e2e BASE harness (that is the harness's own lifecycle).
#
# Usage:  pnpm console-demo:down   (or)   ./scripts/console-demo-down.sh
# =============================================================================
set -uo pipefail
PROJ='federation-hardening-e2e'

printf '=== removing scm-gateway demo overlay container ===\n'
docker rm -f "${PROJ}-scm-gateway-1" 2>/dev/null || true

printf '[OK] demo overlay removed (scm-gateway).\n'
printf '     The fed-e2e base harness is untouched. console-web keeps its overlay env\n'
printf '     until the base is recreated without the demo overlay.\n'
