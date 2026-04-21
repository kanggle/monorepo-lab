#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ERRORS=0

echo "=== Kubernetes Manifest Validation ==="
echo ""

# 1. kubectl dry-run validation
echo "[1/2] Running kubectl apply --dry-run=client..."
if command -v kubectl &> /dev/null; then
  if kubectl apply --dry-run=client -f "$SCRIPT_DIR/" -R 2>&1; then
    echo "  -> kubectl dry-run: PASSED"
  else
    echo "  -> kubectl dry-run: FAILED"
    ERRORS=$((ERRORS + 1))
  fi
else
  echo "  -> kubectl not found, skipping dry-run validation"
fi
echo ""

# 2. kubeconform schema validation
echo "[2/2] Running kubeconform schema validation..."
if command -v kubeconform &> /dev/null; then
  if find "$SCRIPT_DIR" -name '*.yaml' -o -name '*.yml' | xargs kubeconform -strict -summary 2>&1; then
    echo "  -> kubeconform: PASSED"
  else
    echo "  -> kubeconform: FAILED"
    ERRORS=$((ERRORS + 1))
  fi
else
  echo "  -> kubeconform not found, skipping schema validation"
  echo "  -> Install: https://github.com/yannh/kubeconform"
fi
echo ""

# Summary
echo "=== Validation Complete ==="
if [ $ERRORS -eq 0 ]; then
  echo "All checks passed (or tools not available)."
  exit 0
else
  echo "$ERRORS check(s) failed."
  exit 1
fi
