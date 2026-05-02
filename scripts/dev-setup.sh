#!/usr/bin/env bash
# dev-setup.sh — register *.local hostnames in /etc/hosts
#
# Idempotent: re-running is safe. Adds entries only if missing.
# See docs/adr/ADR-MONO-001-port-prefix-scaling.md for the rationale.
#
# Usage:
#   bash scripts/dev-setup.sh
#
# Requires sudo to edit /etc/hosts.

set -euo pipefail

HOSTS_FILE="/etc/hosts"
MARKER_BEGIN="# BEGIN monorepo-lab dev hosts (TASK-MONO-022)"
MARKER_END="# END monorepo-lab dev hosts"

HOSTS=(
    "ecommerce.local"
    "wms.local"
    "gap.local"
    "fan-platform.local"
    "scm.local"
    "erp.local"
    "mes.local"
)

if [[ "$OSTYPE" == "msys"* || "$OSTYPE" == "cygwin"* ]]; then
    echo "[ERROR] On Windows, use scripts/dev-setup.ps1 (Run as Administrator)." >&2
    exit 1
fi

if [[ ! -w "$HOSTS_FILE" ]] && [[ "$EUID" -ne 0 ]]; then
    echo "[INFO] $HOSTS_FILE requires sudo. Re-running with sudo..."
    exec sudo bash "$0" "$@"
fi

# Check if our marker block is already present
if grep -qF "$MARKER_BEGIN" "$HOSTS_FILE"; then
    echo "[OK] $HOSTS_FILE already contains monorepo-lab dev hosts block."
    echo "[OK] Verifying entries..."
    missing=0
    for host in "${HOSTS[@]}"; do
        if ! grep -qE "^[^#]*\b${host}\b" "$HOSTS_FILE"; then
            echo "[WARN] $host not present in active entries — re-run after manual review."
            missing=$((missing + 1))
        fi
    done
    if [[ "$missing" -eq 0 ]]; then
        echo "[OK] All ${#HOSTS[@]} hostnames mapped. Nothing to do."
    fi
    exit 0
fi

echo "[INFO] Appending ${#HOSTS[@]} dev hostnames to $HOSTS_FILE ..."

{
    echo ""
    echo "$MARKER_BEGIN"
    for host in "${HOSTS[@]}"; do
        echo "127.0.0.1  $host"
    done
    echo "$MARKER_END"
} >> "$HOSTS_FILE"

echo "[OK] Added entries:"
for host in "${HOSTS[@]}"; do
    echo "       127.0.0.1  $host"
done
echo ""
echo "[NEXT] Start Traefik: pnpm traefik:up"
echo "       Then bring up your project(s) and access via http://<project>.local/"
